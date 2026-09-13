package com.inkora.platform

import com.inkora.pdf.PdfBoxPdfEngine
import com.inkora.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.print.PrinterException
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.io.FilenameFilter

private fun File.toPlatformFile(): PlatformFile = PlatformFile(
    path = canonicalPath,
    displayName = name.ifBlank { "Untitled" },
    mimeType = when (extension.lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> null
    },
    sizeBytes = if (isFile) length() else null,
)

/** Desktop application storage under the user's data directory. */
class DesktopPlatformFileSystem(
    private val root: Path = Path.of(System.getProperty("inkora.dataDir", Path.of(System.getProperty("user.home"), ".inkora").toString())),
) : PlatformFileSystem {
    override val appDataDirectory: PlatformFile
        get() = root.toFile().toPlatformFile()

    private fun local(file: PlatformFile): File = File(file.path)

    override suspend fun exists(file: PlatformFile): Boolean = withContext(Dispatchers.IO) {
        local(file).exists()
    }

    override suspend fun read(file: PlatformFile): ByteArray = withContext(Dispatchers.IO) {
        local(file).readBytes()
    }

    override suspend fun write(file: PlatformFile, data: ByteArray) = withContext(Dispatchers.IO) {
        val target = local(file).toPath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".inkora-", ".tmp")
        try {
            java.io.FileOutputStream(temporary.toFile()).use { stream -> stream.write(data); stream.fd.sync() }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
        Unit
    }

    override suspend fun list(directory: PlatformFile): List<PlatformFile> = withContext(Dispatchers.IO) {
        local(directory).listFiles()?.map(File::toPlatformFile).orEmpty()
    }

    override suspend fun delete(file: PlatformFile): Boolean = withContext(Dispatchers.IO) {
        local(file).delete()
    }

    override suspend fun createDirectory(directory: PlatformFile): PlatformFile = withContext(Dispatchers.IO) {
        local(directory).mkdirs()
        local(directory).toPlatformFile()
    }

    override fun child(directory: PlatformFile, name: String): PlatformFile =
        File(directory.path, name).toPlatformFile()

    override suspend fun importToManagedStorage(source: PlatformFile, displayName: String?): PlatformFile =
        withContext(Dispatchers.IO) {
            val documents = root.resolve("documents").also { Files.createDirectories(it) }
            val safeName = (displayName ?: source.displayName).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { "document-${UUID.randomUUID()}" }
            var destination = documents.resolve(safeName)
            if (Files.exists(destination)) {
                val stem = destination.fileName.toString().substringBeforeLast('.', destination.fileName.toString())
                val suffix = destination.fileName.toString().substringAfterLast('.', "")
                var index = 2
                do {
                    destination = documents.resolve(if (suffix.isEmpty()) "$stem ($index)" else "$stem ($index).$suffix")
                    index++
                } while (Files.exists(destination))
            }
            Files.copy(Path.of(source.path), destination, StandardCopyOption.COPY_ATTRIBUTES)
            destination.toFile().toPlatformFile().copy(mimeType = source.mimeType ?: destination.toFile().toPlatformFile().mimeType)
        }
}

class DesktopFilePicker : FilePicker {
    override suspend fun pickFile(request: FilePickerRequest): PlatformFile? = withContext(Dispatchers.Swing) {
        if (GraphicsEnvironment.isHeadless()) return@withContext null
        val extensions = request.mimeTypes
            .flatMap { mime -> desktopExtensionsByMime[mime].orEmpty() }
            .toSet()
        val filter = if (extensions.isEmpty()) null else FilenameFilter { _, name ->
            extensions.any { extension -> name.endsWith(".$extension", ignoreCase = true) }
        }

        // AWT delegates FileDialog to the native Windows open-file dialog. This
        // keeps navigation, OneDrive, Quick Access, and search consistent with
        // File Explorer instead of showing the old Java Swing chooser.
        val nativeDialog = FileDialog(null as java.awt.Frame?, "Open file", FileDialog.LOAD).apply {
            isMultipleMode = request.allowMultiple
            filenameFilter = filter
            directory = System.getProperty("user.home")
        }
        nativeDialog.isVisible = true
        val selected = if (request.allowMultiple) {
            nativeDialog.files.firstOrNull()
        } else {
            nativeDialog.file?.let { name -> File(nativeDialog.directory ?: System.getProperty("user.home"), name) }
        }

        // Some Windows configurations ignore AWT's filename filter. Keep the
        // filter enforced after selection so only supported files enter the
        // managed import pipeline.
        selected?.takeIf { file ->
            file.isFile && (extensions.isEmpty() || extensions.any { file.name.endsWith(".$it", ignoreCase = true) })
        }?.toPlatformFile()
    }
}

