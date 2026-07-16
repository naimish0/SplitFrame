package com.example.splitframe.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<PreferenceEntity?>

    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: PreferenceEntity)
}

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ExportHistoryEntity)
}

@Dao
interface FavoriteTemplateDao {
    @Query("SELECT * FROM favorite_templates ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<FavoriteTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteTemplateEntity)

    @Query("DELETE FROM favorite_templates WHERE templateId = :templateId")
    suspend fun remove(templateId: String)
}
