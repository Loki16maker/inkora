package com.inkora

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.inkora.ui.InkoraApp
import com.inkora.app.InkoraRuntime
import com.inkora.platform.createDocumentRepository
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing

/** JVM desktop entry point used by Compose Desktop run and packaging tasks. */
fun main() = application {
    val runtime = remember { InkoraRuntime(createDocumentRepository(), Dispatchers.Swing) }
    DisposableEffect(runtime) { onDispose { runtime.shutdown() } }
    Window(onCloseRequest = { runtime.run { runtime.flush(); exitApplication() } }, title = "Inkora",
        state = rememberWindowState(width = 1200.dp, height = 850.dp)) { InkoraApp(runtime) }
}
