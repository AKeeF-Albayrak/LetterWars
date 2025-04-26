package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

            Log.d("FirestoreQueue", "🎮 Oyun bulundu: $playerId vs $opponentId")
            Triple(true, opponentId, null)

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

    fun listenQueueUserCount(
        duration: GameDuration,
        onUpdate: (Int) -> Unit
    ): ListenerRegistration {
        val queueRef = firestore.collection("matchQueue")
            .document(duration.minutes.toString())

        return queueRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(0)
                return@addSnapshotListener
            }

            val count = snapshot?.data?.size ?: 0
            onUpdate(count)
        }
    }


}
