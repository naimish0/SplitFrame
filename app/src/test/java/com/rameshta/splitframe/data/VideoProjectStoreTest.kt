package com.rameshta.splitframe.data

import com.rameshta.splitframe.data.local.VideoExportWorkDao
import com.rameshta.splitframe.data.local.VideoExportWorkEntity
import com.rameshta.splitframe.data.local.VideoProjectDao
import com.rameshta.splitframe.data.local.VideoProjectEntity
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.MediaSource
import com.rameshta.splitframe.domain.VideoClip
import com.rameshta.splitframe.domain.VideoMergeProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoProjectStoreTest {
    @Test
    fun explicitNewSessionRemainsTransientUntilItHasEdits() = runBlocking {
        val projectDao = FakeVideoProjectDao()
        val store = VideoProjectStore(projectDao, FakeVideoExportWorkDao())

        val created = store.openProject(FirstProjectId, createIfMissing = true)
        val restored = store.openProject(FirstProjectId, createIfMissing = false)

        assertEquals(FirstProjectId, created?.id)
        assertNull(restored)
        assertEquals(0, projectDao.entities.size)
        assertEquals(0, projectDao.upsertCount)
    }

    @Test
    fun restoreOnlyMissingProjectReturnsNullWithoutWriting() = runBlocking {
        val projectDao = FakeVideoProjectDao()
        val store = VideoProjectStore(projectDao, FakeVideoExportWorkDao())

        val restored = store.openProject(FirstProjectId, createIfMissing = false)

        assertNull(restored)
        assertEquals(0, projectDao.upsertCount)
        assertEquals(0, projectDao.entities.size)
    }

    @Test
    fun differentProjectIdsRemainIsolatedAndDeletedIdIsNotResurrected() = runBlocking {
        val projectDao = FakeVideoProjectDao()
        val store = VideoProjectStore(projectDao, FakeVideoExportWorkDao())
        val first = requireNotNull(store.openProject(FirstProjectId, createIfMissing = true))
        val second = requireNotNull(store.openProject(SecondProjectId, createIfMissing = true))
        store.save(projectWithVideo(first.id).copy(exportResolution = ExportResolution.UHD_2160))
        store.save(projectWithVideo(second.id))

        assertEquals(ExportResolution.UHD_2160, store.get(FirstProjectId)?.exportResolution)
        assertNotNull(store.get(SecondProjectId))

        projectDao.delete(FirstProjectId)

        assertNull(store.openProject(FirstProjectId, createIfMissing = false))
        assertNotNull(store.get(SecondProjectId))
        assertEquals(setOf(SecondProjectId), projectDao.entities.keys)
    }

    @Test
    fun malformedMediaPayloadIsReportedAsCorruptWithoutBeingRewritten() = runBlocking {
        val projectDao = FakeVideoProjectDao()
        val store = VideoProjectStore(projectDao, FakeVideoExportWorkDao())
        store.save(projectWithVideo(FirstProjectId))
        val corrupt = requireNotNull(projectDao.entities[FirstProjectId]).copy(
            mediaItems = "not-a-valid-media-row",
        )
        projectDao.entities[FirstProjectId] = corrupt
        val writesBeforeInspection = projectDao.upsertCount

        assertTrue(store.inspect(FirstProjectId) is VideoProjectReadResult.Corrupt)
        assertNull(store.openProject(FirstProjectId, createIfMissing = false))
        assertEquals(corrupt, projectDao.entities[FirstProjectId])
        assertEquals(writesBeforeInspection, projectDao.upsertCount)
    }

    @Test
    fun unknownModernProjectEnumsAreReportedAsCorrupt() = runBlocking {
        val projectDao = FakeVideoProjectDao()
        val store = VideoProjectStore(projectDao, FakeVideoExportWorkDao())
        store.save(projectWithVideo(FirstProjectId))
        val persisted = requireNotNull(projectDao.entities[FirstProjectId])
        val corruptEntities = listOf(
            persisted.copy(canvasAspectRatio = "UNKNOWN_RATIO"),
            persisted.copy(exportResolution = "UNKNOWN_RESOLUTION"),
            persisted.copy(durationMode = "UNKNOWN_DURATION_MODE"),
        )

        corruptEntities.forEach { corrupt ->
            projectDao.entities[FirstProjectId] = corrupt
            assertTrue(store.inspect(FirstProjectId) is VideoProjectReadResult.Corrupt)
        }
    }

    @Test
    fun blankModernPayloadIsAnEmptyDraftAndDoesNotFallBackToLegacyClips() = runBlocking {
        val projectDao = FakeVideoProjectDao()
        val store = VideoProjectStore(projectDao, FakeVideoExportWorkDao())
        store.save(projectWithVideo(FirstProjectId))
        val persisted = requireNotNull(projectDao.entities[FirstProjectId])
        assertNotNull(persisted.clip0)
        projectDao.entities[FirstProjectId] = persisted.copy(mediaItems = "")

        val restored = requireNotNull(store.get(FirstProjectId))

        assertTrue(restored.mediaByCell.isEmpty())
    }

    @Test
    fun persistedTransformRotationAndTrimRoundTripExactly() = runBlocking {
        val store = VideoProjectStore(FakeVideoProjectDao(), FakeVideoExportWorkDao())
        val source = projectWithVideo(FirstProjectId)

        store.save(source)
        val restored = requireNotNull(store.get(FirstProjectId))

        assertEquals(source.clips.getValue(0), restored.clips.getValue(0))
    }

    @Test
    fun exportWorkObservationRemainsScopedToExactProjectId() = runBlocking {
        val exportWorkDao = FakeVideoExportWorkDao()
        val store = VideoProjectStore(FakeVideoProjectDao(), exportWorkDao)
        val secondWork = exportWork(SecondProjectId, "second-work")
        exportWorkDao.upsert(secondWork)

        assertNull(store.observeExportWork(FirstProjectId).first())

        val firstWork = exportWork(FirstProjectId, "first-work")
        exportWorkDao.upsert(firstWork)

        assertEquals(firstWork, store.observeExportWork(FirstProjectId).first())
        assertEquals(secondWork, store.observeExportWork(SecondProjectId).first())
    }

    @Test
    fun staleWorkerCannotOverwriteNewerExportWork() = runBlocking {
        val exportWorkDao = FakeVideoExportWorkDao()
        val store = VideoProjectStore(FakeVideoProjectDao(), exportWorkDao)
        val current = exportWork(FirstProjectId, "current-work")
        exportWorkDao.upsert(current)

        assertFalse(
            store.updateExportWorkIfCurrent(
                exportWork(FirstProjectId, "stale-work").copy(state = "failed"),
                expectedStates = listOf("running"),
            ),
        )
        assertEquals(current, exportWorkDao.get(FirstProjectId))

        assertTrue(
            store.updateExportWorkIfCurrent(
                current.copy(state = "succeeded", progress = 1f),
                expectedStates = listOf("running"),
            ),
        )
        assertEquals("succeeded", exportWorkDao.get(FirstProjectId)?.state)
    }

    @Test
    fun cancelledWorkCannotReturnToRunningOrCompleteWithTheSameWorkId() = runBlocking {
        val exportWorkDao = FakeVideoExportWorkDao()
        val store = VideoProjectStore(FakeVideoProjectDao(), exportWorkDao)
        val running = exportWork(FirstProjectId, "same-work")
        exportWorkDao.upsert(running)

        assertTrue(
            store.updateExportWorkIfCurrent(
                running.copy(state = "cancelled"),
                expectedStates = listOf("queued", "running"),
            ),
        )
        assertFalse(
            store.updateExportWorkIfCurrent(
                running.copy(progress = 0.8f),
                expectedStates = listOf("running"),
            ),
        )
        assertFalse(
            store.updateExportWorkIfCurrent(
                running.copy(state = "succeeded", progress = 1f, outputUri = "content://media/video/1"),
                expectedStates = listOf("running"),
            ),
        )
        assertEquals("cancelled", exportWorkDao.get(FirstProjectId)?.state)
    }

    @Test
    fun globalActiveExportFlowTracksQueuedAndRunningWork() = runBlocking {
        val exportWorkDao = FakeVideoExportWorkDao()
        val store = VideoProjectStore(FakeVideoProjectDao(), exportWorkDao)
        val workId = "active-work"
        assertFalse(store.observeHasActiveExport().first())

        store.setExportWork(exportWork(FirstProjectId, workId).copy(state = "queued"))
        assertTrue(store.observeHasActiveExport().first())

        store.updateExportWorkIfCurrent(
            exportWork(FirstProjectId, workId).copy(state = "succeeded"),
            expectedStates = listOf("queued"),
        )
        assertFalse(store.observeHasActiveExport().first())
    }

    private fun exportWork(projectId: String, workId: String): VideoExportWorkEntity =
        VideoExportWorkEntity(
            projectId = projectId,
            workId = workId,
            state = "running",
            progress = 0.5f,
            outputUri = null,
            errorMessage = null,
            updatedAtMillis = 1L,
        )

    private fun projectWithVideo(projectId: String): VideoMergeProject {
        val clip = VideoClip(
            id = "clip-1",
            uri = "content://media/video/1",
            durationMs = 8_000L,
            trimStartMs = 1_000L,
            trimEndMs = 7_000L,
            width = 1_920,
            height = 1_080,
            rotationDegrees = 90,
            frameRate = 30f,
            hasAudio = true,
            transform = ImageTransform(zoom = 2f, panX = -0.4f, panY = 0.35f),
        )
        return VideoMergeProject(
            id = projectId,
            mediaByCell = mapOf(0 to MediaSource.Video(clip)),
            selectedCellIndex = 0,
        )
    }

    private companion object {
        const val FirstProjectId = "44444444-4444-4444-8444-444444444444"
        const val SecondProjectId = "55555555-5555-4555-8555-555555555555"
    }
}

