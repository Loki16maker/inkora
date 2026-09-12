# Inkora architecture

Inkora is a Kotlin Multiplatform application with a feature-first Clean Architecture boundary:

```text
Compose targets (Android / Desktop / future Apple)
        ↓
feature state holders and application services
        ↓
domain models + repository interfaces
        ↓
SQLDelight metadata     Okio managed files     platform adapters
```

`commonMain` owns document models, repository contracts, drawing math, file-management contracts, and state holders. Platform source sets supply database drivers, file-picker bridges, PDF engines, and stylus event adapters. The common layer never checks the current operating system.

State holders expose immutable `StateFlow` values. A caller supplies a lifecycle-owned `CoroutineScope`; closing the holder cancels its observation job. High-frequency stylus samples are collected by `StrokeInputCollector` and sent directly to a renderer. Only the finalized stroke enters document state and autosave, keeping Compose recomposition out of the input path.

The SQLDelight schema stores queryable metadata and one serialized stroke blob per page. The `InMemoryDocumentRepository` is a complete repository contract implementation for tests and previews; production wiring can replace it with the generated SQLDelight queries without changing presentation code.

Key decisions:

- Local-first persistence is the source of truth; sync states are modeled but V1 uses `LOCAL_ONLY`.
- IDs are opaque value classes, so display names can safely collide.
- Page coordinates are logical and independent of viewport zoom/rotation.
- Undo/redo stores reversible edits and has a bounded depth.
- Managed imports copy files into app-owned storage after basic signature validation.
