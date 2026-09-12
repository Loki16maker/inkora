package com.inkora.platform

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.inkora.database.generated.InkoraDatabase
import com.inkora.domain.repository.DocumentRepository
import java.util.UUID

/**
 * AndroidSqliteDriver opens `files/databases/inkora.db` and runs the generated
 * SQLDelight schema/migrations. It never removes or replaces an existing file;
 * a migration exception is surfaced to the caller for recovery/reporting.
 */
actual fun createDocumentRepository(): DocumentRepository {
    val context = AndroidPlatformContext.context()
    val driver = AndroidSqliteDriver(InkoraDatabase.Schema, context, "inkora.db")
    return documentRepository(InkoraDatabase(driver))
}

actual fun platformEpochMillis(): Long = System.currentTimeMillis()

actual fun platformUuid(): String = UUID.randomUUID().toString()
