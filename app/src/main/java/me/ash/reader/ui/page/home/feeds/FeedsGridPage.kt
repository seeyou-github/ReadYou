package me.ash.reader.ui.page.home.feeds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.group.Group
import me.ash.reader.infrastructure.preference.FeedsLayoutStylePreference
import me.ash.reader.infrastructure.preference.LocalFeedsGroupListExpand
import me.ash.reader.infrastructure.preference.LocalFeedsGridColumnCount
import me.ash.reader.infrastructure.preference.LocalFeedsGridRowSpacing
import me.ash.reader.infrastructure.preference.LocalFeedsGridIconSize
import me.ash.reader.infrastructure.preference.LocalFeedsIconBrightness
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.getDefaultGroupId
import me.ash.reader.ui.page.home.feeds.drawer.feed.FeedOptionViewModel
import me.ash.reader.ui.page.home.feeds.drawer.group.GroupOptionViewModel
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue

/**
 * 类似安卓主屏幕风格的 Feeds 页面布局
 * 使用网格布局显示订阅源，每个订阅源以卡片形式展示
 * 支持分组 Tab 页面，可以左右滑动切换不同分组
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedsGridPage(
    feedsViewModel: FeedsViewModel = hiltViewModel(),
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    navigationToFlow: () -> Unit,
    onGroupClick: (String) -> Unit,
    onFeedClick: (Feed) -> Unit,
    onFeedLongClick: (Feed) -> Unit,
    onGroupLongClick: (Group) -> Unit,
    feedDrawerState: ModalBottomSheetState,
    groupDrawerState: ModalBottomSheetState,
) {
    val groupWithFeedList = feedsViewModel.groupWithFeedsListFlow.collectAsStateValue()
    val feedsUiState = feedsViewModel.feedsUiState.collectAsStateValue()
    val groupsVisible = feedsUiState.groupsVisible
    val filterState = feedsViewModel.filterStateFlow.collectAsStateValue()
    val importantSum = feedsUiState.importantSum
    val groupListExpand = LocalFeedsGroupListExpand.current
    val feedsGridColumnCount = LocalFeedsGridColumnCount.current
    val feedsGridRowSpacing = LocalFeedsGridRowSpacing.current
    val feedsGridIconSize = LocalFeedsGridIconSize.current
    val feedsIconBrightness = LocalFeedsIconBrightness.current
    val scope = rememberCoroutineScope()

    // 获取当前选中的颜色主题
    val colorThemes = me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault }

    // 获取 default 分组 ID
    val accountId = feedsUiState.account?.id ?: 0
    val defaultGroupId = accountId.getDefaultGroupId()

    // 2026-01-27: 修改为只显示 default 分组，不显示"全部"分组
    // 获取 default 分组
    val defaultGroup = groupWithFeedList.find { it.group.id == defaultGroupId }

    // 获取自定义分组列表（按 sortOrder 排序）
    val customGroups by remember(groupWithFeedList) {
        derivedStateOf {
            groupWithFeedList.filter {
                it.feeds.isNotEmpty() && it.group.id != defaultGroupId
            }.sortedBy { it.group.sortOrder }
        }
    }

    // 判断是否需要显示 Tab 栏：只有当有自定义分组（非 default 分组）且分组内容不为空时才显示
    val shouldShowTabBar by remember(customGroups) {
        derivedStateOf {
            customGroups.isNotEmpty()
        }
    }

    // 当前选中的 Tab 索引
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Pager 状态（仅在显示 Tab 栏时使用）
    val pagerState = rememberPagerState {
        if (shouldShowTabBar) customGroups.size + 1 else 1
    }

    // Tab 列表：第一个是 default 分组，后面是自定义分组
    val tabGroups by remember(defaultGroup, customGroups) {
        derivedStateOf {
            buildList {
                defaultGroup?.let { add(it.group) }
                addAll(customGroups.map { it.group })
            }
        }
    }

    // 当分组列表变化时，调整当前选中的 Tab 索引
    LaunchedEffect(tabGroups.size) {
        if (selectedTabIndex >= tabGroups.size) {
            selectedTabIndex = maxOf(0, tabGroups.size - 1)
        }
    }

    // 当 Pager 页面变化时，更新 Tab 索引
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage < tabGroups.size) {
            selectedTabIndex = pagerState.currentPage
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (shouldShowTabBar) {
            // Tab 行
            ScrollableTabRow(
                selectedTabIndex = minOf(selectedTabIndex, tabGroups.size - 1),
                containerColor = if (selectedColorTheme != null) selectedColorTheme.backgroundColor else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    val safeIndex = minOf(selectedTabIndex, tabGroups.size - 1)
                    if (safeIndex >= 0 && safeIndex < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[safeIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {},
                edgePadding = 16.dp
            ) {
                tabGroups.forEachIndexed { index, group ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickable(onClick = {
                                    if (selectedTabIndex == index) {
                                        // 点击当前分组 → 跳转到阅读页面
                                        navigationToFlow()
                                    } else {
                                        // 点击其他分组 → 切换页面
                                        selectedTabIndex = index
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                }, onLongClick = {
                                    groupOptionViewModel.fetchGroup(groupId = group.id)
                                    scope.launch { groupDrawerState.show() }
                                })
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 分隔线
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Pager 内容
            HorizontalPager(
                state = pagerState, modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val group = tabGroups[pageIndex]

                // 根据选中的分组显示对应的订阅源
                val feedsToShow =
                    groupWithFeedList.find { it.group.id == group.id }?.feeds ?: emptyList()

                // 网格布局：显示订阅源
                LazyVerticalGrid(
                    columns = GridCells.Fixed(feedsGridColumnCount),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 16.dp, vertical = 16.dp
                        ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 0.dp, top = 0.dp, end = 0.dp, bottom = (feedsGridIconSize + 16).dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(feedsGridRowSpacing.dp)
                ) {
                    items(
                        items = feedsToShow, key = { it.id }) { feed ->
                        FeedGridItem(
                            feed = feed,
                            iconSize = feedsGridIconSize.dp,
                            brightness = feedsIconBrightness,
                            onClick = { onFeedClick(feed) },
                            onLongClick = {
                                onFeedLongClick(feed)
                                scope.launch {
                                    feedOptionViewModel.fetchFeed(feedId = feed.id)
                                    feedDrawerState.show()
                                }
                            })
                    }
                }
            }
        } else {
            // 不显示 Tab 栏时，直接显示 default 分组的内容
            val feedsToShow = defaultGroup?.feeds ?: emptyList()

            // 网格布局：显示订阅源
            LazyVerticalGrid(
                columns = GridCells.Fixed(feedsGridColumnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp, vertical = 16.dp
                    ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 0.dp, top = 0.dp, end = 0.dp, bottom = (feedsGridIconSize + 16).dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(feedsGridRowSpacing.dp)
            ) {
                items(
                    items = feedsToShow, key = { it.id }) { feed ->
                    FeedGridItem(
                        feed = feed,
                        iconSize = feedsGridIconSize.dp,
                        brightness = feedsIconBrightness,
                        onClick = { onFeedClick(feed) },
                        onLongClick = {
                            onFeedLongClick(feed)
                            scope.launch {
                                feedOptionViewModel.fetchFeed(feedId = feed.id)
                                feedDrawerState.show()
                            }
                        })
                }
            }
        }
    }
}

/**
 * FeedGridItem 订阅源网格项
 * 在网格中显示一个订阅源，包括：
 * - 图标（可调大小）
 * - 右上角文章数量徽章（重要性标记）
 * - 名称文本
 *
 * 参数：
 * - feed: 订阅源数据
 * - iconSize: 图标尺寸（默认 56.dp，可调整）
 * - brightness: 图标亮度百分比
 * - onClick: 点击回调
 * - onLongClick: 长按回调
 */
