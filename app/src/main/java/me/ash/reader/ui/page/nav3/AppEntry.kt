package me.ash.reader.ui.page.nav3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import me.ash.reader.ui.motion.materialSharedAxisXIn
import me.ash.reader.ui.motion.materialSharedAxisXOut
import me.ash.reader.ui.page.adaptive.ArticleData
import me.ash.reader.ui.page.adaptive.ArticleListReaderPage
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.feeds.FeedsPage
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.ui.page.settings.SettingsPage
import me.ash.reader.ui.page.settings.accounts.AccountDetailsPage
import me.ash.reader.ui.page.settings.accounts.AccountViewModel
import me.ash.reader.ui.page.settings.accounts.AccountsPage
import me.ash.reader.ui.page.settings.accounts.AddAccountsPage
import me.ash.reader.ui.page.settings.color.ColorAndStylePage
import me.ash.reader.ui.page.settings.color.DarkThemePage
import me.ash.reader.ui.page.settings.color.HomePageStylePage
import me.ash.reader.ui.page.settings.color.feeds.FeedsPageStylePage
import me.ash.reader.ui.page.settings.color.reading.BoldCharactersPage
import me.ash.reader.ui.page.settings.color.reading.ReadingImagePage
import me.ash.reader.ui.page.settings.color.reading.ReadingStylePage
import me.ash.reader.ui.page.settings.color.reading.ReadingTextPage
import me.ash.reader.ui.page.settings.color.reading.ReadingTitlePage
import me.ash.reader.ui.page.settings.color.reading.ReadingVideoPage

import me.ash.reader.ui.page.settings.interaction.InteractionPage
import me.ash.reader.ui.page.settings.languages.LanguagesPage
import me.ash.reader.ui.page.settings.other.OtherPage
import me.ash.reader.ui.page.settings.backup.BackupAndRestorePage
import me.ash.reader.ui.page.settings.blacklist.BlacklistPage
import me.ash.reader.infrastructure.translate.ui.AITranslationPage
import me.ash.reader.ui.page.settings.tips.LicenseListPage
import me.ash.reader.ui.page.settings.tips.TipsAndSupportPage
import me.ash.reader.ui.page.settings.troubleshooting.TroubleshootingPage
import me.ash.reader.infrastructure.translate.ui.ProviderListPage
import me.ash.reader.infrastructure.translate.ui.ProviderConfigPage
import me.ash.reader.infrastructure.translate.ui.ModelListPage
import me.ash.reader.infrastructure.translate.ui.ModelSelectionViewModel
import me.ash.reader.ui.page.startup.StartupPage

