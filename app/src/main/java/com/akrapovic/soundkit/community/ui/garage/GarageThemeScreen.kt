package com.akrapovic.soundkit.community.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.GarageTheme
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets

@Composable
fun GarageThemeScreen(
    modifier: Modifier = Modifier,
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AkraColors.Ink)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Eyebrow("// GARAGE / THEMES")
            Text(
                text = "Brand-inspired presets",
                style = MaterialTheme.typography.displaySmall,
                color = AkraColors.Pearl,
            )
            Text(
                text = "Community palettes only. No official logos, badges, or OEM UI copies.",
                style = MaterialTheme.typography.bodyMedium,
                color = AkraColors.Silver,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        items(GarageThemePresets, key = { it.id }) { theme ->
            GarageThemeCard(
                theme = theme,
                selected = theme.id == selectedThemeId,
                onClick = { onThemeSelected(theme.id) },
            )
        }
    }
}

@Composable
private fun GarageThemeCard(
    theme: GarageTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(AkraColors.Carbon)
            .border(
                width = 1.dp,
                color = if (selected) theme.highlight else AkraColors.Titanium,
                shape = RoundedCornerShape(2.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Select ${theme.name} theme" }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeSwatch(theme = theme)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = AkraColors.Pearl,
                )
                Text(
                    text = theme.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AkraColors.Silver,
                )
            }
            if (selected) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.highlight,
                )
            }
        }
        Text(
            text = "Preview only for now. Persisted app-wide theming can follow once the navigation and roadmap surfaces settle.",
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Mist,
        )
    }
}

@Composable
private fun ThemeSwatch(theme: GarageTheme) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(2.dp)),
    ) {
        SwatchBlock(theme.base)
        SwatchBlock(theme.surface)
        SwatchBlock(theme.accent)
        SwatchBlock(theme.secondaryAccent)
        SwatchBlock(theme.highlight)
    }
}

@Composable
private fun SwatchBlock(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 10.dp, height = 36.dp)
            .background(color),
    )
}

@Composable
private fun Eyebrow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = AkraColors.Amber,
    )
}
