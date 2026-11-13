package com.datvip.cam

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

object PhotoMetadata {
    fun copy(source: ByteArray, target: ExifInterface, width: Int, height: Int) {
        val original = ExifInterface(ByteArrayInputStream(source))
        // Retain camera, capture time, exposure and GPS metadata; never copy stale geometry/offsets.
        val excluded = setOf("ImageWidth", "ImageLength", "PixelXDimension", "PixelYDimension",
            "Orientation", "ThumbnailImageWidth", "ThumbnailImageLength", "JPEGInterchangeFormat",
            "JPEGInterchangeFormatLength", "StripOffsets", "StripByteCounts", "DefaultCropSize",
            "SubjectArea", "SubjectLocation")
        for (field in ExifInterface::class.java.fields) {
            if (!field.name.startsWith("TAG_") || field.type != String::class.java) continue
            val tag = field.get(null) as String
            if (tag !in excluded) original.getAttribute(tag)?.let { target.setAttribute(tag, it) }
        }
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        target.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
        target.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
        target.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        target.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
    }
}
