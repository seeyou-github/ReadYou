package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.translate.TranslateProviders
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.page.settings.SelectableSettingGroupItem
import me.ash.reader.ui.theme.palette.onLight

/**
 * AI翻译设置页面
 */
@Composable
fun AITranslationPage(
    onBack: () -> Unit,
    onNavigateToProviderList: () -> Unit,
    onNavigateToProviderConfig: (String) -> Unit,
    onNavigateToModelList: (String) -> Unit,
    viewModel: AITranslationViewModel = hiltViewModel()
) {
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    
    val quickModelConfig by viewModel.quickModelConfig.collectAsState()
    // 2026-01-31: 隐藏长按翻译模型功能
    // val longPressModelConfig by viewModel.longPressModelConfig.collectAsState()
    
    var showModelSelection by remember { mutableStateOf(false) }
    // 2026-01-31: 隐藏长按翻译模型功能
    // var showLongPressModelSelection by remember { mutableStateOf(false) }

    RYScaffold(
        containerColor = selectedColorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        topBarColor = selectedColorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(
                        text = stringResource(R.string.ai_translation),
                        desc = ""
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 翻译模型（唯一的翻译模型设置）
                item {
                    val quickConfig = quickModelConfig
                    val quickDesc = if (quickConfig != null) {
                        val provider = TranslateProviders.ALL.find { it.id == quickConfig.provider }
                        "${provider?.name ?: quickConfig.provider}: ${quickConfig.model}"
                    } else {
                        stringResource(R.string.model_not_configured)
                    }
                    
                    SelectableSettingGroupItem(
                        title = "翻译模型",  // 2026-01-31: 修改文字
                        desc = quickDesc,
                        icon = Icons.Outlined.Translate,
                        onClick = { showModelSelection = true }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // AI提供商（移到下面）
                item {
                    SelectableSettingGroupItem(
                        title = "AI提供商",
                        desc = "SiliconFlow, Cerebras",
                        icon = Icons.Outlined.Business,
                        onClick = onNavigateToProviderList
                    )
                }
                
                // 2026-01-31: 隐藏长按翻译模型功能
                // item {
                //     Spacer(modifier = Modifier.height(8.dp))
                // }
                // 
                // // 长按翻译模型(高质量) - 已隐藏
                // item {
                //     val longConfig = longPressModelConfig
                //     val longPressDesc = if (longConfig != null) {
                //         val provider = TranslateProviders.ALL.find { it.id == longConfig.provider }
                //         "${provider?.name ?: longConfig.provider}: ${longConfig.model}"
                //     } else {
                //         stringResource(R.string.model_not_configured)
                //     }
                //     
                //     SelectableSettingGroupItem(
                //         title = "长按翻译模型(高质量)",
                //         desc = longPressDesc,
                //         icon = Icons.Outlined.Translate,
                //         onClick = { showLongPressModelSelection = true }
                //     )
                // }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
    
    // 翻译模型选择（从启用的模型中选择）
    if (showModelSelection) {
        ModelSelectionListPage(
            title = "选择翻译模型",
            onBack = { showModelSelection = false },
            onModelSelected = { config ->
                viewModel.saveQuickModel(config)
                showModelSelection = false
            },
            selectedConfig = quickModelConfig
        )
    }
    
    // 2026-01-31: 隐藏长按翻译模型功能
    // // 长按翻译模型选择（从启用的模型中选择）
    // if (showLongPressModelSelection) {
    //     ModelSelectionListPage(
    //         title = "选择高质量翻译模型",
    //         onBack = { showLongPressModelSelection = false },
    //         onModelSelected = { config ->
    //             viewModel.saveLongPressModel(config)
    //             showLongPressModelSelection = false
    //         },
    //         selectedConfig = longPressModelConfig
    //     )
    // }
}
