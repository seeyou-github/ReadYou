package me.ash.reader.ui.page.home.feeds.drawer.feed

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import timber.log.Timber
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.ui.component.ChangeIconDialog
import me.ash.reader.ui.component.ChangeUrlDialog
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.BottomDrawer
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.roundClick
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.page.home.feeds.FeedOptionView
import me.ash.reader.ui.page.home.feeds.drawer.feed.IconSearchResult
import me.ash.reader.plugin.PluginConstants

@Composable
fun FeedOptionDrawer(
    drawerState: ModalBottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden),
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val scope = rememberCoroutineScope()
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()
    val iconSearchResult = feedOptionUiState.iconSearchResult
    LaunchedEffect(iconSearchResult) {
        when (iconSearchResult) {
            IconSearchResult.NotFound -> {
                context.showToast(context.getString(R.string.icon_not_found))
                feedOptionViewModel.consumeIconSearchResult()
            }
            IconSearchResult.Failed -> {
                context.showToast(context.getString(R.string.icon_search_failed))
                feedOptionViewModel.consumeIconSearchResult()
            }
            null -> Unit
        }
    }

    val feed = feedOptionUiState.feed
    val toastString = stringResource(R.string.rename_toast, feedOptionUiState.newName)
    val isLocalRule = feed?.url?.startsWith(PluginConstants.PLUGIN_URL_PREFIX) == true
    val exportLabel =
        if (isLocalRule) stringResource(R.string.export_local_rule_json)
        else stringResource(R.string.export_feed_as_opml)

    var pendingExportContent by remember { mutableStateOf("") }

    val exportFeedOpmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { result ->
        result?.let { uri ->
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportContent.toByteArray())
                }
            }
        }
    }

    val exportFeedJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { result ->
        result?.let { uri ->
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportContent.toByteArray())
                }
            }
        }
    }

    val importIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { feedOptionViewModel.importIconFromUri(context, it) }
    }

    val exportIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
    ) { uri ->
        uri?.let { feedOptionViewModel.exportIconToUri(context, it) }
    }


    BackHandler(drawerState.isVisible) {
        scope.launch {
            drawerState.hide()
        }
    }

    BottomDrawer(
        drawerState = drawerState,
        sheetContent = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeedIcon(
                            modifier = Modifier.clickable {
                                if (feedOptionViewModel.rssService.get().updateSubscription) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    feedOptionViewModel.showChangeIconDialog()
                                }
                            },
                            feedName = feed?.name,
                            iconUrl = feed?.icon,
                            size = 24.dp
                        )
                        IconButton(
                            enabled = feedOptionViewModel.rssService.get().updateSubscription && !feedOptionUiState.isIconSearching,
                            onClick = { feedOptionViewModel.reloadIcon() }
                        ) {
                            if (feedOptionUiState.isIconSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.search_icon),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        modifier = Modifier.alphaIndicationClickable {
                            if (feedOptionViewModel.rssService.get().updateSubscription) {
                                feedOptionViewModel.showRenameDialog()
                            }
                        },
                        text = feed?.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                FeedOptionView(
                    link = feed?.url ?: stringResource(R.string.unknown),
                    groups = feedOptionUiState.groups,
                    selectedAllowNotificationPreset = feedOptionUiState.feed?.isNotification
                        ?: false,
                    selectedParseFullContentPreset = feedOptionUiState.feed?.isFullContent ?: false,
                    selectedOpenInBrowserPreset = feedOptionUiState.feed?.isBrowser ?: false,
                    selectedAutoTranslatePreset = feedOptionUiState.feed?.isAutoTranslate ?: false,
                    selectedAutoTranslateTitlePreset = feedOptionUiState.feed?.isAutoTranslateTitle ?: false,
                    disableRefererEnabled = feedOptionUiState.feed?.isDisableReferer ?: false,
                    disableJavaScriptEnabled = feedOptionUiState.feed?.isDisableJavaScript ?: false,
                    showImageFilterOption = true,
                    imageFilterEnabled = feedOptionUiState.feed?.isImageFilterEnabled ?: false,
                    isMoveToGroup = true,
                    showGroup = feedOptionViewModel.rssService.get().moveSubscription,
                    showUnsubscribe = feedOptionViewModel.rssService.get().deleteSubscription,
                    notSubscribeMode = true,
                    selectedGroupId = feedOptionUiState.feed?.groupId ?: "",
                    allowNotificationPresetOnClick = {
                        Timber.tag("AutoTranslate").d("onClick: Allow notification button clicked")
                        feedOptionViewModel.changeAllowNotificationPreset()
                    },
                    parseFullContentPresetOnClick = {
                        Timber.tag("AutoTranslate").d("onClick: Parse full content button clicked")
                        feedOptionViewModel.changeParseFullContentPreset()
                    },
                    openInBrowserPresetOnClick = {
                        Timber.tag("AutoTranslate").d("onClick: Open in browser button clicked")
                        feedOptionViewModel.changeOpenInBrowserPreset()
                    },
                    autoTranslatePresetOnClick = {
                        Timber.tag("AutoTranslate").d("onClick: Auto translate button clicked")
                        feedOptionViewModel.changeAutoTranslatePreset()
                    },
                    autoTranslateTitlePresetOnClick = {
                        Timber.tag("AutoTranslateTitle").d("onClick: Auto translate title button clicked")
                        feedOptionViewModel.changeAutoTranslateTitlePreset()
                    },
                    disableRefererOnCheckedChange = {
                        feedOptionViewModel.setDisableReferer(it)
                    },
                    disableJavaScriptOnCheckedChange = {
                        feedOptionViewModel.setDisableJavaScript(it)
                    },
                    onImageFilterClick = {
                        feedOptionViewModel.showImageFilterDialog()
                    },
                    clearArticlesOnClick = {
                        feedOptionViewModel.showClearDialog()
                    },
                    unsubscribeOnClick = {
                        feedOptionViewModel.showDeleteDialog()
                    },
                    exportFeedAsOpmlOnClick = {
                        if (feed == null) return@FeedOptionView
                        scope.launch {
                            val export = feedOptionViewModel.buildExportPayload(feed.id)
                            pendingExportContent = export.content
                            if (export.mime == "application/json") {
                                exportFeedJsonLauncher.launch(export.fileName)
                            } else {
                                exportFeedOpmlLauncher.launch(export.fileName)
                            }
                        }
                    },
                    exportFeedLabel = exportLabel,
                    onGroupClick = {
                        feedOptionViewModel.selectedGroup(it)
                    },
                    onAddNewGroup = {
                        feedOptionViewModel.showNewGroupDialog()
                    },
                    onFeedUrlClick = {
                        context.openURL(feed?.url, openLink, openLinkSpecificBrowser)
                    },
                    onFeedUrlLongClick = {
                        if (feedOptionViewModel.rssService.get().updateSubscription) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            feedOptionViewModel.showFeedUrlDialog()
                        }
                    }
                )
            }
        },
        content = {
            content()
        }
    )

    DeleteFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })

    ClearFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })

    if (feedOptionUiState.imageFilterDialogVisible) {
        AlertDialog(
            onDismissRequest = { feedOptionViewModel.hideImageFilterDialog() },
            title = { Text(text = stringResource(R.string.image_filter)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(R.string.image_filter_enable))
                        Switch(
                            checked = feedOptionUiState.imageFilterEnabled,
                            onCheckedChange = { feedOptionViewModel.inputImageFilterEnabled(it) },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = feedOptionUiState.imageFilterResolution,
                        onValueChange = { feedOptionViewModel.inputImageFilterResolution(it) },
                        label = { Text(text = stringResource(R.string.image_filter_resolution)) },
                        placeholder = { Text(text = stringResource(R.string.image_filter_resolution_hint)) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = feedOptionUiState.imageFilterFileName,
                        onValueChange = { feedOptionViewModel.inputImageFilterFileName(it) },
                        label = { Text(text = stringResource(R.string.image_filter_filename)) },
                        placeholder = { Text(text = stringResource(R.string.image_filter_filename_hint)) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = feedOptionUiState.imageFilterDomain,
                        onValueChange = { feedOptionViewModel.inputImageFilterDomain(it) },
                        label = { Text(text = stringResource(R.string.image_filter_domain)) },
                        placeholder = { Text(text = stringResource(R.string.image_filter_domain_hint)) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.image_filter_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { feedOptionViewModel.saveImageFilterSettings() }) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { feedOptionViewModel.hideImageFilterDialog() }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    TextFieldDialog(
        visible = feedOptionUiState.newGroupDialogVisible,
        title = stringResource(R.string.create_new_group),
        icon = Icons.Outlined.CreateNewFolder,
        value = feedOptionUiState.newGroupContent,
        placeholder = stringResource(R.string.name),
        onValueChange = {
            feedOptionViewModel.inputNewGroup(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideNewGroupDialog()
        },
        onConfirm = {
            feedOptionViewModel.addNewGroup()
        }
    )

    RenameDialog(
        visible = feedOptionUiState.renameDialogVisible,
        value = feedOptionUiState.newName,
        onValueChange = {
            feedOptionViewModel.inputNewName(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideRenameDialog()
        },
        onConfirm = {
            feedOptionViewModel.renameFeed()
            scope.launch { drawerState.hide() }
            context.showToast(toastString)
        }
    )

    ChangeUrlDialog(
        visible = feedOptionUiState.changeUrlDialogVisible,
        value = feedOptionUiState.newUrl,
        onValueChange = {
            feedOptionViewModel.inputNewUrl(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideFeedUrlDialog()
        },
        onConfirm = {
            feedOptionViewModel.changeFeedUrl()
            scope.launch { drawerState.hide() }
        }
    )

    ChangeIconDialog(
        visible = feedOptionUiState.changeIconDialogVisible,
        value = feedOptionUiState.newIcon,
        onValueChange = {
            feedOptionViewModel.inputNewIcon(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideChangeIconDialog()
        },
        onConfirm = {
            feedOptionViewModel.changeIconUrl()
            scope.launch { drawerState.hide() }
        },
        onImportClick = {
            importIconLauncher.launch(arrayOf("image/*"))
        },
        onExportClick = {
            exportIconLauncher.launch(buildIconFileName(feed))
        },
        exportEnabled = !feed?.icon.isNullOrBlank(),
    )
}

private fun buildIconFileName(feed: me.ash.reader.domain.model.feed.Feed?): String {
    val base = feed?.name?.replace("/", "_")?.ifBlank { "feed" } ?: "feed"
    return "${base}_icon.png"
}
