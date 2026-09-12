package com.inkora

import com.inkora.data.store.DocumentEditorStore
import com.inkora.data.store.InMemoryDocumentRepository
import com.inkora.data.store.SaveState
import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.DocumentType
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentEditorStoreTest {
    @Test
    fun updatesAreAutosavedAfterDebounce() = runTest {
        val repository = InMemoryDocumentRepository()
        val summary = DocumentSummary(DocumentId("doc"), DocumentType.TEXT_DOCUMENT, "Notes", 1L, 1L)
        repository.saveDocument(DocumentContent.TextDocument(summary, "before"))
        val store = DocumentEditorStore(repository, this, autosaveDelayMs = 100L)
        store.load(summary.id)
        store.update { (it as DocumentContent.TextDocument).copy(markdown = "after") }
        advanceTimeBy(99L)
        assertEquals(SaveState.Idle, store.saveState.value)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals("after", (repository.getDocument(summary.id) as DocumentContent.TextDocument).markdown)
        assertEquals(SaveState.Saved, store.saveState.value)
    }

    @Test
    fun newerRevisionReplacesPendingAutosaveAndCloseCancelsIt() = runTest {
        val repository = RecordingRepository()
        val summary = DocumentSummary(DocumentId("doc"), DocumentType.TEXT_DOCUMENT, "Notes", 1L, 1L)
        repository.seed(DocumentContent.TextDocument(summary, "before"))
        val store = DocumentEditorStore(repository, this, autosaveDelayMs = 100L)
        store.load(summary.id)
        store.update { (it as DocumentContent.TextDocument).copy(markdown = "one") }
        store.update { (it as DocumentContent.TextDocument).copy(markdown = "two") }
        advanceTimeBy(99L)
        assertEquals(0, repository.saved.size)
        store.close()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, repository.saved.size)
    }

    @Test
    fun latestRevisionIsWhatAutosavePersists() = runTest {
        val repository = RecordingRepository()
        val summary = DocumentSummary(DocumentId("doc"), DocumentType.TEXT_DOCUMENT, "Notes", 1L, 1L)
        repository.seed(DocumentContent.TextDocument(summary, "before"))
        val store = DocumentEditorStore(repository, this, autosaveDelayMs = 100L)
        store.load(summary.id)
        store.update { (it as DocumentContent.TextDocument).copy(markdown = "one") }
        advanceTimeBy(50L)
        store.update { (it as DocumentContent.TextDocument).copy(markdown = "two") }
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(listOf("two"), repository.saved.map { (it as DocumentContent.TextDocument).markdown })
    }

    @Test
    fun failedAutosaveIsVisibleToTheEditor() = runTest {
        val repository = RecordingRepository(failSaves = true)
        val summary = DocumentSummary(DocumentId("doc"), DocumentType.TEXT_DOCUMENT, "Notes", 1L, 1L)
        repository.seed(DocumentContent.TextDocument(summary, "before"))
        val store = DocumentEditorStore(repository, this, autosaveDelayMs = 10L)
        store.load(summary.id)
        store.update { (it as DocumentContent.TextDocument).copy(markdown = "after") }
        advanceTimeBy(10L)
        runCurrent()
        assertEquals(SaveState.Failed("disk full"), store.saveState.value)
    }

    private class RecordingRepository(private val failSaves: Boolean = false) :
        com.inkora.domain.repository.DocumentRepository {
        private val delegate = InMemoryDocumentRepository()
        val saved = mutableListOf<DocumentContent>()

        suspend fun seed(document: DocumentContent) = delegate.saveDocument(document)

        override fun observeDocuments(includeTrashed: Boolean) = delegate.observeDocuments(includeTrashed)
        override fun observeFolders(includeTrashed: Boolean) = delegate.observeFolders(includeTrashed)
        override suspend fun getDocument(id: DocumentId) = delegate.getDocument(id)
        override suspend fun saveDocument(document: DocumentContent) {
            if (failSaves) error("disk full")
            saved += document
            delegate.saveDocument(document)
        }
        override suspend fun createFolder(folder: com.inkora.domain.model.Folder) = delegate.createFolder(folder)
        override suspend fun renameFolder(id: com.inkora.domain.model.FolderId, name: String) = delegate.renameFolder(id, name)
        override suspend fun moveDocument(id: DocumentId, folderId: com.inkora.domain.model.FolderId?) = delegate.moveDocument(id, folderId)
        override suspend fun setFavorite(id: DocumentId, favorite: Boolean) = delegate.setFavorite(id, favorite)
        override suspend fun moveToTrash(id: DocumentId, deletedAtEpochMs: Long) = delegate.moveToTrash(id, deletedAtEpochMs)
        override suspend fun restoreFromTrash(id: DocumentId) = delegate.restoreFromTrash(id)
        override suspend fun permanentlyDelete(id: DocumentId) = delegate.permanentlyDelete(id)
        override suspend fun searchTitles(query: String) = delegate.searchTitles(query)
        override suspend fun listTrash() = delegate.listTrash()
    }
}
