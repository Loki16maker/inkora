package com.inkora.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.lifecycle.lifecycleScope
import com.inkora.app.InkoraRuntime
import com.inkora.platform.createDocumentRepository
import kotlinx.coroutines.launch
import com.inkora.platform.AndroidFilePickerRegistry
import com.inkora.platform.AndroidPlatformContext
import com.inkora.platform.AndroidStylusInputAdapter
import com.inkora.cloud.AndroidOAuthBridge
import com.inkora.ui.InkoraApp

/** Android host boundary for platform services and the common Inkora UI. */
class MainActivity : ComponentActivity() {
    val stylusInputAdapter = AndroidStylusInputAdapter()
    private lateinit var runtime: InkoraRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.attachActivity(this)
        runtime = InkoraRuntime(createDocumentRepository())
        handleOAuthIntent(intent)
        setContent {
            BackHandler { runtime.run { runtime.library() } }
            InkoraApp(runtime)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.host == "auth") {
            AndroidOAuthBridge.dispatch(intent)
        } else if (data.host == "share") {
            val token = data.pathSegments.lastOrNull().orEmpty()
            if (token.isNotBlank()) runtime.run {
                val imported = runtime.importCloudShareLink(token)
                runtime.notice.value = "Shared document imported: ${imported.title}"
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = super.dispatchTouchEvent(event)

    override fun onStop() {
        runtime.run { runtime.flush() }
        super.onStop()
    }

    @Deprecated("Activity result bridge retained for Android API compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AndroidFilePickerRegistry.REQUEST_CODE) {
            AndroidFilePickerRegistry.dispatchResult(resultCode, data)
        }
    }

    override fun onDestroy() {
        stylusInputAdapter.detach()
        runtime.shutdown()
        AndroidPlatformContext.detachActivity(this)
        super.onDestroy()
    }
}
