package me.ash.reader.ui.component.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.theme.ColorTheme
import me.ash.reader.infrastructure.preference.FlowArticleListColorThemesPreference
import me.ash.reader.infrastructure.preference.FlowArticleListFeedIconPreference
import me.ash.reader.infrastructure.preference.FlowArticleListFeedNamePreference
import me.ash.reader.infrastructure.preference.FlowArticleListFirstItemLargeImagePreference
import me.ash.reader.infrastructure.preference.FlowArticleListHorizontalPaddingPreference
import me.ash.reader.infrastructure.preference.FlowArticleListImagePreference
import me.ash.reader.infrastructure.preference.FlowArticleListImageRoundedCornersPreference
import me.ash.reader.infrastructure.preference.FlowArticleListImageSizePreference
import me.ash.reader.infrastructure.preference.FlowArticleListItemSpacingPreference
import me.ash.reader.infrastructure.preference.FlowArticleListRoundedCornersPreference
import me.ash.reader.infrastructure.preference.FlowArticleListTimePreference
import me.ash.reader.infrastructure.preference.FlowArticleListTitleFontSizePreference
import me.ash.reader.infrastructure.preference.FlowArticleListTitleLineHeightPreference
import me.ash.reader.infrastructure.preference.FlowArticleListVerticalPaddingPreference
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.LocalArticleListSwipeStartAction
import me.ash.reader.infrastructure.preference.LocalArticleListSwipeEndAction
import me.ash.reader.infrastructure.preference.LocalSortUnreadArticles
import me.ash.reader.infrastructure.preference.LocalMarkAsReadOnScroll
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.SwipeStartActionPreference
import me.ash.reader.infrastructure.preference.SwipeEndActionPreference
import me.ash.reader.infrastructure.preference.SortUnreadArticlesPreference
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.FlowArticleListDescPreference
import me.ash.reader.infrastructure.preference.FlowArticleListDateStickyHeaderPreference
import me.ash.reader.infrastructure.preference.FlowArticleReadIndicatorPreference
import me.ash.reader.infrastructure.preference.FlowTopBarTonalElevationPreference
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.infrastructure.preference.FlowFilterBarPaddingPreference
import me.ash.reader.infrastructure.preference.FlowFilterBarTonalElevationPreference
// 2026-01-21: 新增过滤栏自动隐藏功能
import me.ash.reader.infrastructure.preference.FlowFilterBarAutoHidePreference
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDesc
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import me.ash.reader.infrastructure.preference.LocalFlowTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarTonalElevation
// 2026-01-21: 新增过滤栏自动隐藏功能
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarAutoHide
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.page.settings.SettingItemMy
import me.ash.reader.infrastructure.preference.LocalReadingImageBrightness
import me.ash.reader.infrastructure.preference.ReadingImageBrightnessPreference

