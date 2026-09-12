package com.inkora.domain.repository

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.Folder
import com.inkora.domain.model.FolderId
import com.inkora.domain.model.TrashEntry
import kotlinx.coroutines.flow.Flow

/** Source of truth for local document metadata and content snapshots. */
public interface DocumentRepository {
    public fun observeDocuments(includeTrashed: Boolean = false): Flow<List<DocumentSummary>>

    public fun observeFolders(includeTrashed: Boolean = false): Flow<List<Folder>>

    public suspend fun getDocument(id: DocumentId): DocumentContent?

    public suspend fun saveDocument(document: DocumentContent)

    public suspend fun createFolder(folder: Folder)

    public suspend fun renameFolder(id: FolderId, name: String)

    public suspend fun moveDocument(id: DocumentId, folderId: FolderId?)

    public suspend fun setFavorite(id: DocumentId, favorite: Boolean)

    public suspend fun moveToTrash(id: DocumentId, deletedAtEpochMs: Long)

    public suspend fun restoreFromTrash(id: DocumentId)

    public suspend fun permanentlyDelete(id: DocumentId)

    public suspend fun searchTitles(query: String): List<DocumentSummary>

    public suspend fun listTrash(): List<TrashEntry>
}
