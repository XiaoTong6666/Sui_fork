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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

object MiuixPopupState {
    var anchorX: Int = 0
    var anchorY: Int = 0
}

class MiuixPopupTransition : Visibility {
    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator = createAnimation(view, true)

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator = createAnimation(view, false)

    private fun createAnimation(view: View, isEnter: Boolean): Animator {
        val startScale = if (isEnter) 0.6f else 1.0f
        val endScale = if (isEnter) 1.0f else 0.6f
        val startAlpha = if (isEnter) 0f else 1f
        val endAlpha = if (isEnter) 1f else 0f

        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, startAlpha, endAlpha)
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, startScale, endScale)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, startScale, endScale)

        val updateListener = object : ValueAnimator.AnimatorUpdateListener {
            var initialized = false
            override fun onAnimationUpdate(animation: ValueAnimator) {
                if (!initialized) {
                    initialized = true
                    val loc = IntArray(2)
                    view.getLocationOnScreen(loc)
                    val popupY = loc[1]

                    view.pivotX = view.width.toFloat()

                    view.pivotY = if (popupY < MiuixPopupState.anchorY) view.height.toFloat() else 0f
                }
            }
        }
        alphaAnim.addUpdateListener(updateListener)

        return AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
            duration = if (isEnter) 200L else 150L
            interpolator = DecelerateInterpolator(if (isEnter) 2f else 1.5f)
        }
    }
}
