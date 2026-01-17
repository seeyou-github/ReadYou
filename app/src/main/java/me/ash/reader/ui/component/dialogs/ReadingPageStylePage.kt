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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.theme.ColorTheme

import me.ash.reader.infrastructure.preference.*

import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.RYSwitch

import me.ash.reader.ui.page.settings.SettingItemMy

import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.toHexCode
import java.util.Locale

/**
 * 将 HSL 颜色转换为十六进制颜色字符串
 * 2026-01-24: 新增
 * 修改原因：支持用户在 UI 中通过 HSL 滑动条选择标题颜色
 */
private fun hslToHex(h: Float, s: Float, l: Float): String {
    val saturation = s / 100f
    val lightness = l / 100f
    val c = (1 - Math.abs(2 * lightness - 1)) * saturation
    val x = c * (1 - Math.abs((h / 60f) % 2 - 1))
    val m = lightness - c / 2

    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val rInt = ((r + m) * 255).toInt()
    val gInt = ((g + m) * 255).toInt()
    val bInt = ((b + m) * 255).toInt()

    return String.format("#%02X%02X%02X", rInt, gInt, bInt)
}

/**
 * 将十六进制颜色字符串转换为 HSL 值
 * 2026-01-24: 新增
 * 修改原因：支持从 Preference 读取颜色并转换为 HSL 显示
 */
private fun hexToHsl(hex: String): Triple<Float, Float, Float> {
    return try {
        val color = android.graphics.Color.parseColor(hex)
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2

        val h = when {
            max == min -> 0f
            max == r -> 60f * ((g - b) / (max - min) % 6)
            max == g -> 60f * ((b - r) / (max - min) + 2)
            else -> 60f * ((r - g) / (max - min) + 4)
        }

        val s = when {
            l == 0f || max == min -> 0f
            l < 0.5f -> (max - min) / (2 * l)
            else -> (max - min) / (2 - 2 * l)
        }

        Triple(h, s * 100, l * 100)
    } catch (e: Exception) {
        Triple(0f, 0f, 87f)  // 默认白色
    }
}

