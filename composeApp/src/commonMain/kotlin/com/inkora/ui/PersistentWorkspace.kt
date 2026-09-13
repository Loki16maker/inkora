@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inkora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation as DragOrientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.inkora.app.*
import com.inkora.data.store.SaveState
import com.inkora.domain.model.*
import com.inkora.platform.platformEpochMillis
import com.inkora.platform.platformUuid
import com.inkora.study.studyText
import com.inkora.study.ReviewSchedule

private data class QuizSource(val documentId: String, val title: String, val text: String, val schedule: ReviewSchedule)

@Composable
fun PersistentWorkspace(runtime: InkoraRuntime, workspace: WorkspaceState) {
    val primary = workspace.primary ?: return
    val documents by remember(runtime) { runtime.repository.observeDocuments() }.collectAsState(emptyList())
    var chooseSecond by remember { mutableStateOf(false) }
    var quizSource by remember { mutableStateOf<QuizSource?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkspaceAction("Library", InkoraSymbol.BACK, { runtime.run { runtime.library() } })
            VerticalDivider(Modifier.height(24.dp))
            Text(if (workspace.secondary == null) "Open canvas" else "Study workspace", style = MaterialTheme.typography.titleSmall)
            WorkspaceAction(if (workspace.secondary == null) "Study split" else "Change document", InkoraSymbol.SPLIT, { chooseSecond = true })
            WorkspaceAction("Quiz", InkoraSymbol.CHECK, {
                runtime.run {
                    val document = runtime.editor(primary).document.value
                    if (document != null) quizSource = QuizSource(primary, document.summary.title, runtime.studyText(document), runtime.loadReviewSchedule(primary))
                }
            })
            if (workspace.secondary != null) {
                TextButton(onClick = { runtime.setWorkspace(workspace.copy(stacked = !workspace.stacked)) }) { Text(if (workspace.stacked) "Side by side" else "Stacked") }
                TextButton(onClick = { runtime.setWorkspace(workspace.copy(primary = workspace.secondary, secondary = primary)) }) { Text("Swap") }
                TextButton(onClick = { runtime.run { runtime.flush(); runtime.setWorkspace(workspace.copy(secondary = null)) } }) { Text("Close split") }
            }
            WorkspaceAction("Save all", InkoraSymbol.CHECK, { runtime.run { runtime.flush() } })
            IconButton(onClick = { runtime.setWorkspace(runtime.workspace.value.copy(dark = !workspace.dark)) }, modifier = Modifier.semantics { contentDescription = "Toggle light or dark theme" }) { InkoraIcon(if (workspace.dark) InkoraSymbol.SUN else InkoraSymbol.MOON) }
        }
        HorizontalDivider()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val second = workspace.secondary
            val stacked = workspace.stacked || maxWidth < 700.dp
            val fraction = workspace.fraction.coerceIn(.25f, .75f)
            val density = LocalDensity.current
            val extent = with(density) { (if (stacked) maxHeight else maxWidth).toPx() }.coerceAtLeast(1f)
            val divider: @Composable () -> Unit = {
                Box(Modifier.then(if (stacked) Modifier.fillMaxWidth().height(14.dp) else Modifier.fillMaxHeight().width(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .draggable(rememberDraggableState { delta ->
                        val current = runtime.workspace.value
                        runtime.setWorkspace(current.copy(fraction = (current.fraction + delta / extent).coerceIn(.25f, .75f)))
                    }, if (stacked) DragOrientation.Vertical else DragOrientation.Horizontal), contentAlignment = Alignment.Center) {
                    Box(Modifier.then(if (stacked) Modifier.width(32.dp).height(3.dp) else Modifier.width(3.dp).height(32.dp)).background(MaterialTheme.colorScheme.outline))
                }
            }
            if (second == null) key(primary) { PersistentDocumentPane(runtime, primary, Modifier.fillMaxSize()) }
            else if (stacked) Column(Modifier.fillMaxSize()) {
                key(primary) { PersistentDocumentPane(runtime, primary, Modifier.weight(fraction).fillMaxWidth()) }
                divider()
                key(second) { PersistentDocumentPane(runtime, second, Modifier.weight(1f - fraction).fillMaxWidth()) }
            } else Row(Modifier.fillMaxSize()) {
                key(primary) { PersistentDocumentPane(runtime, primary, Modifier.weight(fraction).fillMaxHeight()) }
                divider()
                key(second) { PersistentDocumentPane(runtime, second, Modifier.weight(1f - fraction).fillMaxHeight()) }
            }
        }
    }
    if (chooseSecond) AlertDialog(onDismissRequest = { chooseSecond = false }, title = { Text("Choose the second document") }, text = {
        LazyColumn(Modifier.heightIn(max = 450.dp)) {
            items(documents.filter { it.id.value != primary }, key = { it.id.value }) { doc ->
                TextButton(onClick = { chooseSecond = false; runtime.run { runtime.study(doc.id.value) } }, modifier = Modifier.fillMaxWidth()) { Text("${doc.title} · ${doc.type.name.lowercase().replace('_', ' ')}") }
            }
            if (documents.size < 2) item { Text("Create a notebook or import another PDF from the library first.") }
        }
    }, confirmButton = { TextButton(onClick = { chooseSecond = false }) { Text("Cancel") } })
    quizSource?.let { source ->
        QuizDialog(source.title, source.text, source.schedule, onScheduleChange = { schedule ->
            runtime.run { runtime.saveReviewSchedule(source.documentId, schedule) }
        }, onGenerateAiQuiz = { text, count, difficulty -> runtime.generateAiQuiz(text, count, difficulty) }) { quizSource = null }
    }
}

