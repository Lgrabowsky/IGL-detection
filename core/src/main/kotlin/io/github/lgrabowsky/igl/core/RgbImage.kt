package io.github.lgrabowsky.igl.core

/** Minimaler, plattformunabhängiger Lesezugriff auf ein Farbbild. */
interface RgbImage {
    val width: Int
    val height: Int

    /** Farbe an (x, y) als 0xAARRGGBB. Die Koordinaten müssen im Bild liegen. */
    fun argb(x: Int, y: Int): Int
}

/** [RgbImage] auf Basis eines zeilenweise abgelegten ARGB-Arrays (z. B. aus `Bitmap.getPixels`). */
class IntArrayRgbImage(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
) : RgbImage {
    init {
        require(width > 0 && height > 0) { "Bildgröße muss positiv sein" }
        require(pixels.size >= width * height) { "Pixel-Array zu klein" }
    }

    override fun argb(x: Int, y: Int): Int = pixels[y * width + x]
}
