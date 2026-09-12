package com.inkora.domain.model

import kotlinx.serialization.Serializable

@Serializable
public enum class AnnotationType { INK, HIGHLIGHT, TEXT, IMAGE, SHAPE, STICKY_NOTE }

@Serializable
public data class Annotation(
    val id: AnnotationId,
    val pageId: PageId,
    val type: AnnotationType,
    /** Serialized payload is versioned by the annotation codec, allowing future migrations. */
    val payload: String,
    val bounds: Rect = Rect.ZERO,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
)

@Serializable
public data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    public val width: Float get() = right - left
    public val height: Float get() = bottom - top

    public fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    public companion object {
        public val ZERO: Rect = Rect(0f, 0f, 0f, 0f)
    }
}

@Serializable
public data class Bookmark(
    val id: String,
    val documentId: DocumentId,
    val pageIndex: Int,
    val title: String? = null,
    val note: String? = null,
    val createdAtEpochMs: Long,
)
