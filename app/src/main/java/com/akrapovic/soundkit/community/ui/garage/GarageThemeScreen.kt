package com.akrapovic.soundkit.community.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.GarageTheme
import com.akrapovic.soundkit.community.ui.theme.GarageThemeFamilies
import com.akrapovic.soundkit.community.ui.theme.GarageThemeFamily

@Composable
fun GarageThemeScreen(
    modifier: Modifier = Modifier,
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AkraHeroHeader(
                eyebrow = "Garage themes",
                title = "Pick the mood",
                subtitle = "Choose a car-inspired family, then pick the light or dark version.",
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        items(GarageThemeFamilies, key = { it.id }) { family ->
            GarageThemeFamilyCard(
                family = family,
                selectedThemeId = selectedThemeId,
                onThemeSelected = onThemeSelected,
            )
        }
    }
}

@Composable
private fun GarageThemeFamilyCard(
    family: GarageThemeFamily,
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
) {
    val selected = family.contains(selectedThemeId)
    val previewTheme = when (selectedThemeId) {
        family.light.id -> family.light
        else -> family.dark
    }

    AkraCard(
        accent = if (selected) previewTheme.accent else MaterialTheme.colorScheme.outline,
        contentDescription = "Select ${family.name} theme",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(
                theme = previewTheme,
                contentDescription = "${family.name} brand mark",
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = family.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                AkraStatusPill(text = if (previewTheme.isDark) "DARK" else "LIGHT", color = previewTheme.highlight)
            }
        }
        ThemeSwatch(theme = previewTheme)
        VariantSelector(
            family = family,
            selectedThemeId = selectedThemeId,
            onThemeSelected = onThemeSelected,
        )
        Text(
            text = if (selected) "Applied across the app and saved for next launch." else "Choose Light or Dark to apply instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrandMark(
    theme: GarageTheme,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(theme.brandMark),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun VariantSelector(
    family: GarageThemeFamily,
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VariantButton(
            label = "Light",
            selected = selectedThemeId == family.light.id,
            onClick = { onThemeSelected(family.light.id) },
            modifier = Modifier.weight(1f),
        )
        VariantButton(
            label = "Dark",
            selected = selectedThemeId == family.dark.id,
            onClick = { onThemeSelected(family.dark.id) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VariantButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label theme variant" }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeSwatch(theme: GarageTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SwatchBlock(Modifier.weight(1f), theme.base, label = "Base")
        SwatchBlock(Modifier.weight(1f), theme.surface, label = "Surface")
        SwatchBlock(Modifier.weight(1f), theme.accent, label = "Accent")
        SwatchBlock(Modifier.weight(1f), theme.secondaryAccent, label = "Secondary")
        SwatchBlock(Modifier.weight(1f), theme.highlight, label = "Highlight")
    }
}

@Composable
private fun RowScope.SwatchBlock(
    modifier: Modifier,
    color: Color,
    label: String,
) {
    Column(
        modifier = modifier
            .height(52.dp)
            .background(color)
            .semantics { contentDescription = "$label color swatch" },
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (color.luminance() > 0.55f) Color(0xFF111111) else Color(0xFFF5F5F5),
            modifier = Modifier.padding(4.dp),
            maxLines = 1,
        )
    }
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

