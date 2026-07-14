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

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class EdgeDragFastScroller(
    recyclerView: RecyclerView,
    private val edgeWidthDp: Float = 24f,
    private val onDragStart: (() -> Unit)? = null,
) : RecyclerView.SimpleOnItemTouchListener() {

    private val edgeWidthPx = recyclerView.resources.displayMetrics.density * edgeWidthDp
    private var dragging = false

    init {
        recyclerView.addOnItemTouchListener(this)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = event.x >= rv.width - edgeWidthPx
                if (dragging) {
                    onDragStart?.invoke()
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    scrollToOffset(rv, event.y)
                }
                return dragging
            }

            MotionEvent.ACTION_MOVE -> return dragging

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> dragging = false
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
        if (!dragging) return
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> scrollToOffset(rv, event.y)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> dragging = false
        }
    }

    private fun scrollToOffset(recyclerView: RecyclerView, y: Float) {
        val proportion = (y / recyclerView.height.toFloat()).coerceIn(0f, 1f)
        val scrollRange = recyclerView.computeVerticalScrollRange()
        val scrollExtent = recyclerView.computeVerticalScrollExtent()
        val maxScrollOffset = (scrollRange - scrollExtent).coerceAtLeast(0)
        if (maxScrollOffset == 0) return

        val targetOffset = (maxScrollOffset * proportion).toInt()
        val currentOffset = recyclerView.computeVerticalScrollOffset()
        val delta = targetOffset - currentOffset
        if (delta != 0) {
            recyclerView.scrollBy(0, delta)
        }
    }
}
