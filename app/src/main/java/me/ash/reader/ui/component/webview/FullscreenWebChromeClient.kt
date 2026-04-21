package me.ash.reader.ui.component.webview

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
    @Volatile private var preferLandscapeFullscreen: Boolean? = null
    private var rotateCurrentFullscreen: Boolean = false

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        showCustomView(
            view = view,
            callback = callback,
            rotateLandscape = preferLandscapeFullscreen ?: true,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onShowCustomView(
        view: View?,
        requestedOrientation: Int,
        callback: CustomViewCallback?,
    ) {
        showCustomView(
            view = view,
            callback = callback,
            rotateLandscape =
                requestedOrientation.toFullscreenLandscapePreference()
                    ?: preferLandscapeFullscreen
                    ?: true,
        )
    }

    fun updateMediaAspectRatio(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            preferLandscapeFullscreen = width / height >= LANDSCAPE_ASPECT_RATIO_THRESHOLD
        }
    }

    private fun showCustomView(
        view: View?,
        callback: CustomViewCallback?,
        rotateLandscape: Boolean,
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
        rotateCurrentFullscreen = rotateLandscape

        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                layoutFullscreenChild(view, rotateCurrentFullscreen)
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
        val activity = activity
        val container = fullscreenViewContainer
        val view = customView
        try {
            if (container != null && view != null) {
                container.removeView(view)
            }
            if (activity != null && container != null) {
                activity.fullscreenContainer.removeView(container)
                activity.exitFullscreen()
            }
        } finally {
            fullscreenViewContainer = null
            customView = null
            rotateCurrentFullscreen = false
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
        }
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

private const val LANDSCAPE_ASPECT_RATIO_THRESHOLD = 1.2f

private fun Int.toFullscreenLandscapePreference(): Boolean? =
    when (this) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> true
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> false
        else -> null
    }

private fun FrameLayout.layoutFullscreenChild(child: View, rotateLandscape: Boolean) {
    val containerWidth = width
    val containerHeight = height
    if (containerWidth <= 0 || containerHeight <= 0) return

    if (rotateLandscape && containerHeight > containerWidth) {
        child.updateFullscreenLayout(
            width = containerHeight,
            height = containerWidth,
            rotation = 90f,
            translationX = containerWidth.toFloat(),
            translationY = 0f,
        )
    } else {
        child.updateFullscreenLayout(
            width = containerWidth,
            height = containerHeight,
            rotation = 0f,
            translationX = 0f,
            translationY = 0f,
        )
    }
}

private fun View.updateFullscreenLayout(
    width: Int,
    height: Int,
    rotation: Float,
    translationX: Float,
    translationY: Float,
) {
    pivotX = 0f
    pivotY = 0f
    this.rotation = rotation
    this.translationX = translationX
    this.translationY = translationY

    val current = layoutParams as? FrameLayout.LayoutParams
    if (current?.width != width || current.height != height) {
        layoutParams = FrameLayout.LayoutParams(width, height)
    }
}
