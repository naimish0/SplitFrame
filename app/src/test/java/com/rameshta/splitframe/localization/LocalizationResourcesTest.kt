package com.rameshta.splitframe.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    @Test
    fun releaseBundleKeepsEveryLanguageInTheInstalledBaseApk() {
        val appBuildFile = sequenceOf(File("build.gradle.kts"), File("app/build.gradle.kts"))
            .first { file ->
                file.isFile && file.readText().contains("libs.plugins.android.application")
            }
            .readText()

        assertTrue(
            "The in-app language picker requires language splits to be disabled for Play builds.",
            Regex(
                """bundle\s*\{[\s\S]*?language\s*\{[\s\S]*?enableSplit\s*=\s*false""",
            ).containsMatchIn(appBuildFile),
        )
    }

    private val resDirectory = sequenceOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }

    private val localeFolders = listOf(
        "values-de",
        "values-fr",
        "values-ja",
        "values-hi",
        "values-ru",
        "values-es",
        "values-pt-rPT",
        "values-pt-rBR",
        "values-it",
        "values-in",
        "values-ar",
        "values-ko",
        "values-ur",
    )

    @Test
    fun `every supported locale has complete resources and matching format tokens`() {
        val source = entries(File(resDirectory, "values/strings.xml"), skipNonTranslatable = true)

        localeFolders.forEach { folder ->
            val translatedFile = File(resDirectory, "$folder/strings.xml")
            assertTrue("Missing $folder/strings.xml", translatedFile.isFile)
            val translated = entries(translatedFile, skipNonTranslatable = false)

            assertTrue(
                "$folder is missing English resource keys",
                translated.keys.containsAll(source.keys),
            )
            source.forEach { (key, sourceText) ->
                assertEquals(
                    "$folder changed formatting placeholders for $key",
                    placeholders(sourceText),
                    placeholders(translated.getValue(key)),
                )
            }
            translated
                .filterKeys { it !in source }
                .forEach { (key, translatedText) ->
                    val pluralName = key.substringBeforeLast("/", missingDelimiterValue = "")
                    val sourceOther = source["$pluralName/other"]
                    assertTrue(
                        "$folder added an unknown resource key: $key",
                        pluralName.isNotBlank() && sourceOther != null,
                    )
                    assertEquals(
                        "$folder changed formatting placeholders for $key",
                        placeholders(requireNotNull(sourceOther)),
                        placeholders(translatedText),
                    )
                }
        }
    }

    @Test
    fun `locale config declares system languages in product order`() {
        val localeConfig = File(resDirectory, "xml/locales_config.xml").readText()
        val configuredTags = Regex("""android:name="([^"]+)"""")
            .findAll(localeConfig)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            listOf(
                "en", "de", "fr", "ja", "hi", "ru", "es", "pt-PT",
                "pt-BR", "it", "id", "ar", "ko", "ur",
            ),
            configuredTags,
        )
    }

    private fun entries(file: File, skipNonTranslatable: Boolean): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val resources = document.documentElement
        val result = linkedMapOf<String, String>()
        for (index in 0 until resources.childNodes.length) {
            val node = resources.childNodes.item(index)
            if (node !is Element) continue
            if (skipNonTranslatable && node.getAttribute("translatable") == "false") continue
            when (node.tagName) {
                "string" -> result[node.getAttribute("name")] = node.textContent
                "plurals" -> {
                    for (itemIndex in 0 until node.childNodes.length) {
                        val item = node.childNodes.item(itemIndex)
                        if (item is Element && item.tagName == "item") {
                            result["${node.getAttribute("name")}/${item.getAttribute("quantity")}"] =
                                item.textContent
                        }
                    }
                }
            }
        }
        return result
    }

    private fun placeholders(value: String): List<String> =
        Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]""")
            .findAll(value)
            .map { it.value }
            .sorted()
            .toList()
}
