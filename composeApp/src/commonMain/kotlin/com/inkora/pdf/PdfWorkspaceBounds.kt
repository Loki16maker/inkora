package com.inkora.pdf

/** Includes off-page annotations in exports while leaving pages without overflow at their original size. */
/** Computes the exported page rectangle, including any ink or objects outside the source crop. */
fun pdfWorkspaceBounds(size: PdfPageSize, annotations: List<PdfAnnotation>): PdfRect {
    var left = 0f; var top = 0f; var right = size.width; var bottom = size.height
    fun include(rect: PdfRect, padding: Float = 0f) {
        require(listOf(rect.left, rect.top, rect.right, rect.bottom, padding).all { it.isFinite() }) { "Invalid annotation coordinates" }
        if (rect.left - padding < 0f) left = minOf(left, rect.left - padding - 24f)
        if (rect.top - padding < 0f) top = minOf(top, rect.top - padding - 24f)
        if (rect.right + padding > size.width) right = maxOf(right, rect.right + padding + 24f)
        if (rect.bottom + padding > size.height) bottom = maxOf(bottom, rect.bottom + padding + 24f)
    }
    for (annotation in annotations) when (annotation) {
        is PdfInkAnnotation -> if (annotation.points.isNotEmpty()) {
            include(PdfRect(annotation.points.minOf { it.x }, annotation.points.minOf { it.y },
                annotation.points.maxOf { it.x }, annotation.points.maxOf { it.y }), annotation.width * 1.1f)
        }
        is PdfHighlightAnnotation -> annotation.bounds.forEach { include(it) }
        is PdfTextAnnotation -> include(annotation.bounds)
        is PdfShapeAnnotation -> include(annotation.bounds,
            if (annotation.shape == PdfShapeType.ARROW) maxOf(10f, annotation.width * 4f) else annotation.width / 2f)
        is PdfImageAnnotation -> include(annotation.bounds)
    }
    return PdfRect(left, top, right, bottom)
}
