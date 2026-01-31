package me.ash.reader.infrastructure.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsPageColorThemes
import me.ash.reader.infrastructure.translate.TranslateProviders
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.theme.palette.onLight

/**
 * AI提供商列表页面
 */
@Composable
fun ProviderListPage(
    onBack: () -> Unit,
    onProviderClick: (String) -> Unit
) {
    val colorThemes = LocalFeedsPageColorThemes.current
    val selectedColorTheme = colorThemes.firstOrNull { it.isDefault } ?: colorThemes.firstOrNull()
    
    val providers = TranslateProviders.ALL

    RYScaffold(
        containerColor = selectedColorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        topBarColor = selectedColorTheme?.backgroundColor ?: (MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface),
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    DisplayText(
                        text = "AI提供商",
                        desc = ""
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                items(providers) { provider ->
                    Column {
                        ProviderItem(
                            name = provider.name,
                            description = provider.description,
                            onClick = { onProviderClick(provider.id) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}

@Composable
private fun ProviderItem(
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
