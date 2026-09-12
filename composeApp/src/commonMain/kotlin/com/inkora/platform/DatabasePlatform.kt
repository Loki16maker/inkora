package com.inkora.platform

import com.inkora.data.store.SqlDelightDocumentRepository
import com.inkora.domain.repository.DocumentRepository

/**
 * Opens the app-owned Inkora database for the current target. The returned
 * repository owns no coroutine scope; callers close its driver with the
 * platform lifecycle when the process is shutting down.
 */
expect fun createDocumentRepository(): DocumentRepository

/** Monotonic wall-clock value used for persisted metadata timestamps. */
expect fun platformEpochMillis(): Long

/** UUID source kept behind the platform boundary for deterministic tests. */
expect fun platformUuid(): String

/** Shared factory helper for platform bootstrap implementations. */
internal fun documentRepository(database: com.inkora.database.generated.InkoraDatabase): DocumentRepository =
    SqlDelightDocumentRepository(database)
