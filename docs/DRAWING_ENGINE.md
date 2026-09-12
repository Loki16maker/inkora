# Inkora drawing engine

The drawing pipeline is deliberately split into input, rendering, and persistence:

1. A platform stylus adapter receives pointer samples with position, pressure, tilt, and timestamp.
2. The adapter appends samples to `StrokeInputCollector`. This is a mutable hot path and does not publish every point through Compose state.
3. A renderer consumes the active points and draws a transient path. Tool-specific width and opacity are derived from `StrokeStyle`.
4. On pointer-up, the collector creates one immutable `Stroke`; the editor applies an `AddStrokeEdit` and queues page persistence.

Strokes use logical page coordinates. `PageCoordinateTransform` maps them to a viewport around the page center and reverses the operation for hit testing and new input. It supports zoom anchored at a focus point and quarter-turn page rotation. Changing a paper template therefore leaves existing ink in place.

The common model carries pressure and tilt without assuming a specific platform API. Android can map MotionEvent pressure/tilt, Windows can map PointerPoint pressure/tilt, and future Apple targets can map Pencil samples. Prediction and smoothing remain renderer policies; persisted points are the original samples plus timestamps.

Undo/redo is command based. Finalized additions, removals, lasso moves, and style changes should implement `UndoableEdit<StrokeCollectionState>` (or a page-level equivalent). The bounded manager invalidates redo after a new edit and never blocks the input loop.

Partial erasing and lasso selection can operate against `Stroke.bounds` first, then run precise geometry tests. This keeps common hit testing cheap while retaining an extensible path for high fidelity tools.
