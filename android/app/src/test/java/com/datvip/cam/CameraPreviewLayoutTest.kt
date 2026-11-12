package com.datvip.cam

import org.junit.Assert.*
import org.junit.Test

class CameraPreviewLayoutTest {
    @Test fun sideRailPreservesFullHeightAndTextureSizeOnPhone() {
        // 360x760 logical dp, after insets. Portrait controls use 180dp;
        // the landscape side rail uses 168dp of the long edge.
        val portrait = CameraPreviewLayout.fit(360, 760 - 180, false)
        val landscape = CameraPreviewLayout.fit(760 - 168, 360, true)
        assertEquals(360, landscape.height)
        assertEquals(480, landscape.width)
        // The inverse-rotated PreviewView keeps identical buffer dimensions.
        assertEquals(portrait.width, landscape.height)
        assertEquals(portrait.height, landscape.width)
    }

    @Test fun narrowAndShortWindowsKeepCompleteFourByThreeFrame() {
        for (width in listOf(240, 320, 360, 600, 1000, 1920)) {
            for (height in listOf(200, 320, 480, 900, 1600)) {
                for (landscape in listOf(false, true)) {
                    val size = CameraPreviewLayout.fit(width, height, landscape)
                    assertTrue(size.width in 1..width)
                    assertTrue(size.height in 1..height)
                    val error = if (landscape) size.width * 3 - size.height * 4
                        else size.width * 4 - size.height * 3
                    assertTrue("$width x $height, landscape=$landscape: $size", kotlin.math.abs(error) <= 4)
                    assertTrue(size.width == width || size.height == height)
                }
            }
        }
    }

    @Test fun unmeasuredStageDoesNotCreateNegativeDimensions() {
        assertEquals(CameraPreviewLayout.Size(0, 0), CameraPreviewLayout.fit(0, 600, false))
        assertEquals(CameraPreviewLayout.Size(0, 0), CameraPreviewLayout.fit(800, 0, true))
    }
}
