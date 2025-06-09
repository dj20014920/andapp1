//AppDatabase.kt
package com.example.andapp1

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.andapp1.RoomDao
import com.example.andapp1.RoomEntity
import com.example.andapp1.expense.ExpenseItem
import com.example.andapp1.expense.ExpenseDao
import com.example.andapp1.expense.DateConverters

@Database(
    entities = [RoomEntity::class, UserEntity::class, ExpenseItem::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun userDao(): UserDao
    abstract fun expenseDao(): ExpenseDao
}