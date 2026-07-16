package com.example.splitframe.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val savedUri: String,
    val resolution: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "favorite_templates")
data class FavoriteTemplateEntity(
    @PrimaryKey val templateId: String,
    val createdAtMillis: Long,
)
