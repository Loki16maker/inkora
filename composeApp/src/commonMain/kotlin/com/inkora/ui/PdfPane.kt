package com.inkora.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inkora.app.InkoraRuntime
import com.inkora.domain.model.Bookmark
import com.inkora.domain.model.DocumentContent
import com.inkora.drawing.*
import com.inkora.pdf.*
import com.inkora.platform.*
import kotlinx.coroutines.*

/** Real PDF pages and editable objects share the same rotated page-space coordinate system. */
@Composable
fun PdfPane(runtime: InkoraRuntime, content: DocumentContent.Pdf, modifier: Modifier) {
    val id = content.summary.id.value
    val workspace by runtime.workspace.collectAsState()
    val currentContent by runtime.editor(id).document.collectAsState()
    val index = (workspace.pages[id] ?: 0).coerceIn(0, (content.summary.pageCount - 1).coerceAtLeast(0))
    var handle by remember(id) { mutableStateOf<PdfDocument?>(null) }
    var pageSize by remember(id, index) { mutableStateOf<PdfPageSize?>(null) }
    var bitmap by remember(id, index) { mutableStateOf<ImageBitmap?>(null) }
    var failure by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var showPages by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var bookmarksDialog by remember { mutableStateOf(false) }
    var outlines by remember { mutableStateOf<List<PdfOutlineItem>?>(null) }
    var exported by remember { mutableStateOf<PlatformFile?>(null) }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(id, retry) {
        failure = null
        var opened: PdfDocument? = null
        try {
            opened = runtime.pdf.openDocument(PlatformFile(content.managedFilePath, content.summary.sourceFileName ?: "document.pdf", "application/pdf"))
            handle = opened
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Unable to open PDF" }
        finally { handle = null; opened?.let { withContext(NonCancellable) { runtime.pdf.closeDocument(it) } } }
    }
    LaunchedEffect(handle, index, retry) {
        val document = handle ?: return@LaunchedEffect
        bitmap = null
        try {
            pageSize = runtime.pdf.pageSize(document, index)
            val rendered = runtime.pdf.renderPage(document, index, PdfRenderRequest(targetWidthPx = 1400))
            bitmap = withContext(Dispatchers.Default) { decodeImage(rendered.encodedImage) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Unable to render this page" }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WorkspaceAction(
                if (content.bookmarks.any { it.pageIndex == index }) "Bookmarked" else "Bookmark",
                InkoraSymbol.STAR,
                onClick = {
                    runtime.edit(id) {
                        val pdf = it as DocumentContent.Pdf
                        val existing = pdf.bookmarks.any { mark -> mark.pageIndex == index }
                        pdf.copy(bookmarks = if (existing) pdf.bookmarks.filter { mark -> mark.pageIndex != index }
                            else pdf.bookmarks + Bookmark(platformUuid(), pdf.summary.id, index, "Page ${index + 1}", createdAtEpochMs = platformEpochMillis()))
                    }
                },
            )
            WorkspaceAction("Bookmarks", InkoraSymbol.STAR, onClick = { bookmarksDialog = true })
            WorkspaceAction("Find text", InkoraSymbol.SEARCH, enabled = handle != null, onClick = { searchDialog = true })
            WorkspaceAction("Contents", InkoraSymbol.BOOK, enabled = handle != null, onClick = {
                handle?.let { doc -> runtime.run { outlines = runtime.pdf.outline(doc) } }
            })
            WorkspaceAction(
                if (exporting) "Exporting…" else "Export PDF",
                InkoraSymbol.DOWNLOAD,
                enabled = handle != null && !exporting,
                onClick = { exportDialog = true },
            )
            exported?.let { file ->
                WorkspaceAction("Share export", InkoraSymbol.DOWNLOAD, onClick = { runtime.run {
                    when (shareService().share(file, "Annotated PDF")) {
                        ShareResult.FAILED -> error("The export was saved, but the share window could not open: ${file.path}")
                        ShareResult.UNAVAILABLE -> runtime.notice.value = "Export saved at ${file.path}"
                        ShareResult.SHARED -> Unit
                    }
                } })
            }
        }
        val image = bitmap
        val size = pageSize
        if (failure != null) Column(Modifier.padding(20.dp)) {
            Text(failure ?: "PDF error", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }) { Text("Retry") }
        } else if (image == null || size == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else DrawingEditor("$id:pdf:$index", content.pageElements[index].orEmpty(), { elements ->
            runtime.edit(id) { (it as DocumentContent.Pdf).copy(pageElements = it.pageElements + (index to elements)) }
        }, size.width, size.height, Modifier.weight(1f), background = image,
            pageFooter = {
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = index > 0,
                        onClick = { runtime.setPage(id, index - 1) },
                        modifier = Modifier.semantics { contentDescription = "Previous page" },
                    ) { Text("←") }
                    FilledTonalButton(onClick = { showPages = true }) {
                        Text("Page ${index + 1} / ${handle?.pageCount ?: content.summary.pageCount}")
                    }
                    IconButton(
                        onClick = { showPages = true },
                        modifier = Modifier.semantics { contentDescription = "Open page overview" },
                    ) { InkoraIcon(InkoraSymbol.GRID) }
                    TextButton(
                        enabled = index + 1 < content.summary.pageCount,
                        onClick = { runtime.setPage(id, index + 1) },
                        modifier = Modifier.semantics { contentDescription = "Next page" },
                    ) { Text("→") }
                }
            })
    }

    val document = handle
    if (showPages && document != null) AlertDialog(onDismissRequest = { showPages = false }, title = { Text("Pages") }, text = {
        LazyColumn(Modifier.heightIn(max = 550.dp)) {
            items(document.pageCount, key = { it }) { page ->
                PdfThumbnail(runtime, document, page, page == index) { runtime.setPage(id, page); showPages = false }
            }
        }
    }, confirmButton = { TextButton(onClick = { showPages = false }) { Text("Close") } })
    if (searchDialog && document != null) PdfSearchDialog(runtime, document, { searchDialog = false }) { page -> runtime.setPage(id, page); searchDialog = false }
    if (bookmarksDialog) AlertDialog(onDismissRequest = { bookmarksDialog = false }, title = { Text("Bookmarks") }, text = {
        LazyColumn(Modifier.heightIn(max = 450.dp)) {
            if (content.bookmarks.isEmpty()) item { Text("Tap Bookmark on a page to add it here.") }
            items(content.bookmarks.sortedBy { it.pageIndex }, key = { it.id }) { mark ->
                TextButton(onClick = { runtime.setPage(id, mark.pageIndex); bookmarksDialog = false }) { Text(mark.title ?: "Page ${mark.pageIndex + 1}") }
            }
        }
    }, confirmButton = { TextButton(onClick = { bookmarksDialog = false }) { Text("Close") } })
    outlines?.let { entries -> AlertDialog(onDismissRequest = { outlines = null }, title = { Text("Table of contents") }, text = {
        LazyColumn(Modifier.heightIn(max = 450.dp)) {
            val flattened = flattenOutline(entries)
            if (flattened.isEmpty()) item { Text("This PDF has no table of contents.") }
            items(flattened) { entry -> TextButton(enabled = entry.second.pageIndex != null, onClick = { entry.second.pageIndex?.let { runtime.setPage(id, it) }; outlines = null }) { Text("${"  ".repeat(entry.first)}${entry.second.title}") } }
        }
    }, confirmButton = { TextButton(onClick = { outlines = null }) { Text("Close") } }) }

    if (exportDialog && document != null) AlertDialog(onDismissRequest = { exportDialog = false }, title = { Text("Export PDF & margin notes") }, text = { Text("Includes ink, highlights, shapes, text, sticky notes and images. Pages expand where needed to include notes outside the paper. Your original PDF stays unchanged.") }, confirmButton = {
        TextButton(onClick = { exportDialog = false; runtime.run {
            exporting = true
            try {
                runtime.flush()
                val latest = runtime.editor(id).document.value as DocumentContent.Pdf
                exported = exportPdf(runtime, document, latest, null)
                runtime.notice.value = "PDF exported successfully. Use Share export to open its location or share sheet.\n${exported?.path}"
            } finally { exporting = false }
        } }) { Text("All pages") }
    }, dismissButton = {
        TextButton(onClick = { exportDialog = false; runtime.run {
            exporting = true
            try {
                runtime.flush()
                exported = exportPdf(runtime, document, runtime.editor(id).document.value as DocumentContent.Pdf, setOf(index))
                runtime.notice.value = "Page ${index + 1} exported. Use Share export to access the PDF.\n${exported?.path}"
            } finally { exporting = false }
        } }) { Text("Current page") }
    })
}

