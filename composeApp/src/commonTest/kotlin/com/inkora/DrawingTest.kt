package com.inkora

import com.inkora.drawing.AddStrokeEdit
import com.inkora.drawing.CanvasPoint
import com.inkora.drawing.CanvasSize
import com.inkora.drawing.PageCoordinateTransform
import com.inkora.drawing.PageRotation
import com.inkora.drawing.Stroke
import com.inkora.drawing.StrokeCollectionState
import com.inkora.drawing.StrokePoint
import com.inkora.drawing.UndoRedoManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DrawingTest {
    @Test
    fun coordinateTransformRoundTripsAcrossRotationAndZoom() {
        val transform = PageCoordinateTransform(
            pageSize = CanvasSize(1000f, 1400f),
            viewportSize = CanvasSize(800f, 600f),
            zoom = 1.7f,
            translation = CanvasPoint(13f, -8f),
            rotation = PageRotation.DEGREES_90,
        )
        val page = CanvasPoint(125f, 900f)
        val result = transform.viewportToPage(transform.pageToViewport(page))
        assertEquals(page.x, result.x, absoluteTolerance = 0.01f)
        assertEquals(page.y, result.y, absoluteTolerance = 0.01f)
    }

    @Test
    fun undoRedoRestoresStroke() {
        val stroke = Stroke("s1", listOf(StrokePoint(1f, 2f)))
        val manager = UndoRedoManager(StrokeCollectionState())
        manager.execute(AddStrokeEdit(stroke))
        assertEquals(listOf(stroke), manager.state.strokes)
        assertNotNull(manager.undo())
        assertEquals(emptyList(), manager.state.strokes)
        assertNotNull(manager.redo())
        assertEquals(listOf(stroke), manager.state.strokes)
    }
}
