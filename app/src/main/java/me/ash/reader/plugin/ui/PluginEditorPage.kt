package me.ash.reader.plugin.ui

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.webview.RYWebView
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun PluginEditorPage(
    pluginId: String?,
    onBack: () -> Unit,
    viewModel: PluginEditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateValue()

    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

    val iconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val type = context.contentResolver.getType(uri) ?: "image/png"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            context.showToast("导入图标失败")
            return@rememberLauncherForActivityResult
        }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUri = "data:$type;base64,$base64"
        viewModel.updateState { copy(icon = dataUri) }
        context.showToast("图标已导入")
    }

    LaunchedEffect(pluginId) {
        viewModel.load(pluginId)
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
        actions = {
            FeedbackIconButton(
                imageVector = Icons.Outlined.Save,
                contentDescription = stringResource(R.string.save),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    viewModel.save(
                        onInvalid = { context.showToast(it) },
                        onSaved = { context.showToast(context.getString(R.string.saved)) }
                    )
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            DisplayText(
                text = stringResource(R.string.plugin_editor_title),
                desc = stringResource(R.string.plugin_editor_desc),
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.name,
                onValueChange = { viewModel.updateState { copy(name = it) } },
                label = { Text(text = stringResource(R.string.plugin_name)) },
                placeholder = { Text(text = stringResource(R.string.plugin_name_hint)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.subscribeUrl,
                    onValueChange = { viewModel.updateState { copy(subscribeUrl = it) } },
                    label = { Text(text = stringResource(R.string.plugin_subscribe_url)) },
                    placeholder = { Text(text = stringResource(R.string.plugin_subscribe_url_hint)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(
                    onClick = {
                        viewModel.downloadListHtml { message ->
                            context.showToast(message)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.plugin_download_html))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedIcon(
                    modifier = Modifier.size(40.dp),
                    feedName = state.name,
                    iconUrl = state.icon,
                    placeholderIcon = Icons.Outlined.Add,
                )
                Spacer(modifier = Modifier.size(10.dp))
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.icon,
                    onValueChange = { viewModel.updateState { copy(icon = it) } },
                    label = { Text(text = stringResource(R.string.plugin_icon)) },
                    placeholder = { Text(text = stringResource(R.string.plugin_icon_hint)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = { iconLauncher.launch(arrayOf("image/*")) }) {
                    Text(text = stringResource(R.string.plugin_import_icon))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.plugin_list_section), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.debugSelectors() }) {
                        Text(text = stringResource(R.string.plugin_debug_rules))
                    }
                    TextButton(onClick = { viewModel.testListPreview() }) {
                        Text(text = stringResource(R.string.plugin_test_list))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            selectorField(
                value = state.listTitleSelector,
                onValueChange = { viewModel.updateState { copy(listTitleSelector = it) } },
                label = stringResource(R.string.plugin_list_title_selector),
                hint = stringResource(R.string.plugin_selector_hint),
                required = true,
            )
            selectorField(
                value = state.listUrlSelector,
                onValueChange = { viewModel.updateState { copy(listUrlSelector = it) } },
                label = stringResource(R.string.plugin_list_url_selector),
                hint = stringResource(R.string.plugin_selector_hint),
                required = true,
            )
            selectorField(
                value = state.listImageSelector,
                onValueChange = { viewModel.updateState { copy(listImageSelector = it) } },
                label = stringResource(R.string.plugin_list_image_selector),
                hint = stringResource(R.string.plugin_selector_optional),
                required = false,
            )
            selectorField(
                value = state.listTimeSelector,
                onValueChange = { viewModel.updateState { copy(listTimeSelector = it) } },
                label = stringResource(R.string.plugin_list_time_selector),
                hint = stringResource(R.string.plugin_selector_optional),
                required = false,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.plugin_list_json_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.plugin_list_json_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            selectorField(
                value = state.listJsonArraySelector,
                onValueChange = { viewModel.updateState { copy(listJsonArraySelector = it) } },
                label = stringResource(R.string.plugin_list_json_array_selector),
                hint = stringResource(R.string.plugin_json_selector_hint),
                required = false,
            )
            selectorField(
                value = state.listJsonTitleSelector,
                onValueChange = { viewModel.updateState { copy(listJsonTitleSelector = it) } },
                label = stringResource(R.string.plugin_list_json_title_selector),
                hint = stringResource(R.string.plugin_json_selector_hint),
                required = false,
            )
            selectorField(
                value = state.listJsonUrlSelector,
                onValueChange = { viewModel.updateState { copy(listJsonUrlSelector = it) } },
                label = stringResource(R.string.plugin_list_json_url_selector),
                hint = stringResource(R.string.plugin_json_selector_hint),
                required = false,
            )
            selectorField(
                value = state.listJsonImageSelector,
                onValueChange = { viewModel.updateState { copy(listJsonImageSelector = it) } },
                label = stringResource(R.string.plugin_list_json_image_selector),
                hint = stringResource(R.string.plugin_json_selector_hint),
                required = false,
            )
            selectorField(
                value = state.listJsonTimeSelector,
                onValueChange = { viewModel.updateState { copy(listJsonTimeSelector = it) } },
                label = stringResource(R.string.plugin_list_json_time_selector),
                hint = stringResource(R.string.plugin_json_selector_hint),
                required = false,
            )
            if (state.testResult.isNotBlank()) {
                Text(
                    text = state.testResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.plugin_detail_section), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.testDetailPreview() }) {
                    Text(text = stringResource(R.string.plugin_test_detail))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.plugin_cache_content_on_update),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = state.cacheContentOnUpdate,
                    onCheckedChange = { viewModel.updateState { copy(cacheContentOnUpdate = it) } },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            selectorField(
                value = state.detailTitleSelector,
                onValueChange = { viewModel.updateState { copy(detailTitleSelector = it) } },
                label = stringResource(R.string.plugin_detail_title_selector),
                hint = stringResource(R.string.plugin_selector_optional),
                required = false,
            )
            selectorField(
                value = state.detailAuthorSelector,
                onValueChange = { viewModel.updateState { copy(detailAuthorSelector = it) } },
                label = stringResource(R.string.plugin_detail_author_selector),
                hint = stringResource(R.string.plugin_selector_optional),
                required = false,
            )
            selectorField(
                value = state.detailTimeSelector,
                onValueChange = { viewModel.updateState { copy(detailTimeSelector = it) } },
                label = stringResource(R.string.plugin_detail_time_selector),
                hint = stringResource(R.string.plugin_selector_optional),
                required = false,
            )
            state.detailContentSelectors.forEachIndexed { index, selector ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = selector,
                        onValueChange = { value ->
                            viewModel.updateState {
                                copy(
                                    detailContentSelectors =
                                        detailContentSelectors.toMutableList().apply {
                                            this[index] = value
                                        }
                                )
                            }
                        },
                        label = {
                            val suffix = if (index == 0) " *" else ""
                            Text(text = stringResource(R.string.plugin_detail_content_selector_index, index + 1) + suffix)
                        },
                        placeholder = { Text(text = stringResource(R.string.plugin_selector_hint)) },
                        singleLine = true,
                    )
                    if (index == 0) {
                        IconButton(
                            modifier = Modifier.padding(start = 4.dp),
                            onClick = {
                                viewModel.updateState {
                                    copy(detailContentSelectors = detailContentSelectors + "")
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = stringResource(R.string.add))
                        }
                    }
                }
            }
            selectorField(
                value = state.detailExcludeSelector,
                onValueChange = { viewModel.updateState { copy(detailExcludeSelector = it) } },
                label = stringResource(R.string.plugin_detail_exclude_selector),
                hint = stringResource(R.string.plugin_selector_optional),
                required = false,
            )

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (state.listPreviewVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.hideListPreview() },
            title = { Text(text = stringResource(R.string.plugin_test_list_title)) },
            text = {
                Box(modifier = Modifier.height(420.dp).fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.listPreviewItems) { _, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!item.image.isNullOrBlank()) {
                                    RYAsyncImage(
                                        modifier = Modifier.size(72.dp),
                                        data = item.image,
                                        refererUrl = item.link,
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title.ifBlank { item.link },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                    )
                                    if (!item.time.isNullOrBlank()) {
                                        Text(
                                            text = item.time,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideListPreview() }) {
                    Text(text = stringResource(R.string.close))
                }
            },
        )
    }

    if (state.detailPreviewVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDetailPreview() },
            title = { Text(text = stringResource(R.string.plugin_test_detail_title)) },
            text = {
                Box(modifier = Modifier.height(520.dp).fillMaxWidth()) {
                    if (state.detailPreviewHtml.isBlank()) {
                        Text(text = stringResource(R.string.plugin_detail_empty))
                    } else {
                        RYWebView(
                            modifier = Modifier.fillMaxSize(),
                            content = state.detailPreviewHtml,
                            refererDomain = state.subscribeUrl.extractDomain(),
                            enableJavaScript = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideDetailPreview() }) {
                    Text(text = stringResource(R.string.close))
                }
            },
        )
    }

    if (state.debugVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDebug() },
            title = { Text(text = stringResource(R.string.plugin_debug_title)) },
            text = {
                Box(modifier = Modifier.height(520.dp).fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.debugItems) { _, item ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    text = "${item.label} (${item.count})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = item.selector.ifBlank { stringResource(R.string.plugin_selector_empty) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                item.samples.forEach { sample ->
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideDebug() }) {
                    Text(text = stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun selectorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    required: Boolean,
) {
    val suffix = if (required) " *" else ""
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label + suffix) },
        placeholder = { Text(text = hint) },
        singleLine = true,
    )
}
