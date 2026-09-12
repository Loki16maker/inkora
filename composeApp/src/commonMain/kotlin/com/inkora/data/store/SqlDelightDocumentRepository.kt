package com.inkora.data.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.inkora.database.generated.InkoraDatabase
import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.DocumentType
import com.inkora.domain.model.Folder
import com.inkora.domain.model.FolderId
import com.inkora.domain.model.SyncStatus
import com.inkora.domain.model.TrashEntry
import com.inkora.domain.repository.DocumentRepository
import com.inkora.platform.platformEpochMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** SQLDelight-backed repository. A platform supplies the appropriate SqlDriver. */
public class SqlDelightDocumentRepository(
    private val database: InkoraDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DocumentRepository {
    private val queries get() = database.inkoraQueries

    override fun observeDocuments(includeTrashed: Boolean): Flow<List<DocumentSummary>> =
        if (includeTrashed) {
            queries.selectAllDocuments().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map(::toSummary) }
        } else {
            queries.selectDocuments().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map(::toSummary) }
        }

    override fun observeFolders(includeTrashed: Boolean): Flow<List<Folder>> =
        if (includeTrashed) {
            queries.selectAllFolders().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map(::toFolder) }
        } else {
            queries.selectFolders().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map(::toFolder) }
        }

    override suspend fun getDocument(id: DocumentId): DocumentContent? =
        queries.selectDocumentById(id.value).executeAsOneOrNull()?.let { row ->
            runCatching { json.decodeFromString<DocumentContent>(row.metadata_json) }.getOrNull()
        }

    override suspend fun saveDocument(document: DocumentContent) {
        val summary = document.summary
        require(summary.title.isNotBlank()) { "Document title cannot be blank" }
        val existing = queries.selectDocumentById(summary.id.value).executeAsOneOrNull()
        val write: (String, String, String?, Long, Long, Long, Boolean, Boolean, String?, String?, String?, String, String, String) -> Unit = { type, title, folderId, created, modified, count, favorite, trashed, thumb, source, managed, sync, metadata, id ->
            if (existing == null) queries.insertDocument(id, type, title, folderId, created, modified, count, favorite, trashed, thumb, source, managed, sync, metadata)
            else queries.updateDocument(type, title, folderId, created, modified, count, favorite, trashed, thumb, source, managed, sync, metadata, id)
        }
        write(
            summary.type.name, summary.title, summary.folderId?.value,
            summary.createdAtEpochMs, summary.modifiedAtEpochMs, summary.pageCount.toLong(),
            summary.isFavorite, summary.isTrashed, summary.thumbnailPath, summary.sourceFileName,
            (document as? DocumentContent.Pdf)?.managedFilePath, summary.syncStatus.name,
            json.encodeToString(document), summary.id.value,
        )
    }

    override suspend fun createFolder(folder: Folder) {
        val existing = queries.selectAllFolders().executeAsList().firstOrNull { it.id == folder.id.value }
        if (existing == null) queries.insertFolder(folder.id.value, folder.name, folder.parentId?.value, folder.createdAtEpochMs, folder.modifiedAtEpochMs, folder.isFavorite, folder.isTrashed)
        else queries.updateFolder(folder.name, folder.parentId?.value, folder.createdAtEpochMs, folder.modifiedAtEpochMs, folder.isFavorite, folder.isTrashed, folder.id.value)
    }

    override suspend fun renameFolder(id: FolderId, name: String) {
        require(name.isNotBlank()) { "Folder name cannot be blank" }
        val folder = queries.selectAllFolders().executeAsList().firstOrNull { it.id == id.value } ?: return
        queries.updateFolder(name, folder.parent_id, folder.created_at, platformEpochMillis(), folder.is_favorite, folder.is_trashed, folder.id)
    }

    override suspend fun moveDocument(id: DocumentId, folderId: FolderId?) {
        updateDocumentSummary(id) { copy(folderId = folderId, modifiedAtEpochMs = platformEpochMillis()) }
    }

    override suspend fun setFavorite(id: DocumentId, favorite: Boolean) {
        updateDocumentSummary(id) { copy(isFavorite = favorite, modifiedAtEpochMs = platformEpochMillis()) }
    }

    override suspend fun moveToTrash(id: DocumentId, deletedAtEpochMs: Long) {
        val row = queries.selectDocumentById(id.value).executeAsOneOrNull() ?: return
        val folder = row.folder_id
        updateDocumentSummary(id) { copy(isTrashed = true, modifiedAtEpochMs = deletedAtEpochMs) }
        if (queries.selectTrash().executeAsList().any { it.document_id == id.value }) {
            queries.updateTrashMetadata(deletedAtEpochMs, folder, null, id.value)
        } else {
            queries.insertTrashMetadata(id.value, deletedAtEpochMs, folder, null)
        }
    }

    override suspend fun restoreFromTrash(id: DocumentId) {
        val folder = queries.selectTrash().executeAsList().firstOrNull { it.document_id == id.value }?.original_folder_id
        updateDocumentSummary(id) {
            copy(isTrashed = false, folderId = folder?.let(::FolderId), modifiedAtEpochMs = platformEpochMillis())
        }
        queries.deleteTrashMetadata(id.value)
    }

    override suspend fun permanentlyDelete(id: DocumentId) {
        queries.deleteDocument(id.value)
    }

    override suspend fun searchTitles(query: String): List<DocumentSummary> =
        query.trim().takeIf { it.isNotEmpty() }?.let { normalized ->
            queries.searchDocuments(normalized).executeAsList().map(::toSummary)
        } ?: emptyList()

    override suspend fun listTrash(): List<TrashEntry> = queries.selectTrash().executeAsList().map {
        TrashEntry(DocumentId(it.document_id), it.deleted_at, it.original_folder_id?.let(::FolderId), it.purge_after)
    }

    private fun toSummary(row: com.inkora.database.Documents): DocumentSummary = DocumentSummary(
        id = DocumentId(row.id),
        type = enumOrDefault(row.type, DocumentType.NOTEBOOK),
        title = row.title,
        createdAtEpochMs = row.created_at,
        modifiedAtEpochMs = row.modified_at,
        pageCount = row.page_count.toInt(),
        folderId = row.folder_id?.let(::FolderId),
        isFavorite = row.is_favorite,
        isTrashed = row.is_trashed,
        thumbnailPath = row.thumbnail_path,
        sourceFileName = row.source_file_name,
        syncStatus = enumOrDefault(row.sync_status, SyncStatus.LOCAL_ONLY),
    )

    private fun toFolder(row: com.inkora.database.Folders): Folder = Folder(
        id = FolderId(row.id), name = row.name, parentId = row.parent_id?.let(::FolderId),
        createdAtEpochMs = row.created_at, modifiedAtEpochMs = row.modified_at,
        isFavorite = row.is_favorite, isTrashed = row.is_trashed,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private suspend fun updateDocumentSummary(
        id: DocumentId,
        transform: DocumentSummary.() -> DocumentSummary,
    ) {
        val row = queries.selectDocumentById(id.value).executeAsOneOrNull() ?: return
        val content = runCatching { json.decodeFromString<DocumentContent>(row.metadata_json) }.getOrNull()
        if (content != null) {
            saveDocument(content.withSummary(transform))
        } else {
            // Preserve rows created by older builds whose content snapshot is unavailable.
            // Their queryable metadata still follows the same summary transition.
            val updated = toSummary(row).transform()
            queries.updateDocumentSummary(
                folder_id = updated.folderId?.value,
                modified_at = updated.modifiedAtEpochMs,
                is_favorite = updated.isFavorite,
                is_trashed = updated.isTrashed,
                id = id.value,
            )
        }
    }

    private fun DocumentContent.withSummary(transform: DocumentSummary.() -> DocumentSummary): DocumentContent = when (this) {
        is DocumentContent.Notebook -> copy(summary = summary.transform())
        is DocumentContent.Pdf -> copy(summary = summary.transform())
        is DocumentContent.Whiteboard -> copy(summary = summary.transform())
        is DocumentContent.TextDocument -> copy(summary = summary.transform())
        is DocumentContent.QuickNote -> copy(summary = summary.transform())
    }
}
