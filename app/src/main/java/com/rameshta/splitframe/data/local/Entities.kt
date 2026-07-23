package com.rameshta.splitframe.data.local

import androidx.room.Entity
import androidx.room.Index
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

@Entity(tableName = "recent_layouts")
data class RecentLayoutEntity(
    @PrimaryKey val templateId: String,
    val usedAtMillis: Long,
)

@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey val id: String,
    val layout: String,
    val canvasAspectRatio: String,
    val exportResolution: String,
    val primaryAudioSource: String,
    val durationMode: String,
    val spacingDp: Float,
    val cornerRadiusDp: Float,
    val backgroundColor: Long,
    val updatedAtMillis: Long,
    val clip0: String?,
    val clip1: String?,
    val templateId: String?,
    val selectedCellIndex: Int?,
    val primaryAudioMediaId: String?,
    val mediaItems: String?,
    val mergeMode: String,
)

@Entity(tableName = "video_export_work")
data class VideoExportWorkEntity(
    @PrimaryKey val projectId: String,
    val workId: String?,
    val state: String,
    val progress: Float,
    val outputUri: String?,
    val errorMessage: String?,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "recent_projects",
    indices = [Index(value = ["deletedAtMillis", "updatedAtMillis"])],
)
data class RecentProjectEntity(
    @PrimaryKey val projectId: String,
    val projectType: String,
    val name: String,
    val projectFormatVersion: Int,
    val layoutVersion: Int,
    val thumbnailUri: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long?,
    val deletionToken: String?,
)
