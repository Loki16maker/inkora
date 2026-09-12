package com.inkora.domain.model

import kotlinx.serialization.Serializable

@Serializable
public data class InkoraSettings(
    val darkMode: Boolean = false,
    val restoreLastWorkspace: Boolean = true,
    val autosaveDelayMs: Long = 500,
    val drawWithFinger: Boolean = false,
    val defaultPaper: PaperSpec = PaperSpec(),
    val defaultTemplate: PaperTemplate = PaperTemplate.BLANK,
    val defaultCover: NotebookCover = NotebookCover.DEFAULT,
)

@Serializable
public data class WorkspaceSession(
    val openDocumentIds: List<DocumentId> = emptyList(),
    val activeDocumentId: DocumentId? = null,
    val split: SplitWorkspace? = null,
)

@Serializable
public data class SplitWorkspace(
    val firstDocumentId: DocumentId,
    val secondDocumentId: DocumentId,
    val orientation: SplitOrientation = SplitOrientation.VERTICAL,
    val dividerFraction: Float = 0.5f,
)

@Serializable
public enum class SplitOrientation { VERTICAL, HORIZONTAL }

@Serializable
public data class TrashEntry(
    val documentId: DocumentId,
    val deletedAtEpochMs: Long,
    val originalFolderId: FolderId? = null,
    val purgeAfterEpochMs: Long? = null,
)
