package com.inkora.cloud

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.DocumentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Public client configuration supplied at build time. The publishable key is
 * safe to ship in a client; access is enforced by Supabase RLS policies. */
data class SupabaseConfig(
    val url: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean get() = url.startsWith("https://") && publishableKey.isNotBlank()
}

/** Safe client-side defaults for the published Inkora build. */
internal object InkoraCloudDefaults {
    const val url = "https://gfhjwencfqhxfsxwscyv.supabase.co"
    const val publishableKey = "sb_publishable_CbdBFtqexn8ZoRhb5e4_pQ_cMWNHdek"
}

expect fun supabaseConfig(): SupabaseConfig

data class CloudHttpResponse(val status: Int, val body: String)

interface CloudHttpClient {
    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): CloudHttpResponse
}

expect fun cloudHttpClient(): CloudHttpClient

@Serializable
data class CloudSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresInSeconds: Int? = null,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: CloudUser? = null,
)

@Serializable
data class CloudUser(
    val id: String,
    val email: String? = null,
)

data class CloudAuthResult(
    val session: CloudSession? = null,
    val user: CloudUser? = null,
    val message: String,
)

/** Small REST client for Supabase Auth and PostgREST. It intentionally uses
 * only the public client key; all authorization is enforced by RLS. */
class SupabaseClient(
    private val config: SupabaseConfig = supabaseConfig(),
    private val http: CloudHttpClient = cloudHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private fun requireConfigured() {
        check(config.isConfigured) { "Cloud sync is not configured in this build." }
    }

    private fun endpoint(path: String): String = "${config.url.trimEnd('/')}$path"

    private fun headers(
        accessToken: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> = buildMap {
        put("apikey", config.publishableKey)
        put("Content-Type", "application/json")
        accessToken?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
        putAll(extra)
    }

    private suspend fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: JsonObject? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): CloudHttpResponse {
        requireConfigured()
        return http.execute(
            method = method,
            url = endpoint(path),
            headers = headers(accessToken, extraHeaders),
            body = body?.let { json.encodeToString(JsonObject.serializer(), it).encodeToByteArray() },
        )
    }

    suspend fun signIn(email: String, password: String): CloudAuthResult {
        require(email.contains('@')) { "Enter a valid email address." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        val response = request("POST", "/auth/v1/token?grant_type=password", body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        })
        return parseAuthResponse(response, "Signed in")
    }

    suspend fun signUp(email: String, password: String): CloudAuthResult {
        require(email.contains('@')) { "Enter a valid email address." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        val response = request("POST", "/auth/v1/signup", body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        })
        return parseAuthResponse(response, "Account created. Check your email if confirmation is enabled.")
    }

    suspend fun refresh(refreshToken: String): CloudAuthResult {
        val response = request("POST", "/auth/v1/token?grant_type=refresh_token", body = buildJsonObject {
            put("refresh_token", refreshToken)
        })
        return parseAuthResponse(response, "Session refreshed")
    }

    /** Builds the Supabase Auth Google authorization URL using PKCE. */
    fun googleAuthorizeUrl(redirectUri: String, codeChallenge: String): String =
        endpoint("/auth/v1/authorize") +
            "?provider=google" +
            "&redirect_to=${urlEncode(redirectUri)}" +
            "&flow_type=pkce" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256"

    /** Exchanges the one-time authorization code returned by Supabase Auth. */
    suspend fun exchangePkce(code: String, codeVerifier: String): CloudAuthResult {
        require(code.isNotBlank()) { "Google sign-in did not return an authorization code." }
        require(codeVerifier.isNotBlank()) { "Google sign-in expired. Please try again." }
        val response = request("POST", "/auth/v1/token?grant_type=pkce", body = buildJsonObject {
            put("auth_code", code)
            put("code_verifier", codeVerifier)
        })
        return parseAuthResponse(response, "Signed in with Google")
    }

    suspend fun signOut(accessToken: String) {
        val response = request("POST", "/auth/v1/logout", accessToken)
        if (response.status !in 200..299) throw CloudException(response.status, parseError(response.body))
    }

    suspend fun upsertDocument(session: CloudSession, document: DocumentContent) {
        val summary = document.summary
        val payload = json.encodeToJsonElement(cloudSafeDocument(document))
        val body = buildJsonObject {
            put("id", summary.id.value)
            put("owner_id", session.user?.id ?: error("Cloud session has no user id"))
            put("title", summary.title)
            put("kind", summary.type.name)
            put("payload", payload)
            put("source_file_path", summary.sourceFileName)
            put("version", 1)
        }
        val response = request(
            method = "POST",
            path = "/rest/v1/documents?on_conflict=id",
            accessToken = session.accessToken,
            body = body,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        if (response.status !in 200..299) throw CloudException(response.status, parseError(response.body))
    }

    suspend fun listDocuments(session: CloudSession): List<CloudDocumentRow> {
        val response = request(
            method = "GET",
            path = "/rest/v1/documents?select=id,owner_id,title,kind,payload,version,deleted_at&order=updated_at.desc",
            accessToken = session.accessToken,
        )
        if (response.status !in 200..299) throw CloudException(response.status, parseError(response.body))
        return json.decodeFromString(response.body)
    }

    private fun parseAuthResponse(response: CloudHttpResponse, success: String): CloudAuthResult {
        if (response.status !in 200..299) throw CloudException(response.status, parseError(response.body))
        val root = json.parseToJsonElement(response.body).jsonObject
        val session = root["access_token"]?.jsonPrimitive?.contentOrNull?.let {
            json.decodeFromJsonElement<CloudSession>(root)
        }
        val user = root["user"]?.let { json.decodeFromJsonElement<CloudUser>(it) }
        return CloudAuthResult(session, user, success)
    }

    private fun parseError(body: String): String = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["msg"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: root["error_description"]?.jsonPrimitive?.contentOrNull
            ?: root["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Cloud request failed"
}

private fun urlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xff
        val safe = (code in 0x41..0x5a) || (code in 0x61..0x7a) || (code in 0x30..0x39) || code in intArrayOf(0x2d, 0x2e, 0x5f, 0x7e)
        if (safe) append(code.toChar()) else append('%').append(code.toString(16).uppercase().padStart(2, '0'))
    }
}

@Serializable
data class CloudDocumentRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    val kind: String,
    val payload: kotlinx.serialization.json.JsonElement,
    val version: Long = 1,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

class CloudException(val status: Int, override val message: String) : Exception(message)

/** Avoid leaking machine-specific paths into cloud snapshots. The original
 * files remain local until storage upload is added in the next sync phase. */
private fun cloudSafeDocument(document: DocumentContent): DocumentContent = when (document) {
    is DocumentContent.Pdf -> document.copy(managedFilePath = "")
    is DocumentContent.TextDocument -> document.copy(sourceFilePath = null)
    else -> document
}
