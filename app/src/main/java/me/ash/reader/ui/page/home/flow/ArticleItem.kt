package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.size.Precision
import coil.size.Scale
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.theme.ColorTheme
import me.ash.reader.infrastructure.preference.FlowArticleListDescPreference
import me.ash.reader.infrastructure.preference.FlowArticleReadIndicatorPreference
import me.ash.reader.infrastructure.preference.LocalArticleListSwipeEndAction
import me.ash.reader.infrastructure.preference.LocalArticleListSwipeStartAction
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDesc
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedName
import me.ash.reader.infrastructure.preference.LocalFlowArticleListImage
import me.ash.reader.infrastructure.preference.LocalFlowArticleListReadIndicator
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTime
// 2026-01-18: 新增文章列表样式相关的Preference导入
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTitleFontSize
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTitleLineHeight
import me.ash.reader.infrastructure.preference.LocalFlowArticleListHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalFlowArticleListVerticalPadding
import me.ash.reader.infrastructure.preference.LocalFlowArticleListImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalFlowArticleListImageSize
import me.ash.reader.infrastructure.preference.LocalFlowArticleListRoundedCorners
import me.ash.reader.infrastructure.preference.LocalFlowArticleListColorThemes
import me.ash.reader.infrastructure.preference.LocalReadingImageBrightness
import me.ash.reader.infrastructure.preference.SwipeEndActionPreference
import me.ash.reader.infrastructure.preference.SwipeStartActionPreference
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.component.menu.AnimatedDropdownMenu
import me.ash.reader.ui.component.swipe.SwipeAction
import me.ash.reader.ui.component.swipe.SwipeableActionsBox
import android.util.Log
import me.ash.reader.ui.ext.atElevation
import me.ash.reader.ui.ext.requiresBidi
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.page.settings.color.flow.generateArticleWithFeedPreview

import me.ash.reader.ui.theme.applyTextDirection
import me.ash.reader.ui.theme.palette.onDark

private const val TAG = "ArticleItem"

