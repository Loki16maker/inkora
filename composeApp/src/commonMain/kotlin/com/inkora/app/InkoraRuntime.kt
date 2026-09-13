package com.inkora.app

import com.inkora.data.store.DocumentEditorStore
import com.inkora.cloud.CloudAccount
import com.inkora.cloud.CloudAuthResult
import com.inkora.cloud.CloudSyncSummary
import com.inkora.data.store.SaveState
import com.inkora.domain.model.*
import com.inkora.domain.repository.DocumentRepository
import com.inkora.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import com.inkora.study.ReviewSchedule

/** Restorable pane navigation. Each document retains its own page position. */
@Serializable
data class WorkspaceState(
    val primary: String? = null,
    val secondary: String? = null,
    val stacked: Boolean = false,
    val fraction: Float = .5f,
    val pages: Map<String, Int> = emptyMap(),
    val dark: Boolean = false,
)

/** Owns document sessions beyond individual composable lifetimes. */
class InkoraRuntime(val repository: DocumentRepository, uiDispatcher: CoroutineDispatcher = Dispatchers.Main) {
    // UI/editor session state stays on one dispatcher. Desktop supplies Swing
    // directly to avoid ServiceLoader's handling of exclamation marks in paths.
    val scope = CoroutineScope(SupervisorJob() + uiDispatcher)
    val files = platformFileSystem()
    val pdf = pdfEngine()
    val updates = updateService()
    val cloud = CloudAccount(files)
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sessionFile get() = files.child(files.appDataDirectory, "workspace.json")
    private val sessionMutex = Mutex()
    private var sessionJob: Job? = null
    private val editors = mutableMapOf<String, DocumentEditorStore>()
    val workspace = MutableStateFlow(WorkspaceState())
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val updateCheck = MutableStateFlow<UpdateCheckResult?>(null)
    val updateBusy = MutableStateFlow(false)

    init {
        scope.launch { runCatching { cloud.restore() } }
        run {
            try {
                files.createDirectory(files.appDataDirectory)
                if (files.exists(sessionFile)) {
                    val restored = codec.decodeFromString<WorkspaceState>(files.read(sessionFile).decodeToString())
                    val primary = restored.primary?.takeIf { repository.getDocument(DocumentId(it))?.summary?.isTrashed == false }
                    val secondary = restored.secondary?.takeIf { it != primary && repository.getDocument(DocumentId(it))?.summary?.isTrashed == false }
                    primary?.let { load(it) }
                    secondary?.let { load(it) }
                    workspace.value = restored.copy(primary = primary, secondary = secondary, fraction = restored.fraction.coerceIn(.25f, .75f))
                }
            } finally { ready.value = true }
        }
    }

    fun run(operation: suspend () -> Unit) = scope.launch {
        try { operation() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error.value = failure.message ?: "The operation could not be completed." }
    }

    fun editor(id: String): DocumentEditorStore = checkNotNull(editors[id]) { "Document has not loaded" }

    private suspend fun load(id: String) {
        if (id !in editors) {
            val store = DocumentEditorStore(repository, scope, autosaveDelayMs = 0)
            checkNotNull(store.load(DocumentId(id))) { "Document is missing" }
            editors[id] = store
        }
    }

    suspend fun open(id: String) {
        flush()
        load(id)
        setWorkspace(workspace.value.copy(primary = id, secondary = null))
    }

    suspend fun study(secondId: String) {
        require(secondId != workspace.value.primary) { "Choose a different document for the second pane" }
        load(secondId)
        setWorkspace(workspace.value.copy(secondary = secondId))
    }

    suspend fun library() {
        flush()
        setWorkspace(workspace.value.copy(primary = null, secondary = null))
    }

    fun setWorkspace(next: WorkspaceState) {
        workspace.value = next
        sessionJob?.cancel()
        sessionJob = scope.launch {
            delay(200)
            try { saveWorkspace() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error.value = "Workspace could not be saved: ${failure.message}" }
        }
    }

    fun setPage(id: String, index: Int) = setWorkspace(workspace.value.copy(pages = workspace.value.pages + (id to index.coerceAtLeast(0))))

