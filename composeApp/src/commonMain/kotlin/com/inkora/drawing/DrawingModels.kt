package com.inkora.drawing

import com.inkora.domain.model.Rect
import kotlinx.serialization.Serializable

@Serializable
public data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tilt: Float = 0f,
    val timestampNanos: Long = 0L,
)

@Serializable
public data class StrokeStyle(
    val colorArgb: Long = 0xFF202124,
    val width: Float = 2f,
    val opacity: Float = 1f,
    val smoothing: Float = 0.35f,
)

@Serializable
public enum class InkTool { BALL_PEN, FOUNTAIN_PEN, PENCIL, HIGHLIGHTER, ERASER, LASSO, HAND, SHAPE, TEXT, IMAGE }

@Serializable
public enum class EraserMode { WHOLE_STROKE, PARTIAL }

@Serializable
public data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val style: StrokeStyle = StrokeStyle(),
    val tool: InkTool = InkTool.BALL_PEN,
    val bounds: Rect = points.bounds(),
    val createdAtEpochMs: Long = 0L,
)

public fun List<StrokePoint>.bounds(): Rect {
    if (isEmpty()) return Rect.ZERO
    val minX = minOf { it.x }
    val minY = minOf { it.y }
    val maxX = maxOf { it.x }
    val maxY = maxOf { it.y }
    return Rect(minX, minY, maxX, maxY)
}

/** Input collector keeps high-frequency points outside Compose state snapshots. */
public class StrokeInputCollector(
    private val style: StrokeStyle,
    private val tool: InkTool,
    private val idProvider: () -> String,
    private val clockEpochMs: () -> Long,
) {
    private val points = ArrayList<StrokePoint>(64)

    public fun add(point: StrokePoint) {
        points += point
    }

    public fun finish(): Stroke? {
        if (points.isEmpty()) return null
        return Stroke(idProvider(), points.toList(), style, tool, createdAtEpochMs = clockEpochMs())
            .also { points.clear() }
    }

    public fun cancel() {
        points.clear()
    }
}
