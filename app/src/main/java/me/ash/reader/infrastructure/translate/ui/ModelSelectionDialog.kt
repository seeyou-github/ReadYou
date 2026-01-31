package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.translate.model.ModelInfo
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.ModelFetchService
import me.ash.reader.infrastructure.translate.TranslateProviders

/**
 * 模型选择对话框
 */
@Composable
fun ModelSelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    onModelSelected: (TranslateModelConfig) -> Unit,
    modelFetchService: ModelFetchService = hiltViewModel<ModelSelectionViewModel>().modelFetchService
) {
    var currentStep by remember { mutableIntStateOf(0) } // 0: 选择提供商, 1: 输入API Key和选择模型
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var rpm by remember { mutableIntStateOf(10) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<ModelInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    IconButton(onClick = { 
                        currentStep = 0
                        errorMessage = null
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = title)
            }
        },
        text = {
            when (currentStep) {
                0 -> ProviderSelectionStep(
                    onProviderSelected = { providerId ->
                        selectedProvider = providerId
                        currentStep = 1
                    }
                )
                1 -> ModelSelectionStep(
                    providerId = selectedProvider!!,
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    rpm = rpm,
                    onRpmChange = { rpm = it },
                    models = models,
                    selectedModel = selectedModel,
                    onModelSelected = { selectedModel = it },
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    passwordVisible = passwordVisible,
                    onPasswordVisibleChange = { passwordVisible = it },
                    onFetchModels = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = modelFetchService.fetchModels(selectedProvider!!, apiKey)
                            result.onSuccess { 
                                models = it
                                if (it.isEmpty()) {
                                    errorMessage = "未找到可用模型"
                                }
                            }.onFailure { 
                                errorMessage = it.message ?: "获取模型列表失败"
                            }
                            isLoading = false
                        }
                    },
                    onRefreshModels = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = modelFetchService.fetchModels(selectedProvider!!, apiKey)
                            result.onSuccess { 
                                models = it
                            }.onFailure { 
                                errorMessage = it.message ?: "刷新模型列表失败"
                            }
                            isLoading = false
                        }
                    }
                )
            }
        },
        confirmButton = {
            if (currentStep == 1 && selectedModel != null) {
                Button(
                    onClick = {
                        onModelSelected(
                            TranslateModelConfig(
                                provider = selectedProvider!!,
                                model = selectedModel!!.id,
                                apiKey = apiKey,
                                rpm = rpm
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 选择提供商步骤
 */
@Composable
private fun ProviderSelectionStep(
    onProviderSelected: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.select_provider),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        TranslateProviders.ALL.forEach { provider ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProviderSelected(provider.id) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = provider.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 模型选择步骤
 */
@Composable
private fun ModelSelectionStep(
    providerId: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    rpm: Int,
    onRpmChange: (Int) -> Unit,
    models: List<ModelInfo>,
    selectedModel: ModelInfo?,
    onModelSelected: (ModelInfo) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onFetchModels: () -> Unit,
    onRefreshModels: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // API Key 输入
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(stringResource(R.string.enter_api_key)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // RPM 输入
        OutlinedTextField(
            value = rpm.toString(),
            onValueChange = { 
                it.toIntOrNull()?.let { value -> 
                    if (value > 0) onRpmChange(value)
                }
            },
            label = { Text(stringResource(R.string.rpm_limit)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 获取/刷新模型按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onFetchModels,
                enabled = apiKey.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (models.isEmpty()) stringResource(R.string.fetch_models) else stringResource(R.string.refresh_models))
                }
            }
            
            if (models.isNotEmpty()) {
                IconButton(onClick = onRefreshModels) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_models))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 错误信息
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // 模型列表
        if (models.isNotEmpty()) {
            Text(
                text = "选择模型 (${models.size}个可用)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(models) { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModelSelected(model) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedModel?.id == model.id,
                            onClick = { onModelSelected(model) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            model.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}