package me.ash.reader.ui.component.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

// 2026-01-18: 新增文章列表样式设置对话框
// 2026-01-19: 修改 scrimEnabled = false，不遮挡文章列表界面亮度
// 2026-01-21: 改用 BottomDialog 组件，防止滑动关闭
// 2026-01-21: 移除标题栏和关闭按钮
@Composable
fun ArticleListStyleDialog(
    onDismiss: () -> Unit,
    context: android.content.Context,
    scope: CoroutineScope
) {
    me.ash.reader.ui.component.base.BottomDialog(
        onDismiss = onDismiss,
        dialogContent = {
            ArticleListStylePage(
                onDismiss = onDismiss,
                context = context,
                scope = scope
            )
        }
    )
}
