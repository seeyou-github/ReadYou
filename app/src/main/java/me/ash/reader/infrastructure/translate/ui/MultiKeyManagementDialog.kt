package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.ash.reader.infrastructure.translate.model.ApiKeyEntry
import me.ash.reader.infrastructure.translate.model.LoadBalanceStrategy
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import java.util.UUID

/**
 * 多 Key 管理对话框（对应图 2）。
 *
 * - 顶部：标题 / 批量启用 / 批量删除 / 关闭
 * - 「负载均衡策略」下拉：轮询 / 随机
 * - 列表：每行展示脱敏 key、启用 Switch、收藏、编辑、删除
 * - 底部「添加」按钮：新增 Key
 */
@Composable
fun MultiKeyManagementDialog(
    cfg: TranslateProviderConfig,
    onDismiss: () -> Unit,
    onUpdate: (List<ApiKeyEntry>, LoadBalanceStrategy) -> Unit,
) {
    var keys by remember(cfg.id) { mutableStateOf(cfg.keys) }
    var strategy by remember(cfg.id) { mutableStateOf(cfg.loadBalance) }
    var strategyMenu by remember { mutableStateOf(false) }

    var editingKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun commit(newKeys: List<ApiKeyEntry>, newStrategy: LoadBalanceStrategy = strategy) {
        keys = newKeys
        strategy = newStrategy
        onUpdate(newKeys, newStrategy)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "多Key管理",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = {
                        commit(keys.map { it.copy(enabled = true) })
                    }) { Icon(Icons.Default.Favorite, contentDescription = "全部启用") }
                    IconButton(onClick = {
                        commit(emptyList())
                    }) { Icon(Icons.Default.DeleteSweep, contentDescription = "全部删除") }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 负载均衡策略
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("负载均衡策略", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { strategyMenu = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(when (strategy) {
                                LoadBalanceStrategy.ROUND_ROBIN -> "轮询"
                                LoadBalanceStrategy.RANDOM -> "随机"
                            })
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = strategyMenu,
                            onDismissRequest = { strategyMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("轮询") },
                                onClick = {
                                    strategyMenu = false
                                    commit(keys, LoadBalanceStrategy.ROUND_ROBIN)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("随机") },
                                onClick = {
                                    strategyMenu = false
                                    commit(keys, LoadBalanceStrategy.RANDOM)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 列表
                if (keys.isEmpty()) {
                    Text(
                        text = "尚未添加 Key。多 Key 列表为空时，会使用上方主 API Key 发起请求。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(keys, key = { it.id }) { entry ->
                            KeyRow(
                                entry = entry,
                                onToggle = { newEnabled ->
                                    commit(keys.map { if (it.id == entry.id) it.copy(enabled = newEnabled) else it })
                                },
                                onFavorite = {
                                    commit(keys.map { if (it.id == entry.id) it.copy(favorite = !it.favorite) else it })
                                },
                                onEdit = { editingKey = entry },
                                onDelete = { commit(keys.filter { it.id != entry.id }) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("添加")
                }
            }
        }
    }

    if (showAddDialog) {
        KeyEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { newKeyText ->
                showAddDialog = false
                if (newKeyText.isNotBlank()) {
                    commit(keys + ApiKeyEntry(id = UUID.randomUUID().toString(), key = newKeyText.trim()))
                }
            },
        )
    }
    editingKey?.let { entry ->
        KeyEditDialog(
            initial = entry.key,
            onDismiss = { editingKey = null },
            onConfirm = { newKeyText ->
                editingKey = null
                commit(keys.map { if (it.id == entry.id) it.copy(key = newKeyText.trim()) else it })
            },
        )
    }
}

@Composable
private fun KeyRow(
    entry: ApiKeyEntry,
    onToggle: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(enabled = entry.enabled && entry.key.isNotBlank())
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Text(
            text = maskKey(entry.key),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = entry.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (entry.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (entry.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusBadge(enabled: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (enabled) Color(0xFF1B5E20).copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (enabled) "正常" else "停用",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun maskKey(key: String): String {
    if (key.isBlank()) return "(空)"
    val trimmed = key.trim()
    if (trimmed.length <= 8) return trimmed.first().toString() + "···" + trimmed.last()
    return trimmed.take(4) + "····" + trimmed.takeLast(4)
}

@Composable
private fun KeyEditDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 Key" else "编辑 Key") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
