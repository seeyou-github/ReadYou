package me.ash.reader.ui.page.home.feeds.subscribe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.ClipboardTextField
import me.ash.reader.ui.component.base.RYTextField2
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.home.feeds.FeedOptionView

@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun SubscribeDialog(
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val subscribeUiState = subscribeViewModel.subscribeUiState.collectAsStateValue()
    val subscribeState = subscribeViewModel.subscribeState.collectAsStateValue()

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
            uris.forEach { uri ->
                context.contentResolver.openInputStream(uri)?.let { inputStream ->
                    subscribeViewModel.importLocalRule(inputStream) { message ->
                        context.showToast(message)
                    }
                }
            }
        }
    val imageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            subscribeViewModel.importIconFromUri(context, uri)
        }

    if (subscribeState is SubscribeState.Visible) {

        DisposableEffect(Unit) {
            onDispose {
                subscribeViewModel.cancelSearch()
            }
        }

        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.92f).padding(horizontal = 16.dp, vertical = 12.dp),
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false
            ),
            onDismissRequest = {
                focusManager.clearFocus()
                subscribeViewModel.hideDrawer()
            },
            icon = null,
            title = null,
            text = {
                AnimatedContent(
                    targetState = subscribeState,
                    transitionSpec = {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()) using null
                    },
                    contentKey = { it is SubscribeState.Configure }
                ) { state ->
                    when (state) {
                        is SubscribeState.Input -> {
                            val errorText = when (state) {
                                is SubscribeState.Fetching -> ""
                                is SubscribeState.Idle -> state.errorMessage ?: ""
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        FeedIcon(
                                            feedName = null,
                                            iconUrl = state.iconUrlState.text.toString().ifBlank { null },
                                            placeholderIcon = Icons.Rounded.RssFeed,
                                            size = 56.dp,
                                        )
                                        TextButton(
                                            enabled = state.linkState.text.isNotBlank(),
                                            onClick = {
                                                focusManager.clearFocus()
                                                subscribeViewModel.parseIconFromFeedUrl()
                                            },
                                        ) {
                                            Text(text = stringResource(R.string.parse_icon))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    RYTextField2(
                                        state = state.iconUrlState,
                                        modifier = Modifier.weight(1f).onFocusChanged {
                                            if (!it.isFocused) {
                                                subscribeViewModel.resolveIconFromInputUrl()
                                            }
                                        },
                                        placeholder = stringResource(R.string.icon_url_optional),
                                        autoFocus = false,
                                    )
                                    TextButton(
                                        onClick = { imageLauncher.launch(arrayOf("image/*")) },
                                    ) {
                                        Text(text = stringResource(R.string.import_image))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    RYTextField2(
                                        state = state.titleState,
                                        modifier = Modifier.weight(1f),
                                        placeholder = stringResource(R.string.feed_title_optional),
                                        autoFocus = false,
                                    )
                                    TextButton(
                                        enabled = state.linkState.text.isNotBlank(),
                                        onClick = {
                                            focusManager.clearFocus()
                                            subscribeViewModel.parseTitleFromFeedUrl()
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.parse_title))
                                    }
                                }

                                ClipboardTextField(
                                    state = state.linkState,
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = state is SubscribeState.Fetching,
                                    placeholder = stringResource(R.string.feed_url_required),
                                    errorText = errorText,
                                    imeAction = ImeAction.Done,
                                    onConfirm = {
                                        subscribeViewModel.addFeed()
                                    },
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    if (state is SubscribeState.Idle && state.importFromOpmlEnabled) {
                                        TextButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                launcher.launch(arrayOf(MimeType.ANY))
                                                subscribeViewModel.hideDrawer()
                                            }
                                        ) {
                                            Text(text = stringResource(R.string.import_opml_file))
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                    Button(
                                        enabled = state.linkState.text.isNotBlank(),
                                        onClick = {
                                            focusManager.clearFocus()
                                            subscribeViewModel.addFeed()
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.add))
                                    }
                                }
                            }
                        }

                        is SubscribeState.Configure -> {
                            FeedOptionView(
                                link = state.feedLink,
                                groups = state.groups,
                                selectedAllowNotificationPreset = state.notification,
                                selectedParseFullContentPreset = state.fullContent,
                                selectedOpenInBrowserPreset = state.browser,
                                selectedAutoTranslatePreset = state.autoTranslate,
                                selectedGroupId = state.selectedGroupId,
                                allowNotificationPresetOnClick = {
                                    subscribeViewModel.toggleAllowNotificationPreset()
                                },
                                parseFullContentPresetOnClick = {
                                    subscribeViewModel.toggleParseFullContentPreset()
                                },
                                openInBrowserPresetOnClick = {
                                    subscribeViewModel.toggleOpenInBrowserPreset()
                                },
                                autoTranslatePresetOnClick = {
                                    subscribeViewModel.toggleAutoTranslatePreset()
                                },
                                onGroupClick = {
                                    subscribeViewModel.selectedGroup(it)
                                },
                                onAddNewGroup = {
                                    subscribeViewModel.showNewGroupDialog()
                                },
                            )
                        }

                        SubscribeState.Hidden -> {}
                    }
                }
            },
            confirmButton = {
                if (subscribeState is SubscribeState.Configure) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            subscribeViewModel.subscribe()
                        }
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }
            },
            dismissButton = {
                if (subscribeState is SubscribeState.Configure) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            subscribeViewModel.hideDrawer()
                        }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            },
        )

        RenameDialog(
            visible = subscribeUiState.renameDialogVisible,
            value = subscribeUiState.newName,
            onValueChange = {
                subscribeViewModel.inputNewName(it)
            },
            onDismissRequest = {
                subscribeViewModel.hideRenameDialog()
            },
            onConfirm = {
                subscribeViewModel.renameFeed()
                subscribeViewModel.hideRenameDialog()
            }
        )

        TextFieldDialog(
            visible = subscribeUiState.newGroupDialogVisible,
            title = stringResource(R.string.create_new_group),
            icon = Icons.Outlined.CreateNewFolder,
            value = subscribeUiState.newGroupContent,
            placeholder = stringResource(R.string.name),
            onValueChange = {
                subscribeViewModel.inputNewGroup(it)
            },
            onDismissRequest = {
                subscribeViewModel.hideNewGroupDialog()
            },
            onConfirm = {
                subscribeViewModel.addNewGroup()
            }
        )
    }
}
