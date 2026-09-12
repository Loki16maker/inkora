package com.inkora

import com.inkora.drawing.CanvasElement
import com.inkora.drawing.CanvasPoint
import com.inkora.drawing.fitWorkspace
import com.inkora.drawing.workspaceBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasWorkspaceTest {
    @Test
    fun marginInkExpandsWorkspaceWithoutChangingStoredCoordinates() {
        val note = CanvasElement.Ink(
            id = "margin",
            points = listOf(CanvasPoint(-180f, -90f), CanvasPoint(1400f, 1000f)),
            colorArgb = 0xFF202124L,
            width = 8f,
        )
        val bounds = workspaceBounds(800f, 600f, listOf(note), padding = 32f)
        assertEquals(-216f, bounds.left)
        assertEquals(-126f, bounds.top)
        assertEquals(1436f, bounds.right)
        assertEquals(1036f, bounds.bottom)
        assertTrue(bounds.contains(CanvasPoint(-180f, -90f)))
    }

    @Test
    fun fitTransformCentersFullWorkspaceAndRoundTripsCoordinates() {
        val bounds = workspaceBounds(800f, 600f, emptyList(), padding = 100f)
        val transform = fitWorkspace(bounds, viewportWidth = 1000f, viewportHeight = 700f)
        val point = CanvasPoint(-100f, 75f, pressure = .4f)
        val roundTrip = transform.toPage(transform.toScreen(point))
        assertEquals(point.x, roundTrip.x, absoluteTolerance = .001f)
        assertEquals(point.y, roundTrip.y, absoluteTolerance = .001f)
        assertEquals(point.pressure, roundTrip.pressure)
        assertTrue(transform.scale > 0f)
    }
}