// 2026-01-18: 新增文章列表样式设置页面
// 2026-01-19: 优化界面紧凑度，调整Tab位置、滑动条间距、颜色主题选中标记
@Composable
fun ArticleListStylePage(
    onDismiss: () -> Unit, context: android.content.Context, scope: CoroutineScope
) {
    val settings = LocalSettings.current

    // Tab 切换状态：0: 尺寸, 1: 颜色, 2: 列表, 3: 界面
    var selectedTab by remember { mutableStateOf(0) }

    // 获取当前的设置值
    val articleListFeedIcon = settings.flowArticleListFeedIcon
    val articleListFeedName = settings.flowArticleListFeedName
    val articleListImage = settings.flowArticleListImage
    val articleListTime = settings.flowArticleListTime
    // 2026-01-27: 新增首行大图模式配置
    val firstItemLargeImage = settings.flowArticleListFirstItemLargeImage

    // 2026-01-21: 重构：合并原"交互"和"信息流"Tab 的设置值
    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current
    val sortUnreadArticles = LocalSortUnreadArticles.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current
    val pullToSwitchFeed = settings.pullToSwitchFeed
    val articleListDesc = LocalFlowArticleListDesc.current
    val articleListStickyDate = LocalFlowArticleListDateStickyHeader.current
    val articleListReadIndicator = settings.flowArticleListReadIndicator

    // 2026-01-21: 原信息流 Tab 的设置值
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    // 2026-01-21: 新增过滤栏自动隐藏功能
    val filterBarAutoHide = LocalFlowFilterBarAutoHide.current

    var swipeStartDialogVisible by remember { mutableStateOf(false) }
    var swipeEndDialogVisible by remember { mutableStateOf(false) }
    var showSortUnreadArticlesDialog by remember { mutableStateOf(false) }
    var showPullToLoadDialog by remember { mutableStateOf(false) }
    var showArticleListDescDialog by remember { mutableStateOf(false) }
    var articleListReadIndicatorDialogVisible by remember { mutableStateOf(false) }
    var topBarTonalElevationDialogVisible by remember { mutableStateOf(false) }
    var filterBarStyleDialogVisible by remember { mutableStateOf(false) }
    var filterBarPaddingDialogVisible by remember { mutableStateOf(false) }
    var filterBarTonalElevationDialogVisible by remember { mutableStateOf(false) }
    var filterBarPaddingValue: Int? by remember { mutableStateOf(filterBarPadding) }

    var titleFontSize by remember { mutableStateOf(settings.flowArticleListTitleFontSize) }
    var titleLineHeight by remember { mutableStateOf(settings.flowArticleListTitleLineHeight) }
    var horizontalPadding by remember { mutableStateOf(settings.flowArticleListHorizontalPadding) }
    var verticalPadding by remember { mutableStateOf(settings.flowArticleListVerticalPadding) }
    var imageRoundedCorners by remember { mutableStateOf(settings.flowArticleListImageRoundedCorners) }
    var imageSize by remember { mutableStateOf(settings.flowArticleListImageSize) }
    var roundedCorners by remember { mutableStateOf(settings.flowArticleListRoundedCorners) }
    var itemSpacing by remember { mutableStateOf(settings.flowArticleListItemSpacing) }

    val imageBrightness = LocalReadingImageBrightness.current
    var sliderImageBrightness by remember { mutableStateOf(imageBrightness.toFloat()) }

    var colorThemes by remember { mutableStateOf(settings.flowArticleListColorThemes) }
    var editingTheme by remember { mutableStateOf<ColorTheme?>(null) }
    var isCreatingNewTheme by remember { mutableStateOf(false) }

    // 获取当前暗色主题状态并过滤主题列表
    val isDarkTheme = settings.darkTheme.isDarkTheme()
    val filteredColorThemes = colorThemes.filter { it.isDarkTheme == isDarkTheme }

    // 显示颜色主题对话框（新增和编辑共用）
    if (editingTheme != null) {
        ArticleListColorThemeDialog(
            onDismiss = { editingTheme = null },
            onSave = { updatedThemes ->
                scope.launch {
                    FlowArticleListColorThemesPreference.put(context, scope, updatedThemes)
                    colorThemes = updatedThemes
                }
                editingTheme = null
            },
            onDelete = { themeId ->
                scope.launch {
                    val updatedThemes = colorThemes.filter { it.id != themeId }
                    FlowArticleListColorThemesPreference.put(context, scope, updatedThemes)
                    colorThemes = updatedThemes
                }
                editingTheme = null
            },
            context = context,
            scope = scope,
            currentThemes = colorThemes,
            editingTheme = editingTheme,
            isCreatingNew = isCreatingNewTheme,
            isDarkTheme = isDarkTheme
        )
    }

    // 2026-01-19: 调整布局，整体高度从300dp减少到280dp，更紧凑
    // 2026-01-19: Tab按钮位置向上移动，padding从5dp减少到2dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        // 2026-01-21: 重构 Tab 结构：修改 Tab 名称和数量
        // Tab 切换按钮 - 2026-01-19: 向上移动，减少间距
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                text = "界面", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
            TabButton(
                text = "列表", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
            TabButton(
                text = "尺寸", isSelected = selectedTab == 2, onClick = { selectedTab = 2 })
            TabButton(
                text = "颜色", isSelected = selectedTab == 3, onClick = { selectedTab = 3 })


        }

        // 2026-01-19: Tab和内容之间的间距从5dp减少到3dp
        Spacer(modifier = Modifier.height(3.dp))

        // 根据 Tab 显示不同内容，添加滚动
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                2 -> {
                    // 尺寸 Tab - 2026-01-19: 使用更紧凑的滑动条布局
                    CompactSliderRow(
                        label = "文字大小",
                        value = titleFontSize.toFloat(),
                        range = 12f..30f,
                        onValueChange = {
                            titleFontSize = it.toInt()
                            FlowArticleListTitleFontSizePreference.put(context, scope, it.toInt())
                        })
                    CompactSliderRow(
                        label = "标题行距",
                        value = titleLineHeight,
                        range = 1.0f..2.0f,
                        step = 0.1f,
                        onValueChange = {
                            titleLineHeight = it
                            FlowArticleListTitleLineHeightPreference.put(context, scope, it)
                        })
                    CompactSliderRow(
                        label = "左右边距",
                        value = horizontalPadding.toFloat(),
                        range = 0f..24f,
                        onValueChange = {
                            horizontalPadding = it.toInt()
                            FlowArticleListHorizontalPaddingPreference.put(
                                context,
                                scope,
                                it.toInt()
                            )
                        })
                    CompactSliderRow(
                        label = "上下边距",
                        value = verticalPadding.toFloat(),
                        range = 0f..24f,
                        onValueChange = {
                            verticalPadding = it.toInt()
                            FlowArticleListVerticalPaddingPreference.put(context, scope, it.toInt())
                        })
                    CompactSliderRow(
                        label = "图片圆角",
                        value = imageRoundedCorners.toFloat(),
                        range = 0f..40f,
                        onValueChange = {
                            imageRoundedCorners = it.toInt()
                            FlowArticleListImageRoundedCornersPreference.put(
                                context,
                                scope,
                                it.toInt()
                            )
                        })
                    CompactSliderRow(
                        label = "图片大小",
                        value = imageSize.toFloat(),
                        range = 40f..120f,
                        onValueChange = {
                            imageSize = it.toInt()
                            FlowArticleListImageSizePreference.put(context, scope, it.toInt())
                        })
                    CompactSliderRow(
                        label = "列表&文章图片亮度",
                        value = sliderImageBrightness,
                        range = 10f..100f,
                        step = 1f,
                        onValueChange = {
                            sliderImageBrightness = it
                            ReadingImageBrightnessPreference.put(context, scope, it.toInt())
                        })
                    CompactSliderRow(
                        label = "背景圆角",
                        value = roundedCorners.toFloat(),
                        range = 0f..40f,
                        onValueChange = {
                            roundedCorners = it.toInt()
                            FlowArticleListRoundedCornersPreference.put(context, scope, it.toInt())
                        })
                    CompactSliderRow(
                        label = "项间距",
                        value = itemSpacing.toFloat(),
                        range = 0f..24f,
                        onValueChange = {
                            itemSpacing = it.toInt()
                            FlowArticleListItemSpacingPreference.put(context, scope, it.toInt())
                        })
                }

                3 -> {
                    // 颜色主题 Tab - 网格布局
                    // 2026-01-19: 垂直间距从8dp减少到6dp，更紧凑
                    ColorThemeGrid(themes = filteredColorThemes, onThemeClick = { theme ->
                        // 设置选中的颜色主题为默认主题
                        scope.launch {
                            val updatedThemes = colorThemes.map {
                                it.copy(isDefault = it.id == theme.id)
                            }
                            FlowArticleListColorThemesPreference.put(
                                context,
                                scope,
                                updatedThemes
                            )
                            colorThemes = updatedThemes
                        }
                    }, onThemeLongClick = { theme ->
                        // 长按编辑颜色主题
                        isCreatingNewTheme = false
                        editingTheme = theme
                    }, onAddClick = {
                        isCreatingNewTheme = true
                        editingTheme = ColorTheme(
                            name = "",
                            textColor = Color.White,
                            backgroundColor = Color.Black,
                            primaryColor = Color(0xFF2196F3),
                            isDefault = false,
                            isDarkTheme = isDarkTheme
                        )
                    })
                }

                1 -> {
                    // 2026-01-21: 重构 Tab 2：从"开关"改为"列表"，新增交互设置项
                    // 原内容：订阅图标、订阅名称、文章插图、发布时间
                    // 新增：左滑、右滑、文章描述、粘性发布日期标头
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "订阅图标", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = articleListFeedIcon.value) {
                                    val newValue = if (articleListFeedIcon.value) {
                                        FlowArticleListFeedIconPreference.OFF
                                    } else {
                                        FlowArticleListFeedIconPreference.ON
                                    }
                                    newValue.put(context, scope)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "订阅名称", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = articleListFeedName.value) {
                                    val newValue = if (articleListFeedName.value) {
                                        FlowArticleListFeedNamePreference.OFF
                                    } else {
                                        FlowArticleListFeedNamePreference.ON
                                    }
                                    newValue.put(context, scope)
                                }
                            }
                        }


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "文章插图", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = articleListImage.value) {
                                    val newValue = if (articleListImage.value) {
                                        FlowArticleListImagePreference.OFF
                                    } else {
                                        FlowArticleListImagePreference.ON
                                    }
                                    newValue.put(context, scope)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "发布时间", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = articleListTime.value) {
                                    val newValue = if (articleListTime.value) {
                                        FlowArticleListTimePreference.OFF
                                    } else {
                                        FlowArticleListTimePreference.ON
                                    }
                                    newValue.put(context, scope)
                                }
                            }

                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 2026-01-27: 新增首行大图模式开关
                            // 使用本地状态实现即时UI更新
                            var firstItemLargeImageState by remember {
                                mutableStateOf(firstItemLargeImage.value)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "首行大图", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = firstItemLargeImageState) {
                                    val newValue = if (firstItemLargeImageState) {
                                        FlowArticleListFirstItemLargeImagePreference.OFF
                                    } else {
                                        FlowArticleListFirstItemLargeImagePreference.ON
                                    }
                                    firstItemLargeImageState = newValue.value
                                    newValue.put(context, scope)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SettingItemMy(
                                    modifier = Modifier.width(150.dp),
                                    title = "文章描述",
                                    desc = articleListDesc.description(),
                                    onClick = { showArticleListDescDialog = true }) {}

//                            Row(
//                                verticalAlignment = Alignment.CenterVertically,
//                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//
//                                Text(
//                                    text = "粘性发布日期标头",
//                                    style = MaterialTheme.typography.bodyMedium
//                                )
//                                RYSwitch(activated = articleListStickyDate.value) {
//                                    (!articleListStickyDate).put(context, scope)
//                                }
//
//
//
//                            }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "左滑",
                                desc = swipeToStartAction.desc,
                                onClick = { swipeStartDialogVisible = true }) {}



                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "右滑",
                                desc = swipeToEndAction.desc,
                                onClick = { swipeEndDialogVisible = true }) {}
                        }








                    }
                }

                0 -> {
                    // 2026-01-21: 重构 Tab 3：从"交互"改为"界面"，合并原"交互"和"信息流"Tab 的设置
                    // 保留：排列未读文章顺序、滚动时标记为已读、底部上拉、淡化显示
                    // 从"信息流"Tab 移动：标题栏色调海拔、过滤栏样式、过滤栏水平填充、过滤栏色调海拔
                    // 移除：色调海拔（文章列表色调海拔）
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "滚动标记 已读",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = markAsReadOnScroll.value) {
                                    markAsReadOnScroll.toggle(context, scope)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "上划隐藏过滤栏", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = filterBarAutoHide.value) {
                                    (!filterBarAutoHide).put(context, scope)
                                }
                            }

                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "淡化显示",
                                desc = articleListReadIndicator.description,
                                onClick = { articleListReadIndicatorDialogVisible = true }) {}
