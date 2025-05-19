//AppDatabase.kt
package com.example.andapp1

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.andapp1.RoomDao
import com.example.andapp1.RoomEntity

@Database(
    entities = [RoomEntity::class, UserEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun userDao(): UserDao

}