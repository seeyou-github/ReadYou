package me.ash.reader.ui.component.dialogs

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

// 2026-01-21: 新增阅读界面样式设置对话框
// 2026-01-21: 改用 BottomDialog 组件，防止滑动关闭
// 2026-01-21: 移除标题栏和关闭按钮
@Composable
fun ReadingPageStyleDialog(
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: CoroutineScope
) {
    me.ash.reader.ui.component.base.BottomDialog(
        onDismiss = onDismiss,
        dialogContent = {
            ReadingPageStylePage(
                onDismiss = onDismiss,
                context = context,
                scope = scope
            )
        }
    )
}