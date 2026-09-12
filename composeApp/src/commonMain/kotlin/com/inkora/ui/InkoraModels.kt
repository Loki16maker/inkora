package com.inkora.ui

import androidx.compose.ui.graphics.Color

enum class LibraryDestination(val label: String, val glyph: String) {
    Documents("Documents", "▦"),
    Search("Search", "⌕"),
    Favorites("Favorites", "♡"),
    Shared("Shared", "⇄"),
    Templates("Templates", "▤"),
    Trash("Trash", "⌫"),
    Settings("Settings", "⚙")
}

enum class InkoraDocumentType(val label: String, val glyph: String) {
    Notebook("Notebook", "▤"),
    Pdf("PDF", "▧"),
    Whiteboard("Whiteboard", "⌁"),
    TextDocument("Text document", "T"),
    QuickNote("Quick note", "✎"),
    Folder("Folder", "□")
}

data class InkoraDocument(
    val id: String,
    val title: String,
    val type: InkoraDocumentType,
    val lastEdited: String,
    val pages: Int? = null,
    val isFavorite: Boolean = false,
    val folder: String? = null,
    val accent: Color = InkoraColors.primaryBlue
)

enum class CreationKind(val label: String, val glyph: String, val description: String) {
    Notebook("Notebook", "▤", "A paged notebook for handwriting and study"),
    TextDocument("Text document", "T", "A clean, pageless rich text document"),
    Whiteboard("Whiteboard", "⌁", "An infinite canvas for ideas and diagrams"),
    QuickNote("Quick note", "✎", "Start writing immediately with a dated note"),
    Image("Image", "▧", "Place an image on a new page"),
    Folder("Folder", "□", "Organize documents into a folder"),
    Import("Import", "↓", "Bring in a PDF or supported file")
}

enum class PaperSize(val label: String) { A4("A4"), A5("A5"), Letter("Letter"), Legal("Legal"), Square("Square"), Presentation("Presentation"), Custom("Custom") }
enum class PaperColor(val label: String, val color: Color) {
    White("White", Color.White),
    WarmWhite("Warm white", Color(0xFFFFFDF8)),
    Cream("Cream", Color(0xFFFFF3D6)),
    LightGray("Light gray", Color(0xFFF0F2F2)),
    Dark("Dark paper", Color(0xFF202124))
}
enum class PageOrientation(val label: String) { Portrait("Portrait"), Landscape("Landscape") }
enum class PageTemplate(val label: String, val glyph: String) {
    Blank("Blank", "□"), Ruled("Ruled", "≡"), CollegeRuled("College ruled", "≡"), WideRuled("Wide ruled", "≡"),
    Grid("Grid", "▦"), SmallGrid("Small grid", "▦"), Dotted("Dotted", "⁙"), Cornell("Cornell notes", "▤"),
    Checklist("Checklist", "☑"), Music("Music sheet", "♫"), Planner("Planner", "▤"), Lecture("Lecture notes", "✎"),
    ConceptMap("Concept map", "◎"), MindMap("Mind map", "✣"), StudyPlanner("Study planner", "▤"), WeeklyPlanner("Weekly planner", "▤"),
    DailyPlanner("Daily planner", "▤"), AssignmentTracker("Assignment tracker", "☑"), Flashcard("Flashcard sheet", "▤"),
    LabNotes("Lab notes", "⌁"), MedicationNotes("Medication notes", "+"), ExamReview("Exam review", "✓")
}

enum class EditorTool(val label: String, val glyph: String) {
    Hand("Hand", "☝"), BallPen("Pen", "✒"), Pencil("Pencil", "✎"), Highlighter("Highlighter", "▰"),
    Tape("Tape", "▤"), Eraser("Eraser", "⌫"), Lasso("Lasso", "◯"), Shape("Shape", "△"),
    Text("Text", "T"), Image("Image", "▧"), StickyNote("Sticky note", "□"), Laser("Laser", "•")
}

data class NotebookDraft(
    val title: String = "Untitled notebook",
    val template: PageTemplate = PageTemplate.Blank,
    val paperSize: PaperSize = PaperSize.A4,
    val paperColor: PaperColor = PaperColor.WarmWhite,
    val orientation: PageOrientation = PageOrientation.Portrait,
    val coverIndex: Int = 0
)

fun demoInkoraDocuments(): List<InkoraDocument> = listOf(
    InkoraDocument("anatomy", "Anatomy lecture notes", InkoraDocumentType.Notebook, "Edited 12 min ago", 42, true, "University", Color(0xFF3E7CB1)),
    InkoraDocument("design", "Design systems handbook", InkoraDocumentType.Pdf, "Edited yesterday", 128, false, "Reference", Color(0xFFAA6B42)),
    InkoraDocument("chemistry", "Chemistry review", InkoraDocumentType.Notebook, "Edited 2 days ago", 18, false, "University", Color(0xFF6C8F6B)),
    InkoraDocument("ideas", "Project ideas", InkoraDocumentType.Whiteboard, "Edited 4 days ago", null, true, null, Color(0xFF866DAA)),
    InkoraDocument("reading", "Reading list", InkoraDocumentType.TextDocument, "Edited last week", null, false, null, Color(0xFFC47B6B))
)
