package com.rameshta.splitframe.export

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.UUID

internal enum class ExportPublicationPhase {
    Prepared,
    Writing,
    ReadyToPublish,
    Published,
}

internal data class ExportPublicationEntry(
    val journalId: String,
    val collectionUri: String,
    val displayName: String,
    val uri: String?,
    val phase: ExportPublicationPhase,
    val cacheFilePath: String?,
    val ownerProcessId: String = "",
)

internal interface ExportPublicationJournal {
    val currentProcessId: String
    fun entries(): List<ExportPublicationEntry>
    fun prepare(collectionUri: String, displayName: String, cacheFile: File? = null): String
    fun recordWriting(journalId: String, uri: String)
    fun markReadyToPublish(journalId: String)
    fun markPublished(journalId: String)
    fun remove(journalId: String)
}

internal class SharedPreferencesExportPublicationJournal(
    context: Context,
) : ExportPublicationJournal {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val lock = Any()
    override val currentProcessId: String = UUID.randomUUID().toString()

    override fun entries(): List<ExportPublicationEntry> = synchronized(lock) {
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(EntryPrefix)) return@mapNotNull null
            decodeEntry(value as? String ?: return@mapNotNull null)
        }
    }

    override fun prepare(collectionUri: String, displayName: String, cacheFile: File?): String {
        val journalId = UUID.randomUUID().toString()
        put(
            ExportPublicationEntry(
                journalId = journalId,
                collectionUri = collectionUri,
                displayName = displayName,
                uri = null,
                phase = ExportPublicationPhase.Prepared,
                cacheFilePath = cacheFile?.absolutePath,
                ownerProcessId = currentProcessId,
            ),
        )
        return journalId
    }

    override fun recordWriting(journalId: String, uri: String) {
        update(journalId) { it.copy(uri = uri, phase = ExportPublicationPhase.Writing) }
    }

    override fun markReadyToPublish(journalId: String) =
        updatePhase(journalId, ExportPublicationPhase.ReadyToPublish)

    override fun markPublished(journalId: String) = updatePhase(journalId, ExportPublicationPhase.Published)

    override fun remove(journalId: String) {
        synchronized(lock) {
            check(preferences.edit().remove(key(journalId)).commit()) {
                "Could not clear durable export recovery state."
            }
        }
    }

    private fun updatePhase(journalId: String, phase: ExportPublicationPhase) {
        update(journalId) { it.copy(phase = phase) }
    }

    private fun update(journalId: String, transform: (ExportPublicationEntry) -> ExportPublicationEntry) {
        synchronized(lock) {
            val current = decodeEntry(preferences.getString(key(journalId), null))
                ?: error("Durable export recovery state is missing.")
            putLocked(transform(current))
        }
    }

    private fun put(entry: ExportPublicationEntry) {
        synchronized(lock) {
            putLocked(entry)
        }
    }

    private fun putLocked(entry: ExportPublicationEntry) {
        check(preferences.edit().putString(key(entry.journalId), encodeEntry(entry)).commit()) {
            "Could not persist durable export recovery state."
        }
    }

    private companion object {
        const val PreferencesName = "export_publication_recovery"
        const val EntryPrefix = "publication:"

        fun key(journalId: String): String = EntryPrefix + journalId

        fun encodeEntry(entry: ExportPublicationEntry): String = listOf(
            entry.journalId,
            Uri.encode(entry.collectionUri),
            Uri.encode(entry.displayName),
            Uri.encode(entry.uri.orEmpty()),
            entry.phase.name,
            Uri.encode(entry.cacheFilePath.orEmpty()),
            Uri.encode(entry.ownerProcessId),
        ).joinToString("|")

        fun decodeEntry(value: String?): ExportPublicationEntry? {
            val fields = value?.split('|') ?: return null
            if (fields.size != 7) return null
            val journalId = fields[0].takeIf(String::isNotBlank) ?: return null
            val collectionUri = Uri.decode(fields[1]).takeIf(String::isNotBlank) ?: return null
            val displayName = Uri.decode(fields[2]).takeIf(String::isNotBlank) ?: return null
            val phase = runCatching { ExportPublicationPhase.valueOf(fields[4]) }.getOrNull()
                ?: return null
            return ExportPublicationEntry(
                journalId = journalId,
                collectionUri = collectionUri,
                displayName = displayName,
                uri = Uri.decode(fields[3]).takeIf(String::isNotBlank),
                phase = phase,
                cacheFilePath = Uri.decode(fields[5]).takeIf(String::isNotBlank),
                ownerProcessId = Uri.decode(fields[6]).takeIf(String::isNotBlank) ?: return null,
            )
        }
    }
}

internal enum class OwnedPublicationState {
    Missing,
    Pending,
    Published,
}

