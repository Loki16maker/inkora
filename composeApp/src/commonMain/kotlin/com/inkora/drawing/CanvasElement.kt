package com.inkora.drawing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** All coordinates are in unzoomed page points, independent of viewport size and density. */
@Serializable
data class CanvasPoint(val x: Float, val y: Float, val pressure: Float = 1f)

/** Light-weight one-pass smoothing that preserves endpoints and pressure changes. */
fun List<CanvasPoint>.smoothStroke(strength: Float = .28f): List<CanvasPoint> {
    if (size < 3) return this
    val amount = strength.coerceIn(0f, .75f)
    return mapIndexed { index, point ->
        if (index == 0 || index == lastIndex) point
        else {
            val previous = this[index - 1]
            val next = this[index + 1]
            val averageX = (previous.x + point.x * 2f + next.x) / 4f
            val averageY = (previous.y + point.y * 2f + next.y) / 4f
            point.copy(
                x = point.x + (averageX - point.x) * amount,
                y = point.y + (averageY - point.y) * amount,
            )
        }
    }
}

@Serializable
enum class CanvasInkTool { BALL, FOUNTAIN, PENCIL, HIGHLIGHTER }

@Serializable
enum class CanvasShapeKind { LINE, RECTANGLE, ELLIPSE, ARROW }

/** Recognizes deliberately drawn geometric gestures while keeping ordinary
 * handwriting as ink. The caller can opt in with the editor's Auto-shape
 * toggle; no network or handwriting service is involved. */
fun List<CanvasPoint>.recognizeShape(): CanvasShapeKind? {
    if (size < 6) return null
    val first = first()
    val last = last()
    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    val width = maxX - minX
    val height = maxY - minY
    if (width < 12f && height < 12f) return null
    val pathLength = zipWithNext().sumOf { (a, b) -> kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }.toFloat()
    val maxDeviation = maxOf { pointSegmentDistance(it, first, last) }
    val closed = kotlin.math.hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble()) <= max(width, height) * .22f
    if (!closed && pathLength > 0 && maxDeviation <= max(width, height) * .08f) return CanvasShapeKind.LINE
    if (!closed) return null
    val perimeter = 2f * (width + height)
    val rectangularity = if (perimeter > 0f) pathLength / perimeter else 0f
    if (rectangularity in .72f..1.35f) return CanvasShapeKind.RECTANGLE
    val centerX = (minX + maxX) / 2f
    val centerY = (minY + maxY) / 2f
    val radius = ((width + height) / 4f).coerceAtLeast(1f)
    val radialError = map { point ->
        val distance = kotlin.math.hypot((point.x - centerX).toDouble(), (point.y - centerY).toDouble()).toFloat()
        kotlin.math.abs(distance - radius) / radius
    }.average()
    return if (radialError < .28) CanvasShapeKind.ELLIPSE else null
}

/** Persisted drawing objects keep their own style and their order in the containing list. */
@Serializable
sealed interface CanvasElement {
    val id: String

    @Serializable
    @SerialName("ink")
    data class Ink(
        override val id: String,
        val points: List<CanvasPoint>,
        val colorArgb: Long,
        val width: Float,
        val opacity: Float = 1f,
        val tool: CanvasInkTool = CanvasInkTool.BALL,
    ) : CanvasElement

    @Serializable
    @SerialName("shape")
    data class Shape(
        override val id: String,
        val kind: CanvasShapeKind,
        val start: CanvasPoint,
        val end: CanvasPoint,
        val colorArgb: Long,
        val width: Float,
        val opacity: Float = 1f,
    ) : CanvasElement

    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String,
        val x: Float,
        val y: Float,
        val text: String,
        val colorArgb: Long,
        val fontSize: Float,
        val sticky: Boolean = false,
    ) : CanvasElement

    @Serializable
    @SerialName("image")
    data class Image(
        override val id: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val path: String,
    ) : CanvasElement
}

/** Lightweight page-space rectangle shared by selection, movement and erasing. */
data class CanvasBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: CanvasPoint get() = CanvasPoint((left + right) / 2f, (top + bottom) / 2f)

    fun contains(point: CanvasPoint, padding: Float = 0f): Boolean =
        point.x >= left - padding && point.x <= right + padding &&
            point.y >= top - padding && point.y <= bottom + padding

    fun intersects(other: CanvasBounds): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

    fun expanded(amount: Float): CanvasBounds =
        CanvasBounds(left - amount, top - amount, right + amount, bottom + amount)
}

