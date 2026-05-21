package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.preference.DynamicProvidersPreference

/**
 * 添加供应商对话框（对应图 1）。
 *
 * 三 Tab：OpenAI / Google / Claude；提交后写入动态供应商存储并将新 id 置顶。
 */
@Composable
fun AddProviderDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(ProviderKind.OPENAI) }

    // OpenAI
    var oaEnabled by remember { mutableStateOf(true) }
    var oaUseResponses by remember { mutableStateOf(false) }
    var oaName by remember { mutableStateOf("OpenAI") }
    var oaKey by remember { mutableStateOf("") }
    var oaBase by remember { mutableStateOf("https://api.openai.com/v1") }
    var oaPath by remember { mutableStateOf("/chat/completions") }

    // Google
    var ggEnabled by remember { mutableStateOf(true) }
    var ggName by remember { mutableStateOf("Google") }
    var ggKey by remember { mutableStateOf("") }
    var ggBase by remember { mutableStateOf("https://generativelanguage.googleapis.com/v1beta") }

    // Claude
    var clEnabled by remember { mutableStateOf(true) }
    var clName by remember { mutableStateOf("Claude") }
    var clKey by remember { mutableStateOf("") }
    var clBase by remember { mutableStateOf("https://api.anthropic.com/v1") }

    fun submit() {
        val cfg: TranslateProviderConfig = when (tab) {
            ProviderKind.OPENAI -> {
                val display = oaName.trim().ifBlank { "OpenAI" }
                val id = DynamicProvidersPreference.uniqueKey(existingIds, "OpenAI", display)
                TranslateProviderConfig(
                    id = id,
                    kind = ProviderKind.OPENAI,
                    name = display,
                    enabled = oaEnabled,
                    apiKey = oaKey.trim(),
                    baseUrl = oaBase.trim().ifBlank { "https://api.openai.com/v1" },
                    chatPath = if (oaUseResponses) "" else oaPath.trim().ifBlank { "/chat/completions" },
                    useResponsesApi = oaUseResponses,
                )
            }
            ProviderKind.GOOGLE -> {
                val display = ggName.trim().ifBlank { "Google" }
                val id = DynamicProvidersPreference.uniqueKey(existingIds, "Google", display)
                TranslateProviderConfig(
                    id = id,
                    kind = ProviderKind.GOOGLE,
                    name = display,
                    enabled = ggEnabled,
                    apiKey = ggKey.trim(),
                    baseUrl = ggBase.trim().ifBlank { "https://generativelanguage.googleapis.com/v1beta" },
                )
            }
            ProviderKind.CLAUDE -> {
                val display = clName.trim().ifBlank { "Claude" }
                val id = DynamicProvidersPreference.uniqueKey(existingIds, "Claude", display)
                TranslateProviderConfig(
                    id = id,
                    kind = ProviderKind.CLAUDE,
                    name = display,
                    enabled = clEnabled,
                    apiKey = clKey.trim(),
                    baseUrl = clBase.trim().ifBlank { "https://api.anthropic.com/v1" },
                )
            }
        }
        DynamicProvidersPreference.put(context, scope, cfg)
        onCreated(cfg.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "添加供应商",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Segmented tabs
                val kinds = listOf(ProviderKind.OPENAI, ProviderKind.GOOGLE, ProviderKind.CLAUDE)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = tab == k,
                            onClick = { tab = k },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = kinds.size),
                        ) {
                            Text(when (k) {
                                ProviderKind.OPENAI -> "OpenAI"
                                ProviderKind.GOOGLE -> "Google"
                                ProviderKind.CLAUDE -> "Claude"
                            })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    when (tab) {
                        ProviderKind.OPENAI -> {
                            SwitchTile("是否启用", oaEnabled) { oaEnabled = it }
                            Spacer(modifier = Modifier.height(6.dp))
                            SwitchTile("Use Responses API", oaUseResponses) { oaUseResponses = it }
                            Spacer(modifier = Modifier.height(12.dp))
                            Field("名称", oaName) { oaName = it }
                            Field("API Key", oaKey) { oaKey = it }
                            Field("Base URL", oaBase) { oaBase = it }
                            Field("API 路径", oaPath, enabled = !oaUseResponses) { oaPath = it }
                        }
                        ProviderKind.GOOGLE -> {
                            SwitchTile("是否启用", ggEnabled) { ggEnabled = it }
                            Spacer(modifier = Modifier.height(12.dp))
                            Field("名称", ggName) { ggName = it }
                            Field("API Key", ggKey) { ggKey = it }
                            Field("Base URL", ggBase) { ggBase = it }
                        }
                        ProviderKind.CLAUDE -> {
                            SwitchTile("是否启用", clEnabled) { clEnabled = it }
                            Spacer(modifier = Modifier.height(12.dp))
                            Field("名称", clName) { clName = it }
                            Field("API Key", clKey) { clKey = it }
                            Field("Base URL", clBase) { clBase = it }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { submit() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("添加")
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchTile(label: String, value: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChanged)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    enabled: Boolean = true,
    onChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChanged,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        enabled = enabled,
    )
}
