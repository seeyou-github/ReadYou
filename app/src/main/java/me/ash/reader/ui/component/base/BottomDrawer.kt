package me.ash.reader.ui.component.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

// 2026-01-19: 修改 BottomDrawer，添加不遮挡背景亮度的选项
// 修改目的：弹窗时保留文章列表界面的原始亮度，不进行暗化处理
// 2026-01-21: 移除 swipeEnabled 参数，因为 ModalBottomSheetLayout 不支持 gesturesEnabled
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomDrawer(
    modifier: Modifier = Modifier,
    drawerState: ModalBottomSheetState = androidx.compose.material.rememberModalBottomSheetState(
        ModalBottomSheetValue.Hidden
    ),
    sheetContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
    // 2026-01-19: 新增参数，控制是否显示遮挡层（暗化背景），放在末尾以保持兼容性
    // true=显示遮挡层（默认行为），false=不遮挡背景亮度
    scrimEnabled: Boolean = true
) {
    androidx.compose.material.ModalBottomSheetLayout(
        modifier = modifier,
        scrimColor = if (scrimEnabled) {
            // 2026-01-19: 默认遮挡层颜色（半透明黑色）
            ModalBottomSheetDefaults.scrimColor
        } else {
            // 2026-01-19: 不遮挡背景时使用透明色
            Color.Transparent
        },
        sheetShape = RoundedCornerShape(
            topStart = 28.0.dp,
            topEnd = 28.0.dp,
            bottomEnd = 0.0.dp,
            bottomStart = 0.0.dp
        ),
        sheetState = drawerState,
        sheetBackgroundColor = MaterialTheme.colorScheme.surface,
        sheetElevation = if (drawerState.isVisible) ModalBottomSheetDefaults.Elevation else 0.dp,
        sheetContent = {
            Surface(
                modifier = modifier,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp)
                ) {
                    Box {
                        Row(
                            modifier = modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = modifier
                                    .size(30.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    .zIndex(1f)
                            ) {}
                        }
                        Column {
                            Spacer(modifier = Modifier.height(40.dp))
                            sheetContent()
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }
        },
        content = content,
    )
}

// 2026-01-21: 新增 BottomDialog 组件，使用 Dialog 实现底部对话框
// 修改目的：防止滑动关闭，只能通过点击返回键或点击界面外关闭
// 特点：支持滚动和点击，不会意外滑动关闭，不遮挡底层界面亮度
@Composable
fun BottomDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dialogContent: @Composable ColumnScope.() -> Unit = {}
) {
    // 2026-01-21: 使用 remember 创建 MutableInteractionSource，避免重复创建
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    val contentInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 2026-01-21: 设置遮罩层透明度为0，不降低底层界面亮度
        val view = LocalView.current
        val dialogWindowProvider = view.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setDimAmount(0f)

        // 背景点击区域（点击空白区域关闭对话框），使用透明背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = backgroundInteractionSource,
                    indication = null
                ) { onDismiss() }
        ) {
            // 对话框内容
            Surface(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(
                        interactionSource = contentInteractionSource,
                        indication = null
                    ) { /* 阻止点击事件传递到背景 */ },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column {
                    // 顶部拖拽指示器
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .size(30.dp, 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {}
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 对话框内容
                    dialogContent()

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}