    fun edit(id: String, transform: (DocumentContent) -> DocumentContent) {
        editor(id).update { current ->
            val changed = transform(current)
            changed.withSummary(changed.summary.copy(modifiedAtEpochMs = platformEpochMillis()))
        }
    }

    suspend fun create(document: DocumentContent) {
        repository.saveDocument(document)
        open(document.summary.id.value)
    }

    suspend fun importPdf() = importDocument(pdfOnly = true)

    /** Import PDF plus readable Office files through the native platform picker. */
    suspend fun importDocument(pdfOnly: Boolean = false) {
        val source = filePicker().pickFile(
            if (pdfOnly) FilePickerRequest(listOf("application/pdf"))
            else FilePickerRequest(SUPPORTED_IMPORT_MIME_TYPES)
        ) ?: return
        busy.value = true
        var managed: PlatformFile? = null
        try {
            val extension = source.displayName.substringAfterLast('.', "").lowercase()
            when (extension) {
                "pdf" -> {
                    managed = files.importToManagedStorage(source, "${platformUuid()}.pdf")
                    val opened = pdf.openDocument(managed)
                    val count = try { opened.pageCount } finally { pdf.closeDocument(opened) }
                    require(count > 0) { "This PDF contains no pages" }
                    val now = platformEpochMillis()
                    val summary = DocumentSummary(DocumentId(platformUuid()), DocumentType.PDF,
                        source.displayName.substringBeforeLast('.').ifBlank { "Imported PDF" }, now, now,
                        pageCount = count, sourceFileName = source.displayName)
                    repository.saveDocument(DocumentContent.Pdf(summary, managed.path))
                    managed = null // The repository now owns the imported copy.
                    open(summary.id.value)
                }
                "doc", "docx", "docm", "ppt", "pptx", "pptm" -> {
                    managed = files.importToManagedStorage(source, "${platformUuid()}.$extension")
                    val text = officeDocumentImporter().extractText(managed)
                    val now = platformEpochMillis()
                    val summary = DocumentSummary(DocumentId(platformUuid()), DocumentType.TEXT_DOCUMENT,
                        source.displayName.substringBeforeLast('.').ifBlank { "Imported document" }, now, now,
                        sourceFileName = source.displayName)
                    repository.saveDocument(DocumentContent.TextDocument(summary, text, managed.path))
                    managed = null // Keep the managed Office source with the document.
                    open(summary.id.value)
                }
                else -> error("Inkora supports PDF, DOCX and PPTX files. Legacy Office files must be saved in the newer format first.")
            }
        } finally {
            managed?.let { withContext(NonCancellable) { files.delete(it) } }
            busy.value = false
        }
    }

    suspend fun exportBackup(): PlatformFile {
        flush()
        val documents = repository.observeDocuments(includeTrashed = true).first().mapNotNull { repository.getDocument(it.id) }
        val folders = repository.observeFolders(includeTrashed = true).first()
        val backup = InkoraBackup(createdAtEpochMs = platformEpochMillis(), folders = folders, documents = documents)
        val file = files.child(files.appDataDirectory, "inkora-backup-${backup.createdAtEpochMs}.${InkoraConfig.backupExtension}")
        files.write(file, codec.encodeToString(backup).encodeToByteArray())
        return file.copy(mimeType = "application/x-inkora-backup")
    }

    suspend fun importBackup() {
        val source = filePicker().pickFile(FilePickerRequest(listOf("application/x-inkora-backup", "application/octet-stream"))) ?: return
        busy.value = true
        try {
            val backup = codec.decodeFromString<InkoraBackup>(files.read(source).decodeToString())
            require(backup.formatVersion in 1..InkoraConfig.backupFormatVersion) { "This backup was created by a newer version of Inkora" }
            backup.folders.forEach { repository.createFolder(it) }
            backup.documents.forEach { repository.saveDocument(it) }
            notice.value = "Backup restored · ${backup.documents.size} documents"
        } finally { busy.value = false }
    }

