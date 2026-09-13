package com.inkora.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.inkora.pdf.AndroidPdfRendererEngine
import com.inkora.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.coroutines.resume

/** Process-level handles used by the platform services. MainActivity should initialize this. */
object AndroidPlatformContext {
    private var contextRef: WeakReference<Context>? = null
    private var activityRef: WeakReference<Activity>? = null

    fun initialize(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        if (context is Activity) activityRef = WeakReference(context)
    }

    fun attachActivity(activity: Activity) {
        initialize(activity)
        activityRef = WeakReference(activity)
    }

    fun detachActivity(activity: Activity) {
        if (activityRef?.get() === activity) activityRef?.clear()
    }

    fun context(): Context = contextRef?.get() ?: error("Call AndroidPlatformContext.initialize(context) before using Inkora services")
    fun activity(): Activity = activityRef?.get() ?: error("An active Android Activity is required for this service")
}

private fun Context.fileFromUri(uri: Uri): PlatformFile {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "import-${UUID.randomUUID()}"
    var size: Long? = null
    if (uri.scheme == "content") {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
    } else {
        size = File(uri.path ?: "").takeIf(File::isFile)?.length()
    }
    return PlatformFile(uri.toString(), name, contentResolver.getType(uri), size)
}

class AndroidPlatformFileSystem(private val context: Context = AndroidPlatformContext.context()) : PlatformFileSystem {
    private val root: File = File(context.filesDir, "inkora")

    override val appDataDirectory: PlatformFile
        get() = root.toPlatformFile()

    private fun File.toPlatformFile(): PlatformFile = PlatformFile(
        path = absolutePath,
        displayName = name.ifBlank { "Inkora" },
        mimeType = when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> null
        },
        sizeBytes = if (isFile) length() else null,
    )

    private fun openInput(file: PlatformFile) = if (file.path.startsWith("content:")) {
        context.contentResolver.openInputStream(Uri.parse(file.path))
            ?: error("Unable to open ${file.path}")
    } else File(file.path).inputStream()

    override suspend fun exists(file: PlatformFile): Boolean = withContext(Dispatchers.IO) {
        if (file.path.startsWith("content:")) context.contentResolver.getType(Uri.parse(file.path)) != null
        else File(file.path).exists()
    }

    override suspend fun read(file: PlatformFile): ByteArray = withContext(Dispatchers.IO) {
        openInput(file).use { it.readBytes() }
    }

    override suspend fun write(file: PlatformFile, data: ByteArray) = withContext(Dispatchers.IO) {
        require(!file.path.startsWith("content:")) { "Cannot write to an external content URI" }
        val target = File(file.path).apply { parentFile?.mkdirs() }
        val atomic = android.util.AtomicFile(target)
        val stream = atomic.startWrite()
        try { stream.write(data); atomic.finishWrite(stream) }
        catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
    }

    override suspend fun list(directory: PlatformFile): List<PlatformFile> = withContext(Dispatchers.IO) {
        File(directory.path).listFiles()?.map { it.toPlatformFile() }.orEmpty()
    }

    override suspend fun delete(file: PlatformFile): Boolean = withContext(Dispatchers.IO) {
        if (file.path.startsWith("content:")) false else File(file.path).delete()
    }

    override suspend fun createDirectory(directory: PlatformFile): PlatformFile = withContext(Dispatchers.IO) {
        File(directory.path).mkdirs()
        File(directory.path).toPlatformFile()
    }

    override fun child(directory: PlatformFile, name: String): PlatformFile =
        File(directory.path, name).toPlatformFile()

    override suspend fun importToManagedStorage(source: PlatformFile, displayName: String?): PlatformFile =
        withContext(Dispatchers.IO) {
            val documents = File(root, "documents").also(File::mkdirs)
            val safeName = (displayName ?: source.displayName).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { "document-${UUID.randomUUID()}" }
            var destination = File(documents, safeName)
            if (destination.exists()) {
                val stem = destination.nameWithoutExtension
                val suffix = destination.extension
                var index = 2
                do {
                    destination = File(documents, if (suffix.isEmpty()) "$stem ($index)" else "$stem ($index).$suffix")
                    index++
                } while (destination.exists())
            }
            openInput(source).use { input -> FileOutputStream(destination).use { input.copyTo(it) } }
            destination.toPlatformFile().copy(mimeType = source.mimeType ?: destination.toPlatformFile().mimeType)
        }
}

object AndroidFilePickerRegistry {
    const val REQUEST_CODE = 42_101
    private var picker: AndroidFilePicker? = null

    internal fun register(value: AndroidFilePicker) { picker = value }

