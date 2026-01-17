package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingTitleAlign
import me.ash.reader.infrastructure.preference.LocalReadingTitleBold
import me.ash.reader.infrastructure.preference.LocalReadingTitleColor
import me.ash.reader.infrastructure.preference.LocalReadingTitleFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTitleHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingTitleUpperCase
import me.ash.reader.ui.ext.formatAsString
import me.ash.reader.ui.ext.requiresBidi
import me.ash.reader.ui.theme.applyTextDirection
import java.util.Date

/**
 * 文章元数据显示组件（标题、作者、发布时间、订阅源名称）
 *
 * 修改记录：
 * 2026-01-24：集成 Preference 系统实现可配置标题样式
 *             使用 LocalReadingTitleFontSize、LocalReadingTitleColor、LocalReadingTitleHorizontalPadding
 *
 * @param feedName 订阅源名称
 * @param title 文章标题
 * @param publishedDate 发布时间
 * @param modifier 修饰符
 * @param author 作者（可选）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Metadata(
    feedName: String,
    title: String,
    publishedDate: Date,
    modifier: Modifier = Modifier,
    author: String? = null,
) {
    val context = LocalContext.current
    val titleBold = LocalReadingTitleBold.current
    val titleUpperCase = LocalReadingTitleUpperCase.current
    val titleAlign = LocalReadingTitleAlign.current.toTextAlign()
    val dateString =
        remember(publishedDate) { publishedDate.formatAsString(context, atHourMinute = true) }
    val fontFamily = LocalReadingFonts.current.asFontFamily(context)

    val titleUpperCaseString by remember { derivedStateOf { title.uppercase() } }

    val labelColor = MaterialTheme.colorScheme.outline.copy(alpha = .7f)

    // 2026-01-24: 从 Preference 获取标题样式设置
    val titleFontSize = LocalReadingTitleFontSize.current  // 字体大小（sp）
    val titleColorHex = LocalReadingTitleColor.current  // 颜色（十六进制字符串）
    val titleHorizontalPadding = LocalReadingTitleHorizontalPadding.current  // 左右边距（dp）

    // 2026-01-24: 将十六进制颜色字符串转换为 Compose Color
    // 解析失败时使用默认颜色 MaterialTheme.colorScheme.onSurface
    val titleColor = try {
        // 将 #RRGGBB 格式的字符串转换为 Long，然后创建 Compose Color
        // Color 构造函数需要 Long 类型的颜色值
        val colorLong = java.lang.Long.parseUnsignedLong(titleColorHex.replace("#", ""), 16)
        Color(colorLong or 0xFF000000)  // 确保不透明
    } catch (e: Exception) {
        MaterialTheme.colorScheme.onSurface  // 解析失败时使用默认颜色
    }

    // 2026-01-24 修改：移除 Column 的整体 padding，改为给每个子元素单独设置
    // 这样标题可以获得独立的左右边距设置，而不受外层 padding 影响
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 发布时间 - 保持 12dp 左右边距
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            text = dateString,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium.merge(fontFamily = fontFamily),
            textAlign = titleAlign,
        )
        Spacer(modifier = Modifier.height(4.dp))

        /**
         * 文章标题
         *
         * 修改原因：2026-01-24 集成 Preference 系统实现可配置样式
         * 修改内容：
         * - color: 使用 LocalReadingTitleColor Preference（默认 #dddddd）
         * - style: 使用 LocalReadingTitleFontSize Preference（默认 20sp）
         * - modifier: 使用 LocalReadingTitleHorizontalPadding Preference（默认 15dp）
         */
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = titleHorizontalPadding.dp),  // 从 Preference 读取左右边距
            text = if (titleUpperCase.value) titleUpperCaseString else title,
            color = titleColor,  // 从 Preference 读取颜色
            style =
                MaterialTheme.typography.headlineLarge
                    .copy(fontSize = titleFontSize.sp)  // 从 Preference 读取字体大小
                    .merge(
                        fontFamily = fontFamily,
                        fontWeight = if (titleBold.value) FontWeight.Bold else FontWeight.Medium,
                    )
                    .applyTextDirection(requiresBidi = title.requiresBidi()),
            textAlign = titleAlign,
        )

        // 以下为临时测试代码（已保留，可用于调试）
        /*
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
            text = if (titleUpperCase.value) titleUpperCaseString else title,
            color = Color.Red,  // 测试：标题颜色为红色
            style =
                MaterialTheme.typography.headlineLarge
                    .copy(fontSize = 10.sp)  // 测试：标题字体大小 10sp
                    .merge(
                        fontFamily = fontFamily,
                        fontWeight = if (titleBold.value) FontWeight.Bold else FontWeight.Medium,
                    )
                    .applyTextDirection(requiresBidi = title.requiresBidi()),
            textAlign = titleAlign,
        )
        */

        Spacer(modifier = Modifier.height(4.dp))

        // 作者（可选显示）- 保持 12dp 左右边距
        author?.let {
            if (it.isNotEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    text = it,
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium.merge(fontFamily = fontFamily),
                    textAlign = titleAlign,
                )
            }
        }

        // 订阅源名称 - 保持 12dp 左右边距
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            text = feedName,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium.merge(fontFamily = fontFamily),
            textAlign = titleAlign,
        )
    }
}