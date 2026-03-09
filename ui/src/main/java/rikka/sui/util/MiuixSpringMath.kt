package rikka.sui.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

object MiuixSpringMath {

    fun obtainDampingDistance(currentPixelOffset: Float, range: Float): Float {
        val normalizedInput = abs(currentPixelOffset) / range
        val x = max(0f, min(normalizedInput, 1f))

        val dampedFactor = x - x.toDouble().pow(2.0) + (x.toDouble().pow(3.0) / 3.0)

        return (dampedFactor.toFloat() * range) * sign(currentPixelOffset)
    }

    fun obtainTouchDistance(currentPixelOffset: Float, range: Float): Float {
        var absPixelOffset = abs(currentPixelOffset)
        val absMaxOffset = abs(obtainDampingDistance(range, range))

        if (absPixelOffset <= 0f) return 0f
        if (absPixelOffset >= absMaxOffset) {
            absPixelOffset = absMaxOffset
        }

        val base = range - (3.0 * absPixelOffset)
        val part2 = range.toDouble().pow(2.0 / 3.0) * sign(base) * abs(base).pow(1.0 / 3.0)
        return (range - part2).toFloat()
    }
}
