package com.inkora.platform

import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Converts Android MotionEvent samples into page-independent Inkora input events. */
class AndroidStylusInputAdapter : StylusInputProvider {
    private val _events = MutableSharedFlow<StylusEvent>(extraBufferCapacity = 512)
    override val events: SharedFlow<StylusEvent> = _events.asSharedFlow()
    override var drawWithFinger: Boolean = false
    private var attached = false
    private var stylusActive = false

    override fun attach() { attached = true }
    override fun detach() { attached = false; stylusActive = false }

    /** Call from a View/Compose bridge's dispatchTouchEvent or onTouchEvent. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!attached) return false
        val action = event.actionMasked
        val actionIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val pointerId = event.getPointerId(actionIndex)
        val tool = event.getToolType(actionIndex).toToolType()
        if (tool == StylusToolType.FINGER && stylusActive && !drawWithFinger) return true
        if (tool == StylusToolType.FINGER && !drawWithFinger && action != MotionEvent.ACTION_CANCEL) return false
        if (tool == StylusToolType.STYLUS || tool == StylusToolType.ERASER) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) stylusActive = true
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) stylusActive = false
        }
        val actionType = action.toStylusAction() ?: return tool == StylusToolType.STYLUS || tool == StylusToolType.ERASER
        val index = actionIndex
        val historical = if (actionType == StylusAction.MOVE) {
            (0 until event.historySize).map { history -> event.toPoint(index, history) }
        } else emptyList()
        val point = event.toPoint(index, null)
        _events.tryEmit(
            StylusEvent(
                action = actionType,
                point = point,
                toolType = tool,
                pointerId = pointerId,
                buttons = event.buttonState,
                historicalPoints = historical,
            ),
        )
        return true
    }

    private fun MotionEvent.toPoint(index: Int, history: Int?): StylusPoint {
        val x = if (history == null) getX(index) else getHistoricalX(index, history)
        val y = if (history == null) getY(index) else getHistoricalY(index, history)
        val pressure = if (history == null) getPressure(index) else getHistoricalPressure(index, history)
        val orientation = if (history == null) getAxisValue(MotionEvent.AXIS_ORIENTATION, index) else getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, index, history)
        val tilt = if (BuildVersion.supportsTilt) {
            if (history == null) getAxisValue(MotionEvent.AXIS_TILT, index) else getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, history)
        } else null
        val timestamp = if (history == null) eventTime else historicalEventTime(history)
        return StylusPoint(x, y, pressure.coerceIn(0f, 1f), tiltX = tilt, orientation = orientation, timestampMillis = timestamp)
    }

    private fun MotionEvent.historicalEventTime(index: Int): Long = getHistoricalEventTime(index)

    private object BuildVersion {
        val supportsTilt: Boolean = android.os.Build.VERSION.SDK_INT >= 21
    }
}

private fun Int.toToolType(): StylusToolType = when (this) {
    MotionEvent.TOOL_TYPE_STYLUS -> StylusToolType.STYLUS
    MotionEvent.TOOL_TYPE_ERASER -> StylusToolType.ERASER
    MotionEvent.TOOL_TYPE_FINGER -> StylusToolType.FINGER
    MotionEvent.TOOL_TYPE_MOUSE -> StylusToolType.MOUSE
    else -> StylusToolType.UNKNOWN
}

private fun Int.toStylusAction(): StylusAction? = when (this) {
    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> StylusAction.DOWN
    MotionEvent.ACTION_MOVE -> StylusAction.MOVE
    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> StylusAction.UP
    MotionEvent.ACTION_CANCEL -> StylusAction.CANCEL
    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_EXIT -> StylusAction.HOVER
    else -> null
}

