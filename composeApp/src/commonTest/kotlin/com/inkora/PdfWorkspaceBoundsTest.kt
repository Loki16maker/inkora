package com.inkora.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfWorkspaceBoundsTest {
    @Test
    fun inPageAnnotationsKeepOriginalPageSize() {
        val bounds = pdfWorkspaceBounds(
            PdfPageSize(612f, 792f),
            listOf(PdfInkAnnotation(0, listOf(PdfInkPoint(80f, 90f), PdfInkPoint(120f, 130f)), 0xFF000000.toInt(), 4f)),
        )
        assertEquals(PdfRect(0f, 0f, 612f, 792f), bounds)
    }

    @Test
    fun overflowInkAndArrowReceiveReadableExportMargin() {
        val bounds = pdfWorkspaceBounds(
            PdfPageSize(612f, 792f),
            listOf(
                PdfInkAnnotation(0, listOf(PdfInkPoint(-40f, 50f), PdfInkPoint(-20f, 70f)), 0xFF000000.toInt(), 10f),
                PdfShapeAnnotation(
                    pageIndex = 0,
                    bounds = PdfRect(600f, 740f, 630f, 770f),
                    shape = PdfShapeType.ARROW,
                    colorArgb = 0xFF000000.toInt(),
                    width = 3f,
                ),
            ),
        )
        // 24pt breathing room is added outside each visible stroke/arrow edge.
        assertEquals(-75f, bounds.left)
        assertEquals(666f, bounds.right)
        assertEquals(792f, bounds.bottom)
    }
}
