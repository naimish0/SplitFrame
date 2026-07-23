package com.rameshta.splitframe.export

import android.graphics.Bitmap
import com.rameshta.splitframe.domain.SingleImageOutputFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SingleImageExportTransactionTest {
    @Test
    fun successfulWritePublishesWithoutRollback() {
        val calls = mutableListOf<String>()

        val result = transactionalSingleImageExport<String>(
            insert = { calls += "insert"; Entry },
            write = { calls += "write:$it" },
            validate = { calls += "validate:$it" },
            publish = { calls += "publish:$it" },
            rollback = { calls += "rollback:$it"; true },
        )

        assertEquals(Entry, result)
        assertEquals(
            listOf("insert", "write:$Entry", "validate:$Entry", "publish:$Entry"),
            calls,
        )
    }

    @Test
    fun missingInsertFailsWithoutRollback() {
        var rollbackCalled = false

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { null },
                write = {},
                publish = {},
                rollback = { rollbackCalled = true; true },
            )
        }

        assertEquals("Could not create output image.", failure.message)
        assertFalse(rollbackCalled)
    }

    @Test
    fun compressionFailureRollsBackExactEntryAndSkipsPublish() {
        var published = false
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = {
                    writeCompressedSingleImage(ByteArrayOutputStream()) { false }
                },
                publish = { published = true },
                rollback = { rolledBackEntry = it; true },
            )
        }

        assertEquals("Could not encode output image.", failure.message)
        assertFalse(published)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun flushFailureRollsBackBeforePublish() {
        var published = false
        var rolledBack = false

        val failure = assertThrows(IOException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = {
                    FailingFlushOutputStream().use { output ->
                        writeCompressedSingleImage(output) { true }
                    }
                },
                publish = { published = true },
                rollback = { rolledBack = true; true },
            )
        }

        assertEquals("flush failed", failure.message)
        assertFalse(published)
        assertTrue(rolledBack)
    }

    @Test
    fun closeFailureRollsBackBeforePublish() {
        var published = false
        var rolledBack = false

        val failure = assertThrows(IOException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = {
                    FailingCloseOutputStream().use { output ->
                        writeCompressedSingleImage(output) { true }
                    }
                },
                publish = { published = true },
                rollback = { rolledBack = true; true },
            )
        }

        assertEquals("close failed", failure.message)
        assertFalse(published)
        assertTrue(rolledBack)
    }

    @Test
    fun publishFailureRollsBackExactEntry() {
        val original = IllegalStateException("publish failed")
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = {},
                publish = { throw original },
                rollback = { rolledBackEntry = it; true },
            )
        }

        assertSame(original, failure)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun validationFailureRollsBackAndSkipsPublish() {
        val original = IllegalStateException("invalid encoded image")
        var published = false
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = {},
                validate = { throw original },
                publish = { published = true },
                rollback = { rolledBackEntry = it; true },
            )
        }

        assertSame(original, failure)
        assertFalse(published)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun rollbackFalseIsSuppressedWithoutReplacingOriginalFailure() {
        val original = IllegalStateException("write failed")

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = { throw original },
                publish = {},
                rollback = { false },
            )
        }

        assertSame(original, failure)
        assertEquals(1, failure.suppressed.size)
        assertEquals("Could not remove incomplete output image.", failure.suppressed.single().message)
    }

    @Test
    fun rollbackExceptionIsSuppressedWithoutReplacingOriginalFailure() {
        val original = IllegalStateException("write failed")
        val rollbackFailure = IllegalStateException("delete failed")

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = { throw original },
                publish = {},
                rollback = { throw rollbackFailure },
            )
        }

        assertSame(original, failure)
        assertSame(rollbackFailure, failure.suppressed.single())
    }

    @Test
    fun sameRollbackExceptionDoesNotReplaceOriginalFailure() {
        val original = IllegalStateException("shared failure")

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalSingleImageExport<String>(
                insert = { Entry },
                write = { throw original },
                publish = {},
                rollback = { throw original },
            )
        }

        assertSame(original, failure)
        assertTrue(failure.suppressed.isEmpty())
    }

    @Test
    fun successfulCompressionWritesAndFlushes() {
        val output = RecordingOutputStream()

        writeCompressedSingleImage(output) { stream ->
            stream.write(byteArrayOf(1, 2, 3))
            true
        }

        assertTrue(output.flushed)
        assertEquals(byteArrayOf(1, 2, 3).toList(), output.toByteArray().toList())
    }

    @Test
    fun compressionFormatsPreserveApiSpecificMappings() {
        assertSame(Bitmap.CompressFormat.JPEG, SingleImageOutputFormat.Jpeg.compressFormat(apiLevel = 24))
        assertSame(Bitmap.CompressFormat.PNG, SingleImageOutputFormat.Png.compressFormat(apiLevel = 36))
        @Suppress("DEPRECATION")
        assertSame(Bitmap.CompressFormat.WEBP, SingleImageOutputFormat.Webp.compressFormat(apiLevel = 29))
        assertSame(Bitmap.CompressFormat.WEBP_LOSSY, SingleImageOutputFormat.Webp.compressFormat(apiLevel = 30))
    }

    @Test
    fun cleanupReleasesDistinctResourcesOnceAndAliasedResourceOnce() {
        val first = Any()
        val second = Any()
        val released = mutableListOf<Any>()

        releaseDistinctBestEffort(first, second, released::add)
        assertEquals(listOf(first, second), released)

        released.clear()
        releaseDistinctBestEffort(first, first, released::add)
        assertEquals(listOf(first), released)
    }

    @Test
    fun cleanupFailureDoesNotPreventRemainingResourceRelease() {
        val first = Any()
        val second = Any()
        val released = mutableListOf<Any>()

        releaseDistinctBestEffort(first, second) { resource ->
            if (resource === first) error("cleanup failed")
            released += resource
        }

        assertEquals(listOf(second), released)
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
        const val Entry = "content://media/images/resize/1"
    }
}