//                            SettingItemMy(
//                                modifier = Modifier.width(150.dp),
//                                title = "未读文章顺序",
//                                desc = sortUnreadArticles.description(),
//                                onClick = { showSortUnreadArticlesDialog = true }) {}
                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "底部上拉",
                                desc = pullToSwitchFeed.description(),
                                onClick = { showPullToLoadDialog = true }) {}
                        }
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//
//
//                            SettingItemMy(
//                                modifier = Modifier.width(150.dp),
//                                title = "过滤栏色调",
//                                desc = "${filterBarTonalElevation.value}dp",
//                                onClick = { filterBarTonalElevationDialogVisible = true }) {}
//
//                            // 2026-01-21: 从原"信息流"Tab 移动的设置项
//                            SettingItemMy(
//                                modifier = Modifier.width(150.dp),
//                                title = "标题栏色调",
//                                desc = "${topBarTonalElevation.value}dp",
//                                onClick = { topBarTonalElevationDialogVisible = true }) {}
//
//                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "过滤栏样式",
                                desc = filterBarStyle.toDesc(context),
                                onClick = { filterBarStyleDialogVisible = true }) {}

//                            SettingItemMy(
//                                modifier = Modifier.width(150.dp),
//                                title = "过滤栏水平填充", desc = "${filterBarPadding}dp", onClick = {
//                                    filterBarPaddingValue = filterBarPadding
//                                    filterBarPaddingDialogVisible = true
//                                }) {}


                        }









                    }
                }
            }
        }
    }

    // 对话框
    RadioDialog(
        visible = swipeStartDialogVisible,
        title = "左滑",
        options = SwipeStartActionPreference.values.map {
            RadioDialogOption(
                text = it.desc,
                selected = it == swipeToStartAction,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        swipeStartDialogVisible = false
    }

    RadioDialog(
        visible = swipeEndDialogVisible,
        title = "右滑",
        options = SwipeEndActionPreference.values.map {
            RadioDialogOption(
                text = it.desc,
                selected = it == swipeToEndAction,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        swipeEndDialogVisible = false
    }

    RadioDialog(
        visible = showSortUnreadArticlesDialog,
        title = "排列未读文章顺序",
        options = SortUnreadArticlesPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == sortUnreadArticles,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = { showSortUnreadArticlesDialog = false })

    RadioDialog(
        visible = showPullToLoadDialog,
        title = "底部上拉",
        options = PullToLoadNextFeedPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == pullToSwitchFeed,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = { showPullToLoadDialog = false })

    RadioDialog(
        visible = showArticleListDescDialog,
        title = "文章描述",
        options = FlowArticleListDescPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == articleListDesc,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = { showArticleListDescDialog = false })

    RadioDialog(
        visible = articleListReadIndicatorDialogVisible,
        title = "淡化显示",
        options = FlowArticleReadIndicatorPreference.values.map {
            RadioDialogOption(
                text = it.description, selected = it == articleListReadIndicator
            ) {
                it.put(context, scope)
            }
        }) {
        articleListReadIndicatorDialogVisible = false
    }

    RadioDialog(
        visible = topBarTonalElevationDialogVisible,
        title = "标题栏色调海拔",
        options = FlowTopBarTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == topBarTonalElevation,
            ) {
                it.put(context, scope)
            }
        }) {
        topBarTonalElevationDialogVisible = false
    }

    RadioDialog(
        visible = filterBarStyleDialogVisible,
        title = "过滤栏样式",
        options = FlowFilterBarStylePreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == filterBarStyle,
            ) {
                it.put(context, scope)
            }
        }) {
        filterBarStyleDialogVisible = false
    }

    TextFieldDialog(
        visible = filterBarPaddingDialogVisible,
        title = "过滤栏水平填充",
        value = (filterBarPaddingValue ?: "").toString(),
        placeholder = "值",
        onValueChange = {
            filterBarPaddingValue = it.filter { it.isDigit() }.toIntOrNull()
        },
        onDismissRequest = {
            filterBarPaddingDialogVisible = false
        },
        onConfirm = {
            FlowFilterBarPaddingPreference.put(context, scope, filterBarPaddingValue ?: 0)
            filterBarPaddingDialogVisible = false
        })

    RadioDialog(
        visible = filterBarTonalElevationDialogVisible,
        title = "过滤栏色调海拔",
        options = FlowFilterBarTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == filterBarTonalElevation,
            ) {
                it.put(context, scope)
            }
        }) {
        filterBarTonalElevationDialogVisible = false
    }
}


