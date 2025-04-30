import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.GameDuration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private var checkingJob = false

    init {
        // QueueUserCount verisini periyodik olarak güncelle
        updateQueueUserCount()

        // Oyuncu sıraya katılıyor
        joinQueue()

        // Aynı zamanda aktif oyun var mı diye dinlemeye başla
        listenForGameMatch()
    }

    private fun updateQueueUserCount() = viewModelScope.launch {
        while (_isSearching.value) {
            try {
                val count = repo.getQueueUserCount(gameDuration)
                _queueUserCount.value = count
            } catch (e: Exception) {
                // Hata durumunda geçerli değeri koru
            }
            delay(5000L) // 5 saniyede bir güncelle
        }
    }

    private fun listenForGameMatch() {
        gameListener = repo.listenForGameForPlayer(playerId) { foundGameId ->
            if (foundGameId != null) {
                _gameId.value = foundGameId
                _isSearching.value = false
            }
        }
    }

    private fun joinQueue() = viewModelScope.launch {
        // Önce bekleyen oyun var mı kontrol et
        val waitingGame = repo.findWaitingGame(gameDuration)

        if (waitingGame != null && waitingGame.player1Id != playerId) {
            // Eğer bekleyen bir oyun varsa ve bu oyuncu kendi oluşturduğu değilse, katıl
            val joined = repo.joinExistingGame(waitingGame, playerId)
            if (joined) {
                // Katılım başarılı, artık oyun listeneri bize bildirimi yapacak
                // (GameID akışı hemen güncellenmez, Firebase listener üzerinden gelir)
                // Bu da her iki oyuncuya da bildirim gitmesini sağlar
            } else {
                // Eğer katılım başarısız olduysa, yeni bir bekleyen oyun oluştur
                createOwnWaitingGame()
            }
        } else {
            // Bekleyen oyun yoksa, yeni bir bekleyen oyun oluştur
            createOwnWaitingGame()
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
        if (checkingJob) return
        checkingJob = true

        viewModelScope.launch {
            while (_isSearching.value) {
                delay(5000L) // 5 saniyede bir kontrol et

                // Hala aktif arama yapıyorsak
                if (!_isSearching.value) break

                // Bekleyen başka bir oyun var mı kontrol et
                val waitingGame = repo.findWaitingGame(gameDuration)
                if (waitingGame != null && waitingGame.player1Id != playerId) {
                    // Bulunduğunda katılmayı dene
                    val success = repo.joinExistingGame(waitingGame, playerId)
                    if (success) {
                        // Katılmayı başardıysan, kendi bekleme oyununu sil (var ise)
                        repo.deleteOwnWaitingGame(playerId)

                        // GameID'yi artık güncelleme - Firebase listener üzerinden gelecek
                        // Bu da her iki oyuncuya da bildirim gitmesini sağlar
                        break
                    }
                }
            }
            checkingJob = false
        }
    }

    fun leaveQueue() = viewModelScope.launch {
        repo.leaveMatchQueue(playerId)
        _isSearching.value = false
    }

    override fun onCleared() {
        super.onCleared()

        // ViewModel temizlendiğinde sıradan çık ve listener'ları kapat
        viewModelScope.launch {
            try {
                leaveQueue()
            } catch (e: Exception) {
                // Temizlik sırasında hata oluşursa görmezden gel
            }
        }

        gameListener?.remove()
    }
}