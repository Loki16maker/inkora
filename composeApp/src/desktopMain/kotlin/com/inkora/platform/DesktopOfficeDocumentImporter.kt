package com.inkora.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Lightweight OOXML reader: keeps imports dependency-free and safe to ship on Windows. */
private class DesktopOfficeDocumentImporter : OfficeDocumentImporter {
    override suspend fun extractText(source: PlatformFile): String = withContext(Dispatchers.IO) {
        val extension = source.displayName.substringAfterLast('.', "").lowercase()
        require(File(source.path).isFile) { "Office source does not exist: ${source.path}" }
        when (extension) {
            "docx", "docm", "pptx", "pptm" -> ZipFile(source.path).use { zip ->
                if (extension.startsWith("doc")) readDocx(zip) else readPptx(zip)
            }
            "doc", "ppt" -> readLegacyOffice(File(source.path))
            else -> error("Inkora supports PDF, DOC/DOCX and PPT/PPTX files.")
        }.trim().ifBlank { error("No readable text was found in ${source.displayName}") }
    }

    private fun readDocx(zip: ZipFile): String {
        val entry = zip.getEntry("word/document.xml") ?: error("This DOCX has no document body")
        val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        val paragraphs = Regex("(?s)<w:p\\b[^>]*>(.*?)</w:p>").findAll(xml).mapNotNull { match ->
            Regex("(?s)<w:t\\b[^>]*>(.*?)</w:t>").findAll(match.groupValues[1])
                .map { decodeXml(it.groupValues[1]) }.joinToString("").trim().takeIf { it.isNotBlank() }
        }.toList()
        return paragraphs.joinToString("\n\n")
    }

    private fun readPptx(zip: ZipFile): String {
        val slides = zip.entries().asSequence()
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { Regex("slide(\\d+)\\.xml").find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            .toList()
        require(slides.isNotEmpty()) { "This PPTX has no slides" }
        return slides.mapIndexed { index, entry ->
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val text = Regex("(?s)<a:t\\b[^>]*>(.*?)</a:t>").findAll(xml)
                .map { decodeXml(it.groupValues[1]) }.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            "Slide ${index + 1}" + if (text.isBlank()) "" else "\n$text"
        }.joinToString("\n\n")
    }

    private fun decodeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** Best-effort extraction for legacy binary Office files without bundling a
     * large native converter. Unicode text is stored as UTF-16LE in most .doc
     * and .ppt records, while ASCII runs cover older PowerPoint files. */
    private fun readLegacyOffice(file: File): String {
        val bytes = file.readBytes()
        fun runs(chars: Sequence<Char>): List<String> = buildList {
            val current = StringBuilder()
            fun flush() { if (current.length >= 4) add(current.toString().replace(Regex("\\s+"), " ").trim()); current.clear() }
            chars.forEach { char ->
                if (char == '\n' || char == '\r' || char == '\t' || char in ' '..'~') current.append(char)
                else flush()
            }
            flush()
        }
        val utf16 = runs((0 until bytes.size - 1 step 2).asSequence().map { (((bytes[it + 1].toInt() and 0xff) shl 8) or (bytes[it].toInt() and 0xff)).toChar() })
        val ascii = runs(bytes.asSequence().map { (it.toInt() and 0xff).toChar() })
        return (utf16 + ascii).distinct().sortedByDescending { it.length }.take(120).joinToString("\n")
    }
}

actual fun officeDocumentImporter(): OfficeDocumentImporter = DesktopOfficeDocumentImporter()