    fun dispatchResult(resultCode: Int, data: Intent?): Boolean = picker?.dispatchResult(resultCode, data) == true
}

class AndroidFilePicker(
    private val context: Context = AndroidPlatformContext.context(),
) : FilePicker {
    private var continuation: kotlinx.coroutines.CancellableContinuation<PlatformFile?>? = null

    override suspend fun pickFile(request: FilePickerRequest): PlatformFile? = suspendCancellableCoroutine { cont ->
        val activity = AndroidPlatformContext.activity()
        if (continuation != null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        continuation = cont
        AndroidFilePickerRegistry.register(this)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (request.mimeTypes.size > 1 && request.mimeTypes.firstOrNull() == "application/x-inkora-backup") "*/*" else request.mimeTypes.firstOrNull() ?: "application/pdf"
            if (request.mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, request.mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowMultiple)
        }
        activity.startActivityForResult(intent, AndroidFilePickerRegistry.REQUEST_CODE)
        cont.invokeOnCancellation { if (continuation === cont) continuation = null }
    }

    internal fun dispatchResult(resultCode: Int, data: Intent?): Boolean {
        val pending = continuation ?: return false
        continuation = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            pending.resume(null)
            return true
        }
        val uri = data.data ?: data.clipData?.getItemAt(0)?.uri
        pending.resume(uri?.let(context::fileFromUri))
        return true
    }
}

class AndroidShareService(private val context: Context = AndroidPlatformContext.context()) : ShareService {
    override suspend fun share(file: PlatformFile, title: String?): ShareResult {
        val uri = runCatching {
            if (file.path.startsWith("content:")) Uri.parse(file.path)
            else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
        }.recoverCatching {
            // Hosts embedding the service may omit FileProvider; keep a best-effort fallback for
            // old Android versions where file URIs remain legal.
            Uri.fromFile(File(file.path))
        }
            .getOrNull() ?: return ShareResult.FAILED
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            AndroidPlatformContext.activity().startActivity(Intent.createChooser(intent, title ?: "Share document"))
            ShareResult.SHARED
        }.getOrElse { ShareResult.FAILED }
    }
}

class AndroidPrintService(private val context: Context = AndroidPlatformContext.context()) : PrintService {
    override suspend fun print(file: PlatformFile, jobName: String?): PrintResult {
        val activity = runCatching { AndroidPlatformContext.activity() }.getOrNull() ?: return PrintResult.UNAVAILABLE
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager ?: return PrintResult.UNAVAILABLE
        return runCatching {
            manager.print(jobName ?: file.displayName, CopyFilePrintAdapter(context, file), null)
            PrintResult.PRINTED
        }.getOrElse { PrintResult.FAILED }
    }
}

class AndroidExternalBrowserService(private val context: Context = AndroidPlatformContext.context()) : ExternalBrowserService {
    override suspend fun open(url: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

class AndroidClipboardService(private val context: Context = AndroidPlatformContext.context()) : ClipboardService {
    override suspend fun copy(text: String): Boolean = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText("Inkora ChatGPT quiz prompt", text))
        true
    }.getOrDefault(false)
}

private class CopyFilePrintAdapter(private val context: Context, private val file: PlatformFile) : android.print.PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?,
        newAttributes: android.print.PrintAttributes,
        cancellationSignal: android.os.CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(
            android.print.PrintDocumentInfo.Builder(file.displayName)
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build(),
            true,
        )
    }

    override fun onWrite(
        pages: Array<android.print.PageRange>,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal,
        callback: WriteResultCallback,
    ) {
        runCatching {
            if (!cancellationSignal.isCanceled) {
                val input = if (file.path.startsWith("content:")) {
                    context.contentResolver.openInputStream(Uri.parse(file.path)) ?: error("Cannot read file")
                } else File(file.path).inputStream()
                input.use { source -> FileOutputStream(destination.fileDescriptor).use { source.copyTo(it) } }
            }
        }.fold(
            onSuccess = { callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) },
            onFailure = { callback.onWriteFailed(it.message) },
        )
    }
}

actual fun platformFileSystem(): PlatformFileSystem = AndroidPlatformFileSystem()
actual fun filePicker(): FilePicker = AndroidFilePicker()
actual fun shareService(): ShareService = AndroidShareService()
actual fun printService(): PrintService = AndroidPrintService()
actual fun externalBrowserService(): ExternalBrowserService = AndroidExternalBrowserService()
actual fun clipboardService(): ClipboardService = AndroidClipboardService()
actual fun stylusInputProvider(): StylusInputProvider = AndroidStylusInputAdapter()
actual fun pdfEngine(): PdfEngine = AndroidPdfRendererEngine(AndroidPlatformContext.context())