    suspend fun loadReviewSchedule(documentId: String): ReviewSchedule {
        val file = files.child(files.appDataDirectory, "review-${documentId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
        return if (files.exists(file)) runCatching { codec.decodeFromString<ReviewSchedule>(files.read(file).decodeToString()) }.getOrDefault(ReviewSchedule()) else ReviewSchedule()
    }

    suspend fun saveReviewSchedule(documentId: String, schedule: ReviewSchedule) {
        val file = files.child(files.appDataDirectory, "review-${documentId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
        files.write(file, codec.encodeToString(schedule).encodeToByteArray())
    }

    suspend fun importTemplate(): ImportedTemplate? {
        val source = filePicker().pickFile(FilePickerRequest(listOf("application/x-inkora-template", "application/json"))) ?: return null
        val template = codec.decodeFromString<ImportedTemplate>(files.read(source).decodeToString())
        require(template.name.isNotBlank()) { "Template name cannot be blank" }
        return template
    }

    suspend fun checkForUpdates() {
        updateBusy.value = true
        try {
            updateCheck.value = updates.checkForUpdates()
        } finally {
            updateBusy.value = false
        }
    }

    suspend fun installUpdate(manifest: UpdateManifest) {
        updateBusy.value = true
        try {
            val result = updates.install(manifest)
            when (result.status) {
                UpdateInstallStatus.FAILED -> error.value = result.message
                UpdateInstallStatus.STARTED, UpdateInstallStatus.NEEDS_USER_ACTION -> notice.value = result.message
            }
        } finally {
            updateBusy.value = false
        }
    }

    suspend fun cloudSignIn(email: String, password: String): CloudAuthResult = cloud.signIn(email, password)

    suspend fun cloudSignUp(email: String, password: String): CloudAuthResult = cloud.signUp(email, password)

    suspend fun cloudSignInWithGoogle(): CloudAuthResult = cloud.signInWithGoogle()

    suspend fun cloudSignOut() = cloud.signOut()

    suspend fun cloudSync(): CloudSyncSummary = cloud.sync(repository)

    suspend fun createCloudShareLink(documentId: String, role: String = "viewer"): com.inkora.cloud.CloudShareLink =
        cloud.createShareLink(documentId, role)

    suspend fun inviteCloudMember(documentId: String, email: String, role: String = "viewer"): com.inkora.cloud.CloudMember =
        cloud.inviteMember(documentId, email, role)

    suspend fun listCloudShareLinks(documentId: String): List<com.inkora.cloud.CloudShareLinkRow> =
        cloud.listShareLinks(documentId)

    suspend fun revokeCloudShareLink(linkId: String) = cloud.revokeShareLink(linkId)

    suspend fun resolveCloudShareLink(token: String): com.inkora.cloud.CloudSharedDocument =
        cloud.resolveShareLink(token)

    /** Copies a no-key prompt and opens the user's existing ChatGPT session.
     * The consumer ChatGPT website remains in control of authentication and
     * never exposes its cookies or account session to Inkora. */
    suspend fun openChatGptQuizPrompt(sourceText: String, difficulty: String = "mixed") {
        require(sourceText.isNotBlank()) { "Add some study material before opening ChatGPT." }
        val prompt = buildString {
            append("Create an accurate study quiz from the material below. Use only the supplied facts.\n")
            append("Return JSON only with this shape: {\"title\":\"...\",\"questions\":[{\"prompt\":\"...\",\"answer\":\"...\",\"options\":[\"...\"],\"type\":\"MULTIPLE_CHOICE\"|\"TRUE_FALSE\"|\"SHORT_ANSWER\",\"explanation\":\"...\"}]}\n")
            append("Generate 8 questions at ").append(difficulty).append(" difficulty. Use four choices for multiple choice and [\"true\",\"false\"] for true/false.\n\n")
            append(sourceText.take(60_000))
        }
        val copied = clipboardService().copy(prompt)
        val opened = externalBrowserService().open("https://chatgpt.com/")
        notice.value = when {
            copied && opened -> "Quiz prompt copied. Paste it into ChatGPT, then paste the returned JSON into Inkora."
            copied -> "Quiz prompt copied. Open ChatGPT, paste it, then paste the returned JSON into Inkora."
            opened -> "ChatGPT opened. Copy the prompt from Inkora's clipboard failed; use the study text manually."
            else -> "Could not open ChatGPT. Copy the study text and use chatgpt.com manually."
        }
    }

    /** Imports a shared non-PDF snapshot as a new local document. Original
     * PDF bytes remain owner-only until storage transfer is enabled. */
    suspend fun importCloudShareLink(token: String): DocumentSummary {
        val shared = cloud.resolveShareLink(token)
        require(shared.kind != DocumentType.PDF.name) { "Shared PDF files need storage access; ask the owner to export and share the PDF." }
        val incoming = codec.decodeFromJsonElement<DocumentContent>(shared.payload)
        val id = DocumentId(platformUuid())
        val summary = incoming.summary.copy(
            id = id,
            title = "Shared · ${incoming.summary.title}",
            syncStatus = SyncStatus.LOCAL_ONLY,
            modifiedAtEpochMs = platformEpochMillis(),
        )
        val imported = when (incoming) {
            is DocumentContent.Notebook -> incoming.copy(summary = summary, pages = incoming.pages.map { it.copy(documentId = id) })
            is DocumentContent.Pdf -> error("Shared PDF files need storage access; ask the owner to export and share the PDF.")
            is DocumentContent.Whiteboard -> incoming.copy(summary = summary)
            is DocumentContent.TextDocument -> incoming.copy(summary = summary, sourceFilePath = null)
            is DocumentContent.QuickNote -> incoming.copy(summary = summary)
        }
        repository.saveDocument(imported)
        open(id.value)
        return summary
    }

    suspend fun favorite(summary: DocumentSummary) {
        repository.setFavorite(summary.id, !summary.isFavorite)
        editors.remove(summary.id.value)?.close()
    }

    suspend fun trash(summary: DocumentSummary) {
        editors[summary.id.value]?.closeAndSave()
        repository.moveToTrash(summary.id, platformEpochMillis())
        editors.remove(summary.id.value)?.close()
    }

    suspend fun rename(summary: DocumentSummary, title: String) {
        val content = repository.getDocument(summary.id) ?: return
        repository.saveDocument(content.withSummary(content.summary.copy(title = title.trim().ifBlank { "Untitled" }, modifiedAtEpochMs = platformEpochMillis())))
        editors.remove(summary.id.value)?.close()
    }

    private suspend fun saveWorkspace() = sessionMutex.withLock {
        files.write(sessionFile, codec.encodeToString(workspace.value).encodeToByteArray())
    }

    /** Wait for finalized strokes and text to reach durable storage before navigation/exit. */
    suspend fun flush() {
        for (store in editors.values.toList()) {
            store.closeAndSave()
            val state = store.saveState.value
            check(state !is SaveState.Failed) { (state as SaveState.Failed).message }
        }
        sessionJob?.cancelAndJoin()
        saveWorkspace()
    }

    fun shutdown() = run {
        flush()
        editors.values.forEach { it.close() }
        scope.cancel()
    }
}

private val SUPPORTED_IMPORT_MIME_TYPES = listOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-word.document.macroEnabled.12",
    "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
    "application/msword",
    "application/vnd.ms-powerpoint",
)

fun DocumentContent.withSummary(value: DocumentSummary): DocumentContent = when (this) {
    is DocumentContent.Notebook -> copy(summary = value)
    is DocumentContent.Pdf -> copy(summary = value)
    is DocumentContent.Whiteboard -> copy(summary = value)
    is DocumentContent.TextDocument -> copy(summary = value)
    is DocumentContent.QuickNote -> copy(summary = value)
}

fun PaperSpec.dimensions(): Pair<Float, Float> {
    val dimensions = when (size) {
        PaperSize.A4 -> 595f to 842f
        PaperSize.A5 -> 420f to 595f
        PaperSize.LETTER -> 612f to 792f
        PaperSize.LEGAL -> 612f to 1008f
        PaperSize.SQUARE -> 720f to 720f
        PaperSize.PRESENTATION -> 960f to 540f
        PaperSize.CUSTOM -> (customWidth ?: 595f).coerceIn(100f, 4000f) to (customHeight ?: 842f).coerceIn(100f, 4000f)
    }
    return if (orientation == Orientation.LANDSCAPE && dimensions.first < dimensions.second) dimensions.second to dimensions.first else dimensions
}