private val desktopExtensionsByMime = mapOf(
    "application/pdf" to setOf("pdf"),
    "application/msword" to setOf("doc"),
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to setOf("docx"),
    "application/vnd.ms-powerpoint" to setOf("ppt"),
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to setOf("pptx"),
    "application/x-inkora-backup" to setOf("inkorabackup"),
    "application/x-inkora-template" to setOf("inkoratemplate"),
    "image/png" to setOf("png"),
    "image/jpeg" to setOf("jpg", "jpeg"),
    "image/webp" to setOf("webp"),
)

class DesktopShareService : ShareService {
    override suspend fun share(file: PlatformFile, title: String?): ShareResult = withContext(Dispatchers.IO) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) return@withContext ShareResult.UNAVAILABLE
        runCatching {
            // Desktop does not expose a universal share sheet. Opening the containing folder gives
            // the user an immediate handoff to installed share applications.
            Desktop.getDesktop().open(File(file.path).parentFile ?: File(file.path))
        }.fold({ ShareResult.SHARED }, { ShareResult.FAILED })
    }
}

class DesktopPrintService : PrintService {
    override suspend fun print(file: PlatformFile, jobName: String?): PrintResult = withContext(Dispatchers.IO) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
            return@withContext PrintResult.UNAVAILABLE
        }
        try {
            Desktop.getDesktop().print(File(file.path))
            PrintResult.PRINTED
        } catch (_: PrinterException) {
            PrintResult.FAILED
        } catch (_: RuntimeException) {
            PrintResult.FAILED
        }
    }
}

class DesktopExternalBrowserService : ExternalBrowserService {
    override suspend fun open(url: String): Boolean = withContext(Dispatchers.IO) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) return@withContext false
        runCatching { Desktop.getDesktop().browse(URI(url)); true }.getOrDefault(false)
    }
}

class DesktopClipboardService : ClipboardService {
    override suspend fun copy(text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null); true }.getOrDefault(false)
    }
}

/** Compose/Skiko can feed native pointer samples to this adapter through [submit]. */
class DesktopStylusInputAdapter : StylusInputProvider {
    private val _events = MutableSharedFlow<StylusEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<StylusEvent> = _events.asSharedFlow()
    override var drawWithFinger: Boolean = false
    private var attached = false

    override fun attach() { attached = true }
    override fun detach() { attached = false }

    fun submit(event: StylusEvent): Boolean {
        if (!attached) return false
        if (event.toolType == StylusToolType.FINGER && !drawWithFinger) return false
        return _events.tryEmit(event)
    }
}

actual fun platformFileSystem(): PlatformFileSystem = DesktopPlatformFileSystem()
actual fun filePicker(): FilePicker = DesktopFilePicker()
actual fun shareService(): ShareService = DesktopShareService()
actual fun printService(): PrintService = DesktopPrintService()
actual fun externalBrowserService(): ExternalBrowserService = DesktopExternalBrowserService()
actual fun clipboardService(): ClipboardService = DesktopClipboardService()
actual fun stylusInputProvider(): StylusInputProvider = DesktopStylusInputAdapter()
actual fun pdfEngine(): PdfEngine = PdfBoxPdfEngine()