@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左边：调节名称
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            // 右边：加减符号 + 滑动条 + 数值
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        val newValue = (value - step).coerceIn(range)
                        onValueChange(newValue)
                    }, modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "减小",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    steps = ((range.endInclusive - range.start) / step).toInt(),
                    modifier = Modifier.width(120.dp)
                )

                IconButton(
                    onClick = {
                        val newValue = (value + step).coerceIn(range)
                        onValueChange(newValue)
                    }, modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "增大",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // 右边显示数值
                Text(
                    text = if (step >= 1f) value.toInt().toString() else String.format(
                        "%.1f",
                        value
                    ), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(35.dp)
                )
            }
        }
    }
}

// 2026-01-19: 新增颜色主题项组件，添加更明显的选中标记
// 修改目的：为正在使用的颜色主题添加外圈边框标记，更直观识别
@Composable
private fun ColorThemeItem(
    theme: ColorTheme, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier
) {
    // 2026-01-19: 添加边框效果，选中主题时显示3dp宽的主题色边框
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            // 2026-01-19: 选中时显示边框，未选中时无边框
            .then(
                if (theme.isDefault) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick, onLongClick = onLongClick
            )
            .background(theme.backgroundColor), contentAlignment = Alignment.Center
    ) {
        // 2026-01-19: 简化选中指示器，因为边框已经足够明显
        if (theme.isDefault) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
            )
        }
        Text(
            text = theme.name.take(2),
            color = theme.textColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TabButton(
    text: String, isSelected: Boolean, onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable { onClick() }
            // 2026-01-19: Tab按钮内边距从24x8减少到20x6，更紧凑
        .padding(horizontal = 20.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text, color = Color.Black, style = MaterialTheme.typography.bodyMedium
        )
    }
}

