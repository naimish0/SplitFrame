package com.rameshta.splitframe.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.splitframe.data.local.SplitFrameDatabase
import com.rameshta.splitframe.data.local.VideoExportWorkEntity
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoMergeProject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentProjectDatabaseTest {
    private lateinit var database: SplitFrameDatabase
    private var now = 1_000L

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SplitFrameDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun renameDuplicateAndUpdatedOrderingRemainIndependent() = runBlocking {
        val videoStore = videoStore()
        val recentStore = recentStore(videoStore, ids = ArrayDeque(listOf(DuplicateId)))
        videoStore.save(project(ProjectId), initialName = "Holiday")
        now = 2_000L
        videoStore.save(project(SecondProjectId), initialName = "Second")

        assertEquals(SecondProjectId, database.recentProjectDao().observeActive().first().first().projectId)

        now = 3_000L
        assertTrue(recentStore.rename(ProjectId, "  Holiday   final  "))
        assertEquals("Holiday final", database.recentProjectDao().get(ProjectId)?.name)
        assertEquals(ProjectId, database.recentProjectDao().observeActive().first().first().projectId)

        now = 4_000L
        assertEquals(DuplicateId, recentStore.duplicate(ProjectId))
        assertEquals("Holiday final copy", database.recentProjectDao().get(DuplicateId)?.name)
        assertNotNull(videoStore.get(DuplicateId))
        assertNotNull(videoStore.get(ProjectId))
    }

    @Test
    fun deleteUndoAndFinalizationAreTokenGuardedAndPreventResurrection() = runBlocking {
        val videoStore = videoStore()
        val recentStore = recentStore(videoStore, ids = ArrayDeque(listOf(DeleteToken, SecondDeleteToken)))
        val source = project(ProjectId)
        videoStore.save(source, initialName = "Draft")

        val firstDelete = recentStore.delete(ProjectId) as DeleteVideoProjectResult.Deleted
        assertTrue(database.recentProjectDao().observeActive().first().isEmpty())
        assertFalse(videoStore.save(source.copy(exportResolution = ExportResolution.UHD_2160)))
        assertNull(videoStore.get(ProjectId))
        assertEquals("FHD_1080", database.videoProjectDao().get(ProjectId)?.exportResolution)
        assertFalse(
            recentStore.undoDelete(
                firstDelete.deletion.copy(deletionToken = "wrong-token"),
            ),
        )
        assertTrue(recentStore.undoDelete(firstDelete.deletion))

        val secondDelete = recentStore.delete(ProjectId) as DeleteVideoProjectResult.Deleted
        assertTrue(recentStore.finalizeDelete(secondDelete.deletion))
        assertNull(database.recentProjectDao().get(ProjectId))
        assertNull(videoStore.get(ProjectId))
        assertFalse(recentStore.finalizeDelete(firstDelete.deletion))
    }

    @Test
    fun activeExportBlocksDeletion() = runBlocking {
        val videoStore = videoStore()
        val recentStore = recentStore(videoStore, ids = ArrayDeque(listOf(DeleteToken)))
        videoStore.save(project(ProjectId))
        database.videoExportWorkDao().upsert(
            VideoExportWorkEntity(
                projectId = ProjectId,
                workId = WorkId,
                state = "running",
                progress = 0.5f,
                outputUri = null,
                errorMessage = null,
                updatedAtMillis = now,
            ),
        )

        assertEquals(DeleteVideoProjectResult.ExportActive, recentStore.delete(ProjectId))
        assertNotNull(database.recentProjectDao().get(ProjectId)?.takeIf { it.deletedAtMillis == null })
    }

    @Test
    fun emptyMissingAndCorruptProjectsHaveDistinctStates() = runBlocking {
        val videoStore = videoStore()
        val access = MediaUriAccess { uri -> uri.endsWith("/1") }
        val recentStore = recentStore(videoStore, access = access)
        videoStore.save(VideoMergeProject(id = EmptyProjectId), initialName = "Empty")
        videoStore.save(project(ProjectId), initialName = "Missing")

        val initial = recentStore.observeProjects().first { it.size == 2 }.associateBy { it.id }
        assertEquals(RecentVideoProjectStatus.Empty, initial.getValue(EmptyProjectId).status)
        assertEquals(RecentVideoProjectStatus.MissingMedia, initial.getValue(ProjectId).status)
        assertEquals(1, initial.getValue(ProjectId).missingMediaCount)

        val persisted = requireNotNull(database.videoProjectDao().get(ProjectId))
        database.videoProjectDao().upsert(persisted.copy(mediaItems = "broken"))

        val corrupt = recentStore.observeProjects().first()
            .first { it.id == ProjectId }
        assertEquals(RecentVideoProjectStatus.Corrupt, corrupt.status)
    }

    private fun videoStore(): VideoProjectStore =
        VideoProjectStore(
            projectDao = database.videoProjectDao(),
            exportWorkDao = database.videoExportWorkDao(),
            recentProjectDao = database.recentProjectDao(),
            clock = { now },
        )

    private fun recentStore(
        videoStore: VideoProjectStore,
        access: MediaUriAccess = MediaUriAccess { true },
        ids: ArrayDeque<String> = ArrayDeque(),
    ): RecentVideoProjectStore =
        RecentVideoProjectStore(
            recentProjectDao = database.recentProjectDao(),
            videoProjectStore = videoStore,
            mediaUriAccess = access,
            clock = { now },
            idFactory = { ids.removeFirst() },
        )

    private fun project(projectId: String): VideoMergeProject =
        VideoMergeProject(
            id = projectId,
            mediaByCell = mapOf(
                0 to video("1"),
                1 to video("2"),
            ),
            selectedCellIndex = 0,
        )

    private fun video(id: String): MediaSource.Video =
        MediaSource.Video(
            VideoClip(
                id = "clip-$id",
                uri = "content://media/video/$id",
                durationMs = 5_000L,
                width = 1_920,
                height = 1_080,
                rotationDegrees = 0,
            ),
        )

    private companion object {
        const val ProjectId = "11111111-1111-4111-8111-111111111111"
        const val SecondProjectId = "22222222-2222-4222-8222-222222222222"
        const val DuplicateId = "33333333-3333-4333-8333-333333333333"
        const val EmptyProjectId = "44444444-4444-4444-8444-444444444444"
        const val DeleteToken = "delete-token-one"
        const val SecondDeleteToken = "delete-token-two"
        const val WorkId = "55555555-5555-4555-8555-555555555555"
    }
}
