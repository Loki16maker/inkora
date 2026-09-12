# Inkora updates

Inkora checks the latest GitHub Release when the user chooses **Check for updates**. The release contains a `latest.json` manifest and three verified artifacts:

- `Inkora-<version>-windows-portable.zip`
- `Inkora-<version>.msi`
- `Inkora-<version>-android.apk`

Windows portable installs download the ZIP and use a detached PowerShell helper to close Inkora, replace the app folder, and restart it. MSI installs download the MSI and run the normal in-place upgrade. Android downloads the APK into app-owned cache storage and opens the system package installer.

Every payload is checked against the SHA-256 value in `latest.json` before installation. Android still shows the system confirmation screen and may require enabling **Allow from this source** for Inkora once.

If an Android copy was installed by Google Play, Inkora opens its Play listing instead of sideloading the GitHub APK. Copies installed from GitHub use the APK installer flow.

## Publishing a release

1. Bump `version` in the root `build.gradle.kts`, `InkoraConfig.versionName`, and `InkoraConfig.versionCode`.
2. Create and push a tag matching that version, for example `v1.2.0`.
3. GitHub Actions builds the MSI, portable ZIP, and signed Android APK, generates `latest.json`, and publishes the Release.

The workflow expects these repository secrets:

- `INKORA_KEYSTORE_BASE64`
- `INKORA_KEYSTORE_PASSWORD`
- `INKORA_KEY_ALIAS`
- `INKORA_KEY_PASSWORD`

Keep the keystore private and use the same key for every Android release. Without the same signing key, Android cannot install an APK as an update over an existing Inkora installation.
