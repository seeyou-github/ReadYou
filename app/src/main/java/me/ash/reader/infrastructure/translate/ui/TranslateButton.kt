package me.ash.reader.infrastructure.translate.ui

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateManager
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.Settings
import me.ash.reader.infrastructure.translate.TranslateProvider
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateServiceFactory
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig

import me.ash.reader.ui.component.base.CanBeDisabledIconButton
import me.ash.reader.ui.ext.showToast
import timber.log.Timber

/**
 * 翻译状态
 */
sealed class TranslateState {
    /**
     * 空闲
     */
    object Idle : TranslateState()

    /**
     * 标记DOM中
     */
    object MarkingDOM : TranslateState()

    /**
     * 提取文本中
     */
    object ExtractingText : TranslateState()

    /**
     * 翻译中
     */
    object Translating : TranslateState()

    /**
     * 已翻译
     */
    object Translated : TranslateState()
}

/**
 * 翻译按钮组件（使用SSE流式翻译）
 *
 * 封装所有翻译逻辑，ReadingPage只需传入必要的数据
 *
 * @param webView WebView实例
 * @param streamTranslateServiceFactory 流式翻译服务工厂
 * @param currentTranslateServiceId 当前翻译服务ID
 * @param translateState 当前翻译状态
 * @param articleId 文章ID
 * @param onNavigateToAITranslation 导航到AI翻译设置页面的回调
 * @param onShowOriginal 显示原文的回调（已翻译状态下点击）
 * @param onTranslationComplete 翻译完成的回调（有缓存时直接显示译文）
 * @param onStartTranslation 开始翻译的回调（ViewModel中调用），传递 StreamTranslateManager
 * @param onCancelTranslation 取消翻译的回调（ViewModel中调用）
 * @param modifier 修饰符
 */
@Composable
fun TranslateButton(
    webView: WebView?,
    streamTranslateServiceFactory: StreamTranslateServiceFactory,
    currentTranslateServiceId: String,
    translateState: TranslateState,
    articleId: String?,
    onNavigateToAITranslation: () -> Unit,
    onShowOriginal: () -> Unit,
    onStartTranslation: (StreamTranslateManager) -> Unit,
    onCancelTranslation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val coroutineScope = rememberCoroutineScope()

val currentConfig = settings.quickTranslateModel ?: TranslateModelConfig(
    provider = TranslateProvider.SILICONFLOW.serviceId,
    model = "",
    apiKey = ""
)

    // 创建 StreamTranslateManager（使用SSE流式翻译）
    val streamTranslateManager = remember(webView, currentTranslateServiceId, currentConfig) {
        webView?.let {
            val streamService = streamTranslateServiceFactory.getService(currentTranslateServiceId)
            StreamTranslateManager(
                it,
                streamService,
                currentConfig
            )
        }
    }

    // 确定按钮图标、描述和颜色
    val isTranslating = translateState is TranslateState.Translating ||
            translateState is TranslateState.MarkingDOM ||
            translateState is TranslateState.ExtractingText

    val (imageVector, contentDescription) = if (isTranslating) {
        Icons.Outlined.Close to "停止翻译"
    } else if (translateState is TranslateState.Translated) {
        Icons.Outlined.Translate to "显示原文"
    } else {
        Icons.Outlined.Translate to "翻译"
    }

    // 颜色逻辑：已翻译时使用激活颜色，其他情况使用未激活颜色
    val buttonTint = if (translateState is TranslateState.Translated) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.outline
    }

    CanBeDisabledIconButton(
        modifier = modifier.size(40.dp),
        disabled = false,
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = buttonTint,
        onClick = {
            handleTranslateClick(
                context = context,
                translateState = translateState,
                articleId = articleId,
                settings = settings,
                streamTranslateManager = streamTranslateManager,
                coroutineScope = coroutineScope,
                onNavigateToAITranslation = onNavigateToAITranslation,
                onShowOriginal = onShowOriginal,
                onStartTranslation = onStartTranslation,
                onCancelTranslation = onCancelTranslation
            )
        }
    )
}

/**
 * 处理翻译按钮点击（SSE流式翻译）
 */
private fun handleTranslateClick(
    context: Context,
    translateState: TranslateState,
    articleId: String?,
    settings: Settings,
    streamTranslateManager: StreamTranslateManager?,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onNavigateToAITranslation: () -> Unit,
    onShowOriginal: () -> Unit,
    onStartTranslation: (StreamTranslateManager) -> Unit,
    onCancelTranslation: () -> Unit
) {
    Timber.d("[TranslateButton] 点击，状态: $translateState")

    val isTranslating = translateState is TranslateState.Translating ||
            translateState is TranslateState.MarkingDOM ||
            translateState is TranslateState.ExtractingText

    when {
        isTranslating -> {
            // 停止翻译
            Timber.d("[TranslateButton] 停止翻译")
            onCancelTranslation()
            context.showToast("翻译已取消")
        }
        translateState is TranslateState.Translated -> {
            // 显示原文
            Timber.d("[TranslateButton] 显示原文")
            onShowOriginal()
            context.showToast("显示原文")
        }
        else -> {
            // Idle 状态：检查是否已有翻译
            Timber.d("[TranslateButton] Idle状态，直接调用翻译方法，方法内部判断是否有翻译缓存")

            if (streamTranslateManager == null) {
                Timber.e("[TranslateButton] WebView未准备就绪")
                context.showToast("WebView未准备就绪")
                return
            }

            if (articleId == null) {
                Timber.e("[TranslateButton] 文章ID为空")
                context.showToast("没有加载文章")
                return
            }
            onStartTranslation(streamTranslateManager)

        }
    }
}
