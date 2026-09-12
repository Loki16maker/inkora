package com.inkora.study

import com.inkora.drawing.CanvasElement
import com.inkora.domain.model.DocumentContent
import com.inkora.platform.PlatformFile
import com.inkora.app.InkoraRuntime

/** Collects study material from every document type supported by the editor. */
suspend fun InkoraRuntime.studyText(document: DocumentContent): String = when (document) {
    is DocumentContent.Pdf -> extractPdfStudyText(document)
    is DocumentContent.Notebook -> document.pages.joinToString("\n\n") { page ->
        buildString {
            page.title?.let { appendLine(it) }
            page.elements.forEach { element ->
                if (element is CanvasElement.Text) appendLine(element.text)
            }
        }
    }
    is DocumentContent.Whiteboard -> buildString {
        document.elements.forEach { if (it is CanvasElement.Text) appendLine(it.text) }
        document.objects.forEach { objectItem ->
            if (objectItem is com.inkora.domain.model.WhiteboardObject.TextObject) appendLine(objectItem.text)
        }
    }
    is DocumentContent.TextDocument -> document.markdown
    is DocumentContent.QuickNote -> document.text
}.trim()

/** PDF extraction needs a short lived reader because the visible PDF pane has its own handle. */
suspend fun InkoraRuntime.extractPdfStudyText(document: DocumentContent.Pdf): String {
    val handle = pdf.openDocument(PlatformFile(document.managedFilePath, document.summary.sourceFileName ?: "document.pdf", "application/pdf"))
    return try { pdf.extractText(handle) } finally { pdf.closeDocument(handle) }
}

fun DocumentContent.localStudyText(): String = when (this) {
    is DocumentContent.Pdf -> ""
    is DocumentContent.Notebook -> pages.joinToString("\n\n") { page ->
        buildString {
            page.title?.let { appendLine(it) }
            page.elements.forEach { if (it is CanvasElement.Text) appendLine(it.text) }
        }
    }
    is DocumentContent.Whiteboard -> buildString {
        elements.forEach { if (it is CanvasElement.Text) appendLine(it.text) }
        objects.forEach { if (it is com.inkora.domain.model.WhiteboardObject.TextObject) appendLine(it.text) }
    }
    is DocumentContent.TextDocument -> markdown
    is DocumentContent.QuickNote -> text
}.trim()
