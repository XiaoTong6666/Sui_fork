/*
 * This file is part of Sui.
 *
 * Sui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sui.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 Sui Contributors
 */

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
