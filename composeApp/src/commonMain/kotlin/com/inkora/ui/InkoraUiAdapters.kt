package com.inkora.ui

import androidx.compose.ui.graphics.Color
import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.DocumentType
import com.inkora.domain.model.NotebookCover
import com.inkora.domain.model.Orientation
import com.inkora.domain.model.PaperColor
import com.inkora.domain.model.PaperSize
import com.inkora.domain.model.PaperSpec
import com.inkora.domain.model.PaperTemplate

/** Boundary mapping keeps the shared UI independent of repository and generated DB types. */
fun DocumentSummary.toInkoraUiDocument(): InkoraDocument = InkoraDocument(
    id = id.value,
    title = title,
    type = when (type) {
        DocumentType.NOTEBOOK -> InkoraDocumentType.Notebook
        DocumentType.PDF -> InkoraDocumentType.Pdf
        DocumentType.WHITEBOARD -> InkoraDocumentType.Whiteboard
        DocumentType.TEXT_DOCUMENT -> InkoraDocumentType.TextDocument
        DocumentType.QUICK_NOTE -> InkoraDocumentType.QuickNote
    },
    lastEdited = modifiedAtEpochMs.toRelativeEditLabel(),
    pages = pageCount.takeIf { it > 0 },
    isFavorite = isFavorite,
    accent = when (type) {
        DocumentType.NOTEBOOK -> Color(0xFF3E7CB1)
        DocumentType.PDF -> Color(0xFFAA6B42)
        DocumentType.WHITEBOARD -> Color(0xFF866DAA)
        DocumentType.TEXT_DOCUMENT -> Color(0xFFC47B6B)
        DocumentType.QUICK_NOTE -> Color(0xFFC98936)
    }
)

private fun Long.toRelativeEditLabel(): String = "Edited recently"

fun NotebookDraft.toDomainPaperSpec(): PaperSpec = PaperSpec(
    size = when (paperSize) {
        com.inkora.ui.PaperSize.A4 -> PaperSize.A4
        com.inkora.ui.PaperSize.A5 -> PaperSize.A5
        com.inkora.ui.PaperSize.Letter -> PaperSize.LETTER
        com.inkora.ui.PaperSize.Legal -> PaperSize.LEGAL
        com.inkora.ui.PaperSize.Square -> PaperSize.SQUARE
        com.inkora.ui.PaperSize.Presentation -> PaperSize.PRESENTATION
        com.inkora.ui.PaperSize.Custom -> PaperSize.CUSTOM
    },
    orientation = when (orientation) {
        PageOrientation.Portrait -> Orientation.PORTRAIT
        PageOrientation.Landscape -> Orientation.LANDSCAPE
    },
    color = when (paperColor) {
        com.inkora.ui.PaperColor.White -> PaperColor.WHITE
        com.inkora.ui.PaperColor.WarmWhite -> PaperColor.WARM_WHITE
        com.inkora.ui.PaperColor.Cream -> PaperColor.CREAM
        com.inkora.ui.PaperColor.LightGray -> PaperColor.LIGHT_GRAY
        com.inkora.ui.PaperColor.Dark -> PaperColor.DARK
    }
)

fun NotebookDraft.toDomainTemplate(): PaperTemplate = when (template) {
    PageTemplate.Blank -> PaperTemplate.BLANK
    PageTemplate.Ruled -> PaperTemplate.RULED
    PageTemplate.CollegeRuled -> PaperTemplate.COLLEGE_RULED
    PageTemplate.WideRuled -> PaperTemplate.WIDE_RULED
    PageTemplate.Grid -> PaperTemplate.GRID
    PageTemplate.SmallGrid -> PaperTemplate.SMALL_GRID
    PageTemplate.Dotted -> PaperTemplate.DOTTED
    PageTemplate.Cornell -> PaperTemplate.CORNELL_NOTES
    PageTemplate.Checklist -> PaperTemplate.CHECKLIST
    PageTemplate.Music -> PaperTemplate.MUSIC_SHEET
    PageTemplate.Planner -> PaperTemplate.PLANNER
    PageTemplate.Lecture -> PaperTemplate.LECTURE_NOTES
    PageTemplate.ConceptMap -> PaperTemplate.CONCEPT_MAP
    PageTemplate.MindMap -> PaperTemplate.MIND_MAP
    PageTemplate.StudyPlanner -> PaperTemplate.STUDY_PLANNER
    PageTemplate.WeeklyPlanner -> PaperTemplate.WEEKLY_PLANNER
    PageTemplate.DailyPlanner -> PaperTemplate.DAILY_PLANNER
    PageTemplate.AssignmentTracker -> PaperTemplate.ASSIGNMENT_TRACKER
    PageTemplate.Flashcard -> PaperTemplate.FLASHCARD_SHEET
    PageTemplate.LabNotes -> PaperTemplate.LAB_NOTES
    PageTemplate.MedicationNotes -> PaperTemplate.MEDICATION_NOTES
    PageTemplate.ExamReview -> PaperTemplate.EXAM_REVIEW
}

fun NotebookDraft.toDomainCover(): NotebookCover = when (coverIndex) {
    1 -> NotebookCover.BLUE
    2 -> NotebookCover.GREEN
    3 -> NotebookCover.PURPLE
    4 -> NotebookCover.RED
    5 -> NotebookCover.DARK
    else -> NotebookCover.DEFAULT
}

