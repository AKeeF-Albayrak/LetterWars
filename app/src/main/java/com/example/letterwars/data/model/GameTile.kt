package com.example.letterwars.data.model


data class GameTile(
    val letter: String? = null,
    val multiplier: Multiplier? = null,
    val mineType: MineType? = null,
    val rewardType: RewardType? = null,
    val triggeredBy: String? = null,
    val collectedBy: String? = null,
    val cellType: CellType = CellType.NORMAL
)



