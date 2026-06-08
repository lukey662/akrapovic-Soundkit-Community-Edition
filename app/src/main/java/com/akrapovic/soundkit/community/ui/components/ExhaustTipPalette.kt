package com.akrapovic.soundkit.community.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.akrapovic.soundkit.community.ui.theme.AkraColors

/** Fixed Akrapovic-inspired material colours for the exhaust tip hero. */
object ExhaustTipPalette {
    fun carbonSleeve(topLeft: Offset, size: Size): Brush {
        return Brush.linearGradient(
            colors = listOf(
                Color(0xFF1A1A1A),
                AkraColors.Carbon,
                Color(0xFF0A0A0A),
            ),
            start = Offset(topLeft.x, topLeft.y),
            end = Offset(topLeft.x + size.width, topLeft.y + size.height),
        )
    }

    fun titaniumLip(topLeft: Offset, size: Size): Brush {
        return Brush.linearGradient(
            colors = listOf(
                AkraColors.Silver,
                AkraColors.Steel,
                AkraColors.Titanium,
                AkraColors.Graphite,
            ),
            start = Offset(topLeft.x, topLeft.y),
            end = Offset(topLeft.x + size.width * 0.85f, topLeft.y + size.height * 0.9f),
        )
    }

    fun boreInterior(center: Offset, radiusX: Float, radiusY: Float, closed: Boolean): Brush {
        val edge = if (closed) AkraColors.Steel else AkraColors.Titanium
        return Brush.radialGradient(
            colors = listOf(
                Color(0xFF050505),
                AkraColors.Graphite,
                edge.copy(alpha = 0.85f),
            ),
            center = center,
            radius = maxOf(radiusX, radiusY),
        )
    }

    fun heatGlow(center: Offset, radiusX: Float, radiusY: Float, accent: Color, alpha: Float): Brush {
        return Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = alpha * 0.55f),
                AkraColors.Amber.copy(alpha = alpha * 0.35f),
                Color.Transparent,
            ),
            center = center,
            radius = maxOf(radiusX, radiusY) * 0.85f,
        )
    }

    val hatchColor: Color = Color.White.copy(alpha = 0.06f)
    val specularColor: Color = AkraColors.Pearl.copy(alpha = 0.35f)
    val shadowColor: Color = Color.Black.copy(alpha = 0.45f)
    val plateColor: Brush get() = Brush.linearGradient(
        colors = listOf(
            AkraColors.Silver.copy(alpha = 0.95f),
            AkraColors.Steel,
            AkraColors.Titanium,
        ),
    )
}