@Composable
fun ArticleItem(
    modifier: Modifier = Modifier,
    articleWithFeed: ArticleWithFeed,
    isUnread: Boolean = articleWithFeed.article.isUnread,
    translatedTitle: String? = articleWithFeed.article.translatedTitle,  // 2026-02-03: 新增翻译标题参数
    colorTheme: ColorTheme? = null,
    onClick: (ArticleWithFeed) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    forceShowFeedName: Boolean = false, // 2026-01-29: 新增强制显示订阅源名称参数
) {
    val feed = articleWithFeed.feed
    val article = articleWithFeed.article

    ArticleItem(
        modifier = modifier,
        feedName = feed.name,
        feedIconUrl = feed.icon,
        title = article.title,
        translatedTitle = translatedTitle,  // 2026-02-03: 传递翻译标题
        shortDescription = article.shortDescription,
        timeString = article.dateString,
        imgData = article.img,
        disableReferer = feed.isDisableReferer,
        refererUrl = article.link.takeIf { it.isNotBlank() },
        isStarred = article.isStarred,
        isUnread = isUnread,
        colorTheme = colorTheme,
        onClick = { onClick(articleWithFeed) },
        onLongClick = onLongClick,
        forceShowFeedName = forceShowFeedName,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleItem(
    modifier: Modifier = Modifier,
    feedName: String = "",
    feedIconUrl: String? = null,
    title: String = "",
    translatedTitle: String? = null,  // 2026-02-03: 新增翻译标题参数
    shortDescription: String = "",
    timeString: String? = null,
    imgData: Any? = null,
    disableReferer: Boolean = false,
    refererUrl: String? = null,
    isStarred: Boolean = false,
    isUnread: Boolean = false,
    colorTheme: ColorTheme? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    forceShowFeedName: Boolean = false, // 2026-01-29: 新增强制显示订阅源名称参数
) {
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val titleImageUserAgent =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    val articleListFeedName = LocalFlowArticleListFeedName.current
    // 2026-01-29: 计算是否显示订阅源名称（考虑强制显示标志）
    val shouldShowFeedName = articleListFeedName.value || forceShowFeedName
    val articleListImage = LocalFlowArticleListImage.current
    val articleListDesc = LocalFlowArticleListDesc.current
    val articleListDate = LocalFlowArticleListTime.current
    val articleListReadIndicator = LocalFlowArticleListReadIndicator.current
    // 2026-01-18: 新增文章列表样式相关的Preference
    val titleFontSize = LocalFlowArticleListTitleFontSize.current
    val titleLineHeight = LocalFlowArticleListTitleLineHeight.current
    val horizontalPadding = LocalFlowArticleListHorizontalPadding.current
    val verticalPadding = LocalFlowArticleListVerticalPadding.current
    val imageRoundedCorners = LocalFlowArticleListImageRoundedCorners.current
    val imageSize = LocalFlowArticleListImageSize.current
    val roundedCorners = LocalFlowArticleListRoundedCorners.current
    val colorThemes = LocalFlowArticleListColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    val imageBrightness = LocalReadingImageBrightness.current

    Column(
        modifier = modifier
            .padding(horizontal = horizontalPadding.dp)
            .clip(RoundedCornerShape(roundedCorners.dp))
            .background(
                colorTheme?.backgroundColor
                    ?: (if (selectedColorTheme != null) selectedColorTheme.backgroundColor else MaterialTheme.colorScheme.surface)
            )
            .background(
                if (colorTheme != null) colorTheme.backgroundColor.atElevation(
                    sourceColor = MaterialTheme.colorScheme.onSurface, elevation = 1.dp
                )
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = verticalPadding.dp)
            .alpha(
                when (articleListReadIndicator) {
                    FlowArticleReadIndicatorPreference.None -> 1f

                    FlowArticleReadIndicatorPreference.AllRead -> {
                        if (isUnread) 1f else 0.5f
                    }

                    FlowArticleReadIndicatorPreference.ExcludingStarred -> {
                        if (isUnread || isStarred) 1f else 0.5f
                    }
                }
            )
    ) {

        // Bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            // Feed icon
            if (articleListFeedIcon.value) {
                FeedIcon(feedName = feedName, iconUrl = feedIconUrl)
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Article
            Column(modifier = Modifier.weight(1f)) {

                // Title
                Row {
                    Text(
                        text = translatedTitle ?: title,  // 2026-02-03: 优先使用翻译后的标题
                        color = if (selectedColorTheme != null) selectedColorTheme.textColor else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.applyTextDirection((translatedTitle ?: title).requiresBidi())  // 2026-02-03: 使用翻译后的标题进行文本方向检测
                            .merge(
                                fontSize = titleFontSize.sp,
                                lineHeight = (titleFontSize * titleLineHeight).sp
                            ),
                        maxLines = if (articleListDesc != FlowArticleListDescPreference.NONE) 2 else 3,//不显示描述4行改为3行，显示描述2行
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Justify,
                    )
                    if (!shouldShowFeedName && !articleListDate.value) {
                        if (isStarred) {
                            StarredIcon()
                        } else {
//                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }

                // Description
                if (articleListDesc != FlowArticleListDescPreference.NONE && shortDescription.isNotBlank()) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = shortDescription,
                        color = if (selectedColorTheme != null) selectedColorTheme.textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.applyTextDirection(
                            shortDescription.requiresBidi()
                        ),
                        maxLines = when (articleListDesc) {
                            FlowArticleListDescPreference.LONG -> 4
                            FlowArticleListDescPreference.SHORT -> 2
                            else -> throw IllegalStateException()
                        },
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Justify,
                    )
                }
            }

            // Image
            if (imgData != null && articleListImage.value) {
                val brightnessFilter = if (imageBrightness < 100) {
                    val brightnessValue = imageBrightness / 100f
                    androidx.compose.ui.graphics.ColorFilter.lighting(
                        multiply = androidx.compose.ui.graphics.Color(brightnessValue, brightnessValue, brightnessValue),
                        add = androidx.compose.ui.graphics.Color.Transparent
                    )
                } else {
                    null
                }
                if (imgData is String) {
                    Log.d("RLog", "list image: $imgData")
                }
                RYAsyncImage(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(imageSize.dp)
                        .clip(RoundedCornerShape(imageRoundedCorners.dp)),
                    data = imgData,
                    disableReferer = disableReferer,
                    refererUrl = refererUrl,
                    userAgent = titleImageUserAgent,
                    scale = Scale.FILL,
                    precision = Precision.INEXACT,
                    size = SIZE_1000,
                    contentScale = ContentScale.Crop,
                    colorFilter = brightnessFilter,
                )
            }
        }

        // Top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Feed name
            if (shouldShowFeedName) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = if (articleListFeedIcon.value) 30.dp else 0.dp,
                            end = 10.dp,
                        ),
                    text = feedName,
                    color = if (selectedColorTheme != null) selectedColorTheme.textColor else MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier) {
                    // Starred
                    if (isStarred) {
                        StarredIcon()
                    }

                    if (articleListDate.value) {
                        // Time
                        Text(
                            modifier = Modifier,
                            text = timeString ?: "",
                            color = if (selectedColorTheme != null) selectedColorTheme.textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(if (articleListFeedIcon.value) 30.dp else 0.dp))

                    if (articleListDate.value) {
                        // Time
                        Text(
                            modifier = Modifier.weight(1f),
                            text = timeString ?: "",
                            color = if (selectedColorTheme != null) selectedColorTheme.textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        // Starred
                        if (isStarred) {
                            StarredIcon()
                        }
                    }
                }
            }

            // Right

        }

    }
}

@Composable
fun StarredIcon(modifier: Modifier = Modifier) {
    val fontSize = LocalTextStyle.current.fontSize
    val iconSize = with(LocalDensity.current) { fontSize.toDp() }

    Icon(
        modifier = modifier
            .size(iconSize)
            .padding(end = 2.dp),
        imageVector = Icons.Rounded.Star,
        contentDescription = stringResource(R.string.starred),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

private const val PositionalThresholdFraction = 0.4f
private const val SwipeActionDelay = 300L

@Composable
fun SwipeableArticleItem(
    articleWithFeed: ArticleWithFeed,
    isUnread: Boolean = articleWithFeed.article.isUnread,
    translatedTitle: String? = articleWithFeed.article.translatedTitle,  // 2026-02-03: 新增翻译标题参数
    articleListTonalElevation: Int = 0,
    colorTheme: ColorTheme? = null,
    onClick: (ArticleWithFeed) -> Unit = {},
    isSwipeEnabled: () -> Boolean = { false },
    isMenuEnabled: Boolean = true,
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    onSaveToLocal: ((ArticleWithFeed) -> Unit)? = null,
    forceShowFeedName: Boolean = false, // 2026-01-29: 新增强制显示订阅源名称参数
) {

    var isMenuExpanded by remember { mutableStateOf(false) }

    val onLongClick = if (isMenuEnabled) {
        { isMenuExpanded = true }
    } else {
        null
    }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }

    SwipeActionBox(
        articleWithFeed = articleWithFeed,
        isRead = !isUnread,
        isStarred = articleWithFeed.article.isStarred,
        onToggleStarred = onToggleStarred,
        onToggleRead = onToggleRead,
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(isMenuExpanded) {
                awaitEachGesture {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false).let {
                            menuOffset = it.position.round()
                        }
                    }
                }
            }
            .background(
                colorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surfaceColorAtElevation(
                    articleListTonalElevation.dp
                ) onDark MaterialTheme.colorScheme.surface)
            )

            .wrapContentSize()) {
            ArticleItem(
                articleWithFeed = articleWithFeed,
                isUnread = isUnread,
                translatedTitle = translatedTitle,  // 2026-02-03: 传递翻译标题
                colorTheme = colorTheme,
                onClick = onClick,
                onLongClick = onLongClick,
                forceShowFeedName = forceShowFeedName,
            )
            with(articleWithFeed.article) {
                if (isMenuEnabled) {
                    AnimatedDropdownMenu(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                        offset = menuOffset,
                    ) {
                        ArticleItemMenuContent(
                            articleWithFeed = articleWithFeed,
                            isStarred = isStarred,
                            isRead = !isUnread,
                            onToggleStarred = onToggleStarred,
                            onToggleRead = onToggleRead,
                            onMarkAboveAsRead = onMarkAboveAsRead,
                            onMarkBelowAsRead = onMarkBelowAsRead,
                            onShare = onShare,
                            onSaveToLocal = onSaveToLocal,
                        ) {
                            isMenuExpanded = false
                        }
                    }
                }
            }
        }
    }
}

