package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
import android.net.Uri
import me.ash.reader.R
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight

import me.ash.reader.infrastructure.preference.not
import me.ash.reader.infrastructure.preference.CustomReaderThemesPreference


import android.webkit.WebView

import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.ui.component.reader.colorThemeToReaderPaints
import me.ash.reader.infrastructure.preference.LocalCustomReaderThemes
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import timber.log.Timber
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.TranslateProvider
import me.ash.reader.ui.component.dialogs.ReadingPageStyleDialog
import me.ash.reader.ui.component.reader.LocalReaderPaints
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.NavigationAction
import me.ash.reader.ui.page.adaptive.ReaderState
import me.ash.reader.ui.page.home.reading.tts.TtsButton
import me.ash.reader.infrastructure.translate.ui.TranslateButton
import me.ash.reader.infrastructure.translate.ui.TranslateErrorDialog
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateServiceFactory
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateManager
import me.ash.reader.infrastructure.translate.ui.TranslateState

private const val UPWARD = 1
private const val DOWNWARD = -1

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ReadingPage(
    //    navController: NavHostController,
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit = { }, // 2026-01-21: 修改为显示对话框而不是导航
    onNavigateToAITranslation: () -> Unit = { }, // 2026-01-30: 导航到AI翻译设置页面
    streamTranslateServiceFactory: StreamTranslateServiceFactory,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val isPullToSwitchArticleEnabled = LocalPullToSwitchArticle.current.value
    val readingUiState = viewModel.readingUiState.collectAsStateValue()
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val translationState = viewModel.translationStateStateFlow.collectAsStateValue()
    val boldCharacters = LocalReadingBoldCharacters.current
//    val topBarHeight = LocalFeedsTopBarHeight.current
    val coroutineScope = rememberCoroutineScope()

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }

    // 2026-01-21: 新增阅读界面样式设置对话框状态
    var showReadingPageStyleDialog by remember { mutableStateOf(false) }

    // 2026-01-21: 修改 onNavigateToStylePage 的行为，显示对话框而不是导航
    // 由于 onNavigateToStylePage 是一个参数，我们需要在调用时设置对话框状态
    // 这里我们使用 LaunchedEffect 来监听 onNavigateToStylePage 的调用
    // 但是由于 onNavigateToStylePage 是一个函数，我们无法直接监听它的调用
    // 所以我们需要修改 TopBar，让它传递一个设置对话框状态的回调

    // 临时解决方案：直接在 ReadingPage 中处理对话框的显示
    // 我们需要在 TopBar 中传递一个设置对话框状态的回调
    // 但是由于 TopBar 是一个独立的组件，我们需要修改它的接口

    // 为了简化，我们暂时保留 onNavigateToStylePage 参数，但在 ReadingPage 中忽略它
    // 直接在 ReadingPage 中添加一个按钮来显示对话框
    // 或者我们修改 TopBar，让它传递一个设置对话框状态的回调

    var currentImageData by remember { mutableStateOf(ImageData()) }
    
    // 2026-01-31: WebView引用（用于翻译等功能）
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 2026-02-02: 标记是否已触发过自动翻译（避免重复触发）
    var hasTriggeredAutoTranslate by remember { mutableStateOf(false) }

    val isShowToolBar =
        if (LocalReadingAutoHideToolbar.current.value) {
            readerState.articleId != null && !isReaderScrollingDown
        } else {
            true
        }

    var showTopDivider by remember { mutableStateOf(false) }

    //    LaunchedEffect(readerState.listIndex) {
    //        readerState.listIndex?.let {
    //            navController.previousBackStackEntry?.savedStateHandle?.set("articleIndex", it)
    //        }
    //    }

    var bringToTop by remember { mutableStateOf(false) }

    // 获取当前颜色主题并计算 ReaderPaints
    val isDarkTheme = LocalDarkTheme.current.isDarkTheme()
    val customReaderThemes = LocalCustomReaderThemes.current
    val currentColorTheme = customReaderThemes
        .filter { it.isDarkTheme == isDarkTheme }
        .firstOrNull { it.isDefault }
        // 如果没有找到默认主题，回退到默认值
        ?: CustomReaderThemesPreference.default.firstOrNull { it.isDarkTheme == isDarkTheme }


    // 2026-02-02: 在Composable上下文获取Settings和翻译服务ID（避免在LaunchedEffect中调用@Composable函数）
    val settings = LocalSettings.current
    val currentTranslateServiceId = viewModel.currentTranslateServiceId.value

    // 根据 currentColorTheme 设置 LocalReaderPaints
    CompositionLocalProvider(
        LocalReaderPaints provides (currentColorTheme?.let { colorThemeToReaderPaints(it) } ?: LocalReaderPaints.current)
    ) {
        Scaffold(
            containerColor = LocalReaderPaints.current.background,
            content = { paddings ->
            Box(modifier = Modifier.fillMaxSize()) {
                val displayTitle = when {
                    translationState.translateState != TranslateState.Idle &&
                        translationState.translatedTitle != null -> translationState.translatedTitle
                    else -> readerState.title
                }

                if (readerState.articleId != null) {
                    TopBar(
                        isShow = isShowToolBar,
                        isScrolled = showTopDivider,
                        title = displayTitle,
                        link = readerState.link,
                        onClick = { bringToTop = true },
                        navigationAction = navigationAction,
                        onNavButtonClick = onNavAction,
                        onNavigateToStylePage = onNavigateToStylePage,
//                        height = topBarHeight,
                    )
                }

                val isNextArticleAvailable = readerState.nextArticle != null
                val isPreviousArticleAvailable = readerState.previousArticle != null

                if (readerState.articleId != null) {
                    // ContentKey 用于控制 AnimatedContent 的动画
                    // 只有当文章ID或内容变化时才触发动画，翻译状态变化不会触发
                    data class ContentKey(
                        val articleId: String?,
                        val content: ReaderState.ContentState,
                        val nextArticle: ReaderState.PrefetchResult?,
                        val previousArticle: ReaderState.PrefetchResult?
                    )
                    val contentKey = ContentKey(
                        readerState.articleId,
                        readerState.content,
                        readerState.nextArticle,
                        readerState.previousArticle
                    )

                    // Content
                    AnimatedContent(
                        targetState = contentKey,
                        transitionSpec = {
                            val direction =
                                when {
                                    initialState.nextArticle?.articleId == targetState.articleId ->
                                        UPWARD
                                    initialState.previousArticle?.articleId ==
                                        targetState.articleId -> DOWNWARD
                                    initialState.articleId == targetState.articleId -> {
                                        when (targetState.content) {
                                            is ReaderState.Description -> DOWNWARD
                                            else -> UPWARD
                                        }
                                    }

                                    else -> UPWARD
                                }
                            val exit = 100
                            val enter = exit * 2
                            (slideInVertically(
                                initialOffsetY = { (it * 0.2f * direction).toInt() },
                                animationSpec =
                                    spring(
                                        dampingRatio = .9f,
                                        stiffness = Spring.StiffnessLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                            ) +
                                fadeIn(
                                    tween(
                                        delayMillis = exit,
                                        durationMillis = enter,
                                        easing = LinearOutSlowInEasing,
                                    )
                                )) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { (it * -0.2f * direction).toInt() },
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                            visibilityThreshold = IntOffset.VisibilityThreshold,
                                        ),
                                ) +
                                    fadeOut(
                                        tween(durationMillis = exit, easing = FastOutLinearInEasing)
                                    ))
                        },
                        label = "",
                    ) {
                        remember { it }
                            .run {
                                val state =
                                    rememberPullToLoadState(
                                        key = content,
                                        onLoadNext =
                                            if (isNextArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.nextArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                        onLoadPrevious =
                                            if (isPreviousArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.previousArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                    )

                                val listState =
                                    rememberSaveable(
                                        inputs = arrayOf(content),
                                        saver = LazyListState.Saver,
                                    ) {
                                        LazyListState()
                                    }

                                val scrollState = rememberScrollState()

                                val scope = rememberCoroutineScope()

                                LaunchedEffect(bringToTop) {
                                    if (bringToTop) {
                                        scope
                                            .launch {
                                                if (scrollState.value != 0) {
                                                    scrollState.animateScrollTo(0)
                                                } else if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion { bringToTop = false }
                                    }
                                }

                                // 2026-02-02: 自动翻译触发逻辑
                                // 只在文章ID变化时触发（打开文章、切换文章）
                                LaunchedEffect(readerState.articleId) {
                                    Timber.tag("AutoTranslate").d("=== 自动翻译触发检查 ===")
                                    Timber.tag("AutoTranslate").d("文章ID: ${readerState.articleId}")
                                    
                                    // 检查文章ID
                                    val currentArticleId = readerState.articleId
                                    if (currentArticleId == null) {
                                        Timber.tag("AutoTranslate").d("文章ID为空，跳过")
                                        return@LaunchedEffect
                                    }
                                    
                                    // 重置标记
                                    hasTriggeredAutoTranslate = false
                                    Timber.tag("AutoTranslate").d("重置自动翻译标记")
                                    
                                    // 获取Feed信息
                                    val currentFeed = readingUiState.articleWithFeed?.feed
                                    val feedId = currentFeed?.id
                                    val feedName = currentFeed?.name
                                    val isAutoTranslate = currentFeed?.isAutoTranslate ?: false
                                    Timber.tag("AutoTranslate").d("isAutoTranslate = $isAutoTranslate")
                                    
                                    // 检查isAutoTranslate
                                    if (!isAutoTranslate) {
                                        Timber.tag("AutoTranslate").d("isAutoTranslate为false，跳过")
                                        return@LaunchedEffect
                                    }
                                    
                                    // 检查WebView
                                    val webView = webViewRef
                                    Timber.tag("AutoTranslate").d("webView != null = ${webView != null}")
                                    if (webView == null) {
                                        Timber.tag("AutoTranslate").d("WebView未就绪，跳过")
                                        return@LaunchedEffect
                                    }
                                    
                                    // 触发翻译
                                    Timber.tag("AutoTranslate").d("触发自动翻译")
                                    Timber.tag("AutoTranslate").d("Article ID: $currentArticleId")
                                    Timber.tag("AutoTranslate").d("Feed ID: $feedId")
                                    Timber.tag("AutoTranslate").d("Feed Name: $feedName")
                                    
                                    try {
                                        Timber.tag("AutoTranslate").d("currentTranslateServiceId = $currentTranslateServiceId")
                                        
                                        val streamService = streamTranslateServiceFactory.getService(currentTranslateServiceId)
                                        Timber.tag("AutoTranslate").d("streamService = ${streamService.javaClass.simpleName}")
                                        
                                        val currentConfig = settings.quickTranslateModel ?: TranslateModelConfig(
                                            provider = TranslateProvider.SILICONFLOW.serviceId,
                                            model = "",
                                            apiKey = ""
                                        )
                                        Timber.tag("AutoTranslate").d("TranslateModelConfig provider = ${currentConfig.provider}")
                                        Timber.tag("AutoTranslate").d("TranslateModelConfig model = ${currentConfig.model}")
                                        
                                        val manager = StreamTranslateManager(webView, streamService, currentConfig)
                                        Timber.tag("AutoTranslate").d("StreamTranslateManager created successfully")
                                        
                                        viewModel.startTranslation(manager)
                                        hasTriggeredAutoTranslate = true
                                        Timber.tag("AutoTranslate").d("调用 viewModel.startTranslation() 成功")
                                        Timber.tag("AutoTranslate").d("=== 自动翻译触发完成 ===")
                                    } catch (e: Exception) {
                                        Timber.tag("AutoTranslate").e(e, "自动翻译触发失败")
                                        Timber.tag("AutoTranslate").d("=== 自动翻译触发失败 ===")
                                    }
                                }

                                showTopDivider =
                                    snapshotFlow {
                                            scrollState.value >= 120 ||
                                                listState.firstVisibleItemIndex != 0
                                        }
                                        .collectAsStateValue(initial = false)

                                CompositionLocalProvider(
                                    LocalTextStyle provides
                                        LocalTextStyle.current.run {
                                            merge(
                                                lineHeight =
                                                    if (lineHeight.isSpecified)
                                                        (lineHeight.value *
                                                                LocalReadingTextLineHeight.current)
                                                            .sp
                                                    else TextUnit.Unspecified
                                            )
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // 打开文章时，只要有译文的dom就加载
                                        val displayContent = when {
                                            translationState.translateState == TranslateState.Translated &&
                                                translationState.translatedContent != null -> translationState.translatedContent
                                            else -> readerState.content
                                        }

                                        Content(
                                            modifier =
                                                Modifier.pullToLoad(
                                                    state = state,
                                                    onScroll = { f ->
                                                        if (abs(f) > 2f)
                                                            isReaderScrollingDown = f < 0f
                                                    },
                                                    enabled = isPullToSwitchArticleEnabled,
                                                ),
                                            contentPadding = paddings,
                                              content = displayContent.text ?: "",
                                              feedName = readerState.feedName,
                                              title = displayTitle ?: "",
                                              author = readerState.author,
                                              link = readerState.link,
                                              publishedDate = readerState.publishedDate,
                                              isLoading = readerState.content is ReaderState.Loading,
                                              scrollState = scrollState,
                                              listState = listState,
                                              disableJavaScript = readingUiState.articleWithFeed?.feed?.isDisableJavaScript ?: false,
                                              onImageClick = { imgUrl, altText ->
                                                  currentImageData = ImageData(imgUrl, altText)
                                                  showFullScreenImageViewer = true
                                              },
                                            onWebViewReady = { webView ->
                                                webViewRef = webView
                                                // 2026-01-31: WebView准备就绪，后续缓存恢复由TranslateButton处理
                                            },
                                        )
                                        PullToLoadIndicator(
                                            state = state,
                                            canLoadPrevious = isPreviousArticleAvailable,
                                            canLoadNext = isNextArticleAvailable,
                                        )
                                    }
                                }
                            }
                    }
                }
                // Bottom Bar
                if (readerState.articleId != null) {
                    BottomBar(
                        isShow = isShowToolBar,
                        isUnread = readingUiState.isUnread,
                        isStarred = readingUiState.isStarred,
                        isNextArticleAvailable = isNextArticleAvailable,
                        isFullContent =
                            readerState.content is ReaderState.FullContent ||
                                readerState.content is ReaderState.Error,
                        isBoldCharacters = boldCharacters.value,
//                        onUnread = { viewModel.updateReadStatus(it) },
//                        onStarred = { viewModel.updateStarredStatus(it) },
                        onNextArticle = {
                            readerState.nextArticle?.let {
                                val (id, index) = it
                                onLoadArticle(id, index)
                            }
                        },
                        onFullContent = {
                            if (it) viewModel.renderFullContent()
                            else viewModel.renderDescriptionContent()
                        },
                        onBoldCharacters = { (!boldCharacters).put(context, coroutineScope) },
                        onReadAloud = {
                            viewModel.textToSpeechManager.readHtml(
                                readerState.content.text ?: return@BottomBar
                            )
                        },
                        // 2026-01-31: 使用新的TranslateButton组件，封装所有翻译逻辑
                        translateButton = {
                            TranslateButton(
                                webView = webViewRef,
                                streamTranslateServiceFactory = streamTranslateServiceFactory,
                                currentTranslateServiceId = viewModel.currentTranslateServiceId.collectAsStateValue(),
                                translateState = translationState.translateState,
                                articleId = readerState.articleId,
                                onNavigateToAITranslation = onNavigateToAITranslation,
                                onShowOriginal = { viewModel.showOriginal() },
                                onStartTranslation = { manager -> viewModel.startTranslation(manager) },
                                onCancelTranslation = { viewModel.cancelTranslation() }
                            )
                        },
                        ttsButton = {
                            TtsButton(
                                onClick = {
                                    when (it) {
                                        TextToSpeechManager.State.Error -> {
                                            context.showToast("TextToSpeech initialization failed")
                                        }

                                        TextToSpeechManager.State.Idle -> {
                                            viewModel.textToSpeechManager.readHtml(
                                                readerState.content.text ?: ""
                                            )
                                        }

                                        is TextToSpeechManager.State.Reading -> {
                                            viewModel.textToSpeechManager.stop()
                                        }

                                        TextToSpeechManager.State.Preparing -> {
                                            /* no-op */
                                        }
                                    }
                                },
                                state =
                                    viewModel.textToSpeechManager.stateFlow.collectAsStateValue(),
                            )
                        },


                        )
                }
            }
            },
        )
    }
    if (showFullScreenImageViewer) {

        ReaderImageViewer(
            imageData = currentImageData,
            onDownloadImage = { url ->
                viewModel.downloadImage(
                    url,
                    onSuccess = { uri: Uri -> 
                        context.showToast(context.getString(R.string.image_saved)) 
                    },
                    onFailure = { throwable: Throwable ->
                        throw throwable
                    },
                )
            },
            onDismissRequest = { showFullScreenImageViewer = false },
        )
    }

    // 2026-01-21: 新增阅读界面样式设置对话框
    if (showReadingPageStyleDialog) {
        ReadingPageStyleDialog(
            onDismiss = { showReadingPageStyleDialog = false },
            context = context,
            scope = coroutineScope
        )
    }

    // 2026-01-30: 翻译错误对话框
    translationState.translateError?.let { error ->
        TranslateErrorDialog(
            errorMessage = error,
            onDismiss = { viewModel.dismissTranslateError() },
            onNavigateToSettings = onNavigateToAITranslation
        )
    }
}
