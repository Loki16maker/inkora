package com.inkora.data.store

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.repository.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public sealed interface SaveState {
    public data object Idle : SaveState
    public data object Saving : SaveState
    public data object Saved : SaveState
    public data class Failed(val message: String) : SaveState
}

/**
 * Lifecycle-owned editor state with debounced local autosave. Only finalized
 * document snapshots should be passed to [update]; pointer samples belong in
 * [com.inkora.drawing.StrokeInputCollector].
 */
public class DocumentEditorStore(
    private val repository: DocumentRepository,
    private val scope: CoroutineScope,
    private val autosaveDelayMs: Long = 500L,
) {
    init {
        require(autosaveDelayMs >= 0L) { "Autosave delay cannot be negative" }
    }

    private val saveMutex = Mutex()
    private val _document = MutableStateFlow<DocumentContent?>(null)
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    private var autosaveJob: Job? = null

    public val document: StateFlow<DocumentContent?> = _document.asStateFlow()
    public val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    public suspend fun load(id: DocumentId): DocumentContent? {
        val loaded = repository.getDocument(id)
        _document.value = loaded
        _saveState.value = SaveState.Idle
        return loaded
    }

    public fun update(transform: (DocumentContent) -> DocumentContent) {
        _document.update { current -> current?.let(transform) }
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(autosaveDelayMs)
            saveNow()
        }
    }

    public suspend fun saveNow() {
        val snapshot = document.value ?: return
        saveMutex.withLock {
            _saveState.value = SaveState.Saving
            runCatching { repository.saveDocument(snapshot) }
                .onSuccess { _saveState.value = SaveState.Saved }
                .onFailure { error -> _saveState.value = SaveState.Failed(error.message ?: "Unable to save document") }
        }
    }

    /** Flushes a pending autosave before the editor leaves the workspace. */
    public suspend fun closeAndSave() {
        autosaveJob?.cancel()
        autosaveJob = null
        saveNow()
    }

    public fun close() {
        autosaveJob?.cancel()
        autosaveJob = null
    }
}