private enum class SwipeDirection {
    StartToEnd, EndToStart,
}

@Composable
private fun SwipeActionBox(
    modifier: Modifier = Modifier,
    articleWithFeed: ArticleWithFeed,
    isStarred: Boolean,
    isRead: Boolean,
    onToggleStarred: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    content: @Composable () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer

    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current

    val onSwipeEndToStart = when (swipeToStartAction) {
        SwipeStartActionPreference.None -> null
        SwipeStartActionPreference.ToggleRead -> onToggleRead
        SwipeStartActionPreference.ToggleStarred -> onToggleStarred
    }

    val onSwipeStartToEnd = when (swipeToEndAction) {
        SwipeEndActionPreference.None -> null
        SwipeEndActionPreference.ToggleRead -> onToggleRead
        SwipeEndActionPreference.ToggleStarred -> onToggleStarred
    }

    if (onSwipeStartToEnd == null && onSwipeEndToStart == null) {
        content()
        return
    }

    val startAction = onSwipeStartToEnd?.let {
        SwipeAction(
            icon = {
                swipeActionIcon(
                    direction = SwipeDirection.StartToEnd,
                    isStarred = isStarred,
                    isRead = isRead,
                )?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            },
            background = containerColor,
            isUndo = false,
            onSwipe = { onSwipeStartToEnd.invoke(articleWithFeed) },
        )
    }

    val endAction = onSwipeEndToStart?.let {
        SwipeAction(
            icon = {
                swipeActionIcon(
                    direction = SwipeDirection.EndToStart,
                    isStarred = isStarred,
                    isRead = isRead,
                )?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            },
            background = containerColor,
            isUndo = false,
            onSwipe = { onSwipeEndToStart.invoke(articleWithFeed) },
        )
    }

    SwipeableActionsBox(
        modifier = modifier,
        startActions = listOfNotNull(startAction),
        endActions = listOfNotNull(endAction),
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surface,
    ) {
        content.invoke()
    }
}

