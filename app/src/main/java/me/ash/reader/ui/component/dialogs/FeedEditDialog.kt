package me.ash.reader.ui.component.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.ui.component.base.BottomDialog
import me.ash.reader.ui.component.base.TextFieldDialog

/**
 * 订阅源编辑对话框
 * 支持编辑订阅源的图标URL、名称、URL和全文解析选项
 */
@Composable
fun FeedEditDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    feed: Feed,
    onSave: (Feed) -> Unit
) {
    // 编辑状态
    var name by remember { mutableStateOf(feed.name) }
    var iconUrl by remember { mutableStateOf(feed.icon ?: "") }
    var feedUrl by remember { mutableStateOf(feed.url) }
    var isFullContent by remember { mutableStateOf(feed.isFullContent) }

    // 子对话框状态
    var showRenameDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }

    // 重命名对话框
    if (showRenameDialog) {
        TextFieldDialog(
            modifier = Modifier,
            properties = DialogProperties(),
            visible = true,
            readOnly = false,
            singleLine = true,
            title = "订阅源名称",
            icon = null,
            value = name,
            placeholder = "请输入订阅源名称",
            isPassword = false,
            errorText = "",
            dismissText = "取消",
            confirmText = "确认",
            onValueChange = { newValue: String -> name = newValue },
            onDismissRequest = { showRenameDialog = false },
            onConfirm = { newName: String ->
                name = newName
                showRenameDialog = false
            }
        )
    }

    // URL 编辑对话框
    if (showUrlDialog) {
        TextFieldDialog(
            modifier = Modifier,
            properties = DialogProperties(),
            visible = true,
            readOnly = false,
            singleLine = true,
            title = "订阅源 URL",
            icon = null,
            value = feedUrl,
            placeholder = "请输入订阅源 URL",
            isPassword = false,
            errorText = "",
            dismissText = "取消",
            confirmText = "确认",
            onValueChange = { newValue: String -> feedUrl = newValue },
            onDismissRequest = { showUrlDialog = false },
            onConfirm = { newUrl: String ->
                feedUrl = newUrl
                showUrlDialog = false
            }
        )
    }

    // 图标 URL 编辑对话框
    if (showIconDialog) {
        TextFieldDialog(
            modifier = Modifier,
            properties = DialogProperties(),
            visible = true,
            readOnly = false,
            singleLine = true,
            title = "图标 URL",
            icon = null,
            value = iconUrl,
            placeholder = "请输入图标 URL",
            isPassword = false,
            errorText = "",
            dismissText = "取消",
            confirmText = "确认",
            onValueChange = { newValue: String -> iconUrl = newValue },
            onDismissRequest = { showIconDialog = false },
            onConfirm = { newIconUrl ->
                iconUrl = newIconUrl
                showIconDialog = false
            }
        )
    }

    // 主编辑对话框
    if (visible) {
        BottomDialog(
            onDismiss = onDismiss,
            dialogContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 标题
                    Text(
                        text = "编辑订阅源",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 订阅源名称
                    EditItem(
                        label = "订阅源名称",
                        value = name,
                        onClick = { showRenameDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 图标 URL
                    EditItem(
                        label = "图标 URL",
                        value = iconUrl.ifEmpty { "未设置" },
                        onClick = { showIconDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 订阅源 URL
                    EditItem(
                        label = "订阅源 URL",
                        value = feedUrl,
                        onClick = { showUrlDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 全文解析开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "全文解析",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "开启后获取文章全文内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isFullContent,
                            onCheckedChange = { isFullContent = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 确认按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = {
                                val updatedFeed = feed.copy(
                                    name = name,
                                    icon = iconUrl.ifEmpty { null },
                                    url = feedUrl,
                                    isFullContent = isFullContent
                                )
                                onSave(updatedFeed)
                                onDismiss()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "确认",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "保存",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * 编辑项组件
 */
@Composable
private fun EditItem(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
