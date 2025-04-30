import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameDuration
import com.example.letterwars.data.model.GameStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class QueueRepository(
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()
) {

    suspend fun findWaitingGame(duration: GameDuration): Game? {
        return gameDataSource.findWaitingGame(duration)
    }

    suspend fun createWaitingGame(playerId: String, duration: GameDuration): String? {
        // Önce mevcut bekleyen oyunları temizle
        deleteOwnWaitingGame(playerId)
        return gameDataSource.createWaitingGame(playerId, duration)
    }

    suspend fun joinExistingGame(game: Game, player2Id: String): Boolean {
        // Önce kendi bekleyen oyunlarımızı temizleyelim
        deleteOwnWaitingGame(player2Id)
        return gameDataSource.tryJoinWaitingGame(game.gameId, player2Id)
    }

    suspend fun findWaitingGameForPlayer(playerId: String): Game? {
        return gameDataSource.findWaitingGameForPlayer(playerId)
    }

    suspend fun deleteOwnWaitingGame(playerId: String) {
        val myWaitingGame = gameDataSource.findWaitingGameForPlayer(playerId)
        if (myWaitingGame != null) {
            gameDataSource.deleteGame(myWaitingGame.gameId)
        }
    }

    suspend fun leaveMatchQueue(playerId: String) {
        deleteOwnWaitingGame(playerId)
    }

    fun listenForGameForPlayer(
        playerId: String,
        onGameFound: (String?) -> Unit
    ): ListenerRegistration {
        return gameDataSource.listenForGameForPlayer(playerId) { gameId ->
            onGameFound(gameId)
        }
    }

    suspend fun getQueueUserCount(duration: GameDuration): Int {
        // Sıradaki kullanıcı sayısını almak için Firestore'a sorgu yap
        return try {
            val snapshot = FirebaseFirestore.getInstance().collection("games")
                .whereEqualTo("status", GameStatus.WAITING_FOR_PLAYER.name)
                .whereEqualTo("duration", duration)
                .get()
                .await()

            snapshot.documents.size
        } catch (e: Exception) {
            0
        }
    }

    suspend fun findActiveGamesForPlayer(playerId: String): List<Game> {
        return try {
            val snapshot = FirebaseFirestore.getInstance().collection("games")
                .whereIn("status", listOf(GameStatus.IN_PROGRESS.name, GameStatus.WAITING_FOR_PLAYER.name))
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val game = doc.toObject(Game::class.java)
                if (game != null && (game.player1Id == playerId || game.player2Id == playerId)) {
                    game
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}