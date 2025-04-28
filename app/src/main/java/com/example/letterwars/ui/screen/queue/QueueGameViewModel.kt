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
        joinQueue()
        listenForGameMatch()
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
        val waitingGame = repo.findWaitingGame(gameDuration)

        if (waitingGame != null && waitingGame.player1Id != playerId) {
            val joined = repo.joinExistingGame(waitingGame, playerId)
            if (joined) {
                _gameId.value = waitingGame.gameId
                _isSearching.value = false
            } else {
                // Eğer başkası katıldıysa, tekrar beklemeye devam
                val myWaitingGameId = repo.createWaitingGame(playerId, gameDuration)
                if (myWaitingGameId != null) {
                    _isSearching.value = true
                    startCheckingForOtherGames()
                }
            }
        } else {
            val myWaitingGameId = repo.createWaitingGame(playerId, gameDuration)
            if (myWaitingGameId != null) {
                _isSearching.value = true
                startCheckingForOtherGames()
            }
        }
    }


    private fun startCheckingForOtherGames() {
        if (checkingJob) return
        checkingJob = true

        viewModelScope.launch {
            while (_isSearching.value) {
                delay(5000L)
                val waitingGame = repo.findWaitingGame(gameDuration)
                if (waitingGame != null && waitingGame.player1Id != playerId) {
                    repo.joinExistingGame(waitingGame, playerId)
                    repo.deleteOwnWaitingGame(playerId)
                    _gameId.value = waitingGame.gameId
                    _isSearching.value = false
                    break
                }
            }
        }
    }

    fun leaveQueue() = viewModelScope.launch {
        repo.leaveMatchQueue(playerId)
        _isSearching.value = false
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            leaveQueue()
        }
        gameListener?.remove()
    }
}
