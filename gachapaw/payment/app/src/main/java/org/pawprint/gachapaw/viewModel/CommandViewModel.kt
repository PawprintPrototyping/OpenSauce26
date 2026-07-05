package org.pawprint.gachapaw.viewModel

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.pawprint.gachapaw.model.ServiceState
import org.pawprint.gachapaw.model.InputGpioState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.pawprint.gachapaw.MainActivity
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
        const val LOG_TAG = "CommandViewModel"
        const val RETURN_TIMEOUT = 3_200L
        const val NOTE_DEVELOPMENT = "Gashapaw TEST transaction"
        const val NOTE_PAWB_TRANSACTION = "Pawprint Proto PAW PCB"
    }

    private val _serviceState = MutableStateFlow(ServiceState())
    val uiState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    val isConnected: Flow<Boolean> = gpioRepository.service.map { service ->
        service is GpioServiceState.Connected
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<TransactionState> = gpioRepository.service.flatMapLatest { service ->
        if (service is GpioServiceState.Disconnected) {
            flowOf(TransactionState.DISCONNECTED)
        } else {
            (service as GpioServiceState.Connected).service.state
        }
    }

    private val _activityLaunchEvents: MutableSharedFlow<Intent> = MutableSharedFlow()
    val activityLaunchEvents: SharedFlow<Intent> = _activityLaunchEvents.asSharedFlow()
    fun handlePaymentResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val data = result.data ?: return
                val success = squarePaymentRepository.parseChargeSuccess(data)
                Log.i(LOG_TAG, "payment succeeded: ${success.clientTransactionId}")
            }
            Activity.RESULT_CANCELED -> {
                Log.i(LOG_TAG, "payment cancelled")
            }
            else -> {
                val data = result.data ?: return
                val failure = squarePaymentRepository.parseChargeError(data)
                Log.i(LOG_TAG, "payment failed: ${failure.debugDescription}")
            }
        }
    }

    suspend fun launchSquareReaderActivity(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>, cost: CharSequence) {
        val costFloat = cost.toString().toFloatOrNull() ?: 0f
        if (costFloat !in 1.0f..25.0f) {
            Log.d("CommandViewModel", "triggerEnableCardReader unexpected cost: $cost")
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
        delay((RETURN_TIMEOUT + 1000).milliseconds)
        if (_uiState.value.isSquareReaderActive) {
            Log.d("CommandViewModel", "Square Reader timed out, forcing return to main activity")
            resetPaymentFlow()
        }
    }

    fun enterProdMode() {
        withConnectedService {
            it.exitProdMode()
        }
    }

    fun enterDebugMode() {
        withConnectedService {
            it.enterProdMode()
        }
    }

    private fun withConnectedService(block: (GpioService) -> Unit) {
        (gpioRepository.service.value as? GpioServiceState.Connected)?.service?.let(block)
    }


    private suspend fun returnToMain(intent: Intent) {
        _activityLaunchEvents.emit(intent)
    }
}
