package com.inkora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.inkora.app.InkoraRuntime

@Composable
fun CloudAccountDialog(runtime: InkoraRuntime, onDismiss: () -> Unit) {
    val session by runtime.cloud.session.collectAsState()
    val busy by runtime.cloud.busy.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (session == null) "Inkora cloud" else "Inkora account") },
        text = {
            if (!runtime.cloud.configured) {
                Text("Cloud sync is not configured in this build yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (session != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(session?.user?.email ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                    Text("Your notes and drawings can sync across Inkora devices.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { runtime.run { val result = runtime.cloudSync(); runtime.notice.value = "Cloud sync complete · ${result.uploaded} uploaded, ${result.downloaded} downloaded" } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        else InkoraIcon(InkoraSymbol.DOWNLOAD)
                        Spacer(Modifier.width(8.dp))
                        Text("Sync now")
                    }
                    TextButton(onClick = { runtime.run { runtime.cloudSignOut(); runtime.notice.value = "Signed out of Inkora cloud" } }, enabled = !busy) { Text("Sign out") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (createAccount) "Create an account to sync Inkora across devices." else "Sign in to sync your Inkora library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { createAccount = false }, enabled = !busy) { Text("Sign in") }
                        TextButton(onClick = { createAccount = true }, enabled = !busy) { Text("Create account") }
                    }
                    Button(
                        onClick = { runtime.run { val result = if (createAccount) runtime.cloudSignUp(email, password) else runtime.cloudSignIn(email, password); runtime.notice.value = result.message } },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        else Text(if (createAccount) "Create account" else "Sign in")
                    }
                    Text("or", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { runtime.run { val result = runtime.cloudSignInWithGoogle(); runtime.notice.value = result.message } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue with Google")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") } },
    )
}
