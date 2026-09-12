package com.inkora.cloud

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.SyncStatus
import com.inkora.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

data class CloudSyncSummary(
    val uploaded: Int,
    val downloaded: Int,
    val skipped: Int,
)

/** Owns the optional cloud account and the first offline-first sync pass. */
class CloudAccount(
    private val files: com.inkora.platform.PlatformFileSystem,
    private val client: SupabaseClient = SupabaseClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val sessionFile = files.child(files.appDataDirectory, "cloud-session.json")
    private val _session = MutableStateFlow<CloudSession?>(null)
    val session: StateFlow<CloudSession?> = _session.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val configured: Boolean get() = supabaseConfig().isConfigured

    suspend fun restore() {
        if (!files.exists(sessionFile)) return
        _session.value = runCatching { json.decodeFromString<CloudSession>(files.read(sessionFile).decodeToString()) }.getOrNull()
    }

    suspend fun signIn(email: String, password: String): CloudAuthResult = runBusy {
        val result = client.signIn(email, password)
        result.session?.let { persist(it) }
        result
    }

    suspend fun signUp(email: String, password: String): CloudAuthResult = runBusy {
        val result = client.signUp(email, password)
        result.session?.let { persist(it) }
        result
    }

    suspend fun signOut() = runBusy {
        _session.value?.let { runCatching { client.signOut(it.accessToken) } }
        _session.value = null
        files.delete(sessionFile)
    }

    suspend fun sync(repository: DocumentRepository): CloudSyncSummary = runBusy {
        val current = _session.value ?: error("Sign in to sync your Inkora documents.")
        val local = repository.observeDocuments(includeTrashed = true).first().mapNotNull { repository.getDocument(it.id) }
        var uploaded = 0
        local.forEach { document ->
            client.upsertDocument(current, document)
            repository.saveDocument(document.withSyncStatus(SyncStatus.SYNCED))
            uploaded++
        }

        val localIds = local.mapTo(mutableSetOf()) { it.summary.id.value }
        var downloaded = 0
        var skipped = 0
        client.listDocuments(current).forEach { row ->
            if (row.id in localIds) {
                skipped++
                return@forEach
            }
            if (row.kind == "PDF") {
                // A PDF's source bytes are uploaded in the storage phase. Do not
                // create a broken local PDF that has no managed file path.
                skipped++
                return@forEach
            }
            runCatching { json.decodeFromJsonElement<DocumentContent>(row.payload) }
                .onSuccess { repository.saveDocument(it.withSyncStatus(SyncStatus.SYNCED)); downloaded++ }
                .onFailure { skipped++ }
        }
        CloudSyncSummary(uploaded, downloaded, skipped)
    }

    private suspend fun persist(value: CloudSession) {
        _session.value = value
        files.write(sessionFile, json.encodeToString(value).encodeToByteArray())
    }

    private suspend fun <T> runBusy(block: suspend () -> T): T {
        _busy.value = true
        return try {
            block()
        } finally {
            _busy.value = false
        }
    }

    private fun DocumentContent.withSyncStatus(status: SyncStatus): DocumentContent {
        val next = summary.copy(syncStatus = status)
        return when (this) {
            is DocumentContent.Notebook -> copy(summary = next)
            is DocumentContent.Pdf -> copy(summary = next)
            is DocumentContent.Whiteboard -> copy(summary = next)
            is DocumentContent.TextDocument -> copy(summary = next)
            is DocumentContent.QuickNote -> copy(summary = next)
        }
    }
}
