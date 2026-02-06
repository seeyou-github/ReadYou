package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.datastore.get
import me.ash.reader.infrastructure.datastore.getOrDefault
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.translate.preference.LocalCerebrasConfig

import me.ash.reader.infrastructure.translate.preference.LocalQuickTranslateModel
import me.ash.reader.infrastructure.translate.preference.LocalSiliconFlowConfig
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.dataStore
// 2026-01-23: 导入列表视图列表边距 Preference
import me.ash.reader.infrastructure.translate.preference.LocalTranslateServiceId
import javax.inject.Inject

class SettingsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope coroutineScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher
) {
    private val _settingsFlow = MutableStateFlow(Settings())
    val settingsFlow: StateFlow<Settings> = _settingsFlow
    val settings: Settings get() = settingsFlow.value

    val dataStore = context.dataStore.data

    val preferencesFlow: StateFlow<Preferences> =
        dataStore.stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesOf()
        )

    val preferences get() = preferencesFlow.value

    inline fun <reified T> get(key: Preferences.Key<T>): T? = preferences[key]

    inline fun <reified T> get(key: String): T? = preferences.get(key)

    inline fun <reified T> getOrDefault(key: String, default: T): T =
        preferences.getOrDefault(key, default) ?: default


    init {
        coroutineScope.launch(ioDispatcher) {
            preferencesFlow.collect {
                _settingsFlow.value = it.toSettings()
            }
        }
    }

    @Composable
    fun ProvidesSettings(content: @Composable () -> Unit) {
        val settings = settingsFlow.collectAsStateValue()
        CompositionLocalProvider(
            LocalSettings provides settings,

            // Version
            NewVersionNumberPreference.provide(settings),
            LocalSkipVersionNumber provides settings.skipVersionNumber,
            LocalNewVersionPublishDate provides settings.newVersionPublishDate,
            LocalNewVersionLog provides settings.newVersionLog,
            LocalNewVersionSize provides settings.newVersionSize,
            LocalNewVersionDownloadUrl provides settings.newVersionDownloadUrl,
            LocalBasicFonts provides settings.basicFonts,

            // Theme
            LocalThemeIndex provides settings.themeIndex,
            LocalCustomPrimaryColor provides settings.customPrimaryColor,
            LocalDarkTheme provides settings.darkTheme,
            LocalAmoledDarkTheme provides settings.amoledDarkTheme,
            LocalBasicFonts provides settings.basicFonts,

            // Feeds page
            LocalFeedsTopBarTonalElevation provides settings.feedsTopBarTonalElevation,
            LocalFeedsGroupListExpand provides settings.feedsGroupListExpand,
            LocalFeedsGroupListTonalElevation provides settings.feedsGroupListTonalElevation,
            LocalFeedsFilterBarStyle provides settings.feedsFilterBarStyle,
            LocalFeedsFilterBarPadding provides settings.feedsFilterBarPadding,
            LocalFeedsFilterBarTonalElevation provides settings.feedsFilterBarTonalElevation,
            LocalFeedsFilterBarHeight provides settings.feedsFilterBarHeight,
            LocalFeedsTopBarHeight provides settings.feedsTopBarHeight,
            LocalFeedsLayoutStyle provides settings.feedsLayoutStyle,
            LocalFeedsPageColorThemes provides settings.feedsPageColorThemes,
            LocalFeedsPageColorTheme provides settings.feedsPageColorThemes.firstOrNull { it.isDefault },
            // 2026-01-21: 新增订阅源图标样式设置
            LocalFeedsIconBrightness provides settings.feedsIconBrightness,
            LocalFeedsGridColumnCount provides settings.feedsGridColumnCount,
            LocalFeedsGridRowSpacing provides settings.feedsGridRowSpacing,
            LocalFeedsGridIconSize provides settings.feedsGridIconSize,
            LocalFeedsListItemHeight provides settings.feedsListItemHeight,
            // 2026-01-23: 提供列表视图列表边距设置
            // 修改原因：在 Compose 中提供列表视图列表边距的访问
            LocalFeedsListItemPadding provides settings.feedsListItemPadding,

            // Flow page
            LocalFlowTopBarTonalElevation provides settings.flowTopBarTonalElevation,
            LocalFlowArticleListFeedIcon provides settings.flowArticleListFeedIcon,
            LocalFlowArticleListFeedName provides settings.flowArticleListFeedName,
            LocalFlowArticleListImage provides settings.flowArticleListImage,
            LocalFlowArticleListDesc provides settings.flowArticleListDesc,
            LocalFlowArticleListTime provides settings.flowArticleListTime,
            LocalFlowArticleListDateStickyHeader provides settings.flowArticleListDateStickyHeader,
            LocalFlowArticleListTonalElevation provides settings.flowArticleListTonalElevation,
            LocalFlowFilterBarStyle provides settings.flowFilterBarStyle,
            LocalFlowFilterBarPadding provides settings.flowFilterBarPadding,
            LocalFlowFilterBarTonalElevation provides settings.flowFilterBarTonalElevation,
            // 2026-01-21: 新增过滤栏自动隐藏功能
            LocalFlowFilterBarAutoHide provides settings.flowFilterBarAutoHide,
            LocalFlowArticleListReadIndicator provides settings.flowArticleListReadIndicator,
            LocalSortUnreadArticles provides settings.flowSortUnreadArticles,
            // 2026-01-27: 新增首行大图模式设置
            LocalFlowArticleListFirstItemLargeImage provides settings.flowArticleListFirstItemLargeImage,
            // 2026-01-18: 新增文章列表样式设置相关的LocalSettings
            LocalFlowArticleListTitleFontSize provides settings.flowArticleListTitleFontSize,
            LocalFlowArticleListTitleLineHeight provides settings.flowArticleListTitleLineHeight,
            LocalFlowArticleListHorizontalPadding provides settings.flowArticleListHorizontalPadding,
            LocalFlowArticleListVerticalPadding provides settings.flowArticleListVerticalPadding,
            LocalFlowArticleListImageRoundedCorners provides settings.flowArticleListImageRoundedCorners,
            LocalFlowArticleListImageSize provides settings.flowArticleListImageSize,
            LocalFlowArticleListRoundedCorners provides settings.flowArticleListRoundedCorners,
            LocalFlowArticleListItemSpacing provides settings.flowArticleListItemSpacing,
            LocalFlowArticleListColorThemes provides settings.flowArticleListColorThemes,

            // Reading page
            LocalReadingRenderer provides settings.readingRenderer,
            LocalReadingBoldCharacters provides settings.readingBoldCharacters,
            LocalReadingTheme provides settings.readingTheme,
            LocalReadingPageTonalElevation provides settings.readingPageTonalElevation,
            LocalReadingAutoHideToolbar provides settings.readingAutoHideToolbar,
            LocalReadingTextFontSize provides settings.readingTextFontSize,
            LocalReadingTextLineHeight provides settings.readingTextLineHeight,
            LocalReadingTextLetterSpacing provides settings.readingLetterSpacing,
            LocalReadingTextHorizontalPadding provides settings.readingTextHorizontalPadding,
            LocalReadingTextAlign provides settings.readingTextAlign,
            LocalReadingTextBold provides settings.readingTextBold,
            LocalReadingTitleAlign provides settings.readingTitleAlign,
            LocalReadingSubheadAlign provides settings.readingSubheadAlign,
            LocalReadingFonts provides settings.readingFonts,
            LocalReadingTitleBold provides settings.readingTitleBold,
            LocalReadingSubheadBold provides settings.readingSubheadBold,
            LocalReadingTitleUpperCase provides settings.readingTitleUpperCase,
            LocalReadingSubheadUpperCase provides settings.readingSubheadUpperCase,
            LocalReadingImageHorizontalPadding provides settings.readingImageHorizontalPadding,
            LocalReadingImageRoundedCorners provides settings.readingImageRoundedCorners,
            LocalReadingImageBrightness provides settings.readingImageBrightness,
            LocalReadingImageMaximize provides settings.readingImageMaximize,
            LocalCustomReaderThemes provides settings.customReaderThemes,
            LocalCustomReaderTheme provides settings.customReaderThemes.firstOrNull { it.isDefault },
            // 2026-01-24: 新增阅读页面标题样式设置的 CompositionLocal 提供
            // 修改原因：在 Compose 中提供标题字体大小、颜色、左右边距的访问
            LocalReadingTitleFontSize provides settings.readingTitleFontSize,
            LocalReadingTitleColor provides settings.readingTitleColor,
            LocalReadingTitleHorizontalPadding provides settings.readingTitleHorizontalPadding,

            // Translate
            LocalTranslateServiceId provides settings.translateServiceId,
            LocalQuickTranslateModel provides settings.quickTranslateModel,

            LocalSiliconFlowConfig provides settings.siliconFlowConfig,
            LocalCerebrasConfig provides settings.cerebrasConfig,

            // Interaction
            LocalInitialPage provides settings.initialPage,
            LocalInitialFilter provides settings.initialFilter,
            LocalArticleListSwipeStartAction provides settings.swipeStartAction,
            LocalArticleListSwipeEndAction provides settings.swipeEndAction,
            LocalMarkAsReadOnScroll provides settings.markAsReadOnScroll,
            LocalHideEmptyGroups provides settings.hideEmptyGroups,
            LocalPullToSwitchArticle provides settings.pullToSwitchArticle,
            LocalOpenLink provides settings.openLink,
            LocalOpenLinkSpecificBrowser provides settings.openLinkSpecificBrowser,
            LocalSharedContent provides settings.sharedContent,

            // Cache
            LocalCacheTitleImageOnUpdate provides settings.cacheTitleImageOnUpdate,
            LocalCacheContentImageOnUpdate provides settings.cacheContentImageOnUpdate,

            // Languages
            LocalLanguages provides settings.languages,
        ) {
            content()
        }
    }
}
