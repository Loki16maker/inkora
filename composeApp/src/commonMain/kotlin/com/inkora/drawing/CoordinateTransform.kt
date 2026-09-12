package com.inkora.drawing

import com.inkora.domain.model.Orientation
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

@Serializable
public data class CanvasSize(val width: Float, val height: Float)

@Serializable
public enum class PageRotation(public val degrees: Int) {
    DEGREES_0(0),
    DEGREES_90(90),
    DEGREES_180(180),
    DEGREES_270(270),
}

/**
 * Converts logical page coordinates to viewport coordinates around the page
 * center. Keeping strokes in logical coordinates means zoom, rotation, and
 * paper template changes never move persisted handwriting.
 */
public data class PageCoordinateTransform(
    public val pageSize: CanvasSize,
    public val viewportSize: CanvasSize,
    public val zoom: Float = 1f,
    public val translation: CanvasPoint = CanvasPoint(0f, 0f),
    public val rotation: PageRotation = PageRotation.DEGREES_0,
) {
    init {
        require(pageSize.width > 0f && pageSize.height > 0f) { "Page dimensions must be positive" }
        require(viewportSize.width >= 0f && viewportSize.height >= 0f) { "Viewport dimensions cannot be negative" }
        require(zoom > 0f) { "Zoom must be positive" }
    }

    public fun pageToViewport(point: CanvasPoint): CanvasPoint {
        val centered = CanvasPoint(point.x - pageSize.width / 2f, point.y - pageSize.height / 2f)
        val rotated = rotate(centered, rotation)
        return CanvasPoint(
            viewportSize.width / 2f + translation.x + rotated.x * zoom,
            viewportSize.height / 2f + translation.y + rotated.y * zoom,
        )
    }

    public fun viewportToPage(point: CanvasPoint): CanvasPoint {
        val centered = CanvasPoint(
            (point.x - viewportSize.width / 2f - translation.x) / zoom,
            (point.y - viewportSize.height / 2f - translation.y) / zoom,
        )
        val unrotated = rotate(centered, inverse(rotation))
        return CanvasPoint(unrotated.x + pageSize.width / 2f, unrotated.y + pageSize.height / 2f)
    }

    public fun withZoomAt(factor: Float, focus: CanvasPoint): PageCoordinateTransform {
        val newZoom = (zoom * factor).coerceIn(0.05f, 32f)
        val pageAtFocus = viewportToPage(focus)
        val base = copy(zoom = newZoom)
        val projected = base.pageToViewport(pageAtFocus)
        return base.copy(translation = CanvasPoint(
            base.translation.x + focus.x - projected.x,
            base.translation.y + focus.y - projected.y,
        ))
    }

    public fun rotatedPageSize(): CanvasSize = when (rotation) {
        PageRotation.DEGREES_0, PageRotation.DEGREES_180 -> pageSize
        PageRotation.DEGREES_90, PageRotation.DEGREES_270 -> CanvasSize(pageSize.height, pageSize.width)
    }

    private fun rotate(point: CanvasPoint, pageRotation: PageRotation): CanvasPoint {
        val radians = pageRotation.degrees * (kotlin.math.PI / 180.0)
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return CanvasPoint(point.x * c - point.y * s, point.x * s + point.y * c)
    }

    private fun inverse(value: PageRotation): PageRotation = when (value) {
        PageRotation.DEGREES_0 -> PageRotation.DEGREES_0
        PageRotation.DEGREES_90 -> PageRotation.DEGREES_270
        PageRotation.DEGREES_180 -> PageRotation.DEGREES_180
        PageRotation.DEGREES_270 -> PageRotation.DEGREES_90
    }
}

public fun CanvasSize.forOrientation(orientation: Orientation): CanvasSize = when (orientation) {
    Orientation.PORTRAIT -> if (height >= width) this else CanvasSize(height, width)
    Orientation.LANDSCAPE -> if (width >= height) this else CanvasSize(height, width)
}