@Composable
private fun swipeActionIcon(
    direction: SwipeDirection,
    isStarred: Boolean,
    isRead: Boolean,
): ImageVector? {
    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current

    val starImageVector =
        remember(isStarred) { if (isStarred) Icons.Outlined.StarOutline else Icons.Rounded.Star }

    val readImageVector =
        remember(isRead) { if (isRead) Icons.Outlined.Circle else Icons.Rounded.CheckCircleOutline }

    return remember(direction) {
        when (direction) {
            SwipeDirection.StartToEnd -> {

                when (swipeToEndAction) {
                    SwipeEndActionPreference.None -> null
                    SwipeEndActionPreference.ToggleRead -> readImageVector
                    SwipeEndActionPreference.ToggleStarred -> starImageVector
                }
            }

            SwipeDirection.EndToStart -> {
                when (swipeToStartAction) {
                    SwipeStartActionPreference.None -> null
                    SwipeStartActionPreference.ToggleRead -> readImageVector
                    SwipeStartActionPreference.ToggleStarred -> starImageVector
                }
            }
        }
    }
}

@Composable
private fun swipeActionText(
    direction: SwipeDirection,
    isStarred: Boolean,
    isRead: Boolean,
): String {
    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current

    val starText =
        stringResource(if (isStarred) R.string.mark_as_unstar else R.string.mark_as_starred)

    val readText = stringResource(if (isRead) R.string.mark_as_unread else R.string.mark_as_read)

    return remember(direction) {
        when (direction) {
            SwipeDirection.StartToEnd -> {
                when (swipeToEndAction) {
                    SwipeEndActionPreference.None -> "null"
                    SwipeEndActionPreference.ToggleRead -> readText
                    SwipeEndActionPreference.ToggleStarred -> starText
                }
            }

            SwipeDirection.EndToStart -> {
                when (swipeToStartAction) {
                    SwipeStartActionPreference.None -> "null"
                    SwipeStartActionPreference.ToggleRead -> readText
                    SwipeStartActionPreference.ToggleStarred -> starText
                }
            }
        }
    }
}

@Composable
fun ArticleItemMenuContent(
    articleWithFeed: ArticleWithFeed,
    iconSize: DpSize = DpSize(width = 20.dp, height = 20.dp),
    isStarred: Boolean = false,
    isRead: Boolean = false,
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    onSaveToLocal: ((ArticleWithFeed) -> Unit)? = null,
    onItemClick: (() -> Unit)? = null,
) {
    val starImageVector =
        remember(isStarred) { if (isStarred) Icons.Outlined.StarOutline else Icons.Rounded.Star }

    val readImageVector = remember(isRead) {
        if (isRead) Icons.Outlined.FiberManualRecord else Icons.Rounded.FiberManualRecord
    }

    val starText =
        stringResource(if (isStarred) R.string.mark_as_unstar else R.string.mark_as_starred)

    val readText = stringResource(if (isRead) R.string.mark_as_unread else R.string.mark_as_read)

    DropdownMenuItem(
        text = { Text(text = readText) },
        onClick = {
            onToggleRead(articleWithFeed)
            onItemClick?.invoke()
        },
        leadingIcon = {
            Icon(
                imageVector = readImageVector,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        },
    )
    DropdownMenuItem(
        text = { Text(text = starText) },
        onClick = {
            onToggleStarred(articleWithFeed)
            onItemClick?.invoke()
        },
        leadingIcon = {
            Icon(
                imageVector = starImageVector,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        },
    )

    if (onMarkAboveAsRead != null || onMarkBelowAsRead != null) {
        HorizontalDivider()
    }
    onMarkAboveAsRead?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.mark_above_as_read)) },
            onClick = {
                onMarkAboveAsRead(articleWithFeed)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    onMarkBelowAsRead?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.mark_below_as_read)) },
            onClick = {
                onMarkBelowAsRead(articleWithFeed)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    onShare?.let {
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.share)) },
            onClick = {
                onShare(articleWithFeed)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    onSaveToLocal?.let {
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.save_article_to_local)) },
            onClick = {
                onSaveToLocal(articleWithFeed)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
}

@Preview
@Composable
fun MenuContentPreview() {
    MaterialTheme {
        Surface() {
            Column(modifier = Modifier.padding()) {
                ArticleItemMenuContent(
                    articleWithFeed = generateArticleWithFeedPreview(),
                    onMarkBelowAsRead = {},
                    onMarkAboveAsRead = {},
                    onShare = {},
                    onSaveToLocal = {},
                )
            }
        }
    }
}
