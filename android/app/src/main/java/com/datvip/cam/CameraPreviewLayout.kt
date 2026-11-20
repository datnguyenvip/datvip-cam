package com.datvip.cam

/** Fits the complete 4:3 photo frame inside the available viewfinder area. */
internal object CameraPreviewLayout {
    data class Size(val width: Int, val height: Int)

    fun fit(width: Int, height: Int, landscape: Boolean): Size {
        if (width <= 0 || height <= 0) return Size(0, 0)
        val longSide = if (landscape) 4 else 3
        val shortSide = if (landscape) 3 else 4
        return if (width.toLong() * shortSide <= height.toLong() * longSide) {
            Size(width, (width.toLong() * shortSide / longSide).toInt())
        } else {
            Size((height.toLong() * longSide / shortSide).toInt(), height)
        }
    }
}
