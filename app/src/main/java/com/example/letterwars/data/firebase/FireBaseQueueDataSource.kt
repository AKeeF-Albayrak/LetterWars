package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreGameDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun joinQueue(
        playerId: String,
        duration: GameDuration
    ): Triple<Boolean, String?, String?> {
        val queueRef = firestore.collection("matchQueue")
            .document(duration.minutes.toString())

        val snapshot = queueRef.get().await()

        val playersInQueue = snapshot.data?.keys?.filter { it != playerId } ?: emptyList()

        return if (playersInQueue.isNotEmpty()) {
            val opponentId = playersInQueue.first()

            // 🔥 Kuyruktan sil
            queueRef.update(
                mapOf(
                    opponentId to FieldValue.delete(),
                    playerId to FieldValue.delete()
                )
            ).await()

            val tempGameId = UUID.randomUUID().toString()

            // 📣 Logcat'e yaz
            Log.d("FirestoreQueue", "🎮 Oyun bulundu: $playerId vs $opponentId (gameId: $tempGameId)")

            Triple(true, opponentId, tempGameId)
        } else {
            val queueEntry = mapOf(playerId to true)
            queueRef.set(queueEntry, SetOptions.merge()).await()

            Log.d("FirestoreQueue", "🕒 Oyuncu sıraya eklendi: $playerId (${duration.minutes} dakika)")

            Triple(false, null, null)
        }
    }

    suspend fun leaveQueue(playerId: String, duration: GameDuration): Boolean {
        return try {
            val queueRef = firestore.collection("matchQueue")
                .document(duration.minutes.toString())
            queueRef.update(mapOf(playerId to com.google.firebase.firestore.FieldValue.delete())).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createGame(
        player1Id: String,
        player2Id: String,
        duration: GameDuration
    ): Pair<Boolean, String?> {
        return try {
            val gameId = UUID.randomUUID().toString()

            val allLetters = generateLetterPool()
            val player1Letters = drawLetters(allLetters, 7).toMutableList()
            val player2Letters = drawLetters(allLetters, 7).toMutableList()
            val board = generateEmptyBoard()
            val firstTurnPlayerId = if (Math.random() < 0.5) player1Id else player2Id

            val game = Game(
                gameId = gameId,
                player1Id = player1Id,
                player2Id = player2Id,
                currentTurnPlayerId = firstTurnPlayerId,
                duration = duration,
                board = board,
                remainingLetters = allLetters,
                player1Letters = player1Letters,
                player2Letters = player2Letters,
                startTimeMillis = System.currentTimeMillis()
            )

            firestore.collection("games").document(gameId).set(game).await()
            Pair(true, gameId)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }
}
