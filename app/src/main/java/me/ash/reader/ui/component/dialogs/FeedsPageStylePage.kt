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
import me.ash.reader.infrastructure.preference.DarkThemePreference
import me.ash.reader.infrastructure.preference.BasicFontsPreference
import me.ash.reader.infrastructure.preference.FeedsFilterBarPaddingPreference
import me.ash.reader.infrastructure.preference.FeedsFilterBarStylePreference
import me.ash.reader.infrastructure.preference.FeedsFilterBarTonalElevationPreference
import me.ash.reader.infrastructure.preference.FeedsFilterBarHeightPreference
import me.ash.reader.infrastructure.preference.FeedsGroupListExpandPreference
import me.ash.reader.infrastructure.preference.FeedsGroupListTonalElevationPreference
import me.ash.reader.infrastructure.preference.FeedsLayoutStylePreference
import me.ash.reader.infrastructure.preference.FeedsTopBarTonalElevationPreference
import me.ash.reader.infrastructure.preference.FeedsTopBarHeightPreference
import me.ash.reader.infrastructure.preference.FeedsIconBrightnessPreference
import me.ash.reader.infrastructure.preference.FeedsGridColumnCountPreference
import me.ash.reader.infrastructure.preference.FeedsGridRowSpacingPreference
import me.ash.reader.infrastructure.preference.FeedsGridIconSizePreference
import me.ash.reader.infrastructure.preference.FeedsListItemHeightPreference
// 2026-01-23: 导入列表视图列表边距设置
import me.ash.reader.infrastructure.preference.FeedsListItemPaddingPreference
import me.ash.reader.infrastructure.preference.LocalFeedsListItemPadding
import me.ash.reader.infrastructure.preference.FeedsPageColorThemesPreference
import me.ash.reader.infrastructure.preference.FlowArticleListColorThemesPreference

import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.infrastructure.preference.LocalSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.tooling.preview.Preview
import me.ash.reader.infrastructure.preference.CustomReaderThemesPreference
import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.showToast

