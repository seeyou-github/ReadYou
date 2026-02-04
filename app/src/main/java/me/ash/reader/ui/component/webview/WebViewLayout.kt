package me.ash.reader.ui.component.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import timber.log.Timber

object WebViewLayout {

    @SuppressLint("SetJavaScriptEnabled")
    fun get(
        context: Context,
        readingFontsPreference: ReadingFontsPreference,
        webViewClient: WebViewClient,
        enableJavaScript: Boolean = true,
        onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    ) = WebView(context).apply {
        this.webViewClient = webViewClient
        scrollBarSize = 0
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
        with(this.settings) {

            standardFontFamily = when (readingFontsPreference) {
                ReadingFontsPreference.Cursive -> "cursive"
                ReadingFontsPreference.Monospace -> "monospace"
                ReadingFontsPreference.SansSerif -> "sans-serif"
                ReadingFontsPreference.Serif -> "serif"
                ReadingFontsPreference.External -> {
                    allowFileAccess = true
                    allowFileAccessFromFileURLs = true
                    "sans-serif"
                }
                ReadingFontsPreference.External -> {
                    allowFileAccess = true
                    allowFileAccessFromFileURLs = true
                    "sans-serif"
                }
                else -> "sans-serif"
            }
            domStorageEnabled = true
            javaScriptEnabled = enableJavaScript
            addJavascriptInterface(object : JavaScriptInterface {
                @JavascriptInterface
                override fun onImgTagClick(imgUrl: String?, alt: String?) {
                    if (onImageClick != null && imgUrl != null) {
                        onImageClick.invoke(imgUrl, alt ?: "")
                    }
                }

                @JavascriptInterface
                override fun onDomMarked(totalNodes: Int) {
                    // DOM 标记完成回调
                }

                @JavascriptInterface
                override fun onTextNodesExtracted(nodesJson: String) {
                    // 文本节点提取完成回调
                }

                @JavascriptInterface
                override fun onVisibleNodesExtracted(nodesJson: String) {
                    // 可视区域节点提取完成回调
                }

                @JavascriptInterface
                override fun onTranslationsUpdated(updatedCount: Int) {
                    // 翻译节点更新完成回调
                }

                @JavascriptInterface
                override fun onLog(message: String) {
                    // 日志回调
                    timber.log.Timber.d("[WebView] $message")
                }
            }, JavaScriptInterface.NAME)
            setSupportZoom(false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                isAlgorithmicDarkeningAllowed = true
            }
        }
    }
}
