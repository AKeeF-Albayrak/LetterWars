// ActiveGameInfo.kt  —  yalnızca ekranın ihtiyaç duyduğu alanlar
package com.example.letterwars.data.model

data class ActiveGameInfo(
    val startedAt: Long,            // startTimeMillis
    val isYourTurn: Boolean,        // currentTurnPlayerId == uid
    val remainingTimeLabel: String  // expireTimeMillis - now
)
