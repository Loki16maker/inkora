package com.inkora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inkora.platform.UpdateCheckResult
import com.inkora.platform.UpdateManifest

@Composable
fun UpdateDialog(
    result: UpdateCheckResult,
    busy: Boolean,
    onDismiss: () -> Unit,
    onInstall: (UpdateManifest) -> Unit,
) {
    val manifest = result.manifest
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.available) "Update available" else "Inkora updates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(result.message)
                manifest?.notes?.takeIf(String::isNotBlank)?.let { notes ->
                    Text("Release notes", style = MaterialTheme.typography.labelLarge)
                    Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (result.available && manifest != null) {
                Button(enabled = !busy, onClick = { onInstall(manifest) }) { Text("Download and install") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = if (result.available) ({ TextButton(enabled = !busy, onClick = onDismiss) { Text("Later") } }) else null,
    )
}
