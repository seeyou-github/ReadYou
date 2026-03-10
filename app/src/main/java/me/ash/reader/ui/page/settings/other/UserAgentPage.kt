package me.ash.reader.ui.page.settings.other

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.rememberCoroutineScope
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.UserAgentPreference
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.page.settings.SelectableSettingGroupItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun UserAgentPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    var dialogVisible by remember { mutableStateOf(false) }

    if (dialogVisible) {
        val textFieldState = rememberTextFieldState(settings.userAgent)
        TextFieldDialog(
            textFieldState = textFieldState,
            title = stringResource(R.string.user_agent_setting),
            placeholder = stringResource(R.string.user_agent_placeholder),
            singleLine = false,
            onDismissRequest = { dialogVisible = false },
            onConfirm = { value ->
                UserAgentPreference.put(context, scope, value)
                dialogVisible = false
            },
        )
    }

    RYScaffold(
        containerColor = selectedColorTheme?.backgroundColor
            ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        topBarColor = selectedColorTheme?.backgroundColor
            ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(
                        text = stringResource(R.string.user_agent_setting),
                        desc = stringResource(R.string.user_agent_setting_desc),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    SelectableSettingGroupItem(
                        title = stringResource(R.string.user_agent_current),
                        desc = settings.userAgent,
                        icon = Icons.Outlined.MoreHoriz,
                        onClick = { dialogVisible = true },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}
