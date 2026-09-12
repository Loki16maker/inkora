package com.inkora.pdf

import com.inkora.platform.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/** PDFBox 3 reader. Every operation on an open PDF is serialized, including close and export. */
class PdfBoxPdfEngine : PdfEngine {
    private data class OpenPdf(val pdf: PDDocument, val source: File, val mutex: Mutex = Mutex())
    private val documents = ConcurrentHashMap<String, OpenPdf>()

    override suspend fun openDocument(source: PlatformFile): PdfDocument = withContext(Dispatchers.IO) {
        val file = File(source.path).canonicalFile
        require(file.isFile) { "PDF source does not exist: ${source.path}" }
        val pdf = Loader.loadPDF(file)
        if (pdf.numberOfPages == 0) { pdf.close(); error("This PDF has no pages") }
        val id = UUID.randomUUID().toString()
        documents[id] = OpenPdf(pdf, file)
        PdfDocument(id, source, pdf.numberOfPages)
    }

    override suspend fun closeDocument(document: PdfDocument) {
        withContext(Dispatchers.IO) {
            val open = documents[document.id] ?: return@withContext
            open.mutex.withLock { if (documents.remove(document.id, open)) open.pdf.close() }
        }
    }

    private suspend fun <T> withPdf(document: PdfDocument, block: suspend (OpenPdf) -> T): T = withContext(Dispatchers.IO) {
        val open = documents[document.id] ?: error("PDF document is closed")
        open.mutex.withLock {
            check(documents[document.id] === open) { "PDF document is closed" }
            block(open)
        }
    }

    override suspend fun pageSize(document: PdfDocument, pageIndex: Int): PdfPageSize = withPdf(document) { open ->
        require(pageIndex in 0 until open.pdf.numberOfPages)
        val page = open.pdf.getPage(pageIndex)
        pdfDisplaySize(page.cropBox.width, page.cropBox.height, page.rotation)
    }

    override suspend fun renderPage(document: PdfDocument, pageIndex: Int, request: PdfRenderRequest): PdfRenderedPage = withPdf(document) { open ->
        require(pageIndex in 0 until open.pdf.numberOfPages)
        val page = open.pdf.getPage(pageIndex)
        val size = pdfDisplaySize(page.cropBox.width, page.cropBox.height, page.rotation)
        val (width, height) = pdfRenderDimensions(size, request)
        val image = PDFRenderer(open.pdf).renderImage(pageIndex, minOf(width / size.width, height / size.height), ImageType.RGB)
        try {
            val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
            PdfRenderedPage(pageIndex, image.width, image.height, bytes)
        } finally { image.flush() }
    }

    override suspend fun renderThumbnail(document: PdfDocument, pageIndex: Int, longestSidePx: Int): PdfRenderedPage {
        require(longestSidePx > 0)
        return renderPage(document, pageIndex, PdfRenderRequest(targetWidthPx = longestSidePx, targetHeightPx = longestSidePx))
    }

    private fun text(pdf: PDDocument, pageIndex: Int?): String {
        val stripper = PDFTextStripper()
        if (pageIndex != null) {
            require(pageIndex in 0 until pdf.numberOfPages)
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
        }
        return stripper.getText(pdf)
    }

    override suspend fun extractText(document: PdfDocument, pageIndex: Int?): String = withPdf(document) { text(it.pdf, pageIndex) }

    override suspend fun search(document: PdfDocument, query: String, caseSensitive: Boolean): List<PdfSearchMatch> = withPdf(document) { open ->
        if (query.isBlank()) return@withPdf emptyList()
        buildList {
            for (pageIndex in 0 until open.pdf.numberOfPages) {
                currentCoroutineContext().ensureActive()
                addAll(pdfSearchMatches(pageIndex, text(open.pdf, pageIndex), query, caseSensitive))
            }
        }
    }

    override suspend fun outline(document: PdfDocument): List<PdfOutlineItem> = withPdf(document) { open ->
        val root = open.pdf.documentCatalog.documentOutline ?: return@withPdf emptyList()
        val seen = HashSet<org.apache.pdfbox.cos.COSDictionary>()
        fun walk(parent: PDOutlineNode, depth: Int): List<PdfOutlineItem> {
            if (depth > 64) return emptyList()
            val result = mutableListOf<PdfOutlineItem>()
            var item = parent.firstChild
            while (item != null && seen.add(item.cosObject)) {
                val current = item
                val page = runCatching { current.findDestinationPage(open.pdf) }.getOrNull()
                val index = page?.let { open.pdf.pages.indexOf(it).takeIf { value -> value >= 0 } }
                result += PdfOutlineItem(current.title ?: "Untitled", index, walk(current, depth + 1))
                item = current.nextSibling
            }
            return result
        }
        walk(root, 0)
    }

    override suspend fun exportAnnotatedPdf(
        document: PdfDocument,
        annotations: List<PdfAnnotation>,
        destination: PlatformFile,
        selectedPages: Set<Int>?,
    ): PlatformFile = withPdf(document) { open ->
        val target = File(destination.path).canonicalFile
        require(target != open.source) { "Export must create a different file; the source PDF is read-only" }
        val pages = pdfExportPages(open.pdf.numberOfPages, selectedPages)
        annotations.forEach { require(it.pageIndex in 0 until open.pdf.numberOfPages) { "Annotation page is out of range" } }
        target.parentFile?.mkdirs()
        val staged = File.createTempFile("inkora-export-", ".pdf", target.parentFile)
        try {
            Loader.loadPDF(open.source).use { output ->
                val keep = pages.toSet()
                for (index in output.numberOfPages - 1 downTo 0) if (index !in keep) output.removePage(index)
                if (pages.size != open.pdf.numberOfPages) output.documentCatalog.documentOutline = null
                val writer = PdfAnnotationWriter(output)
                pages.forEachIndexed { outputIndex, sourceIndex ->
                    currentCoroutineContext().ensureActive()
                    writer.append(output.getPage(outputIndex), annotations.filter { it.pageIndex == sourceIndex })
                }
                output.save(staged)
            }
            currentCoroutineContext().ensureActive()
            try {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { staged.delete() }
        destination.copy(sizeBytes = target.length(), mimeType = "application/pdf")
    }
}
