package com.inkora.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Writes each editable overlay as vector content (or an embedded image), in display coordinates. */
internal class PdfAnnotationWriter(private val owner: PDDocument) {
    private val font: PDFont by lazy {
        val candidates = listOf(
            "${System.getenv("WINDIR") ?: "C:/Windows"}/Fonts/arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
        )
        candidates.asSequence().map(::File).firstOrNull { it.isFile }?.let { PDType0Font.load(owner, it) }
            ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
    }

    fun append(page: PDPage, annotations: List<PdfAnnotation>) {
        if (annotations.isEmpty()) return
        val crop = page.cropBox
        val matrix = pdfDisplayTransform(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation)
        val displaySize = pdfDisplaySize(crop.width, crop.height, page.rotation)
        val workspace = pdfWorkspaceBounds(displaySize, annotations)
        val hasMargins = workspace.left < 0 || workspace.top < 0 || workspace.right > displaySize.width || workspace.bottom > displaySize.height
        if (hasMargins) {
            // Keep any source content outside the original crop hidden when expanding the paper.
            PDPageContentStream(owner, page, PDPageContentStream.AppendMode.PREPEND, true, false).use {
                it.saveGraphicsState()
                it.addRect(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
                it.clip()
            }
            PDPageContentStream(owner, page, PDPageContentStream.AppendMode.APPEND, true, false).use { it.restoreGraphicsState() }
        }
        PDPageContentStream(owner, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
            annotations.forEach { annotation ->
                content.saveGraphicsState()
                try {
                    content.transform(Matrix(matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f))
                    content.setLineCapStyle(1)
                    content.setLineJoinStyle(1)
                    when (annotation) {
                        is PdfInkAnnotation -> drawInk(content, annotation)
                        is PdfHighlightAnnotation -> drawHighlight(content, annotation)
                        is PdfTextAnnotation -> drawText(content, annotation)
                        is PdfShapeAnnotation -> drawShape(content, annotation)
                        is PdfImageAnnotation -> drawImage(content, annotation)
                    }
                } finally { content.restoreGraphicsState() }
            }
        }
        if (hasMargins) {
            val corners = listOf(workspace.left to workspace.top, workspace.right to workspace.top,
                workspace.left to workspace.bottom, workspace.right to workspace.bottom)
                .map { (x, y) -> (matrix.a * x + matrix.c * y + matrix.e) to (matrix.b * x + matrix.d * y + matrix.f) }
            val left = corners.minOf { it.first }; val bottom = corners.minOf { it.second }
            val expanded = PDRectangle(left, bottom, corners.maxOf { it.first } - left, corners.maxOf { it.second } - bottom)
            page.mediaBox = expanded
            page.cropBox = expanded
            page.trimBox = expanded
            page.bleedBox = expanded
            page.artBox = expanded
        }
    }

    private fun state(content: PDPageContentStream, argb: Int, opacity: Float, multiply: Boolean = false) {
        require(opacity.isFinite()) { "Invalid annotation opacity" }
        val alpha = (((argb ushr 24) and 255) / 255f * opacity).coerceIn(0f, 1f)
        content.setGraphicsStateParameters(PDExtendedGraphicsState().apply {
            strokingAlphaConstant = alpha
            nonStrokingAlphaConstant = alpha
            blendMode = if (multiply) BlendMode.MULTIPLY else BlendMode.NORMAL
        })
        val red = ((argb ushr 16) and 255) / 255f
        val green = ((argb ushr 8) and 255) / 255f
        val blue = (argb and 255) / 255f
        content.setStrokingColor(red, green, blue)
        content.setNonStrokingColor(red, green, blue)
    }

    private fun drawInk(content: PDPageContentStream, annotation: PdfInkAnnotation) {
        if (annotation.points.isEmpty()) return
        require(annotation.width.isFinite() && annotation.width > 0f) { "Invalid ink width" }
        state(content, annotation.colorArgb, annotation.opacity)
        if (annotation.points.size == 1) {
            val point = annotation.points.first()
            val radius = annotation.width * point.pressure.coerceIn(0.15f, 2f) / 2f
            ellipse(content, PdfRect(point.x - radius, point.y - radius, point.x + radius, point.y + radius))
            content.fill()
            return
        }
        val varyingPressure = annotation.points.any { it.pressure != annotation.points.first().pressure }
        if (!varyingPressure) {
            content.setLineWidth(annotation.width * annotation.points.first().pressure.coerceIn(0.15f, 2f))
            content.moveTo(annotation.points.first().x, annotation.points.first().y)
            annotation.points.drop(1).forEach { content.lineTo(it.x, it.y) }
            content.stroke()
        } else {
            annotation.points.zipWithNext().forEach { (from, to) ->
                content.setLineWidth(annotation.width * ((from.pressure + to.pressure) / 2f).coerceIn(0.15f, 2f))
                content.moveTo(from.x, from.y)
                content.lineTo(to.x, to.y)
                content.stroke()
            }
        }
    }

    private fun drawHighlight(content: PDPageContentStream, annotation: PdfHighlightAnnotation) {
        state(content, annotation.colorArgb, annotation.opacity, multiply = true)
        annotation.bounds.forEach { rect -> content.addRect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top) }
        content.fill()
    }

