package com.rameshta.splitframe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResourcesTest {
    @Test
    fun `every activity theme override remains AppCompat compatible`() {
        val resDirectory = sequenceOf(File("src/main/res"), File("app/src/main/res"))
            .first { it.isDirectory }
        val themeFiles = resDirectory.walkTopDown()
            .filter { it.isFile && it.name == "themes.xml" }
            .toList()

        assertTrue("No theme resources found", themeFiles.isNotEmpty())
        themeFiles.forEach { file ->
            val parent = Regex(
                """<style\s+name="Theme\.SplitFrame"\s+parent="([^"]+)"""",
            ).find(file.readText())?.groupValues?.get(1)
            assertEquals(
                "${file.relativeTo(resDirectory)} must inherit from AppCompat",
                "Theme.AppCompat.DayNight.NoActionBar",
                parent,
            )
        }
    }
}
