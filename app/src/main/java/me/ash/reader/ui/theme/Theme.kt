package me.ash.reader.ui.theme

import android.os.Build
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import me.ash.reader.infrastructure.preference.LocalBasicFonts
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorTheme
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.preference.LocalFlowArticleListColorThemes
import me.ash.reader.infrastructure.preference.LocalThemeIndex
import me.ash.reader.ui.theme.palette.LocalTonalPalettes
import me.ash.reader.ui.theme.palette.TonalPalettes
import me.ash.reader.ui.theme.palette.core.ProvideZcamViewingConditions
import me.ash.reader.ui.theme.palette.dynamic.extractTonalPalettesFromUserWallpaper
import me.ash.reader.ui.theme.palette.dynamicDarkColorScheme
import me.ash.reader.ui.theme.palette.dynamicLightColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    useDarkTheme: Boolean,
    wallpaperPalettes: List<TonalPalettes> = extractTonalPalettesFromUserWallpaper(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    LaunchedEffect(useDarkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (useDarkTheme) {
                view.windowInsetsController?.setSystemBarsAppearance(
                    0, APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                view.windowInsetsController?.setSystemBarsAppearance(
                    APPEARANCE_LIGHT_STATUS_BARS, APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }
    }

    val themeIndex = LocalThemeIndex.current

    val tonalPalettes = wallpaperPalettes[
        if (themeIndex >= wallpaperPalettes.size) {
            when {
                wallpaperPalettes.size == 5 -> 0
                wallpaperPalettes.size > 5 -> 5
                else -> 0
            }
        } else {
            themeIndex
        }
    ]

    ProvideZcamViewingConditions {
        CompositionLocalProvider(
            LocalTonalPalettes provides tonalPalettes.apply { Preparing() },
            LocalTextStyle provides LocalTextStyle.current.applyTextDirection()
        ) {
            val lightColors = dynamicLightColorScheme()
            val darkColors = dynamicDarkColorScheme()
            val feedsColorThemes = LocalFeedsPageColorThemes.current
            val flowColorThemes = LocalFlowArticleListColorThemes.current
            val selectedFeedsColorTheme = feedsColorThemes.firstOrNull { it.isDefault } ?: feedsColorThemes.firstOrNull()
            val selectedFlowColorTheme = flowColorThemes.firstOrNull { it.isDefault } ?: flowColorThemes.firstOrNull()
            
            val baseColorScheme = if (useDarkTheme) darkColors else lightColors
            
            // 根据当前上下文决定使用哪个主题的背景色
            // 优先使用 FeedsPage 的主题，如果没有则使用 FlowPage 的主题
            val themeBackgroundColor = selectedFeedsColorTheme?.backgroundColor 
                ?: selectedFlowColorTheme?.backgroundColor
            
            val colorScheme = if (themeBackgroundColor != null) {
                baseColorScheme.copy(
                    surface = themeBackgroundColor,
                    background = themeBackgroundColor,
                    primary = selectedFeedsColorTheme?.primaryColor ?: baseColorScheme.primary,
                    onPrimary = baseColorScheme.onPrimary,
                    primaryContainer = (selectedFeedsColorTheme?.primaryColor ?: baseColorScheme.primary).copy(alpha = 0.12f),
                    onPrimaryContainer = selectedFeedsColorTheme?.primaryColor ?: baseColorScheme.primary,
                )
            } else {
                baseColorScheme
            }
            
            MaterialTheme(
                motionScheme = MotionScheme.expressive(),
                colorScheme = colorScheme,
                typography = LocalBasicFonts.current.asTypography(LocalContext.current)
                    .applyTextDirection(),
                shapes = Shapes,
                content = content,
            )
        }
    }
}
