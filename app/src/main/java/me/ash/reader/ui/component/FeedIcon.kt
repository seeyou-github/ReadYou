package me.ash.reader.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.ui.component.base.Base64Image
import me.ash.reader.ui.component.base.RYAsyncImage

// 2026-01-21: 图标圆角半径（iPhone 风格，约为尺寸的 22%）
private val iconCornerRadius = 12.dp

@Composable
fun FeedIcon(
    modifier: Modifier = Modifier,
    feedName: String? = "",
    iconUrl: String?,
    size: Dp = 20.dp,
    placeholderIcon: ImageVector? = null,
    brightness: Int = 100,
    backgroundColor: Color? = null,
) {
    // 2026-01-21: 使用圆角方形代替圆形（iPhone 风格）
    // 修改原因：用户希望图标显示为方形，类似 iPhone 应用图标
    val iconShape = RoundedCornerShape(iconCornerRadius)

    // 2026-01-21: 添加亮度调节
    // 使用 lighting 滤镜调整亮度：multiply 值越高越亮
    val brightnessFilter = if (brightness < 100) {
        val brightnessValue = brightness / 100f
        ColorFilter.lighting(
            multiply = Color(brightnessValue, brightnessValue, brightnessValue),
            add = Color.Transparent
        )
    } else {
        null
    }

    if (iconUrl.isNullOrEmpty()) {
        if (placeholderIcon == null) {
            FontIcon(modifier, size, feedName ?: "", iconShape, brightnessFilter)
        } else {
            ImageIcon(modifier, placeholderIcon, feedName ?: "", brightnessFilter)
        }
    }
    // e.g. data:image/gif;base64,R0lGODlh... or image/gif;base64,R0lGODlh...
    else if ("^(data:)?image/.*;base64,.*".toRegex().matches(iconUrl)) {
        Base64Image(
            modifier = modifier
                .size(size)
                .clip(iconShape),
            base64Uri = iconUrl,
            onEmpty = { FontIcon(modifier, size, feedName ?: "", iconShape, brightnessFilter) },
            colorFilter = brightnessFilter,
        )
    } else {
        // 2026-01-23: 使用 iconUrl 作为缓存 key，当 URL 变化时强制 Coil 重新加载
        val imageKey = remember(iconUrl) { iconUrl }
        RYAsyncImage(
            modifier = modifier
                .size(size)
                .clip(iconShape),
            contentDescription = feedName ?: "",
            data = iconUrl,
            key = imageKey,  // 强制 Coil 在 URL 变化时重新加载
            placeholder = null,
            colorFilter = brightnessFilter,
            backgroundColor = backgroundColor,
        )
    }
}

@Composable
private fun ImageIcon(
    modifier: Modifier,
    placeholderIcon: ImageVector,
    feedName: String,
    colorFilter: ColorFilter?
) {
    Icon(
        modifier = modifier,
        imageVector = placeholderIcon,
        contentDescription = feedName,
        tint = colorFilter?.let { Color.White } ?: MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FontIcon(
    modifier: Modifier,
    size: Dp,
    feedName: String,
    shape: androidx.compose.ui.graphics.Shape,
    colorFilter: ColorFilter?
) {
    // 获取页面主题颜色
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    Box(
        modifier = modifier
            .size(size)
//            .clip(shape)
            .background(
                selectedColorTheme?.backgroundColor ?: MaterialTheme.colorScheme.primary
            ),
//            .background(MaterialTheme.colorScheme.primary),
//            .background(Color.Transparent),//FeedIcon 自身不再覆盖背景
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = feedName.ifEmpty { " " }.first().toString(),
            style = MaterialTheme.typography.bodyMedium.merge(
                color = colorFilter?.let { Color.White } ?: MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ))
    }
}

@Preview
@Composable
fun FeedIconPrev() {
    FeedIcon(feedName = stringResource(R.string.preview_feed_name), iconUrl = null)
}
