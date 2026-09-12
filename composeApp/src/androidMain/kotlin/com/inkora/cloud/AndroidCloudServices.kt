package com.inkora.cloud

import com.inkora.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual fun supabaseConfig(): SupabaseConfig = SupabaseConfig(
    url = BuildConfig.SUPABASE_URL,
    publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
)

actual fun cloudHttpClient(): CloudHttpClient = AndroidCloudHttpClient()

private class AndroidCloudHttpClient : CloudHttpClient {
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
