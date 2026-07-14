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

package rikka.sui.util.refresh

import android.view.View

interface RefreshKernel

interface RefreshLayout

enum class SpinnerStyle {
    Translate,
}

enum class RefreshState {
    None,
    PullDownToRefresh,
    ReleaseToRefresh,
    Refreshing,
}

interface RefreshHeader {
    fun getView(): View

    fun getSpinnerStyle(): SpinnerStyle

    fun setPrimaryColors(vararg colors: Int)

    fun onInitialized(kernel: RefreshKernel, height: Int, maxDragHeight: Int)

    fun onMoving(isDragging: Boolean, percent: Float, offset: Int, height: Int, maxDragHeight: Int)

    fun onReleased(refreshLayout: RefreshLayout, height: Int, maxDragHeight: Int)

    fun onStartAnimator(refreshLayout: RefreshLayout, height: Int, maxDragHeight: Int)

    fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int

    fun onHorizontalDrag(percentX: Float, offsetX: Int, offsetMax: Int)

    fun isSupportHorizontalDrag(): Boolean

    fun autoOpen(duration: Int, dragRate: Float, animationOnly: Boolean): Boolean

    fun onStateChanged(refreshLayout: RefreshLayout, oldState: RefreshState, newState: RefreshState)
}