@Composable
private fun PdfThumbnail(runtime: InkoraRuntime, document: PdfDocument, index: Int, selected: Boolean, onClick: () -> Unit) {
    var image by remember(document.id, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(document.id, index) {
        try { image = decodeImage(runtime.pdf.renderThumbnail(document, index, 180).encodedImage) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The page number remains available when a thumbnail cannot render. */ }
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        image?.let { Image(it, "Page ${index + 1}", Modifier.size(64.dp, 90.dp)) }
        Text("${if (selected) "● " else ""}Page ${index + 1}", Modifier.padding(12.dp))
    }
}

@Composable
private fun PdfSearchDialog(runtime: InkoraRuntime, document: PdfDocument, onDismiss: () -> Unit, onPage: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PdfSearchMatch>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Find in PDF") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Searchable text") })
            TextButton(enabled = query.isNotBlank(), onClick = {
                job?.cancel()
                job = scope.launch {
                    searching = true; error = null; completed = false
                    try { results = runtime.pdf.search(document, query); completed = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message }
                    finally { searching = false }
                }
            }) { Text("Find") }
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (completed && results.isEmpty()) Text("No matches. Scanned image PDFs need OCR before their text can be searched.")
            LazyColumn(Modifier.heightIn(max = 350.dp)) { items(results) { result -> TextButton(onClick = { onPage(result.pageIndex) }) { Text("Page ${result.pageIndex + 1} · ${result.snippet}") } } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

private fun flattenOutline(items: List<PdfOutlineItem>, depth: Int = 0): List<Pair<Int, PdfOutlineItem>> =
    items.flatMap { listOf(depth to it) + flattenOutline(it.children, depth + 1) }

private suspend fun exportPdf(runtime: InkoraRuntime, document: PdfDocument, content: DocumentContent.Pdf, selected: Set<Int>?): PlatformFile {
    val folder = runtime.files.createDirectory(runtime.files.child(runtime.files.appDataDirectory, "exports"))
    val title = content.summary.title.map { if (it.isLetterOrDigit() || it in " -_") it else '_' }.joinToString("").take(90)
    val destination = runtime.files.child(folder, "$title-annotated-${platformUuid()}.pdf")
    val annotations = content.pageElements.flatMap { (page, elements) -> elements.toPdfAnnotations(page, runtime.files) }
    return runtime.pdf.exportAnnotatedPdf(document, annotations, destination, selected)
}

/** Converts stored page objects without rasterizing either the original PDF or handwritten ink. */
suspend fun List<CanvasElement>.toPdfAnnotations(pageIndex: Int, files: PlatformFileSystem): List<PdfAnnotation> = map { element ->
    when (element) {
        is CanvasElement.Ink -> if (element.tool == CanvasInkTool.HIGHLIGHTER) {
            val bounds = element.bounds()
            PdfHighlightAnnotation(pageIndex,
                listOf(PdfRect(bounds.left, bounds.top, bounds.right, bounds.bottom)),
                element.colorArgb.toInt(), element.opacity)
        } else PdfInkAnnotation(pageIndex, element.points.map { PdfInkPoint(it.x, it.y, if (element.tool == CanvasInkTool.FOUNTAIN || element.tool == CanvasInkTool.PENCIL) it.pressure else 1f) }, element.colorArgb.toInt(), element.width, element.opacity)
        is CanvasElement.Shape -> PdfShapeAnnotation(pageIndex,
            PdfRect(minOf(element.start.x, element.end.x), minOf(element.start.y, element.end.y), maxOf(element.start.x, element.end.x), maxOf(element.start.y, element.end.y)),
            PdfShapeType.valueOf(element.kind.name), element.colorArgb.toInt(), element.width, element.opacity,
            start = PdfInkPoint(element.start.x, element.start.y), end = PdfInkPoint(element.end.x, element.end.y))
        is CanvasElement.Text -> {
            val bounds = element.bounds()
            PdfTextAnnotation(pageIndex, PdfRect(bounds.left, bounds.top, bounds.right, bounds.bottom), element.text, element.colorArgb.toInt(), element.fontSize,
                backgroundColorArgb = if (element.sticky) 0xFFFFF0A6.toInt() else null)
        }
        is CanvasElement.Image -> PdfImageAnnotation(pageIndex, PdfRect(element.x, element.y, element.x + element.width, element.y + element.height), files.read(PlatformFile(element.path, "image")))
    }
}
