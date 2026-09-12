package com.inkora.domain.model

import kotlinx.serialization.Serializable

/** Stable identifier for a document. UUID generation is supplied by the caller. */
@Serializable
@JvmInline
public value class DocumentId(public val value: String)

@Serializable
@JvmInline
public value class FolderId(public val value: String)

@Serializable
@JvmInline
public value class PageId(public val value: String)

@Serializable
@JvmInline
public value class AnnotationId(public val value: String)

@Serializable
public enum class DocumentType {
    NOTEBOOK,
    PDF,
    WHITEBOARD,
    TEXT_DOCUMENT,
    QUICK_NOTE,
}

@Serializable
public enum class SyncStatus {
    LOCAL_ONLY,
    SYNCED,
    SYNCING,
    SYNC_ERROR,
    CONFLICT,
}

@Serializable
public data class DocumentSummary(
    val id: DocumentId,
    val type: DocumentType,
    val title: String,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val pageCount: Int = 0,
    val folderId: FolderId? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val thumbnailPath: String? = null,
    val sourceFileName: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)

@Serializable
public data class Folder(
    val id: FolderId,
    val name: String,
    val parentId: FolderId? = null,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
)

@Serializable
public data class DocumentInfo(
    val summary: DocumentSummary,
    val byteSize: Long = 0,
    val offlineAvailable: Boolean = true,
)

@Serializable
public sealed interface DocumentContent {
    public val summary: DocumentSummary

    @Serializable
    public data class Notebook(
        override val summary: DocumentSummary,
        val cover: NotebookCover = NotebookCover.DEFAULT,
        val paper: PaperSpec = PaperSpec(),
        val pages: List<Page> = emptyList(),
    ) : DocumentContent

    @Serializable
    public data class Pdf(
        override val summary: DocumentSummary,
        val managedFilePath: String,
        val bookmarks: List<Bookmark> = emptyList(),
        val annotations: List<Annotation> = emptyList(),
        val pageElements: Map<Int, List<com.inkora.drawing.CanvasElement>> = emptyMap(),
    ) : DocumentContent

    @Serializable
    public data class Whiteboard(
        override val summary: DocumentSummary,
        val objects: List<WhiteboardObject> = emptyList(),
        val elements: List<com.inkora.drawing.CanvasElement> = emptyList(),
    ) : DocumentContent

    @Serializable
    public data class TextDocument(
        override val summary: DocumentSummary,
        val markdown: String = "",
        /** Original imported Office file when this document came from DOCX/PPTX. */
        val sourceFilePath: String? = null,
    ) : DocumentContent

    @Serializable
    public data class QuickNote(
        override val summary: DocumentSummary,
        val text: String = "",
        val strokes: List<String> = emptyList(),
    ) : DocumentContent
}

@Serializable
public data class Page(
    val id: PageId,
    val documentId: DocumentId,
    val index: Int,
    val title: String? = null,
    val template: PaperTemplate = PaperTemplate.BLANK,
    val pageSpec: PaperSpec = PaperSpec(),
    val strokes: List<String> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val elements: List<com.inkora.drawing.CanvasElement> = emptyList(),
)

@Serializable
public enum class NotebookCover { DEFAULT, BLUE, GREEN, RED, PURPLE, DARK, CUSTOM }

@Serializable
public enum class PaperSize { A4, A5, LETTER, LEGAL, SQUARE, PRESENTATION, CUSTOM }

@Serializable
public enum class Orientation { PORTRAIT, LANDSCAPE }

@Serializable
public enum class PaperColor { WHITE, WARM_WHITE, CREAM, LIGHT_GRAY, DARK }

@Serializable
public data class PaperSpec(
    val size: PaperSize = PaperSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val color: PaperColor = PaperColor.WHITE,
    val customWidth: Float? = null,
    val customHeight: Float? = null,
)

/** Portable template payload used by the Import template action. */
@Serializable
public data class ImportedTemplate(
    val name: String = "Imported template",
    val baseTemplate: PaperTemplate = PaperTemplate.BLANK,
    val paper: PaperSpec = PaperSpec(),
)

@Serializable
public enum class PaperTemplate {
    BLANK,
    RULED,
    COLLEGE_RULED,
    WIDE_RULED,
    GRID,
    SMALL_GRID,
    DOTTED,
    CORNELL_NOTES,
    CHECKLIST,
    MUSIC_SHEET,
    PLANNER,
    LECTURE_NOTES,
    CONCEPT_MAP,
    MIND_MAP,
    STUDY_PLANNER,
    WEEKLY_PLANNER,
    DAILY_PLANNER,
    ASSIGNMENT_TRACKER,
    FLASHCARD_SHEET,
    LAB_NOTES,
    MEDICATION_NOTES,
    EXAM_REVIEW,
    CUSTOM,
}

@Serializable
public sealed interface WhiteboardObject {
    public val id: String

    @Serializable
    public data class StrokeObject(override val id: String, val strokeId: String) : WhiteboardObject

    @Serializable
    public data class TextObject(override val id: String, val text: String, val x: Float, val y: Float) : WhiteboardObject

    @Serializable
    public data class ImageObject(
        override val id: String,
        val assetPath: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) : WhiteboardObject
}
