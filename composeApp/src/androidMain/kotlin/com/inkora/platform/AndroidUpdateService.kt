package com.inkora.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.inkora.app.InkoraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Android sideload updater. Android's package installer keeps the final approval in the user's hands. */
class AndroidUpdateService : UpdateService {
    override val platform: UpdatePlatform = UpdatePlatform.ANDROID
    private val json = Json { ignoreUnknownKeys = true }
    private val context get() = AndroidPlatformContext.context()

    override suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = json.decodeFromString<UpdateManifest>(downloadText(InkoraConfig.updateManifestUrl))
            when {
                manifest.versionCode <= InkoraConfig.versionCode -> UpdateCheckResult(
                    available = false,
                    manifest = manifest,
                    message = "Inkora ${InkoraConfig.versionName} is up to date.",
                )
                manifest.androidApk == null -> UpdateCheckResult(
                    available = false,
                    manifest = manifest,
                    message = "Inkora ${manifest.version} is available, but no Android APK was published yet.",
                )
                else -> UpdateCheckResult(
                    available = true,
                    manifest = manifest,
                    message = "Inkora ${manifest.version} is ready to install.",
                )
            }
        }.getOrElse { failure -> UpdateCheckResult(false, message = failure.message ?: "Could not check for updates.") }
    }

    override suspend fun install(update: UpdateManifest): UpdateInstallResult = withContext(Dispatchers.IO) {
        runCatching {
            require(update.versionCode > InkoraConfig.versionCode) { "Inkora is already up to date." }
            val artifact = update.androidApk ?: error("No Android APK was published for this release.")

            // Play-installed copies should stay on Play so Play Protect and
            // the store's signing key remain in control. Sideloaded copies
            // continue through the GitHub APK flow below.
            val installedByPlay = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName == "com.android.vending"
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName) == "com.android.vending"
                }
            }.getOrDefault(false)
            if (installedByPlay && update.playStoreUrl.isNotBlank()) {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.playStoreUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                AndroidPlatformContext.activity().startActivity(storeIntent)
                return@withContext UpdateInstallResult(UpdateInstallStatus.STARTED, "Google Play opened to update Inkora.")
            }
            val payload = downloadBytes(artifact.url)
            verifySha256(payload, artifact.sha256)
            val updateDirectory = File(context.cacheDir, "inkora/updates").also(File::mkdirs)
            val apk = File(updateDirectory, "Inkora-${update.version}.apk")
            apk.writeBytes(payload)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                AndroidPlatformContext.activity().startActivity(settings)
                return@withContext UpdateInstallResult(
                    UpdateInstallStatus.NEEDS_USER_ACTION,
                    "Allow Inkora to install updates, then choose Check for updates again.",
                )
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val installer = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            AndroidPlatformContext.activity().startActivity(installer)
            UpdateInstallResult(UpdateInstallStatus.STARTED, "Android opened the installer. Confirm the update to finish.")
        }.getOrElse { failure -> UpdateInstallResult(UpdateInstallStatus.FAILED, failure.message ?: "Could not install the update.") }
    }

    private fun downloadText(url: String): String = downloadBytes(url).toString(StandardCharsets.UTF_8)

    private fun downloadBytes(url: String): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Inkora/${InkoraConfig.versionName}")
        try {
            require(connection.responseCode in 200..299) { "Update server returned HTTP ${connection.responseCode}." }
            val expected = connection.contentLengthLong
            require(expected <= MAX_DOWNLOAD_BYTES) { "Update package is too large." }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream((expected.coerceAtLeast(0).coerceAtMost(4 * 1024 * 1024)).toInt())
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_DOWNLOAD_BYTES) { "Update package is too large." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifySha256(payload: ByteArray, expected: String) {
        require(expected.isNotBlank()) { "The release is missing its SHA-256 checksum." }
        val actual = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        require(actual.equals(expected.trim(), ignoreCase = true)) { "The downloaded update failed checksum verification." }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L
    }
}

actual fun updateService(): UpdateService = AndroidUpdateService()
