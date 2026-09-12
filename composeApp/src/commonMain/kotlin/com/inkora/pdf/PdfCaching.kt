package com.inkora.pdf

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded least-recently-used cache for encoded pages or thumbnails. */
class PdfLruCache<K, V>(
    private val maxEntries: Int,
    private val maxWeight: Long = Long.MAX_VALUE,
    private val weigh: (V) -> Long = { 1L },
) {
    init { require(maxEntries > 0) { "maxEntries must be positive" } }

    private val mutex = Mutex()
    private val values = LinkedHashMap<K, V>()
    private var weight = 0L

    suspend fun get(key: K): V? = mutex.withLock {
        values.remove(key)?.also { values[key] = it }
    }

    suspend fun put(key: K, value: V) {
        mutex.withLock {
            values.remove(key)?.let { weight -= weigh(it).coerceAtLeast(0) }
            val incomingWeight = weigh(value).coerceAtLeast(0)
            if (incomingWeight > maxWeight) return@withLock
            values[key] = value
            weight += incomingWeight
            while (values.size > maxEntries || weight > maxWeight) {
                val removed = values.remove(values.entries.first().key) ?: break
                weight -= weigh(removed).coerceAtLeast(0)
            }
        }
    }

    suspend fun remove(key: K) = mutex.withLock { values.remove(key)?.also { weight -= weigh(it).coerceAtLeast(0) } }

    suspend fun clear() = mutex.withLock { values.clear(); weight = 0L }

    suspend fun size(): Int = mutex.withLock { values.size }
}

/**
 * Coordinates a small prefetch window around the visible page. The caller owns the scope and can
 * cancel jobs when the document tab closes.
 */
class PdfPageVirtualizer(
    private val document: PdfDocument,
    private val engine: PdfEngine,
    private val pageCache: PdfLruCache<String, PdfRenderedPage>,
    private val thumbnailCache: PdfLruCache<String, PdfRenderedPage>,
) {
    suspend fun page(pageIndex: Int, request: PdfRenderRequest = PdfRenderRequest()): PdfRenderedPage {
        require(pageIndex in 0 until document.pageCount) { "Page index out of range: $pageIndex" }
        return pageCache.get(cacheKey(pageIndex, request))
            ?: engine.renderPage(document, pageIndex, request).also {
                pageCache.put(cacheKey(pageIndex, request), it)
            }
    }

    suspend fun thumbnail(pageIndex: Int, longestSidePx: Int = 240): PdfRenderedPage {
        require(pageIndex in 0 until document.pageCount) { "Page index out of range: $pageIndex" }
        return thumbnailCache.get(thumbKey(pageIndex, longestSidePx))
            ?: engine.renderThumbnail(document, pageIndex, longestSidePx).also {
                thumbnailCache.put(thumbKey(pageIndex, longestSidePx), it)
            }
    }

    suspend fun visiblePages(centerPage: Int, radius: Int = 1): List<Int> {
        require(radius >= 0)
        val first = (centerPage - radius).coerceAtLeast(0)
        val last = (centerPage + radius).coerceAtMost(document.pageCount - 1)
        return if (last < first) emptyList() else (first..last).toList()
    }

    private fun cacheKey(pageIndex: Int, request: PdfRenderRequest): String =
        "${document.id.length}:${document.id}:page:$pageIndex:${request.targetWidthPx ?: 0}:${request.targetHeightPx ?: 0}:${request.dpi}"

    private fun thumbKey(pageIndex: Int, longestSidePx: Int): String = "${document.id.length}:${document.id}:thumbnail:$pageIndex:$longestSidePx"
}
