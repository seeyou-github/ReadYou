package me.ash.reader.ui.page.home.flow

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Precision
import coil.size.Scale
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.theme.ColorTheme
import me.ash.reader.infrastructure.preference.LocalFlowArticleListColorThemes
import me.ash.reader.infrastructure.preference.LocalFlowArticleListHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalFlowArticleListImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalFlowArticleListRoundedCorners
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTitleFontSize
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTitleLineHeight
import me.ash.reader.infrastructure.preference.LocalReadingImageBrightness
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.ext.requiresBidi
import me.ash.reader.ui.theme.applyTextDirection

@Composable
fun LargeImageArticleItem(
    modifier: Modifier = Modifier,
    articleWithFeed: ArticleWithFeed,
    translatedTitle: String? = articleWithFeed.article.translatedTitle,  // 2026-02-03: ????????
    onClick: (ArticleWithFeed) -> Unit = {},
) {
    val article = articleWithFeed.article
    val imageUrl = article.img

    // 2026-01-28: 获取尺寸设置
    val horizontalPadding = LocalFlowArticleListHorizontalPadding.current
    val imageRoundedCorners = LocalFlowArticleListRoundedCorners.current  //圆角使用：列表背景圆�?
    val imageBrightness = LocalReadingImageBrightness.current
      val titleImageUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    // 2026-01-28: 标题大小和行�?
    val titleFontSize = LocalFlowArticleListTitleFontSize.current
    val titleLineHeight = LocalFlowArticleListTitleLineHeight.current

    // 2026-01-28: 获取颜色主题
    val colorThemes = LocalFlowArticleListColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()

    // 计算亮度滤镜
    val brightnessFilter = if (imageBrightness < 100) {
        val brightnessValue = imageBrightness / 100f
        ColorFilter.lighting(
            multiply = Color(brightnessValue, brightnessValue, brightnessValue),
            add = Color.Transparent
        )
    } else {
        null
    }

    // 2026-01-28: 使用颜色主题的文字颜色和背景�?50%透明�?
    val themeTextColor = selectedColorTheme?.textColor ?: Color.Black
    val themeBackgroundColor = selectedColorTheme?.backgroundColor?.let {
        Color(it.red, it.green, it.blue, 0.6f) // 50%透明�?
    } ?: Color.Black.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .fillMaxWidth()                       // 宽度撑满父布局
            .aspectRatio(16f / 9f)                // 固定�?16:9 比例（常见封面图�?
            .padding(horizontal = horizontalPadding.dp)       // 左右内边�?
            .clip(RoundedCornerShape(imageRoundedCorners.dp)) // 裁剪成圆�?
            .clickable {                           // 整个区域可点�?
                onClick(articleWithFeed)           // 点击回调，传入文章数�?
            }
    ) {
        // 封面背景大图（有图才显示�?
        if (imageUrl != null) {
            Log.d("RLog", "list large image: ${imageUrl}")
            RYAsyncImage(
                modifier = Modifier.fillMaxSize(), // 图片铺满整个 Box
                data = imageUrl,
                disableReferer = articleWithFeed.feed.isDisableReferer,
                  refererUrl = articleWithFeed.article.link,
                  userAgent = titleImageUserAgent,
                                  // 图片地址（URL�?
                scale = Scale.FIT,                 // 按目标尺寸加载，省内�?
                precision = Precision.INEXACT,     // 不要求精确尺寸，加载更快
                size = SIZE_1000,                  // 限制最大加载尺寸，防止原图过大
                contentScale = ContentScale.Crop,  // 等比裁剪填满（封面图常用�?
                colorFilter = brightnessFilter,    // 亮度/暗色滤镜，增强可读�?
            )
        }



    // 底部半透明背景容器
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    color = themeBackgroundColor, // 2026-01-28: 使用颜色主题的背景色(50%透明�?
//                    shape = RoundedCornerShape(bottomStart = imageRoundedCorners.dp, bottomEnd = imageRoundedCorners.dp)
                )
                .padding(0.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // 文章标题（最�?行）- 2026-01-28: 使用标题大小和行距设�?
            Text(
                text = translatedTitle ?: article.title,
                style = MaterialTheme.typography.titleMedium.applyTextDirection((translatedTitle ?: article.title).requiresBidi()).copy(
                    fontSize = titleFontSize.sp,
                    lineHeight = (titleFontSize * titleLineHeight).sp
                ),
                color = themeTextColor, // 2026-01-28: 使用颜色主题的文字颜�?
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp, 0.dp)
            )
        }
    }
}