    private fun drawText(content: PDPageContentStream, annotation: PdfTextAnnotation) {
        require(annotation.textSize.isFinite() && annotation.textSize > 0f) { "Invalid text size" }
        val rect = annotation.bounds
        annotation.backgroundColorArgb?.let {
            state(content, it, annotation.opacity)
            content.addRect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top)
            content.fill()
        }
        state(content, annotation.colorArgb, annotation.opacity)
        val lines = wrapText(annotation.text, (rect.right - rect.left).coerceAtLeast(annotation.textSize), annotation.textSize)
        content.beginText()
        content.setFont(font, annotation.textSize)
        content.setLeading(annotation.textSize * 1.25f)
        content.setTextMatrix(Matrix(1f, 0f, 0f, -1f, rect.left, rect.top + annotation.textSize))
        lines.forEachIndexed { index, line ->
            if (index > 0) content.newLine()
            content.showText(line)
        }
        content.endText()
    }

    private fun wrapText(value: String, width: Float, size: Float): List<String> = buildList {
        value.replace("\t", "    ").replace("\r", "").split('\n').forEach { paragraph ->
            var line = ""
            paragraph.codePoints().toArray().forEach { codePoint ->
                val character = String(Character.toChars(codePoint))
                val candidate = line + character
                val measured = try { font.getStringWidth(candidate) / 1000f * size } catch (error: IllegalArgumentException) {
                    throw IllegalArgumentException("The export font cannot represent a character in this text annotation", error)
                }
                if (line.isNotEmpty() && measured > width) { add(line); line = character } else line = candidate
            }
            add(line)
        }
    }

    private fun drawShape(content: PDPageContentStream, annotation: PdfShapeAnnotation) {
        require(annotation.width.isFinite() && annotation.width > 0f)
        state(content, annotation.colorArgb, annotation.opacity)
        content.setLineWidth(annotation.width)
        val rect = annotation.bounds
        when (annotation.shape) {
            PdfShapeType.RECTANGLE -> content.addRect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top)
            PdfShapeType.ELLIPSE -> ellipse(content, rect)
            PdfShapeType.LINE, PdfShapeType.ARROW -> {
                val from = annotation.start ?: PdfInkPoint(rect.left, rect.top)
                val to = annotation.end ?: PdfInkPoint(rect.right, rect.bottom)
                content.moveTo(from.x, from.y)
                content.lineTo(to.x, to.y)
                if (annotation.shape == PdfShapeType.ARROW) {
                    val angle = atan2(to.y - from.y, to.x - from.x)
                    val length = maxOf(10f, annotation.width * 4f)
                    for (side in listOf(-0.5f, 0.5f)) {
                        content.moveTo(to.x, to.y)
                        content.lineTo(to.x - length * cos(angle + side), to.y - length * sin(angle + side))
                    }
                }
            }
        }
        if (annotation.fillColorArgb != null && annotation.shape in listOf(PdfShapeType.RECTANGLE, PdfShapeType.ELLIPSE)) {
            val color = annotation.fillColorArgb
            content.setNonStrokingColor(((color ushr 16) and 255) / 255f, ((color ushr 8) and 255) / 255f, (color and 255) / 255f)
            content.setGraphicsStateParameters(PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = ((color ushr 24) and 255) / 255f * annotation.opacity.coerceIn(0f, 1f)
            })
            content.fillAndStroke()
        } else content.stroke()
    }

    private fun drawImage(content: PDPageContentStream, annotation: PdfImageAnnotation) {
        state(content, -1, annotation.opacity)
        val rect = annotation.bounds
        val image = PDImageXObject.createFromByteArray(owner, annotation.encodedImage, "Inkora image")
        content.drawImage(image, Matrix(rect.right - rect.left, 0f, 0f, -(rect.bottom - rect.top), rect.left, rect.bottom))
    }

    private fun ellipse(content: PDPageContentStream, rect: PdfRect) {
        val rx = (rect.right - rect.left) / 2f
        val ry = (rect.bottom - rect.top) / 2f
        val cx = rect.left + rx
        val cy = rect.top + ry
        val k = 0.55228475f
        content.moveTo(cx + rx, cy)
        content.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
        content.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
        content.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
        content.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
        content.closePath()
    }
}
