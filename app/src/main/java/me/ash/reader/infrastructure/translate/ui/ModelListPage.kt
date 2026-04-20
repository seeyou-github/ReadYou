package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.translate.ModelFetchService
import me.ash.reader.infrastructure.translate.TranslateProviders
import me.ash.reader.infrastructure.translate.model.ModelInfo
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.preference.CerebrasConfigPreference
import me.ash.reader.infrastructure.translate.preference.LocalCerebrasConfig
import me.ash.reader.infrastructure.translate.preference.LocalSiliconFlowConfig
import me.ash.reader.infrastructure.translate.preference.SiliconFlowConfigPreference
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

private const val OTHER_GROUP_NAME = "other"

private data class ModelGroup(
    val key: String,
    val title: String,
    val models: List<ModelInfo>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelListPage(
    providerId: String,
    onBack: () -> Unit,
    modelFetchService: ModelFetchService,
) {
    val context = LocalContext.current
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val scope = rememberCoroutineScope()

    val provider = TranslateProviders.getById(providerId)
    val config =
        when (providerId) {
            "siliconflow" -> LocalSiliconFlowConfig.current
            "cerebras" -> LocalCerebrasConfig.current
            else -> null
        }

    var searchQuery by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var enabledModels by remember { mutableStateOf(config?.enabledModels ?: emptyList()) }

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

    val filteredModels =
        remember(searchQuery, models) {
            if (searchQuery.isBlank()) {
                models
            } else {
                models.filter {
                    it.id.contains(searchQuery, ignoreCase = true) ||
                        it.name.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    val groupedModels =
        remember(filteredModels, enabledModels) {
            buildModelGroups(filteredModels, enabledModels)
        }

    val pagerState = rememberPagerState { groupedModels.size.coerceAtLeast(1) }

    LaunchedEffect(groupedModels.size) {
        if (groupedModels.isEmpty()) return@LaunchedEffect
        val lastIndex = groupedModels.lastIndex
        if (pagerState.currentPage > lastIndex) {
            pagerState.scrollToPage(lastIndex)
        }
    }

    fun saveEnabledModels(newEnabledModels: List<String>) {
        enabledModels = newEnabledModels
        val newConfig =
            TranslateProviderConfig(
                providerId = providerId,
                apiKey = config?.apiKey ?: "",
                rpm = config?.rpm ?: 10,
                enabledModels = newEnabledModels,
            )
        scope.launch {
            when (providerId) {
                "siliconflow" -> SiliconFlowConfigPreference.put(context, scope, newConfig)
                "cerebras" -> CerebrasConfigPreference.put(context, scope, newConfig)
            }
        }
    }

    fun toggleModel(modelId: String, enabled: Boolean) {
        val newEnabledModels =
            if (enabled) {
                (enabledModels + modelId).distinct()
            } else {
                enabledModels - modelId
            }
        saveEnabledModels(newEnabledModels)
    }

    RYScaffold(
        containerColor =
            selectedColorTheme?.backgroundColor
                ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        topBarColor =
            selectedColorTheme?.backgroundColor
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
            Column(modifier = Modifier.fillMaxSize()) {
                DisplayText(
                    text = "选择模型",
                    desc = "${provider?.name ?: ""} (${filteredModels.size}个模型)",
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索模型") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (groupedModels.isEmpty()) {
                    Text(
                        text = if (isLoading) "正在获取模型..." else "暂无可用模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage.coerceAtMost(groupedModels.lastIndex),
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        indicator = { tabPositions ->
                            val currentPage = pagerState.currentPage.coerceAtMost(groupedModels.lastIndex)
                            if (tabPositions.isNotEmpty()) {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentPage]),
                                )
                            }
                        },
                    ) {
                        groupedModels.forEachIndexed { index, group ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text("${group.title} (${group.models.size})")
                                },
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) { page ->
                        val group = groupedModels.getOrNull(page) ?: return@HorizontalPager
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = group.models,
                                key = { it.id },
                            ) { model ->
                                val isEnabled = enabledModels.contains(model.id)
                                ModelItem(
                                    modelName = displayModelName(model),
                                    isEnabled = isEnabled,
                                    onToggle = { toggleModel(model.id, it) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun buildModelGroups(
    models: List<ModelInfo>,
    enabledModels: List<String>,
): List<ModelGroup> {
    if (models.isEmpty()) return emptyList()

    val groupedByPrefix = linkedMapOf<String, MutableList<ModelInfo>>()
    val otherModels = mutableListOf<ModelInfo>()

    models.forEach { model ->
        val prefix = model.id.substringBefore("/", missingDelimiterValue = "")
        if (prefix.isBlank()) {
            otherModels += model
        } else {
            groupedByPrefix.getOrPut(prefix) { mutableListOf() }.add(model)
        }
    }

    val result = mutableListOf<ModelGroup>()

    groupedByPrefix.toSortedMap().forEach { (prefix, groupModels) ->
        if (groupModels.size > 2) {
            result +=
                ModelGroup(
                    key = prefix,
                    title = prefix,
                    models = sortModels(groupModels, enabledModels),
                )
        } else {
            otherModels += groupModels
        }
    }

    if (otherModels.isNotEmpty()) {
        result +=
            ModelGroup(
                key = OTHER_GROUP_NAME,
                title = OTHER_GROUP_NAME,
                models = sortModels(otherModels, enabledModels),
            )
    }

    return result
}

private fun sortModels(
    models: List<ModelInfo>,
    enabledModels: List<String>,
): List<ModelInfo> {
    return models.sortedWith(
        compareByDescending<ModelInfo> { enabledModels.contains(it.id) }
            .thenBy { it.id.lowercase() },
    )
}

private fun displayModelName(model: ModelInfo): String {
    return model.name.ifBlank { model.id.substringAfterLast("/") }
}

@Composable
private fun ModelItem(
    modelName: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
