package com.rcmiku.media.control.tweak.utils

object MathUtil {

    fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return (1 - fraction) * start + fraction * stop
    }

    fun lerpInv(a: Float, b: Float, value: Float): Float {
        return if (a != b) (value - a) / (b - a) else 0f
    }

    private fun saturate(value: Float): Float {
        return value.coerceIn(0f, 1f)
    }

    fun lerpInvSat(a: Float, b: Float, value: Float): Float {
        return saturate(lerpInv(a, b, value))
    }

}