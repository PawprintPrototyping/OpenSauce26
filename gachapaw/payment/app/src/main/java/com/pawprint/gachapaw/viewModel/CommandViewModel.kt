package com.pawprint.gachapaw.viewModel

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.pawprint.gachapaw.service.GpioManager
import com.pawprint.gachapaw.model.CommandUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class CommandViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CommandUiState())
    private val gpioManager = GpioManager()
    val uiState: StateFlow<CommandUiState> = _uiState.asStateFlow()

    fun triggerEnablePrizeDispenser() {
        gpioManager.setGpioState(5,false)
    }

    fun getRandomOpaqueColor(): Color {
        return Color(
            red = Random.nextInt(256),
            green = Random.nextInt(256),
            blue = Random.nextInt(256),
            alpha = 255
        )
    }

    fun triggerSetNeopixelColor() {
        gpioManager.setNeopixelColor(getRandomOpaqueColor())
    }
    fun triggerEnableSquareReader() {
        Log.d("CommandViewModel", "triggerEnableCardReader")
    }
}
