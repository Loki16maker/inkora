package com.inkora.cloud

actual fun supabaseConfig(): SupabaseConfig = SupabaseConfig("", "")

actual fun cloudHttpClient(): CloudHttpClient = object : CloudHttpClient {
    override suspend fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray?): CloudHttpResponse =
        CloudHttpResponse(503, "Cloud sync is not available on iOS yet.")
}