private const val INITIAL_OFFSET_FACTOR = 0.10f

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun AppEntry(backStack: NavBackStack<NavKey>) {
    val subscribeViewModel = hiltViewModel<SubscribeViewModel>()

    val onBack: () -> Unit = {
        if (backStack.size == 1) backStack[0] = Route.Feeds else backStack.removeLastOrNull()
    }

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())

    val navigator =
        rememberListDetailPaneScaffoldNavigator<ArticleData>(
            scaffoldDirective = scaffoldDirective,
            isDestinationHistoryAware = false,
        )

    SharedTransitionLayout {
        NavDisplay(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            transitionSpec = {
                materialSharedAxisXIn(
                    initialOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(
                        targetOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                    )
            },
            popTransitionSpec = {
                materialSharedAxisXIn(
                    initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
            },
            predictivePopTransitionSpec = {
                materialSharedAxisXIn(
                    initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
            },
            onBack = { backStack.removeLastOrNull() },
            entryProvider = { key ->
                when (key) {
                    Route.Feeds -> {
                        NavEntry(key) {
                            FeedsPage(
                                subscribeViewModel = subscribeViewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                navigateToSettings = { backStack.add(Route.Settings) },
                                navigationToFlow = { backStack.add(Route.Reading(null)) },
                                navigateToAccountList = { backStack.add(Route.Accounts) },
                                navigateToAccountDetail = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    }
                    is Route.Reading -> {
                        NavEntry(key) {
                            val key = rememberSaveable(saver = Route.Reading.Saver) { key }

                            LaunchedEffect(key) {
                                if (key.articleId != null) {
                                    delay(50L)
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        ArticleData(key.articleId),
                                    )
                                }
                            }

                            val viewModel = hiltViewModel<ArticleListReaderViewModel>()

                            ArticleListReaderPage(
                                scaffoldDirective = scaffoldDirective,
                                navigator = navigator,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                viewModel = viewModel,

                                streamTranslateServiceFactory = viewModel.streamTranslateServiceFactory,
                                onBack = onBack,
                                // 2026-01-21: 移除对 Route.ReadingPageStyle 的引用，改为在 ArticleListReadingPage 中显示对话框
                                onNavigateToStylePage = { },
                                // 2026-01-30: 添加导航到AI翻译设置页面
                                onNavigateToAITranslation = { backStack.add(Route.AITranslation) },
                            )
                        }
                    }
                    //                    is Route.Reading -> {
                    //                        NavEntry(key) {
                    //                            val articleId = key.articleId
                    //
                    //                            val readingViewModel: ReadingViewModel =
                    //                                hiltViewModel<
                    //                                    ReadingViewModel,
                    //                                    ReadingViewModel.ReadingViewModelFactory,
                    //                                > { factory ->
                    //                                    factory.create(articleId.toString(), null)
                    //                                }
                    //
                    //                            ReadingPage(
                    //                                readingViewModel = readingViewModel,
                    //                                onBack = onBack,
                    //                                onNavigateToStylePage = {
                    // backStack.add(Route.ReadingPageStyle) },
                    //                            )
                    //                        }
                    //                    }
                    Route.Startup -> {
                        NavEntry(key) {
                            StartupPage(onNavigateToFeeds = { backStack.add(Route.Feeds) })
                        }
                    }
                    Route.Settings ->
                        NavEntry(key) {
                            SettingsPage(
                                onBack = onBack,
                                navigateToAccounts = { backStack.add(Route.Accounts) },
                                navigateToColorAndStyle = { backStack.add(Route.ColorAndStyle) },
                                navigateToInteraction = { backStack.add(Route.Interaction) },
                                navigateToBackupAndRestore = { backStack.add(Route.BackupAndRestore) },
                                navigateToOther = { backStack.add(Route.Other) },
                                navigateToBlacklist = { backStack.add(Route.Blacklist) },
                                navigateToAITranslation = { backStack.add(Route.AITranslation) },
                            )
                        }
                    Route.Accounts ->
                        NavEntry(key) {
                            AccountsPage(
                                onBack = onBack,
                                navigateToAddAccount = { backStack.add(Route.AddAccounts) },
                                navigateToAccountDetails = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    is Route.AccountDetails ->
                        NavEntry(key) {
                            AccountDetailsPage(
                                viewModel =
                                    hiltViewModel<AccountViewModel>().also {
                                        it.initData(key.accountId)
                                    },
                                onBack = onBack,
                                navigateToFeeds = { backStack.add(Route.Feeds) },
                            )
                        }
                    Route.AddAccounts ->
                        NavEntry(key) {
                            AddAccountsPage(
                                onBack = onBack,
                                navigateToAccountDetails = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    Route.ColorAndStyle ->
                        NavEntry(key) {
                            ColorAndStylePage(
                                onBack = onBack,
                                navigateToDarkTheme = { backStack.add(Route.DarkTheme) },
                                navigateToHomePageStyle = { backStack.add(Route.HomePageStyle) },
                                navigateToFeedsPageStyle = { backStack.add(Route.FeedsPageStyle) },
                                // 2026-01-21: 移除对 Route.ReadingPageStyle 的引用，改为在阅读页面中直接打开样式设置对话框
                                navigateToReadingPageStyle = { },
                            )
                        }
                    Route.DarkTheme -> NavEntry(key) { DarkThemePage(onBack = onBack) }
                    Route.HomePageStyle -> NavEntry(key) { HomePageStylePage(onBack = onBack) }
                    Route.FeedsPageStyle -> NavEntry(key) { FeedsPageStylePage(onBack = onBack) }
                    Route.ReadingPageStyle ->
                        NavEntry(key) {
                            ReadingStylePage(
                                onBack = onBack,
                                navigateToReadingBoldCharacters = {
                                    backStack.add(Route.ReadingBoldCharacters)
                                },
                                navigateToReadingPageTitle = {
                                    backStack.add(Route.ReadingPageTitle)
                                },
                                navigateToReadingPageText = {
                                    backStack.add(Route.ReadingPageText)
                                },
                                navigateToReadingPageImage = {
                                    backStack.add(Route.ReadingPageImage)
                                },
                                navigateToReadingPageVideo = {
                                    backStack.add(Route.ReadingPageVideo)
                                },
                                navigateToColorTheme = { backStack.add(Route.ReadingColorTheme) },
                            )
                        }
                    Route.ReadingBoldCharacters ->
                        NavEntry(key) { BoldCharactersPage(onBack = onBack) }
                    Route.ReadingPageTitle -> NavEntry(key) { ReadingTitlePage(onBack = onBack) }
                    Route.ReadingPageText -> NavEntry(key) { ReadingTextPage(onBack = onBack) }
                    Route.ReadingPageImage -> NavEntry(key) { ReadingImagePage(onBack = onBack) }
                    Route.ReadingPageVideo -> NavEntry(key) { ReadingVideoPage(onBack = onBack) }
//                    Route.ReadingColorTheme -> NavEntry(key) {
//                        ReadingColorThemePage(onBack = onBack)
//                    }
                    Route.Interaction -> NavEntry(key) { InteractionPage(onBack = onBack) }
                    Route.Blacklist -> NavEntry(key) {
                        BlacklistPage(
                            viewModel = hiltViewModel(),
                            onBack = onBack,
                        )
                    }
                    Route.Languages -> NavEntry(key) { LanguagesPage(onBack = onBack) }
                    Route.Troubleshooting -> NavEntry(key) { TroubleshootingPage(onBack = onBack) }
                    Route.TipsAndSupport ->
                        NavEntry(key) {
                            TipsAndSupportPage(
                                onBack = onBack,
                                navigateToLicenseList = { backStack.add(Route.LicenseList) },
                            )
                        }
                    Route.LicenseList -> NavEntry(key) { LicenseListPage(onBack = onBack) }
                    Route.BackupAndRestore -> NavEntry(key) { BackupAndRestorePage(onBack = onBack) }
                    Route.AITranslation ->
                        NavEntry(key) {
                            AITranslationPage(
                                onBack = onBack,
                                onNavigateToProviderList = { backStack.add(Route.AIProviderList) },
                                onNavigateToProviderConfig = { providerId -> backStack.add(Route.AIProviderConfig(providerId)) },
                                onNavigateToModelList = { providerId -> backStack.add(Route.AIModelList(providerId)) },
                            )
                        }
                    Route.AIProviderList ->
                        NavEntry(key) {
                            ProviderListPage(
                                onBack = onBack,
                                onProviderClick = { providerId -> backStack.add(Route.AIProviderConfig(providerId)) },
                            )
                        }
                    is Route.AIProviderConfig ->
                        NavEntry(key) {
                            ProviderConfigPage(
                                providerId = key.providerId,
                                onBack = onBack,
                                onFetchModels = { backStack.add(Route.AIModelList(key.providerId)) },
                            )
                        }
                    is Route.AIModelList ->
                        NavEntry(key) {
                            ModelListPage(
                                providerId = key.providerId,
                                onBack = onBack,
                                modelFetchService = hiltViewModel<ModelSelectionViewModel>().modelFetchService,
                            )
                        }
                    Route.Other ->
                        NavEntry(key) {
                            OtherPage(
                                onBack = onBack,
                                navigateToLanguages = { backStack.add(Route.Languages) },
                                navigateToTroubleshooting = { backStack.add(Route.Troubleshooting) },
                                navigateToTipsAndSupport = { backStack.add(Route.TipsAndSupport) },
                            )
                        }
                    else -> NavEntry(key) { throw Exception("Unknown destination") }
                }
            },
        )
    }
}