package com.example.letterwars.data.model

data class Tile(
    val letter: Char? = null,
    val multiplier: Multiplier? = null,
    val mineType: MineType? = null,
    val rewardType: RewardType? = null,
    val triggeredBy: String? = null,
    val collectedBy: String? = null
)