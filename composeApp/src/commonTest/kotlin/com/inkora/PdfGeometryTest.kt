package com.inkora.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfGeometryTest {
    @Test
    fun displaySizeSwapsForQuarterTurns() {
        assertEquals(PdfPageSize(612f, 792f), pdfDisplaySize(612f, 792f, 0))
        assertEquals(PdfPageSize(792f, 612f), pdfDisplaySize(612f, 792f, 90))
        assertEquals(PdfPageSize(612f, 792f), pdfDisplaySize(612f, 792f, 180))
        assertEquals(PdfPageSize(792f, 612f), pdfDisplaySize(612f, 792f, 270))
        assertEquals(PdfPageSize(792f, 612f), pdfDisplaySize(612f, 792f, -90))
    }

    @Test
    fun displayCornersMapToCropCornersForEveryRotation() {
        val left = 31f
        val bottom = 47f
        val width = 612f
        val height = 792f
        val rotations = listOf(0, 90, 180, 270)
        rotations.forEach { rotation ->
            val size = pdfDisplaySize(width, height, rotation)
            val transform = pdfDisplayTransform(left, bottom, width, height, rotation)
            val mapped = listOf(
                map(transform, 0f, 0f),
                map(transform, size.width, 0f),
                map(transform, 0f, size.height),
                map(transform, size.width, size.height),
            )
            val expected = setOf(
                left to bottom,
                left + width to bottom,
                left to bottom + height,
                left + width to bottom + height,
            )
            mapped.forEach { point ->
                check(expected.any { (x, y) -> close(point.first, x) && close(point.second, y) }) {
                    "rotation=$rotation mapped unexpected crop corner=$point"
                }
            }
        }
    }

    @Test
    fun displayInteriorRoundTripsThroughRotationTransform() {
        val left = 31f
        val bottom = 47f
        val width = 612f
        val height = 792f
        listOf(0, 90, 180, 270, 450, -90).forEach { rotation ->
            val size = pdfDisplaySize(width, height, rotation)
            val transform = pdfDisplayTransform(left, bottom, width, height, rotation)
            val point = map(transform, size.width * .37f, size.height * .61f)
            check(point.first in left..(left + width) && point.second in bottom..(bottom + height)) {
                "rotation=$rotation mapped outside crop=$point"
            }
        }
    }

    private fun map(t: PdfDisplayTransform, x: Float, y: Float): Pair<Float, Float> =
        (t.a * x + t.c * y + t.e) to (t.b * x + t.d * y + t.f)

    private fun close(actual: Float, expected: Float): Boolean =
        kotlin.math.abs(actual - expected) < .01f
}
