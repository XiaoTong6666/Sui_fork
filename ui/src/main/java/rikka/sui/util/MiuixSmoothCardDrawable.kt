package rikka.sui.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet

class MiuixSmoothCardDrawable(
    private val cornerRadius: Float,
    private val fillColor: Int,
    private val topCornersOnly: Boolean = false,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val path = Path()

    override fun getConstantState(): ConstantState = MiuixSmoothCardState(cornerRadius, fillColor, topCornersOnly)

    override fun mutate(): Drawable = this

    private class MiuixSmoothCardState(
        val cornerRadius: Float,
        val fillColor: Int,
        val topCornersOnly: Boolean,
    ) : ConstantState() {
        override fun newDrawable(): Drawable = MiuixSmoothCardDrawable(cornerRadius, fillColor, topCornersOnly)
        override fun getChangingConfigurations() = 0
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        updatePath()
    }

    private fun updatePath() {
        MiuixSquircleUtils.createSquirclePath(bounds.width().toFloat(), bounds.height().toFloat(), cornerRadius, path, topCornersOnly)
        if (bounds.left != 0 || bounds.top != 0) {
            path.offset(bounds.left.toFloat(), bounds.top.toFloat())
        }
    }

    override fun draw(canvas: Canvas) {
        if (path.isEmpty && !bounds.isEmpty) {
            updatePath()
        }
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getOutline(outline: Outline) {
        if (path.isEmpty && !bounds.isEmpty) {
            updatePath()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!path.isEmpty) {
                outline.setPath(path)
                return
            }
        }
        outline.setRoundRect(bounds, cornerRadius)
    }

    companion object {
        fun createSelectorWithOverlay(
            context: Context,
            baseColorInt: Int,
            radiusDp: Float = 16f,
            topCornersOnly: Boolean = false,
        ): StateListDrawable {
            val radiusPx = radiusDp * context.resources.displayMetrics.density
            val isNightMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val overlayColor = if (isNightMode) 0x1AFFFFFF else 0x1A000000

            val normalDrawable = MiuixSmoothCardDrawable(radiusPx, baseColorInt, topCornersOnly)
            val pressedDrawable = android.graphics.drawable.LayerDrawable(
                arrayOf(
                    MiuixSmoothCardDrawable(radiusPx, baseColorInt, topCornersOnly),
                    MiuixSmoothCardDrawable(radiusPx, overlayColor, topCornersOnly),
                ),
            )

            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
                addState(StateSet.WILD_CARD, normalDrawable)
            }
        }
    }
}

object MiuixSquircleUtils {
    fun createSquirclePath(w: Float, h: Float, r: Float, path: Path, topCornersOnly: Boolean = false) {
        path.reset()
        val smooth = (1.52866f * r).coerceAtMost(w / 2f)

        if (w < smooth * 2 || h < smooth * 2) {
            path.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
            return
        }

        path.moveTo(w / 2, 0f)
        path.lineTo(w - smooth, 0f)

        path.cubicTo(w - r * 1.08849f, 0f, w - r * 0.868407f, 0f, w - r * 0.631494f, r * 0.074911f)
        path.cubicTo(w - r * 0.372824f, r * 0.16906f, w - r * 0.16906f, r * 0.372824f, w - r * 0.074911f, r * 0.631494f)
        path.cubicTo(w, r * 0.868407f, w, r * 1.08849f, w, smooth)

        path.lineTo(w, h - smooth)

        if (topCornersOnly) {
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.lineTo(0f, smooth)
        } else {
            path.cubicTo(w, h - r * 1.08849f, w, h - r * 0.868407f, w - r * 0.074911f, h - r * 0.631494f)
            path.cubicTo(w - r * 0.16906f, h - r * 0.372824f, w - r * 0.372824f, h - r * 0.16906f, w - r * 0.631494f, h - r * 0.074911f)
            path.cubicTo(w - r * 0.868407f, h, w - r * 1.08849f, h, w - smooth, h)

            path.lineTo(smooth, h)

            path.cubicTo(r * 1.08849f, h, r * 0.868407f, h, r * 0.631494f, h - r * 0.074911f)
            path.cubicTo(r * 0.372824f, h - r * 0.16906f, r * 0.16906f, h - r * 0.372824f, r * 0.074911f, h - r * 0.631494f)
            path.cubicTo(0f, h - r * 0.868407f, 0f, h - r * 1.08849f, 0f, h - smooth)

            path.lineTo(0f, smooth)
        }

        path.cubicTo(0f, r * 1.08849f, 0f, r * 0.868407f, r * 0.074911f, r * 0.631494f)
        path.cubicTo(r * 0.16906f, r * 0.372824f, r * 0.372824f, r * 0.16906f, r * 0.631494f, r * 0.074911f)
        path.cubicTo(r * 0.868407f, 0f, r * 1.08849f, 0f, smooth, 0f)

        path.close()
    }

    @android.annotation.SuppressLint("NewApi")
    fun getBottomCornerRadius(context: Context): Float {
        var radiusPx = 0f
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            var activity: android.app.Activity? = null
            var actContext = context
            while (actContext is android.content.ContextWrapper) {
                if (actContext is android.app.Activity) {
                    activity = actContext
                    break
                }
                actContext = actContext.baseContext
            }
            activity?.window?.decorView?.rootWindowInsets?.let { insets ->
                insets.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)?.let { corner ->
                    radiusPx = corner.radius.toFloat()
                }
            }
        }
        val density = context.resources.displayMetrics.density
        val marginPx = 12f * density
        val minRadiusPx = 32f * density

        val finalRadiusPx = if (radiusPx > 0f) {
            (radiusPx - marginPx).coerceAtLeast(minRadiusPx)
        } else {
            minRadiusPx
        }

        return finalRadiusPx
    }
}

class MiuixSquircleProvider(private val radiusDp: Float) : android.view.ViewOutlineProvider() {
    private val path = Path()

    override fun getOutline(view: android.view.View, outline: Outline) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val radiusPx = radiusDp * view.context.resources.displayMetrics.density
        MiuixSquircleUtils.createSquirclePath(w, h, radiusPx, path)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            outline.setPath(path)
        } else {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
}