@Composable
fun FeedsPageStylePage(
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: CoroutineScope,
    onShowGroupSortDialog: () -> Unit = {}, // 2026-01-22: 新增参数，用于显示分组排序对话框
    onShowFeedSortDialog: () -> Unit = {}   // 2026-01-27: 新增参数，用于显示订阅源排序对话框
) {
    val settings = LocalSettings.current

    // Tab 切换状态：0: 主页, 1: 样式, 2: 颜色
    // 2026-01-21: 修改原因：将"布局"改为"主页"，更符合功能描述
    var selectedTab by remember { mutableStateOf(0) }

    // 获取当前的设置值
    val layoutStyle = settings.feedsLayoutStyle
    val filterBarStyle = settings.feedsFilterBarStyle
    val groupListExpand = settings.feedsGroupListExpand

    var topBarTonalElevation by remember { mutableStateOf(settings.feedsTopBarTonalElevation.value) }
    var filterBarPadding by remember { mutableStateOf(settings.feedsFilterBarPadding) }
    var filterBarTonalElevation by remember { mutableStateOf(settings.feedsFilterBarTonalElevation.value) }
    var groupListTonalElevation by remember { mutableStateOf(settings.feedsGroupListTonalElevation.value) }

    var colorThemes by remember { mutableStateOf(settings.feedsPageColorThemes) }
    var editingTheme by remember { mutableStateOf<ColorTheme?>(null) }
    var isCreatingNewTheme by remember { mutableStateOf(false) }

    // 获取当前暗色主题状态并过滤主题列表
    val isDarkTheme = settings.darkTheme.isDarkTheme()
    val filteredColorThemes = colorThemes.filter { it.isDarkTheme == isDarkTheme }



    // 2026-01-21: 添加字体对话框状态
    // 修改原因：支持基本字体设置项的对话框显示
    var basicFontsDialogVisible by remember { mutableStateOf(false) }

    // 2026-01-23: 获取列表视图列表边距设置
    // 修改原因：读取用户自定义的列表视图列表边距
    val listItemPadding = LocalFeedsListItemPadding.current

    // 2026-01-21: 添加外部字体选择器 launcher
    // 修改原因：支持用户选择外部字体文件，参考 ColorAndStylePage.kt 的实现
    val fontsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                try {
                    ExternalFonts(
                        context, it, ExternalFonts.FontType.BasicFont
                    ).copyToInternalStorage()
                    // 2026-01-21: 先关闭对话框，避免在对话框显示时重启导致应用退出
                    basicFontsDialogVisible = false
                    BasicFontsPreference.External.put(context, scope)
                } catch (e: Exception) {
                    e.printStackTrace()
                    context.showToast("Failed to load font: ${e.message}")
                }
            } ?: context.showToast("Cannot get activity result with launcher")
        }

    // 显示颜色主题对话框（新增和编辑共用）
    if (editingTheme != null) {
        FeedsPageColorThemeDialog(
            onDismiss = { editingTheme = null },
            onSave = { updatedThemes ->
                scope.launch {
                    FeedsPageColorThemesPreference.put(context, scope, updatedThemes)
                    colorThemes = updatedThemes
                }
                editingTheme = null
            },
            onDelete = { themeId ->
                scope.launch {
                    val updatedThemes = colorThemes.filter { it.id != themeId }
                    FeedsPageColorThemesPreference.put(context, scope, updatedThemes)
                    colorThemes = updatedThemes
                }
                editingTheme = null
            },
            context = context,
            scope = scope,
            currentThemes = colorThemes,
            isDarkTheme = isDarkTheme,
            editingTheme = editingTheme,
            isCreatingNew = isCreatingNewTheme
        )
    }

    // 2026-01-21: 添加基本字体对话框
    // 修改原因：支持基本字体设置项的对话框显示
    BasicFontsDialog(
        visible = basicFontsDialogVisible,
        onDismiss = { basicFontsDialogVisible = false },
        context = context,
        scope = scope,
        currentFonts = settings.basicFonts,
        fontsLauncher = fontsLauncher
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        // Tab 切换按钮
        // 2026-01-21: 修改原因：将"布局"改为"主页"，更符合功能描述
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                text = "界面", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
            TabButton(
                text = "尺寸", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
            TabButton(
                text = "主题", isSelected = selectedTab == 2, onClick = { selectedTab = 2 })
        }

        Spacer(modifier = Modifier.height(3.dp))

        // 根据 Tab 显示不同内容，添加滚动
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                0 -> {
                    // 主页 Tab
                    // 2026-01-25: 统一美化布局，使其更紧凑
                    // 修改原因：用户反馈需要更紧凑的布局
                    // 时间：2026-01-25
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp), // 左右边距20dp
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 主页布局样式
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "主页布局", style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LayoutStyleButton(
                                    text = "列表",
                                    isSelected = layoutStyle is FeedsLayoutStylePreference.List,
                                    onClick = {
                                        scope.launch {
                                            FeedsLayoutStylePreference.List.put(context, scope)
                                        }
                                    },
                                    modifier = Modifier.padding(1.dp) // 按钮内部padding=5dp
                                )
                                LayoutStyleButton(
                                    text = "网格",
                                    isSelected = layoutStyle is FeedsLayoutStylePreference.Grid,
                                    onClick = {
                                        scope.launch {
                                            FeedsLayoutStylePreference.Grid.put(context, scope)
                                        }
                                    },
                                    modifier = Modifier.padding(1.dp) // 按钮内部padding=5dp
                                )
                            }
                        }


                        // 过滤栏样式

                        Text(
                            text = "过滤栏样式", style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterBarStyleButton(
                                text = "图标",
                                isSelected = filterBarStyle is FeedsFilterBarStylePreference.Icon,
                                onClick = {
                                    scope.launch {
                                        FeedsFilterBarStylePreference.Icon.put(context, scope)
                                    }
                                },
                                modifier = Modifier.weight(1f)

                            )
                            FilterBarStyleButton(
                                text = "图标标签",
                                isSelected = filterBarStyle is FeedsFilterBarStylePreference.IconLabel,
                                onClick = {
                                    scope.launch {
                                        FeedsFilterBarStylePreference.IconLabel.put(context, scope)
                                    }
                                },
                                modifier = Modifier.weight(1f)

                            )
                            FilterBarStyleButton(
                                text = "仅选中",
                                isSelected = filterBarStyle is FeedsFilterBarStylePreference.IconLabelOnlySelected,
                                onClick = {
                                    scope.launch {
                                        FeedsFilterBarStylePreference.IconLabelOnlySelected.put(
                                            context, scope
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)

                            )
                            FilterBarStyleButton(
                                text = "隐藏",
                                isSelected = filterBarStyle is FeedsFilterBarStylePreference.Hide,
                                onClick = {
                                    scope.launch {
                                        FeedsFilterBarStylePreference.Hide.put(context, scope)
                                    }
                                },
                                modifier = Modifier.weight(1f)

                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 深色主题
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "深色主题", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(
                                    activated = settings.darkTheme.isDarkTheme(),
                                    modifier = Modifier.padding(5.dp) // 按钮内部padding=5dp
                                ) {
                                    // 手动切换主题，不使用 @Composable 的 not() 操作符
                                    val newTheme = when (settings.darkTheme) {
                                        DarkThemePreference.UseDeviceTheme -> DarkThemePreference.ON
                                        DarkThemePreference.ON -> DarkThemePreference.OFF
                                        DarkThemePreference.OFF -> DarkThemePreference.ON
                                    }
                                    newTheme.put(context, scope)
                                    
                                    // 2026-01-25: 自动应用对应的颜色主题
                                    // 切换到暗黑主题时应用暗黑主题，切换到浅色主题时应用浅色主题
                                    // 直接根据 newTheme 的类型判断，不调用 @Composable 函数
                                    val isDarkTheme = newTheme == DarkThemePreference.ON

                                    // 1. 应用到 FeedsPageColorThemesPreference
                                    val feedsThemes = settings.feedsPageColorThemes
                                    val updatedFeedsThemes = feedsThemes.map {
                                        it.copy(isDefault = it.isDarkTheme == isDarkTheme)
                                    }
                                    FeedsPageColorThemesPreference.put(context, scope, updatedFeedsThemes)

                                    // 2. 应用到 FlowArticleListColorThemesPreference
                                    val flowThemes = settings.flowArticleListColorThemes
                                    val updatedFlowThemes = flowThemes.map {
                                        it.copy(isDefault = it.isDarkTheme == isDarkTheme)
                                    }
                                    FlowArticleListColorThemesPreference.put(context, scope, updatedFlowThemes)


                                    // 3. 应用到 CustomReaderThemesPreference.kt
                                    val readThemes = settings.customReaderThemes
                                    val updatedReadThemes = readThemes.map {
                                        it.copy(isDefault = it.isDarkTheme == isDarkTheme)
                                    }
                                    CustomReaderThemesPreference.put(context, scope, updatedReadThemes)

                                }
                            }
                            // 分组列表展开
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "分组列表展开", style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(
                                    activated = groupListExpand.value,
                                    modifier = Modifier.padding(5.dp) // 按钮内部padding=5dp
                                ) {
                                    val newValue = if (groupListExpand.value) {
                                        FeedsGroupListExpandPreference.OFF
                                    } else {
                                        FeedsGroupListExpandPreference.ON
                                    }
                                    newValue.put(context, scope)
                                }
                            }


                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 分组排序
                            Row(
                                modifier = Modifier.width(150.dp)
//                                    .fillMaxWidth()
                                    .height(30.dp) // 每行高度30dp
                                    .clickable { onShowGroupSortDialog() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "分组排序", style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "点击排序",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 订阅源排序
                            Row(
                                modifier = Modifier.width(150.dp)
//                                    .fillMaxWidth()
                                    .height(30.dp) // 每行高度30dp
                                    .clickable { onShowFeedSortDialog() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "订阅源排序", style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "点击排序",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 2026-01-25: 将基本字体设置从样式tab移动到主页tab
                        // 修改原因：用户反馈希望基本字体设置在主页tab更方便访问
                        // 时间：2026-01-25
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 基本字体设置占据整行
                            Row(
                                modifier = Modifier.width(150.dp)
//                                    .fillMaxWidth()
                                    .height(30.dp) // 每行高度30dp
                                    .clickable { basicFontsDialogVisible = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "基本字体", style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = settings.basicFonts.toDesc(context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }


                    }
                }

                1 -> {
                    // 样式 Tab
                    // 过滤栏设置
                    var topBarHeightDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "顶部栏高度(全局)",
                        value = settings.feedsTopBarHeight.toFloat(),
                        range = 48f..96f,
                        step = 1f,
                        onValueChange = { value ->
                            topBarHeightDebounceJob?.cancel()
                            topBarHeightDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsTopBarHeightPreference.put(context, scope, value.toInt())
                            }
                        })
                    CompactSliderRow(
                        label = "顶栏底栏色调",
                        value = topBarTonalElevation.toFloat(),
                        range = 0f..5f,
                        onValueChange = {
                            topBarTonalElevation = it.toInt()
                            scope.launch {
                                val newPreference = when (it.toInt()) {
                                    0 -> FeedsTopBarTonalElevationPreference.Level0
                                    1 -> FeedsTopBarTonalElevationPreference.Level1
                                    2 -> FeedsTopBarTonalElevationPreference.Level2
                                    3 -> FeedsTopBarTonalElevationPreference.Level3
                                    4 -> FeedsTopBarTonalElevationPreference.Level4
                                    else -> FeedsTopBarTonalElevationPreference.Level5
                                }
                                newPreference.put(context, scope)
                            }
                        })
                    var filterBarHeightDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "过滤栏高度(全局)",
                        value = settings.feedsFilterBarHeight.toFloat(),
                        range = 40f..80f,
                        step = 1f,
                        onValueChange = { value ->
                            filterBarHeightDebounceJob?.cancel()
                            filterBarHeightDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsFilterBarHeightPreference.put(context, scope, value.toInt())
                            }
                        })
                    CompactSliderRow(
                        label = "过滤栏内边距",
                        value = filterBarPadding.toFloat(),
                        range = 0f..120f,
                        onValueChange = {
                            filterBarPadding = it.toInt()
                            scope.launch {
                                FeedsFilterBarPaddingPreference.put(context, scope, it.toInt())
                            }
                        })

                    // 图标设置
                    var brightnessDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "图标亮度",
                        value = settings.feedsIconBrightness.toFloat(),
                        range = 10f..100f,
                        step = 1f,
                        onValueChange = { value ->
                            brightnessDebounceJob?.cancel()
                            brightnessDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsIconBrightnessPreference.put(context, scope, value.toInt())
                            }
                        })
                    var columnCountDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "图标列数",
                        value = settings.feedsGridColumnCount.toFloat(),
                        range = 3f..5f,
                        step = 1f,
                        onValueChange = { value ->
                            columnCountDebounceJob?.cancel()
                            columnCountDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsGridColumnCountPreference.put(context, scope, value.toInt())
                            }
                        })
                    var rowSpacingDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "图标行距",
                        value = settings.feedsGridRowSpacing.toFloat(),
                        range = 8f..32f,
                        step = 1f,
                        onValueChange = { value ->
                            rowSpacingDebounceJob?.cancel()
                            rowSpacingDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsGridRowSpacingPreference.put(context, scope, value.toInt())
                            }
                        })
                    var iconSizeDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "图标大小",
                        value = settings.feedsGridIconSize.toFloat(),
                        range = 45f..65f,
                        step = 1f,
                        onValueChange = { value ->
                            iconSizeDebounceJob?.cancel()
                            iconSizeDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsGridIconSizePreference.put(context, scope, value.toInt())
                            }
                        })

                    // 列表设置
                    var listItemHeightDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "列表布局列表高度",
                        value = settings.feedsListItemHeight.toFloat(),
                        range = 48f..96f,
                        step = 1f,
                        onValueChange = { value ->
                            listItemHeightDebounceJob?.cancel()
                            listItemHeightDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsListItemHeightPreference.put(context, scope, value.toInt())
                            }
                        })

                    // 2026-01-23: 新增列表视图列表边距滑动条
                    // 修改原因：支持用户通过滑动条调节列表视图的左右边距
                    var listItemPaddingDebounceJob by remember {
                        mutableStateOf<kotlinx.coroutines.Job?>(
                            null
                        )
                    }
                    CompactSliderRow(
                        label = "列表布局列表边距",
                        value = listItemPadding.toFloat(),
                        range = 0f..30f,
                        step = 1f,
                        onValueChange = { value ->
                            listItemPaddingDebounceJob?.cancel()
                            listItemPaddingDebounceJob = scope.launch {
                                kotlinx.coroutines.delay(300)
                                FeedsListItemPaddingPreference.put(context, scope, value.toInt())
                            }
                        })
                }

                2 -> {
                    // 颜色主题 Tab - 网格布局
                    ColorThemeGrid(themes = filteredColorThemes, onThemeClick = { theme ->
                        // 设置选中的颜色主题为默认主题
                        scope.launch {
                            val updatedThemes = colorThemes.map {
                                it.copy(isDefault = it.id == theme.id)
                            }
                            FeedsPageColorThemesPreference.put(context, scope, updatedThemes)
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
            }
        }
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左边：调节名称
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )

            // 右边：加减符号 + 滑动条 + 数值
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.weight(2f)
            ) {
                IconButton(
                    onClick = {
                        val newValue = (value - step).coerceIn(range)
                        onValueChange(newValue)
                    }, modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "减小",
                        tint = MaterialTheme.colorScheme.primary,
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
                    }, modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "增大",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }

                // 右边显示数值
                Text(
                    text = if (step >= 1f) value.toInt().toString() else String.format(
                        "%.1f", value
                    ), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorThemeItem(
    theme: ColorTheme, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (theme.isDefault) {
                    Modifier.border(
                        width = 3.dp, color = theme.primaryColor, shape = RoundedCornerShape(12.dp)
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
        if (theme.isDefault) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.primaryColor)
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
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        Text(
            text = text, color = Color.Black, style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LayoutStyleButton(
    text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable { onClick() }
            .height(30.dp)
            .padding(horizontal = 10.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center) {
        Text(
            text = text, color = Color.Black
        )
    }
}

@Composable
private fun FilterBarStyleButton(
    text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 1.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center) {
        Text(
            text = text, color = Color.Black,
        )
    }
}

// 2026-01-21: 添加基本字体对话框
// 修改原因：支持基本字体设置项的对话框显示，与颜色和样式页面逻辑保持一致
@Composable
private fun BasicFontsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: CoroutineScope,
    currentFonts: BasicFontsPreference,
    fontsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val fonts = BasicFontsPreference.values

    RadioDialog(
        visible = visible, title = "基本字体", options = fonts.map { font ->
            RadioDialogOption(
                text = font.toDesc(context),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = font.asFontFamily(context)
                ),
                selected = font == currentFonts,
            ) {
                // 2026-01-21: 处理外部字体选择，参考 ColorAndStylePage.kt 的实现
                if (font.value == BasicFontsPreference.External.value) {
                    fontsLauncher.launch(arrayOf(MimeType.FONT))
                } else {
                    font.put(context, scope)
                }
            }
        }) {
        onDismiss()
    }
}

@Composable
private fun ColorThemeGrid(
    themes: List<ColorTheme>,
    onThemeClick: (ColorTheme) -> Unit,
    onThemeLongClick: (ColorTheme) -> Unit,
    onAddClick: () -> Unit
) {
    val itemsPerRow = 5
    val rows = themes.chunked(itemsPerRow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                repeat(itemsPerRow - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // 添加按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新增颜色主题",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            repeat(itemsPerRow - 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

