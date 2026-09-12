package com.inkora.pdf

import kotlin.math.floor
import kotlin.math.sqrt

/** Affine transform from displayed crop-box coordinates into the source page's PDF coordinates. */
internal data class PdfDisplayTransform(
    val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float,
)

internal fun pdfDisplayTransform(left: Float, bottom: Float, width: Float, height: Float, rotation: Int): PdfDisplayTransform =
    when (((rotation % 360) + 360) % 360) {
        90 -> PdfDisplayTransform(0f, 1f, 1f, 0f, left, bottom)
        180 -> PdfDisplayTransform(-1f, 0f, 0f, 1f, left + width, bottom)
        270 -> PdfDisplayTransform(0f, -1f, -1f, 0f, left + width, bottom + height)
        else -> PdfDisplayTransform(1f, 0f, 0f, -1f, left, bottom + height)
    }

internal fun pdfDisplaySize(width: Float, height: Float, rotation: Int): PdfPageSize =
    if (((rotation % 180) + 180) % 180 == 90) PdfPageSize(height, width) else PdfPageSize(width, height)

/** Bound individual renders to 12 million pixels and preserve page aspect ratio. */
internal fun pdfRenderDimensions(size: PdfPageSize, request: PdfRenderRequest): Pair<Int, Int> {
    require(size.width > 0 && size.height > 0 && size.width.isFinite() && size.height.isFinite()) { "Invalid PDF page size" }
    require(request.dpi > 0 && request.dpi.isFinite()) { "DPI must be positive" }
    request.targetWidthPx?.let { require(it > 0) { "Render width must be positive" } }
    request.targetHeightPx?.let { require(it > 0) { "Render height must be positive" } }
    val scale = when {
        request.targetWidthPx != null && request.targetHeightPx != null ->
            minOf(request.targetWidthPx / size.width, request.targetHeightPx / size.height)
        request.targetWidthPx != null -> request.targetWidthPx / size.width
        request.targetHeightPx != null -> request.targetHeightPx / size.height
        else -> request.dpi / 72f
    }
    val bounded = minOf(scale, 4096f / maxOf(size.width, size.height), sqrt(12_000_000f / (size.width * size.height)))
    return floor(size.width * bounded).toInt().coerceAtLeast(1) to floor(size.height * bounded).toInt().coerceAtLeast(1)
}

internal fun pdfExportPages(pageCount: Int, selectedPages: Set<Int>?): List<Int> {
    val pages = selectedPages?.sorted() ?: (0 until pageCount).toList()
    require(pages.isNotEmpty()) { "At least one page must be selected for export" }
    pages.forEach { require(it in 0 until pageCount) { "Page index out of range: $it" } }
    return pages
}

internal fun pdfSearchMatches(pageIndex: Int, text: String, query: String, caseSensitive: Boolean): List<PdfSearchMatch> {
    if (query.isBlank()) return emptyList()
    val result = mutableListOf<PdfSearchMatch>()
    var cursor = 0
    while (cursor <= text.length - query.length) {
        val hit = text.indexOf(query, startIndex = cursor, ignoreCase = !caseSensitive)
        if (hit < 0) break
        result += PdfSearchMatch(pageIndex, hit, hit + query.length,
            text.substring((hit - 40).coerceAtLeast(0), (hit + query.length + 40).coerceAtMost(text.length)).replace('\n', ' '))
        cursor = hit + query.length
    }
    return result
}
