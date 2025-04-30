import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameDuration
import com.example.letterwars.data.model.GameStatus
import com.google.firebase.firestore.ListenerRegistration

class QueueRepository(
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()
) {

    suspend fun findWaitingGame(duration: GameDuration) =
        gameDataSource.findWaitingGame(duration)

    suspend fun createWaitingGame(playerId: String, duration: GameDuration): String? =
        gameDataSource.createWaitingGame(playerId, duration)

    suspend fun joinExistingGame(game: Game, player2Id: String): Boolean {
        return gameDataSource.tryJoinWaitingGame(game.gameId, player2Id)
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
        return gameDataSource.listenForGameForPlayer(playerId, onGameFound)
    }
}
