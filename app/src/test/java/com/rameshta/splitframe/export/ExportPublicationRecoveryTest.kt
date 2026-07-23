package com.rameshta.splitframe.export

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPublicationRecoveryTest {
    @Test
    fun `writing row is deleted and exact journal entry is cleared`() {
        val fixture = fixture(
            entry(ImageUri, ExportPublicationPhase.Writing),
            state = OwnedPublicationState.Pending,
        )

        val result = fixture.reconciler.reconcile()

        assertEquals(listOf(ImageUri), fixture.access.deleted)
        assertTrue(fixture.journal.saved.isEmpty())
        assertEquals(1, result.removedIncomplete)
    }

    @Test
    fun `ready pending row is deleted but ready published row is retained`() {
        val pending = fixture(
            entry(ImageUri, ExportPublicationPhase.ReadyToPublish),
            state = OwnedPublicationState.Pending,
        )
        pending.reconciler.reconcile()
        assertEquals(listOf(ImageUri), pending.access.deleted)

        val published = fixture(
            entry(ImageUri, ExportPublicationPhase.ReadyToPublish),
            state = OwnedPublicationState.Published,
        )
        val result = published.reconciler.reconcile()
        assertTrue(published.access.deleted.isEmpty())
        assertTrue(published.journal.saved.isEmpty())
        assertEquals(1, result.retainedPublished)
    }

    @Test
    fun `published row is retained and repeated reconciliation is idempotent`() {
        val fixture = fixture(
            entry(VideoUri, ExportPublicationPhase.Published),
            state = OwnedPublicationState.Published,
        )

        assertEquals(1, fixture.reconciler.reconcile().retainedPublished)
        assertEquals(0, fixture.reconciler.reconcile().retainedPublished)
        assertTrue(fixture.access.deleted.isEmpty())
    }

    @Test
    fun `current process publication is not reconciled`() {
        val fixture = fixture(
            entry(ImageUri, ExportPublicationPhase.Writing, ownerProcessId = CurrentProcess),
            state = OwnedPublicationState.Pending,
        )

        val result = fixture.reconciler.reconcile()

        assertTrue(fixture.access.deleted.isEmpty())
        assertEquals(1, fixture.journal.saved.size)
        assertEquals(ExportReconciliationResult(0, 0, 0), result)
    }

    @Test
    fun `provider delete failure remains journaled for retry`() {
        val fixture = fixture(
            entry(ImageUri, ExportPublicationPhase.Writing),
            state = OwnedPublicationState.Pending,
            deleteSucceeds = false,
        )

        val result = fixture.reconciler.reconcile()

        assertEquals(1, result.retryableFailures)
        assertEquals(1, fixture.journal.saved.size)
    }

    @Test
    fun `missing row clears journal without delete`() {
        val fixture = fixture(
            entry(ImageUri, ExportPublicationPhase.Writing),
            state = OwnedPublicationState.Missing,
        )

        fixture.reconciler.reconcile()

        assertTrue(fixture.access.deleted.isEmpty())
        assertTrue(fixture.journal.saved.isEmpty())
    }

    @Test
    fun `pre-insert reservation finds and deletes exact named row`() {
        val reserved = entry(null, ExportPublicationPhase.Prepared)
        val fixture = fixture(
            reserved,
            state = OwnedPublicationState.Pending,
            foundUri = ImageUri,
        )

        fixture.reconciler.reconcile()

        assertEquals(listOf(ImageUri), fixture.access.deleted)
        assertEquals(listOf(CollectionUri to DisplayName), fixture.access.finds)
        assertTrue(fixture.journal.saved.isEmpty())
    }

    @Test
    fun `recovery deletes only journaled mp4 inside owned cache directory`() {
        val root = Files.createTempDirectory("splitframe-recovery").toFile()
        val ownedDir = File(root, "video_exports").apply { mkdirs() }
        val owned = File(ownedDir, "owned.mp4").apply { writeBytes(byteArrayOf(1)) }
        val outside = File(root, "outside.mp4").apply { writeBytes(byteArrayOf(2)) }
        try {
            val fixture = fixture(
                entry(VideoUri, ExportPublicationPhase.Writing, owned.absolutePath),
                state = OwnedPublicationState.Pending,
                videoDirectory = ownedDir,
            )
            fixture.reconciler.reconcile()
            assertFalse(owned.exists())

            val outsideFixture = fixture(
                entry(VideoUri, ExportPublicationPhase.Writing, outside.absolutePath),
                state = OwnedPublicationState.Pending,
                videoDirectory = ownedDir,
            )
            outsideFixture.reconciler.reconcile()
            assertTrue(outside.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fixture(
        entry: ExportPublicationEntry,
        state: OwnedPublicationState,
        deleteSucceeds: Boolean = true,
        foundUri: String? = null,
        videoDirectory: File = Files.createTempDirectory("splitframe-video-cache").toFile(),
    ): Fixture {
        val journal = FakeJournal(mutableListOf(entry))
        val access = FakeAccess(state, deleteSucceeds, foundUri)
        return Fixture(
            journal,
            access,
            ExportPublicationReconciler(journal, access, videoDirectory),
        )
    }

    private data class Fixture(
        val journal: FakeJournal,
        val access: FakeAccess,
        val reconciler: ExportPublicationReconciler,
    )

    private class FakeJournal(
        val saved: MutableList<ExportPublicationEntry>,
    ) : ExportPublicationJournal {
        override val currentProcessId: String = CurrentProcess
        override fun entries(): List<ExportPublicationEntry> = saved.toList()
        override fun prepare(collectionUri: String, displayName: String, cacheFile: File?): String = JournalId
        override fun recordWriting(journalId: String, uri: String) = Unit
        override fun markReadyToPublish(journalId: String) = Unit
        override fun markPublished(journalId: String) = Unit
        override fun remove(journalId: String) {
            saved.removeAll { it.journalId == journalId }
        }
    }

    private class FakeAccess(
        private var currentState: OwnedPublicationState,
        private val deleteSucceeds: Boolean,
        private val foundUri: String?,
    ) : OwnedPublicationAccess {
        val deleted = mutableListOf<String>()
        val finds = mutableListOf<Pair<String, String>>()

        override fun find(collectionUri: String, displayName: String): String? {
            finds += collectionUri to displayName
            return foundUri
        }

        override fun state(uri: String): OwnedPublicationState = currentState

        override fun delete(uri: String): Boolean {
            deleted += uri
            if (deleteSucceeds) currentState = OwnedPublicationState.Missing
            return deleteSucceeds
        }
    }

    private fun entry(
        uri: String?,
        phase: ExportPublicationPhase,
        cacheFilePath: String? = null,
        ownerProcessId: String = "previous-process",
    ) = ExportPublicationEntry(
        journalId = JournalId,
        collectionUri = CollectionUri,
        displayName = DisplayName,
        uri = uri,
        phase = phase,
        cacheFilePath = cacheFilePath,
        ownerProcessId = ownerProcessId,
    )

    private companion object {
        const val JournalId = "journal-id"
        const val CollectionUri = "content://media/images"
        const val DisplayName = "SplitFrame_unique.jpg"
        const val ImageUri = "content://media/images/41"
        const val VideoUri = "content://media/videos/82"
        const val CurrentProcess = "current-process"
    }
}
