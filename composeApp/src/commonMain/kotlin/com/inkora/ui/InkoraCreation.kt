package com.inkora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun NotebookCreationDialog(onDismiss: () -> Unit, onCreate: (NotebookDraft) -> Unit) {
    var draft by remember { mutableStateOf(NotebookDraft()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("New notebook", style = MaterialTheme.typography.titleLarge); Text("Set up your page before you start writing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onCreate(draft) }, shape = RoundedCornerShape(9.dp), contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Create notebook") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxWidth()) {
                    item {
                        OutlinedTextField(value = draft.title, onValueChange = { draft = draft.copy(title = it) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Notebook title") }, placeholder = { Text("e.g. Biology 101") })
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Cover", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Text("${draft.coverIndex + 1} of 6", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(Color(0xFF275D8C), Color(0xFF6F8F72), Color(0xFFAA6B42), Color(0xFF866DAA), Color(0xFFB66A68), Color(0xFF3E444C)).forEachIndexed { index, color -> CoverChoice(color, selected = draft.coverIndex == index, onClick = { draft = draft.copy(coverIndex = index) }) } }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Page template", style = MaterialTheme.typography.titleMedium)
                            LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), modifier = Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = true) {
                                items(PageTemplate.entries) { template ->
                                    TemplateChoice(template, selected = draft.template == template, onClick = { draft = draft.copy(template = template) })
                                }
                            }
                        }
                    }
                    item { SettingChoices("Paper size", PaperSize.entries.map { it.label }, draft.paperSize.label) { value -> draft = draft.copy(paperSize = PaperSize.entries.first { it.label == value }) } }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Paper color", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PaperColor.entries.forEach { color -> PaperColorChoice(color, selected = color == draft.paperColor, onClick = { draft = draft.copy(paperColor = color) }) } }
                        }
                    }
                    item { SettingChoices("Orientation", PageOrientation.entries.map { it.label }, draft.orientation.label) { value -> draft = draft.copy(orientation = PageOrientation.entries.first { it.label == value }) } }
                }
            }
        }
    }
}

@Composable
private fun CoverChoice(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(width = 54.dp, height = 68.dp).clip(RoundedCornerShape(8.dp)).background(color).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .1f), RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(25.dp, 2.dp).background(Color.White.copy(alpha = .75f), RoundedCornerShape(2.dp))); Box(Modifier.size(17.dp, 2.dp).background(Color.White.copy(alpha = .45f), RoundedCornerShape(2.dp))) }
    }
}

@Composable
private fun TemplateChoice(template: PageTemplate, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f)) else null) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Glyph(template.glyph, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(7.dp)); Text(template.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
    }
}

@Composable
private fun PaperColorChoice(color: PaperColor, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.width(60.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color.color).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)))
        Text(color.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun SettingChoices(title: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> InkoraPill(option, selected = option == selected, onClick = { onSelected(option) }) } }
    }
}
