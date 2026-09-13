package com.inkora.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.inkora.platform.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android PdfRenderer implementation. Source PDFs are never overwritten; exports are new files. */
class AndroidPdfRendererEngine(private val context: android.content.Context) : PdfEngine {
    private data class OpenPdf(val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer, val mutex: Mutex = Mutex())
    private val documents = ConcurrentHashMap<String, OpenPdf>()
    private val ocrRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val ocrCache = ConcurrentHashMap<String, String>()

    override suspend fun openDocument(source: PlatformFile): PdfDocument = withContext(Dispatchers.IO) {
        val file = File(source.path)
        require(file.isFile) { "PDF source does not exist: ${source.path}" }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try { PdfRenderer(descriptor) } catch (failure: Throwable) { descriptor.close(); throw failure }
        require(renderer.pageCount > 0) { renderer.close(); descriptor.close(); error("This PDF has no pages") }
        val id = UUID.randomUUID().toString()
        documents[id] = OpenPdf(descriptor, renderer)
        PdfDocument(id, source, renderer.pageCount)
    }

    override suspend fun closeDocument(document: PdfDocument) {
        withContext(Dispatchers.IO) {
            documents.remove(document.id)?.let { open -> open.mutex.withLock { open.renderer.close(); open.descriptor.close() } }
        }
    }

    private suspend fun <T> withDocument(document: PdfDocument, block: (OpenPdf) -> T): T = withContext(Dispatchers.IO) {
        val open = documents[document.id] ?: error("PDF document is closed")
        open.mutex.withLock { block(open) }
    }

    override suspend fun pageSize(document: PdfDocument, pageIndex: Int): PdfPageSize = withDocument(document) { open ->
        require(pageIndex in 0 until open.renderer.pageCount)
        open.renderer.openPage(pageIndex).use { PdfPageSize(it.width.toFloat(), it.height.toFloat()) }
    }

    override suspend fun renderPage(document: PdfDocument, pageIndex: Int, request: PdfRenderRequest): PdfRenderedPage = render(document, pageIndex, request)

    private suspend fun render(document: PdfDocument, pageIndex: Int, request: PdfRenderRequest): PdfRenderedPage = withDocument(document) { open ->
        require(pageIndex in 0 until open.renderer.pageCount)
        open.renderer.openPage(pageIndex).use { page ->
            val scale = when {
                request.targetWidthPx != null -> request.targetWidthPx.toFloat() / page.width
                request.targetHeightPx != null -> request.targetHeightPx.toFloat() / page.height
                else -> (request.dpi / 72f).coerceIn(.25f, 8f)
            }.coerceIn(.1f, 8f)
            val width = (page.width * scale).toInt().coerceIn(1, 8192)
            val height = (page.height * scale).toInt().coerceIn(1, 8192)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, android.graphics.Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            PdfRenderedPage(pageIndex, width, height, bytes)
        }
    }

    override suspend fun renderThumbnail(document: PdfDocument, pageIndex: Int, longestSidePx: Int): PdfRenderedPage {
        require(longestSidePx > 0)
        return render(document, pageIndex, PdfRenderRequest(targetWidthPx = longestSidePx, targetHeightPx = longestSidePx))
    }

    /** Runs on-device ML Kit OCR over rendered pages and caches each result for this session. */
    override suspend fun extractText(document: PdfDocument, pageIndex: Int?): String = withContext(Dispatchers.Default) {
        val pages = pageIndex?.let { listOf(it) } ?: (0 until document.pageCount).toList()
        buildString {
            pages.forEachIndexed { index, page ->
                if (index > 0) append("\n\n")
                append(ocrPage(document, page))
            }
        }
    }

