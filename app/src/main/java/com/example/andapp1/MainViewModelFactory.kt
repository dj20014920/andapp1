package com.example.andapp1

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = RoomDatabaseInstance.getInstance(context)
        val repository = RoomRepository(db) // ✅ AppDatabase 넘겨줌
        return MainViewModel(repository, context) as T
    }
}