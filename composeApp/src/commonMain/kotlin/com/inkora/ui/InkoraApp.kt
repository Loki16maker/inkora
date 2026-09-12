@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inkora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inkora.app.InkoraRuntime
import com.inkora.domain.model.*
import com.inkora.platform.platformEpochMillis
import com.inkora.platform.platformUuid
import com.inkora.platform.ShareResult
import com.inkora.platform.shareService

/** Application routes always use the local repository; there is no generated sample library. */
@Composable
fun InkoraApp(runtime: InkoraRuntime) {
    val state by runtime.workspace.collectAsState()
    val ready by runtime.ready.collectAsState()
    val error by runtime.error.collectAsState()
    val notice by runtime.notice.collectAsState()
    val busy by runtime.busy.collectAsState()
    val updateCheck by runtime.updateCheck.collectAsState()
    val updateBusy by runtime.updateBusy.collectAsState()
    InkoraTheme(darkTheme = state.dark) {
        Surface(Modifier.fillMaxSize()) {
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (state.primary == null) PersistentLibrary(runtime)
            else PersistentWorkspace(runtime, state)
            if (busy || updateBusy) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text(if (updateBusy) "Checking for updates…" else "Importing document…", Modifier.padding(16.dp)) }
            }
        }
        error?.let { message -> AlertDialog(onDismissRequest = { runtime.error.value = null }, title = { Text("Couldn’t complete the action") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { runtime.error.value = null }) { Text("OK") } }) }
        notice?.let { message -> AlertDialog(onDismissRequest = { runtime.notice.value = null }, title = { Text("Inkora") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { runtime.notice.value = null }) { Text("Done") } }) }
        updateCheck?.let { result ->
            UpdateDialog(
                result = result,
                busy = updateBusy,
                onDismiss = { runtime.updateCheck.value = null },
                onInstall = { manifest ->
                    runtime.updateCheck.value = null
                    runtime.run { runtime.installUpdate(manifest) }
                },
            )
        }
    }
}