// 2026-01-19: 优化CompactSliderRow使滑动条更紧凑
// 修改目的：减少垂直间距和元素间距，让尺寸Tab显示更多内容
@Composable
private fun CompactSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    // 2026-01-19: 垂直内边距从2dp减少到1dp
    Column(modifier = Modifier.padding(vertical = 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左边：调节名称
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall, // 2026-01-19: 字体从bodyMedium改为bodySmall
                modifier = Modifier.weight(1f)
            )

            // 右边：加减符号 + 滑动条 + 数值
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // 2026-01-19: 元素间距从1dp减少到0dp，更紧凑
                horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.weight(4f)
            ) {
                IconButton(
                    onClick = {
                        val newValue = (value - step).coerceIn(range)
                        onValueChange(newValue)
                    },
                    // 2026-01-19: 按钮尺寸从16dp减少到14dp
                    modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "减小",
                        tint = MaterialTheme.colorScheme.primary,
                        // 2026-01-19: 图标尺寸从14dp减少到12dp
                        modifier = Modifier.size(12.dp)
                    )
                }

                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    steps = 0,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = androidx.compose.ui.graphics.Color.Transparent,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )

                IconButton(
                    onClick = {
                        val newValue = (value + step).coerceIn(range)
                        onValueChange(newValue)
                    },
                    // 2026-01-19: 按钮尺寸从16dp减少到14dp
                    modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "增大",
                        tint = MaterialTheme.colorScheme.primary,
                        // 2026-01-19: 图标尺寸从14dp减少到12dp
                        modifier = Modifier.size(12.dp)
                    )
                }

                // 右边显示数值
                // 2026-01-19: 宽度从25dp减少到22dp
                Text(
                    text = if (step >= 1f) value.toInt().toString() else String.format(
                        "%.1f",
                        value
                    ), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(22.dp)
                )
            }
        }
    }
}

