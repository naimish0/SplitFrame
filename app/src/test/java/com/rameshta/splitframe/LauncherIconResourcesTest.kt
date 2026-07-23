package com.rameshta.splitframe

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourcesTest {
    private val resDirectory = sequenceOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }

    @Test
    fun `launcher and splash use the canonical SplitFrame logo`() {
        val launcherForeground = File(
            resDirectory,
            "drawable/ic_launcher_foreground.xml",
        ).readText()
        val splashLogo = File(resDirectory, "drawable/ic_splash_logo.xml").readText()

        assertTrue(
            "The launcher foreground must use the same logo shown inside the app.",
            launcherForeground.contains("""android:src="@drawable/ic_splitframe_logo""""),
        )
        assertTrue(
            "The system splash must use the same logo shown inside the app.",
            splashLogo.contains("""android:drawable="@drawable/ic_splitframe_logo""""),
        )
    }

    @Test
    fun `every adaptive launcher icon uses the shared foreground`() {
        val adaptiveIcons = resDirectory.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "xml" &&
                    file.parentFile?.name?.startsWith("mipmap-anydpi") == true &&
                    file.name in setOf("ic_launcher.xml", "ic_launcher_round.xml")
            }
            .toList()

        assertTrue("No adaptive launcher icon resources found.", adaptiveIcons.isNotEmpty())
        adaptiveIcons.forEach { icon ->
            assertTrue(
                "${icon.relativeTo(resDirectory)} must use the shared launcher foreground.",
                icon.readText().contains(
                    """<foreground android:drawable="@drawable/ic_launcher_foreground" />""",
                ),
            )
        }
    }
}
