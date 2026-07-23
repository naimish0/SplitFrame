package com.rameshta.splitframe.export

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageExportTransactionTest {
    @Test
    fun successfulWritePublishesWithoutRollback() {
        val calls = mutableListOf<String>()

        val result = transactionalPhotoExport<String>(
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
            transactionalPhotoExport<String>(
                insert = { null },
                write = {},
                publish = {},
                rollback = { rollbackCalled = true; true },
            )
        }

        assertEquals("Could not create MediaStore entry.", failure.message)
        assertFalse(rollbackCalled)
    }

    @Test
    fun writeFailureRollsBackAndSkipsPublish() {
        val original = IllegalStateException("write failed")
        var published = false
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalPhotoExport<String>(
                insert = { Entry },
                write = { throw original },
                publish = { published = true },
                rollback = { rolledBackEntry = it; true },
            )
        }

        assertSame(original, failure)
        assertFalse(published)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun publishFailureRollsBackExactEntry() {
        val original = IllegalStateException("publish failed")
        var rolledBackEntry: String? = null

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalPhotoExport<String>(
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
            transactionalPhotoExport<String>(
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
    fun transactionCancellationRollsBackExactEntryAndPreservesCancellation() {
        val cancellation = CancellationException("export cancelled")
        var rolledBackEntry: String? = null

        val failure = assertThrows(CancellationException::class.java) {
            transactionalPhotoExport<String>(
                insert = { Entry },
                write = { throw cancellation },
                publish = {},
                rollback = { rolledBackEntry = it; true },
            )
        }

        assertSame(cancellation, failure)
        assertEquals(Entry, rolledBackEntry)
    }

    @Test
    fun rollbackFailureIsSuppressedWithoutReplacingOriginalFailure() {
        val original = IllegalStateException("write failed")

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalPhotoExport<String>(
                insert = { Entry },
                write = { throw original },
                publish = {},
                rollback = { false },
            )
        }

        assertSame(original, failure)
        assertEquals(1, failure.suppressed.size)
        assertEquals("Could not remove incomplete photo export.", failure.suppressed.single().message)
    }

    @Test
    fun rollbackExceptionIsSuppressedWithoutReplacingOriginalFailure() {
        val original = IllegalStateException("write failed")
        val rollbackFailure = IllegalStateException("delete failed")

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalPhotoExport<String>(
                insert = { Entry },
                write = { throw original },
                publish = {},
                rollback = { throw rollbackFailure },
            )
        }

        assertSame(original, failure)
        assertEquals(1, failure.suppressed.size)
        assertSame(rollbackFailure, failure.suppressed.single())
    }

    @Test
    fun sameRollbackExceptionDoesNotReplaceOriginalFailure() {
        val original = IllegalStateException("shared failure")

        val failure = assertThrows(IllegalStateException::class.java) {
            transactionalPhotoExport<String>(
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
    fun pngWriterRequiresLosslessEncodeSuccessAndFlushesSuccessfulOutput() {
        val failedOutput = RecordingOutputStream()
        val failure = assertThrows(IllegalStateException::class.java) {
            writeLosslessPng(failedOutput) { false }
        }
        assertEquals("Could not encode lossless PNG export.", failure.message)
        assertFalse(failedOutput.flushed)

        val successfulOutput = RecordingOutputStream()
        writeLosslessPng(successfulOutput) { output ->
            output.write(byteArrayOf(1, 2, 3))
            true
        }

        assertTrue(successfulOutput.flushed)
        assertEquals(byteArrayOf(1, 2, 3).toList(), successfulOutput.toByteArray().toList())
    }

    private class RecordingOutputStream : ByteArrayOutputStream() {
        var flushed = false
            private set

        override fun flush() {
            flushed = true
            super.flush()
        }
    }

    private companion object {
        const val Entry = "content://media/images/1"
    }
}