private class FakeVideoProjectDao : VideoProjectDao {
    val entities = mutableMapOf<String, VideoProjectEntity>()
    private val observed = mutableMapOf<String, MutableStateFlow<VideoProjectEntity?>>()
    var upsertCount: Int = 0
        private set

    override suspend fun get(id: String): VideoProjectEntity? = entities[id]

    override fun observe(id: String): Flow<VideoProjectEntity?> =
        observed.getOrPut(id) { MutableStateFlow(entities[id]) }

    override suspend fun upsert(project: VideoProjectEntity) {
        upsertCount += 1
        entities[project.id] = project
        observed[project.id]?.value = project
    }

    override suspend fun delete(id: String) {
        entities.remove(id)
        observed[id]?.value = null
    }
}

private class FakeVideoExportWorkDao : VideoExportWorkDao {
    private val entities = mutableMapOf<String, VideoExportWorkEntity>()
    private val observed = mutableMapOf<String, MutableStateFlow<VideoExportWorkEntity?>>()
    private val hasActiveExport = MutableStateFlow(false)

    override fun observe(projectId: String): Flow<VideoExportWorkEntity?> =
        observed.getOrPut(projectId) { MutableStateFlow(entities[projectId]) }

    override fun observeHasActiveExport(): Flow<Boolean> = hasActiveExport

