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
    private var previousTime: Long? = null
    private var candidate = 0
    private var candidateSince = 0L
    fun update(rawX: Double, rawY: Double, timeMs: Long) {
        if (!rawX.isFinite() || !rawY.isFinite()) { tilt = null; return }
        val elapsed = previousTime?.let { (timeMs - it).coerceAtLeast(0) }
        previousTime = timeMs
        val alpha = elapsed?.let { 1 - exp(-it / 80.0) } ?: 1.0
        if (hypot(rawX, rawY) <= .45) {
            tilt = null; initialized = false; candidate = angle; candidateSince = timeMs; return
        }
        if (!initialized) { x = rawX; y = rawY; initialized = true }
        else { x += alpha * (rawX - x); y += alpha * (rawY - y) }
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
