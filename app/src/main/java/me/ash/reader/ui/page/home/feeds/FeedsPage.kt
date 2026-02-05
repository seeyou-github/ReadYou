package me.ash.reader.ui.page.home.feeds

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.zIndex
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import timber.log.Timber
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import androidx.work.WorkInfo
import kotlin.collections.set
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.FeedsLayoutStylePreference
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarHeight
import me.ash.reader.infrastructure.preference.LocalFeedsGroupListExpand
import me.ash.reader.infrastructure.preference.LocalFeedsGroupListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.preference.LocalFeedsLayoutStyle
import me.ash.reader.infrastructure.preference.LocalFeedsTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFeedsTopBarHeight
import me.ash.reader.infrastructure.preference.LocalNewVersionNumber
import me.ash.reader.infrastructure.preference.LocalSkipVersionNumber
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.atElevation
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.findActivity
import me.ash.reader.ui.ext.getCurrentVersion
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.page.common.RouteName
import me.ash.reader.ui.page.home.feeds.accounts.AccountsTab
import me.ash.reader.ui.page.home.feeds.drawer.feed.FeedOptionDrawer
import me.ash.reader.ui.page.home.feeds.drawer.group.GroupOptionDrawer
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeDialog
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel
import me.ash.reader.ui.page.settings.accounts.AccountViewModel
import me.ash.reader.ui.page.home.feeds.FeedsGridPage
import me.ash.reader.ui.page.home.feeds.drawer.feed.FeedOptionViewModel
import me.ash.reader.ui.page.home.feeds.drawer.group.GroupOptionViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FeedsPage(
    //    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    accountViewModel: AccountViewModel = hiltViewModel(),
    feedsViewModel: FeedsViewModel = hiltViewModel(),
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    navigateToSettings: () -> Unit,
    navigationToFlow: () -> Unit,
    navigateToAccountList: () -> Unit,
    navigateToAccountDetail: (Int) -> Unit,
    navigateToLocalRuleEditor: () -> Unit,
) {
    var accountTabVisible by remember { mutableStateOf(false) }
    var showFeedsPageStyleDialog by remember { mutableStateOf(false) }
    var showGroupSortDialog by remember { mutableStateOf(false) } // 2026-01-22: 新增分组排序对话框状态
    var showFeedSortDialog by remember { mutableStateOf(false) }  // 2026-01-27: 新增订阅源排序对话框状态
    var showFeedEditDialog by remember { mutableStateOf(false) }  // 2026-01-27: 新增订阅源编辑对话框状态
    var editingFeed by remember { mutableStateOf<me.ash.reader.domain.model.feed.Feed?>(null) } // 当前编辑的订阅源
    var feedToUpdateInSortDialog by remember { mutableStateOf<me.ash.reader.domain.model.feed.Feed?>(null) } // 需要在排序界面更新的订阅源

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val topBarTonalElevation = LocalFeedsTopBarTonalElevation.current
    val topBarHeight = LocalFeedsTopBarHeight.current
    val groupListTonalElevation = LocalFeedsGroupListTonalElevation.current
    val groupListExpand = LocalFeedsGroupListExpand.current
    val filterBarStyle = LocalFeedsFilterBarStyle.current
    val filterBarPadding = LocalFeedsFilterBarPadding.current
    val filterBarHeight = LocalFeedsFilterBarHeight.current
    val filterBarTonalElevation = LocalFeedsFilterBarTonalElevation.current
    val layoutStyle = LocalFeedsLayoutStyle.current

    val accounts = accountViewModel.accounts.collectAsStateValue(initial = emptyList())

    val feedsUiState = feedsViewModel.feedsUiState.collectAsStateValue()
    val filterState = feedsViewModel.filterStateFlow.collectAsStateValue()
    val importantSum = feedsUiState.importantSum
    val groupWithFeedList = feedsViewModel.groupWithFeedsListFlow.collectAsStateValue()
    val groupsVisible: SnapshotStateMap<String, Boolean> = feedsUiState.groupsVisible
    val hasGroupVisible by
        remember(groupWithFeedList) {
            derivedStateOf { groupWithFeedList.fastAny { groupsVisible[it.group.id] == true } }
        }

    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion = remember { context.getCurrentVersion() }
    val listState =
        if (groupWithFeedList.isNotEmpty()) feedsUiState.listState else rememberLazyListState()

    val owner = LocalLifecycleOwner.current

    var isSyncing by remember { mutableStateOf(false) }
    val syncingState = rememberPullToRefreshState()
    val syncingScope = rememberCoroutineScope()
    val doSync: () -> Unit = {
        isSyncing = true
        syncingScope.launch { feedsViewModel.sync() }
    }

    DisposableEffect(owner) {
        scope.launch {
            owner.lifecycle.eventFlow.collect {
                when (it) {
                    Lifecycle.Event.ON_RESUME,
                    Lifecycle.Event.ON_PAUSE -> {
                        feedsViewModel.commitDiffs()
                    }

                    else -> {
                        /* no-op */
                    }
                }
            }
        }
        feedsViewModel.syncWorkLiveData.observe(owner) { workInfoList ->
            workInfoList.let {
                isSyncing = it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
            }
        }
        onDispose { feedsViewModel.syncWorkLiveData.removeObservers(owner) }
    }

    fun expandAllGroups() {
        groupWithFeedList.forEach { groupWithFeed -> groupsVisible[groupWithFeed.group.id] = true }
    }

    fun collapseAllGroups() {
        groupWithFeedList.forEach { groupWithFeed -> groupsVisible[groupWithFeed.group.id] = false }
    }

    val groupDrawerState =
        rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val feedDrawerState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)

    BackHandler(true) {
        if (isSyncing) {
            feedsViewModel.cancelSync()
        } else {
            context.findActivity()?.moveTaskToBack(false)
        }
    }

    RYScaffold(
        //最底层背景色，订阅源列表下层
        containerColor = if (selectedColorTheme != null) selectedColorTheme.backgroundColor else MaterialTheme.colorScheme.surface,
        topBarTonalElevation = topBarTonalElevation.value.dp,
        //        containerTonalElevation = groupListTonalElevation.value.dp,
        topBar = {
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
                title = {},
                navigationIcon = {
                    FeedbackIconButton(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                        showBadge = newVersion.whetherNeedUpdate(currentVersion, skipVersion),
                    ) {
                        navigateToSettings()
                    }
                },
                actions = {
                    if (subscribeViewModel.rssService.get().addSubscription) {
                        FeedbackIconButton(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.subscribe),
                            tint = MaterialTheme.colorScheme.onSurface,
                        ) {
                            subscribeViewModel.showDrawer()
                        }
                    }
                    // 2026-01-28: 新增一键标记所有文章为已读按钮
                    FeedbackIconButton(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = stringResource(R.string.mark_all_as_read),
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        feedsViewModel.markAllAsRead()
                    }
                    FeedbackIconButton(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = "样式设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        showFeedsPageStyleDialog = true
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            if (selectedColorTheme != null) {
                                selectedColorTheme.backgroundColor.atElevation(
                                    sourceColor = MaterialTheme.colorScheme.onSurface,
                                    elevation = topBarTonalElevation.value.dp
                                )
                            } else {
                                MaterialTheme.colorScheme.surfaceColorAtElevation(
                                    topBarTonalElevation.value.dp
                                )
                            }
                    ),
            )
        },
        content = {
            PullToRefreshBox(state = syncingState, isRefreshing = false, onRefresh = doSync) {
                // 2026-01-25: 悬浮显示同步进度（最上层，不占用布局空间）
                // 原因：用户反馈进度控件占用空间影响排版，且需要移除转圈圈动画
                // 时间：2026-01-25
                if (isSyncing) {
                    val syncProgress by feedsViewModel.syncProgress.collectAsState()
                    Timber.tag("SyncProgressUI").d("UI收到进度: $syncProgress, isSyncing: $isSyncing")
                    syncProgress?.let { (current, total) ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f), // 确保在最上层
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = "更新中：$current/$total",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                val groupWithFeedList = feedsViewModel.groupWithFeedsListFlow.collectAsStateValue()

                // 根据布局样式显示不同的页面
                if (layoutStyle is FeedsLayoutStylePreference.Grid) {
                    FeedsGridPage(
                        feedsViewModel = feedsViewModel,
                        feedOptionViewModel = feedOptionViewModel,
                        groupOptionViewModel = groupOptionViewModel,
                        navigationToFlow = navigationToFlow,
                        onGroupClick = { groupId ->
                            val groupName = groupWithFeedList.find { it.group.id == groupId }?.group?.name
                            Timber.tag("TitleTranslate").d("FeedsPage: Group click (Grid) -> groupId=$groupId, groupName=$groupName")
                            feedsViewModel.changeFilter(
                                filterState.copy(group = groupWithFeedList
                                    .find { it.group.id == groupId }?.group, feed = null)
                            )
                            navigationToFlow()
                        },
                        onFeedClick = { feed ->
                            Timber.tag("TitleTranslate").d("FeedsPage: Feed click (Grid) -> feedId=${feed.id}, feedName=${feed.name}, isAutoTranslateTitle=${feed.isAutoTranslateTitle}")
                            feedsViewModel.changeFilter(filterState.copy(feed = feed, group = null))
                            navigationToFlow()
                        },
                        onFeedLongClick = { feed ->
                            // Feed 长按逻辑在 FeedsGridPage 中处理
                        },
                        onGroupLongClick = { group ->
                            // Group 长按逻辑在 FeedsGridPage 中处理
                        },
                        feedDrawerState = feedDrawerState,
                        groupDrawerState = groupDrawerState
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
//                    item {
//                        DisplayText(text = feedsUiState.account?.name ?: "", desc = "") {
//                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
//                            accountTabVisible = true
//                        }
//                    }
//                    item {
//                        FeedsBanner(
//                            filter = filterState.filter,
//                            desc = importantSum.ifEmpty { stringResource(R.string.loading) },
//                        ) {
//                            feedsViewModel.changeFilter(filterState.copy(group = null, feed = null))
//                            navigationToFlow()
//                        }
//                    }

                    //分组文字
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.feeds),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            IconButton(
                                onClick = {
                                    if (hasGroupVisible) collapseAllGroups() else expandAllGroups()
                                },
                                modifier = Modifier
                                    .padding(end = 0.dp)
                                    .size(28.dp),
                            ) {
                                Icon(
                                    imageVector =
                                        if (hasGroupVisible) Icons.Rounded.UnfoldLess
                                        else Icons.Rounded.UnfoldMore,
                                    contentDescription = stringResource(R.string.unfold_less),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    //订阅源列表
                    itemsIndexed(groupWithFeedList) { _, (group, feeds) ->
                        //分组容器
                        GroupWithFeedsContainer {
                            GroupItem(
                                isExpanded = {
                                    groupsVisible.getOrPut(group.id, groupListExpand::value)
                                },
                                group = group,
                                onExpanded = {
                                    groupsVisible[group.id] =
                                        groupsVisible
                                            .getOrPut(group.id, groupListExpand::value)
                                            .not()
                                },
                                onLongClick = { scope.launch { groupDrawerState.show() } },
                            ) {
                                feedsViewModel.changeFilter(
                                    filterState.copy(group = group, feed = null)
                                )
                                navigationToFlow()
                            }

                            feeds.forEachIndexed { index, feed ->
                                FeedItem(
                                    feed = feed,
                                    isLastItem = { index == feeds.lastIndex },
                                    isExpanded = {
                                        groupsVisible.getOrPut(feed.groupId, groupListExpand::value)
                                    },
                                    onClick = {
                                        feedsViewModel.changeFilter(
                                            filterState.copy(feed = feed, group = null)
                                        )
                                        navigationToFlow()
                                    },
                                    onLongClick = { scope.launch { feedDrawerState.show() } },
                                )
                            }
                        }
                    }

                    //底部padding 防止 底栏遮挡
                    item {
                        Spacer(modifier = Modifier.height(128.dp))
                        Spacer(
                            modifier =
                                Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                        )
                    }
                }
                }
            }
        },
        bottomBar = {
            // 当 filterBarStyle.value == 3 (Hide) 时，不显示 FilterBar
            if (filterBarStyle.value != 3) {
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
                    filter = filterState.filter,
                    filterBarStyle = filterBarStyle.value,
                    filterBarFilled = true,
                    filterBarPadding = filterBarPadding.dp,
                    filterBarTonalElevation = topBarTonalElevation.value.dp,
                    backgroundColor = selectedColorTheme?.backgroundColor?.atElevation(
                        sourceColor = MaterialTheme.colorScheme.onSurface,
                        elevation = topBarTonalElevation.value.dp
                    ), // 2026-01-25: 使用当前主题的背景色，并应用顶部栏色调海拔效果
                ) {
                    feedsViewModel.changeFilter(filterState.copy(filter = it))
                }
            }
        },
    )

    SubscribeDialog(
        subscribeViewModel = subscribeViewModel,
    )

    GroupOptionDrawer(drawerState = groupDrawerState)
    FeedOptionDrawer(drawerState = feedDrawerState)

    val currentAccountId = feedsUiState.account?.id

    AccountsTab(
        visible = accountTabVisible,
        accounts = accounts,
        currentAccountId = currentAccountId,
        onAccountSwitch = { accountViewModel.switchAccount(it) { accountTabVisible = false } },
        onClickSettings = {
            accountTabVisible = false
            navigateToAccountDetail(currentAccountId!!)
        },
        onClickManage = {
            accountTabVisible = false
            navigateToAccountList()
        },
        onDismissRequest = { accountTabVisible = false },
    )

    if (showFeedsPageStyleDialog) {
        me.ash.reader.ui.component.dialogs.FeedsPageStyleDialog(
            onDismiss = { showFeedsPageStyleDialog = false },
            context = context,
            scope = scope,
            onShowGroupSortDialog = { showGroupSortDialog = true },
            onShowFeedSortDialog = { showFeedSortDialog = true }  // 2026-01-27: 添加订阅源排序入口
        )
    }

    // 2026-01-22: 新增分组排序对话框
    if (showGroupSortDialog) {
        me.ash.reader.ui.component.dialogs.GroupSortDialog(
            visible = showGroupSortDialog,
            onDismiss = { showGroupSortDialog = false },
            context = context,
            scope = scope,
            groupWithFeedList = groupWithFeedList,
            onUpdateGroup = { group ->
                scope.launch {
                    feedsViewModel.updateGroup(group)
                }
            }
        )
    }

    // 2026-01-27: 新增订阅源排序对话框
    if (showFeedSortDialog) {
        me.ash.reader.ui.component.dialogs.FeedSortDialog(
            visible = showFeedSortDialog,
            onDismiss = { showFeedSortDialog = false },
            groupWithFeedList = groupWithFeedList,
            onUpdateFeed = { feed ->
                scope.launch {
                    feedsViewModel.updateFeed(feed)
                }
            },
            onDeleteFeed = { feed ->
                scope.launch {
                    feedsViewModel.deleteFeed(feed)
                }
            },
            onEditFeed = { feed ->
                editingFeed = feed
                showFeedEditDialog = true
            },
            onFeedUpdated = { updatedFeed ->
                feedToUpdateInSortDialog = updatedFeed
            }
        )
    }

    // 2026-01-27: 新增订阅源编辑对话框
    if (showFeedEditDialog && editingFeed != null) {
        me.ash.reader.ui.component.dialogs.FeedEditDialog(
            visible = showFeedEditDialog,
            onDismiss = {
                showFeedEditDialog = false
                editingFeed = null
            },
            feed = editingFeed!!,
            onSave = { updatedFeed ->
                scope.launch {
                    feedsViewModel.updateFeed(updatedFeed)
                    // 更新排序界面中的订阅源
                    feedToUpdateInSortDialog = updatedFeed
                }
            }
        )
    }
}
