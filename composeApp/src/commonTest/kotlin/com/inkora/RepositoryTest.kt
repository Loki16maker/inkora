package com.inkora

import com.inkora.data.store.InMemoryDocumentRepository
import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.DocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryTest {
    @Test
    fun documentLifecycleIsObservableAndRestorable() = runTest {
        val repository = InMemoryDocumentRepository()
        val id = DocumentId("notebook-1")
        val summary = DocumentSummary(id, DocumentType.NOTEBOOK, "Physics", 1L, 2L, pageCount = 1)
        repository.saveDocument(DocumentContent.TextDocument(summary, "notes"))
        assertEquals("Physics", repository.observeDocuments().first().single().title)

        repository.moveToTrash(id, deletedAtEpochMs = 3L)
        assertTrue(repository.observeDocuments().first().isEmpty())
        repository.restoreFromTrash(id)
        assertEquals(id, repository.observeDocuments().first().single().id)
    }

    @Test
    fun searchIncludesStoredDocumentText() = runTest {
        val repository = InMemoryDocumentRepository()
        val id = DocumentId("notes-1")
        val summary = DocumentSummary(id, DocumentType.TEXT_DOCUMENT, "Lecture notes", 1L, 2L)
        repository.saveDocument(DocumentContent.TextDocument(summary, "Renal physiology and filtration"))

        assertEquals(listOf(id), repository.searchTitles("FILTRATION").map { it.id })
        assertTrue(repository.searchTitles("missing").isEmpty())
    }
}
