package com.inkora.drawing

/** A reversible edit. Implementations should be immutable and side-effect free. */
public interface UndoableEdit<S> {
    public fun apply(state: S): S
    public fun revert(state: S): S
}

/**
 * Bounded undo/redo history for page edits. The stack stores commands, while
 * the page state remains owned by the caller and can be persisted after each
 * finalized edit.
 */
public class UndoRedoManager<S>(
    initialState: S,
    private val maxDepth: Int = 100,
) {
    init {
        require(maxDepth > 0) { "Undo history depth must be positive" }
    }

    private var current: S = initialState
    private val undoStack = ArrayDeque<UndoableEdit<S>>()
    private val redoStack = ArrayDeque<UndoableEdit<S>>()

    public val state: S get() = current
    public val canUndo: Boolean get() = undoStack.isNotEmpty()
    public val canRedo: Boolean get() = redoStack.isNotEmpty()

    public fun execute(edit: UndoableEdit<S>): S {
        current = edit.apply(current)
        undoStack.addLast(edit)
        if (undoStack.size > maxDepth) undoStack.removeFirst()
        redoStack.clear()
        return current
    }

    public fun undo(): S? {
        val edit = undoStack.removeLastOrNull() ?: return null
        current = edit.revert(current)
        redoStack.addLast(edit)
        return current
    }

    public fun redo(): S? {
        val edit = redoStack.removeLastOrNull() ?: return null
        current = edit.apply(current)
        undoStack.addLast(edit)
        return current
    }

    public fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

public data class StrokeCollectionState(val strokes: List<Stroke> = emptyList())

public class AddStrokeEdit(private val stroke: Stroke) : UndoableEdit<StrokeCollectionState> {
    override fun apply(state: StrokeCollectionState): StrokeCollectionState =
        state.copy(strokes = state.strokes + stroke)

    override fun revert(state: StrokeCollectionState): StrokeCollectionState =
        state.copy(strokes = state.strokes.filterNot { it.id == stroke.id })
}

public class RemoveStrokesEdit(private val ids: Set<String>) : UndoableEdit<StrokeCollectionState> {
    private var removed: List<Stroke> = emptyList()

    override fun apply(state: StrokeCollectionState): StrokeCollectionState {
        removed = state.strokes.filter { it.id in ids }
        return state.copy(strokes = state.strokes.filterNot { it.id in ids })
    }

    override fun revert(state: StrokeCollectionState): StrokeCollectionState {
        val restored = (state.strokes + removed).distinctBy { it.id }
        return state.copy(strokes = restored)
    }
}
