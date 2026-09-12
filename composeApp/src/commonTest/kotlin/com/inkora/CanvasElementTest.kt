package com.inkora

import com.inkora.drawing.CanvasElement
import com.inkora.drawing.CanvasHistory
import com.inkora.drawing.CanvasInkTool
import com.inkora.drawing.CanvasPoint
import com.inkora.drawing.CanvasShapeKind
import com.inkora.drawing.bounds
import com.inkora.drawing.hitTest
import com.inkora.drawing.insideLasso
import com.inkora.drawing.scaled
import com.inkora.drawing.translated
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasElementTest {
    private fun ink(id: String = "ink") = CanvasElement.Ink(id,
        listOf(CanvasPoint(0f, 0f, .2f), CanvasPoint(100f, 100f, .9f)),
        0xFF1464A5L, 4f, .8f, CanvasInkTool.FOUNTAIN)

    @Test
    fun persistedObjectsRoundTripStylesPressureAndLayerOrder() {
        val original: List<CanvasElement> = listOf(
            ink(),
            CanvasElement.Shape("shape", CanvasShapeKind.ARROW, CanvasPoint(4f, 8f), CanvasPoint(40f, 80f), 0xFFC4473AL, 2f, .6f),
            CanvasElement.Text("text", 30f, 45f, "Study\nα + β", 0xFF202124L, 16f, true),
            CanvasElement.Image("image", 10f, 20f, 100f, 80f, "managed/image.png"),
        )
        val encoded = Json.encodeToString(original)
        val restored = Json.decodeFromString<List<CanvasElement>>(encoded)
        assertEquals(original, restored)
    }

    @Test
    fun eraserHitsSegmentsButNotEmptyPartsOfTheirBounds() {
        val element = ink()
        assertTrue(element.hitTest(CanvasPoint(50f, 52f), 2f))
        assertFalse(element.hitTest(CanvasPoint(10f, 90f), 5f))
        val dot = element.copy(points = listOf(CanvasPoint(12f, 14f)))
        assertTrue(dot.hitTest(CanvasPoint(13f, 14f)))
        assertFalse(dot.hitTest(CanvasPoint(25f, 14f)))
    }

    @Test
    fun movementPreservesPressureStyleIdentityAndDoesNotMutateOriginal() {
        val original = ink()
        val moved = original.translated(20f, -10f) as CanvasElement.Ink
        assertEquals(original.id, moved.id)
        assertEquals(original.colorArgb, moved.colorArgb)
        assertEquals(original.width, moved.width)
        assertEquals(original.tool, moved.tool)
        assertEquals(CanvasPoint(20f, -10f, .2f), moved.points.first())
        assertEquals(CanvasPoint(0f, 0f, .2f), original.points.first())
    }

    @Test
    fun polygonLassoDoesNotSelectObjectsOnlyInsideThePolygonBoundingBox() {
        val polygon = listOf(CanvasPoint(0f, 0f), CanvasPoint(100f, 0f), CanvasPoint(0f, 100f))
        val selected = CanvasElement.Ink("selected", listOf(CanvasPoint(10f, 10f)), 0xFF202124L, 2f)
        val outside = selected.copy(id = "outside", points = listOf(CanvasPoint(90f, 90f)))
        assertTrue(selected.insideLasso(polygon))
        assertFalse(outside.insideLasso(polygon))
    }

    @Test
    fun resizeKeepsGroupOriginAndImageAspectRatio() {
        val image = CanvasElement.Image("image", 30f, 40f, 80f, 60f, "image.png")
        val resized = image.scaled(2f, CanvasPoint(10f, 20f)) as CanvasElement.Image
        assertEquals(50f, resized.x)
        assertEquals(60f, resized.y)
        assertEquals(160f, resized.width)
        assertEquals(120f, resized.height)
        assertEquals(image.width / image.height, resized.width / resized.height)
    }

    @Test
    fun eraserUndoRestoresOriginalLayerOrderAndRedoIsDiscardedAfterNewEdit() {
        val original = listOf(ink("bottom"), ink("middle"), ink("top"))
        val history = CanvasHistory(original)
        history.commit(original.filterNot { it.id == "middle" })
        assertEquals(original, history.undo())
        assertTrue(history.canRedo)
        assertEquals(listOf("bottom", "top"), history.redo().map { it.id })
        history.undo()
        history.commit(original + ink("new"))
        assertFalse(history.canRedo)
        assertEquals(listOf("bottom", "middle", "top", "new"), history.current.map { it.id })
    }

    @Test
    fun perPageHistoriesAndHistoryLimitAreIndependent() {
        val first = CanvasHistory(emptyList(), limit = 2)
        val second = CanvasHistory(listOf(ink("other")))
        first.commit(listOf(ink("one")))
        first.commit(listOf(ink("two")))
        first.commit(listOf(ink("three")))
        assertEquals("two", first.undo().single().id)
        assertEquals("one", first.undo().single().id)
        assertFalse(first.canUndo)
        assertFalse(second.canUndo)
        assertEquals("other", second.current.single().id)
    }

    @Test
    fun visibleStrokeWidthIsIncludedInSelectionBounds() {
        val bounds = ink().bounds()
        assertEquals(-2f, bounds.left)
        assertEquals(102f, bounds.right)
    }
}
