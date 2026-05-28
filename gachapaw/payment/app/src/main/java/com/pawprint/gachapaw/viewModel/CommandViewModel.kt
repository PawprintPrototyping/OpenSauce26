package com.pawprint.gachapaw.viewModel

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawprint.gachapaw.service.GpioManager
import com.pawprint.gachapaw.model.CommandUiState
import com.pawprint.gachapaw.model.InputGpioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    fun triggerWaitForGpio() {
        Log.d("CommandViewModel", "triggerWaitForGpio")
        if (_uiState.value.inputGpioState == InputGpioState.WAITING) {
            Log.d("CommandViewModel", "triggerEnableCardReader: waiting")
            return
        }
        _uiState.update { it.copy(inputGpioState = InputGpioState.WAITING) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = gpioManager.waitForGpioState(true)
            if (result == 0) {
                _uiState.update { it.copy(inputGpioState = InputGpioState.LATCH_HIT) }
            } else {
                _uiState.update { it.copy(inputGpioState = InputGpioState.NOT_CHECKING) }
            }
        }
    }

    fun triggerCancelWaitForGpio() {
        Log.d("CommandViewModel", "triggerCancelWaitForGpio")
        gpioManager.cancelWaitForGpio()
        _uiState.update { it.copy(inputGpioState = InputGpioState.NOT_CHECKING) }
    }
}
