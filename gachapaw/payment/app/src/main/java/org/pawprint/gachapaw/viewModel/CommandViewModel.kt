package org.pawprint.gachapaw.viewModel

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.pawprint.gachapaw.model.LogSeverity
import org.pawprint.gachapaw.model.ServiceState
import org.pawprint.gachapaw.model.TransactionState
import org.pawprint.gachapaw.service.GpioRepository
import org.pawprint.gachapaw.service.GpioService
import org.pawprint.gachapaw.service.GpioServiceState
import org.pawprint.gachapaw.service.LoggingRepository
import org.pawprint.gachapaw.service.SquarePaymentRepository
import kotlin.math.roundToInt

class CommandViewModel(
    private val gpioRepository: GpioRepository,
    private val squarePaymentRepository: SquarePaymentRepository,
    private val loggingRepository: LoggingRepository
) : ViewModel() {
    companion object {
        const val LOG_TAG = "CommandViewModel"
        const val RETURN_TIMEOUT = 3_200L
        const val NOTE_DEVELOPMENT = "Gashapaw TEST transaction"
        const val NOTE_PAWB_TRANSACTION = "Pawprint Proto PAW PCB"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _uiState: StateFlow<ServiceState> = gpioRepository.service
        .flatMapLatest { serviceState ->
            when (serviceState) {
                is GpioServiceState.Connected -> serviceState.service.state
                is GpioServiceState.Disconnected -> flowOf(TransactionState.DISCONNECTED)
            }
        }
        .map { txState ->
            // For now, we're only mapping the transaction state.
            // You can later combine this with other flows for a full ServiceState.
            ServiceState(transactionState = txState)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServiceState(TransactionState.DISCONNECTED)
        )

    val uiState: StateFlow<ServiceState> = _uiState

    val isConnected: Flow<Boolean> = gpioRepository.service.map { serviceState ->
        serviceState is GpioServiceState.Connected
    }

    private val _activityLaunchEvents: MutableSharedFlow<Intent> = MutableSharedFlow()
    val activityLaunchEvents: SharedFlow<Intent> = _activityLaunchEvents.asSharedFlow()
    fun handlePaymentResult(result: ActivityResult) {
        loggingRepository.addLog("CommandViewModel: Handling payment result", LogSeverity.DEBUG)
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val data = result.data ?: return
                val success = squarePaymentRepository.parseChargeSuccess(data)
                Log.i(LOG_TAG, "payment succeeded: ${success.clientTransactionId}")
                loggingRepository.addLog("Payment: Success (TxID: ${success.clientTransactionId})", LogSeverity.INFO)
            }
            Activity.RESULT_CANCELED -> {
                Log.i(LOG_TAG, "payment cancelled")
                loggingRepository.addLog("Payment: Cancelled by user", LogSeverity.WARNING)
            }
            else -> {
                val data = result.data ?: return
                val failure = squarePaymentRepository.parseChargeError(data)
                Log.i(LOG_TAG, "payment failed: ${failure.debugDescription}")
                loggingRepository.addLog("Payment: Failed (${failure.debugDescription})", LogSeverity.ERROR)
            }
        }
    }

    fun launchSquareReaderActivity(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>, cost: CharSequence) {
        val costFloat = cost.toString().toFloatOrNull() ?: 0f
        if (costFloat !in 1.0f..25.0f) {
            Log.d("CommandViewModel", "triggerEnableCardReader unexpected cost: $cost")
            loggingRepository.addLog("Command: Invalid payment amount: $cost", LogSeverity.WARNING)
            return
        }
        val costInt = (costFloat * 100).roundToInt()
        Log.d("CommandViewModel", "triggerEnableCardReader for $costInt cents")
        loggingRepository.addLog("Command: Launching Square Reader for $cost", LogSeverity.INFO)

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
        loggingRepository.addLog("Command: Requesting Prod Mode", LogSeverity.INFO)
        withConnectedService {
            it.enterProdMode()
        }
    }

    fun enterDebugMode() {
        loggingRepository.addLog("Command: Requesting Debug Mode", LogSeverity.INFO)
        withConnectedService {
            it.exitProdMode()
        }
    }

    fun unlockPrizeDispenser() {
        loggingRepository.addLog("Command: Unlocking Prize Dispenser", LogSeverity.INFO)
        withConnectedService {
            it.setGpioState(5, false)
        }
    }

    fun lockPrizeDispenser() {
        loggingRepository.addLog("Command: Locking Prize Dispenser", LogSeverity.INFO)
        withConnectedService {
            it.setGpioState(5, true)
        }
    }

    fun setRandomNeopixelColor() {
        loggingRepository.addLog("Command: Cycling Neopixel Color", LogSeverity.INFO)
        withConnectedService {
            it.setNeopixelColor(org.pawprint.gachapaw.ui.theme.getRandomOpaqueColor())
        }
    }

    fun waitForButtonPress() {
        loggingRepository.addLog("Command: Setting GPIO Latch", LogSeverity.INFO)
    }

    fun cancelWaitForButtonPress() {
        loggingRepository.addLog("Command: Cancelling GPIO Latch", LogSeverity.INFO)
    }

    fun resetPaymentFlow() {
        loggingRepository.addLog("Command: Resetting Payment Flow", LogSeverity.WARNING)
    }

    private fun withConnectedService(block: (GpioService) -> Unit) {
        (gpioRepository.service.value as? GpioServiceState.Connected)?.service?.let(block)
    }
}