// 2026-01-21: 新增阅读界面样式设置页面
@Composable
fun ReadingPageStylePage(
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: CoroutineScope
) {
    // Tab 切换状态：0: 标题, 1: 正文, 2: 颜色, 3: 页面
    var selectedTab by remember { mutableStateOf(0) }

    // 标题 Tab 的设置值
    val titleBold = LocalReadingTitleBold.current
    val subheadBold = LocalReadingSubheadBold.current
    val titleAlign = LocalReadingTitleAlign.current
    val titleUpperCase = LocalReadingTitleUpperCase.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current

    // 2026-01-24: 标题样式设置 - 从 Preference 读取初始值
    val titleFontSize = LocalReadingTitleFontSize.current
    val titleColor = LocalReadingTitleColor.current
    val titleHorizontalPadding = LocalReadingTitleHorizontalPadding.current

    // 2026-01-24: 将十六进制颜色转换为 HSL 值用于滑动条显示
    val (initialHue, initialSaturation, initialLightness) = hexToHsl(titleColor)
    var sliderTitleHue by remember { mutableStateOf(initialHue) }
    var sliderTitleSaturation by remember { mutableStateOf(initialSaturation) }
    var sliderTitleLightness by remember { mutableStateOf(initialLightness) }
    var sliderTitleFontSize by remember { mutableStateOf(titleFontSize.toFloat()) }
    var sliderTitleHorizontalPadding by remember { mutableStateOf(titleHorizontalPadding.toFloat()) }

    // 正文 Tab 的设置值
    val fontSize = LocalReadingTextFontSize.current
    val lineHeight = LocalReadingTextLineHeight.current
    val letterSpacing = LocalReadingTextLetterSpacing.current
    val horizontalPadding = LocalReadingTextHorizontalPadding.current
    val textAlign = LocalReadingTextAlign.current
    val textBold = LocalReadingTextBold.current
    val imageRoundedCorners = LocalReadingImageRoundedCorners.current
    val imageBrightness = LocalReadingImageBrightness.current

    // 页面 Tab 的设置值
    val readingRenderer = LocalReadingRenderer.current
    val boldCharacters = LocalReadingBoldCharacters.current
    val readingFonts = LocalReadingFonts.current
    val autoHideToolbar = LocalReadingAutoHideToolbar.current
    val pullToSwitchArticle = LocalPullToSwitchArticle.current
    val tonalElevation = LocalReadingPageTonalElevation.current

    // 对话框状态
    var titleAlignDialogVisible by remember { mutableStateOf(false) }
    var textAlignDialogVisible by remember { mutableStateOf(false) }
    var rendererDialogVisible by remember { mutableStateOf(false) }
    var fontsDialogVisible by remember { mutableStateOf(false) }
    var tonalElevationDialogVisible by remember { mutableStateOf(false) }
    var colorThemePageVisible by remember { mutableStateOf(false) }

    // 2026-01-21: 添加颜色主题状态管理
    // 修改原因：将 ReadingColorThemePage 的功能移植到 ReadingPageStylePage 中
    var colorThemes by remember { mutableStateOf(CustomReaderThemesPreference.default) }
    var editingTheme by remember { mutableStateOf<ColorTheme?>(null) }
    var isCreatingNewTheme by remember { mutableStateOf(false) }

    // 获取当前暗色主题状态并过滤主题列表
    val isDarkTheme = LocalDarkTheme.current.isDarkTheme()
    val filteredColorThemes = colorThemes.filter { it.isDarkTheme == isDarkTheme }

    // 确保有对应类型的默认主题
    LaunchedEffect(isDarkTheme) {
        val hasDefaultTheme = filteredColorThemes.any { it.isDefault }
        if (!hasDefaultTheme && filteredColorThemes.isNotEmpty()) {
            // 如果没有默认主题，设置第一个为默认主题
            val updatedThemes = colorThemes.map {
                if (it.id == filteredColorThemes.first().id) {
                    it.copy(isDefault = true)
                } else {
                    it.copy(isDefault = false)
                }
            }
            CustomReaderThemesPreference.put(context, scope, updatedThemes)
            colorThemes = updatedThemes
        }
    }

    // 2026-01-21: 初始化颜色主题数据
    LaunchedEffect(Unit) {
        context.dataStore.data.collect { preferences ->
            colorThemes = CustomReaderThemesPreference.fromPreferences(preferences)
        }
    }

    // 2026-01-21: 添加外部字体选择器 launcher
    // 修改原因：支持用户选择外部字体文件，参考 ReadingStylePage.kt 的实现
    val fontsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                ExternalFonts(context, it, ExternalFonts.FontType.ReadingFont).copyToInternalStorage()
                // 2026-01-21: 先关闭对话框，避免在对话框显示时重启导致应用退出
                fontsDialogVisible = false
                ReadingFontsPreference.External.put(context, scope)
            } catch (e: Exception) {
                e.printStackTrace()
                context.showToast("Failed to load font: ${e.message}")
            }
        } ?: context.showToast("Cannot get activity result with launcher")
    }

    // 滑动条本地状态
    var sliderFontSize by remember { mutableStateOf(fontSize.toFloat()) }
    var sliderLetterSpacing by remember { mutableStateOf(letterSpacing) }
    var sliderLineHeight by remember { mutableStateOf(lineHeight) }
    var sliderHorizontalPadding by remember { mutableStateOf(horizontalPadding.toFloat()) }
    var sliderImageRoundedCorners by remember { mutableStateOf(imageRoundedCorners.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        // Tab 切换按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                text = "页面",
                isSelected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            TabButton(
                text = "标题",
                isSelected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
            TabButton(
                text = "正文",
                isSelected = selectedTab == 2,
                onClick = { selectedTab = 2 }
            )
            TabButton(
                text = "颜色",
                isSelected = selectedTab == 3,
                onClick = { selectedTab = 3 }
            )

        }

        // 根据 Tab 显示不同内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTab) {
                1 -> {
                    // 标题 Tab - 2026-01-21: 从 ReadingTitlePage.kt 复制
                    Column(
                        modifier = Modifier.fillMaxWidth(),
//                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 标题设置
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                text = "标题加粗",
//                                style = MaterialTheme.typography.bodyMedium
//                            )
//                            RYSwitch(activated = titleBold.value) {
//                                (!titleBold).put(context, scope)
//                                ReadingThemePreference.Custom.put(context, scope)
//                            }
//                        }
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                text = "标题大写",
//                                style = MaterialTheme.typography.bodyMedium
//                            )
//                            RYSwitch(activated = titleUpperCase.value) {
//                                (!titleUpperCase).put(context, scope)
//                                ReadingThemePreference.Custom.put(context, scope)
//                            }
//                        }


                        // 2026-01-24: 新增标题样式设置 - 文字大小、颜色、左右边距
                        // 标题文字大小滑动条
                        CompactSliderRow(
                            label = "文字大小",
                            value = sliderTitleFontSize,
                            range = 10f..40f,
                            onValueChange = {
                                sliderTitleFontSize = it
                                ReadingTitleFontSizePreference.put(context, scope, it.toInt())
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
// 标题左右边距滑动条
                        CompactSliderRow(
                            label = "左右边距",
                            value = sliderTitleHorizontalPadding,
                            range = 0f..50f,
                            onValueChange = {
                                sliderTitleHorizontalPadding = it
                                ReadingTitleHorizontalPaddingPreference.put(context, scope, it.toInt())
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
                        // 2026-01-24: 标题颜色 HSL 滑动条（使用 CompactSliderRow 风格，无thumb指示器）
                        // 显示颜色标签和实时颜色代码
                        val currentHexColor = hslToHex(sliderTitleHue, sliderTitleSaturation, sliderTitleLightness)
                        CompactSliderRow(
                            label = "颜色  $currentHexColor",
                            value = sliderTitleHue,
                            range = 0f..360f,
                            onValueChange = {
                                sliderTitleHue = it
                                val hexColor = hslToHex(sliderTitleHue, sliderTitleSaturation, sliderTitleLightness)
                                ReadingTitleColorPreference.put(context, scope, hexColor)
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
                        // 饱和度滑动条
                        CompactSliderRow(
                            label = "",
                            value = sliderTitleSaturation,
                            range = 0f..100f,
                            onValueChange = {
                                sliderTitleSaturation = it
                                val hexColor = hslToHex(sliderTitleHue, sliderTitleSaturation, sliderTitleLightness)
                                ReadingTitleColorPreference.put(context, scope, hexColor)
                                ReadingThemePreference.Custom.put(context, scope)
                            },
                            showLabel = false
                        )
                        // 亮度滑动条
                        CompactSliderRow(
                            label = "",
                            value = sliderTitleLightness,
                            range = 0f..100f,
                            onValueChange = {
                                sliderTitleLightness = it
                                val hexColor = hslToHex(sliderTitleHue, sliderTitleSaturation, sliderTitleLightness)
                                ReadingTitleColorPreference.put(context, scope, hexColor)
                                ReadingThemePreference.Custom.put(context, scope)
                            },
                            showLabel = false
                        )
                        SettingItemMy(
                            modifier = Modifier.width(150.dp),
                            title = "标题对齐",
                            desc = titleAlign.toDesc(context),
                            onClick = { titleAlignDialogVisible = true }
                        ) {}


                        // 子标题设置
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                text = "子标题加粗",
//                                style = MaterialTheme.typography.bodyMedium
//                            )
//                            RYSwitch(activated = subheadBold.value) {
//                                (!subheadBold).put(context, scope)
//                                ReadingThemePreference.Custom.put(context, scope)
//                            }
//                        }
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                text = "子标题大写",
//                                style = MaterialTheme.typography.bodyMedium
//                            )
//                            RYSwitch(activated = subheadUpperCase.value) {
//                                (!subheadUpperCase).put(context, scope)
//                                ReadingThemePreference.Custom.put(context, scope)
//                            }
//                        }
                    }
                }
                2 -> {
                    // 正文 Tab - 2026-01-21: 从 ReadingTextPage.kt 和 ReadingImagePage.kt 复制
                    Column(
                        modifier = Modifier.fillMaxWidth(),
//                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 文本设置 - 使用滑动条
                        CompactSliderRow(
                            label = "字体大小",
                            value = sliderFontSize,
                            range = 12f..30f,
                            onValueChange = {
                                sliderFontSize = it
                                ReadingTextFontSizePreference.put(context, scope, it.toInt())
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                text = "加粗",
//                                style = MaterialTheme.typography.bodyMedium
//                            )
//                            RYSwitch(activated = textBold.value) {
//                                (!textBold).put(context, scope)
//                                ReadingThemePreference.Custom.put(context, scope)
//                            }
//                        }
//
//                        Row(
//                            verticalAlignment = Alignment.CenterVertically,
//                            horizontalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            Text(
//                                text = "字符加粗",
//                                style = MaterialTheme.typography.bodyMedium
//                            )
//                            RYSwitch(
//                                enable = readingRenderer == ReadingRendererPreference.WebView,
//                                activated = boldCharacters.value
//                            ) {
//                                (!boldCharacters).put(context, scope)
//                            }
//                        }

                        CompactSliderRow(
                            label = "字符间距",
                            value = sliderLetterSpacing,
                            range = 0f..5f,
                            step = 0.1f,
                            onValueChange = {
                                sliderLetterSpacing = it
                                ReadingTextLetterSpacingPreference.put(context, scope, it)
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
                        CompactSliderRow(
                            label = "行高倍率",
                            value = sliderLineHeight,
                            range = 0.8f..2.0f,
                            step = 0.1f,
                            onValueChange = {
                                sliderLineHeight = it
                                ReadingTextLineHeightPreference.put(context, scope, it)
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
                        CompactSliderRow(
                            label = "水平填充",
                            value = sliderHorizontalPadding,
                            range = 0f..100f,
                            onValueChange = {
                                sliderHorizontalPadding = it
                                ReadingTextHorizontalPaddingPreference.put(context, scope, it.toInt())
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )

                        // 图片设置 - 使用滑动条
                        CompactSliderRow(
                            label = "图片圆角",
                            value = sliderImageRoundedCorners,
                            range = 0f..50f,
                            onValueChange = {
                                sliderImageRoundedCorners = it
                                ReadingImageRoundedCornersPreference.put(context, scope, it.toInt())
                                ReadingThemePreference.Custom.put(context, scope)
                            }
                        )
                        SettingItemMy(
                            modifier = Modifier.width(150.dp),
                            title = "对齐",
                            desc = textAlign.toDesc(context),
                            onClick = { textAlignDialogVisible = true }
                        ) {}

                    }
                }
                3 -> {
                    // 颜色 Tab - 2026-01-21: 添加颜色主题网格布局
                    // 修改原因：将 ReadingColorThemePage 的功能移植到 ReadingPageStylePage 中
                    // 参考 ArticleListStylePage.kt 的颜色 tab 实现
                    ColorThemeGrid(
                        themes = filteredColorThemes,
                        onThemeClick = { theme ->
                            // 设置选中的颜色主题为默认主题
                            scope.launch {
//                                val selectedThemeId = withContext(Dispatchers.IO) {
//                                    SelectedReaderThemeIdPreference.fromPreferences(context.dataStore.data.first())
//                                }
                                val updatedThemes = colorThemes.map {
                                    it.copy(isDefault = it.id == theme.id)
                                }
                                CustomReaderThemesPreference.put(context, scope, updatedThemes)
//                                SelectedReaderThemeIdPreference.put(context, scope, theme.id)
                                colorThemes = updatedThemes
                            }
                        },
                        onThemeLongClick = { theme ->
                            // 长按编辑颜色主题
                            isCreatingNewTheme = false
                            editingTheme = theme
                        },
                        onAddClick = {
                            // 新增颜色主题
                            isCreatingNewTheme = true
                            editingTheme = ColorTheme(
                                name = "",
                                textColor = Color.White,
                                backgroundColor = Color.Black,
                                primaryColor = Color(0xFF2196F3),
                                isDefault = false,
                                isDarkTheme = isDarkTheme
                            )
                        }
                    )
                }
                0 -> {
                    // 页面 Tab - 2026-01-21: 从 ReadingStylePage.kt 复制
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "内容渲染器",
                                desc = readingRenderer.toDesc(context),
                                onClick = { rendererDialogVisible = true }
                            ) {}
                            SettingItemMy(
                                modifier = Modifier.width(150.dp),
                                title = "阅读字体",
                                desc = readingFonts.toDesc(context),
                                onClick = { fontsDialogVisible = true }
                            ) {}
                        }

//                        SettingItemMy(
//                            modifier = Modifier.width(150.dp),
//                            title = "工具栏色调海拔",
//                            desc = "${tonalElevation.value}dp",
//                            onClick = { tonalElevationDialogVisible = true }
//                        ) {}
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "下拉切换文章",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = pullToSwitchArticle.value) {
                                    pullToSwitchArticle.toggle(context, scope)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "自动隐藏工具栏",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                RYSwitch(activated = autoHideToolbar.value) {
                                    (!autoHideToolbar).put(context, scope)
                                }
                            }
                        }





                    }
                }
            }
        }
    }

    // 对话框
    RadioDialog(
        visible = titleAlignDialogVisible,
        title = "标题对齐",
        options = ReadingTitleAlignPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == titleAlign,
            ) {
                it.put(context, scope)
                ReadingThemePreference.Custom.put(context, scope)
            }
        }
    ) {
        titleAlignDialogVisible = false
    }

    RadioDialog(
        visible = textAlignDialogVisible,
        title = "文本对齐",
        options = ReadingTextAlignPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == textAlign,
            ) {
                it.put(context, scope)
                ReadingThemePreference.Custom.put(context, scope)
            }
        }
    ) {
        textAlignDialogVisible = false
    }

    RadioDialog(
        visible = rendererDialogVisible,
        title = "内容渲染器",
        options = ReadingRendererPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == readingRenderer,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        rendererDialogVisible = false
    }

    RadioDialog(
        visible = fontsDialogVisible,
        title = "阅读字体",
        options = ReadingFontsPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == readingFonts,
            ) {
                // 2026-01-21: 处理外部字体选择，参考 ReadingStylePage.kt 的实现
                if (it.value == ReadingFontsPreference.External.value) {
                    // 先关闭对话框，避免在对话框显示时重启导致应用退出
                    fontsDialogVisible = false
                    fontsLauncher.launch(arrayOf(MimeType.FONT))
                } else {
                    it.put(context, scope)
                }
            }
        }
    ) {
        fontsDialogVisible = false
    }

    RadioDialog(
        visible = tonalElevationDialogVisible,
        title = "工具栏色调海拔",
        options = ReadingPageTonalElevationPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == tonalElevation,
            ) {
                it.put(context, scope)
            }
        }
    ) {
        tonalElevationDialogVisible = false
    }

    // 2026-01-21: 颜色主题编辑对话框
    // 修改原因：将 ReadingColorThemePage 的功能移植到 ReadingPageStylePage 中
    // 参考 ArticleListColorThemeDialog.kt 的实现
    if (editingTheme != null) {
        ReadingColorThemeDialog(
            onDismiss = { editingTheme = null },
            onSave = { updatedThemes ->
                scope.launch {
                    CustomReaderThemesPreference.put(context, scope, updatedThemes)
                    colorThemes = updatedThemes
                }
                editingTheme = null
            },
            onDelete = { themeId ->
                scope.launch {
                    // 获取被删除的主题
                    val deletedTheme = colorThemes.firstOrNull { it.id == themeId }
                    val updatedThemes = colorThemes.filter { it.id != themeId }
                    
                    // 如果删除的是当前默认主题，需要设置新的默认主题
                    val finalThemes = if (deletedTheme?.isDefault == true) {
                        // 如果删除的是默认主题，将剩余主题中的第一个设置为默认
                        if (updatedThemes.isNotEmpty()) {
                            updatedThemes.mapIndexed { index, theme ->
                                if (index == 0) theme.copy(isDefault = true) else theme.copy(isDefault = false)
                            }
                        } else {
                            // 如果没有剩余主题，恢复到默认值
                            CustomReaderThemesPreference.default
                        }
                    } else {
                        updatedThemes
                    }
                    
                    CustomReaderThemesPreference.put(context, scope, finalThemes)
                    colorThemes = finalThemes
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
}

// 2026-01-21: 新增 TabButton 组件
@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
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
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// 2026-01-21: 新增 CompactSliderRow 组件，参考 ArticleListStylePage.kt
// 2026-01-24: 添加 showLabel 参数支持隐藏标签，减少垂直间距
@Composable
private fun CompactSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    onValueChange: (Float) -> Unit,
    showLabel: Boolean = true
) {
    // 2026-01-24: 减小垂直间距，标签显示时4dp，隐藏标签时2dp
    Column() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2026-01-24: 根据 showLabel 参数决定是否显示标签
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .then(if (!showLabel) Modifier.padding(start = 0.dp) else Modifier)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.weight(4f)
            ) {
                IconButton(
                    onClick = {
                        val newValue = (value - step).coerceIn(range)
                        onValueChange(newValue)
                    },
                    modifier = Modifier.size(14.dp)
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
                    },
                    modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "增大",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = if (step >= 1f) value.toInt().toString() else String.format("%.1f", value),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(22.dp)
                )
            }
        }
    }
}

