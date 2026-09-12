package com.inkora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.inkora.app.InkoraRuntime

@Composable
fun TemplatesScreen(modifier: Modifier = Modifier, runtime: InkoraRuntime? = null) {
    var favoritesOnly by remember { mutableStateOf(false) }
    val templates = PageTemplate.entries.filter { !favoritesOnly || it.ordinal % 3 == 0 }
    Column(modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Templates", style = MaterialTheme.typography.headlineSmall); Text("Start with a page that fits the way you study.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(enabled = runtime != null, onClick = {
                runtime?.run {
                    runtime.importTemplate()?.let { imported -> runtime.notice.value = "Imported template · ${imported.name}. Open a notebook to apply it." }
                }
            }, shape = RoundedCornerShape(9.dp)) { Text("Import template") }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { InkoraPill("All templates", selected = !favoritesOnly, onClick = { favoritesOnly = false }); InkoraPill("Favorites", selected = favoritesOnly, onClick = { favoritesOnly = true }); Spacer(Modifier.weight(1f)); Text("${templates.size} built in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 176.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(templates) { template -> TemplatePreviewCard(template) }
        }
    }
}

@Composable
private fun TemplatePreviewCard(template: PageTemplate) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, shadowElevation = 1.dp) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(8.dp)).background(InkoraColors.paper), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(18.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(template.glyph, style = MaterialTheme.typography.titleLarge, color = InkoraColors.primaryBlue)
                    when (template) {
                        PageTemplate.Blank -> Unit
                        PageTemplate.Dotted -> repeat(5) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(6) { Text("·", color = Color(0xFFABB6C0), style = MaterialTheme.typography.bodySmall) } } }
                        PageTemplate.Grid, PageTemplate.SmallGrid -> repeat(4) { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFCFD7DC))) }
                        else -> repeat(4) { Box(Modifier.fillMaxWidth(if (it == 3) .55f else .85f).height(2.dp).background(Color(0xFFBBC4CB), RoundedCornerShape(2.dp))) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text(template.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Text("♡", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Built in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var restoreWorkspace by remember { mutableStateOf(true) }
    var darkPaper by remember { mutableStateOf(false) }
    var autosave by remember { mutableStateOf(true) }
    Column(modifier.padding(horizontal = 28.dp, vertical = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Make Inkora feel like your workspace.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(26.dp))
        SettingsGroup("Workspace") {
            SettingsToggle("Restore last workspace", "Reopen your tabs after restarting Inkora", restoreWorkspace) { restoreWorkspace = it }
            SettingsToggle("Autosave", "Save changes locally as you write", autosave) { autosave = it }
            SettingsAction("Toolbar customization", "Choose which tools stay visible")
        }
        SettingsGroup("Appearance") {
            SettingsAction("Theme", "System default")
            SettingsToggle("Dark paper option", "Keep dark paper available in notebook creation", darkPaper) { darkPaper = it }
            SettingsAction("Paper defaults", "A4 · Warm white · Ruled")
        }
        SettingsGroup("Storage") {
            SettingsAction("Local backup", "Export a portable .inkorabackup file")
            SettingsAction("Trash", "Items are kept for 30 days")
            SettingsAction("Open source licenses", "Review the libraries used by Inkora")
        }
        SettingsGroup("About") {
            InspectorRow("Application", InkoraBrand.productName)
            InspectorRow("Version", InkoraBrand.versionName)
            InspectorRow("Storage mode", "Local only")
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) { Column(Modifier.padding(horizontal = 16.dp)) { content() } }
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = checked, onCheckedChange = onChecked) }
}

@Composable
private fun SettingsAction(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().clickable {}.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Glyph("›", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
