package com.pawprint.gachapaw.service

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class GpioManager {
    companion object {
        init {
            // Load the library. Note: Do NOT include "lib" prefix or ".so" suffix.
            System.loadLibrary("gpio-lib")
        }
    }
    fun setNeopixelColor(color: Color) {
        setNeopixelColor(color.toArgb())
    }
    external fun setGpioState(pin: Int, state: Boolean)
    external fun setNeopixelColor(color: Int)
}