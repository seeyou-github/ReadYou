package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.preference.DynamicProvidersPreference
import me.ash.reader.infrastructure.translate.preference.LocalDynamicProviders
import me.ash.reader.infrastructure.translate.preference.LocalDynamicProvidersOrder
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

/**
 * AI 提供商列表页面（动态版）。
 *
 * - 顶部「+ 添加」按钮 → 打开 [AddProviderDialog]
 * - 每条供应商：名称 / 类型徽标 / 启用 Switch / 进入配置
 */
@Composable
fun ProviderListPage(
    onBack: () -> Unit,
    onProviderClick: (String) -> Unit,
) {
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val providersMap = LocalDynamicProviders.current
    val order = LocalDynamicProvidersOrder.current

    val displayList = remember(providersMap, order) {
        val ordered = order.filter { it in providersMap }
        val missing = providersMap.keys - ordered.toSet()
        (ordered + missing).mapNotNull { providersMap[it] }
    }

    var showAdd by remember { mutableStateOf(false) }

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
        actions = {
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "添加供应商")
            }
        },
        content = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            DisplayText(text = "AI提供商", desc = "")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (displayList.isEmpty()) {
                    item {
                        Text(
                            text = "尚未添加任何供应商，点右上角「+」开始添加。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    items(displayList, key = { it.id }) { cfg ->
                        Column {
                            ProviderItem(
                                name = cfg.name,
                                description = when (cfg.kind) {
                                    ProviderKind.OPENAI -> "OpenAI · ${cfg.baseUrl}"
                                    ProviderKind.GOOGLE -> "Google · ${cfg.baseUrl}"
                                    ProviderKind.CLAUDE -> "Claude · ${cfg.baseUrl}"
                                },
                                enabled = cfg.enabled,
                                onToggleEnabled = {
                                    DynamicProvidersPreference.put(
                                        context, scope, cfg.copy(enabled = it)
                                    )
                                },
                                onClick = { onProviderClick(cfg.id) },
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

    if (showAdd) {
        AddProviderDialog(
            existingIds = providersMap.keys,
            onDismiss = { showAdd = false },
            onCreated = { createdId ->
                showAdd = false
                onProviderClick(createdId)
            },
        )
    }
}

@Composable
private fun ProviderItem(
    name: String,
    description: String,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggleEnabled)
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
