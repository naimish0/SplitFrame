package com.rameshta.splitframe.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoExportTransactionTest {
    @Test
    fun `encoder fallback cannot silently change requested output contract`() {
        assertFalse(VideoEncoderFallbackEnabled)
    }

    @Test
    fun `stale cache sweep removes only old video export files`() {
        val directory = Files.createTempDirectory("splitframe-video-export").toFile()
        try {
            val stale = File(directory, "stale.mp4").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(100L)
            }
            val recent = File(directory, "recent.mp4").apply {
                writeBytes(byteArrayOf(2))
                setLastModified(900L)
            }
            val unrelated = File(directory, "notes.txt").apply {
                writeText("keep")
                setLastModified(100L)
            }

            assertEquals(1, cleanupStaleVideoExportFiles(directory, olderThanMillis = 500L))
            assertFalse(stale.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test
    fun successfulPublicationCommitsAfterPublishWithoutRollback() = runBlocking {
        val calls = mutableListOf<String>()

        val result = transactionalVideoPublication<String>(
            insert = { calls += "insert"; Entry },
            write = { calls += "write:$it" },
            beforePublish = { calls += "ownership" },
            publish = { calls += "publish:$it" },
            commit = { calls += "commit:$it" },
            rollback = { calls += "rollback:$it"; true },
        )

        assertEquals(Entry, result)
        assertEquals(
            listOf("insert", "write:$Entry", "ownership", "publish:$Entry", "commit:$Entry"),
            calls,
        )
    }

    @Test
    fun missingInsertFailsWithoutRollback() {
        var rollbackCalled = false

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { null },
                    write = {},
                    beforePublish = {},
                    publish = {},
                    commit = {},
                    rollback = { rollbackCalled = true; true },
                )
            }
        }

        assertEquals("Could not create MediaStore video.", failure.message)
        assertFalse(rollbackCalled)
    }

    @Test
    fun writeFailureRollsBackExactEntryAndSkipsLaterSteps() {
        val original = IOException("write failed")
        var published = false
        var committed = false
        var rolledBackEntry: String? = null

        val failure = assertThrows(IOException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = { throw original },
                    beforePublish = {},
                    publish = { published = true },
                    commit = { committed = true },
                    rollback = { rolledBackEntry = it; true },
                )
            }
        }

        assertSame(original, failure)
        assertFalse(published)
        assertFalse(committed)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun ownershipLossBeforePublishRollsBackAndSkipsPublish() {
        val cancellation = CancellationException("ownership changed")
        var published = false
        var rolledBack = false

        val failure = assertThrows(CancellationException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = {},
                    beforePublish = { throw cancellation },
                    publish = { published = true },
                    commit = {},
                    rollback = { rolledBack = true; true },
                )
            }
        }

        assertSame(cancellation, failure)
        assertFalse(published)
        assertTrue(rolledBack)
    }

    @Test
    fun publishFailureRollsBackAndSkipsCommit() {
        val original = IllegalStateException("publish failed")
        var committed = false
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = {},
                    beforePublish = {},
                    publish = { throw original },
                    commit = { committed = true },
                    rollback = { rolledBackEntry = it; true },
                )
            }
        }

        assertSame(original, failure)
        assertFalse(committed)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun terminalCommitFailureRollsBackPublishedEntry() {
        val original = IllegalStateException("room commit failed")
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = {},
                    beforePublish = {},
                    publish = {},
                    commit = { throw original },
                    rollback = { rolledBackEntry = it; true },
                )
            }
        }

        assertSame(original, failure)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun terminalOwnershipLossRollsBackPublishedEntryAndPreservesCancellation() {
        var rolledBackEntry: String? = null

        val failure = assertThrows(CancellationException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = {},
                    beforePublish = {},
                    publish = {},
                    commit = {
                        commitVideoPublication(
                            ensureActive = {},
                            commit = { false },
                        )
                    },
                    rollback = { rolledBackEntry = it; true },
                )
            }
        }

        assertEquals("Video export ownership changed during publication.", failure.message)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun terminalCommitCannotBeCancelledAfterFinalOwnershipCheck() = runBlocking {
        val commitStarted = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        var committed = false
        val job = launch {
            commitVideoPublication(
                ensureActive = {},
                commit = {
                    commitStarted.complete(Unit)
                    allowCommit.await()
                    committed = true
                    true
                },
            )
        }

        commitStarted.await()
        job.cancel()
        allowCommit.complete(Unit)
        job.join()

        assertTrue(committed)
    }

    @Test
    fun rollbackFalseIsSuppressedWithoutReplacingOriginalFailure() {
        val original = IllegalStateException("copy failed")

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = { throw original },
                    beforePublish = {},
                    publish = {},
                    commit = {},
                    rollback = { false },
                )
            }
        }

        assertSame(original, failure)
        assertEquals(1, failure.suppressed.size)
        assertEquals("Could not remove incomplete video.", failure.suppressed.single().message)
    }

    @Test
    fun rollbackExceptionIsSuppressedWithoutReplacingOriginalFailure() {
        val original = IllegalStateException("copy failed")
        val rollbackFailure = IllegalStateException("delete failed")

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = { throw original },
                    beforePublish = {},
                    publish = {},
                    commit = {},
                    rollback = { throw rollbackFailure },
                )
            }
        }

        assertSame(original, failure)
        assertSame(rollbackFailure, failure.suppressed.single())
    }

    @Test
    fun sameRollbackExceptionDoesNotReplaceOriginalFailure() {
        val original = IllegalStateException("shared failure")

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalVideoPublication<String>(
                    insert = { Entry },
                    write = { throw original },
                    beforePublish = {},
                    publish = {},
                    commit = {},
                    rollback = { throw original },
                )
            }
        }

        assertSame(original, failure)
        assertTrue(failure.suppressed.isEmpty())
    }

    @Test
    fun cachePreflightRejectsMissingAndEmptyFilesBeforeUse() {
        val empty = File.createTempFile("splitframe-empty-", ".mp4")
        val missing = File(empty.parentFile, "${empty.name}.missing")
        try {
            val missingFailure = assertThrows(IllegalStateException::class.java) {
                missing.requireVideoOutputSize()
            }
            assertEquals("Rendered video is unavailable.", missingFailure.message)

            val directoryFailure = assertThrows(IllegalStateException::class.java) {
                requireNotNull(empty.parentFile).requireVideoOutputSize()
            }
            assertEquals("Rendered video is unavailable.", directoryFailure.message)

            val emptyFailure = assertThrows(IllegalStateException::class.java) {
                empty.requireVideoOutputSize()
            }
            assertEquals("Rendered video is empty.", emptyFailure.message)

            empty.writeBytes(byteArrayOf(1, 2, 3, 4))
            assertEquals(4L, empty.requireVideoOutputSize())
        } finally {
            empty.delete()
        }
    }

    @Test
    fun videoCopyPreservesBytesFlushesAndChecksCancellationPerChunk() {
        val source = ByteArray(150_000) { index -> (index % 251).toByte() }
        val output = RecordingOutputStream()
        var activityChecks = 0

        copyVideoOutput(
            input = ByteArrayInputStream(source),
            output = output,
            expectedBytes = source.size.toLong(),
            ensureActive = { activityChecks += 1 },
        )

        assertEquals(source.toList(), output.toByteArray().toList())
        assertTrue(output.flushed)
        assertTrue(activityChecks >= 4)
    }

    @Test
    fun incompleteCopyFailsBeforeFlush() {
        val output = RecordingOutputStream()

        val failure = assertThrows(IllegalStateException::class.java) {
            copyVideoOutput(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                output = output,
                expectedBytes = 4L,
                ensureActive = {},
            )
        }

        assertEquals("Could not copy complete video.", failure.message)
        assertFalse(output.flushed)
    }

    @Test
    fun cancellationDuringCopyIsPreserved() {
        val cancellation = CancellationException("copy cancelled")
        var activityChecks = 0

        val failure = assertThrows(CancellationException::class.java) {
            copyVideoOutput(
                input = ByteArrayInputStream(ByteArray(150_000)),
                output = ByteArrayOutputStream(),
                expectedBytes = 150_000L,
                ensureActive = {
                    activityChecks += 1
                    if (activityChecks == 2) throw cancellation
                },
            )
        }

        assertSame(cancellation, failure)
    }

    @Test
    fun flushAndCloseFailuresRollBackBeforePublish() {
        listOf(FailingFlushOutputStream(), FailingCloseOutputStream()).forEach { output ->
            var published = false
            var rolledBack = false

            assertThrows(IOException::class.java) {
                runBlocking {
                    transactionalVideoPublication<String>(
                        insert = { Entry },
                        write = {
                            output.use {
                                copyVideoOutput(
                                    input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                                    output = it,
                                    expectedBytes = 3L,
                                    ensureActive = {},
                                )
                            }
                        },
                        beforePublish = {},
                        publish = { published = true },
                        commit = {},
                        rollback = { rolledBack = true; true },
                    )
                }
            }

            assertFalse(published)
            assertTrue(rolledBack)
        }
    }

    private class RecordingOutputStream : ByteArrayOutputStream() {
        var flushed = false
            private set

        override fun flush() {
            flushed = true
            super.flush()
        }
    }

    private class FailingFlushOutputStream : ByteArrayOutputStream() {
        override fun flush() {
            throw IOException("flush failed")
        }
    }

    private class FailingCloseOutputStream : ByteArrayOutputStream() {
        override fun close() {
            throw IOException("close failed")
        }
    }

    private companion object {
        const val Entry = "content://media/video/1"
    }
}
