package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.preference.DynamicProvidersPreference
import me.ash.reader.infrastructure.translate.preference.LocalDynamicProviders
import me.ash.reader.infrastructure.translate.preference.QuickTranslateModelPreference
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

private fun displayEnabledModelName(modelId: String): String = modelId.substringAfterLast("/")

/**
 * 通用「供应商详情」页：编辑名称 / API Key / Base URL / RPM / 启用开关 / 已启用模型 / 多 Key 管理 / 删除。
 */
@Composable
fun ProviderConfigPage(
    providerId: String,
    onBack: () -> Unit,
    onFetchModels: () -> Unit,
) {
    val context = LocalContext.current
    val colorThemes = LocalFeedsPageColorThemes.current
    val settings = LocalSettings.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val scope = rememberCoroutineScope()

    val providersMap = LocalDynamicProviders.current
    val cfg = providersMap[providerId]
    if (cfg == null) {
        // 配置已被删除：自动返回上一页
        LaunchedEffect(providerId) { onBack() }
        return
    }

    val quickModelConfig = settings.quickTranslateModel

    var name by remember(cfg.id) { mutableStateOf(cfg.name) }
    var apiKey by remember(cfg.id) { mutableStateOf(cfg.apiKey) }
    var baseUrl by remember(cfg.id) { mutableStateOf(cfg.baseUrl) }
    var chatPath by remember(cfg.id) { mutableStateOf(cfg.chatPath) }
    var useResponses by remember(cfg.id) { mutableStateOf(cfg.useResponsesApi) }
    var enabled by remember(cfg.id) { mutableStateOf(cfg.enabled) }
    var rpm by remember(cfg.id) { mutableIntStateOf(cfg.rpm) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showMultiKey by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun persist(
        nextName: String = name,
        nextEnabled: Boolean = enabled,
        nextApiKey: String = apiKey,
        nextBaseUrl: String = baseUrl,
        nextChatPath: String = chatPath,
        nextUseResponses: Boolean = useResponses,
        nextRpm: Int = rpm,
        nextEnabledModels: List<String> = cfg.enabledModels,
        nextKeys: List<me.ash.reader.infrastructure.translate.model.ApiKeyEntry> = cfg.keys,
        nextLoadBalance: me.ash.reader.infrastructure.translate.model.LoadBalanceStrategy = cfg.loadBalance,
    ) {
        val updated = cfg.copy(
            name = nextName.ifBlank { cfg.id },
            enabled = nextEnabled,
            apiKey = nextApiKey,
            baseUrl = nextBaseUrl,
            chatPath = nextChatPath,
            useResponsesApi = nextUseResponses,
            rpm = nextRpm,
            enabledModels = nextEnabledModels,
            keys = nextKeys,
            loadBalance = nextLoadBalance,
        )
        DynamicProvidersPreference.put(context, scope, updated)

        // 同步刷新当前快翻模型快照里的 apiKey / baseUrl
        if (quickModelConfig?.provider == providerId) {
            QuickTranslateModelPreference.put(
                context, scope,
                quickModelConfig.copy(
                    apiKey = nextApiKey,
                    rpm = nextRpm,
                    baseUrl = nextBaseUrl,
                    chatPath = nextChatPath,
                    useResponsesApi = nextUseResponses,
                )
            )
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
                    DisplayText(text = cfg.name, desc = when (cfg.kind) {
                        ProviderKind.OPENAI -> "OpenAI 兼容协议"
                        ProviderKind.GOOGLE -> "Google Gemini"
                        ProviderKind.CLAUDE -> "Anthropic Claude"
                    })
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {

                        // 启用开关
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用此供应商", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = enabled, onCheckedChange = {
                                enabled = it; persist(nextEnabled = it)
                            })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; persist(nextName = it) },
                            label = { Text("名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; persist(nextApiKey = it) },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; persist(nextBaseUrl = it) },
                            label = { Text("Base URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (cfg.kind == ProviderKind.OPENAI) {
                            OutlinedTextField(
                                value = chatPath,
                                onValueChange = { chatPath = it; persist(nextChatPath = it) },
                                label = { Text("API 路径") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Use Responses API", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(checked = useResponses, onCheckedChange = {
                                    useResponses = it; persist(nextUseResponses = it)
                                })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = rpm.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { value ->
                                    if (value > 0) {
                                        rpm = value
                                        persist(nextRpm = value)
                                    }
                                }
                            },
                            label = { Text("RPM (每分钟请求限制)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMultiKey = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("多 Key 管理", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (cfg.keys.isEmpty()) "未配置（使用上方主 Key）"
                                    else "${cfg.keys.count { it.enabled }} / ${cfg.keys.size} 已启用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onFetchModels,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = apiKey.isNotBlank() || cfg.keys.any { it.enabled && it.key.isNotBlank() },
                        ) { Text("获取模型") }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (cfg.enabledModels.isNotEmpty()) {
                            Text(
                                text = "已启用模型 (${cfg.enabledModels.size}个)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            cfg.enabledModels.forEach { modelId ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = displayEnabledModelName(modelId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        persist(nextEnabledModels = cfg.enabledModels - modelId)
                                    }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "删除模型")
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "暂无启用的模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                            Text("删除供应商")
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

    if (showMultiKey) {
        MultiKeyManagementDialog(
            cfg = cfg,
            onDismiss = { showMultiKey = false },
            onUpdate = { newKeys, newStrategy ->
                persist(nextKeys = newKeys, nextLoadBalance = newStrategy)
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除供应商？") },
            text = { Text("删除「${cfg.name}」后将无法恢复其 API Key 与启用模型列表。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    DynamicProvidersPreference.remove(context, scope, providerId)
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
@Suppress("unused")
private fun PlaceholderColumn() {
    // 占位以保持 Arrangement 引用
    val _u = Arrangement.Top
}
