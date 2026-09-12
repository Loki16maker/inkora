package com.inkora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Simple Markdown editor with a preview, useful formatting shortcuts, and a live word count. */
@Composable
fun RichTextEditor(markdown: String, onMarkdownChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var preview by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { onMarkdownChange(markdown + "\n**bold text**") }) { Text("Bold") }
            TextButton(onClick = { onMarkdownChange(markdown + "\n_italic text_") }) { Text("Italic") }
            TextButton(onClick = { onMarkdownChange(markdown + "\n- list item") }) { Text("List") }
            TextButton(onClick = { onMarkdownChange(markdown + "\n## Heading") }) { Text("Heading") }
            FilterChip(selected = preview, onClick = { preview = !preview }, label = { Text(if (preview) "Edit" else "Preview") })
            Text("${markdown.trim().split(Regex("\\s+")).count { it.isNotBlank() }} words", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (preview) {
            MarkdownPreview(markdown, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp))
        } else {
            OutlinedTextField(
                value = markdown,
                onValueChange = onMarkdownChange,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                placeholder = { Text("Write your notes here… Use the toolbar for Markdown formatting.") },
            )
        }
    }
}

@Composable
private fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (markdown.isBlank()) Text("Nothing to preview yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        markdown.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text("• " + trimmed.drop(2), style = MaterialTheme.typography.bodyLarge)
                trimmed.isBlank() -> Text(" ", Modifier.padding(2.dp))
                else -> Text(trimmed.replace("**", "").replace("_", ""), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
