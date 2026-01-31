package me.ash.reader.infrastructure.preference

import androidx.compose.runtime.compositionLocalOf
import me.ash.reader.domain.model.general.Version
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.preference.TranslateServiceIdPreference

val LocalSettings = compositionLocalOf { Settings() }

data class Settings(
    // Version
    val newVersionNumber: Version = NewVersionNumberPreference.default,
    val skipVersionNumber: Version = SkipVersionNumberPreference.default,
    val newVersionPublishDate: String = NewVersionPublishDatePreference.default,
    val newVersionLog: String = NewVersionLogPreference.default,
    val newVersionSize: String = NewVersionSizePreference.default,
    val newVersionDownloadUrl: String = NewVersionDownloadUrlPreference.default,

    // Theme
    val themeIndex: Int = ThemeIndexPreference.default,
    val customPrimaryColor: String = CustomPrimaryColorPreference.default,
    val darkTheme: DarkThemePreference = DarkThemePreference.default,
    val amoledDarkTheme: AmoledDarkThemePreference = AmoledDarkThemePreference.default,
    val basicFonts: BasicFontsPreference = BasicFontsPreference.default,
    val customReaderThemes: List<me.ash.reader.domain.model.theme.ColorTheme> = CustomReaderThemesPreference.default,
//    val selectedReaderThemeId: String = SelectedReaderThemeIdPreference.default,

    // Feeds page
    val feedsFilterBarStyle: FeedsFilterBarStylePreference = FeedsFilterBarStylePreference.default,
    val feedsFilterBarPadding: Int = FeedsFilterBarPaddingPreference.default,
    val feedsFilterBarTonalElevation: FeedsFilterBarTonalElevationPreference = FeedsFilterBarTonalElevationPreference.default,
    val feedsFilterBarHeight: Int = FeedsFilterBarHeightPreference.default,
    val feedsTopBarHeight: Int = FeedsTopBarHeightPreference.default,
    val feedsTopBarTonalElevation: FeedsTopBarTonalElevationPreference = FeedsTopBarTonalElevationPreference.default,
    val feedsGroupListExpand: FeedsGroupListExpandPreference = FeedsGroupListExpandPreference.default,
    val feedsGroupListTonalElevation: FeedsGroupListTonalElevationPreference = FeedsGroupListTonalElevationPreference.default,
    val feedsLayoutStyle: FeedsLayoutStylePreference = FeedsLayoutStylePreference.default,
    val feedsPageColorThemes: List<me.ash.reader.domain.model.theme.ColorTheme> = FeedsPageColorThemesPreference.default,
    // 2026-01-21: 新增订阅源图标样式设置
    val feedsIconBrightness: Int = FeedsIconBrightnessPreference.default,
    val feedsGridColumnCount: Int = FeedsGridColumnCountPreference.default,
    val feedsGridRowSpacing: Int = FeedsGridRowSpacingPreference.default,
    val feedsGridIconSize: Int = FeedsGridIconSizePreference.default,
    val feedsListItemHeight: Int = FeedsListItemHeightPreference.default,
    // 2026-01-23: 新增列表视图列表边距字段
    // 修改原因：在 Settings 中存储列表视图列表边距设置
    val feedsListItemPadding: Int = FeedsListItemPaddingPreference.default,

    // Flow page
    val flowFilterBarStyle: FlowFilterBarStylePreference = FlowFilterBarStylePreference.default,
    val flowFilterBarPadding: Int = FlowFilterBarPaddingPreference.default,
    val flowFilterBarTonalElevation: FlowFilterBarTonalElevationPreference = FlowFilterBarTonalElevationPreference.default,
    val flowTopBarTonalElevation: FlowTopBarTonalElevationPreference = FlowTopBarTonalElevationPreference.default,
    // 2026-01-21: 新增过滤栏自动隐藏功能
    val flowFilterBarAutoHide: FlowFilterBarAutoHidePreference = FlowFilterBarAutoHidePreference.default,
    val flowArticleListFeedIcon: FlowArticleListFeedIconPreference = FlowArticleListFeedIconPreference.default,
    val flowArticleListFeedName: FlowArticleListFeedNamePreference = FlowArticleListFeedNamePreference.default,
    val flowArticleListImage: FlowArticleListImagePreference = FlowArticleListImagePreference.default,
    val flowArticleListDesc: FlowArticleListDescPreference = FlowArticleListDescPreference.default,
    val flowArticleListTime: FlowArticleListTimePreference = FlowArticleListTimePreference.default,
    val flowArticleListDateStickyHeader: FlowArticleListDateStickyHeaderPreference = FlowArticleListDateStickyHeaderPreference.default,
    val flowArticleListTonalElevation: FlowArticleListTonalElevationPreference = FlowArticleListTonalElevationPreference.default,
    val flowArticleListReadIndicator: FlowArticleReadIndicatorPreference = FlowArticleReadIndicatorPreference.default,
    val flowSortUnreadArticles: SortUnreadArticlesPreference = SortUnreadArticlesPreference.default,
    // 2026-01-27: 新增首行大图模式设置
    val flowArticleListFirstItemLargeImage: FlowArticleListFirstItemLargeImagePreference = FlowArticleListFirstItemLargeImagePreference.default,
    // 2026-01-18: 新增文章列表样式设置相关的字段
    val flowArticleListTitleFontSize: Int = FlowArticleListTitleFontSizePreference.default,
    val flowArticleListTitleLineHeight: Float = FlowArticleListTitleLineHeightPreference.default,
    val flowArticleListHorizontalPadding: Int = FlowArticleListHorizontalPaddingPreference.default,
    val flowArticleListVerticalPadding: Int = FlowArticleListVerticalPaddingPreference.default,
    val flowArticleListImageRoundedCorners: Int = FlowArticleListImageRoundedCornersPreference.default,
    val flowArticleListImageSize: Int = FlowArticleListImageSizePreference.default,
    val flowArticleListColorThemes: List<me.ash.reader.domain.model.theme.ColorTheme> = FlowArticleListColorThemesPreference.default,
    val flowArticleListRoundedCorners: Int = FlowArticleListRoundedCornersPreference.default,
    val flowArticleListItemSpacing: Int = FlowArticleListItemSpacingPreference.default,

    // Reading page
    val readingRenderer: ReadingRendererPreference = ReadingRendererPreference.default,
    val readingBoldCharacters: ReadingBoldCharactersPreference = ReadingBoldCharactersPreference.default,
    val readingTheme: ReadingThemePreference = ReadingThemePreference.default,
    val readingPageTonalElevation: ReadingPageTonalElevationPreference = ReadingPageTonalElevationPreference.default,
    val readingAutoHideToolbar: ReadingAutoHideToolbarPreference = ReadingAutoHideToolbarPreference.default,
    val readingTextFontSize: Int = ReadingTextFontSizePreference.default,
    val readingTextLineHeight: Float = ReadingTextLineHeightPreference.default,
    val readingLetterSpacing: Float = ReadingTextLetterSpacingPreference.default,
    val readingTextHorizontalPadding: Int = ReadingTextHorizontalPaddingPreference.default,
    val readingTextAlign: ReadingTextAlignPreference = ReadingTextAlignPreference.default,
    val readingTextBold: ReadingTextBoldPreference = ReadingTextBoldPreference.default,
    val readingTitleAlign: ReadingTitleAlignPreference = ReadingTitleAlignPreference.default,
    val readingSubheadAlign: ReadingSubheadAlignPreference = ReadingSubheadAlignPreference.default,
    val readingFonts: ReadingFontsPreference = ReadingFontsPreference.default,
    val readingTitleBold: ReadingTitleBoldPreference = ReadingTitleBoldPreference.default,
    val readingSubheadBold: ReadingSubheadBoldPreference = ReadingSubheadBoldPreference.default,
    val readingTitleUpperCase: ReadingTitleUpperCasePreference = ReadingTitleUpperCasePreference.default,
    val readingSubheadUpperCase: ReadingSubheadUpperCasePreference = ReadingSubheadUpperCasePreference.default,
    val readingImageHorizontalPadding: Int = ReadingImageHorizontalPaddingPreference.default,
    val readingImageRoundedCorners: Int = ReadingImageRoundedCornersPreference.default,
    val readingImageBrightness: Int = ReadingImageBrightnessPreference.default,
    val readingImageMaximize: ReadingImageMaximizePreference = ReadingImageMaximizePreference.default,
    // 2026-01-24: 新增阅读页面标题样式设置相关字段
    // 修改原因：支持标题字体大小、颜色、左右边距的可配置存储
    val readingTitleFontSize: Int = ReadingTitleFontSizePreference.default,
    val readingTitleColor: String = ReadingTitleColorPreference.default,
    val readingTitleHorizontalPadding: Int = ReadingTitleHorizontalPaddingPreference.default,

    // Translate
    val translateServiceId: String = TranslateServiceIdPreference.default,
    val quickTranslateModel: TranslateModelConfig? = null,
    val longPressTranslateModel: TranslateModelConfig? = null,
    val siliconFlowConfig: TranslateProviderConfig? = null,
    val cerebrasConfig: TranslateProviderConfig? = null,

    // Interaction
    val initialPage: InitialPagePreference = InitialPagePreference.default,
    val initialFilter: InitialFilterPreference = InitialFilterPreference.default,
    val swipeStartAction: SwipeStartActionPreference = SwipeStartActionPreference.default,
    val swipeEndAction: SwipeEndActionPreference = SwipeEndActionPreference.default,
    val markAsReadOnScroll: MarkAsReadOnScrollPreference = MarkAsReadOnScrollPreference.default,
    val hideEmptyGroups: HideEmptyGroupsPreference = HideEmptyGroupsPreference.default,
    val pullToSwitchFeed: PullToLoadNextFeedPreference = PullToLoadNextFeedPreference.default,
    val pullToSwitchArticle: PullToSwitchArticlePreference = PullToSwitchArticlePreference.default,
    val openLink: OpenLinkPreference = OpenLinkPreference.default,
    val openLinkSpecificBrowser: OpenLinkSpecificBrowserPreference = OpenLinkSpecificBrowserPreference.default,
    val sharedContent: SharedContentPreference = SharedContentPreference.default,

    // Languages
    val languages: LanguagesPreference = LanguagesPreference.default,
)

