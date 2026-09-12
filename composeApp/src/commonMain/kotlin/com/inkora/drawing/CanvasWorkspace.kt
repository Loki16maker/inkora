package com.inkora.drawing

import kotlin.math.min

/** The page is an anchor in an unbounded workspace, not a limit on stored coordinates. */
fun workspaceBounds(pageWidth: Float, pageHeight: Float, elements: List<CanvasElement>, padding: Float = 64f): CanvasBounds {
    val page = CanvasBounds(0f, 0f, pageWidth.coerceAtLeast(1f), pageHeight.coerceAtLeast(1f))
    val bounds = elements.map { it.bounds() }
    return CanvasBounds(
        minOf(page.left, bounds.minOfOrNull { it.left } ?: page.left),
        minOf(page.top, bounds.minOfOrNull { it.top } ?: page.top),
        maxOf(page.right, bounds.maxOfOrNull { it.right } ?: page.right),
        maxOf(page.bottom, bounds.maxOfOrNull { it.bottom } ?: page.bottom),
    ).expanded(padding.coerceAtLeast(0f))
}

data class CanvasViewTransform(val scale: Float, val x: Float, val y: Float) {
    fun toPage(point: CanvasPoint) = point.copy(x = (point.x - x) / scale, y = (point.y - y) / scale)
    fun toScreen(point: CanvasPoint) = point.copy(x = point.x * scale + x, y = point.y * scale + y)
}

fun fitWorkspace(bounds: CanvasBounds, viewportWidth: Float, viewportHeight: Float): CanvasViewTransform {
    val width = viewportWidth.coerceAtLeast(1f)
    val height = viewportHeight.coerceAtLeast(1f)
    val scale = min(width / bounds.width.coerceAtLeast(1f), height / bounds.height.coerceAtLeast(1f))
    return CanvasViewTransform(scale, width / 2f - bounds.center.x * scale, height / 2f - bounds.center.y * scale)
}
