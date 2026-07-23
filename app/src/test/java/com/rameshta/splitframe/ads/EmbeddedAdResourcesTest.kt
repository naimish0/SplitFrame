package com.rameshta.splitframe.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAdResourcesTest {
    @Test
    fun `ad owner replaces and destroys resources exactly once`() {
        val destroyed = mutableListOf<String>()
        val owner = ReplaceableAdOwner<String>(destroyed::add)
        val firstGeneration = owner.beginLoad()
        assertTrue(owner.accept(firstGeneration, "first"))
        val secondGeneration = owner.beginLoad()
        assertTrue(owner.accept(secondGeneration, "second"))
        owner.dispose()
        owner.dispose()

        assertEquals(listOf("first", "second"), destroyed)
    }

    @Test
    fun `late and disposed load callbacks are destroyed`() {
        val destroyed = mutableListOf<String>()
        val owner = ReplaceableAdOwner<String>(destroyed::add)
        val staleGeneration = owner.beginLoad()
        val currentGeneration = owner.beginLoad()

        assertFalse(owner.accept(staleGeneration, "stale"))
        assertTrue(owner.accept(currentGeneration, "current"))
        owner.dispose()
        assertFalse(owner.accept(currentGeneration, "late"))

        assertEquals(listOf("stale", "current", "late"), destroyed)
    }

    @Test
    fun `load failure clears only its matching generation`() {
        val destroyed = mutableListOf<String>()
        val owner = ReplaceableAdOwner<String>(destroyed::add)
        val first = owner.beginLoad()
        owner.accept(first, "first")
        val second = owner.beginLoad()
        owner.failed(first)
        assertTrue(owner.accept(second, "second"))
        owner.failed(second)

        assertEquals(listOf("first", "second"), destroyed)
    }

    @Test
    fun `view lifecycle loads once per attachment and releases once`() {
        val calls = mutableListOf<String>()
        val controller = EmbeddedAdViewLifecycleController<String>(
            load = { calls += "load:$it" },
            resume = { calls += "resume:$it" },
            pause = { calls += "pause:$it" },
            destroy = { calls += "destroy:$it" },
        )

        controller.attach("one")
        controller.attach("one")
        controller.onResume()
        controller.onResume()
        controller.onPause()
        controller.onPause()
        controller.release("one")
        controller.release("one")

        assertEquals(
            listOf("load:one", "pause:one", "resume:one", "pause:one", "destroy:one"),
            calls,
        )
    }

    @Test
    fun `rotation destroys old view and loads replacement`() {
        val calls = mutableListOf<String>()
        val controller = EmbeddedAdViewLifecycleController<String>(
            load = { calls += "load:$it" },
            resume = { calls += "resume:$it" },
            pause = { calls += "pause:$it" },
            destroy = { calls += "destroy:$it" },
        )
        controller.onResume()
        controller.attach("portrait")
        controller.release("portrait")
        controller.attach("landscape")

        assertEquals(
            listOf(
                "load:portrait",
                "resume:portrait",
                "destroy:portrait",
                "load:landscape",
                "resume:landscape",
            ),
            calls,
        )
    }
}
