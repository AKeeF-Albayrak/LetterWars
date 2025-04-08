package com.example.letterwars.data.repository

import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

class GameRepository {

    private val dbRef = FirebaseDatabase.getInstance().reference

    fun createGame(
        player1Id: String,
        player2Id: String,
        duration: GameDuration,
        onComplete: (Boolean, String?) -> Unit
    ) {
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

        dbRef.child("games").child(gameId)
            .setValue(game)
            .addOnSuccessListener {
                onComplete(true, gameId)
            }
            .addOnFailureListener {
                onComplete(false, null)
            }
    }


    fun joinMatchQueue(
        playerId: String,
        duration: GameDuration,
        onGameReady: (Boolean, String?) -> Unit
    ) {
        val dbRef = FirebaseDatabase.getInstance().reference
        val queueRef = dbRef.child("matchQueue").child(duration.name)

        queueRef.get().addOnSuccessListener { snapshot ->
            val otherPlayerEntry = snapshot.children.firstOrNull { it.key != playerId }

            if (otherPlayerEntry != null) {
                val otherPlayerId = otherPlayerEntry.key!!

                queueRef.child(playerId).removeValue()
                queueRef.child(otherPlayerId).removeValue()

                createGame(player1Id = otherPlayerId, player2Id = playerId, duration = duration) { success, gameId ->
                    onGameReady(success, gameId)
                }
            } else {
                queueRef.child(playerId).setValue(System.currentTimeMillis())
                    .addOnSuccessListener {
                        onGameReady(false, null)
                    }
                    .addOnFailureListener {
                        onGameReady(false, null)
                    }
            }
        }
    }

}
