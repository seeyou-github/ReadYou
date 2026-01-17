package me.ash.reader.ui.component.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import me.ash.reader.domain.model.theme.ColorTheme

@Immutable
data class ReaderPaints(
    val bodyText: Color,
    val linkText: Color,
    val codeBlockBackground: Color,
    val codeBlockText: Color,
    val blockquoteText: Color,
    val blockquoteBorder: Color,
    val background: Color,
    // Add other specific colors for different elements if needed
)

private val dummyReaderPaints = ReaderPaints(
    bodyText = Color.Black,
    linkText = Color.Blue,
    codeBlockBackground = Color.LightGray,
    codeBlockText = Color.Black,
    blockquoteText = Color.Gray,
    blockquoteBorder = Color.Gray,
    background = Color.White
)

val LocalReaderPaints = compositionLocalOf { dummyReaderPaints }

@Composable
fun defaultReaderPaints(): ReaderPaints {
    // These defaults should ideally come from MaterialTheme.colorScheme
    // For now, use some sensible defaults.
    return ReaderPaints(
        bodyText = MaterialTheme.colorScheme.onBackground,
        linkText = MaterialTheme.colorScheme.primary,
        codeBlockBackground = MaterialTheme.colorScheme.surfaceVariant,
        codeBlockText = MaterialTheme.colorScheme.onSurfaceVariant,
        blockquoteText = MaterialTheme.colorScheme.onSurfaceVariant,
        blockquoteBorder = MaterialTheme.colorScheme.outline,
        background = MaterialTheme.colorScheme.background
    )
}

@Composable
fun colorThemeToReaderPaints(colorTheme: ColorTheme): ReaderPaints {
    val isLight = colorTheme.backgroundColor.luminance() > 0.5f // Simple check for light/dark

    // Use theme's colors for main text/background
    val body = colorTheme.textColor
    val background = colorTheme.backgroundColor

    // Derive other colors, potentially based on the main colors or MaterialTheme
    val link = MaterialTheme.colorScheme.primary // Often links use primary color
    val codeBlockBg = if (isLight) Color(0xFFE0E0E0) else Color(0xFF333333) // Light/dark dependent
    val codeBlockTxt = if (isLight) Color.DarkGray else Color.LightGray
    val blockquoteTxt = if (isLight) Color.Gray else Color.LightGray.copy(alpha = 0.7f)
    val blockquoteBorder = if (isLight) Color.LightGray else Color.DarkGray.copy(alpha = 0.7f)


    return ReaderPaints(
        bodyText = body,
        linkText = link,
        codeBlockBackground = codeBlockBg,
        codeBlockText = codeBlockTxt,
        blockquoteText = blockquoteTxt,
        blockquoteBorder = blockquoteBorder,
        background = background
    )
}
