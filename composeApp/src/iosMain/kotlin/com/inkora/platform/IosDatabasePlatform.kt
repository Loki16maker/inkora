package com.inkora.platform

import com.inkora.domain.repository.DocumentRepository

/**
 * iOS keeps the same factory contract. The NativeSqliteDriver is intentionally
 * wired by the future Xcode host; Windows cannot link the native driver. This
 * explicit failure prevents silently falling back to an in-memory library.
 */
actual fun createDocumentRepository(): DocumentRepository = error(
    "Inkora iOS persistence requires the host NativeSqliteDriver bootstrap; no in-memory fallback is used",
)

actual fun platformEpochMillis(): Long = platform.Foundation.NSDate().timeIntervalSince1970.times(1000).toLong()

actual fun platformUuid(): String = platform.Foundation.NSUUID().UUIDString
