package me.ash.reader.ui.component.webview

import android.content.Context
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.ash.reader.ui.ext.isUrl
import me.ash.reader.domain.model.article.ArticleImageCacheType
import me.ash.reader.domain.repository.ArticleImageCacheDao
import java.io.DataInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection

const val INJECTION_TOKEN = "/android_asset_font/"

class WebViewClient(
    private val context: Context,
    private val refererDomain: String?,
    private val onOpenLink: (url: String) -> Unit,
    private val enableJavaScript: Boolean = true,
    private val articleId: String? = null,
) : WebViewClient() {

    private val cacheDao: ArticleImageCacheDao? by lazy {
        runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ArticleImageCacheEntryPoint::class.java,
                )
            }
            .getOrNull()
            ?.articleImageCacheDao()
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null && articleId != null) {
            try {
                val cache =
                    cacheDao?.queryByArticleIdAndUrlSync(
                        articleId = articleId,
                        url = url,
                        type = ArticleImageCacheType.CONTENT,
                    )
                if (cache != null) {
                    val file = File(cache.localPath)
                    if (file.exists()) {
                        val mime =
                            URLConnection.guessContentTypeFromName(file.name) ?: "image/*"
                        return WebResourceResponse(mime, null, file.inputStream())
                    }
                }
            } catch (e: Exception) {
                Log.e("RLog", "shouldInterceptRequest cache: $e")
            }
        }
        if (url != null && url.contains(INJECTION_TOKEN)) {
            try {
                val assetPath = url.substring(
                    url.indexOf(INJECTION_TOKEN) + INJECTION_TOKEN.length,
                    url.length
                )
                return WebResourceResponse(
                    "text/HTML",
                    "UTF-8",
                    context.assets.open(assetPath)
                )
            } catch (e: Exception) {
                Log.e("RLog", "WebView shouldInterceptRequest: $e")
            }
        } else if (url != null && url.isUrl()) {
            try {
                var connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                if (connection.responseCode == 403) {
                    connection.disconnect()
                    connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                    connection.setRequestProperty("Referer", refererDomain)
                    val inputStream = DataInputStream(connection.inputStream)
                    return WebResourceResponse(connection.contentType, "UTF-8", inputStream)
                }
            } catch (e: Exception) {
                Log.e("RLog", "shouldInterceptRequest url: $e")
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (enableJavaScript) {
            view!!.evaluateJavascript(OnImgClickScript, null)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (null == request?.url) return false
        val url = request.url.toString()
        if (url.isNotEmpty()) onOpenLink(url)
        return true
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        Log.e("RLog", "RYWebView onReceivedError: $error")
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }

    @InstallIn(SingletonComponent::class)
    @EntryPoint
    interface ArticleImageCacheEntryPoint {
        fun articleImageCacheDao(): ArticleImageCacheDao
    }

    companion object {
        private const val OnImgClickScript = """
            javascript:(function() {
                var imgs = document.getElementsByTagName("img");
                for(var i = 0; i < imgs.length; i++){
                    imgs[i].pos = i;
                    imgs[i].onclick = function(event) {
                        event.preventDefault();
                        window.${JavaScriptInterface.NAME}.onImgTagClick(this.src, this.alt);
                    }
                }
            })()
            """
    }
}
