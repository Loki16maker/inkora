@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.inkora.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.isCtrlPressed as isKeyCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed as isKeyMetaPressed
import androidx.compose.ui.input.key.isShiftPressed as isKeyShiftPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.inkora.drawing.workspaceBounds
import com.inkora.drawing.fitWorkspace
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inkora.domain.model.PaperTemplate
import com.inkora.drawing.CanvasBounds
import com.inkora.drawing.CanvasElement
import com.inkora.drawing.CanvasHistory
import com.inkora.drawing.CanvasInkTool
import com.inkora.drawing.CanvasPoint
import com.inkora.drawing.CanvasShapeKind
import com.inkora.drawing.bounds
import com.inkora.drawing.hitTest
import com.inkora.drawing.insideLasso
import com.inkora.drawing.scaled
import com.inkora.drawing.smoothStroke
import com.inkora.drawing.translated
import com.inkora.platform.FilePickerRequest
import com.inkora.platform.PlatformFile
import com.inkora.platform.decodeImage
import com.inkora.platform.filePicker
import com.inkora.platform.platformFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.floor
import kotlin.random.Random

private enum class CanvasEditorTool(val label: String) {
    BALL("Pen"), FOUNTAIN("Fountain"), PENCIL("Pencil"), HIGHLIGHTER("Highlight"),
    ERASER("Erase"), LASSO("Select"), HAND("Pan"), SHAPE("Shape"), TEXT("Text"), STICKY("Sticky"), IMAGE("Image")
}

private fun CanvasEditorTool.symbol() = when(this) {
    CanvasEditorTool.BALL, CanvasEditorTool.FOUNTAIN, CanvasEditorTool.PENCIL -> InkoraSymbol.PEN
    CanvasEditorTool.HIGHLIGHTER -> InkoraSymbol.HIGHLIGHT
    CanvasEditorTool.ERASER -> InkoraSymbol.ERASE
    CanvasEditorTool.LASSO -> InkoraSymbol.SELECT
    CanvasEditorTool.HAND -> InkoraSymbol.HAND
    CanvasEditorTool.SHAPE -> InkoraSymbol.SHAPE
    CanvasEditorTool.TEXT -> InkoraSymbol.TEXT
    CanvasEditorTool.STICKY -> InkoraSymbol.NOTE
    CanvasEditorTool.IMAGE -> InkoraSymbol.IMAGE
}

private class CanvasPageSession(initial: List<CanvasElement>) {
    val history = CanvasHistory(initial)
    var elements by mutableStateOf(initial)
    var revision by mutableIntStateOf(0)
    var zoom by mutableStateOf(.78f)
    var pan by mutableStateOf(Offset.Zero)
    var selected by mutableStateOf(emptySet<String>())
    fun commit(value: List<CanvasElement>): Boolean {
        elements = value
        val changed = history.commit(value)
        revision++
        return changed
    }
}

private data class PendingCanvasText(val point: CanvasPoint, val sticky: Boolean, val existing: CanvasElement.Text? = null)

/**
 * Persistent-object drawing surface. Callbacks emit complete page snapshots only after completed
 * edits. Each page retains its own viewport and undo history while this editor remains open.
 */