@Composable
private fun PersistentDocumentPane(runtime: InkoraRuntime, id: String, modifier: Modifier) {
    val store = remember(runtime, id) { runtime.editor(id) }
    val content by store.document.collectAsState()
    val saved by store.saveState.collectAsState()
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(content?.summary?.title ?: "Loading…", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val label = when (saved) {
                SaveState.Idle -> "Saved locally"
                SaveState.Saved -> "Saved locally"
                SaveState.Saving -> "Saving…"
                is SaveState.Failed -> "Save failed · Retry"
            }
            TextButton(onClick = { runtime.run { store.saveNow(); (store.saveState.value as? SaveState.Failed)?.let { runtime.error.value = it.message } } }) {
                InkoraIcon(
                    if (saved is SaveState.Failed) InkoraSymbol.ERASE else InkoraSymbol.CHECK,
                    Modifier.size(16.dp),
                    if (saved is SaveState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (saved is SaveState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
        when (val document = content) {
            is DocumentContent.Notebook -> NotebookPane(runtime, document, Modifier.weight(1f))
            is DocumentContent.Pdf -> PdfPane(runtime, document, Modifier.weight(1f))
            is DocumentContent.Whiteboard -> DrawingEditor(id, document.elements, { elements -> runtime.edit(id) { (it as DocumentContent.Whiteboard).copy(elements = elements) } }, 3200f, 2400f, Modifier.weight(1f))
            is DocumentContent.TextDocument -> RichTextEditor(document.markdown, { text -> runtime.edit(id) { (it as DocumentContent.TextDocument).copy(markdown = text) } }, Modifier.weight(1f).fillMaxWidth())
            is DocumentContent.QuickNote -> OutlinedTextField(document.text, { text -> runtime.edit(id) { (it as DocumentContent.QuickNote).copy(text = text) } }, Modifier.fillMaxWidth().weight(1f).padding(16.dp), placeholder = { Text("Capture a quick thought…") })
            null -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun NotebookPane(runtime: InkoraRuntime, notebook: DocumentContent.Notebook, modifier: Modifier) {
    val id = notebook.summary.id.value
    val workspace by runtime.workspace.collectAsState()
    val index = (workspace.pages[id] ?: 0).coerceIn(0, (notebook.pages.size - 1).coerceAtLeast(0))
    var templateMenu by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val page = notebook.pages.getOrNull(index)
    fun changePages(transform: (List<Page>) -> List<Page>) {
        runtime.edit(id) {
            val current = it as DocumentContent.Notebook
            val pages = transform(current.pages).mapIndexed { number, item -> item.copy(index = number) }
            current.copy(pages = pages, summary = current.summary.copy(pageCount = pages.size))
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = index > 0, onClick = { runtime.setPage(id, index - 1) }, modifier = Modifier.semantics { contentDescription = "Previous page" }) { Text("‹") }
            Box {
                TextButton(onClick = { pageMenu = true }) { Text("Page ${index + 1} / ${notebook.pages.size}") }
                DropdownMenu(pageMenu, { pageMenu = false }) { notebook.pages.forEachIndexed { number, entry -> DropdownMenuItem(text = { Text("${number + 1} · ${entry.template.name.lowercase().replace('_', ' ')}") }, onClick = { pageMenu = false; runtime.setPage(id, number) }) } }
            }
            TextButton(enabled = index < notebook.pages.lastIndex, onClick = { runtime.setPage(id, index + 1) }, modifier = Modifier.semantics { contentDescription = "Next page" }) { Text("›") }
            TextButton(onClick = {
                val now = platformEpochMillis()
                val added = Page(PageId(platformUuid()), notebook.summary.id, notebook.pages.size, template = page?.template ?: PaperTemplate.BLANK, pageSpec = page?.pageSpec ?: notebook.paper, createdAtEpochMs = now, modifiedAtEpochMs = now)
                changePages { it + added }; runtime.setPage(id, notebook.pages.size)
            }) { Text("+ Page") }
            TextButton(enabled = page != null, onClick = {
                page?.let { original ->
                    val now = platformEpochMillis()
                    changePages { it.toMutableList().apply { add(index + 1, original.copy(id = PageId(platformUuid()), createdAtEpochMs = now, modifiedAtEpochMs = now)) } }
                    runtime.setPage(id, index + 1)
                }
            }) { Text("Duplicate") }
            TextButton(enabled = index > 0, onClick = {
                changePages { it.toMutableList().apply { add(index - 1, removeAt(index)) } }; runtime.setPage(id, index - 1)
            }) { Text("Move earlier") }
            TextButton(enabled = notebook.pages.size > 1, onClick = {
                changePages { it.filterIndexed { number, _ -> number != index } }; runtime.setPage(id, (index - 1).coerceAtLeast(0))
            }) { Text("Delete page") }
            Box {
                TextButton(onClick = { templateMenu = true }) { Text("Template") }
                DropdownMenu(templateMenu, { templateMenu = false }) {
                    PaperTemplate.entries.filter { it != PaperTemplate.CUSTOM }.forEach { template -> DropdownMenuItem(text = { Text(template.name.lowercase().replace('_', ' ')) }, onClick = {
                        templateMenu = false
                        changePages { it.mapIndexed { number, entry -> if (number == index) entry.copy(template = template) else entry } }
                    }) }
                }
            }
            TextButton(onClick = {
                runtime.run {
                    runtime.importTemplate()?.let { imported ->
                        changePages { pages -> pages.mapIndexed { number, entry -> if (number == index) entry.copy(template = imported.baseTemplate, pageSpec = imported.paper) else entry } }
                        runtime.notice.value = "Template applied · ${imported.name}"
                    }
                }
            }) { Text("Import template") }
        }
        if (page != null) {
            val (width, height) = page.pageSpec.dimensions()
            val color = when (page.pageSpec.color) {
                com.inkora.domain.model.PaperColor.WHITE -> Color.White
                com.inkora.domain.model.PaperColor.WARM_WHITE -> Color(0xFFFFFDF8)
                com.inkora.domain.model.PaperColor.CREAM -> Color(0xFFFFF3D6)
                com.inkora.domain.model.PaperColor.LIGHT_GRAY -> Color(0xFFF0F2F2)
                com.inkora.domain.model.PaperColor.DARK -> Color(0xFF202124)
            }
            DrawingEditor(page.id.value, page.elements, { elements ->
                changePages { pages -> pages.map { entry -> if (entry.id == page.id) entry.copy(elements = elements, modifiedAtEpochMs = platformEpochMillis()) else entry } }
            }, width, height, Modifier.weight(1f), template = page.template, paperColor = color)
        }
    }
}