internal interface OwnedPublicationAccess {
    fun find(collectionUri: String, displayName: String): String?
    fun state(uri: String): OwnedPublicationState
    fun delete(uri: String): Boolean
}

internal class ContentResolverOwnedPublicationAccess(
    private val resolver: ContentResolver,
    private val apiLevel: Int = Build.VERSION.SDK_INT,
) : OwnedPublicationAccess {
    override fun find(collectionUri: String, displayName: String): String? {
        val collection = Uri.parse(collectionUri)
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
            }
        }
    }

    override fun state(uri: String): OwnedPublicationState {
        val parsedUri = Uri.parse(uri)
        if (apiLevel < Build.VERSION_CODES.Q) {
            return if (exists(parsedUri)) OwnedPublicationState.Published else OwnedPublicationState.Missing
        }
        return resolver.query(
            parsedUri,
            arrayOf(MediaStore.MediaColumns.IS_PENDING),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                OwnedPublicationState.Missing
            } else {
                val column = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
                if (cursor.getInt(column) == 1) {
                    OwnedPublicationState.Pending
                } else {
                    OwnedPublicationState.Published
                }
            }
        } ?: OwnedPublicationState.Missing
    }

    override fun delete(uri: String): Boolean = resolver.delete(Uri.parse(uri), null, null) > 0

    private fun exists(uri: Uri): Boolean =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { cursor -> cursor.moveToFirst() }
            ?: false
}

internal data class ExportReconciliationResult(
    val removedIncomplete: Int,
    val retainedPublished: Int,
    val retryableFailures: Int,
)

internal class ExportPublicationReconciler(
    private val journal: ExportPublicationJournal,
    private val publicationAccess: OwnedPublicationAccess,
    private val videoExportDirectory: File,
) {
    fun reconcile(): ExportReconciliationResult {
        var removedIncomplete = 0
        var retainedPublished = 0
        var retryableFailures = 0
        journal.entries().forEach { entry ->
            if (entry.ownerProcessId == journal.currentProcessId) return@forEach
            val uri = entry.uri ?: try {
                publicationAccess.find(entry.collectionUri, entry.displayName)
            } catch (_: Throwable) {
                retryableFailures++
                return@forEach
            }
            if (uri == null) {
                try {
                    deleteOwnedCacheFile(entry.cacheFilePath)
                    journal.remove(entry.journalId)
                } catch (_: Throwable) {
                    retryableFailures++
                }
                return@forEach
            }
            if (!uri.startsWith("content://")) {
                retryableFailures++
                return@forEach
            }
            try {
                val action = if (entry.phase == ExportPublicationPhase.Published) {
                    ExportRecoveryAction.RetainPublished
                } else {
                    recoveryAction(entry.phase, publicationAccess.state(uri))
                }
                when (action) {
                    ExportRecoveryAction.DeleteIncomplete -> {
                        val deleted = publicationAccess.delete(uri)
                        if (deleted || publicationAccess.state(uri) == OwnedPublicationState.Missing) {
                            deleteOwnedCacheFile(entry.cacheFilePath)
                            journal.remove(entry.journalId)
                            removedIncomplete++
                        } else {
                            retryableFailures++
                        }
                    }
                    ExportRecoveryAction.RetainPublished -> {
                        deleteOwnedCacheFile(entry.cacheFilePath)
                        journal.remove(entry.journalId)
                        retainedPublished++
                    }
                    ExportRecoveryAction.ClearMissing -> {
                        deleteOwnedCacheFile(entry.cacheFilePath)
                        journal.remove(entry.journalId)
                    }
                }
            } catch (_: Throwable) {
                retryableFailures++
            }
        }
        return ExportReconciliationResult(removedIncomplete, retainedPublished, retryableFailures)
    }

    private fun deleteOwnedCacheFile(path: String?) {
        val file = path?.let(::File) ?: return
        val expectedParent = videoExportDirectory.canonicalFile
        val candidate = file.canonicalFile
        if (candidate.parentFile == expectedParent && candidate.extension.equals("mp4", ignoreCase = true)) {
            if (candidate.exists()) check(candidate.delete()) { "Could not remove incomplete video cache file." }
        }
    }
}

internal enum class ExportRecoveryAction {
    DeleteIncomplete,
    RetainPublished,
    ClearMissing,
}

internal fun recoveryAction(
    phase: ExportPublicationPhase,
    state: OwnedPublicationState,
): ExportRecoveryAction = when {
    state == OwnedPublicationState.Missing -> ExportRecoveryAction.ClearMissing
    phase == ExportPublicationPhase.Published -> ExportRecoveryAction.RetainPublished
    phase == ExportPublicationPhase.ReadyToPublish && state == OwnedPublicationState.Published ->
        ExportRecoveryAction.RetainPublished
    else -> ExportRecoveryAction.DeleteIncomplete
}
