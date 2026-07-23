package com.rameshta.splitframe.export

import com.rameshta.splitframe.domain.ImageDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
                release = { it.releaseCount += 1 },
            )
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(1, source.releaseCount)
    }

    @Test
    fun `distinct orientation result releases only original`() {
        val source = Resource("source")
        val oriented = Resource("oriented")

        val result = transformOwnedResource(
            owned = source,
            transform = { oriented },
            release = { it.releaseCount += 1 },
        )

        assertSame(oriented, result)
        assertEquals(1, source.releaseCount)
        assertEquals(0, oriented.releaseCount)
    }

    @Test
    fun `identity transform retains original ownership`() {
        val source = Resource("source")

        val result = transformOwnedResource(
            owned = source,
            transform = { it },
            release = { it.releaseCount += 1 },
        )

        assertSame(source, result)
        assertEquals(0, source.releaseCount)
    }

    @Test
    fun `transform failure remains primary when owned cleanup also fails`() {
        val source = Resource("source")
        val transformFailure = OutOfMemoryError("orientation allocation failed")
        val cleanupFailure = IllegalStateException("recycle failed")

        val thrown = runCatching {
            transformOwnedResource(
                owned = source,
                transform = { throw transformFailure },
                release = {
                    it.releaseCount += 1
                    throw cleanupFailure
                },
            )
        }.exceptionOrNull()

        assertSame(transformFailure, thrown)
        assertEquals(1, source.releaseCount)
        assertEquals(listOf(cleanupFailure), thrown?.suppressed?.toList())
    }

    @Test
    fun `failed original release also releases distinct transformed resource`() {
        val source = Resource("source")
        val oriented = Resource("oriented")
        val releaseFailure = IllegalStateException("source recycle failed")

        val thrown = runCatching {
            transformOwnedResource(
                owned = source,
                transform = { oriented },
                release = { resource ->
                    resource.releaseCount += 1
                    if (resource === source) throw releaseFailure
                },
            )
        }.exceptionOrNull()

        assertSame(releaseFailure, thrown)
        assertEquals(1, source.releaseCount)
        assertEquals(1, oriented.releaseCount)
    }

    private data class Resource(
        val name: String,
        var releaseCount: Int = 0,
    )
}
