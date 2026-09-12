package com.inkora.data.store

import com.inkora.domain.model.DocumentSummary
import com.inkora.domain.model.Folder
import com.inkora.domain.repository.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

public data class LibraryState(
    val documents: List<DocumentSummary> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/** ViewModel-style state holder; callers own and cancel the supplied scope. */
public class LibraryStore(
    private val repository: DocumentRepository,
    scope: CoroutineScope,
) {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(LibraryState())
    public val state: StateFlow<LibraryState> = _state.asStateFlow()
    private var observation: Job? = null

    init {
        observation = combine(repository.observeDocuments(), repository.observeFolders(), query) { docs, folders, search ->
            val visible = if (search.isBlank()) docs else docs.filter { it.title.contains(search, ignoreCase = true) }
            LibraryState(visible, folders, search, isLoading = false)
        }.onEach { _state.value = it }.launchIn(scope)
    }

    public fun setSearchQuery(value: String) {
        query.value = value
    }

    public fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /** Stops collection before the owner cancels its scope. */
    public fun close() {
        observation?.cancel()
        observation = null
    }
}
