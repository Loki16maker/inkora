package com.inkora.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Android counterpart for the dependency-free OOXML text importer. */
private class AndroidOfficeDocumentImporter : OfficeDocumentImporter {
    override suspend fun extractText(source: PlatformFile): String = withContext(Dispatchers.IO) {
        val extension = source.displayName.substringAfterLast('.', "").lowercase()
        require(extension == "docx" || extension == "pptx") {
            "${source.displayName} uses the legacy Office format. Save it as .docx or .pptx and import again."
        }
        require(File(source.path).isFile) { "Office source does not exist: ${source.path}" }
        ZipFile(source.path).use { zip -> if (extension == "docx") readDocx(zip) else readPptx(zip) }
            .trim().ifBlank { error("No readable text was found in ${source.displayName}") }
    }

    private fun readDocx(zip: ZipFile): String {
        val entry = zip.getEntry("word/document.xml") ?: error("This DOCX has no document body")
        val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        return Regex("(?s)<w:p\\b[^>]*>(.*?)</w:p>").findAll(xml).mapNotNull { match ->
            Regex("(?s)<w:t\\b[^>]*>(.*?)</w:t>").findAll(match.groupValues[1])
                .map { decodeXml(it.groupValues[1]) }.joinToString("").trim().takeIf { it.isNotBlank() }
        }.toList().joinToString("\n\n")
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
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}

actual fun officeDocumentImporter(): OfficeDocumentImporter = AndroidOfficeDocumentImporter()
