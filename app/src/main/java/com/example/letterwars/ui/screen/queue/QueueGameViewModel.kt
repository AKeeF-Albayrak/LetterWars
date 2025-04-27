import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.GameDuration
import com.example.letterwars.data.repository.QueueRepository
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
    private var queueListener: ListenerRegistration? = null

    init {
        joinQueue()
        startListeningQueueUserCount()
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
        while (_isSearching.value) {
            repo.joinMatchQueue(playerId, gameDuration) { ready, gameId ->
                if (ready && gameId != null) {
                    _gameId.value = gameId
                    _isSearching.value = false
                }
            }
            delay(2000L)
        }
    }

    private fun startListeningQueueUserCount() {
        queueListener = repo.listenQueueUserCount(gameDuration) { count ->
            _queueUserCount.value = count
        }
    }

    fun leaveQueue() = viewModelScope.launch {
        repo.leaveMatchQueue(playerId, gameDuration) { /* ignore result */ }
        _isSearching.value = false
    }

    override fun onCleared() {
        viewModelScope.launch {
            leaveQueue()
        }
        queueListener?.remove()
        gameListener?.remove()
        super.onCleared()
    }
}
