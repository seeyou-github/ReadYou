package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R

/**
 * 翻译错误对话框
 *
 * @param errorMessage 错误信息
 * @param onDismiss 关闭回调
 * @param onNavigateToSettings 导航到设置页面的回调
 */
@Composable
fun TranslateErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.translate_error_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = errorMessage)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.translate_error_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.retry))
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onNavigateToSettings()
                    }
                ) {
                    Text(text = stringResource(R.string.go_to_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
