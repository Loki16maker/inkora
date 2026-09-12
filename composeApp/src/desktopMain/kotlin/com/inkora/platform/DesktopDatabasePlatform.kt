package com.inkora.platform

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.inkora.database.generated.InkoraDatabase
import com.inkora.domain.repository.DocumentRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.jar.JarFile

/**
 * Opens the desktop database at `~/.inkora/inkora.db`. Existing databases are
 * migrated in place using SQLDelight's generated schema. A failed migration is
 * propagated and the file is retained for recovery; no delete/recreate fallback
 * is attempted.
 */
actual fun createDocumentRepository(): DocumentRepository {
    val appDirectory = Path.of(platformFileSystem().appDataDirectory.path)
    Files.createDirectories(appDirectory)
    val databasePath = appDirectory.resolve("inkora.db")
    prepareSqliteNativeLibrary(appDirectory)
    // The trimmed jpackage runtime does not always discover JDBC service
    // providers before SQLDelight asks DriverManager for a connection.
    Class.forName("org.sqlite.JDBC")
    val existed = Files.exists(databasePath) && Files.size(databasePath) > 0L
    val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.toAbsolutePath()}")
    prepareSchema(driver, existed)
    return documentRepository(InkoraDatabase(driver))
}

/**
 * SQLite JDBC normally extracts its native DLL through a `jar:` URL. Windows
 * treats `!` as a JAR separator in that URL, so launching an unpacked build
 * from a path such as `D:\PDFDRAWER!!` makes the stream lookup fail. Extract
 * through JarFile instead and point the driver at the stable app-data copy.
 */
private fun prepareSqliteNativeLibrary(appDirectory: Path) {
    // sqlite-jdbc is supplied transitively by SQLDelight's desktop driver, so
    // keep these references reflective instead of making the dependency part
    // of the desktop source set's compile API.
    val sqliteLoader = Class.forName("org.sqlite.SQLiteJDBCLoader", false,
        Thread.currentThread().contextClassLoader)
    val libraryLoaderUtil = Class.forName("org.sqlite.util.LibraryLoaderUtil", false,
        sqliteLoader.classLoader)
    val resourcePath = (libraryLoaderUtil.getMethod("getNativeLibResourcePath")
        .invoke(null) as String).removePrefix("/")
    val libraryName = libraryLoaderUtil.getMethod("getNativeLibName").invoke(null) as String
    val nativeDirectory = appDirectory.resolve("native")
    val nativePath = nativeDirectory.resolve(libraryName)
    Files.createDirectories(nativeDirectory)

    if (!Files.exists(nativePath) || Files.size(nativePath) == 0L) {
        val codeSource = sqliteLoader.protectionDomain?.codeSource?.location
        val sourcePath = codeSource?.toURI()?.let(Path::of)
        when {
            sourcePath != null && Files.isRegularFile(sourcePath) -> {
                JarFile(sourcePath.toFile()).use { jar ->
                    val entry = requireNotNull(jar.getJarEntry("$resourcePath/$libraryName")) {
                        "SQLite native library $resourcePath/$libraryName is missing from $sourcePath"
                    }
                    jar.getInputStream(entry).use { input ->
                        Files.copy(input, nativePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            sourcePath != null && Files.isDirectory(sourcePath) -> {
                val resourceFile = sourcePath.resolve("$resourcePath/$libraryName")
                require(Files.isRegularFile(resourceFile)) {
                    "SQLite native library is missing from $resourceFile"
                }
                Files.copy(resourceFile, nativePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            else -> error("Unable to locate the SQLite JDBC runtime")
        }
    }

    System.setProperty("org.sqlite.lib.path", nativeDirectory.toAbsolutePath().toString())
}

actual fun platformEpochMillis(): Long = System.currentTimeMillis()

actual fun platformUuid(): String = UUID.randomUUID().toString()

private fun prepareSchema(driver: SqlDriver, existed: Boolean) {
    val schema = InkoraDatabase.Schema
    if (!existed) {
        schema.create(driver)
        setSchemaVersion(driver, schema.version)
        return
    }

    var currentVersion = readSchemaVersion(driver)
    if (!hasDocumentsTable(driver)) {
        // A valid SQLite container can exist without application tables (for
        // example after an interrupted first launch). Initialize it in place;
        // this does not delete or replace the user's database file.
        schema.create(driver)
        setSchemaVersion(driver, schema.version)
        return
    }
    // Databases created before Inkora recorded PRAGMA user_version are treated
    // as schema v1 so their additive migrations are still applied.
    if (currentVersion == 0L && hasDocumentsTable(driver)) currentVersion = 1L
    require(currentVersion <= schema.version) {
        "Inkora database version $currentVersion is newer than this app's ${schema.version}"
    }
    if (currentVersion < schema.version) schema.migrate(driver, currentVersion, schema.version)
    setSchemaVersion(driver, schema.version)
}

private fun readSchemaVersion(driver: SqlDriver): Long {
    var version = 0L
    driver.executeQuery(null, "PRAGMA user_version", { cursor ->
        version = cursor.getLong(0) ?: 0L
        QueryResult.Unit
    }, 0)
    return version
}

private fun hasDocumentsTable(driver: SqlDriver): Boolean {
    var present = false
    driver.executeQuery(null,
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'documents' LIMIT 1",
        { cursor ->
            present = cursor.getLong(0) != null
            QueryResult.Unit
        }, 0)
    return present
}

private fun setSchemaVersion(driver: SqlDriver, version: Long) {
    driver.execute(null, "PRAGMA user_version = $version", 0)
}
