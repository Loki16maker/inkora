package com.inkora.platform

import com.inkora.app.InkoraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.system.exitProcess

/** Windows updater for both portable ZIP and MSI installations. */
class DesktopUpdateService : UpdateService {
    override val platform: UpdatePlatform = UpdatePlatform.WINDOWS
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = json.decodeFromString<UpdateManifest>(downloadText(InkoraConfig.updateManifestUrl))
            val artifact = chooseArtifact(manifest)
            when {
                manifest.versionCode <= InkoraConfig.versionCode -> UpdateCheckResult(
                    available = false,
                    manifest = manifest,
                    message = "Inkora ${InkoraConfig.versionName} is up to date.",
                )
                artifact == null -> UpdateCheckResult(
                    available = false,
                    manifest = manifest,
                    message = "Inkora ${manifest.version} is available, but no Windows package was published yet.",
                )
                else -> UpdateCheckResult(
                    available = true,
                    manifest = manifest,
                    message = "Inkora ${manifest.version} is ready to install.",
                )
            }
        }.getOrElse { failure ->
            UpdateCheckResult(false, message = failure.message ?: "Could not check for updates.")
        }
    }

    override suspend fun install(update: UpdateManifest): UpdateInstallResult = withContext(Dispatchers.IO) {
        runCatching {
            require(update.versionCode > InkoraConfig.versionCode) { "Inkora is already up to date." }
            val artifact = chooseArtifact(update) ?: error("No Windows update package is available for this installation.")
            val payload = downloadBytes(artifact.url)
            verifySha256(payload, artifact.sha256)
            val updateDirectory = Path.of(System.getProperty("user.home"), ".inkora", "updates").also { Files.createDirectories(it) }
            val extension = if (artifact === update.windowsMsi) "msi" else "zip"
            val payloadFile = updateDirectory.resolve("Inkora-${update.version}.$extension")
            Files.write(payloadFile, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            val script = if (extension == "zip") {
                writePortableUpdater(updateDirectory, payloadFile)
            } else {
                writeMsiUpdater(updateDirectory, payloadFile)
            }
            ProcessBuilder(
                "powershell.exe", "-NoLogo", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
            ).start()
            // The helper owns the replacement after this process exits. This prevents locked jars
            // and lets the helper restart the exact launcher that was running.
            exitProcess(0)
        }.getOrElse { failure ->
            UpdateInstallResult(UpdateInstallStatus.FAILED, failure.message ?: "Could not install the update.")
        }
    }

    private fun chooseArtifact(update: UpdateManifest): UpdateArtifact? {
        val portable = update.windowsPortable
        val msi = update.windowsMsi
        return if (isWritablePortableInstall()) portable ?: msi else msi ?: portable
    }

    private fun isWritablePortableInstall(): Boolean {
        val directory = installDirectory()
        return Files.exists(directory.resolve("Inkora.exe")) && Files.isWritable(directory)
    }

    private fun installDirectory(): Path {
        val command = runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
        val packaged = command?.let { Path.of(it).parent?.parent?.parent }
        return if (packaged != null && Files.exists(packaged.resolve("Inkora.exe"))) {
            packaged
        } else {
            Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        }
    }

    private fun launcherPath(): Path = installDirectory().resolve("Inkora.exe")

    private fun writePortableUpdater(directory: Path, zip: Path): Path {
        val script = directory.resolve("Inkora-update-${System.currentTimeMillis()}.ps1")
        val body = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}processId = ${ProcessHandle.current().pid()}
            ${'$'}zip = ${psQuote(zip.toAbsolutePath().toString())}
            ${'$'}install = ${psQuote(installDirectory().toAbsolutePath().toString())}
            ${'$'}launcher = ${psQuote(launcherPath().toAbsolutePath().toString())}
            for (${ '$'}i = 0; ${'$'}i -lt 80; ${'$'}i++) {
                if (-not (Get-Process -Id ${'$'}processId -ErrorAction SilentlyContinue)) { break }
                Start-Sleep -Milliseconds 250
            }
            Start-Sleep -Milliseconds 700
            ${'$'}staging = Join-Path ${'$'}env:TEMP ('inkora-update-' + [guid]::NewGuid().ToString())
            New-Item -ItemType Directory -Force -Path ${'$'}staging | Out-Null
            Expand-Archive -LiteralPath ${'$'}zip -DestinationPath ${'$'}staging -Force
            Get-ChildItem -LiteralPath ${'$'}staging -Force | ForEach-Object {
                Copy-Item -LiteralPath ${'$'}_.FullName -Destination ${'$'}install -Recurse -Force
            }
            if (Test-Path ${'$'}launcher) { Start-Process -FilePath ${'$'}launcher }
            Remove-Item -LiteralPath ${'$'}staging -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}zip -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}PSCommandPath -Force -ErrorAction SilentlyContinue
        """.trimIndent()
        Files.writeString(script, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return script
    }

    private fun writeMsiUpdater(directory: Path, msi: Path): Path {
        val script = directory.resolve("Inkora-update-${System.currentTimeMillis()}.ps1")
        val body = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}processId = ${ProcessHandle.current().pid()}
            ${'$'}msi = ${psQuote(msi.toAbsolutePath().toString())}
            ${'$'}launcher = ${psQuote(launcherPath().toAbsolutePath().toString())}
            for (${ '$'}i = 0; ${'$'}i -lt 80; ${'$'}i++) {
                if (-not (Get-Process -Id ${'$'}processId -ErrorAction SilentlyContinue)) { break }
                Start-Sleep -Milliseconds 250
            }
            Start-Sleep -Milliseconds 700
            ${'$'}result = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', ${'$'}msi, '/passive', '/norestart') -Wait -PassThru
            if (${ '$'}result.ExitCode -eq 0 -and (Test-Path ${'$'}launcher)) { Start-Process -FilePath ${'$'}launcher }
            Remove-Item -LiteralPath ${'$'}msi -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}PSCommandPath -Force -ErrorAction SilentlyContinue
        """.trimIndent()
        Files.writeString(script, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return script
    }

    private fun psQuote(value: String): String = "'${value.replace("'", "''")}'"

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

actual fun updateService(): UpdateService = DesktopUpdateService()
