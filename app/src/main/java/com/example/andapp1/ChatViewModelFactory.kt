package com.example.andapp1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context

class ChatViewModelFactory(
    private val roomCode: String,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(roomCode, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}