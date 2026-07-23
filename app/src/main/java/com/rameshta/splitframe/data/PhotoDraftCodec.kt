package com.rameshta.splitframe.data

import com.rameshta.splitframe.domain.CollageBackgroundKind
import com.rameshta.splitframe.domain.CollageBackgroundStyle
import com.rameshta.splitframe.domain.CollageBorderKind
import com.rameshta.splitframe.domain.CollageBorderStyle
import com.rameshta.splitframe.domain.CollageGradient
import com.rameshta.splitframe.domain.CollagePattern
import com.rameshta.splitframe.domain.CollageTextFont
import com.rameshta.splitframe.domain.CollageTextLayer
import com.rameshta.splitframe.domain.CropShape
import com.rameshta.splitframe.domain.ExportResolution
import com.rameshta.splitframe.domain.ImageSource
import com.rameshta.splitframe.domain.ImageTransform
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.domain.MergeProject
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.io.encoding.Base64

object PhotoDraftCodec {
    fun encode(project: MergeProject): String = buildString {
        appendLine("$Header|$CurrentVersion")
        appendLine(
            listOf(
                "project",
                project.id.encoded(),
                project.template.id.encoded(),
                project.spacingDp,
                project.cornerRadiusDp,
                project.backgroundColor.hex(),
                project.backgroundGradient.startColor.hex(),
                project.backgroundGradient.centerColor.hex(),
                project.backgroundGradient.endColor.hex(),
                project.backgroundGradient.isFallback,
                project.borderColor.hex(),
                project.borderWidthDp,
                project.exportResolution.name,
                project.beforeAfterSlider,
            ).joinToString("|"),
        )
        project.backgroundStyle.normalized().let { style ->
            appendLine(
                listOf(
                    "background",
                    style.kind.name,
                    style.primaryColor.hex(),
                    style.secondaryColor.hex(),
                    style.tertiaryColor.hex(),
                    style.angleDegrees,
                    style.blurSourceCellIndex ?: "",
                    style.blurRadius,
                    style.pattern.name,
                ).joinToString("|"),
            )
        }
        project.borderStyle.normalized().let { style ->
            appendLine(
                listOf(
                    "border",
                    style.kind.name,
                    style.primaryColor.hex(),
                    style.secondaryColor.hex(),
                    style.dashLengthDp,
                    style.gapLengthDp,
                ).joinToString("|"),
            )
        }
        project.template.cells.sortedBy { it.index }.forEach { cell ->
            val source = project.assignedImages[cell.index] as? ImageSource.LocalUri ?: return@forEach
            val persistedTransform = project.imageTransforms[cell.index]
            val transform = (persistedTransform ?: ImageTransform.Default).normalized()
            appendLine(
                listOf(
                    "image",
                    cell.index,
                    source.uri.encoded(),
                    persistedTransform != null,
                    transform.zoom,
                    transform.panX,
                    transform.panY,
                ).joinToString("|"),
            )
        }
        project.cropShapes.toSortedMap().forEach { (cellIndex, shape) ->
            appendLine("shape|$cellIndex|${shape.name}")
        }
        project.textLayers.take(MaxTextLayers).forEach { layer ->
            val safe = layer.normalized()
            appendLine(
                listOf(
                    "text",
                    safe.id.encoded(),
                    safe.text.encoded(),
                    safe.font.name,
                    safe.fontSize,
                    safe.color.hex(),
                    safe.outlineColor.hex(),
                    safe.outlineWidth,
                    safe.shadowColor.hex(),
                    safe.shadowRadius,
                    safe.shadowOffsetX,
                    safe.shadowOffsetY,
                    safe.opacity,
                    safe.rotationDegrees,
                    safe.centerX,
                    safe.centerY,
                    safe.scale,
                ).joinToString("|"),
            )
        }
    }

