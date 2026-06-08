package org.pawprint.gachapaw.viewModel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.sdk.pos.ChargeRequest
import com.squareup.sdk.pos.CurrencyCode
import com.squareup.sdk.pos.PosSdk
import org.pawprint.gachapaw.service.GpioManager
import org.pawprint.gachapaw.model.CommandUiState
import org.pawprint.gachapaw.model.InputGpioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pawprint.gachapaw.BuildConfig
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random

class CommandViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val RETURN_TIMEOUT = 3_200L
        const val NOTE_DEVELOPMENT = "Gashapaw TEST transaction"
        const val NOTE_PAWB_TRANSACTION = "Gashapaw PAWB PCB"
    }
    private val _uiState = MutableStateFlow(CommandUiState())
    private val gpioManager = GpioManager()
    val uiState: StateFlow<CommandUiState> = _uiState.asStateFlow()
    val posClient = PosSdk.createClient(
        application.applicationContext,
        BuildConfig.MPSDK_APPLICATION_ID
    )

    fun handlePaymentResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val success = posClient.parseChargeSuccess(result.data!!)
                Log.i("Gashapaw", "payment succeeded: ${success.clientTransactionId}")
            }
            Activity.RESULT_CANCELED -> {
                Log.i("Gashapaw", "payment cancelled")
            }
            else -> {
                val failure = posClient.parseChargeError(result.data!!)
                Log.i("Gashapaw", "payment failed: ${failure.debugDescription}")
            }
        }
    }

    fun triggerEnablePrizeDispenser() {
        gpioManager.setGpioState(5,false)
    }

    fun triggerDisablePrizeDispenser() {
        gpioManager.setGpioState(5,true)
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

    fun triggerEnableSquareReader(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>, cost: CharSequence) {
        var costConversion = cost.toString().toFloat()
        if (costConversion !in 1.0f..25.0f) {
            Log.d("CommandViewModel", "triggerEnableCardReader unexpected cost: $cost")
            return
        }
        costConversion *= 100
        val costInt = costConversion.roundToInt()
        Log.d("CommandViewModel", "triggerEnableCardReader for $costInt cents")
        val intent = posClient.createChargeIntent(
            ChargeRequest
                .Builder(
                    costInt,
                    CurrencyCode.USD
                )
                .autoReturn(RETURN_TIMEOUT, TimeUnit.MILLISECONDS)
                .note(NOTE_DEVELOPMENT)
                .build()
        )
        launcher.launch(intent)
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
