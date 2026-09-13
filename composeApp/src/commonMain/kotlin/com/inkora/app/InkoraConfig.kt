package com.inkora.app

/** Central product and storage versions used by every platform target. */
public object InkoraConfig {
    public const val productName: String = "Inkora"
    public const val versionName: String = "1.3.9"
    public const val versionCode: Int = 14
    public const val databaseVersion: Int = 3
    public const val backupFormatVersion: Int = 1
    public const val backupExtension: String = "inkorabackup"

    /** GitHub Releases endpoint used by the desktop and Android update services. */
    public const val githubOwner: String = "Loki16maker"
    public const val githubRepository: String = "inkora"
    public const val updateManifestUrl: String =
        "https://github.com/$githubOwner/$githubRepository/releases/latest/download/latest.json"
}