@Composable
fun DrawingEditor(
    pageKey: String,
    elements: List<CanvasElement>,
    onElementsChange: (List<CanvasElement>) -> Unit,
    pageWidth: Float,
    pageHeight: Float,
    modifier: Modifier = Modifier,
    background: ImageBitmap? = null,
    template: PaperTemplate = PaperTemplate.BLANK,
    paperColor: Color = Color.White,
) {
    val sessions = remember { mutableMapOf<String, CanvasPageSession>() }
    val session = remember(pageKey) { sessions.getOrPut(pageKey) { CanvasPageSession(elements) } }
    val onChange by rememberUpdatedState(onElementsChange)
    val visiblePageKey by rememberUpdatedState(pageKey)
    var tool by remember { mutableStateOf(CanvasEditorTool.BALL) }
    var color by remember { mutableStateOf(0xFF202124L) }
    var width by remember { mutableStateOf(2.5f) }
    var shapeKind by remember { mutableStateOf(CanvasShapeKind.RECTANGLE) }
    var drawFinger by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var showHelp by remember { mutableStateOf(false) }
    var spaceHeld by remember { mutableStateOf(false) }
    val canvasFocus = remember { FocusRequester() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var error by remember { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var pendingText by remember(pageKey) { mutableStateOf<PendingCanvasText?>(null) }
    val activePoints = remember(pageKey) { ArrayList<CanvasPoint>(1024) }
    var activeShape by remember(pageKey) { mutableStateOf<CanvasElement.Shape?>(null) }
    var repaint by remember(pageKey) { mutableIntStateOf(0) }
    val imageCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val scope = rememberCoroutineScope()
    val fileSystem = remember { platformFileSystem() }
    val picker = remember { filePicker() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val safePageWidth = pageWidth.coerceAtLeast(1f)
    val safePageHeight = pageHeight.coerceAtLeast(1f)
    val fitScale = min((viewport.width - 24f).coerceAtLeast(1f) / safePageWidth, (viewport.height - 24f).coerceAtLeast(1f) / safePageHeight)
    fun currentScale() = fitScale * session.zoom
    fun origin(): Offset {
        val scale = currentScale()
        return Offset((viewport.width - safePageWidth * scale) / 2f, (viewport.height - safePageHeight * scale) / 2f) + session.pan
    }
    fun toPage(position: Offset, pressure: Float = 1f): CanvasPoint {
        val point = (position - origin()) / currentScale()
        return CanvasPoint(point.x, point.y, pressure.coerceIn(.05f, 2f))
    }
    fun commit(next: List<CanvasElement>) {
        if (session.commit(next)) onChange(next)
    }
    fun zoomAt(factor: Float, anchor: Offset) {
        val pageAnchor = (anchor - origin()) / currentScale()
        session.zoom = (session.zoom * factor).coerceIn(.005f, 12f)
        val scale = currentScale()
        val base = Offset((viewport.width - safePageWidth * scale) / 2f, (viewport.height - safePageHeight * scale) / 2f)
        session.pan = anchor - base - pageAnchor * scale
    }
    fun fitAll() {
        val fitted = fitWorkspace(workspaceBounds(safePageWidth, safePageHeight, session.elements), viewport.width.toFloat(), viewport.height.toFloat())
        session.zoom = fitted.scale / fitScale
        session.pan = Offset(fitted.x - (viewport.width - safePageWidth * fitted.scale) / 2f,
            fitted.y - (viewport.height - safePageHeight * fitted.scale) / 2f)
    }
    fun undo() { session.elements = session.history.undo(); session.revision++; session.selected = emptySet(); onChange(session.elements) }
    fun redo() { session.elements = session.history.redo(); session.revision++; session.selected = emptySet(); onChange(session.elements) }
    fun deleteSelection() { commit(session.elements.filterNot { it.id in session.selected }); session.selected = emptySet() }

    // Reopening a page reveals its saved margin notes as well as the paper.
    LaunchedEffect(pageKey, viewport) { if (viewport.width > 0 && session.revision == 0) fitAll() }

    LaunchedEffect(pageKey, elements) {
        // A persisted callback echo must not clear the history of the edit that just completed.
        if (elements != session.history.current) {
            session.history.reset(elements)
            session.elements = elements
            session.selected = emptySet()
            session.revision++
        }
    }
    val imagePaths = session.elements.filterIsInstance<CanvasElement.Image>().map { it.path }.distinct()
    LaunchedEffect(imagePaths) {
        imageCache.keys.filter { it !in imagePaths }.forEach { imageCache.remove(it) }
        for (path in imagePaths) {
            if (imageCache[path] != null) continue
            try {
                val bytes = fileSystem.read(PlatformFile(path, path.substringAfterLast('/').substringAfterLast('\\')))
                imageCache[path] = withContext(Dispatchers.Default) { decodeImage(bytes) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "An inserted image could not be loaded: ${failure.message ?: "file unavailable"}"
            }
        }
    }

    fun insertImage() {
        if (importBusy) return
        val targetSession = session
        val targetPageKey = pageKey
        val targetCenter = toPage(Offset(viewport.width / 2f, viewport.height / 2f))
        importBusy = true
        scope.launch {
            try {
                val picked = picker.pickFile(FilePickerRequest(listOf("image/png", "image/jpeg", "image/webp"))) ?: return@launch
                require((picked.sizeBytes ?: 0L) <= 24L * 1024 * 1024) { "Choose an image smaller than 24 MB." }
                val bytes = fileSystem.read(picked)
                require(bytes.size <= 24 * 1024 * 1024) { "Choose an image smaller than 24 MB." }
                val bitmap = withContext(Dispatchers.Default) { decodeImage(bytes) }
                val managed = fileSystem.importToManagedStorage(picked)
                imageCache[managed.path] = bitmap
                val imageWidth = min(safePageWidth * .55f, bitmap.width.toFloat()).coerceAtLeast(1f)
                val imageHeight = imageWidth * bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
                val element = CanvasElement.Image(canvasId(), targetCenter.x - imageWidth / 2f,
                    targetCenter.y - imageHeight / 2f, imageWidth, imageHeight, managed.path)
                // The picker can remain open while the caller switches pages. Never send it to another page.
                if (visiblePageKey == targetPageKey && sessions[targetPageKey] === targetSession) {
                    commit(targetSession.elements + element)
                    targetSession.selected = setOf(element.id)
                    tool = CanvasEditorTool.LASSO
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Could not insert image."
            } finally {
                importBusy = false
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CanvasEditorTool.entries.filter { it != CanvasEditorTool.FOUNTAIN && it != CanvasEditorTool.PENCIL }.forEach { choice ->
                        val active = tool == choice || choice == CanvasEditorTool.BALL && tool in listOf(CanvasEditorTool.FOUNTAIN, CanvasEditorTool.PENCIL)
                        DrawingToolButton(if (choice == CanvasEditorTool.BALL && active) tool.label else choice.label, choice.symbol(), active,
                            enabled = choice != CanvasEditorTool.IMAGE || !importBusy) {
                            if (choice == CanvasEditorTool.IMAGE) insertImage()
                            else { tool = choice; if (choice != CanvasEditorTool.LASSO) session.selected = emptySet() }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Suppress("UNUSED_VARIABLE") val historyRevision = session.revision
                    WorkspaceAction("Undo", InkoraSymbol.UNDO, { undo() }, enabled = session.history.canUndo)
                    WorkspaceAction("Redo", InkoraSymbol.REDO, { redo() }, enabled = session.history.canRedo)
                    VerticalDivider(Modifier.height(24.dp))
                    val palette = listOf(0xFF202124L to "Graphite", 0xFF1464A5L to "Blue", 0xFFC4473AL to "Coral", 0xFF39855CL to "Forest", 0xFF8C58ABL to "Violet", 0xFFFFD54FL to "Yellow")
                    palette.forEach { (argb, name) ->
                        Surface(onClick = { color = argb }, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                            border = if (color == argb) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.size(44.dp).semantics { contentDescription = "$name ink${if (color == argb) ", selected" else ""}" }) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(24.dp)) { drawCircle(Color(argb)) }
                                if (color == argb) InkoraIcon(InkoraSymbol.CHECK, Modifier.size(16.dp), if (name == "Yellow") Color(0xFF202124) else Color.White)
                            }
                        }
                    }
                    var widthMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { widthMenu = true }) { Text("${width} pt") }
                        DropdownMenu(widthMenu, { widthMenu = false }) {
                            listOf(1f, 2.5f, 5f, 8f, 12f).forEach { value -> DropdownMenuItem(text = { Text("$value pt") }, onClick = { width = value; widthMenu = false }) }
                        }
                    }
                    if (tool in listOf(CanvasEditorTool.BALL, CanvasEditorTool.FOUNTAIN, CanvasEditorTool.PENCIL)) {
                        var penMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { penMenu = true }) { Text(tool.label) }
                            DropdownMenu(penMenu, { penMenu = false }) {
                                listOf(CanvasEditorTool.BALL, CanvasEditorTool.FOUNTAIN, CanvasEditorTool.PENCIL).forEach { variant ->
                                    DropdownMenuItem(text = { Text(variant.label) }, onClick = { tool = variant; penMenu = false })
                                }
                            }
                        }
                    }
                    if (tool == CanvasEditorTool.SHAPE) {
                        var shapeMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { shapeMenu = true }) { Text(shapeKind.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            DropdownMenu(shapeMenu, { shapeMenu = false }) {
                                CanvasShapeKind.entries.forEach { kind -> DropdownMenuItem(text = { Text(kind.name.lowercase()) }, onClick = { shapeKind = kind; shapeMenu = false }) }
                            }
                        }
                    }
                    FilterChip(selected = drawFinger, onClick = { drawFinger = !drawFinger }, label = { Text("Finger ink") })
                    if (session.selected.isNotEmpty()) {
                        VerticalDivider(Modifier.height(24.dp))
                        WorkspaceAction("Delete ${session.selected.size}", InkoraSymbol.TRASH, { deleteSelection() })
                        listOf(.8f to "Smaller", 1.25f to "Larger").forEach { (factor, label) ->
                            TextButton(onClick = {
                                val anchor = selectionBounds(session.elements.filter { it.id in session.selected })?.center ?: CanvasPoint(0f, 0f)
                                commit(session.elements.map { if (it.id in session.selected) it.scaled(factor, anchor) else it })
                            }) { Text(label) }
                        }
                        val selectedText = session.elements.singleOrNull { it.id in session.selected } as? CanvasElement.Text
                        if (selectedText != null) TextButton(onClick = { pendingText = PendingCanvasText(CanvasPoint(selectedText.x, selectedText.y), selectedText.sticky, selectedText) }) { Text("Edit text") }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val cachedPaths = remember(session.elements) { session.elements.filterIsInstance<CanvasElement.Ink>().associate { it.id to canvasStrokePath(it.points) } }
        // Keep black margin ink readable even when the surrounding controls use a dark theme.
        val deskColor = Color(0xFFF0F3F0)
        val dotColor = Color(0xFF536663).copy(alpha = .22f)
        val accentColor = MaterialTheme.colorScheme.primary
        Box(Modifier.weight(1f).fillMaxWidth().background(deskColor).clipToBounds()) {
            Canvas(Modifier.fillMaxSize().onSizeChanged { viewport = it }
                .focusRequester(canvasFocus).onPreviewKeyEvent { event ->
                    if (event.key == Key.Spacebar) { spaceHeld = event.type == KeyEventType.KeyDown; true }
                    else if (event.type != KeyEventType.KeyDown) false
                    else if (event.isKeyCtrlPressed || event.isKeyMetaPressed) when (event.key) {
                        Key.Z -> { if (event.isKeyShiftPressed) redo() else undo(); true }
                        Key.Y -> { redo(); true }
                        else -> false
                    } else when (event.key) {
                        Key.P -> { tool = CanvasEditorTool.BALL; true }
                        Key.H -> { tool = CanvasEditorTool.HAND; true }
                        Key.E -> { tool = CanvasEditorTool.ERASER; true }
                        Key.L -> { tool = CanvasEditorTool.LASSO; true }
                        Key.T -> { tool = CanvasEditorTool.TEXT; true }
                        Key.F -> { fitAll(); true }
                        Key.Escape -> { session.selected = emptySet(); spaceHeld = false; true }
                        Key.Delete, Key.Backspace -> { if (session.selected.isNotEmpty()) { deleteSelection(); true } else false }
                        else -> false
                    }
                }.focusable()
                .semantics { contentDescription = "Drawing workspace. Ink anywhere around the page. Select Pan to move, or Fit all notes to see everything." }
                .pointerInput(pageKey, viewport, tool, width, color, shapeKind, drawFinger, safePageWidth, safePageHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        canvasFocus.requestFocus()
                        val initial = session.elements
                        val primaryId = down.id
                        var previous = down.position
                        val start = toPage(down.position, down.pressure)
                        val panOnly = spaceHeld || currentEvent.buttons.isSecondaryPressed || tool == CanvasEditorTool.HAND || (down.type == PointerType.Touch && !drawFinger)
                        val actualTool = if (down.type == PointerType.Eraser) CanvasEditorTool.ERASER else tool
                        var multiTouch = false
                        var moved = false
                        val selectionHit = actualTool == CanvasEditorTool.LASSO && session.elements.any { it.id in session.selected && it.bounds().contains(start, 8f / currentScale()) }
                        activePoints.clear()
                        if (!panOnly) {
                            if (actualTool.isInk() || actualTool == CanvasEditorTool.LASSO && !selectionHit) activePoints.add(start)
                            if (actualTool == CanvasEditorTool.ERASER) session.elements = initial.filterNot { it.hitTest(start, max(6f, width * 2)) }
                            if (actualTool == CanvasEditorTool.SHAPE) activeShape = CanvasElement.Shape(canvasId(), shapeKind, start, start, color, width)
                        }
                        down.consume()
                        repaint++
                        var end = start
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.size >= 2) {
                                if (!multiTouch) { session.elements = initial; activePoints.clear(); activeShape = null; multiTouch = true }
                                val a = pointers[0]
                                val b = pointers[1]
                                if (a.previousPressed && b.previousPressed) {
                                    val oldCenter = (a.previousPosition + b.previousPosition) / 2f
                                    val newCenter = (a.position + b.position) / 2f
                                    val oldDistance = (a.previousPosition - b.previousPosition).getDistance()
                                    val newDistance = (a.position - b.position).getDistance()
                                    if (oldDistance > 1f) zoomAt(newDistance / oldDistance, oldCenter)
                                    session.pan += newCenter - oldCenter
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.firstOrNull { it.id == primaryId }
                                if (change != null && change.pressed) {
                                    end = toPage(change.position, change.pressure)
                                    val delta = change.position - previous
                                    if (delta.getDistance() > .4f) moved = true
                                    if (multiTouch || panOnly) session.pan += delta
                                    else when {
                                        actualTool.isInk() -> {
                                            change.historical.forEach { history ->
                                                val sample = toPage(history.position, change.pressure)
                                                if (activePoints.lastOrNull()?.let { abs(it.x - sample.x) + abs(it.y - sample.y) > .15f } != false) activePoints.add(sample)
                                            }
                                            if (activePoints.lastOrNull()?.let { abs(it.x - end.x) + abs(it.y - end.y) > .15f } != false) activePoints.add(end)
                                        }
                                        actualTool == CanvasEditorTool.ERASER -> {
                                            val before = toPage(previous, change.pressure)
                                            val distance = (change.position - previous).getDistance() / currentScale()
                                            val steps = (distance / max(3f, width)).roundToInt().coerceIn(1, 200)
                                            session.elements = session.elements.filterNot { element ->
                                                (0..steps).any { step ->
                                                    val t = step.toFloat() / steps
                                                    element.hitTest(CanvasPoint(before.x + (end.x - before.x) * t, before.y + (end.y - before.y) * t), max(6f, width * 2))
                                                }
                                            }
                                        }
                                        actualTool == CanvasEditorTool.SHAPE -> activeShape = activeShape?.copy(end = end)
                                        actualTool == CanvasEditorTool.LASSO && selectionHit -> session.elements = initial.map { if (it.id in session.selected) it.translated(end.x - start.x, end.y - start.y) else it }
                                        actualTool == CanvasEditorTool.LASSO -> activePoints.add(end)
                                    }
                                    previous = change.position
                                    change.consume()
                                }
                            }
                            repaint++
                            pressed = event.changes.any { it.pressed }
                        } while (pressed)
                        if (!multiTouch && !panOnly) {
                            when {
                                actualTool.isInk() && activePoints.isNotEmpty() -> commit(initial + activeInk(actualTool, activePoints.toList(), color, width).copy(id = canvasId()))
                                actualTool == CanvasEditorTool.ERASER || selectionHit -> commit(session.elements)
                                actualTool == CanvasEditorTool.SHAPE -> activeShape?.let { commit(initial + it) }
                                actualTool == CanvasEditorTool.LASSO -> {
                                    session.selected = if (moved && activePoints.size >= 3) initial.filter { it.insideLasso(activePoints) }.map { it.id }.toSet()
                                    else initial.lastOrNull { it.hitTest(start, 8f / currentScale()) }?.let { setOf(it.id) } ?: emptySet()
                                }
                                actualTool == CanvasEditorTool.TEXT || actualTool == CanvasEditorTool.STICKY -> {
                                    val existing = initial.lastOrNull { it is CanvasElement.Text && it.hitTest(start) } as? CanvasElement.Text
                                    pendingText = PendingCanvasText(start, actualTool == CanvasEditorTool.STICKY, existing)
                                }
                            }
                        }
                        activePoints.clear()
                        activeShape = null
                        repaint++
                    }
                }) {
                @Suppress("UNUSED_VARIABLE") val drawRevision = repaint
                val scale = currentScale()
                val pageOrigin = origin()
                if (showGrid) {
                    val gap = max(18f, 24f * scale)
                    var x = pageOrigin.x - floor(pageOrigin.x / gap) * gap
                    while (x < size.width) {
                        var y = pageOrigin.y - floor(pageOrigin.y / gap) * gap
                        while (y < size.height) { drawCircle(dotColor, .9f, Offset(x, y)); y += gap }
                        x += gap
                    }
                }
                val visibleBounds = CanvasBounds(-pageOrigin.x / scale, -pageOrigin.y / scale,
                    (size.width - pageOrigin.x) / scale, (size.height - pageOrigin.y) / scale)
                withTransform({ translate(pageOrigin.x, pageOrigin.y); scale(scale, scale, Offset.Zero) }) {
                    drawRect(Color.Black.copy(alpha = .055f), Offset(4f / scale, 5f / scale), Size(safePageWidth, safePageHeight))
                    drawRect(paperColor, size = Size(safePageWidth, safePageHeight))
                    clipRect(0f, 0f, safePageWidth, safePageHeight) {
                        if (background != null) drawImage(background, dstSize = IntSize(safePageWidth.roundToInt().coerceAtLeast(1), safePageHeight.roundToInt().coerceAtLeast(1)))
                        else drawCanvasPaper(template, safePageWidth, safePageHeight, paperColor)
                    }
                    drawRect(Color(0xFFADB5BB), size = Size(safePageWidth, safePageHeight), style = Stroke(1f / scale))
                    session.elements.forEach { if (it.bounds().intersects(visibleBounds)) drawCanvasElement(it, textMeasurer, imageCache, cachedPaths[it.id]) }
                        if (tool.isInk() && activePoints.isNotEmpty()) drawCanvasElement(activeInk(tool, activePoints, color, width), textMeasurer, imageCache)
                        activeShape?.let { drawCanvasElement(it, textMeasurer, imageCache) }
                        if (tool == CanvasEditorTool.LASSO && activePoints.size > 1) {
                            val path = canvasStrokePath(activePoints).apply { close() }
                            drawPath(path, Color(0x181464A5))
                            drawPath(path, Color(0xFF1464A5), style = Stroke(1.5f / scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f / scale, 4f / scale))))
                        }
                        selectionBounds(session.elements.filter { it.id in session.selected })?.let { bounds ->
                            drawRect(Color(0xFF1464A5), Offset(bounds.left - 3, bounds.top - 3), Size(bounds.width + 6, bounds.height + 6), style = Stroke(1.5f / scale))
                        }
                }
            }
            if (session.elements.isEmpty()) Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .95f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text("Room for every thought. Draw on or around the page.", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val outsideCount = session.elements.count { val b = it.bounds(); b.left < 0 || b.top < 0 || b.right > safePageWidth || b.bottom > safePageHeight }
                Text(if (outsideCount > 0) "$outsideCount margin ${if (outsideCount == 1) "note" else "notes"}" else "Open canvas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { showGrid = !showGrid }, modifier = Modifier.semantics { contentDescription = if (showGrid) "Hide canvas grid" else "Show canvas grid" }) {
                    InkoraIcon(InkoraSymbol.GRID, tint = if (showGrid) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VerticalDivider(Modifier.height(20.dp))
                IconButton(onClick = { zoomAt(.8f, Offset(viewport.width / 2f, viewport.height / 2f)) }, modifier = Modifier.semantics { contentDescription = "Zoom out" }) { InkoraIcon(InkoraSymbol.MINUS) }
                Text("${(session.zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { zoomAt(1.25f, Offset(viewport.width / 2f, viewport.height / 2f)) }, modifier = Modifier.semantics { contentDescription = "Zoom in" }) { InkoraIcon(InkoraSymbol.PLUS) }
                TextButton(onClick = { session.zoom = 1f; session.pan = Offset.Zero }) { Text("Fit page") }
                WorkspaceAction("Fit all notes", InkoraSymbol.FIT, { fitAll() })
                TextButton(onClick = { showHelp = true }) { Text("Shortcuts") }
            }
        }
    }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Your canvas, without edges") }, text = {
        Text("Draw, highlight, erase and add notes anywhere around the paper. Everything is saved with this page.\n\nPan: select Pan, hold Space and drag, or use two fingers.\nZoom: pinch, use + / −, or Ctrl + mouse wheel.\nP: pen · E: eraser · L: selection · T: text · H: pan\nCtrl + Z: undo · Ctrl + Shift + Z: redo\nF: fit all notes · Delete: delete selection\n\nFit page returns to the paper. Fit all notes reveals every margin note. PDF exports expand to include them.")
    }, confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } })

    pendingText?.let { pending ->
        var value by remember(pending) { mutableStateOf(pending.existing?.text ?: "") }
        AlertDialog(onDismissRequest = { pendingText = null }, title = { Text(if (pending.sticky) "Sticky note" else "Text") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Write here") }) },
            confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = {
                val text = pending.existing?.copy(text = value) ?: CanvasElement.Text(canvasId(), pending.point.x, pending.point.y, value, color, 16f, pending.sticky)
                commit(if (pending.existing != null) session.elements.map { if (it.id == text.id) text else it } else session.elements + text)
                pendingText = null
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { pendingText = null }) { Text("Cancel") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Drawing editor") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

private fun canvasId(): String = "element-${Random.nextLong().toULong().toString(16)}-${Random.nextLong().toULong().toString(16)}"

private fun CanvasEditorTool.isInk(): Boolean = this == CanvasEditorTool.BALL || this == CanvasEditorTool.FOUNTAIN || this == CanvasEditorTool.PENCIL || this == CanvasEditorTool.HIGHLIGHTER

private fun activeInk(tool: CanvasEditorTool, points: List<CanvasPoint>, color: Long, width: Float): CanvasElement.Ink =
    CanvasElement.Ink("active", points.smoothStroke(), color, if (tool == CanvasEditorTool.HIGHLIGHTER) width * 5 else width,
        opacity = when (tool) { CanvasEditorTool.HIGHLIGHTER -> .3f; CanvasEditorTool.PENCIL -> .72f; else -> 1f },
        tool = when (tool) { CanvasEditorTool.FOUNTAIN -> CanvasInkTool.FOUNTAIN; CanvasEditorTool.PENCIL -> CanvasInkTool.PENCIL; CanvasEditorTool.HIGHLIGHTER -> CanvasInkTool.HIGHLIGHTER; else -> CanvasInkTool.BALL })

private fun canvasStrokePath(points: List<CanvasPoint>): Path = Path().apply {
    val first = points.firstOrNull() ?: return@apply
    moveTo(first.x, first.y)
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val point = points[index]
        quadraticTo(previous.x, previous.y, (previous.x + point.x) / 2f, (previous.y + point.y) / 2f)
    }
    points.lastOrNull()?.let { lineTo(it.x, it.y) }
}

private fun selectionBounds(elements: List<CanvasElement>): CanvasBounds? {
    if (elements.isEmpty()) return null
    val bounds = elements.map { it.bounds() }
    return CanvasBounds(bounds.minOf { it.left }, bounds.minOf { it.top }, bounds.maxOf { it.right }, bounds.maxOf { it.bottom })
}

private fun DrawScope.drawCanvasElement(element: CanvasElement, textMeasurer: TextMeasurer, images: Map<String, ImageBitmap>, cachedPath: Path? = null) {
    when (element) {
        is CanvasElement.Ink -> {
            val inkColor = Color(element.colorArgb).copy(alpha = element.opacity.coerceIn(0f, 1f))
            if (element.points.size == 1) { val p = element.points[0]; drawCircle(inkColor, element.width / 2, Offset(p.x, p.y)) }
            else if (element.tool == CanvasInkTool.FOUNTAIN || element.tool == CanvasInkTool.PENCIL) {
                element.points.zipWithNext().forEach { (a, b) ->
                    val pressure = ((a.pressure + b.pressure) / 2f).coerceIn(.05f, 1.5f)
                    val strokeWidth = element.width * if (element.tool == CanvasInkTool.FOUNTAIN) (.25f + pressure * 1.25f) else (.6f + pressure * .5f)
                    drawLine(inkColor, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth, StrokeCap.Round)
                }
            } else drawPath(cachedPath ?: canvasStrokePath(element.points), inkColor, style = Stroke(element.width, cap = if (element.tool == CanvasInkTool.HIGHLIGHTER) StrokeCap.Butt else StrokeCap.Round))
        }
        is CanvasElement.Shape -> {
            val color = Color(element.colorArgb).copy(alpha = element.opacity.coerceIn(0f, 1f))
            val start = Offset(element.start.x, element.start.y)
            val end = Offset(element.end.x, element.end.y)
            val corner = Offset(min(start.x, end.x), min(start.y, end.y))
            val size = Size(abs(end.x - start.x), abs(end.y - start.y))
            when (element.kind) {
                CanvasShapeKind.LINE -> drawLine(color, start, end, element.width, StrokeCap.Round)
                CanvasShapeKind.RECTANGLE -> drawRect(color, corner, size, style = Stroke(element.width))
                CanvasShapeKind.ELLIPSE -> drawOval(color, corner, size, style = Stroke(element.width))
                CanvasShapeKind.ARROW -> {
                    drawLine(color, start, end, element.width, StrokeCap.Round)
                    val angle = atan2(end.y - start.y, end.x - start.x)
                    val length = max(10f, element.width * 4)
                    val path = Path().apply {
                        moveTo(end.x - length * cos(angle - .5f), end.y - length * sin(angle - .5f))
                        lineTo(end.x, end.y)
                        lineTo(end.x - length * cos(angle + .5f), end.y - length * sin(angle + .5f))
                    }
                    drawPath(path, color, style = Stroke(element.width, cap = StrokeCap.Round))
                }
            }
        }
        is CanvasElement.Text -> {
            val padding = if (element.sticky) 12f else 0f
            if (element.sticky) {
                val bounds = element.bounds()
                drawRect(Color(0xFFFFEE91), Offset(element.x, element.y), Size(bounds.width, bounds.height))
                drawRect(Color(0xFFE2C967), Offset(element.x, element.y), Size(bounds.width, bounds.height), style = Stroke(.7f))
            }
            drawText(textMeasurer, element.text, topLeft = Offset(element.x + padding, element.y + padding), style = TextStyle(color = Color(element.colorArgb), fontSize = element.fontSize.toSp()))
        }
        is CanvasElement.Image -> {
            val image = images[element.path]
            if (image != null) drawImage(image, dstOffset = IntOffset(element.x.roundToInt(), element.y.roundToInt()), dstSize = IntSize(element.width.roundToInt().coerceAtLeast(1), element.height.roundToInt().coerceAtLeast(1)))
            else {
                drawRect(Color(0xFFE8EBEE), Offset(element.x, element.y), Size(element.width, element.height))
                drawText(textMeasurer, "Loading image…", topLeft = Offset(element.x + 8, element.y + 8), style = TextStyle(color = Color.DarkGray, fontSize = 12f.toSp()))
            }
        }
    }
}

/** Templates are rendered in document coordinates so the rulings never drift while zooming. */
private fun DrawScope.drawCanvasPaper(template: PaperTemplate, width: Float, height: Float, paperColor: Color) {
    val line = if (paperColor.red + paperColor.green + paperColor.blue < 1.5f) Color(0xFF59636F) else Color(0xFFD7DFE7)
    fun horizontal(y: Float, left: Float = 28f, right: Float = width - 28f) = drawLine(line, Offset(left, y), Offset(right, y), .65f)
    fun vertical(x: Float, top: Float = 28f, bottom: Float = height - 28f) = drawLine(line, Offset(x, top), Offset(x, bottom), .65f)
    when (template) {
        PaperTemplate.BLANK, PaperTemplate.CUSTOM -> Unit
        PaperTemplate.GRID, PaperTemplate.SMALL_GRID, PaperTemplate.DOTTED -> {
            val gap = if (template == PaperTemplate.SMALL_GRID) 12f else 24f
            var y = 28f
            while (y < height - 20) {
                if (template == PaperTemplate.DOTTED) { var x = 28f; while (x < width - 20) { drawCircle(line, .8f, Offset(x, y)); x += gap } }
                else horizontal(y)
                y += gap
            }
            if (template != PaperTemplate.DOTTED) { var x = 28f; while (x < width - 20) { vertical(x); x += gap } }
        }
        PaperTemplate.MUSIC_SHEET -> { var y = 55f; while (y < height - 60) { repeat(5) { horizontal(y + it * 8) }; y += 90 } }
        PaperTemplate.CORNELL_NOTES, PaperTemplate.LECTURE_NOTES -> {
            horizontal(65f); horizontal(height - 140); vertical(width * .28f, 65f, height - 140)
            var y = 93f; while (y < height - 145) { horizontal(y, width * .28f); y += 26 }
        }
        PaperTemplate.WEEKLY_PLANNER -> { repeat(8) { vertical(28f + (width - 56) * it / 7, 70f) }; horizontal(70f); horizontal(120f) }
        PaperTemplate.FLASHCARD_SHEET -> { vertical(width / 2); repeat(5) { horizontal(28f + (height - 56) * it / 4) } }
        PaperTemplate.CONCEPT_MAP, PaperTemplate.MIND_MAP -> drawOval(line, Offset(width * .35f, height * .4f), Size(width * .3f, height * .12f), style = Stroke(.8f))
        else -> {
            val spacing = when (template) { PaperTemplate.COLLEGE_RULED -> 20f; PaperTemplate.WIDE_RULED -> 32f; else -> 26f }
            horizontal(64f)
            var y = 64f + spacing
            while (y < height - 28f) {
                if (template == PaperTemplate.CHECKLIST || template == PaperTemplate.ASSIGNMENT_TRACKER) {
                    drawRect(line, Offset(30f, y - 13f), Size(10f, 10f), style = Stroke(.8f)); horizontal(y, 48f)
                } else horizontal(y)
                y += spacing
            }
            if (template == PaperTemplate.PLANNER || template == PaperTemplate.DAILY_PLANNER || template == PaperTemplate.STUDY_PLANNER) vertical(width * .3f, 64f)
            if (template == PaperTemplate.LAB_NOTES || template == PaperTemplate.MEDICATION_NOTES || template == PaperTemplate.EXAM_REVIEW) vertical(width * .5f, 64f)
        }
    }
}