/** Bounds include visible ink width; text uses a conservative wrapping-independent estimate. */
fun CanvasElement.bounds(): CanvasBounds = when (this) {
    is CanvasElement.Ink -> {
        if (points.isEmpty()) CanvasBounds(0f, 0f, 0f, 0f)
        else {
            // A pressure sample below 1 still renders at the tool's base width; only
            // high-pressure samples need a larger fit-all/selection envelope.
            val maxPressure = points.maxOf { it.pressure.coerceIn(.15f, 2f) }.coerceAtLeast(1f)
            CanvasBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
                .expanded(width * maxPressure / 2f)
        }
    }
    is CanvasElement.Shape -> CanvasBounds(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
        .expanded(if (kind == CanvasShapeKind.ARROW) max(width / 2f, width * 4f) else width / 2f)
    is CanvasElement.Text -> {
        val lines = text.lines()
        val padding = if (sticky) 12f else 0f
        CanvasBounds(x, y, x + max(if (sticky) 140f else 12f, (lines.maxOfOrNull { it.length } ?: 0) * fontSize * .62f + padding * 2),
            y + max(if (sticky) 80f else fontSize * 1.3f, lines.size * fontSize * 1.3f + padding * 2))
    }
    is CanvasElement.Image -> CanvasBounds(x, y, x + width, y + height)
}

/** Move an object without changing its identity, pressure samples, style or layer order. */
fun CanvasElement.translated(dx: Float, dy: Float): CanvasElement = when (this) {
    is CanvasElement.Ink -> copy(points = points.map { it.copy(x = it.x + dx, y = it.y + dy) })
    is CanvasElement.Shape -> copy(start = start.copy(x = start.x + dx, y = start.y + dy), end = end.copy(x = end.x + dx, y = end.y + dy))
    is CanvasElement.Text -> copy(x = x + dx, y = y + dy)
    is CanvasElement.Image -> copy(x = x + dx, y = y + dy)
}

/** Scale selected contents around a common origin, including text and image dimensions. */
fun CanvasElement.scaled(factor: Float, origin: CanvasPoint): CanvasElement {
    require(factor > 0f && factor.isFinite())
    fun CanvasPoint.scale() = copy(x = origin.x + (x - origin.x) * factor, y = origin.y + (y - origin.y) * factor)
    return when (this) {
        is CanvasElement.Ink -> copy(points = points.map { it.scale() }, width = width * factor)
        is CanvasElement.Shape -> copy(start = start.scale(), end = end.scale(), width = width * factor)
        is CanvasElement.Text -> CanvasPoint(x, y).scale().let { copy(x = it.x, y = it.y, fontSize = fontSize * factor) }
        is CanvasElement.Image -> CanvasPoint(x, y).scale().let { copy(x = it.x, y = it.y, width = width * factor, height = height * factor) }
    }
}

/** Whole-object eraser hit test against actual stroke segments, rather than their bounding box. */
fun CanvasElement.hitTest(point: CanvasPoint, radius: Float = 0f): Boolean {
    if (!bounds().contains(point, radius)) return false
    return when (this) {
        is CanvasElement.Ink -> points.firstOrNull()?.let { first ->
            val tolerance = radius + width / 2f
            if (points.size == 1) pointSegmentDistance(point, first, first) <= tolerance
            else points.zipWithNext().any { (a, b) -> pointSegmentDistance(point, a, b) <= tolerance }
        } ?: false
        is CanvasElement.Shape -> {
            val tolerance = radius + width / 2f
            val rect = CanvasBounds(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
            when (kind) {
                CanvasShapeKind.LINE, CanvasShapeKind.ARROW -> pointSegmentDistance(point, start, end) <= tolerance
                CanvasShapeKind.RECTANGLE ->
                    min(min(abs(point.x - rect.left), abs(point.x - rect.right)), min(abs(point.y - rect.top), abs(point.y - rect.bottom))) <= tolerance
                CanvasShapeKind.ELLIPSE -> {
                    val rx = rect.width / 2f
                    val ry = rect.height / 2f
                    if (rx < .01f || ry < .01f) pointSegmentDistance(point, start, end) <= tolerance
                    else {
                        val nx = (point.x - rect.center.x) / rx
                        val ny = (point.y - rect.center.y) / ry
                        abs(sqrt(nx * nx + ny * ny) - 1f) * min(rx, ry) <= tolerance
                    }
                }
            }
        }
        is CanvasElement.Text, is CanvasElement.Image -> true
    }
}

/** Distance to a finite segment also handles a single-point stroke without division by zero. */
fun pointSegmentDistance(point: CanvasPoint, start: CanvasPoint, end: CanvasPoint): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared <= .000001f) 0f else (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val ex = point.x - (start.x + t * dx)
    val ey = point.y - (start.y + t * dy)
    return sqrt(ex * ex + ey * ey)
}

/** Polygon lasso includes objects whose sampled points or center lie inside the polygon. */
fun CanvasElement.insideLasso(polygon: List<CanvasPoint>): Boolean {
    if (polygon.size < 3) return false
    fun contains(point: CanvasPoint): Boolean {
        var inside = false
        var previous = polygon.last()
        for (vertex in polygon) {
            if ((vertex.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - vertex.x) * (point.y - vertex.y) / (previous.y - vertex.y) + vertex.x
            ) inside = !inside
            previous = vertex
        }
        return inside
    }
    return contains(bounds().center) || when (this) {
        is CanvasElement.Ink -> points.any { contains(it) }
        is CanvasElement.Shape -> contains(start) || contains(end)
        else -> false
    }
}

/** One history entry is a completed gesture, including a whole eraser sweep or selection move. */
class CanvasHistory(initial: List<CanvasElement>, private val limit: Int = 100) {
    private val undoStack = ArrayDeque<List<CanvasElement>>()
    private val redoStack = ArrayDeque<List<CanvasElement>>()
    var current: List<CanvasElement> = initial.toList()
        private set
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(next: List<CanvasElement>): Boolean {
        if (next == current) return false
        undoStack.addLast(current)
        while (undoStack.size > limit.coerceAtLeast(1)) undoStack.removeFirst()
        redoStack.clear()
        current = next.toList()
        return true
    }

    fun undo(): List<CanvasElement> {
        if (undoStack.isNotEmpty()) { redoStack.addLast(current); current = undoStack.removeLast() }
        return current
    }

    fun redo(): List<CanvasElement> {
        if (redoStack.isNotEmpty()) { undoStack.addLast(current); current = redoStack.removeLast() }
        return current
    }

    /** External reload starts fresh history instead of undoing into a different document revision. */
    fun reset(value: List<CanvasElement>) { current = value.toList(); undoStack.clear(); redoStack.clear() }
}
