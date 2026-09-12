package com.inkora.data.store

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.Folder
import com.inkora.domain.model.FolderId
import com.inkora.domain.model.TrashEntry
import com.inkora.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A deterministic repository useful for previews, unit tests, and bootstrapping
 * a platform database. The same contract is implemented by the SQLDelight data
 * source in production wiring.
 */
public class InMemoryDocumentRepository : DocumentRepository {
    private val mutex = Mutex()
    private val documents = MutableStateFlow<Map<DocumentId, DocumentContent>>(emptyMap())
    private val folders = MutableStateFlow<Map<FolderId, Folder>>(emptyMap())
    private val trash = MutableStateFlow<Map<DocumentId, TrashEntry>>(emptyMap())

    override fun observeDocuments(includeTrashed: Boolean): Flow<List<DocumentSummary>> =
        documents.asStateFlow().map { values ->
            values.values
                .map { it.summary }
                .filter { includeTrashed || !it.isTrashed }
                .sortedWith(compareByDescending<DocumentSummary> { it.modifiedAtEpochMs }.thenBy { it.title.lowercase() })
        }

    override fun observeFolders(includeTrashed: Boolean): Flow<List<Folder>> =
        folders.asStateFlow().map { values ->
            values.values
                .filter { includeTrashed || !it.isTrashed }
                .sortedBy { it.name.lowercase() }
        }

    override suspend fun getDocument(id: DocumentId): DocumentContent? = mutex.withLock { documents.value[id] }

    override suspend fun saveDocument(document: DocumentContent) {
        require(document.summary.title.isNotBlank()) { "Document title cannot be blank" }
        mutex.withLock { documents.value = documents.value + (document.summary.id to document) }
    }

    override suspend fun createFolder(folder: Folder) {
        require(folder.name.isNotBlank()) { "Folder name cannot be blank" }
        mutex.withLock { folders.value = folders.value + (folder.id to folder) }
    }

    override suspend fun renameFolder(id: FolderId, name: String) {
        require(name.isNotBlank()) { "Folder name cannot be blank" }
        mutex.withLock {
            val folder = folders.value[id] ?: return@withLock
            folders.value = folders.value + (id to folder.copy(name = name, modifiedAtEpochMs = folder.modifiedAtEpochMs))
        }
    }

    override suspend fun moveDocument(id: DocumentId, folderId: FolderId?) {
        mutex.withLock {
            val document = documents.value[id] ?: return@withLock
            documents.value = documents.value + (id to document.withSummary { copy(folderId = folderId) })
        }
    }

    override suspend fun setFavorite(id: DocumentId, favorite: Boolean) {
        mutex.withLock {
            val document = documents.value[id] ?: return@withLock
            documents.value = documents.value + (id to document.withSummary { copy(isFavorite = favorite) })
        }
    }

    override suspend fun moveToTrash(id: DocumentId, deletedAtEpochMs: Long) {
        mutex.withLock {
            val document = documents.value[id] ?: return@withLock
            documents.value = documents.value + (id to document.withSummary { copy(isTrashed = true) })
            trash.value = trash.value + (id to TrashEntry(id, deletedAtEpochMs, document.summary.folderId))
        }
    }

    override suspend fun restoreFromTrash(id: DocumentId) {
        mutex.withLock {
            val document = documents.value[id] ?: return@withLock
            val originalFolder = trash.value[id]?.originalFolderId
            documents.value = documents.value + (id to document.withSummary {
                copy(isTrashed = false, folderId = originalFolder)
            })
            trash.value = trash.value - id
        }
    }

    override suspend fun permanentlyDelete(id: DocumentId) {
        mutex.withLock {
            documents.value = documents.value - id
            trash.value = trash.value - id
        }
    }

    override suspend fun searchTitles(query: String): List<DocumentSummary> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return documents.value.values
            .map { it.summary }
            .filter { !it.isTrashed && it.title.lowercase().contains(normalized) }
            .sortedBy { it.title.lowercase() }
    }

    override suspend fun listTrash(): List<TrashEntry> = trash.value.values.sortedByDescending { it.deletedAtEpochMs }

    private fun DocumentContent.withSummary(transform: DocumentSummary.() -> DocumentSummary): DocumentContent = when (this) {
        is DocumentContent.Notebook -> copy(summary = summary.transform())
        is DocumentContent.Pdf -> copy(summary = summary.transform())
        is DocumentContent.Whiteboard -> copy(summary = summary.transform())
        is DocumentContent.TextDocument -> copy(summary = summary.transform())
        is DocumentContent.QuickNote -> copy(summary = summary.transform())
    }
}
