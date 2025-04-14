package com.example.andapp1
// ✅ 완료

import android.content.Context
import androidx.room.Room
import com.example.andapp1.AppDatabase // ✅ 꼭 명확하게!

object RoomDatabaseInstance {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "favorite_rooms_db"
            ).fallbackToDestructiveMigration().build()
            INSTANCE = instance
            instance
        }
    }
}