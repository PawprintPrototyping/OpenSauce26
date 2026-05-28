package com.pawprint.gachapaw.service

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GpioManager {
    companion object {
        const val TAG = "GpioManager"
        init {
            // Load the library. Note: Do NOT include "lib" prefix or ".so" suffix.
            System.loadLibrary("gpio-lib")
        }
    }

    suspend fun waitForGpioState(expectedState: Boolean): Int = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cause ->
            Log.i(TAG, "waitForGpioState cancelled, cause: $cause")
            cancelWaitForGpio()
        }
        val result = waitForGpio(26 /*pi gpio pin*/, expectedState)
        continuation.resume(result)
    }

    fun setNeopixelColor(color: Color) {
        setNeopixelColor(color.toArgb())
    }

    fun displayOnLcd(text: String) {
        updateLcdText(text)
    }

    external fun waitForGpio(pin: Int, expectedState: Boolean): Int
    external fun setGpioState(pin: Int, state: Boolean)
    external fun setNeopixelColor(color: Int)
    external fun updateLcdText(text: String)
    external fun cancelWaitForGpio()
}