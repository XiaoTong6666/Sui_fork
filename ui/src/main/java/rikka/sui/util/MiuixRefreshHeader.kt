package rikka.sui.util

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.view.animation.PathInterpolatorCompat
import com.scwang.smart.refresh.layout.api.RefreshHeader
import com.scwang.smart.refresh.layout.api.RefreshKernel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.SpinnerStyle
import kotlin.math.cos
import kotlin.math.sin
import com.scwang.smart.refresh.layout.constant.RefreshState as SmartRefreshState

class MiuixRefreshHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr),
    RefreshHeader {
    override fun autoOpen(p0: Int, p1: Float, p2: Boolean): Boolean = false

    private val capsuleView = CapsuleRefreshView(context)

    init {
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(capsuleView, layoutParams)
    }

    override fun getView(): View = this
    override fun getSpinnerStyle(): SpinnerStyle = SpinnerStyle.Translate

    override fun setPrimaryColors(vararg colors: Int) {
        if (colors.isNotEmpty()) {
            capsuleView.setColor(colors[0])
        }
    }

    override fun onInitialized(kernel: RefreshKernel, height: Int, maxDragHeight: Int) {
        capsuleView.setMaxDragHeight(maxDragHeight)
    }

    override fun onMoving(
        isDragging: Boolean,
        percent: Float,
        offset: Int,
        height: Int,
        maxDragHeight: Int,
    ) {
        if (isDragging) {
            capsuleView.setPullOffset(offset.toFloat(), height.toFloat())
        }
    }

    override fun onReleased(refreshLayout: RefreshLayout, height: Int, maxDragHeight: Int) {
    }

    override fun onStartAnimator(refreshLayout: RefreshLayout, height: Int, maxDragHeight: Int) {
        capsuleView.startRefreshing()
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int {
        capsuleView.finishRefreshing()
        return 300
    }

    override fun onHorizontalDrag(percentX: Float, offsetX: Int, offsetMax: Int) {}
    override fun isSupportHorizontalDrag(): Boolean = false

    override fun onStateChanged(
        refreshLayout: RefreshLayout,
        oldState: SmartRefreshState,
        newState: SmartRefreshState,
    ) {
        when (newState) {
            SmartRefreshState.None -> {
                capsuleView.setRefreshState(RefreshState.IDLE)
            }

            SmartRefreshState.PullDownToRefresh -> {
                capsuleView.setRefreshState(RefreshState.PULLING)
            }

            SmartRefreshState.ReleaseToRefresh -> {
                capsuleView.setRefreshState(RefreshState.THRESHOLD_REACHED)
            }

            SmartRefreshState.Refreshing -> {
                capsuleView.setRefreshState(RefreshState.REFRESHING)
            }

            else -> {}
        }
    }
}

enum class RefreshState {
    IDLE,
    PULLING,
    THRESHOLD_REACHED,
    REFRESHING,
    REFRESH_COMPLETE,
}

class CapsuleRefreshView(context: Context) : View(context) {

    private var state: RefreshState = RefreshState.IDLE
    private var pullOffset: Float = 0f
    private var thresholdHeight: Float = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#999999")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var rotationAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    private var completeProgress = 0f
    private var completeAnimator: ValueAnimator? = null

    private var circleSizePx = 0f
    private var indicatorRadiusPx = 0f
    private var ringStrokeWidthPx = 0f

    private val arcRectF = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        circleSizePx = 20f * density
        ringStrokeWidthPx = circleSizePx / 11f
        indicatorRadiusPx = circleSizePx / 2f
        paint.strokeWidth = ringStrokeWidthPx
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setMaxDragHeight(height: Int) {
    }

    fun setPullOffset(offset: Float, threshold: Float) {
        pullOffset = offset
        thresholdHeight = threshold
        invalidate()
    }

    fun setRefreshState(newState: RefreshState) {
        if (state != newState) {
            state = newState
            if (newState == RefreshState.THRESHOLD_REACHED) {
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
            invalidate()
        }
    }

    fun startRefreshing() {
        state = RefreshState.REFRESHING
        startRotating()
        invalidate()
    }

    fun finishRefreshing() {
        state = RefreshState.REFRESH_COMPLETE
        stopRotating()
        startCompleteAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = (height / 2f).coerceAtLeast(circleSizePx / 2f)

        when (state) {
            RefreshState.IDLE, RefreshState.PULLING -> {
                val progress = (pullOffset / thresholdHeight).coerceIn(0f, 1f)
                val alphaF = if (progress > 0.6f) (progress - 0.5f) * 2f else 0f
                paint.alpha = (alphaF * 255).toInt()
                canvas.drawCircle(cx, cy, indicatorRadiusPx, paint)
            }

            RefreshState.THRESHOLD_REACHED, RefreshState.REFRESHING -> {
                paint.alpha = 255
                val overDrag = (pullOffset - thresholdHeight).coerceAtLeast(0f)
                val lineLength = overDrag * 1.0f

                if (lineLength > 0f) {
                    val topY = cy - lineLength / 2
                    val bottomY = cy + lineLength / 2

                    arcRectF.set(cx - indicatorRadiusPx, topY - indicatorRadiusPx, cx + indicatorRadiusPx, topY + indicatorRadiusPx)
                    canvas.drawArc(arcRectF, 180f, 180f, false, paint)

                    arcRectF.set(cx - indicatorRadiusPx, bottomY - indicatorRadiusPx, cx + indicatorRadiusPx, bottomY + indicatorRadiusPx)
                    canvas.drawArc(arcRectF, 0f, 180f, false, paint)

                    canvas.drawLine(cx - indicatorRadiusPx, topY, cx - indicatorRadiusPx, bottomY, paint)
                    canvas.drawLine(cx + indicatorRadiusPx, topY, cx + indicatorRadiusPx, bottomY, paint)
                } else {
                    canvas.drawCircle(cx, cy, indicatorRadiusPx, paint)
                }

                if (state == RefreshState.REFRESHING) {
                    val orbitRadius = indicatorRadiusPx - 2 * ringStrokeWidthPx
                    val angleRad = Math.toRadians((rotationAngle - 90f).toDouble())
                    val dotCx = cx + (orbitRadius * cos(angleRad)).toFloat()
                    val dotCy = cy + (orbitRadius * sin(angleRad)).toFloat()

                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(dotCx, dotCy, ringStrokeWidthPx, paint)
                    paint.style = Paint.Style.STROKE
                }
            }

            RefreshState.REFRESH_COMPLETE -> {
                val alphaF = (1f - (completeProgress * 2f)).coerceIn(0f, 1f)
                paint.alpha = (alphaF * 255).toInt()
                val animatedRadius = indicatorRadiusPx * (1f - (completeProgress * 0.1f))
                canvas.drawCircle(cx, cy, animatedRadius, paint)
            }
        }
    }

    private fun startRotating() {
        if (rotationAnimator?.isRunning == true) return
        rotationAngle = 0f
        rotationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                rotationAngle = (it.animatedValue as Float) * 360f
                if (state == RefreshState.REFRESHING) {
                    invalidate()
                }
            }
            start()
        }
    }

    private fun stopRotating() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun startCompleteAnimation() {
        completeAnimator?.cancel()
        completeProgress = 0f
        completeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 0.37f)
            addUpdateListener {
                completeProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
