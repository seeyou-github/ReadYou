package me.ash.reader.ui.component.webview

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import me.ash.reader.ui.ext.findActivity

class FullscreenWebChromeClient(
    context: Context,
) : WebChromeClient() {
    private val activity: Activity? = context.findActivity()
    private var fullscreenViewContainer: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        showCustomView(view = view, callback = callback)
    }

    @Deprecated("Deprecated in Java")
    override fun onShowCustomView(
        view: View?,
        requestedOrientation: Int,
        callback: CustomViewCallback?,
    ) {
        showCustomView(view = view, callback = callback)
    }

    private fun showCustomView(
        view: View?,
        callback: CustomViewCallback?,
    ) {
        val activity = activity
        if (view == null || activity == null) {
            callback?.onCustomViewHidden()
            return
        }

        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback

        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                layoutLandscapeChild(view)
            }
        }
        fullscreenViewContainer = container
        view.setBackgroundColor(Color.TRANSPARENT)
        container.addView(view)
        activity.fullscreenContainer.addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.enterFullscreen()
    }

    override fun onHideCustomView() {
        val activity = activity ?: return
        val container = fullscreenViewContainer ?: return
        val view = customView

        if (view != null) {
            container.removeView(view)
        }
        activity.fullscreenContainer.removeView(container)
        activity.exitFullscreen()
        fullscreenViewContainer = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }
}

private val Activity.fullscreenContainer: FrameLayout
    get() = window.decorView as FrameLayout

private fun Activity.enterFullscreen() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Activity.exitFullscreen() {
    WindowCompat.getInsetsController(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}

private fun FrameLayout.layoutLandscapeChild(child: View) {
    val containerWidth = width
    val containerHeight = height
    if (containerWidth <= 0 || containerHeight <= 0) return

    if (containerHeight > containerWidth) {
        child.rotation = 90f
        child.pivotX = 0f
        child.pivotY = 0f
        child.translationX = containerWidth.toFloat()
        child.translationY = 0f
        child.layoutParams =
            FrameLayout.LayoutParams(containerHeight, containerWidth)
    } else {
        child.rotation = 0f
        child.translationX = 0f
        child.translationY = 0f
        child.layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
    }
}
