package com.example.splitframe.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PreferenceEntity::class,
        ExportHistoryEntity::class,
        FavoriteTemplateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SplitFrameDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun favoriteTemplateDao(): FavoriteTemplateDao
}
