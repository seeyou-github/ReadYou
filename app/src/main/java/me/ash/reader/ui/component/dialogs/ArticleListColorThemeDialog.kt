package me.ash.reader.ui.component.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.theme.ColorTheme
import me.ash.reader.infrastructure.preference.FlowArticleListColorThemesPreference
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.toHexCode

// 2026-01-18: 新增文章列表颜色主题设置页面
@Composable
fun ArticleListColorThemeDialog(
    onDismiss: () -> Unit,
    onSave: (List<ColorTheme>) -> Unit,
    onDelete: (String) -> Unit,
    context: android.content.Context,
    scope: CoroutineScope,
    currentThemes: List<ColorTheme>,
    editingTheme: ColorTheme? = null,
    isCreatingNew: Boolean = false,
    isDarkTheme: Boolean = false
) {
    // 如果是编辑模式，初始化值
    var themeName by remember { mutableStateOf(editingTheme?.name ?: "") }
    var textColor by remember { mutableStateOf(editingTheme?.textColor ?: Color.Black) }
    var backgroundColor by remember { mutableStateOf(editingTheme?.backgroundColor ?: Color.White) }
    var primaryColor by remember { mutableStateOf(editingTheme?.primaryColor ?: Color(0xFF2196F3)) }
    var selectedTab by remember { mutableStateOf(0) }  // 0: 文字颜色, 1: 背景颜色, 2: 主题色

    var red by remember { mutableStateOf(0f) }
    var green by remember { mutableStateOf(0f) }
    var blue by remember { mutableStateOf(0f) }

    // 当前选择的颜色
    val currentColor = when (selectedTab) {
        0 -> textColor
        1 -> backgroundColor
        2 -> primaryColor
        else -> textColor
    }

    // 当选择Tab改变时，更新RGB滑块
    LaunchedEffect(selectedTab) {
        red = currentColor.red
        green = currentColor.green
        blue = currentColor.blue
    }

    // 当RGB滑块改变时，更新当前颜色
    LaunchedEffect(red, green, blue) {
        val newColor = Color(red, green, blue)
        when (selectedTab) {
            0 -> textColor = newColor
            1 -> backgroundColor = newColor
            2 -> primaryColor = newColor
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp) // 全屏高度
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 第一排：预设名称
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = { Text("预设名称（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 第二排：颜色按钮（这里使用Tab切换）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val textColorButtonColor = if (selectedTab == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                    val backgroundColorButtonColor = if (selectedTab == 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }

                    Button(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("文字颜色", color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("背景颜色", color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 第三排：预览区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "这是第一行预览文字\n这是第二行预览文字",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 第四排：RGB滑动条
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Red: ${(red * 255).toInt()}", modifier = Modifier.width(60.dp))
                        Slider(
                            value = red,
                            onValueChange = { red = it },
                            valueRange = 0f..1f,
                            steps = 0, // 取消竖指针
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Green: ${(green * 255).toInt()}", modifier = Modifier.width(60.dp))
                        Slider(
                            value = green,
                            onValueChange = { green = it },
                            valueRange = 0f..1f,
                            steps = 0, // 取消竖指针
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Blue: ${(blue * 255).toInt()}", modifier = Modifier.width(60.dp))
                        Slider(
                            value = blue,
                            onValueChange = { blue = it },
                            valueRange = 0f..1f,
                            steps = 0, // 取消竖指针
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 第四排：颜色代码
                OutlinedTextField(
                    value = currentColor.toHexCode(),
                    onValueChange = {
                        try {
                            val color = Color(android.graphics.Color.parseColor(it))
                            if (selectedTab == 0) {
                                textColor = color
                            } else {
                                backgroundColor = color
                            }
                        } catch (e: Exception) {
                            // 忽略无效的十六进制代码
                        }
                    },
                    label = { Text("#ffffff") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 第五排：保存和删除按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 删除按钮（仅编辑模式）
                    if (!isCreatingNew && editingTheme != null) {
                        Button(
                            onClick = {
                                onDelete(editingTheme!!.id)
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // 取消按钮
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // 保存按钮
                    Button(
                        onClick = {
                            if (themeName.isBlank()) {
                                // 如果没有输入名称，使用默认名称
                                themeName = "主题${currentThemes.size + 1}"
                            }
                            val newTheme = ColorTheme(
                                name = themeName,
                                textColor = textColor,
                                backgroundColor = backgroundColor,
                                primaryColor = primaryColor,
                                isDefault = isCreatingNew, // 新增主题时设为默认
                                isDarkTheme = isDarkTheme
                            )
                            scope.launch {
                                val updatedThemes = if (!isCreatingNew && editingTheme != null) {
                                    // 编辑模式：更新现有主题
                                    currentThemes.map {
                                        if (it.id == editingTheme!!.id) {
                                            newTheme.copy(id = it.id, isDefault = it.isDefault)
                                        } else {
                                            it
                                        }
                                    }
                                } else {
                                    // 新增模式：添加新主题并设为默认
                                    val themesWithNew = currentThemes + newTheme
                                    themesWithNew.map {
                                        it.copy(isDefault = it.id == newTheme.id)
                                    }
                                }
                                FlowArticleListColorThemesPreference.put(
                                    context,
                                    scope,
                                    updatedThemes
                                )
                                onSave(updatedThemes)
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

// Preview annotations
@androidx.compose.ui.tooling.preview.Preview(
    name = "ArticleListColorThemeDialog Preview",
    showBackground = true
)
@Composable
fun ArticleListColorThemeDialogPreview() {
    // Preview implementation
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        androidx.compose.material3.Text(
            "ArticleListColorThemeDialog Preview",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}
