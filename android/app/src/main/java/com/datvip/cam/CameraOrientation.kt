package com.datvip.cam

import kotlin.math.*

/** Angles are derived from gravity, independent of the system rotation lock. */
class CameraOrientation {
    var angle = 0
        private set
    var tilt: Float? = null
        private set
    private var x = 0.0
    private var y = 1.0
    private var initialized = false
    private var candidate = 0
    private var candidateSince = 0L
    fun update(rawX: Double, rawY: Double, timeMs: Long) {
        if (!rawX.isFinite() || !rawY.isFinite()) return
        if (!initialized) { x = rawX; y = rawY; initialized = true }
        else { x += .18 * (rawX - x); y += .18 * (rawY - y) }
        if (hypot(x, y) <= .45) { tilt = null; candidate = angle; candidateSince = timeMs; return }
        val measured = Math.toDegrees(atan2(x, y))
        val nearest = (measured / 90).roundToInt() * 90
        val normalized = if (nearest == -180) 180 else nearest
        if (abs(measured - nearest) <= 30) {
            if (candidate != normalized) { candidate = normalized; candidateSince = timeMs }
            if (timeMs - candidateSince >= 250) angle = candidate
        } else { candidate = angle; candidateSince = timeMs }
        var error = measured - angle
        while (error > 180) error -= 360
        while (error < -180) error += 360
        tilt = error.toFloat()
    }
}
