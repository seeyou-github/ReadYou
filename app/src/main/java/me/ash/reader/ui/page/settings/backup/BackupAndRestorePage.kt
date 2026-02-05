package me.ash.reader.ui.page.settings.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.getCurrentVersion
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun BackupAndRestorePage(
    onBack: () -> Unit,
    viewModel: BackupAndRestoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState = viewModel.backupUiState.collectAsStateValue()

    // Show backup success/error toast
    LaunchedEffect(uiState.backupSuccess, uiState.backupError) {
        when {
            uiState.backupSuccess -> {
                Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
            }
            uiState.backupError != null -> {
                Toast.makeText(context, context.getString(R.string.backup_failed, uiState.backupError), Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(uiState.restoreSuccess, uiState.restoreError) {
        when {
            uiState.restoreSuccess -> {
                Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
            }
            uiState.restoreError != null -> {
                Toast.makeText(context, context.getString(R.string.restore_failed, uiState.restoreError), Toast.LENGTH_LONG).show()
            }
        }
    }

    // 瀵煎叆瀵煎嚭 Launchers
    val exportJSONLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MimeType.JSON)
    ) { result ->
        viewModel.exportPreferencesAsJSON(context) { byteArray ->
            result?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(byteArray)
                }
            }
        }
    }

    val importJSONLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let { uri ->
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            viewModel.tryImport(context, inputStream.readBytes())
        }
    }}

    val oneClickBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MimeType.JSON)
    ) { result ->
        result?.let { uri ->
            viewModel.performOneClickBackup(context, uri)
        }
    }

    val oneClickRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let { uri ->
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            viewModel.tryRestoreOneClick(context, inputStream.readBytes())
        }
    }}

    val exportOPMLLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MimeType.ANY)
    ) { result ->
        viewModel.exportAsOPML(context) { string ->
            result?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(string.toByteArray())
                }
            }
        }
    }

    val exportKeywordsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MimeType.JSON)
    ) { result ->
        viewModel.exportKeywordsAsJSON(context) { byteArray ->
            result?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(byteArray)
                }
            }
        }
    }

    val importKeywordsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let { uri ->
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            viewModel.tryImportKeywords(context, inputStream.readBytes())
        }
    }}

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
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
                        text = stringResource(R.string.backup_and_restore),
                        desc = stringResource(R.string.backup_and_restore_desc)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.one_click_backup)
                    )
                    Text(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        text = stringResource(R.string.one_click_backup_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isBackingUp && !uiState.isRestoring,
                            onClick = { oneClickBackupFileLauncher(context, oneClickBackupLauncher) },
                        ) {
                            if (uiState.isBackingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(text = stringResource(R.string.one_click_backup))
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isRestoring && !uiState.isBackingUp,
                            onClick = { oneClickRestoreLauncher.launch(arrayOf(MimeType.ANY)) },
                        ) {
                            if (uiState.isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(text = stringResource(R.string.one_click_restore))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // JSON 瀵煎叆瀵煎嚭
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.app_preferences)
                    )
                    SettingItem(
                        title = stringResource(R.string.import_from_json),
                        onClick = { importJSONLauncher.launch(arrayOf(MimeType.ANY)) }
                    )
                    SettingItem(
                        title = stringResource(R.string.export_as_json),
                        onClick = { preferenceFileLauncher(context, exportJSONLauncher) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // OPML 瀵煎嚭
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.subscriptions)
                    )
                    SettingItem(
                        title = stringResource(R.string.export_as_opml),
                        onClick = { viewModel.showExportOPMLModeDialog() }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 鍏抽敭瀛楀鍏ュ鍑?
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.keywords)
                    )
                    SettingItem(
                        title = stringResource(R.string.import_keywords_from_json),
                        onClick = { importKeywordsLauncher.launch(arrayOf(MimeType.ANY)) }
                    )
                    SettingItem(
                        title = stringResource(R.string.export_keywords_as_json),
                        onClick = { keywordsFileLauncher(context, exportKeywordsLauncher) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    // 瀵硅瘽妗?
    RYDialog(
        visible = uiState.warningDialogVisible,
        onDismissRequest = { viewModel.hideWarningDialog() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = stringResource(R.string.warning),
            )
        },
        title = { Text(text = stringResource(R.string.warning)) },
        text = { Text(text = stringResource(R.string.invalid_json_file_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.importPreferencesFromJSON(context, uiState.byteArray)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideWarningDialog() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    RYDialog(
        visible = uiState.keywordsWarningDialogVisible,
        onDismissRequest = { viewModel.hideKeywordsWarningDialog() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = stringResource(R.string.warning),
            )
        },
        title = { Text(text = stringResource(R.string.warning)) },
        text = {
            if (uiState.keywordsImportCount >= 0) {
                Text(text = context.getString(R.string.import_keywords_warning, uiState.keywordsImportCount))
            } else {
                Text(text = stringResource(R.string.invalid_keywords_json_warning))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.importKeywordsFromJSON(context, uiState.keywordsByteArray)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideKeywordsWarningDialog() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    RYDialog(
        visible = uiState.oneClickRestoreDialogVisible,
        onDismissRequest = { viewModel.hideOneClickRestoreDialog() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = stringResource(R.string.warning),
            )
        },
        title = { Text(text = stringResource(R.string.warning)) },
        text = { Text(text = stringResource(R.string.one_click_restore_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.performOneClickRestore(context, uiState.oneClickRestoreByteArray)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideOneClickRestoreDialog() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    RYDialog(
        visible = uiState.exportOPMLModeDialogVisible,
        title = {
            Text(
                text = stringResource(R.string.export_as_opml),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            LazyColumn {
                item {
                    Text(text = stringResource(R.string.additional_info_desc))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                items(
                    listOf(
                        RadioDialogOption(
                            text = context.getString(R.string.include_additional_info),
                            selected = uiState.exportOPMLMode == ExportOPMLMode.ATTACH_INFO,
                        ) {
                            viewModel.changeExportOPMLMode(ExportOPMLMode.ATTACH_INFO)
                        },
                        RadioDialogOption(
                            text = context.getString(R.string.exclude),
                            selected = uiState.exportOPMLMode == ExportOPMLMode.NO_ATTACH,
                        ) {
                            viewModel.changeExportOPMLMode(ExportOPMLMode.NO_ATTACH)
                        },
                    )
                ) { option ->
                    androidx.compose.foundation.layout.Row(
                        modifier =
                            Modifier.fillMaxWidth().clip(CircleShape).clickable {
                                option.onClick()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.selected, onClick = { option.onClick() })
                        Text(
                            modifier = Modifier.padding(start = 6.dp),
                            text = option.text,
                            style =
                                MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.hideExportOPMLModeDialog()
                    subscriptionOPMLFileLauncher(context, exportOPMLLauncher)
                }
            ) {
                Text(stringResource(R.string.export))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideExportOPMLModeDialog() }) {
                Text(stringResource(R.string.cancel))
            }
        },
        onDismissRequest = { viewModel.hideExportOPMLModeDialog() },
    )
}

private fun preferenceFileLauncher(
    context: Context,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
) {
    launcher.launch(
        "preferences.json"
    )
}

private fun oneClickBackupFileLauncher(
    context: Context,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
) {
    launcher.launch(
        "read_you_backup.json"
    )
}

private fun subscriptionOPMLFileLauncher(
    context: Context,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
) {
    launcher.launch(
        "subscription.xml"
    )
}

private fun keywordsFileLauncher(
    context: Context,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
) {
    launcher.launch(
        "keyword.json"
    )
}