// 2026-01-19: 优化ColorThemeGrid使颜色主题区域更紧凑
@Composable
private fun ColorThemeGrid(
    themes: List<ColorTheme>,
    onThemeClick: (ColorTheme) -> Unit,
    onThemeLongClick: (ColorTheme) -> Unit,
    onAddClick: () -> Unit
) {
    // 计算每行显示的主题数量
    val itemsPerRow = 5
    val rows = themes.chunked(itemsPerRow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        // 2026-01-19: 垂直间距从8dp减少到6dp
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                // 2026-01-19: 水平间距从8dp减少到6dp
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowThemes.forEach { theme ->
                    ColorThemeItem(
                        theme = theme,
                        onClick = { onThemeClick(theme) },
                        onLongClick = { onThemeLongClick(theme) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 如果这一行没有填满，添加占位符
                repeat(itemsPerRow - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // 添加按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            // 2026-01-19: 水平间距从8dp减少到6dp
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onAddClick, modifier = Modifier
                    // 2026-01-19: 按钮尺寸从48dp减少到42dp
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp)) // 2026-01-19: 圆角从12dp减少到10dp
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新增颜色主题",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 添加占位符
            repeat(itemsPerRow - 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// Preview annotations
@Composable
fun ArticleListStylePagePreview() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        androidx.compose.material3.Text(
            "ArticleListStylePage Preview",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}
