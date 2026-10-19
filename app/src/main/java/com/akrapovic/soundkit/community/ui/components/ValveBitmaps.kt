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
 *
 * The returned bitmap is immutable. Android Auto sends bitmaps to the host over binder, and an
 * immutable bitmap can be shared by file descriptor instead of inlining every pixel into the
 * transaction, which keeps a three-tile template well inside the 1MB binder budget.
 */
object ValveBitmaps {
    private val cache = LruCache<String, Bitmap>(12)

    fun render(
        sizePx: Int,
        openAmount: Float,
        palette: ValveArtPalette,
        density: Float,
        maxPx: Int = Int.MAX_VALUE,
        contentAlpha: Float = 1f,
    ): Bitmap {
        val size = sizePx.coerceIn(1, maxPx.coerceAtLeast(1))
        val key = "$size-$openAmount-${palette.glow.toArgb()}-${palette.heatTintedLip}-$density-$contentAlpha"
        cache.get(key)?.let { return it }
        val image = ImageBitmap(size, size)
        CanvasDrawScope().draw(
            density = Density(density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = Size(size.toFloat(), size.toFloat()),
        ) {
            with(ValveArt) { drawValve(openAmount = openAmount, palette = palette, contentAlpha = contentAlpha) }
        }
        val mutable = image.asAndroidBitmap()
        val bitmap = mutable.copy(Bitmap.Config.ARGB_8888, false) ?: mutable
        if (bitmap !== mutable) mutable.recycle()
        cache.put(key, bitmap)
        return bitmap
    }
}