@Composable
fun FeedGridItem(
    feed: Feed,
    iconSize: androidx.compose.ui.unit.Dp = 56.dp,
    brightness: Int = 100,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 获取页面主题颜色
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

    // 外层 Column：水平居中排列，垂直间距固定
    Column(
        modifier = Modifier
            .fillMaxWidth()                       // 占满父级宽度
            .background(
                selectedColorTheme?.backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant
            )
            .combinedClickable(                   // 支持点击 & 长按
                onClick = onClick, onLongClick = onLongClick
            )
            .padding(8.dp),                       // 内边距，内容与外层分隔
        horizontalAlignment = Alignment.CenterHorizontally,//所有子元素水平居中
        verticalArrangement = Arrangement.spacedBy(8.dp)//每个子元素之间竖直间距固定为 8dp
    ) {
        // 图标 + 徽章容器
        // Box 用于叠放图标和右上角徽章
        Box(
            modifier = Modifier
                .size(iconSize) // Box 尺寸 = 图标大小
                .background(
                    selectedColorTheme?.backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant
                ), contentAlignment = Alignment.Center   // 内容居中
        ) {
            Box(
                modifier = Modifier.background(
                        selectedColorTheme?.backgroundColor
                            ?: MaterialTheme.colorScheme.surfaceVariant
                    )
//                .padding(8.dp)
            ) {
                // 订阅源图标
                FeedIcon(
                    feedName = feed.name,
                    iconUrl = feed.icon,
                    size = iconSize,
                    modifier = Modifier.size(iconSize),
                    brightness = brightness,
                    backgroundColor = selectedColorTheme?.backgroundColor
                )
            }


            // 文章数量徽章（重要性标记）
            if (feed.important != 0) {
                androidx.compose.material3.Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)      // 对齐到 Box 右上角
                        .offset(x = 4.dp, y = (-3).dp), // 微调位置，更贴近角落
                    containerColor = androidx.compose.ui.graphics.Color(0xFF8B0000), // 背景色：暗红色
                    contentColor = if (selectedColorTheme != null) selectedColorTheme.textColor
                    else MaterialTheme.colorScheme.onSurface, // 徽章文字颜色
                ) {
                    Text(
                        text = feed.important.toString(), // 显示文章数量
                        color = Color(0xFFdddddd),        // 固定文字颜色
                        modifier = Modifier.padding(
                            horizontal = 0.dp,           // 文字左右内边距
                            vertical = 2.dp              // 文字上下内边距
                        ), fontSize = 13.sp
                    )
                }
            }
        }

        // 订阅源名称
        Text(
            text = feed.name,
            color = if (selectedColorTheme != null) selectedColorTheme.textColor
            else MaterialTheme.colorScheme.onSurface, // 名称文字颜色
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            maxLines = 2,                           // 最多显示两行
            overflow = TextOverflow.Ellipsis,       // 超出显示省略号
            textAlign = TextAlign.Center            // 文本水平居中
        )
    }
}

