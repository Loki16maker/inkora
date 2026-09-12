package com.inkora.filesystem

import okio.BufferedSink
import okio.BufferedSource
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.buffer

/** File kinds understood by managed imports. */
public enum class ManagedFileKind(public val extension: String) {
    PDF("pdf"),
    PNG("png"),
    JPEG("jpg"),
    OTHER("bin"),
}

public data class ManagedFile(
    val id: String,
    val path: Path,
    val kind: ManagedFileKind,
    val byteSize: Long,
    val sourceName: String?,
)

/** Small platform boundary. Platform code can provide a picker or share target. */
public interface PlatformFileSystem {
    public suspend fun importFile(source: Path, id: String, sourceName: String? = null): ManagedFile
    public suspend fun write(id: String, extension: String, source: BufferedSource): ManagedFile
    public suspend fun open(path: Path): BufferedSource
    public suspend fun create(path: Path): BufferedSink
    public suspend fun metadata(path: Path): FileMetadata
    public suspend fun exists(path: Path): Boolean
    public suspend fun delete(path: Path)
}

/**
 * Okio-backed managed storage. The root is app-owned and must be supplied by
 * Android/Desktop/iOS bootstrap code; this class does not touch user paths.
 */
public class OkioPlatformFileSystem(
    private val root: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : PlatformFileSystem {
    init {
        fileSystem.createDirectories(root)
    }

    override suspend fun importFile(source: Path, id: String, sourceName: String?): ManagedFile {
        require(fileSystem.exists(source)) { "Source file does not exist: $source" }
        val kind = detectKind(source)
        val destination = managedPath(id, kind.extension)
        fileSystem.delete(destination, mustExist = false)
        fileSystem.copy(source, destination)
        return managedFile(id, destination, kind, sourceName)
    }

    override suspend fun write(id: String, extension: String, source: BufferedSource): ManagedFile {
        val normalizedExtension = extension.trim().trimStart('.').lowercase().ifBlank { ManagedFileKind.OTHER.extension }
        val destination = managedPath(id, normalizedExtension)
        fileSystem.delete(destination, mustExist = false)
        fileSystem.sink(destination).buffer().use { sink -> source.use { input -> input.readAll(sink) } }
        return managedFile(id, destination, kindForExtension(normalizedExtension), null)
    }

    override suspend fun open(path: Path): BufferedSource = fileSystem.source(path).buffer()

    override suspend fun create(path: Path): BufferedSink {
        path.parent?.let(fileSystem::createDirectories)
        return fileSystem.sink(path).buffer()
    }

    override suspend fun metadata(path: Path): FileMetadata = fileSystem.metadata(path)

    override suspend fun exists(path: Path): Boolean = fileSystem.exists(path)

    override suspend fun delete(path: Path) {
        fileSystem.delete(path, mustExist = false)
    }

    public fun managedPath(id: String, extension: String): Path {
        val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        require(safeId.isNotEmpty()) { "Managed file id cannot be blank" }
        val safeExtension = extension.trim().trimStart('.').lowercase().filter { it.isLetterOrDigit() }
        require(safeExtension.isNotEmpty()) { "Managed file extension cannot be blank" }
        return root.resolve("$safeId.$safeExtension")
    }

    private fun managedFile(id: String, destination: Path, kind: ManagedFileKind, sourceName: String?): ManagedFile =
        ManagedFile(id, destination, kind, fileSystem.metadata(destination).size ?: 0L, sourceName)

    private fun detectKind(path: Path): ManagedFileKind {
        val extension = path.name.substringAfterLast('.', "").lowercase()
        val kindByExtension = kindForExtension(extension)
        val signature = fileSystem.source(path).buffer().use { source ->
            val peeked = source.peek()
            val size = minOf(8L, fileSystem.metadata(path).size ?: 0L)
            peeked.request(size)
            peeked.readByteArray(size).map { it.toInt() and 0xff }
        }
        val valid = when (kindByExtension) {
            ManagedFileKind.PDF -> signature.take(4) == listOf(0x25, 0x50, 0x44, 0x46)
            ManagedFileKind.PNG -> signature.take(8) == listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ManagedFileKind.JPEG -> signature.take(2) == listOf(0xFF, 0xD8)
            ManagedFileKind.OTHER -> true
        }
        require(valid) { "File signature does not match extension: $path" }
        return kindByExtension
    }

    private fun kindForExtension(extension: String): ManagedFileKind = when (extension.lowercase()) {
        "pdf" -> ManagedFileKind.PDF
        "png" -> ManagedFileKind.PNG
        "jpg", "jpeg" -> ManagedFileKind.JPEG
        else -> ManagedFileKind.OTHER
    }

}
