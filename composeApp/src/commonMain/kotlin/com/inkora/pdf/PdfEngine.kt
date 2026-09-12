package com.inkora.pdf

import com.inkora.platform.PlatformFile

data class PdfPageSize(val width: Float, val height: Float)

data class PdfRenderRequest(
    val targetWidthPx: Int? = null,
    val targetHeightPx: Int? = null,
    val dpi: Float = 144f,
)

/** PNG/JPEG encoded output keeps the common API independent of Android Bitmap and AWT. */
data class PdfRenderedPage(
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val encodedImage: ByteArray,
    val mimeType: String = "image/png",
)

data class PdfDocument(
    val id: String,
    val source: PlatformFile,
    val pageCount: Int,
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val start: Int,
    val end: Int,
    val snippet: String,
)

data class PdfOutlineItem(
    val title: String,
    val pageIndex: Int? = null,
    val children: List<PdfOutlineItem> = emptyList(),
)

data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(right >= left) { "right must be >= left" }
        require(bottom >= top) { "bottom must be >= top" }
    }
}

data class PdfInkPoint(val x: Float, val y: Float, val pressure: Float = 1f)

/** Coordinates use the displayed crop box, in PDF points, with the origin at its top left. */
sealed interface PdfAnnotation {
    val pageIndex: Int
}

data class PdfInkAnnotation(
    override val pageIndex: Int,
    val points: List<PdfInkPoint>,
    val colorArgb: Int,
    val width: Float,
    val opacity: Float = 1f,
) : PdfAnnotation

data class PdfHighlightAnnotation(
    override val pageIndex: Int,
    val bounds: List<PdfRect>,
    val colorArgb: Int = 0xFFFFFF00.toInt(),
    val opacity: Float = 0.35f,
) : PdfAnnotation

data class PdfTextAnnotation(
    override val pageIndex: Int,
    val bounds: PdfRect,
    val text: String,
    val colorArgb: Int = 0xFF202124.toInt(),
    val textSize: Float = 12f,
    val opacity: Float = 1f,
    val backgroundColorArgb: Int? = null,
) : PdfAnnotation

enum class PdfShapeType { LINE, RECTANGLE, ELLIPSE, ARROW }

data class PdfShapeAnnotation(
    override val pageIndex: Int,
    val bounds: PdfRect,
    val shape: PdfShapeType,
    val colorArgb: Int,
    val width: Float = 2f,
    val opacity: Float = 1f,
    val fillColorArgb: Int? = null,
    val start: PdfInkPoint? = null,
    val end: PdfInkPoint? = null,
) : PdfAnnotation

/** PNG or JPEG bytes are embedded as an image object; the underlying PDF page stays vector. */
data class PdfImageAnnotation(
    override val pageIndex: Int,
    val bounds: PdfRect,
    val encodedImage: ByteArray,
    val opacity: Float = 1f,
) : PdfAnnotation

/**
 * Rendering and export abstraction. Implementations must treat the source as read-only and emit
 * a new file for annotated exports.
 */
interface PdfEngine {
    suspend fun openDocument(source: PlatformFile): PdfDocument
    suspend fun closeDocument(document: PdfDocument)
    suspend fun pageSize(document: PdfDocument, pageIndex: Int): PdfPageSize
    suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        request: PdfRenderRequest = PdfRenderRequest(),
    ): PdfRenderedPage

    suspend fun renderThumbnail(
        document: PdfDocument,
        pageIndex: Int,
        longestSidePx: Int = 240,
    ): PdfRenderedPage

    suspend fun extractText(document: PdfDocument, pageIndex: Int? = null): String
    suspend fun search(document: PdfDocument, query: String, caseSensitive: Boolean = false): List<PdfSearchMatch>
    suspend fun outline(document: PdfDocument): List<PdfOutlineItem>
    suspend fun exportAnnotatedPdf(
        document: PdfDocument,
        annotations: List<PdfAnnotation>,
        destination: PlatformFile,
        selectedPages: Set<Int>? = null,
    ): PlatformFile
}

/** A platform can expose a readable but incomplete PDF engine while native support is unavailable. */
class UnsupportedPdfEngine(private val reason: String) : PdfEngine {
    private fun unsupported(): Nothing = error(reason)
    override suspend fun openDocument(source: PlatformFile): PdfDocument = unsupported()
    override suspend fun closeDocument(document: PdfDocument) = Unit
    override suspend fun pageSize(document: PdfDocument, pageIndex: Int): PdfPageSize = unsupported()
    override suspend fun renderPage(document: PdfDocument, pageIndex: Int, request: PdfRenderRequest): PdfRenderedPage = unsupported()
    override suspend fun renderThumbnail(document: PdfDocument, pageIndex: Int, longestSidePx: Int): PdfRenderedPage = unsupported()
    override suspend fun extractText(document: PdfDocument, pageIndex: Int?): String = unsupported()
    override suspend fun search(document: PdfDocument, query: String, caseSensitive: Boolean): List<PdfSearchMatch> = unsupported()
    override suspend fun outline(document: PdfDocument): List<PdfOutlineItem> = unsupported()
    override suspend fun exportAnnotatedPdf(document: PdfDocument, annotations: List<PdfAnnotation>, destination: PlatformFile, selectedPages: Set<Int>?): PlatformFile = unsupported()
}
