package com.datvip.cam

/** Bounds of non-transparent pixels. Color values, including opaque white, are never removed. */
object LogoAlphaBounds {
    fun find(width: Int, height: Int, pixels: IntArray): IntArray? {
        require(width > 0 && height > 0 && pixels.size == width * height)
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 != 0) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        return if (right < left) null else intArrayOf(left, top, right - left + 1, bottom - top + 1)
    }
}