    override suspend fun get(projectId: String): VideoExportWorkEntity? = entities[projectId]

    override suspend fun getActive(): List<VideoExportWorkEntity> =
        entities.values.filter { it.state == "queued" || it.state == "running" }

    override suspend fun upsert(work: VideoExportWorkEntity) {
        entities[work.projectId] = work
        observed[work.projectId]?.value = work
        updateHasActiveExport()
    }

    override suspend fun updateIfWorkMatches(
        projectId: String,
        workId: String,
        state: String,
        progress: Float,
        outputUri: String?,
        errorMessage: String?,
        updatedAtMillis: Long,
        expectedStates: List<String>,
    ): Int {
        val current = entities[projectId]
            ?.takeIf { it.workId == workId && it.state in expectedStates }
            ?: return 0
        val updated = current.copy(
            state = state,
            progress = progress,
            outputUri = outputUri,
            errorMessage = errorMessage,
            updatedAtMillis = updatedAtMillis,
        )
        entities[projectId] = updated
        observed[projectId]?.value = updated
        updateHasActiveExport()
        return 1
    }

    override suspend fun delete(projectId: String) {
        entities.remove(projectId)
        observed[projectId]?.value = null
        updateHasActiveExport()
    }

    private fun updateHasActiveExport() {
        hasActiveExport.value = entities.values.any { it.state == "queued" || it.state == "running" }
    }
}
