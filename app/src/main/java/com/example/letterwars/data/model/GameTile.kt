package com.example.letterwars.data.model

import com.example.letterwars.data.model.MineType
import com.example.letterwars.data.model.Multiplier
import com.example.letterwars.data.model.RewardType

data class GameTile(
    val letter: String? = null, // 🔄 Char → String
    val multiplier: Multiplier? = null,
    val mineType: MineType? = null,
    val rewardType: RewardType? = null,
    val triggeredBy: String? = null,
    val collectedBy: String? = null
)

