package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import me.ash.reader.infrastructure.translate.preference.CerebrasConfigPreference

import me.ash.reader.infrastructure.translate.preference.QuickTranslateModelPreference
import me.ash.reader.infrastructure.translate.preference.SiliconFlowConfigPreference

sealed class Preference {

    abstract fun put(context: Context, scope: CoroutineScope)
}

fun Preferences.toSettings(): Settings {
    return Settings(
        // Version
        newVersionNumber = NewVersionNumberPreference.fromPreferences(this),
        skipVersionNumber = SkipVersionNumberPreference.fromPreferences(this),
        newVersionPublishDate = NewVersionPublishDatePreference.fromPreferences(this),
        newVersionLog = NewVersionLogPreference.fromPreferences(this),
        newVersionSize = NewVersionSizePreference.fromPreferences(this),
        newVersionDownloadUrl = NewVersionDownloadUrlPreference.fromPreferences(this),

        // Theme
        themeIndex = ThemeIndexPreference.fromPreferences(this),
        customPrimaryColor = CustomPrimaryColorPreference.fromPreferences(this),
        darkTheme = DarkThemePreference.fromPreferences(this),
        amoledDarkTheme = AmoledDarkThemePreference.fromPreferences(this),
        basicFonts = BasicFontsPreference.fromPreferences(this),
        customReaderThemes = CustomReaderThemesPreference.fromPreferences(this),
//        selectedReaderThemeId = SelectedReaderThemeIdPreference.fromPreferences(this),

        // Feeds page
        feedsFilterBarStyle = FeedsFilterBarStylePreference.fromPreferences(this),
        feedsFilterBarPadding = FeedsFilterBarPaddingPreference.fromPreferences(this),
        feedsFilterBarTonalElevation = FeedsFilterBarTonalElevationPreference.fromPreferences(this),
        feedsFilterBarHeight = FeedsFilterBarHeightPreference.fromPreferences(this),
        feedsTopBarHeight = FeedsTopBarHeightPreference.fromPreferences(this),
        feedsTopBarTonalElevation = FeedsTopBarTonalElevationPreference.fromPreferences(this),
        feedsGroupListExpand = FeedsGroupListExpandPreference.fromPreferences(this),
        feedsGroupListTonalElevation = FeedsGroupListTonalElevationPreference.fromPreferences(this),
        feedsLayoutStyle = FeedsLayoutStylePreference.fromPreferences(this),
        feedsPageColorThemes = FeedsPageColorThemesPreference.fromPreferences(this),
        // 2026-01-21: 新增订阅源图标样式设置
        feedsIconBrightness = FeedsIconBrightnessPreference.fromPreferences(this),
        feedsGridColumnCount = FeedsGridColumnCountPreference.fromPreferences(this),
        feedsGridRowSpacing = FeedsGridRowSpacingPreference.fromPreferences(this),
        feedsGridIconSize = FeedsGridIconSizePreference.fromPreferences(this),
        feedsListItemHeight = FeedsListItemHeightPreference.fromPreferences(this),
        // 2026-01-23: 加载列表视图列表边距
        // 修改原因：从持久化存储中读取列表视图列表边距设置
        feedsListItemPadding = FeedsListItemPaddingPreference.fromPreferences(this),

        // Flow page
        flowFilterBarStyle = FlowFilterBarStylePreference.fromPreferences(this),
        flowFilterBarPadding = FlowFilterBarPaddingPreference.fromPreferences(this),
        flowFilterBarTonalElevation = FlowFilterBarTonalElevationPreference.fromPreferences(this),
        flowTopBarTonalElevation = FlowTopBarTonalElevationPreference.fromPreferences(this),
        // 2026-01-21: 新增过滤栏自动隐藏功能
        flowFilterBarAutoHide = FlowFilterBarAutoHidePreference.fromPreferences(this),
        flowArticleListFeedIcon = FlowArticleListFeedIconPreference.fromPreferences(this),
        flowArticleListFeedName = FlowArticleListFeedNamePreference.fromPreferences(this),
        flowArticleListImage = FlowArticleListImagePreference.fromPreferences(this),
        flowArticleListDesc = FlowArticleListDescPreference.fromPreferences(this),
        flowArticleListTime = FlowArticleListTimePreference.fromPreferences(this),
        flowArticleListDateStickyHeader = FlowArticleListDateStickyHeaderPreference.fromPreferences(
            this
        ),
        flowArticleListReadIndicator = FlowArticleReadIndicatorPreference.fromPreferences(this),
        flowArticleListTonalElevation = FlowArticleListTonalElevationPreference.fromPreferences(this),
        flowSortUnreadArticles = SortUnreadArticlesPreference.fromPreferences(this),
        // 2026-01-27: 新增首行大图模式设置
        flowArticleListFirstItemLargeImage = FlowArticleListFirstItemLargeImagePreference.fromPreferences(this),
        // 2026-01-18: 新增文章列表样式设置相关的Preference映射
        flowArticleListTitleFontSize = FlowArticleListTitleFontSizePreference.fromPreferences(this),
        flowArticleListTitleLineHeight = FlowArticleListTitleLineHeightPreference.fromPreferences(this),
        flowArticleListHorizontalPadding = FlowArticleListHorizontalPaddingPreference.fromPreferences(this),
        flowArticleListVerticalPadding = FlowArticleListVerticalPaddingPreference.fromPreferences(this),
        flowArticleListImageRoundedCorners = FlowArticleListImageRoundedCornersPreference.fromPreferences(this),
        flowArticleListImageSize = FlowArticleListImageSizePreference.fromPreferences(this),
        flowArticleListRoundedCorners = FlowArticleListRoundedCornersPreference.fromPreferences(this),
        flowArticleListItemSpacing = FlowArticleListItemSpacingPreference.fromPreferences(this),
        flowArticleListColorThemes = FlowArticleListColorThemesPreference.fromPreferences(this),

        // Reading page
        readingRenderer = ReadingRendererPreference.fromPreferences(this),
        readingBoldCharacters = ReadingBoldCharactersPreference.fromPreferences(this),
        readingTheme = ReadingThemePreference.fromPreferences(this),
        readingPageTonalElevation = ReadingPageTonalElevationPreference.fromPreferences(this),
        readingAutoHideToolbar = ReadingAutoHideToolbarPreference.fromPreferences(this),
        readingTextFontSize = ReadingTextFontSizePreference.fromPreferences(this),
        readingTextLineHeight = ReadingTextLineHeightPreference.fromPreferences(this),
        readingLetterSpacing = ReadingTextLetterSpacingPreference.fromPreferences(this),
        readingTextHorizontalPadding = ReadingTextHorizontalPaddingPreference.fromPreferences(this),
        readingTextAlign = ReadingTextAlignPreference.fromPreferences(this),
        readingTextBold = ReadingTextBoldPreference.fromPreferences(this),
        readingTitleAlign = ReadingTitleAlignPreference.fromPreferences(this),
        readingSubheadAlign = ReadingSubheadAlignPreference.fromPreferences(this),
        readingFonts = ReadingFontsPreference.fromPreferences(this),
        readingTitleBold = ReadingTitleBoldPreference.fromPreferences(this),
        readingSubheadBold = ReadingSubheadBoldPreference.fromPreferences(this),
        readingTitleUpperCase = ReadingTitleUpperCasePreference.fromPreferences(this),
        readingSubheadUpperCase = ReadingSubheadUpperCasePreference.fromPreferences(this),
        readingImageHorizontalPadding = ReadingImageHorizontalPaddingPreference.fromPreferences(this),
        readingImageRoundedCorners = ReadingImageRoundedCornersPreference.fromPreferences(this),
        readingImageBrightness = ReadingImageBrightnessPreference.fromPreferences(this),
        readingImageMaximize = ReadingImageMaximizePreference.fromPreferences(this),
        // 2026-01-24: 新增阅读页面标题样式设置相关的 Preference 映射
        // 修改原因：支持标题字体大小、颜色、左右边距的持久化读取
        readingTitleFontSize = ReadingTitleFontSizePreference.fromPreferences(this),
        readingTitleColor = ReadingTitleColorPreference.fromPreferences(this),
        readingTitleHorizontalPadding = ReadingTitleHorizontalPaddingPreference.fromPreferences(this),

        // Interaction
        initialPage = InitialPagePreference.fromPreferences(this),
        initialFilter = InitialFilterPreference.fromPreferences(this),
        swipeStartAction = SwipeStartActionPreference.fromPreferences(this),
        swipeEndAction = SwipeEndActionPreference.fromPreferences(this),
        markAsReadOnScroll = MarkAsReadOnScrollPreference.fromPreferences(this),
        hideEmptyGroups = HideEmptyGroupsPreference.fromPreferences(this),
        pullToSwitchFeed = PullToLoadNextFeedPreference.fromPreference(this),
        pullToSwitchArticle = PullToSwitchArticlePreference.fromPreference(this),
        openLink = OpenLinkPreference.fromPreferences(this),
        openLinkSpecificBrowser = OpenLinkSpecificBrowserPreference.fromPreferences(this),
        sharedContent = SharedContentPreference.fromPreferences(this),

        // Languages
        languages = LanguagesPreference.fromPreferences(this),

        // Translate
        quickTranslateModel = QuickTranslateModelPreference.fromPreferences(this),

        siliconFlowConfig = SiliconFlowConfigPreference.fromPreferences(this),
        cerebrasConfig = CerebrasConfigPreference.fromPreferences(this),
    )
}