    override suspend fun search(document: PdfDocument, query: String, caseSensitive: Boolean): List<PdfSearchMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val matches = mutableListOf<PdfSearchMatch>()
        for (page in 0 until document.pageCount) {
            val text = ocrPage(document, page)
            val haystack = if (caseSensitive) text else text.lowercase()
            val target = if (caseSensitive) needle else needle.lowercase()
            var start = haystack.indexOf(target)
            while (start >= 0) {
                val end = start + target.length
                val snippetStart = (start - 48).coerceAtLeast(0)
                val snippetEnd = (end + 72).coerceAtMost(text.length)
                matches += PdfSearchMatch(page, start, end, text.substring(snippetStart, snippetEnd).replace(Regex("\\s+"), " ").trim())
                start = haystack.indexOf(target, start + target.length)
            }
        }
        return matches
    }

    private suspend fun ocrPage(document: PdfDocument, pageIndex: Int): String {
        require(pageIndex in 0 until document.pageCount)
        val key = "${document.id}:$pageIndex"
        ocrCache[key]?.let { return it }
        val rendered = render(document, pageIndex, PdfRenderRequest(targetWidthPx = 1800))
        val bitmap = BitmapFactory.decodeByteArray(rendered.encodedImage, 0, rendered.encodedImage.size)
            ?: return ""
        val text = try {
            val result = ocrRecognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
            result.text.trim()
        } finally { bitmap.recycle() }
        ocrCache[key] = text
        return text
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
        addOnCanceledListener { continuation.cancel() }
    }

    override suspend fun outline(document: PdfDocument): List<PdfOutlineItem> = emptyList()

    override suspend fun exportAnnotatedPdf(document: PdfDocument, annotations: List<PdfAnnotation>, destination: PlatformFile, selectedPages: Set<Int>?): PlatformFile = withContext(Dispatchers.IO) {
        val source = File(document.source.path)
        val output = File(destination.path)
        require(source.canonicalFile != output.canonicalFile) { "Export must create a different file" }
        require(annotations.all { it.pageIndex in 0 until document.pageCount }) { "Annotation page is out of range" }
        val exportPages = pdfExportPages(document.pageCount, selectedPages).toSet()
        output.parentFile?.mkdirs()
        val staging = File.createTempFile("inkora-export-", ".pdf", output.parentFile)
        try {
            val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                val out = android.graphics.pdf.PdfDocument()
                try {
                    var outputIndex = 0
                    for (sourceIndex in 0 until renderer.pageCount) {
                        coroutineContext.ensureActive()
                        if (sourceIndex !in exportPages) continue
                        renderer.openPage(sourceIndex).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val pageAnnotations = annotations.filter { it.pageIndex == sourceIndex }
                            val bounds = pdfWorkspaceBounds(PdfPageSize(page.width.toFloat(), page.height.toFloat()), pageAnnotations)
                            val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(kotlin.math.ceil(bounds.right - bounds.left).toInt(), kotlin.math.ceil(bounds.bottom - bounds.top).toInt(), outputIndex++).create()
                            val target = out.startPage(info)
                            target.canvas.drawColor(Color.WHITE)
                            target.canvas.save()
                            target.canvas.translate(-bounds.left, -bounds.top)
                            target.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            drawAnnotations(target.canvas, pageAnnotations, page.width.toFloat(), page.height.toFloat())
                            target.canvas.restore()
                            out.finishPage(target)
                            bitmap.recycle()
                        }
                    }
                    FileOutputStream(staging).use { out.writeTo(it); it.fd.sync() }
                } finally { out.close() }
            }
            if (!staging.renameTo(output)) { staging.copyTo(output, overwrite = true); staging.delete() }
            destination.copy(sizeBytes = output.length(), mimeType = "application/pdf")
        } finally { staging.delete() }
    }

    private fun drawAnnotations(canvas: Canvas, annotations: List<PdfAnnotation>, width: Float, height: Float) {
        annotations.forEach { annotation ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = annotationColor(annotation)
                alpha = (annotationOpacity(annotation) * 255).toInt().coerceIn(0, 255)
                style = Paint.Style.STROKE
                strokeWidth = annotationWidth(annotation).coerceAtLeast(.5f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            when (annotation) {
                is PdfInkAnnotation -> {
                    if (annotation.points.size == 1) {
                        val point = annotation.points.first()
                        canvas.drawCircle(point.x, point.y, annotation.width * point.pressure.coerceIn(.15f, 2f) / 2f, paint.apply { style = Paint.Style.FILL })
                    } else {
                        annotation.points.zipWithNext().forEach { (from, to) ->
                            paint.strokeWidth = annotation.width * ((from.pressure + to.pressure) / 2f).coerceIn(.15f, 2f)
                            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
                        }
                    }
                }
                is PdfHighlightAnnotation -> { paint.style = Paint.Style.FILL; annotation.bounds.forEach { canvas.drawRect(it.left, it.top, it.right, it.bottom, paint) } }
                is PdfTextAnnotation -> {
                    annotation.backgroundColorArgb?.let { background ->
                        paint.color = background
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(annotation.bounds.left, annotation.bounds.top, annotation.bounds.right, annotation.bounds.bottom, paint)
                    }
                    paint.color = annotation.colorArgb
                    paint.style = Paint.Style.FILL
                    paint.textSize = annotation.textSize
                    annotation.text.replace("\r", "").split('\n').forEachIndexed { index, line ->
                        canvas.drawText(line, annotation.bounds.left, annotation.bounds.top + annotation.textSize * (index + 1), paint)
                    }
                }
                is PdfShapeAnnotation -> {
                    if (annotation.fillColorArgb != null && annotation.shape in listOf(PdfShapeType.RECTANGLE, PdfShapeType.ELLIPSE)) {
                        paint.color = annotation.fillColorArgb
                        paint.style = Paint.Style.FILL
                        if (annotation.shape == PdfShapeType.RECTANGLE) canvas.drawRect(annotation.bounds.left, annotation.bounds.top, annotation.bounds.right, annotation.bounds.bottom, paint)
                        else canvas.drawOval(RectF(annotation.bounds.left, annotation.bounds.top, annotation.bounds.right, annotation.bounds.bottom), paint)
                        paint.color = annotation.colorArgb
                        paint.style = Paint.Style.STROKE
                    }
                    val fromX = annotation.start?.x ?: annotation.bounds.left
                    val fromY = annotation.start?.y ?: annotation.bounds.top
                    val toX = annotation.end?.x ?: annotation.bounds.right
                    val toY = annotation.end?.y ?: annotation.bounds.bottom
                    when (annotation.shape) {
                        PdfShapeType.LINE -> canvas.drawLine(fromX, fromY, toX, toY, paint)
                        PdfShapeType.ARROW -> {
                            canvas.drawLine(fromX, fromY, toX, toY, paint)
                            val angle = atan2(toY - fromY, toX - fromX)
                            val length = max(10f, annotation.width * 4f)
                            val head = Path().apply {
                                moveTo(toX, toY)
                                lineTo(toX - length * cos(angle - .5f), toY - length * sin(angle - .5f))
                                moveTo(toX, toY)
                                lineTo(toX - length * cos(angle + .5f), toY - length * sin(angle + .5f))
                            }
                            canvas.drawPath(head, paint)
                        }
                        PdfShapeType.RECTANGLE -> canvas.drawRect(annotation.bounds.left, annotation.bounds.top, annotation.bounds.right, annotation.bounds.bottom, paint)
                        PdfShapeType.ELLIPSE -> canvas.drawOval(RectF(annotation.bounds.left, annotation.bounds.top, annotation.bounds.right, annotation.bounds.bottom), paint)
                    }
                }
                is PdfImageAnnotation -> {
                    val bitmap = BitmapFactory.decodeByteArray(annotation.encodedImage, 0, annotation.encodedImage.size)
                    if (bitmap != null) {
                        paint.style = Paint.Style.FILL
                        val source = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                        val target = RectF(annotation.bounds.left, annotation.bounds.top, annotation.bounds.right, annotation.bounds.bottom)
                        canvas.drawBitmap(bitmap, source, target, paint)
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun annotationColor(annotation: PdfAnnotation): Int = when (annotation) {
        is PdfInkAnnotation -> annotation.colorArgb
        is PdfHighlightAnnotation -> annotation.colorArgb
        is PdfTextAnnotation -> annotation.colorArgb
        is PdfShapeAnnotation -> annotation.colorArgb
        is PdfImageAnnotation -> Color.BLACK
    }
    private fun annotationOpacity(annotation: PdfAnnotation): Float = when (annotation) {
        is PdfInkAnnotation -> annotation.opacity
        is PdfHighlightAnnotation -> annotation.opacity
        is PdfTextAnnotation -> annotation.opacity
        is PdfShapeAnnotation -> annotation.opacity
        is PdfImageAnnotation -> annotation.opacity
    }
    private fun annotationWidth(annotation: PdfAnnotation): Float = when (annotation) {
        is PdfInkAnnotation -> annotation.width
        is PdfShapeAnnotation -> annotation.width
        else -> 1f
    }
}
