package me.ash.reader.ui.page.home.flow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

import me.ash.reader.R
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListItemSpacing
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarTonalElevation
// 2026-01-21: 新增过滤栏自动隐藏功能
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarAutoHide
import me.ash.reader.infrastructure.preference.LocalFeedsTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFeedsTopBarHeight
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarHeight
import me.ash.reader.infrastructure.preference.LocalMarkAsReadOnScroll
import me.ash.reader.infrastructure.preference.FlowArticleListColorThemesPreference
import me.ash.reader.infrastructure.preference.LocalFlowArticleListColorThemes
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.LocalSortUnreadArticles
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFirstItemLargeImage
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.SortUnreadArticlesPreference
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYExtensibleVisibility
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.atElevation
import me.ash.reader.ui.component.scrollbar.VerticalScrollIndicatorFactory
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.scrollbar.scrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedXAxisTransitionSlow
import me.ash.reader.ui.motion.sharedYAxisTransitionExpressive
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults.ContentOffsetMultiple
import me.ash.reader.ui.page.home.reading.PullToLoadState
import me.ash.reader.ui.page.home.reading.pullToLoad
import me.ash.reader.ui.page.home.reading.rememberPullToLoadState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterialApi::class,
)
@Composable
fun FlowPage(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isTwoPane: Boolean,
    viewModel: ArticleListReaderViewModel,
    onNavigateUp: () -> Unit,
    navigateToArticle: (String, Int) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
    val itemSpacing = LocalFlowArticleListItemSpacing.current
    val topBarTonalElevation = LocalFeedsTopBarTonalElevation.current
    val topBarHeight = LocalFeedsTopBarHeight.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    val filterBarHeight = LocalFeedsFilterBarHeight.current
    // 2026-01-21: 新增过滤栏自动隐藏功能
    val filterBarAutoHide = LocalFlowFilterBarAutoHide.current
    // 2026-01-27: 新增首行大图模式配置
    val firstItemLargeImage = LocalFlowArticleListFirstItemLargeImage.current
    val sharedContent = LocalSharedContent.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current.value
    val context = LocalContext.current

    // 2026-01-25: 获取当前颜色主题
    val colorThemes = LocalFlowArticleListColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current

    val settings = LocalSettings.current
    val pullToSwitchFeed = settings.pullToSwitchFeed
    val scope = rememberCoroutineScope()
    // 2026-01-18: 新增文章列表样式设置对话框状态
    var showArticleListStyleDialog by remember { mutableStateOf(false) }



    val flowUiState = viewModel.flowUiState.collectAsStateValue()
    if (flowUiState == null) return

    val pagerData: PagerData = flowUiState.pagerData

    val filterUiState = pagerData.filterState

    val listState = rememberSaveable(pagerData, saver = LazyListState.Saver) { LazyListState(0, 0) }

    val isTopBarElevated = topBarTonalElevation.value > 0
    val scrolledTopBarContainerColor =
        with(MaterialTheme.colorScheme) { if (isTopBarElevated) surfaceContainer else surface }

    val titleText =
        when {
            filterUiState.group != null -> filterUiState.group.name
            filterUiState.feed != null -> filterUiState.feed.name
            else -> filterUiState.filter.toName()
        }


    val focusRequester = remember { FocusRequester() }
    var markAsRead by remember { mutableStateOf(false) }
    var onSearch by rememberSaveable { mutableStateOf(false) }

    var currentPullToLoadState: PullToLoadState? by remember { mutableStateOf(null) }
    var currentLoadAction: LoadAction? by remember { mutableStateOf(null) }

    val settleSpec = remember { spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy) }

    val lastVisibleIndex =
        remember(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .filterNotNull()
        }

    // 2026-01-21: 新增过滤栏自动隐藏功能
    // 参考 ReadingStylePage.kt 中 autoHideToolbar 的实现逻辑
    // 通过监听滚动偏移量 f 来判断滑动方向：f < 0 表示向上滑动（隐藏），f > 0 表示向下滑动（显示）
    var isFilterBarScrollingDown by remember { mutableStateOf(false) }

    // 2026-01-21: 新增 NestedScrollConnection 用于滑动检测，与 ReadingPage 保持一致
    val filterBarNestedScrollConnection = remember(filterBarAutoHide) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 2026-01-21: 当启用自动隐藏时，通过滚动偏移量判断滑动方向
                // f < 0 表示向上滑动，触发隐藏；f > 0 表示向下滑动，触发显示
                if (filterBarAutoHide.value && abs(available.y) > 2f) {
                    isFilterBarScrollingDown = available.y < 0f
                }
                // 返回 Offset.Zero 以便其他 NestedScrollConnection（如 scrollBehavior）可以正常处理滚动
                return Offset.Zero
            }
        }
    }

    val onToggleStarred: (ArticleWithFeed) -> Unit = remember {
        { article ->
            viewModel.updateStarredStatus(
                articleId = article.article.id,
                isStarred = !article.article.isStarred,
            )
        }
    }

    val onToggleRead: (ArticleWithFeed) -> Unit = remember {
        { articleWithFeed -> viewModel.diffMapHolder.updateDiff(articleWithFeed) }
    }

    val sortByEarliest =
        filterUiState.filter.isUnread() &&
            LocalSortUnreadArticles.current == SortUnreadArticlesPreference.Earliest

    val onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsReadFromListByDate(
                    date = it.article.date,
                    isBefore = sortByEarliest,
                )
            }
        }

    val onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsReadFromListByDate(
                    date = it.article.date,
                    isBefore = !sortByEarliest,
                )
            }
        }

    val onShare: ((ArticleWithFeed) -> Unit)? = remember {
        { articleWithFeed ->
            with(articleWithFeed.article) { sharedContent.share(context, title, link) }
        }
    }

    LaunchedEffect(onSearch) {
        if (!onSearch) {
            keyboardController?.hide()
            viewModel.inputSearchContent(null)
        }
    }

    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()

    var pagingItems: LazyPagingItems<ArticleFlowItem>? by remember { mutableStateOf(null) }

    if (isTwoPane) {
        LaunchedEffect(readerState) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = pagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    listState.animateScrollToItem(index, scrollOffset = -200)
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = pagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    listState.requestScrollToItem(index, scrollOffset = -400)
                }
            }
        }
    }

    val isSyncing = viewModel.isSyncingFlow.collectAsStateValue()

    Box(modifier = Modifier.fillMaxSize()) {
        RYScaffold(
            containerColor = selectedColorTheme?.backgroundColor ?: MaterialTheme.colorScheme.surface,
            containerTonalElevation = articleListTonalElevation.value.dp,
            topBar = {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography =
                        MaterialTheme.typography.copy(
                            headlineMedium = MaterialTheme.typography.displaySmall,
                            titleLarge =
                                MaterialTheme.typography.titleLarge.merge(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        ),
                ) {
                    TopAppBar(
                        modifier =
                            Modifier
                                .height(topBarHeight.dp)
                                .clickable(
                                    onClick = {
                                        scope.launch {
                                            if (listState.firstVisibleItemIndex != 0) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ),
                        title = {
                            Text(
                                text = titleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            FeedbackIconButton(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            ) {
                                onSearch = false
                                onNavigateUp()
                            }
                        },
                        actions = {
                            RYExtensibleVisibility(visible = !filterUiState.filter.isStarred()) {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = stringResource(R.string.mark_all_as_read),
                                    tint =
                                        if (markAsRead) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                ) {
                                    if (markAsRead) {
                                        markAsRead = false
                                    } else {
                                        scope
                                            .launch {
                                                if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion {
                                                markAsRead = true
                                                onSearch = false
                                            }
                                    }
                                }
                            }
                            FeedbackIconButton(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search),
                                tint =
                                    if (onSearch) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            ) {
                                if (onSearch) {
                                    onSearch = false
                                } else {
                                    scope
                                        .launch {
                                            if (listState.firstVisibleItemIndex != 0) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                        .invokeOnCompletion {
                                            scope.launch {
                                                onSearch = true
                                                markAsRead = false
                                                delay(100)
                                                focusRequester.requestFocus()
                                            }
                                        }
                                }
                            }
                            // 2026-01-18: 新增文章列表样式设置按钮
                            FeedbackIconButton(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = "文章列表样式",
                                tint = MaterialTheme.colorScheme.onSurface,
                            ) {
                                showArticleListStyleDialog = true
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = if (selectedColorTheme != null) {
                                    selectedColorTheme.backgroundColor.atElevation(
                                        sourceColor = MaterialTheme.colorScheme.onSurface,
                                        elevation = topBarTonalElevation.value.dp
                                    )
                                } else {
                                    scrolledTopBarContainerColor
                                },
                                scrolledContainerColor = if (selectedColorTheme != null) {
                                    selectedColorTheme.backgroundColor.atElevation(
                                        sourceColor = MaterialTheme.colorScheme.onSurface,
                                        elevation = topBarTonalElevation.value.dp
                                    )
                                } else {
                                    scrolledTopBarContainerColor
                                }
                            ),
                    )
                }
            },
            content = {
                // 2026-01-25: 下拉更新时支持返回键取消同步
                // 原因：用户反馈需要能取消正在进行的同步操作
                // 时间：2026-01-25
                BackHandler(isSyncing) { viewModel.cancelSync() }

                RYExtensibleVisibility(modifier = Modifier.zIndex(1f), visible = onSearch) {
                    BackHandler(onSearch) { onSearch = false }
                    SearchBar(
                        value = filterUiState.searchContent ?: "",
                        placeholder =
                            when {
                                filterUiState.group != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.group.name,
                                    )

                                filterUiState.feed != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.feed.name,
                                    )

                                else ->
                                    stringResource(
                                        R.string.search_for,
                                        filterUiState.filter.toName(),
                                    )
                            },
                        focusRequester = focusRequester,
                        colorTheme = selectedColorTheme,
                        onValueChange = { viewModel.inputSearchContent(it) },
                        onClose = {
                            onSearch = false
                            viewModel.inputSearchContent(null)
                        },
                    )
                }

                RYExtensibleVisibility(markAsRead) {
                    BackHandler(markAsRead) { markAsRead = false }

                    MarkAsReadBar(colorTheme = selectedColorTheme) {
                        markAsRead = false
                        viewModel.updateReadStatus(
                            groupId = filterUiState.group?.id,
                            feedId = filterUiState.feed?.id,
                            articleId = null,
                            conditions = it,
                            isUnread = false,
                        )
                    }
                }
                val contentTransitionVertical =
                    sharedYAxisTransitionExpressive(direction = Direction.Forward)
                val contentTransitionBackward =
                    sharedXAxisTransitionSlow(direction = Direction.Backward)
                val contentTransitionForward =
                    sharedXAxisTransitionSlow(direction = Direction.Forward)
                AnimatedContent(
                    targetState = flowUiState,
                    contentKey = { it.pagerData.filterState.copy(searchContent = null) },
                    transitionSpec = {
                        val targetFilter = targetState.pagerData.filterState
                        val initialFilter = initialState.pagerData.filterState

                        if (targetFilter.filter.index > initialFilter.filter.index) {
                            contentTransitionForward
                        } else if (targetFilter.filter.index < initialFilter.filter.index) {
                            contentTransitionBackward
                        } else if (
                            targetFilter.group != initialFilter.group ||
                                targetFilter.feed != initialFilter.feed
                        ) {
                            contentTransitionVertical
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                ) { flowUiState ->
                    val pager = flowUiState.pagerData.pager
                    val filterState = flowUiState.pagerData.filterState
                    val pagingItems = pager.collectAsLazyPagingItems().also { pagingItems = it }

                    if (markAsReadOnScroll && filterState.filter.isUnread()) {
                        LaunchedEffect(listState.isScrollInProgress) {
                            if (!listState.isScrollInProgress) {
                                val firstItemKey =
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.contentType == CONTENT_TYPE_ARTICLE }
                                        ?.key
                                val items = mutableListOf<ArticleWithFeed>()
                                var found = false
                                val itemCount = pagingItems.itemCount
                                for (index in 0 until itemCount) {
                                    pagingItems.peek(index).let {
                                        if (it is ArticleFlowItem.Article) {
                                            if (it.articleWithFeed.article.id == firstItemKey) {
                                                found = true
                                                break
                                            }
                                            items.add(it.articleWithFeed)
                                        }
                                    }
                                }
                                if (items.isNotEmpty() && found) {
                                    viewModel.diffMapHolder.updateDiff(
                                        articleWithFeed = items.toTypedArray(),
                                        isUnread = false,
                                    )
                                }
                            }
                        }
                    }

                    if (settings.flowArticleListDateStickyHeader.value) {
                        LaunchedEffect(lastVisibleIndex) {
                            lastVisibleIndex.collect {
                                if (it in (pagingItems.itemCount - 25..pagingItems.itemCount - 1)) {
                                    pagingItems.get(it)
                                }
                            }
                        }
                    }

                    val listState = remember(pager) { listState }

                    val isSyncing by rememberUpdatedState(isSyncing)

                    LaunchedEffect(pagingItems) {
                        snapshotFlow { pagingItems.loadState.isIdle }
                            .collect {
                                if (isSyncing) {
                                    listState.scrollToItem(0)
                                }
                            }
                    }

                    val loadAction =
                        remember(pager, flowUiState, pullToSwitchFeed) {
                                when (pullToSwitchFeed) {
                                    PullToLoadNextFeedPreference.None -> null
                                    else -> {
                                        when {
                                            flowUiState.nextFilterState != null ->
                                                LoadAction.NextFeed.fromFilterState(
                                                    flowUiState.nextFilterState
                                                )

                                            filterState.filter.isUnread() &&
                                                pullToSwitchFeed ==
                                                    PullToLoadNextFeedPreference
                                                        .MarkAsReadAndLoadNextFeed ->
                                                LoadAction.MarkAllAsRead

                                            else -> null
                                        }
                                    }
                                }
                            }
                            .also { currentLoadAction = it }

                    val onLoadNext: (() -> Unit)? =
                        when (loadAction) {
                            is LoadAction.NextFeed -> viewModel::loadNextFeedOrGroup
                            LoadAction.MarkAllAsRead -> {
                                {
                                    viewModel.markAllAsRead()
                                    currentPullToLoadState?.animateDistanceTo(
                                        targetValue = 0f,
                                        animationSpec = settleSpec,
                                    )
                                }
                            }

                            else -> null
                        }

                    val onPullToSync: (() -> Unit)? =
                        if (isSyncing) null
                        else {
                            {
                                viewModel.sync()
                                currentPullToLoadState?.animateDistanceTo(
                                    targetValue = 0f,
                                    animationSpec = settleSpec,
                                )
                            }
                        }

                    val pullToLoadState =
                        rememberPullToLoadState(
                                key = pager,
                                onLoadNext = onLoadNext,
                                onLoadPrevious = onPullToSync,
                                loadThreshold = PullToLoadDefaults.loadThreshold(.1f),
                            )
                            .also { currentPullToLoadState = it }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier =
                                Modifier.pullToLoad(
                                        state = pullToLoadState,
                                        enabled = true,
                                        contentOffsetY = { fraction ->
                                            if (fraction > 0f) {
                                                (fraction * ContentOffsetMultiple * 1.5f)
                                                    .dp
                                                    .roundToPx()
                                            } else {
                                                (fraction * ContentOffsetMultiple * 2f)
                                                    .dp
                                                    .roundToPx()
                                            }
                                        },
                                        onScroll = {
                                            if (it < -10f) {
                                                markAsRead = false
                                            }
                                        },
                                    )
                                    // 2026-01-21: 新增过滤栏自动隐藏功能的滑动检测
                                    // 参考 ReadingPage.kt 中 autoHideToolbar 的实现
                                    // 使用 NestedScrollConnection 监听滚动方向，与 pullToLoad 修饰符配合使用
                                    .nestedScroll(filterBarNestedScrollConnection)
                                    .fillMaxSize(),
                            state = listState,
                        ) {
                                                // 2026-01-29: 判断是否强制显示订阅源名称
                                                // 当查看分组文章列表时（group != null && feed == null），强制显示订阅源名称
                                                val forceShowFeedName = filterUiState.group != null && filterUiState.feed == null

                                                ArticleList(
                                                    pagingItems = pagingItems,
                                                    diffMap = viewModel.diffMapHolder.diffMap,
                                                    isShowFeedIcon = articleListFeedIcon.value,
                                                    isShowStickyHeader = articleListDateStickyHeader.value,
                                                    articleListTonalElevation = articleListTonalElevation.value,
                                                    itemSpacing = itemSpacing,
                                                    isSwipeEnabled = { listState.isScrollInProgress },
                                                    colorTheme = selectedColorTheme,
                                                    onClick = { articleWithFeed, index ->
                                                        if (articleWithFeed.feed.isBrowser) {
                                                            // 在浏览器中打开：立即更新已读状态
                                                            viewModel.diffMapHolder.updateDiff(
                                                                articleWithFeed,
                                                                isUnread = false,
                                                            )
                                                            context.openURL(
                                                                articleWithFeed.article.link,
                                                                openLink,
                                                                openLinkSpecificBrowser,
                                                            )
                                                        } else {
                                                            // 在应用内阅读：不立即更新已读状态
                                                            navigateToArticle(articleWithFeed.article.id, index)
                                                        }
                                                    },
                                                    onToggleStarred = onToggleStarred,
                                                    onToggleRead = onToggleRead,
                                                    onMarkAboveAsRead = onMarkAboveAsRead,
                                                    onMarkBelowAsRead = onMarkBelowAsRead,
                                                    onShare = onShare,
                                                    // 2026-01-27: 传递首行大图模式参数
                                                    isFirstItemLargeImageEnabled = firstItemLargeImage.value,
                                                    // 2026-01-29: 传递强制显示订阅源名称参数
                                                    forceShowFeedName = forceShowFeedName,
                                                )
                            item {
                                Spacer(modifier = Modifier.height(128.dp))
                                Spacer(
                                    modifier =
                                        Modifier.windowInsetsBottomHeight(
                                            WindowInsets.navigationBars
                                        )
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            bottomBar = {
                // 2026-01-21: 新增过滤栏自动隐藏功能
                // 当 filterBarStyle.value == 3 (Hide) 时，不显示 FilterBar
                // 当 filterBarAutoHide 为 true 且 isFilterBarScrollingDown 为 true 时，隐藏 FilterBar
                val shouldShowFilterBar = filterBarStyle.value != 3 &&
                    !(filterBarAutoHide.value && isFilterBarScrollingDown)

                if (shouldShowFilterBar) {
                    FilterBar(
                        modifier =
                            with(sharedTransitionScope) {
                                Modifier
                                    .height(filterBarHeight.dp)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState("filterBar"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                            },
                        filter = filterUiState.filter,
                        filterBarStyle = filterBarStyle.value,
                        filterBarFilled = true,
                        filterBarPadding = filterBarPadding.dp,
                        filterBarTonalElevation = topBarTonalElevation.value.dp, // 使用顶栏的海拔配置
                        backgroundColor = selectedColorTheme?.backgroundColor?.atElevation(
                            sourceColor = MaterialTheme.colorScheme.onSurface,
                            elevation = topBarTonalElevation.value.dp
                        ), // 2026-01-25: 使用当前主题的背景色，并应用色调海拔效果
                    ) {
                        if (filterUiState.filter != it) {
                            viewModel.changeFilter(filterUiState.copy(filter = it))
                        } else {
                            scope.launch {
                                if (listState.firstVisibleItemIndex != 0) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }
                    }
                }
            },
        )
        currentPullToLoadState?.let {
            PullToSyncIndicator(pullToLoadState = it, isSyncing = isSyncing)
            PullToLoadIndicator(
                state = it,
                loadAction = currentLoadAction,
                modifier =
                    Modifier.padding(bottom = 36.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)
                        ),
            )
        }
    }

    // 2026-01-18: 新增文章列表样式设置对话框
    if (showArticleListStyleDialog) {
        me.ash.reader.ui.component.dialogs.ArticleListStyleDialog(
            onDismiss = { showArticleListStyleDialog = false },
            context = context,
            scope = scope
        )
    }
}
