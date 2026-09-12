package com.inkora

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.inkora.data.store.SqlDelightDocumentRepository
import com.inkora.database.generated.InkoraDatabase
import com.inkora.domain.model.Annotation
import com.inkora.domain.model.AnnotationId
import com.inkora.domain.model.AnnotationType
import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.DocumentType
import com.inkora.domain.model.Page
import com.inkora.domain.model.PageId
import com.inkora.drawing.CanvasElement
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlDelightDocumentRepositoryTest {
    @Test
    fun notebookSurvivesDriverRestartWithElementsAnnotationsAndMetadata() = runTest {
        val directory = Files.createTempDirectory("inkora-sqlite-test")
        val databasePath = directory.resolve("inkora.db")
        try {
            val first = open(databasePath)
            val documentId = DocumentId("durable-notebook")
            val pageId = PageId("page-1")
            val now = 10L
            val element = CanvasElement.Text(
                id = "text-1",
                x = 24f,
                y = 32f,
                text = "persisted",
                colorArgb = 0xFF202124,
                fontSize = 14f,
            )
            val annotation = Annotation(
                id = AnnotationId("annotation-1"),
                pageId = pageId,
                type = AnnotationType.STICKY_NOTE,
                payload = "{\"text\":\"remember\"}",
                createdAtEpochMs = now,
                modifiedAtEpochMs = now,
            )
            val summary = DocumentSummary(documentId, DocumentType.NOTEBOOK, "Biology", now, now, pageCount = 1)
            first.repository.saveDocument(
                DocumentContent.Notebook(
                    summary = summary,
                    pages = listOf(
                        Page(
                            id = pageId,
                            documentId = documentId,
                            index = 0,
                            annotations = listOf(annotation),
                            elements = listOf(element),
                            createdAtEpochMs = now,
                            modifiedAtEpochMs = now,
                        ),
                    ),
                ),
            )
            first.repository.setFavorite(documentId, favorite = true)
            first.repository.moveToTrash(documentId, deletedAtEpochMs = 20L)
            first.driver.close()

            val second = open(databasePath)
            val restoredSnapshot = second.repository.getDocument(documentId) as DocumentContent.Notebook
            assertTrue(restoredSnapshot.summary.isFavorite)
            assertTrue(restoredSnapshot.summary.isTrashed)
            assertEquals(element, restoredSnapshot.pages.single().elements.single())
            assertEquals(annotation, restoredSnapshot.pages.single().annotations.single())
            assertEquals(1, second.repository.listTrash().size)

            second.repository.restoreFromTrash(documentId)
            val restored = second.repository.getDocument(documentId)!!.summary
            assertFalse(restored.isTrashed)
            assertTrue(restored.isFavorite)
            assertEquals(0, second.repository.listTrash().size)
            second.driver.close()
        } finally {
            Files.deleteIfExists(databasePath)
            Files.deleteIfExists(directory)
        }
    }

    private fun open(path: Path): OpenedRepository {
        val newDatabase = !Files.exists(path) || Files.size(path) == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
        if (newDatabase) {
            InkoraDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = ${InkoraDatabase.Schema.version}", 0)
        }
        return OpenedRepository(SqlDelightDocumentRepository(InkoraDatabase(driver)), driver)
    }

    private data class OpenedRepository(
        val repository: SqlDelightDocumentRepository,
        val driver: JdbcSqliteDriver,
    )
}
