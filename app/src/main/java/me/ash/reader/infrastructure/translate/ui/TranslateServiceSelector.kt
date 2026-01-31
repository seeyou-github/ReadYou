package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ash.reader.infrastructure.translate.TranslateProvider

/**
 * 翻译服务选择对话框
 *
 * @param visible 是否显示对话框
 * @param currentServiceId 当前选中的服务ID
 * @param onDismiss 关闭对话框回调
 * @param onServiceSelected 选择服务回调
 */
@Composable
fun TranslateServiceSelectorDialog(
    visible: Boolean,
    currentServiceId: String,
    onDismiss: () -> Unit,
    onServiceSelected: (String) -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择翻译服务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "选择一个翻译服务提供商",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                TranslateProvider.values().forEach { provider ->
                    ServiceOption(
                        serviceName = provider.serviceName,
                        isSelected = provider.serviceId == currentServiceId,
                        onClick = {
                            onServiceSelected(provider.serviceId)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 服务选项
 *
 * @param serviceName 服务名称
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun ServiceOption(
    serviceName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = serviceName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}