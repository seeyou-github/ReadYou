package me.ash.reader.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.ui.component.base.ClipboardTextField
import me.ash.reader.ui.component.base.RYDialog

@Composable
fun ChangeIconDialog(
    visible: Boolean = false,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
    onImportClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    exportEnabled: Boolean = false,
) {
    if (visible) {
        val textFieldState = rememberTextFieldState(value)
        val focusManager = LocalFocusManager.current
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.text }.collect { onValueChange(it.toString()) }
        }
        RYDialog(
            visible = true,
            onDismissRequest = onDismissRequest,
            icon = { androidx.compose.material3.Icon(imageVector = Icons.Outlined.Image, contentDescription = null) },
            title = {
                Text(
                    text = stringResource(R.string.change_icon),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    ClipboardTextField(
                        state = textFieldState,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.icon_url_placeholder),
                        singleLine = true,
                        onConfirm = onConfirm,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(onClick = onImportClick) {
                            Text(text = stringResource(R.string.import_icon))
                        }
                        TextButton(
                            onClick = onExportClick,
                            enabled = exportEnabled,
                        ) {
                            Text(
                                text = stringResource(R.string.export_icon),
                                color =
                                    if (exportEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = textFieldState.text.isNotBlank(),
                    onClick = {
                        focusManager.clearFocus()
                        onConfirm(textFieldState.text.toString())
                    },
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color =
                            if (textFieldState.text.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                            },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}
