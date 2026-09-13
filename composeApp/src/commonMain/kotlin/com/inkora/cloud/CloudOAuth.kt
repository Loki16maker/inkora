package com.inkora.cloud

/** Result delivered by a platform browser callback after OAuth completes. */
data class OAuthCallback(
    val code: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val error: String? = null,
)

/** Starts the system browser and waits for the platform callback. */
expect suspend fun launchOAuth(authorizeUrl: String, redirectUri: String): OAuthCallback

/** URI registered in Supabase Auth for the current platform. */
expect fun oauthRedirectUri(): String

/** SHA-256 code challenge encoded as unpadded base64url for PKCE. */
expect fun oauthCodeChallenge(verifier: String): String

