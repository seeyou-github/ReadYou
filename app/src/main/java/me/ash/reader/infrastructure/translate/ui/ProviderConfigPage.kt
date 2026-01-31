package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.preference.CerebrasConfigPreference
import me.ash.reader.infrastructure.translate.preference.LocalCerebrasConfig
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.translate.preference.LocalSiliconFlowConfig
import me.ash.reader.infrastructure.translate.preference.SiliconFlowConfigPreference
import me.ash.reader.infrastructure.translate.preference.QuickTranslateModelPreference
import me.ash.reader.infrastructure.translate.TranslateProviders
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

/**
 * 提供商配置页面
 */
@Composable
fun ProviderConfigPage(
    providerId: String,
    onBack: () -> Unit,
    onFetchModels: () -> Unit
) {
    val context = LocalContext.current
    val colorThemes = LocalFeedsPageColorThemes.current
    val settings = LocalSettings.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val scope = rememberCoroutineScope()
    
    val provider = TranslateProviders.getById(providerId)
    val config = when (providerId) {
        "siliconflow" -> LocalSiliconFlowConfig.current
        "cerebras" -> LocalCerebrasConfig.current
        else -> null
    }
    
    // 获取当前快速翻译模型配置
    val quickModelConfig = settings.quickTranslateModel
    
    // 本地状态管理
    var apiKey by remember { mutableStateOf(config?.apiKey ?: "") }
    var rpm by remember { mutableIntStateOf(config?.rpm ?: 10) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // 实时保存配置
    fun saveConfig() {
        val newConfig = TranslateProviderConfig(
            providerId = providerId,
            apiKey = apiKey,
            rpm = rpm,
            enabledModels = config?.enabledModels ?: emptyList()
        )
        when (providerId) {
            "siliconflow" -> SiliconFlowConfigPreference.put(context, scope, newConfig)
            "cerebras" -> CerebrasConfigPreference.put(context, scope, newConfig)
        }
        
        // 如果当前选择的快速翻译模型属于此提供商，同步更新 API Key
        if (quickModelConfig?.provider == providerId) {
            val updatedModelConfig = TranslateModelConfig(
                provider = quickModelConfig.provider,
                model = quickModelConfig.model,
                apiKey = apiKey,  // 使用新的 API Key
                rpm = rpm
            )
            QuickTranslateModelPreference.put(context, scope, updatedModelConfig)
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
                        text = provider?.name ?: "提供商配置",
                        desc = ""
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        // API Key 输入框
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { 
                                apiKey = it
                                saveConfig()
                            },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "隐藏" else "显示"
                                    )
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // RPM 输入框
                        OutlinedTextField(
                            value = rpm.toString(),
                            onValueChange = { 
                                it.toIntOrNull()?.let { value -> 
                                    if (value > 0) {
                                        rpm = value
                                        saveConfig()
                                    }
                                }
                            },
                            label = { Text("RPM (每分钟请求限制)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 获取模型按钮
                        Button(
                            onClick = onFetchModels,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = apiKey.isNotBlank()
                        ) {
                            Text("获取模型")
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // 已启用模型列表
                        if (config?.enabledModels?.isNotEmpty() == true) {
                            Text(
                                text = "已启用模型 (${config.enabledModels.size}个)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            config.enabledModels.forEach { modelId ->
                                Text(
                                    text = modelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "暂无启用的模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}