    fun decode(
        encoded: String,
        templates: List<LayoutTemplate>,
    ): MergeProject? = runCatching {
        if (encoded.length !in 1..MaxEncodedLength) return null
        val lines = encoded.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.firstOrNull()?.split('|') ?: return null
        if (header.getOrNull(0) != Header || header.getOrNull(1)?.toIntOrNull() != CurrentVersion) return null
        val records = lines.drop(1)
        val knownTypes = setOf("project", "background", "border", "image", "shape", "text")
        if (records.any { line -> line.substringBefore('|') !in knownTypes }) return null
        if (records.count { it.startsWith("project|") } != 1) return null
        if (records.count { it.startsWith("background|") } != 1) return null
        if (records.count { it.startsWith("border|") } != 1) return null
        val projectFields = records.single { it.startsWith("project|") }.split('|')
        if (projectFields.size != 14) return null
        val id = projectFields[1].decoded()?.takeIf { value ->
            runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
        } ?: return null
        val templateId = projectFields[2].decoded() ?: return null
        val template = templates.firstOrNull { it.id == templateId } ?: return null
        val validCells = template.cells.map { it.index }.toSet()
        val backgroundGradient = CollageGradient(
            startColor = projectFields[6].color() ?: return null,
            centerColor = projectFields[7].color() ?: return null,
            endColor = projectFields[8].color() ?: return null,
            isFallback = projectFields[9].toBooleanStrictOrNull() ?: return null,
        )
        val images = linkedMapOf<Int, ImageSource>()
        val transforms = linkedMapOf<Int, ImageTransform>()
        records.filter { it.startsWith("image|") }.forEach { line ->
            val fields = line.split('|')
            if (fields.size != 7) return null
            val cell = fields[1].toIntOrNull()?.takeIf(validCells::contains) ?: return null
            val uri = fields[2].decoded()?.takeIf { it.length in 2..MaxUriLength && ':' in it } ?: return null
            if (images.containsKey(cell)) return null
            val hasTransform = fields[3].toBooleanStrictOrNull() ?: return null
            val zoom = fields[4].finiteFloat() ?: return null
            val panX = fields[5].finiteFloat() ?: return null
            val panY = fields[6].finiteFloat() ?: return null
            images[cell] = ImageSource.LocalUri(uri)
            if (hasTransform) {
                transforms[cell] = ImageTransform(
                    zoom = zoom,
                    panX = panX,
                    panY = panY,
                ).normalized()
            }
        }
        val shapePairs = records.filter { it.startsWith("shape|") }.map { line ->
            val fields = line.split('|')
            if (fields.size != 3) return null
            val cell = fields[1].toIntOrNull()?.takeIf(validCells::contains) ?: return null
            val shape = fields[2].enumOrNull<CropShape>() ?: return null
            cell to shape
        }
        if (shapePairs.map { it.first }.distinct().size != shapePairs.size) return null
        val shapes = shapePairs.filter { it.second != CropShape.Rectangle }.toMap()
        val textRecords = records.filter { it.startsWith("text|") }
        if (textRecords.size > MaxTextLayers) return null
        val textLayers = textRecords.map { line -> decodeText(line) ?: return null }
        if (textLayers.map(CollageTextLayer::id).distinct().size != textLayers.size) return null

        MergeProject(
            id = id,
            template = template,
            assignedImages = images,
            imageTransforms = transforms,
            spacingDp = projectFields[3].finiteFloat()?.coerceIn(0f, 36f) ?: return null,
            cornerRadiusDp = projectFields[4].finiteFloat()?.coerceIn(0f, 64f) ?: return null,
            backgroundColor = projectFields[5].color() ?: return null,
            borderColor = projectFields[10].color() ?: return null,
            borderWidthDp = projectFields[11].finiteFloat()?.coerceIn(0f, 12f) ?: return null,
            exportResolution = projectFields[12].enumOrNull<ExportResolution>() ?: ExportResolution.FHD_1080,
            beforeAfterSlider = projectFields[13].finiteFloat()?.coerceIn(0.05f, 0.95f) ?: 0.5f,
            backgroundGradient = backgroundGradient,
            backgroundStyle = decodeBackground(records) ?: return null,
            borderStyle = decodeBorder(records) ?: return null,
            cropShapes = shapes,
            textLayers = textLayers,
        )
    }.getOrNull()

