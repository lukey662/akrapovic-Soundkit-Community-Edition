package com.akrapovic.soundkit.community.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Renders [ValveArt] to a bitmap for surfaces without Compose: Android Auto tiles and the widget.
 */
object ValveBitmaps {
    private val cache = LruCache<String, Bitmap>(12)

    fun render(sizePx: Int, openAmount: Float, palette: ValveArtPalette, density: Float): Bitmap {
        val key = "$sizePx-$openAmount-${palette.glow.toArgb()}-${palette.heatTintedLip}-$density"
        cache.get(key)?.let { return it }
        val image = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(
            density = Density(density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = Size(sizePx.toFloat(), sizePx.toFloat()),
        ) {
            with(ValveArt) { drawValve(openAmount = openAmount, palette = palette) }
        }
        val bitmap = image.asAndroidBitmap()
        cache.put(key, bitmap)
        return bitmap
    }
}
