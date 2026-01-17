package me.ash.reader.ui.ext

import androidx.compose.ui.graphics.Color
import java.util.Locale

// 2026-01-21: 颜色转十六进制字符串辅助函数
// 修改原因：将 Color.toHexCode() 函数移到公共扩展文件中，避免在多个文件中重复定义
fun Color.toHexCode(): String {
    val r = (this.red * 255).toInt().coerceIn(0, 255)
    val g = (this.green * 255).toInt().coerceIn(0, 255)
    val b = (this.blue * 255).toInt().coerceIn(0, 255)
    val a = (this.alpha * 255).toInt().coerceIn(0, 255)
    return if (a == 255) {
        String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    } else {
        String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
    }
}