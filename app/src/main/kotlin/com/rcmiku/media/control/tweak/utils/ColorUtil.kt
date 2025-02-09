package com.rcmiku.media.control.tweak.utils

object ColorUtil {

    fun setAlphaComponent(
        color: Int,
        alpha: Int
    ): Int {
        require(alpha in 0..255) { "alpha must be between 0 and 255." }
        return (color and 0x00ffffff) or (alpha shl 24)
    }
}