    private fun decodeBackground(lines: List<String>): CollageBackgroundStyle? {
        val fields = lines.firstOrNull { it.startsWith("background|") }?.split('|') ?: return null
        if (fields.size != 9) return null
        val blurSourceCellIndex = if (fields[6].isBlank()) {
            null
        } else {
            fields[6].toIntOrNull() ?: return null
        }
        return CollageBackgroundStyle(
            kind = fields[1].enumOrNull<CollageBackgroundKind>() ?: return null,
            primaryColor = fields[2].color() ?: return null,
            secondaryColor = fields[3].color() ?: return null,
            tertiaryColor = fields[4].color() ?: return null,
            angleDegrees = fields[5].finiteFloat() ?: return null,
            blurSourceCellIndex = blurSourceCellIndex,
            blurRadius = fields[7].toIntOrNull() ?: return null,
            pattern = fields[8].enumOrNull<CollagePattern>() ?: return null,
        ).normalized()
    }

    private fun decodeBorder(lines: List<String>): CollageBorderStyle? {
        val fields = lines.firstOrNull { it.startsWith("border|") }?.split('|') ?: return null
        if (fields.size != 6) return null
        return CollageBorderStyle(
            kind = fields[1].enumOrNull<CollageBorderKind>() ?: return null,
            primaryColor = fields[2].color() ?: return null,
            secondaryColor = fields[3].color() ?: return null,
            dashLengthDp = fields[4].finiteFloat() ?: return null,
            gapLengthDp = fields[5].finiteFloat() ?: return null,
        ).normalized()
    }

    private fun decodeText(line: String): CollageTextLayer? {
        val fields = line.split('|')
        if (fields.size != 17) return null
        return CollageTextLayer(
            id = fields[1].decoded()?.takeIf { it.isNotBlank() } ?: return null,
            text = fields[2].decoded() ?: return null,
            font = fields[3].enumOrNull<CollageTextFont>() ?: CollageTextFont.SansSerif,
            fontSize = fields[4].finiteFloat() ?: 32f,
            color = fields[5].color() ?: return null,
            outlineColor = fields[6].color() ?: return null,
            outlineWidth = fields[7].finiteFloat() ?: 0f,
            shadowColor = fields[8].color() ?: return null,
            shadowRadius = fields[9].finiteFloat() ?: 0f,
            shadowOffsetX = fields[10].finiteFloat() ?: 0f,
            shadowOffsetY = fields[11].finiteFloat() ?: 0f,
            opacity = fields[12].finiteFloat() ?: 1f,
            rotationDegrees = fields[13].finiteFloat() ?: 0f,
            centerX = fields[14].finiteFloat() ?: 0.5f,
            centerY = fields[15].finiteFloat() ?: 0.5f,
            scale = fields[16].finiteFloat() ?: 1f,
        ).normalized()
    }

    private fun String.encoded(): String = Base64.UrlSafe
        .encode(toByteArray(StandardCharsets.UTF_8))
        .trimEnd('=')

    private fun String.decoded(): String? = runCatching {
        val padding = (4 - length % 4) % 4
        String(Base64.UrlSafe.decode(this + "=".repeat(padding)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun ULong.hex(): String = toString(16).padStart(8, '0')

    private fun String.color(): ULong? = toULongOrNull(16)?.takeIf { it <= 0xFFFFFFFFuL }

    private fun String.finiteFloat(): Float? = toFloatOrNull()?.takeIf(Float::isFinite)

    private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private const val Header = "SPLITFRAME_PHOTO_DRAFT"
    private const val CurrentVersion = 1
    private const val MaxEncodedLength = 512_000
    private const val MaxUriLength = 4_096
    private const val MaxTextLayers = 20
}
