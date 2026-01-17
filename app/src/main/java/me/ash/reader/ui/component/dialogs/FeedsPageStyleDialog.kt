package me.ash.reader.ui.component.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CoroutineScope

// 2026-01-21: 改用 BottomDialog 组件，防止滑动关闭
// 2026-01-21: 移除标题栏和关闭按钮
@Composable
fun FeedsPageStyleDialog(
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: CoroutineScope,
    onShowGroupSortDialog: () -> Unit = {}, // 2026-01-22: 新增参数，用于显示分组排序对话框
    onShowFeedSortDialog: () -> Unit = {}   // 2026-01-27: 新增参数，用于显示订阅源排序对话框
) {
    me.ash.reader.ui.component.base.BottomDialog(
        onDismiss = onDismiss,
        dialogContent = {
            FeedsPageStylePage(
                onDismiss = onDismiss,
                context = context,
                scope = scope,
                onShowGroupSortDialog = onShowGroupSortDialog,
                onShowFeedSortDialog = onShowFeedSortDialog
            )
        }
    )
}