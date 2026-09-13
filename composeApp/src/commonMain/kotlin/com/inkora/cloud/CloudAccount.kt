package com.inkora.cloud

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentId
import com.inkora.domain.model.SyncStatus
import com.inkora.domain.repository.DocumentRepository
import com.inkora.platform.platformUuid
import com.inkora.platform.PlatformFile
import com.inkora.study.Quiz
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

    suspend fun signInWithGoogle(): CloudAuthResult = runBusy {
        val verifier = "${platformUuid().replace("-", "")}${platformUuid().replace("-", "")}".take(128)
        val redirect = oauthRedirectUri()
        val authorizeUrl = client.googleAuthorizeUrl(redirect, oauthCodeChallenge(verifier))
        val callback = launchOAuth(authorizeUrl, redirect)
        callback.error?.let { error("Google sign-in failed: $it") }
        val code = callback.code ?: error("Google sign-in did not return an authorization code.")
        val result = client.exchangePkce(code, verifier)
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
            if (document is DocumentContent.Pdf && document.managedFilePath.isNotBlank()) {
                val source = PlatformFile(document.managedFilePath, document.summary.sourceFileName ?: "document.pdf", "application/pdf")
                if (files.exists(source)) client.uploadDocumentFile(current, document, files.read(source))
            }
            client.upsertDocument(current, document)
            repository.saveDocument(document.withSyncStatus(SyncStatus.SYNCED))
            uploaded++
        }

        var downloaded = 0
        var skipped = 0
        client.listDocuments(current).forEach { row ->
            runCatching { json.decodeFromJsonElement<DocumentContent>(row.payload) }
                .onSuccess { remote ->
                    val localDocument = repository.getDocument(DocumentId(row.id))
                    if (localDocument != null && remote.summary.modifiedAtEpochMs <= localDocument.summary.modifiedAtEpochMs) {
                        skipped++
                        return@onSuccess
                    }
                    if (remote is DocumentContent.Pdf) {
                        val storagePath = row.sourceFilePath
                        if (storagePath.isNullOrBlank()) { skipped++; return@onSuccess }
                        runCatching {
                            val bytes = client.downloadDocumentFile(current, storagePath)
                            val folder = files.createDirectory(files.child(files.appDataDirectory, "documents"))
                            val managed = files.child(folder, "cloud-${row.id}.pdf")
                            files.write(managed, bytes)
                            repository.saveDocument(remote.copy(managedFilePath = managed.path).withSyncStatus(SyncStatus.SYNCED))
                            downloaded++
                        }.onFailure { skipped++ }
                    } else {
                        repository.saveDocument(remote.withSyncStatus(SyncStatus.SYNCED))
                        downloaded++
                    }
                }
                .onFailure { skipped++ }
        }
        CloudSyncSummary(uploaded, downloaded, skipped)
    }

    suspend fun createShareLink(documentId: String, role: String = "viewer"): CloudShareLink = runBusy {
        val current = _session.value ?: error("Sign in to create a share link.")
        client.createShareLink(current, documentId, role)
    }

    suspend fun listShareLinks(documentId: String): List<CloudShareLinkRow> = runBusy {
        val current = _session.value ?: error("Sign in to manage share links.")
        client.listShareLinks(current, documentId)
    }

    suspend fun revokeShareLink(linkId: String) = runBusy {
        val current = _session.value ?: error("Sign in to manage share links.")
        client.revokeShareLink(current, linkId)
    }

    suspend fun resolveShareLink(rawToken: String): CloudSharedDocument = runBusy {
        client.resolveShareLink(rawToken)
    }

    suspend fun inviteMember(documentId: String, email: String, role: String = "viewer"): CloudMember = runBusy {
        val current = _session.value ?: error("Sign in to invite a collaborator.")
        client.inviteMember(current, documentId, email, role)
    }

    suspend fun generateAiQuiz(sourceText: String, requestedCount: Int = 8, difficulty: String = "mixed"): Quiz = runBusy {
        val current = _session.value ?: error("Sign in under Account before generating a ChatGPT quiz.")
        client.generateAiQuiz(current, sourceText, requestedCount, difficulty)
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
