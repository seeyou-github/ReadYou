package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.translate.KeyPicker
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.preference.LocalDynamicProviders
import me.ash.reader.infrastructure.translate.preference.LocalDynamicProvidersOrder
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun ModelSelectionListPage(
    title: String,
    onBack: () -> Unit,
    onModelSelected: (TranslateModelConfig) -> Unit,
    selectedConfig: TranslateModelConfig? = null,
) {
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

    val providersMap = LocalDynamicProviders.current
    val order = LocalDynamicProvidersOrder.current

    val enabledModels = buildList {
        val ordered = order.filter { it in providersMap }
        val missing = providersMap.keys - ordered.toSet()
        (ordered + missing).forEach { id ->
            val cfg = providersMap[id] ?: return@forEach
            if (!cfg.enabled) return@forEach
            cfg.enabledModels.forEach { modelId ->
                add(EnabledModelInfo(
                    providerId = cfg.id,
                    providerName = cfg.name,
                    modelId = modelId,
                    displayName = modelId,
                ))
            }
        }
    }

    RYScaffold(
        containerColor = selectedColorTheme?.backgroundColor
            ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        topBarColor = selectedColorTheme?.backgroundColor
            ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    DisplayText(text = title, desc = "${enabledModels.size}个可用模型")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (enabledModels.isEmpty()) {
                    item {
                        Text(
                            text = "暂无启用的模型，请先配置AI提供商并启用模型",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                } else {
                    items(enabledModels) { model ->
                        val isSelected = selectedConfig?.provider == model.providerId &&
                            selectedConfig.model == model.modelId
                        Column {
                            ModelSelectionItem(
                                providerName = model.providerName,
                                modelId = model.modelId,
                                isSelected = isSelected,
                                onClick = {
                                    val cfg = providersMap[model.providerId] ?: return@ModelSelectionItem
                                    onModelSelected(
                                        TranslateModelConfig(
                                            provider = cfg.id,
                                            model = model.modelId,
                                            apiKey = KeyPicker.pick(cfg),
                                            rpm = cfg.rpm,
                                            kind = cfg.kind,
                                            baseUrl = cfg.baseUrl,
                                            chatPath = cfg.chatPath,
                                            useResponsesApi = cfg.useResponsesApi,
                                        )
                                    )
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}

data class EnabledModelInfo(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val displayName: String,
)

@Composable
private fun ModelSelectionItem(
    providerName: String,
    modelId: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$providerName - $modelId",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
