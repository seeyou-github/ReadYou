package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.translate.model.ModelInfo
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.preference.CerebrasConfigPreference
import me.ash.reader.infrastructure.translate.preference.LocalCerebrasConfig
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.translate.preference.LocalSiliconFlowConfig
import me.ash.reader.infrastructure.translate.preference.SiliconFlowConfigPreference
import me.ash.reader.infrastructure.translate.ModelFetchService
import me.ash.reader.infrastructure.translate.TranslateProviders
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

/**
 * 模型列表页面（带搜索）
 */
@Composable
fun ModelListPage(
    providerId: String,
    onBack: () -> Unit,
    modelFetchService: ModelFetchService
) {
    val context = LocalContext.current
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val scope = rememberCoroutineScope()
    
    val provider = TranslateProviders.getById(providerId)
    val config = when (providerId) {
        "siliconflow" -> LocalSiliconFlowConfig.current
        "cerebras" -> LocalCerebrasConfig.current
        else -> null
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var enabledModels by remember { mutableStateOf(config?.enabledModels ?: emptyList()) }
    
    // 获取模型列表
    LaunchedEffect(providerId, config?.apiKey) {
        if (config?.apiKey?.isNotBlank() == true) {
            isLoading = true
            errorMessage = null
            val result = modelFetchService.fetchModels(providerId, config.apiKey)
            result.onSuccess { 
                models = it
            }.onFailure { 
                errorMessage = it.message ?: "获取模型列表失败"
            }
            isLoading = false
        }
    }
    
    // 过滤模型
    val filteredModels = remember(searchQuery, models) {
        if (searchQuery.isBlank()) {
            models
        } else {
            models.filter { 
                it.id.contains(searchQuery, ignoreCase = true) || 
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // 保存启用状态
    fun toggleModel(modelId: String, enabled: Boolean) {
        val newEnabledModels = if (enabled) {
            enabledModels + modelId
        } else {
            enabledModels - modelId
        }
        enabledModels = newEnabledModels
        
        // 保存到 DataStore
        val newConfig = TranslateProviderConfig(
            providerId = providerId,
            apiKey = config?.apiKey ?: "",
            rpm = config?.rpm ?: 10,
            enabledModels = newEnabledModels
        )
        scope.launch {
            when (providerId) {
                "siliconflow" -> SiliconFlowConfigPreference.put(context, scope, newConfig)
                "cerebras" -> CerebrasConfigPreference.put(context, scope, newConfig)
            }
        }
    }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    DisplayText(
                        text = "选择模型",
                        desc = "${provider?.name ?: ""} (${filteredModels.size}个模型)"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        // 搜索框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("搜索模型") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 错误信息
                        errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // 加载指示器
                        if (isLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                items(filteredModels) { model ->
                    val isEnabled = enabledModels.contains(model.id)
                    ModelItem(
                        modelId = model.id,
                        modelName = model.name,
                        isEnabled = isEnabled,
                        onToggle = { toggleModel(model.id, it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}

@Composable
private fun ModelItem(
    modelId: String,
    modelName: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onToggle
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = modelId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