@Composable
private fun PersistentLibrary(runtime: InkoraRuntime) {
    val documents by remember(runtime) { runtime.repository.observeDocuments(true) }.collectAsState(emptyList())
    val folders by remember(runtime) { runtime.repository.observeFolders() }.collectAsState(emptyList())
    val workspace by runtime.workspace.collectAsState()
    var section by remember { mutableStateOf("Documents") }
    var folder by remember { mutableStateOf<FolderId?>(null) }
    var query by remember { mutableStateOf("") }
    var createMenu by remember { mutableStateOf(false) }
    var createNotebook by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<DocumentSummary?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf<DocumentSummary?>(null) }
    var delete by remember { mutableStateOf<DocumentSummary?>(null) }
    var accountDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val filtered = documents.filter {
        it.isTrashed == (section == "Trash") && (section != "Favorites" || it.isFavorite) &&
            (folder == null || it.folderId == folder) && it.title.contains(query, ignoreCase = true)
    }.sortedByDescending { it.modifiedAtEpochMs }

    fun createNew(kind: String) {
        createMenu = false
        when (kind) {
            "Notebook" -> createNotebook = true
            "PDF or Office file" -> runtime.run { runtime.importDocument() }
            "Export backup" -> runtime.run {
                val file = runtime.exportBackup()
                when (shareService().share(file, "Inkora backup")) {
                    ShareResult.FAILED -> runtime.error.value = "Backup saved at ${file.path}, but the share window could not open."
                    ShareResult.UNAVAILABLE -> runtime.notice.value = "Backup saved at ${file.path}"
                    ShareResult.SHARED -> runtime.notice.value = "Backup exported"
                }
            }
            "Restore backup" -> runtime.run { runtime.importBackup() }
            "Check for updates" -> runtime.run { runtime.checkForUpdates() }
            "Account" -> accountDialog = true
            "Folder" -> { name = ""; folderDialog = true }
            else -> runtime.run {
                val now = platformEpochMillis()
                val type = when (kind) { "Whiteboard" -> DocumentType.WHITEBOARD; "Quick note" -> DocumentType.QUICK_NOTE; else -> DocumentType.TEXT_DOCUMENT }
                val summary = DocumentSummary(DocumentId(platformUuid()), type, "Untitled ${kind.lowercase()}", now, now, folderId = folder)
                runtime.create(when (type) {
                    DocumentType.WHITEBOARD -> DocumentContent.Whiteboard(summary)
                    DocumentType.QUICK_NOTE -> DocumentContent.QuickNote(summary)
                    else -> DocumentContent.TextDocument(summary)
                })
            }
        }
    }
    val sections = listOf("Documents" to InkoraSymbol.BOOK, "Favorites" to InkoraSymbol.STAR, "Trash" to InkoraSymbol.TRASH)
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 1000.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Surface(Modifier.width(224.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { InkoraIcon(InkoraSymbol.PEN, tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Text("inkora", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text("YOUR WORKSPACE", Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sections.forEach { (tab, icon) -> DrawingToolButton(tab, icon, section == tab && folder == null, modifier = Modifier.fillMaxWidth()) { section = tab; folder = null } }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("FOLDERS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { name = ""; folderDialog = true }, modifier = Modifier.semantics { contentDescription = "New folder" }) { InkoraIcon(InkoraSymbol.PLUS) }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        if (folders.isEmpty()) item { Text("Organize your subjects here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(folders, key = { it.id.value }) { item ->
                            TextButton(onClick = { folder = item.id; section = "Documents" }, modifier = Modifier.fillMaxWidth()) {
                                InkoraIcon(InkoraSymbol.FOLDER); Spacer(Modifier.width(8.dp)); Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InkoraIcon(InkoraSymbol.CHECK, tint = MaterialTheme.colorScheme.primary)
                        Column { Text("Saved on this device", style = MaterialTheme.typography.labelMedium); Text("Your space. Your ideas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (wide) "Library" else "inkora", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { runtime.setWorkspace(workspace.copy(dark = !workspace.dark)) }, modifier = Modifier.semantics { contentDescription = if (workspace.dark) "Switch to light theme" else "Switch to dark theme" }) {
                        InkoraIcon(if (workspace.dark) InkoraSymbol.SUN else InkoraSymbol.MOON)
                    }
                    if (wide) {
                        WorkspaceAction("Import file", InkoraSymbol.DOWNLOAD, { runtime.run { runtime.importDocument() } })
                        WorkspaceAction("Check for updates", InkoraSymbol.CHECK, { runtime.run { runtime.checkForUpdates() } })
                        WorkspaceAction("Account", InkoraSymbol.ACCOUNT, { accountDialog = true })
                    }
                    Box {
                        Button(onClick = { createMenu = true }, shape = RoundedCornerShape(12.dp)) { InkoraIcon(InkoraSymbol.PLUS); Spacer(Modifier.width(8.dp)); Text("Create") }
                        DropdownMenu(createMenu, { createMenu = false }) {
                            listOf("Notebook", "PDF or Office file", "Whiteboard", "Text document", "Quick note", "Folder", "Export backup", "Restore backup", "Check for updates", "Account").forEach { kind ->
                                DropdownMenuItem(text = { Text(kind) }, onClick = { createNew(kind) })
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!wide) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sections.forEach { (tab, _) -> FilterChip(section == tab, onClick = { section = tab; folder = null }, label = { Text(tab) }) }
                    if (section != "Trash") {
                        FilterChip(folder == null, onClick = { folder = null }, label = { Text("All folders") })
                        folders.forEach { item -> FilterChip(folder == item.id, onClick = { folder = item.id }, label = { Text(item.name) }) }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(folder?.let { selected -> folders.firstOrNull { it.id == selected }?.name } ?: when(section) { "Documents" -> "A little room to think."; "Favorites" -> "Keep the good ideas close."; else -> "Trash" }, style = MaterialTheme.typography.headlineSmall)
                    Text(if (section == "Trash") "Restore a document, or remove it permanently." else "Notebooks, PDFs and ideas — all in one place.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                        shape = RoundedCornerShape(12.dp), label = { Text("Search your documents") }, leadingIcon = { InkoraIcon(InkoraSymbol.SEARCH) })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 32.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} ${if (filtered.size == 1) "document" else "documents"}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Recently edited", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { InkoraIcon(if (section == "Trash") InkoraSymbol.TRASH else InkoraSymbol.BOOK, Modifier.size(36.dp), MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Text(if (query.isNotBlank()) "No matching documents" else if (section == "Trash") "Nothing in the trash" else if (section == "Favorites") "Your favorites will appear here" else "Start with a blank page", style = MaterialTheme.typography.titleLarge)
                        Text(if (section == "Favorites") "Use the star on a document to keep it close." else if (section == "Trash") "Deleted documents can be restored here." else "Write on the paper. Think beyond its edges.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (section == "Documents" && query.isBlank()) {
                            Button(onClick = { createNotebook = true }, shape = RoundedCornerShape(12.dp)) { InkoraIcon(InkoraSymbol.PLUS); Spacer(Modifier.width(8.dp)); Text("New notebook") }
                            WorkspaceAction("Import PDF or Office file", InkoraSymbol.DOWNLOAD, { runtime.run { runtime.importDocument() } })
                        }
                    }
                } else LazyVerticalGrid(GridCells.Adaptive(240.dp), Modifier.weight(1f), contentPadding = PaddingValues(if (wide) 32.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(filtered, key = { it.id.value }) { doc ->
                        Card(onClick = { if (!doc.isTrashed) runtime.run { runtime.open(doc.id.value) } },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Surface(Modifier.fillMaxWidth().height(112.dp).padding(8.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    InkoraIcon(when(doc.type) { DocumentType.PDF -> InkoraSymbol.PDF; DocumentType.WHITEBOARD -> InkoraSymbol.BOARD; DocumentType.QUICK_NOTE -> InkoraSymbol.NOTE; DocumentType.TEXT_DOCUMENT -> InkoraSymbol.TEXT; else -> InkoraSymbol.BOOK }, Modifier.size(40.dp), MaterialTheme.colorScheme.primary)
                                    Text(doc.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (doc.pageCount > 0) "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"} · Local" else "Saved locally", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (doc.isTrashed) {
                                        TextButton(onClick = { runtime.run { runtime.repository.restoreFromTrash(doc.id) } }) { Text("Restore") }
                                        TextButton(onClick = { delete = doc }) { Text("Delete") }
                                    } else {
                                        IconButton(onClick = { runtime.run { runtime.favorite(doc) } }, modifier = Modifier.semantics { contentDescription = if (doc.isFavorite) "Remove favorite" else "Add favorite" }) { InkoraIcon(InkoraSymbol.STAR, tint = if (doc.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                        var menu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Actions for ${doc.title}" }) { InkoraIcon(InkoraSymbol.MORE) }
                                            DropdownMenu(menu, { menu = false }) {
                                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; name = doc.title; rename = doc })
                                                DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menu = false; move = doc })
                                                DropdownMenuItem(text = { Text("Move to trash") }, onClick = { menu = false; runtime.run { runtime.trash(doc) } })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (createNotebook) NotebookCreationDialog({ createNotebook = false }) { draft ->
        createNotebook = false
        runtime.run {
            val now = platformEpochMillis()
            val id = DocumentId(platformUuid())
            val paper = draft.toDomainPaperSpec()
            val summary = DocumentSummary(id, DocumentType.NOTEBOOK, draft.title.trim().ifBlank { "Untitled notebook" }, now, now, pageCount = 1, folderId = folder)
            runtime.create(DocumentContent.Notebook(summary, draft.toDomainCover(), paper,
                listOf(Page(PageId(platformUuid()), id, 0, template = draft.toDomainTemplate(), pageSpec = paper, createdAtEpochMs = now, modifiedAtEpochMs = now))))
        }
    }
    if (rename != null || folderDialog) AlertDialog(onDismissRequest = { rename = null; folderDialog = false }, title = { Text(if (folderDialog) "New folder" else "Rename document") }, text = {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        val document = rename
        rename = null; folderDialog = false
        runtime.run {
            if (document != null) runtime.rename(document, name)
            else { val now = platformEpochMillis(); runtime.repository.createFolder(Folder(FolderId(platformUuid()), name.trim(), createdAtEpochMs = now, modifiedAtEpochMs = now)) }
        }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = { rename = null; folderDialog = false }) { Text("Cancel") } })
    move?.let { doc -> AlertDialog(onDismissRequest = { move = null }, title = { Text("Move ${doc.title}") }, text = {
        LazyColumn { item { TextButton(onClick = { move = null; runtime.run { runtime.repository.moveDocument(doc.id, null) } }) { Text("No folder") } }; items(folders) { target -> TextButton(onClick = { move = null; runtime.run { runtime.repository.moveDocument(doc.id, target.id) } }) { Text(target.name) } } }
    }, confirmButton = { TextButton(onClick = { move = null }) { Text("Cancel") } }) }
    delete?.let { doc -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Permanently delete ${doc.title}?") }, text = { Text("The saved document and its annotations will be removed. This cannot be undone.") }, confirmButton = { TextButton(onClick = { delete = null; runtime.run { runtime.repository.permanentlyDelete(doc.id) } }) { Text("Delete permanently") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
    if (accountDialog) CloudAccountDialog(runtime) { accountDialog = false }
}
