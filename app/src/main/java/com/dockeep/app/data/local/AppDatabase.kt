package com.dockeep.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dockeep.app.domain.DefaultCategory

@Database(
    entities = [CategoryEntity::class, DocumentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun documentDao(): DocumentDao

    companion object {
        const val NAME = "dockeep.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        fun defaultCategories(): List<CategoryEntity> =
            DefaultCategory.entries.map {
                CategoryEntity(
                    id = it.id,
                    name = it.displayName,
                    emoji = it.emoji,
                    folderName = it.folderName,
                    sortOrder = it.group.sortOrder * 100 + it.sortOrder
                )
            }
    }
}
