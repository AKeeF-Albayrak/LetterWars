// ActiveGameInfo.kt  —  yalnızca ekranın ihtiyaç duyduğu alanlar
package com.example.letterwars.data.model
// data/model/ActiveGameInfo.kt
data class ActiveGameInfo(
    val startedAt: Long,
    val isYourTurn: Boolean,
    val remainingTimeLabel: String,
    val gameId: String            //  NEW
)

