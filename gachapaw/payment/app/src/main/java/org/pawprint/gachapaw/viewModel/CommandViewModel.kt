package org.pawprint.gachapaw.viewModel

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.pawprint.gachapaw.model.ServiceState
import org.pawprint.gachapaw.model.InputGpioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pawprint.gachapaw.model.TransactionState
import org.pawprint.gachapaw.service.GpioRepository
import org.pawprint.gachapaw.service.GpioService
import org.pawprint.gachapaw.service.GpioServiceState
import org.pawprint.gachapaw.service.SquarePaymentRepository
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class CommandViewModel(
    private val gpioRepository: GpioRepository,
    private val squarePaymentRepository: SquarePaymentRepository
) : ViewModel() {
    companion object {
        const val RETURN_TIMEOUT = 3_200L
        const val NOTE_DEVELOPMENT = "Gashapaw TEST transaction"
        const val NOTE_PAWB_TRANSACTION = "Pawprint Proto PAW PCB"
    }

    private val _uiState = MutableStateFlow(ServiceState())
    val uiState: StateFlow<ServiceState> = _uiState.asStateFlow()

    val isConnected: Flow<Boolean> = gpioRepository.service.map { service ->
        service is GpioServiceState.Connected
    }

    fun handlePaymentResult(result: ActivityResult) {
        _uiState.update { it.copy(isSquareReaderActive = false) }
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val data = result.data ?: return
                val success = squarePaymentRepository.parseChargeSuccess(data)
                Log.i("Gashapaw", "payment succeeded: ${success.clientTransactionId}")
            }
            Activity.RESULT_CANCELED -> {
                Log.i("Gashapaw", "payment cancelled")
            }
            else -> {
                val data = result.data ?: return
                val failure = squarePaymentRepository.parseChargeError(data)
                Log.i("Gashapaw", "payment failed: ${failure.debugDescription}")
            }
        }
    }

    private suspend fun withConnectedServiceSuspend(block: suspend (GpioService) -> Unit) {
        (gpioRepository.service.value as? GpioServiceState.Connected)?.service?.let { block(it) }
    }

    private fun withConnectedService(block: (GpioService) -> Unit) {
        (gpioRepository.service.value as? GpioServiceState.Connected)?.service?.let(block)
    }

    fun enablePrizeDispenser() {
        withConnectedService { service ->
            service.setGpioState(5, false)
        }
    }

    fun disablePrizeDispenser() {
        withConnectedService { service ->
            service.setGpioState(5, true)
        }
    }

    fun getRandomOpaqueColor(): Color {
        return Color(
            red = Random.nextInt(256),
            green = Random.nextInt(256),
            blue = Random.nextInt(256),
            alpha = 255
        )
    }

    fun setNeoPixelColor(color: Color) {
        withConnectedService { service ->
            service.setNeopixelColor(color)
        }
    }

    fun setRandomNeopixelColor() {
        setNeoPixelColor(getRandomOpaqueColor())
    }

    fun launchSquareReaderActivity(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>, cost: CharSequence) {
        _uiState.update { it.copy(isSquareReaderActive = true) }
        val costFloat = cost.toString().toFloatOrNull() ?: 0f
        if (costFloat !in 1.0f..25.0f) {
            Log.d("CommandViewModel", "triggerEnableCardReader unexpected cost: $cost")
            _uiState.update { it.copy(isSquareReaderActive = false) }
            return
        }
        val costInt = (costFloat * 100).roundToInt()
        Log.d("CommandViewModel", "triggerEnableCardReader for $costInt cents")
        
        val note = when(_uiState.value.transactionState) {
            TransactionState.MAINTENANCE -> NOTE_DEVELOPMENT
            else -> NOTE_PAWB_TRANSACTION
        }

        val intent = squarePaymentRepository.createChargeIntent(
            amountCents = costInt,
            note = note,
            returnTimeout = RETURN_TIMEOUT
        )
        launcher.launch(intent)

        viewModelScope.launch {
            delay((RETURN_TIMEOUT + 1000).milliseconds)
            if (_uiState.value.isSquareReaderActive) {
                Log.d("CommandViewModel", "Square Reader timed out, forcing return to main activity")
                resetPaymentFlow()
            }
        }
    }

    fun enterProdMode() {
        resetBoardState()
        _uiState.update { it.copy(transactionState = TransactionState.INITIALIZING) }
    }

    fun enterDebugMode() {
        resetBoardState()
        _uiState.update { it.copy(transactionState = TransactionState.MAINTENANCE) }
    }

    fun resetPaymentFlow() {
        resetBoardState()
        _uiState.update { it.copy(transactionState = TransactionState.INITIALIZING, isSquareReaderActive = false)}
        returnToMain()
    }

    private fun returnToMain() {
        withConnectedService { service ->
            service.bringToFront()
        }
    }

    fun resetBoardState() {
        cancelWaitForButtonPress()
        disablePrizeDispenser()
        setNeoPixelColor(Color.Black)
    }

    fun waitForButtonPress() {
        Log.d("CommandViewModel", "triggerWaitForGpio")
        if (_uiState.value.inputGpioState == InputGpioState.WAITING) {
            Log.d("CommandViewModel", "triggerEnableCardReader: waiting")
            return
        }
        _uiState.update { it.copy(inputGpioState = InputGpioState.WAITING) }
        viewModelScope.launch(Dispatchers.IO) {
            withConnectedServiceSuspend { service ->
                val result = service.waitForGpioState(true)
                if (result == 0) {
                    _uiState.update { it.copy(inputGpioState = InputGpioState.LATCH_HIT) }
                } else {
                    _uiState.update { it.copy(inputGpioState = InputGpioState.NOT_CHECKING) }
                }
            }
        }
    }

    fun cancelWaitForButtonPress() {
        Log.d("CommandViewModel", "triggerCancelWaitForGpio")
        withConnectedService { service ->
            service.cancelWaitForGpio()
        }
        _uiState.update { it.copy(inputGpioState = InputGpioState.NOT_CHECKING) }
    }
}
