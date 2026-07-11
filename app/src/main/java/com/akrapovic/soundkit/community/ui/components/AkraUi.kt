package com.akrapovic.soundkit.community.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

val AkraSurfaceShape = RoundedCornerShape(16.dp)
val AkraCardShape = AkraSurfaceShape
val AkraButtonShape = RoundedCornerShape(999.dp)
val AkraGroupShape = RoundedCornerShape(16.dp)

@Composable
fun AkraScreen(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 20.dp)

    Column(
        modifier = modifier.then(if (scroll) base.verticalScroll(rememberScrollState()) else base),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun AkraHeroHeader(
    eyebrow: String? = null,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (compact) 8.dp else 12.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (eyebrow != null && !compact) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = if (compact) {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = titleModifier,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AkraSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = clickableModifier(onClick, contentDescription)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkraSurfaceShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AkraSurfaceShape)
            .then(clickableModifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun AkraElevated(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = clickableModifier(onClick, contentDescription)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, AkraSurfaceShape, clip = false)
            .clip(AkraSurfaceShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AkraSurfaceShape)
            .then(clickableModifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Backward-compatible alias — maps to flat [AkraSurface]. */
@Composable
fun AkraCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AkraSurface(
        modifier = modifier,
        onClick = onClick,
        contentDescription = contentDescription,
        content = content,
    )
}

@Composable
fun AkraListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkraGroupShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AkraGroupShape),
        content = content,
    )
}

@Composable
fun AkraListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    val clickableModifier = clickableModifier(onClick, contentDescription ?: title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AkraListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun AkraInlineStatus(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = AkraColors.Signal,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AkraBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkraSurfaceShape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.22f), AkraSurfaceShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            AkraActionButton(
                label = actionLabel,
                filled = false,
                onClick = onAction,
            )
        }
    }
}

@Composable
fun <T> AkraSegmentedControl(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkraButtonShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AkraButtonShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelected(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun AkraActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    contentDescription: String = label,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val akraTheme = LocalAkraTheme.current
    val screenBackground = MaterialTheme.colorScheme.background
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "buttonScale",
    )
    val filledGradientColors = remember(accent, akraTheme.secondaryAccent, screenBackground) {
        filledActionGradientColors(
            accent = accent,
            secondaryAccent = akraTheme.secondaryAccent,
            background = screenBackground,
        )
    }
    val background = when {
        !enabled -> Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant),
        )
        filled -> Brush.linearGradient(filledGradientColors)
        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        filled -> MaterialTheme.colorScheme.onPrimary
        else -> accent
    }

    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 52.dp)
            .clip(AkraButtonShape)
            .background(background)
            .border(
                BorderStroke(1.dp, if (enabled) accent.copy(alpha = if (filled) 0f else 0.35f) else AkraColors.Titanium),
                AkraButtonShape,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun AkraStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun AkraSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AkraSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                stateDescription = if (checked) "$title enabled" else "$title disabled"
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = AkraColors.Mist,
                uncheckedTrackColor = AkraColors.Titanium,
                uncheckedBorderColor = AkraColors.Steel,
            ),
        )
    }
}

@Composable
fun AkraButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private const val LOW_CONTRAST_COLOR_DELTA = 0.15f

private fun filledActionGradientColors(
    accent: Color,
    secondaryAccent: Color,
    background: Color,
): List<Color> {
    val gradientEnd = if (colorsTooSimilar(secondaryAccent, background)) {
        lerp(accent, Color.Black, 0.25f)
    } else {
        secondaryAccent
    }
    return listOf(accent, gradientEnd)
}

private fun colorsTooSimilar(first: Color, second: Color): Boolean {
    val delta = kotlin.math.abs(first.red - second.red) +
        kotlin.math.abs(first.green - second.green) +
        kotlin.math.abs(first.blue - second.blue)
    return delta < LOW_CONTRAST_COLOR_DELTA
}

private fun clickableModifier(onClick: (() -> Unit)?, contentDescription: String?): Modifier {
    if (onClick == null) return Modifier
    return Modifier
        .clickable(role = Role.Button, onClick = onClick)
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
}
