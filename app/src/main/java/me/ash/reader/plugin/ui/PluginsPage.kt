package me.ash.reader.plugin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.theme.palette.onLight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PluginsPage(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: PluginListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val rules = viewModel.rules.collectAsStateValue()
    var pendingExportContent by remember { mutableStateOf("") }
    var expandedMenuRuleId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { result ->
        result?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pendingExportContent.toByteArray())
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = inputStream.readBytes().decodeToString()
                viewModel.importRule(json) { message ->
                    context.showToast(message)
                }
            }
        }
    }

    RYScaffold(
        containerColor = selectedColorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        topBarColor = selectedColorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
    ) {
        LazyColumn {
            item {
                DisplayText(text = stringResource(R.string.local_rule_list), desc = stringResource(R.string.local_rule_list_desc))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .clickable { onAdd() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.create_local_rule),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            modifier = Modifier.padding(start = 12.dp),
                            text = stringResource(R.string.create_local_rule),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .clickable { importLauncher.launch(arrayOf("application/json", "text/*")) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = stringResource(R.string.import_local_rule),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            modifier = Modifier.padding(start = 12.dp),
                            text = stringResource(R.string.import_local_rule),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(rules, key = { it.id }) { rule ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .combinedClickable(
                                onClick = { onEdit(rule.id) },
                                onLongClick = { expandedMenuRuleId = rule.id },
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FeedIcon(
                                feedName = rule.name,
                                iconUrl = rule.icon,
                                placeholderIcon = Icons.Outlined.Build,
                                size = 42.dp,
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = rule.name.ifBlank { rule.subscribeUrl },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = rule.subscribeUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }

                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { viewModel.toggleRule(rule, it) },
                        )
                    }

                    DropdownMenu(
                        expanded = expandedMenuRuleId == rule.id,
                        onDismissRequest = { expandedMenuRuleId = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.export_local_rule_json)) },
                            onClick = {
                                expandedMenuRuleId = null
                                scope.launch {
                                    val export = viewModel.exportRule(rule)
                                    if (export == null) {
                                        context.showToast(context.getString(R.string.export_failed))
                                        return@launch
                                    }
                                    pendingExportContent = export.content
                                    exportLauncher.launch(export.fileName)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.delete)) },
                            onClick = {
                                expandedMenuRuleId = null
                                viewModel.deleteRule(rule)
                                context.showToast(context.getString(R.string.plugin_deleted))
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
