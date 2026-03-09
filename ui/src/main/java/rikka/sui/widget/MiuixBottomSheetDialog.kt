package rikka.sui.widget

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import rikka.sui.R

class MiuixBottomSheetDialog(
    context: Context,
    private val contentView: View,
) : ComponentDialog(context, R.style.MiuixBottomSheetDialogStyle) {

    private lateinit var layout: MiuixBottomSheetLayout
    private var dimOverlay: View? = null

    private fun setupDimOverlay() {
        var actContext = context
        while (actContext is android.content.ContextWrapper) {
            if (actContext is android.app.Activity) break
            actContext = actContext.baseContext
        }
        val activity = actContext as? android.app.Activity ?: return

        dimOverlay = View(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            setBackgroundColor(if (isDark) 0x99000000.toInt() else 0x4D000000.toInt())
            alpha = 0f
        }
        (activity.window.decorView as ViewGroup).addView(dimOverlay)
    }

    private fun removeDimOverlay() {
        dimOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        dimOverlay = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupDimOverlay()

        layout = MiuixBottomSheetLayout(context)

        layout.onDimAlphaChange = { alpha ->
            dimOverlay?.alpha = alpha
        }
        layout.onDimAlphaAnimate = { alpha, duration ->
            dimOverlay?.animate()?.alpha(alpha)?.setDuration(duration)?.setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))?.start()
        }

        (contentView.parent as? ViewGroup)?.removeView(contentView)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.gravity = android.view.Gravity.BOTTOM
        layout.addView(contentView, lp)

        layout.onDismissRequest = {
            super.dismiss()
        }

        setContentView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        @Suppress("DEPRECATION")
        window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.setBackgroundDrawableResource(android.R.color.transparent)

            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                it.isNavigationBarContrastEnforced = false
                it.isStatusBarContrastEnforced = false
            }

            it.statusBarColor = android.graphics.Color.TRANSPARENT
            it.navigationBarColor = android.graphics.Color.TRANSPARENT
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    layout.dismiss()
                }
            },
        )
    }

    override fun show() {
        super.show()
        layout.show()
    }

    override fun dismiss() {
        layout.dismiss()
    }

    override fun onStop() {
        super.onStop()
        removeDimOverlay()
    }
}
