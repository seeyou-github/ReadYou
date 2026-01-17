package me.ash.reader.ui.component.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.ui.component.base.BottomDialog
import me.ash.reader.ui.ext.getDefaultGroupId

/**
 * 订阅源排序对话框
 * 支持用户通过点击选中订阅源，然后使用向上/向下按钮来调整顺序
 * 支持删除订阅源（带撤销功能）和编辑订阅源
 */
@Composable
fun FeedSortDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    groupWithFeedList: List<GroupWithFeed>,
    onUpdateFeed: suspend (Feed) -> Unit,
    onDeleteFeed: suspend (Feed) -> Unit,
    onEditFeed: (Feed) -> Unit,
    onFeedUpdated: (Feed) -> Unit = {} // 订阅源更新后的回调
) {
    val scope = rememberCoroutineScope()

    // 获取 default 分组 ID
    val accountId = groupWithFeedList.firstOrNull()?.group?.accountId ?: 0
    val defaultGroupId = accountId.getDefaultGroupId()

    // 用于撤销删除的临时存储（后进先出）
    data class DeletedFeed(val feed: Feed, val index: Int)
    val deletedFeeds = remember { mutableStateListOf<DeletedFeed>() }

    // 按分组组织 Feed，显示所有订阅源（包括 default 分组），并按 sortOrder 排序
    val feedList = remember(groupWithFeedList) {
        val feeds = mutableListOf<Feed>()
        groupWithFeedList
            .sortedBy { it.group.sortOrder }
            .forEach { groupWithFeed ->
                feeds.addAll(
                    groupWithFeed.feeds
                        .filter { it.groupId == groupWithFeed.group.id }
                        .sortedBy { it.sortOrder }
                )
            }
        feeds
    }

    // 使用 mutableStateListOf 支持直接修改
    val sortedFeeds = remember { mutableStateListOf<Feed>().apply { addAll(feedList) } }

    // 辅助函数：更新 sortedFeeds 中的订阅源
    fun updateFeedInList(updatedFeed: Feed) {
        val index = sortedFeeds.indexOfFirst { it.id == updatedFeed.id }
        if (index >= 0) {
            sortedFeeds[index] = updatedFeed
        }
        // 通知外部更新
        onFeedUpdated(updatedFeed)
    }

    // 选中的订阅源
    var selectedFeedIndex by remember { mutableStateOf(-1) }

    // 列表状态
    val listState = rememberLazyListState()

    // 显示对话框
    if (visible) {
        BottomDialog(
            onDismiss = {
                // 清空待删除列表，不执行删除
                deletedFeeds.clear()
                onDismiss()
            },
            dialogContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // 标题
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "订阅源排序",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 订阅源列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = sortedFeeds,
                            key = { index, feed -> "${feed.groupId}_${feed.id}" }
                        ) { index, feed ->
                            FeedSortItem(
                                feed = feed,
                                index = index,
                                isSelected = index == selectedFeedIndex,
                                onClick = { selectedFeedIndex = index },
                                onEdit = {
                                    onEditFeed(feed)
                                },
                                onDelete = {
                                    // 保存到已删除列表（后进先出）
                                    deletedFeeds.add(DeletedFeed(feed, index))
                                    // 从列表中移除（不真正删除）
                                    sortedFeeds.removeAt(index)
                                    if (selectedFeedIndex >= index) {
                                        selectedFeedIndex = maxOf(0, selectedFeedIndex - 1)
                                    }
                                }
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
                        // 左侧按钮组：向上/向下按钮
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedFeedIndex > 0) {
                                        // 向上移动
                                        val temp = sortedFeeds[selectedFeedIndex]
                                        sortedFeeds[selectedFeedIndex] = sortedFeeds[selectedFeedIndex - 1]
                                        sortedFeeds[selectedFeedIndex - 1] = temp
                                        selectedFeedIndex--
                                        // 滚动列表以保持可见性
                                        scope.launch {
                                            listState.animateScrollToItem(selectedFeedIndex)
                                        }
                                    }
                                },
                                enabled = selectedFeedIndex > 0,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (selectedFeedIndex > 0) {
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
                                    tint = if (selectedFeedIndex > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (selectedFeedIndex >= 0 && selectedFeedIndex < sortedFeeds.size - 1) {
                                        // 向下移动
                                        val temp = sortedFeeds[selectedFeedIndex]
                                        sortedFeeds[selectedFeedIndex] = sortedFeeds[selectedFeedIndex + 1]
                                        sortedFeeds[selectedFeedIndex + 1] = temp
                                        selectedFeedIndex++
                                        // 滚动列表以保持可见性
                                        scope.launch {
                                            listState.animateScrollToItem(selectedFeedIndex)
                                        }
                                    }
                                },
                                enabled = selectedFeedIndex >= 0 && selectedFeedIndex < sortedFeeds.size - 1,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (selectedFeedIndex >= 0 && selectedFeedIndex < sortedFeeds.size - 1) {
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
                                    tint = if (selectedFeedIndex >= 0 && selectedFeedIndex < sortedFeeds.size - 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        // 右侧按钮组：撤销删除 + 确认
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 撤销删除按钮（固定显示）
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (deletedFeeds.isNotEmpty()) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(
                                        enabled = deletedFeeds.isNotEmpty()
                                    ) {
                                        // 撤销删除（后进先出）
                                        scope.launch {
                                            deletedFeeds.removeLastOrNull()?.let { deletedItem ->
                                                // 更新 sortOrder 为原位置
                                                val restoredFeedWithSort = deletedItem.feed.copy(sortOrder = deletedItem.index)
                                                onUpdateFeed(restoredFeedWithSort)
                                                // 恢复到列表中
                                                val insertIndex = minOf(deletedItem.index, sortedFeeds.size)
                                                sortedFeeds.add(insertIndex, deletedItem.feed)
                                                selectedFeedIndex = insertIndex
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "撤销删除 (${deletedFeeds.size})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (deletedFeeds.isNotEmpty()) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
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
                                        // 保存所有排序并真正删除待删除的订阅源
                                        scope.launch {
                                            sortedFeeds.forEachIndexed { index, feed ->
                                                if (feed.sortOrder != index) {
                                                    onUpdateFeed(feed.copy(sortOrder = index))
                                                }
                                            }
                                            // 真正删除待删除列表中的订阅源
                                            deletedFeeds.forEach { deletedItem ->
                                                onDeleteFeed(deletedItem.feed)
                                            }
                                            onDismiss()
                                        }
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
            }
        )
    }
}

/**
 * 订阅源排序项
 */
@Composable
fun FeedSortItem(
    feed: Feed,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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

            // 订阅源名称
            Text(
                text = feed.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )

            // 编辑图标
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 删除图标
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}