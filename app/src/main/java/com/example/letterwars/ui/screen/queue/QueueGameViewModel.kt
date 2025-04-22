package com.example.letterwars.ui.screen.queue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class QueueViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    // Get game duration from navigation arguments (default to 5 minutes if not provided)
    val gameDuration: Long = savedStateHandle.get<String>("duration")?.toLongOrNull() ?: 5
}