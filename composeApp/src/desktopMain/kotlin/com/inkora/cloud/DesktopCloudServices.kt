package com.inkora.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

actual fun supabaseConfig(): SupabaseConfig {
    val properties = Properties()
    runCatching {
        val loader = object {}.javaClass.classLoader
        (loader?.getResourceAsStream("inkora-supabase.properties")
            ?: object {}.javaClass.getResourceAsStream("/inkora-supabase.properties"))
            ?.use(properties::load)
    }
    return SupabaseConfig(
        url = System.getenv("INKORA_SUPABASE_URL").orEmpty()
            .ifBlank { properties.getProperty("url").orEmpty() }
            .ifBlank { InkoraCloudDefaults.url },
        publishableKey = System.getenv("INKORA_SUPABASE_PUBLISHABLE_KEY").orEmpty()
            .ifBlank { properties.getProperty("publishableKey").orEmpty() }
            .ifBlank { InkoraCloudDefaults.publishableKey },
    )
}

actual fun cloudHttpClient(): CloudHttpClient = DesktopCloudHttpClient()

actual suspend fun launchOAuth(authorizeUrl: String, redirectUri: String): OAuthCallback = withContext(Dispatchers.IO) {
    check(Desktop.isDesktopSupported()) { "No desktop browser is available for Google sign-in." }
    val callback = CompletableDeferred<OAuthCallback>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 54321), 0)
    server.createContext("/callback") { exchange ->
        val values = exchange.requestURI.rawQuery.orEmpty().split('&').asSequence()
            .mapNotNull { item -> item.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to URLDecoder.decode(value, Charsets.UTF_8.name()) }
        val result = OAuthCallback(
            code = values["code"],
            error = values["error_description"] ?: values["error"],
        )
        val body = "<html><head><title>Inkora</title></head><body><h2>Google sign-in complete</h2><p>You can return to Inkora.</p><script>window.close()</script></body></html>"
        exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
        callback.complete(result)
    }
    server.start()
    try {
        Desktop.getDesktop().browse(URI(authorizeUrl))
        withTimeout(5 * 60 * 1000L) { callback.await() }
    } finally {
        server.stop(0)
    }
}

actual fun oauthRedirectUri(): String = "http://127.0.0.1:54321/callback"

actual fun oauthCodeChallenge(verifier: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

private class DesktopCloudHttpClient : CloudHttpClient {
    override suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): CloudHttpResponse = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            CloudHttpResponse(status, bytes.decodeToString(), bytes)
        } finally {
            connection.disconnect()
        }
    }
}
