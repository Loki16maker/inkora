package com.inkora.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

actual fun supabaseConfig(): SupabaseConfig {
    val properties = Properties()
    runCatching {
        object {}.javaClass.getResourceAsStream("/inkora-supabase.properties")?.use(properties::load)
    }
    return SupabaseConfig(
        url = System.getenv("INKORA_SUPABASE_URL").orEmpty().ifBlank { properties.getProperty("url").orEmpty() },
        publishableKey = System.getenv("INKORA_SUPABASE_PUBLISHABLE_KEY").orEmpty()
            .ifBlank { properties.getProperty("publishableKey").orEmpty() },
    )
}

actual fun cloudHttpClient(): CloudHttpClient = DesktopCloudHttpClient()

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
            CloudHttpResponse(status, stream?.use { it.readBytes().decodeToString() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}