// 2026-01-21: 颜色主题网格布局
// 修改原因：将 ReadingColorThemePage 的功能移植到 ReadingPageStylePage 中
// 参考 ArticleListStylePage.kt 的 ColorThemeGrid 实现
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
        modifier = Modifier.fillMaxWidth().padding(10.dp),
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
                // 如果这一行没有填满，添加占位符
                repeat(itemsPerRow - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // 添加按钮行
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
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
            // 添加占位符
            repeat(itemsPerRow - 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// 2026-01-21: 颜色主题项
// 修改原因：将 ReadingColorThemePage 的功能移植到 ReadingPageStylePage 中
// 参考 ArticleListStylePage.kt 的 ColorThemeItem 实现
@Composable
private fun ColorThemeItem(
    theme: ColorTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            // 选中时显示边框，未选中时无边框
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
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(theme.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // 选中指示器
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

// 2026-01-21: 阅读页面颜色主题编辑对话框
// 修改原因：将 ReadingColorThemePage 的功能移植到 ReadingPageStylePage 中
// 参考 ArticleListColorThemeDialog.kt 的实现
@Composable
private fun ReadingColorThemeDialog(
    onDismiss: () -> Unit,
    onSave: (List<ColorTheme>) -> Unit,
    onDelete: (String) -> Unit,
    context: android.content.Context,
    scope: CoroutineScope,
    currentThemes: List<ColorTheme>,
    editingTheme: ColorTheme? = null,
    isCreatingNew: Boolean = false,
    isDarkTheme: Boolean = false
) {
    var themeName by remember { mutableStateOf(editingTheme?.name ?: "") }
    var textColor by remember { mutableStateOf(editingTheme?.textColor ?: Color.Black) }
    var backgroundColor by remember { mutableStateOf(editingTheme?.backgroundColor ?: Color.White) }
    var primaryColor by remember { mutableStateOf(editingTheme?.primaryColor ?: Color(0xFF2196F3)) }
    var selectedTab by remember { mutableStateOf(0) }  // 0: 文字颜色, 1: 背景颜色, 2: 主题色

    var red by remember { mutableStateOf(0f) }
    var green by remember { mutableStateOf(0f) }
    var blue by remember { mutableStateOf(0f) }

    // 当前选择的颜色
    val currentColor = when (selectedTab) {
        0 -> textColor
        1 -> backgroundColor
        2 -> primaryColor
        else -> textColor
    }

    // 当选择Tab改变时，更新RGB滑块
    LaunchedEffect(selectedTab) {
        red = currentColor.red
        green = currentColor.green
        blue = currentColor.blue
    }

    // 当RGB滑块改变时，更新当前颜色
    LaunchedEffect(red, green, blue) {
        val newColor = Color(red, green, blue)
        when (selectedTab) {
            0 -> textColor = newColor
            1 -> backgroundColor = newColor
            2 -> primaryColor = newColor
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 预设名称
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = { Text("预设名称（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 颜色按钮（使用Tab切换）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val textColorButtonColor = if (selectedTab == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                    val backgroundColorButtonColor = if (selectedTab == 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }

                    Button(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("文字颜色", color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("背景颜色", color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 预览区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "这是第一行预览文字\n这是第二行预览文字",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // RGB滑动条
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Red: ${(red * 255).toInt()}", modifier = Modifier.width(60.dp))
                        Slider(
                            value = red,
                            onValueChange = { red = it },
                            valueRange = 0f..1f,
                            steps = 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Green: ${(green * 255).toInt()}", modifier = Modifier.width(60.dp))
                        Slider(
                            value = green,
                            onValueChange = { green = it },
                            valueRange = 0f..1f,
                            steps = 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Blue: ${(blue * 255).toInt()}", modifier = Modifier.width(60.dp))
                        Slider(
                            value = blue,
                            onValueChange = { blue = it },
                            valueRange = 0f..1f,
                            steps = 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 颜色代码
                OutlinedTextField(
                    value = currentColor.toHexCode(),
                    onValueChange = {
                        try {
                            val color = Color(android.graphics.Color.parseColor(it))
                            if (selectedTab == 0) {
                                textColor = color
                            } else {
                                backgroundColor = color
                            }
                        } catch (e: Exception) {
                            // 忽略无效的十六进制代码
                        }
                    },
                    label = { Text("#ffffff") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 保存和删除按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 删除按钮（仅编辑模式）
                    if (!isCreatingNew && editingTheme != null) {
                        Button(
                            onClick = {
                                onDelete(editingTheme!!.id)
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // 取消按钮
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // 保存按钮
                    Button(
                        onClick = {
                            if (themeName.isBlank()) {
                                themeName = "主题${currentThemes.size + 1}"
                            }
                            val newTheme = ColorTheme(
                                name = themeName,
                                textColor = textColor,
                                backgroundColor = backgroundColor,
                                primaryColor = primaryColor,
                                isDefault = isCreatingNew,
                                isDarkTheme = isDarkTheme
                            )
                            scope.launch {
                                val updatedThemes = if (!isCreatingNew && editingTheme != null) {
                                    // 编辑模式：更新现有主题
                                    currentThemes.map {
                                        if (it.id == editingTheme!!.id) {
                                            newTheme.copy(id = it.id, isDefault = it.isDefault)
                                        } else {
                                            it
                                        }
                                    }
                                } else {
                                    // 新增模式：添加新主题并设为默认
                                    val themesWithNew = currentThemes + newTheme
                                    themesWithNew.map {
                                        it.copy(isDefault = it.id == newTheme.id)
                                    }
                                }
                                CustomReaderThemesPreference.put(context, scope, updatedThemes)
                                onSave(updatedThemes)
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}