package me.ash.reader.ui.page.settings.blacklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ash.reader.R
import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

/**
 * 2026-01-24: 关键词黑名单管理页面
 * 允许用户添加、删除关键词过滤规则
 * 与账户无关，仅和订阅源相关
 * 支持多订阅源匹配
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlacklistPage(
    onBack: () -> Unit,
    viewModel: BlacklistViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    
    // 获取颜色主题
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 标题和描述
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    DisplayText(
                        text = stringResource(R.string.blacklist),
                        desc = stringResource(R.string.blacklist_desc),
                    )
                }

                // 新增关键词区域
                item {
                    AddKeywordSection(
                        keyword = uiState.newKeyword,
                        onKeywordChange = viewModel::updateNewKeyword,
                        selectedFeedUrls = uiState.selectedFeedUrls,
                        availableFeeds = uiState.availableFeeds,
                        onFeedToggle = viewModel::toggleFeedSelection,
                        onClearSelection = viewModel::clearFeedSelection,
                        onAdd = { viewModel.addKeyword(uiState.newKeyword, uiState.selectedFeedUrls) },
                    )
                }

                // 关键词列表标题
                if (keywords.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.blacklist_keywords_count, keywords.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 关键词列表
                items(
                    items = keywords,
                    key = { it.id },
                ) { keyword ->
                    BlacklistKeywordItem(
                        keyword = keyword,
                        onToggleEnabled = { viewModel.toggleKeywordEnabled(keyword.id) },
                        onDelete = { viewModel.deleteKeyword(keyword.id) },
                    )
                }

                // 底部占位
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}

/**
 * 2026-01-24: 新增关键词输入区域
 * 支持多订阅源选择
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddKeywordSection(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    selectedFeedUrls: Set<String>,
    availableFeeds: List<Feed>,
    onFeedToggle: (String) -> Unit,
    onClearSelection: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 关键词输入框
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.blacklist_keyword_hint)) },
                singleLine = true,
                enabled = availableFeeds.isNotEmpty(),
            )

            // 添加按钮
            TextButton(
                onClick = onAdd,
                enabled = keyword.isNotBlank() && availableFeeds.isNotEmpty(),
            ) {
                Text(stringResource(R.string.add))
            }
        }

        // 订阅源多选区域
        if (availableFeeds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.blacklist_scope),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearSelection) {
                    Text(stringResource(R.string.clear))
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 全部订阅源选项（点击取消所有选择）
                FilterChip(
                    selected = selectedFeedUrls.isEmpty(),
                    onClick = onClearSelection,
                    label = { Text(stringResource(R.string.blacklist_all_feeds)) },
                )
                // 各订阅源多选芯片
                availableFeeds.forEach { feed ->
                    FilterChip(
                        selected = selectedFeedUrls.contains(feed.url),
                        onClick = { onFeedToggle(feed.url) },
                        label = { Text(feed.name) },
                    )
                }
            }
        } else {
            // 无订阅源时的提示
            Text(
                text = stringResource(R.string.blacklist_no_feeds_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 2026-01-24: 关键词列表项
 */
@Composable
private fun BlacklistKeywordItem(
    keyword: BlacklistKeyword,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 启用/禁用勾选框
            Checkbox(
                checked = keyword.enabled,
                onCheckedChange = { onToggleEnabled() },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = keyword.keyword,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (keyword.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = formatFeedScope(keyword),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 格式化显示作用范围
 */
@Composable
fun formatFeedScope(keyword: BlacklistKeyword): String {
    return when {
        keyword.feedUrls.isNullOrBlank() -> stringResource(R.string.blacklist_all_feeds)
        else -> {
            val urls = keyword.feedUrls.split(",").filter { it.isNotBlank() }
            val names = keyword.feedNames?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            if (urls.size == 1 && names.isNotEmpty()) {
                names.first()
            } else if (urls.size <= 2 && names.size == urls.size) {
                names.joinToString(", ")
            } else {
                stringResource(R.string.blacklist_multi_feeds_count, urls.size)
            }
        }
    }
}