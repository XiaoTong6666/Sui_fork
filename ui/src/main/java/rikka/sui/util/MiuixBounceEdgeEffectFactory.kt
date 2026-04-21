package rikka.sui.util

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.EdgeEffect
import androidx.core.content.res.ResourcesCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import rikka.sui.R
import rikka.sui.util.MiuixPullToRefreshView.RefreshState
import kotlin.math.abs

class MiuixBounceEdgeEffectFactory(private val onRefreshListener: (() -> Unit)? = null) : RecyclerView.EdgeEffectFactory() {

    private val isInteractiveOverscrollSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    interface PullStateChangeListener {
        fun onPullStateChanged(dragOffset: Float, state: RefreshState, thresholdOffset: Float, maxDragDistancePx: Float)
    }

    var stateListener: PullStateChangeListener? = null

    private var topTranslationY = 0f
    private var bottomTranslationY = 0f
    private var attachedRecyclerView: RecyclerView? = null

    private var topEffect: BounceEdgeEffect? = null
    private var bottomEffect: BounceEdgeEffect? = null

    private var lastTouchY = 0f
    private var activePointerId = -1

    private val touchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = e.y
                    activePointerId = e.getPointerId(0)
                }

                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex = e.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) return false

                    val y = e.getY(pointerIndex)
                    val deltaY = y - lastTouchY

                    if (!isInteractiveOverscrollSupported) {
                        if (topTranslationY > 0f && deltaY < 0f) {
                            topEffect?.syncTouchAccumulation()
                            return true
                        }
                        if (bottomTranslationY < 0f && deltaY > 0f) {
                            bottomEffect?.syncTouchAccumulation()
                            return true
                        }
                    }
                    lastTouchY = y
                }
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex = e.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) return

                    val y = e.getY(pointerIndex)
                    val deltaY = y - lastTouchY
                    lastTouchY = y

                    if (!isInteractiveOverscrollSupported) {
                        if (topTranslationY > 0f) {
                            topEffect?.handlePull(deltaY)
                        } else if (bottomTranslationY < 0f) {
                            bottomEffect?.handlePull(-deltaY)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isInteractiveOverscrollSupported) {
                        topEffect?.onRelease()
                        bottomEffect?.onRelease()
                    }
                    activePointerId = -1
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    fun finishRefresh() {
        topEffect?.finishRefresh()
    }

    private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(child: View) {
            child.translationY = topTranslationY + bottomTranslationY
        }

        override fun onChildViewDetachedFromWindow(child: View) {
            child.translationY = 0f
        }
    }

    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        if (attachedRecyclerView != view) {
            attachedRecyclerView?.removeOnChildAttachStateChangeListener(attachListener)
            attachedRecyclerView?.removeOnItemTouchListener(touchListener)
            view.addOnChildAttachStateChangeListener(attachListener)
            view.addOnItemTouchListener(touchListener)
            attachedRecyclerView = view
        }

        val effect = BounceEdgeEffect(view.context, direction)
        if (direction == DIRECTION_TOP) {
            topEffect = effect
        } else if (direction == DIRECTION_BOTTOM) {
            bottomEffect = effect
        }
        return effect
    }

    private inner class BounceEdgeEffect(context: Context, val direction: Int) : EdgeEffect(context) {
        private var rawTouchAccumulation = 0f
        private var currentTranslationY = 0f
        private var isRefreshing = false
        private var isReleasingToRefresh = false

        override fun draw(c: Canvas): Boolean = false

        fun syncTouchAccumulation() {
            val view = attachedRecyclerView ?: return
            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
            rawTouchAccumulation = MiuixSpringMath.obtainTouchDistance(currentTranslationY, view.height.toFloat()) * sign
        }

        private fun dispatchState(state: RefreshState) {
            val view = attachedRecyclerView ?: return
            if (direction == DIRECTION_TOP) {
                stateListener?.onPullStateChanged(currentTranslationY, state, view.height / 24f, view.height / 6f)
            }
        }

        private val translationProxy = object : FloatPropertyCompat<BounceEdgeEffect>("translationY") {
            override fun getValue(effect: BounceEdgeEffect): Float = currentTranslationY

            override fun setValue(effect: BounceEdgeEffect, value: Float) {
                currentTranslationY = value
                if (direction == DIRECTION_TOP) {
                    topTranslationY = value
                } else if (direction == DIRECTION_BOTTOM) {
                    bottomTranslationY = value
                }

                val total = topTranslationY + bottomTranslationY
                attachedRecyclerView?.let { rv ->
                    for (i in 0 until rv.childCount) {
                        rv.getChildAt(i).translationY = total
                    }
                }

                if (direction == DIRECTION_TOP) {
                    when {
                        isRefreshing -> dispatchState(RefreshState.REFRESHING)
                        isReleasingToRefresh -> dispatchState(RefreshState.THRESHOLD_REACHED)
                        currentTranslationY > (attachedRecyclerView?.height?.div(24f) ?: 0f) -> dispatchState(RefreshState.THRESHOLD_REACHED)
                        currentTranslationY > 0f -> dispatchState(RefreshState.PULLING)
                        else -> dispatchState(RefreshState.IDLE)
                    }
                }
            }
        }

        private val springAnim = SpringAnimation(this, translationProxy)
            .setSpring(
                SpringForce()
                    .setFinalPosition(0f)
                    .setDampingRatio(0.9f)
                    .setStiffness(ResourcesCompat.getFloat(context.resources, R.dimen.miuix_bounce_stiffness)),
            )

        init {
            springAnim.addEndListener { _, canceled, _, _ ->
                if (canceled) {
                    isReleasingToRefresh = false
                    return@addEndListener
                }

                if (isReleasingToRefresh) {
                    isReleasingToRefresh = false
                    isRefreshing = true
                    attachedRecyclerView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onRefreshListener?.invoke()
                    dispatchState(RefreshState.REFRESHING)
                } else if (!isRefreshing) {
                    translationProxy.setValue(this@BounceEdgeEffect, 0f)
                }
            }
        }

        fun finishRefresh() {
            if (direction == DIRECTION_TOP) {
                isRefreshing = false
                isReleasingToRefresh = false
                dispatchState(RefreshState.REFRESH_COMPLETE)
                springAnim.spring.finalPosition = 0f
                springAnim.start()
            }
        }

        fun handlePull(deltaY: Float): Float {
            val view = attachedRecyclerView ?: return 0f
            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1

            if (springAnim.isRunning) {
                springAnim.cancel()
                syncTouchAccumulation()
            } else if (currentTranslationY == 0f && !isRefreshing) {
                rawTouchAccumulation = 0f
            }

            val originalAccumulation = rawTouchAccumulation
            rawTouchAccumulation += deltaY * sign

            if (direction == DIRECTION_TOP && rawTouchAccumulation < 0f) {
                rawTouchAccumulation = 0f
            } else if (direction == DIRECTION_BOTTOM && rawTouchAccumulation > 0f) {
                rawTouchAccumulation = 0f
            }

            val consumedY = (rawTouchAccumulation - originalAccumulation) * sign

            val translationY = MiuixSpringMath.obtainDampingDistance(rawTouchAccumulation, view.height.toFloat())

            if (direction == DIRECTION_TOP && onRefreshListener != null && !isRefreshing) {
                val threshold = view.height / 24f
                val previousTranslation = currentTranslationY
                if (previousTranslation < threshold && translationY >= threshold) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            translationProxy.setValue(this, translationY)
            return consumedY
        }

        override fun onPull(deltaDistance: Float) {
            handlePull(deltaDistance * (attachedRecyclerView?.height ?: 0))
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            handlePull(deltaDistance * (attachedRecyclerView?.height ?: 0))
        }

        override fun getDistance(): Float {
            val view = attachedRecyclerView ?: return 0f
            return abs(currentTranslationY) / view.height.toFloat()
        }

        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
            val view = attachedRecyclerView ?: return 0f
            val consumedY = handlePull(deltaDistance * view.height)
            return consumedY / view.height.toFloat()
        }

        override fun onRelease() {
            rawTouchAccumulation = 0f
            val view = attachedRecyclerView

            if (direction == DIRECTION_TOP && onRefreshListener != null && !isRefreshing && !isReleasingToRefresh && view != null) {
                val threshold = view.height / 24f
                if (currentTranslationY >= threshold) {
                    isReleasingToRefresh = true
                    springAnim.spring.finalPosition = threshold
                    springAnim.start()
                    return
                }
            }

            if (currentTranslationY != 0f) {
                if (!isRefreshing && !isReleasingToRefresh) {
                    springAnim.spring.finalPosition = 0f
                }
                springAnim.start()
            }
        }

        override fun onAbsorb(velocity: Int) {
            rawTouchAccumulation = 0f
            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1

            val startVelocity = velocity.toFloat() * sign * 0.15f

            if (!isRefreshing && !isReleasingToRefresh) {
                springAnim.spring.finalPosition = 0f
            }

            springAnim.setStartVelocity(startVelocity).start()
        }

        override fun isFinished(): Boolean = !springAnim.isRunning && abs(currentTranslationY) < 0.1f && !isRefreshing && !isReleasingToRefresh
    }
}
