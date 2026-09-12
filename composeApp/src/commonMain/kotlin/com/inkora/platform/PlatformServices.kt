package com.inkora.platform

import kotlinx.coroutines.flow.Flow
import com.inkora.pdf.PdfEngine

/** A file known to a platform integration. The path may be a local path or a content URI. */
data class PlatformFile(
    val path: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

/** A small, platform-neutral filesystem surface used by import/export and caches. */
interface PlatformFileSystem {
    /** Application-owned directory suitable for managed documents and cache data. */
    val appDataDirectory: PlatformFile

    suspend fun exists(file: PlatformFile): Boolean
    suspend fun read(file: PlatformFile): ByteArray
    suspend fun write(file: PlatformFile, data: ByteArray)
    suspend fun list(directory: PlatformFile): List<PlatformFile>
    suspend fun delete(file: PlatformFile): Boolean
    suspend fun createDirectory(directory: PlatformFile): PlatformFile
    fun child(directory: PlatformFile, name: String): PlatformFile

    /** Copy an external import into application-owned storage without modifying the source. */
    suspend fun importToManagedStorage(source: PlatformFile, displayName: String? = null): PlatformFile
}

data class FilePickerRequest(
    val mimeTypes: List<String> = listOf("application/pdf"),
    val allowMultiple: Boolean = false,
)

interface FilePicker {
    suspend fun pickFile(request: FilePickerRequest = FilePickerRequest()): PlatformFile?
}

/** Reads Office Open XML files into a searchable Inkora text document. */
interface OfficeDocumentImporter {
    suspend fun extractText(source: PlatformFile): String
}

/** A signed, versioned artifact published in the Inkora GitHub Release. */
@kotlinx.serialization.Serializable
data class UpdateArtifact(
    val url: String,
    val sha256: String,
    val sizeBytes: Long? = null,
)

/** Release metadata consumed by the in-app updater. Keep this schema stable. */
@kotlinx.serialization.Serializable
data class UpdateManifest(
    val version: String,
    val versionCode: Int,
    val notes: String = "",
    val releaseUrl: String = "",
    val windowsPortable: UpdateArtifact? = null,
    val windowsMsi: UpdateArtifact? = null,
    val androidApk: UpdateArtifact? = null,
)

enum class UpdatePlatform { WINDOWS, ANDROID, IOS }

data class UpdateCheckResult(
    val available: Boolean,
    val manifest: UpdateManifest? = null,
    val message: String,
)

enum class UpdateInstallStatus { STARTED, NEEDS_USER_ACTION, FAILED }

data class UpdateInstallResult(
    val status: UpdateInstallStatus,
    val message: String,
)

/** Platform-specific download, verification, and installation surface. */
interface UpdateService {
    val platform: UpdatePlatform
    suspend fun checkForUpdates(): UpdateCheckResult
    suspend fun install(update: UpdateManifest): UpdateInstallResult
}

enum class ShareResult { SHARED, UNAVAILABLE, FAILED }

interface ShareService {
    suspend fun share(file: PlatformFile, title: String? = null): ShareResult
}

enum class PrintResult { PRINTED, UNAVAILABLE, FAILED }

interface PrintService {
    suspend fun print(file: PlatformFile, jobName: String? = null): PrintResult
}

/** A normalized stream of native pointer samples. Consumers should buffer points off Compose state. */
enum class StylusToolType { STYLUS, ERASER, FINGER, MOUSE, UNKNOWN }

enum class StylusAction { DOWN, MOVE, UP, CANCEL, HOVER }

data class StylusPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tiltX: Float? = null,
    val tiltY: Float? = null,
    val orientation: Float? = null,
    val timestampMillis: Long,
)

data class StylusEvent(
    val action: StylusAction,
    val point: StylusPoint,
    val toolType: StylusToolType,
    val pointerId: Int = 0,
    val buttons: Int = 0,
    val historicalPoints: List<StylusPoint> = emptyList(),
)

interface StylusInputProvider {
    val events: Flow<StylusEvent>

    /** Enable or disable finger-to-ink input. Stylus and eraser input remain available. */
    var drawWithFinger: Boolean

    fun attach()
    fun detach()
}

/** Platform factories are implemented in each target source set. */
expect fun platformFileSystem(): PlatformFileSystem
expect fun filePicker(): FilePicker
expect fun officeDocumentImporter(): OfficeDocumentImporter
expect fun updateService(): UpdateService
expect fun shareService(): ShareService
expect fun printService(): PrintService
expect fun stylusInputProvider(): StylusInputProvider
expect fun pdfEngine(): PdfEngine
