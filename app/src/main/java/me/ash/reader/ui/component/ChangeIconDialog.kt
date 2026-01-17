package me.ash.reader.ui.component

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.ui.component.base.TextFieldDialog

@Composable
fun ChangeIconDialog(
    visible: Boolean = false,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
) {
    if (visible){
        val textFieldState = rememberTextFieldState(value)
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.text }.collect { onValueChange(it.toString()) }
        }
        TextFieldDialog(
            textFieldState = textFieldState,
            title = stringResource(R.string.change_icon),
            icon = Icons.Outlined.Image,
            placeholder = stringResource(R.string.icon_url_placeholder),
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
        )
    }
}