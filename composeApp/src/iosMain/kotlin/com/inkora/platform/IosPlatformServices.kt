package com.inkora.platform

import com.inkora.pdf.PdfEngine
import com.inkora.pdf.UnsupportedPdfEngine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathDomainMask
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun iosRoot(): String =
    (NSFileManager.defaultManager.URLsForDirectory(NSSearchPathDirectory.NSDocumentDirectory, NSSearchPathDomainMask.NSUserDomainMask)
        .firstOrNull() as? NSURL)?.path ?: "/"

/** File metadata and directory operations are ready for iOS; byte streaming is supplied by the app host. */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformFileSystem : PlatformFileSystem {
    private val fileManager = NSFileManager.defaultManager
    override val appDataDirectory: PlatformFile = PlatformFile(iosRoot(), "Inkora")

    override suspend fun exists(file: PlatformFile): Boolean = fileManager.fileExistsAtPath(file.path)

    override suspend fun read(file: PlatformFile): ByteArray = error("Use the iOS document provider for byte streaming")

    override suspend fun write(file: PlatformFile, data: ByteArray) = error("Use the iOS document provider for byte streaming")

    override suspend fun list(directory: PlatformFile): List<PlatformFile> =
        (fileManager.contentsOfDirectoryAtPath(directory.path, null) as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { child(directory, it) }
            .orEmpty()

    override suspend fun delete(file: PlatformFile): Boolean = fileManager.removeItemAtPath(file.path, null)

    override suspend fun createDirectory(directory: PlatformFile): PlatformFile {
        fileManager.createDirectoryAtPath(directory.path, true, null, null)
        return directory
    }

    override fun child(directory: PlatformFile, name: String): PlatformFile =
        PlatformFile("${directory.path.trimEnd('/')}/$name", name)

    override suspend fun importToManagedStorage(source: PlatformFile, displayName: String?): PlatformFile =
        error("Use UIDocumentPickerViewController to import into iOS managed storage")
}

private class IosFilePicker : FilePicker {
    override suspend fun pickFile(request: FilePickerRequest): PlatformFile? = null
}

private class IosShareService : ShareService {
    override suspend fun share(file: PlatformFile, title: String?): ShareResult = ShareResult.UNAVAILABLE
}

private class IosPrintService : PrintService {
    override suspend fun print(file: PlatformFile, jobName: String?): PrintResult = PrintResult.UNAVAILABLE
}

private class IosStylusInputAdapter : StylusInputProvider {
    private val stream = MutableSharedFlow<StylusEvent>(extraBufferCapacity = 256)
    override val events: Flow<StylusEvent> = stream.asSharedFlow()
    override var drawWithFinger: Boolean = false
    override fun attach() = Unit
    override fun detach() = Unit
}

actual fun platformFileSystem(): PlatformFileSystem = IosPlatformFileSystem()
actual fun filePicker(): FilePicker = IosFilePicker()
actual fun officeDocumentImporter(): OfficeDocumentImporter = object : OfficeDocumentImporter {
    override suspend fun extractText(source: PlatformFile): String = error("Office import needs the iOS document provider integration")
}
actual fun updateService(): UpdateService = object : UpdateService {
    override val platform: UpdatePlatform = UpdatePlatform.IOS
    override suspend fun checkForUpdates(): UpdateCheckResult =
        UpdateCheckResult(false, message = "iOS updates are delivered through the App Store.")
    override suspend fun install(update: UpdateManifest): UpdateInstallResult =
        UpdateInstallResult(UpdateInstallStatus.FAILED, "iOS updates are delivered through the App Store.")
}
actual fun shareService(): ShareService = IosShareService()
actual fun printService(): PrintService = IosPrintService()
actual fun stylusInputProvider(): StylusInputProvider = IosStylusInputAdapter()
actual fun pdfEngine(): PdfEngine = UnsupportedPdfEngine("PDFKit integration is reserved for the iOS host target")
