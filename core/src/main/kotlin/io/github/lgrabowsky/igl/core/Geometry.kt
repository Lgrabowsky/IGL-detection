package io.github.lgrabowsky.igl.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Achsenparalleles Rechteck in Bildkoordinaten (Pixel; [right] und [bottom] exklusiv). */
data class BoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Int get() = max(0, width) * max(0, height)

    fun union(other: BoundingBox) = BoundingBox(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )

    fun intersectionOverUnion(other: BoundingBox): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        if (w <= 0 || h <= 0) return 0f
        val intersection = w.toFloat() * h
        val union = area.toFloat() + other.area - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    fun centerDistanceTo(other: BoundingBox): Float =
        hypot(centerX - other.centerX, centerY - other.centerY)
}
