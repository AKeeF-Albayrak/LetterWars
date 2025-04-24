package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FireBaseGameDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun createGame(
        player1Id: String,
        player2Id: String,
        duration: GameDuration
    ): Pair<Boolean, String?> {
        return try {
            val gameId = UUID.randomUUID().toString()

            val allLetters = generateLetterPool() // örnek: Map<Char, Int>
            val stringKeyedLetters = allLetters.mapKeys { it.key.toString() } // ✅ Map<String, Int>

            val player1Letters = drawLetters(allLetters, 7).map { it.toString() }.toMutableList()
            val player2Letters = drawLetters(allLetters, 7).map { it.toString() }.toMutableList()
            val board = generateEmptyBoard()
            val firstTurnPlayerId = if (Math.random() < 0.5) player1Id else player2Id

            val game = Game(
                gameId = gameId,
                player1Id = player1Id,
                player2Id = player2Id,
                currentTurnPlayerId = firstTurnPlayerId,
                duration = duration,
                board = board,
                remainingLetters = stringKeyedLetters,
                player1Letters = player1Letters,
                player2Letters = player2Letters,
                startTimeMillis = System.currentTimeMillis()
            )

            firestore.collection("games").document(gameId).set(game).await()
            Pair(true, gameId)
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ createGame hatası: ${e.message}")
            Pair(false, null)
        }
    }
}
