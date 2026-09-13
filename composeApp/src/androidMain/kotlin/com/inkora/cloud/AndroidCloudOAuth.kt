package com.inkora.cloud

import android.content.Intent
import android.net.Uri
import com.inkora.platform.AndroidPlatformContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private var oauthContinuation: CancellableContinuation<OAuthCallback>? = null

/** Android deep-link bridge for the OAuth callback registered in the manifest. */
object AndroidOAuthBridge {
    internal fun dispatch(intent: Intent): Boolean {
        val pending = oauthContinuation ?: return false
        val uri = intent.data ?: return false
        oauthContinuation = null
        pending.resume(
            OAuthCallback(
                code = uri.parameter("code"),
                accessToken = uri.parameter("access_token"),
                refreshToken = uri.parameter("refresh_token"),
                error = uri.parameter("error_description") ?: uri.parameter("error"),
            ),
        )
        return true
    }

    private fun Uri.parameter(name: String): String? = getQueryParameter(name)
        ?: fragment?.split('&')?.asSequence()
            ?.mapNotNull { item -> item.split('=', limit = 2).takeIf { it.size == 2 } }
            ?.firstOrNull { it[0] == name }
            ?.getOrNull(1)
}

actual suspend fun launchOAuth(authorizeUrl: String, redirectUri: String): OAuthCallback =
    suspendCancellableCoroutine { cont ->
        if (oauthContinuation != null) {
            cont.resume(OAuthCallback(error = "Another Google sign-in is already in progress."))
            return@suspendCancellableCoroutine
        }
        oauthContinuation = cont
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { AndroidPlatformContext.activity().startActivity(intent) }
            .onFailure {
                if (oauthContinuation === cont) oauthContinuation = null
                cont.resume(OAuthCallback(error = it.message ?: "Unable to open the browser."))
            }
        cont.invokeOnCancellation { if (oauthContinuation === cont) oauthContinuation = null }
    }

actual fun oauthRedirectUri(): String = "inkora://auth/callback"

actual fun oauthCodeChallenge(verifier: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
    return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
}
