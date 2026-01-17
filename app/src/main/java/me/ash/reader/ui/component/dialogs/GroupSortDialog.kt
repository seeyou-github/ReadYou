package me.ash.reader.ui.component.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.ui.ext.getDefaultGroupId

/**
 * 分组排序对话框
 * 支持用户通过点击选中分组，然后使用向上/向下按钮来调整分组顺序
 */
@Composable
fun GroupSortDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    groupWithFeedList: List<GroupWithFeed>,
    onUpdateGroup: suspend (Group) -> Unit
) {
    // 获取 default 分组 ID
    val accountId = groupWithFeedList.firstOrNull()?.group?.accountId ?: 0
    val defaultGroupId = accountId.getDefaultGroupId()

    // 过滤掉 default 分组，并按 sortOrder 排序
    val sortedGroups = remember(groupWithFeedList) {
        groupWithFeedList
            .filter { it.group.id != defaultGroupId }
            .sortedBy { it.group.sortOrder }
            .map { it.group }
            .toMutableList()
    }

    // 选中的分组
    var selectedGroupIndex by remember { mutableStateOf(-1) }

    // 列表状态
    val listState = rememberLazyListState()

    // 保存排序
    val saveSortOrder = {
        scope.launch {
            // 更新每个分组的 sortOrder
            sortedGroups.forEachIndexed { index, group ->
                val updatedGroup = group.copy(sortOrder = index)
                onUpdateGroup(updatedGroup)
            }
        }
    }

    // 显示对话框
    if (visible) {
        me.ash.reader.ui.component.base.BottomDialog(
            onDismiss = {
                saveSortOrder()
                onDismiss()
            },
            dialogContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // 标题
                    Text(
                        text = "分组排序",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 分组列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = sortedGroups,
                            key = { index, group -> group.id }
                        ) { index, group ->
                            GroupSortItem(
                                group = group,
                                index = index,
                                isSelected = index == selectedGroupIndex,
                                onClick = { selectedGroupIndex = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 控制按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 向上/向下按钮
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedGroupIndex > 0) {
                                        // 向上移动
                                        val temp = sortedGroups[selectedGroupIndex]
                                        sortedGroups[selectedGroupIndex] = sortedGroups[selectedGroupIndex - 1]
                                        sortedGroups[selectedGroupIndex - 1] = temp
                                        selectedGroupIndex--
                                    }
                                },
                                enabled = selectedGroupIndex > 0,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (selectedGroupIndex > 0) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "向上移动",
                                    tint = if (selectedGroupIndex > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (selectedGroupIndex >= 0 && selectedGroupIndex < sortedGroups.size - 1) {
                                        // 向下移动
                                        val temp = sortedGroups[selectedGroupIndex]
                                        sortedGroups[selectedGroupIndex] = sortedGroups[selectedGroupIndex + 1]
                                        sortedGroups[selectedGroupIndex + 1] = temp
                                        selectedGroupIndex++
                                    }
                                },
                                enabled = selectedGroupIndex >= 0 && selectedGroupIndex < sortedGroups.size - 1,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (selectedGroupIndex >= 0 && selectedGroupIndex < sortedGroups.size - 1) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "向下移动",
                                    tint = if (selectedGroupIndex >= 0 && selectedGroupIndex < sortedGroups.size - 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        // 确认按钮
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    saveSortOrder()
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "确认",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "确认",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

/**
 * 分组排序项
 */
@Composable
private fun GroupSortItem(
    group: Group,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 分组名称
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}