package com.datvip.cam

import org.junit.Assert.*
import org.junit.Test

class CameraOrientationTest {
    @Test fun turnsBothWaysAndReturnsToPortrait() {
        val filter = CameraOrientation()
        var time = 0L
        fun feed(x: Double, y: Double) { repeat(40) { time += 50; filter.update(x, y, time) } }
        feed(0.0, 1.0); assertEquals(0, filter.angle)
        feed(-1.0, 0.0); assertEquals(90, filter.angle)
        assertEquals(0f, filter.tilt!!, .1f)
        feed(1.0, 0.0); assertEquals(-90, filter.angle)
        feed(0.0, 1.0); assertEquals(0, filter.angle)
    }
    @Test fun boundaryJitterAndFlatPhoneDoNotRotate() {
        val filter = CameraOrientation()
        repeat(100) { filter.update(-.7, if (it % 2 == 0) .69 else .72, it * 50L) }
        assertEquals(0, filter.angle)
        repeat(60) { filter.update(0.0, 0.0, (it + 100) * 50L) }
        assertEquals(0, filter.angle); assertNull(filter.tilt)
    }
    @Test fun briefTurnDoesNotCommitOrientation() {
        val filter = CameraOrientation()
        filter.update(-1.0, 0.0, 10000)
        filter.update(-1.0, 0.0, 10100)
        assertEquals(0, filter.angle)
        filter.update(-1.0, 0.0, 10300)
        assertEquals(90, filter.angle)
    }
}
