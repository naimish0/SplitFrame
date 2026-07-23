package com.rameshta.splitframe.export

import com.rameshta.splitframe.domain.ImageDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSourceReaderOwnershipTest {
    @Test
    fun `decode sample planner enforces edge and pixel budgets`() {
        assertEquals(4, imageDecodeSampleSize(ImageDimensions(16_383, 16_383), 8_192))
        assertEquals(2, imageDecodeSampleSize(ImageDimensions(30_000, 1_000), 8_000))
        assertEquals(1, imageDecodeSampleSize(ImageDimensions(4_000, 3_000), 8_192))
    }
    @Test
    fun `failed orientation transform releases decoder owned resource`() {
        val source = Resource("source")
        val failure = IllegalStateException("allocation failed")

        val thrown = runCatching {
            transformOwnedResource(
                owned = source,
                transform = { throw failure },
                release = { it.released = true },
            )
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertTrue(source.released)
    }

    @Test
    fun `distinct orientation result releases only original`() {
        val source = Resource("source")
        val oriented = Resource("oriented")

        val result = transformOwnedResource(
            owned = source,
            transform = { oriented },
            release = { it.released = true },
        )

        assertSame(oriented, result)
        assertTrue(source.released)
        assertEquals(false, oriented.released)
    }

    @Test
    fun `identity transform retains original ownership`() {
        val source = Resource("source")

        val result = transformOwnedResource(
            owned = source,
            transform = { it },
            release = { it.released = true },
        )

        assertSame(source, result)
        assertEquals(false, source.released)
    }

    private data class Resource(
        val name: String,
        var released: Boolean = false,
    )
}
