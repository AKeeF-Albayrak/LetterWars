import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.GameDuration
import com.example.letterwars.data.model.GameStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job

class QueueViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repo = QueueRepository()
    private val playerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private val durationMinutes: Int =
        savedStateHandle.get<String>("duration")?.toIntOrNull() ?: 5
    val gameDuration: GameDuration = GameDuration.fromMinutes(durationMinutes)

    private val _isSearching = MutableStateFlow(true)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _gameId = MutableStateFlow<String?>(null)
    val gameId: StateFlow<String?> = _gameId

    private val _queueUserCount = MutableStateFlow(0)
    val queueUserCount: StateFlow<Int> = _queueUserCount

    private var gameListener: ListenerRegistration? = null
    private var checkingJob: Job? = null
    private val queueMutex = Mutex() // Eşzamanlılık kontrolü için mutex

    init {
        joinQueue()
        listenForGameMatch()
        startQueueCountUpdater()
    }

    private fun listenForGameMatch() {
        gameListener = repo.listenForGameForPlayer(playerId) { foundGameId ->
            viewModelScope.launch {
                queueMutex.withLock {
                    if (foundGameId != null && _gameId.value == null) {
                        _gameId.value = foundGameId
                        _isSearching.value = false
                        // Dinleyici işi bittiğinde diğer işleri iptal et
                        checkingJob?.cancel()
                    }
                }
            }
        }
    }

    private fun startQueueCountUpdater() = viewModelScope.launch {
        while (_isSearching.value) {
            try {
                val count = repo.getQueueUserCount(gameDuration)
                _queueUserCount.value = count
            } catch (e: Exception) {
                // Hata durumunda loglama yapabiliriz
            }
            delay(3000L) // 3 saniyede bir güncelle
        }
    }

    private fun joinQueue() = viewModelScope.launch {
        queueMutex.withLock {
            // Önce kendi bekleyen oyunumuzu temizleyelim (varsa)
            repo.deleteOwnWaitingGame(playerId)

            // Bekleyen bir oyun var mı kontrol edelim
            val waitingGame = repo.findWaitingGame(gameDuration)

            if (waitingGame != null && waitingGame.player1Id != playerId) {
                // Bekleyen oyuna katılmayı deneyelim
                val joined = repo.joinExistingGame(waitingGame, playerId)
                if (joined) {
                    _gameId.value = waitingGame.gameId
                    _isSearching.value = false
                } else {
                    // Eğer başkası katıldıysa, kendi oyunumuzu oluşturalım
                    createOwnWaitingGame()
                }
            } else {
                // Bekleyen oyun yoksa, kendi oyunumuzu oluşturalım
                createOwnWaitingGame()
            }
        }
    }

    private suspend fun createOwnWaitingGame() {
        val myWaitingGameId = repo.createWaitingGame(playerId, gameDuration)
        if (myWaitingGameId != null) {
            _isSearching.value = true
            startCheckingForOtherGames()
        }
    }

    private fun startCheckingForOtherGames() {
        // Önceki iş varsa iptal et
        checkingJob?.cancel()

        checkingJob = viewModelScope.launch {
            while (_isSearching.value) {
                delay(2000L) // Daha sık kontrol edelim

                queueMutex.withLock {
                    if (!_isSearching.value || _gameId.value != null) {
                        return@withLock // Artık arama yapmıyorsak veya oyun bulunmuşsa çıkalım
                    }

                    // Önce kendi oyunumuzun hala aktif olup olmadığını kontrol edelim
                    val myWaitingGame = repo.findWaitingGameForPlayer(playerId)

                    if (myWaitingGame == null) {
                        // Kendi oyunumuz silinmiş veya değiştirilmiş, muhtemelen başka bir oyuncu katıldı
                        // Yeni oyunlarımızı kontrol edelim
                        val myGames = repo.findActiveGamesForPlayer(playerId)
                        val activeGame = myGames.firstOrNull { it.status == GameStatus.IN_PROGRESS }

                        if (activeGame != null) {
                            _gameId.value = activeGame.gameId
                            _isSearching.value = false
                            return@withLock
                        } else {
                            // Hiçbir oyun bulunamadıysa, yeni bir bekleme oyunu oluşturalım
                            createOwnWaitingGame()
                            return@withLock
                        }
                    }

                    // Diğer bekleyen oyunları kontrol edelim
                    val waitingGame = repo.findWaitingGame(gameDuration)
                    if (waitingGame != null && waitingGame.player1Id != playerId) {
                        val joined = repo.joinExistingGame(waitingGame, playerId)
                        if (joined) {
                            // Başarıyla katıldık, kendi bekleme oyunumuzu temizleyelim
                            repo.deleteOwnWaitingGame(playerId)
                            _gameId.value = waitingGame.gameId
                            _isSearching.value = false
                        }
                    }
                }
            }
        }
    }

    fun leaveQueue() = viewModelScope.launch {
        queueMutex.withLock {
            repo.leaveMatchQueue(playerId)
            _isSearching.value = false
            checkingJob?.cancel()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            leaveQueue()
        }
        gameListener?.remove()
        checkingJob?.cancel()
    }
}