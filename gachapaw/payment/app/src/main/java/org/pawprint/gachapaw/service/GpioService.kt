package org.pawprint.gachapaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pawprint.gachapaw.PawApplication
import org.pawprint.gachapaw.model.TransactionState
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.DefaultState
import ru.nsk.kstatemachine.state.addInitialState
import ru.nsk.kstatemachine.state.addState
import ru.nsk.kstatemachine.state.autoTransition
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStateMachine
import ru.nsk.kstatemachine.statemachine.onTransitionComplete
import ru.nsk.kstatemachine.statemachine.onTransitionTriggered
import ru.nsk.kstatemachine.statemachine.processEventByLaunch
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.targetState
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GpioService : LifecycleService() {

    companion object {
        private const val TAG = "GpioService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "gpio_service_channel"

        object ButtonPressed: Event
        sealed class PaymentFinished : Event {
            object Success: PaymentFinished()
            data class Failed(val failureReason: String) : PaymentFinished()
        }
        object EnterMaintenance: Event
        object ExitMaintenance: Event
        sealed class States : DefaultState() {
            object Initialize : States()
            object Advertise : States()
            object PaymentRequest : States()
            object PaymentFailed : States()
            object DispenseCookie : States()
            object Maintenance : States()
        }

        private val adverts = listOf(
            "Take a FUN memory game home TODAY!",
            "Stave off the existential dread\nBuy a PAW PCB",
            "Roses are red, violets are blue,\nThese PAWs will always be here for you",
            "It's raining PAWs, my dream...",
            "8008135",
        )
    }

    private val _state: MutableStateFlow<TransactionState> =
        MutableStateFlow(TransactionState.INITIALIZING)
    val state = _state.asStateFlow()
    private val gpioManager = GpioManager()
    private val gpioRepository by lazy { (applicationContext as PawApplication).gpioRepository }
    private lateinit var stateMachine: StateMachine

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        createNotificationChannel()
        setNeopixelColor(Color.Red)
        displayOnLcd("Disconnected...")
        lifecycleScope.launch {
            Log.i(TAG, "onCreate: Initializing")
            initialize()
            gpioRepository.onServiceStarted(this@GpioService)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        gpioRepository.onServiceStopped()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        super.onStartCommand(intent, flags, startId)
        startForegroundService()
        return START_STICKY
    }

    suspend fun initialize() {
        stateMachine = createStateMachine(scope = lifecycleScope, "GashapawSM") {
            onTransitionTriggered {
                Log.d(TAG, "transition triggered: ${it.transition}")
            }
            onTransitionComplete { activeStates, _ ->
                Log.d(TAG, "Active state: $activeStates")
            }
            // Base transition always moves to maintenance if a state doesn't override.
            transition<EnterMaintenance> {
                targetState = States.Maintenance
            }
            addInitialState(States.Initialize) {
                onEntry {
                    resetHardwareState()
                    setNeopixelColor(Color.Blue)
                    _state.update { TransactionState.INITIALIZING }
                }
                autoTransition {
                    targetState = States.Advertise
                }
            }
            addState(States.Advertise) {
                onEntry {
                    _state.update { TransactionState.WAITING_FOR_BUTTON_PRESS }
                    coroutineScope {
                        launch { advertise() }
                        val result = withContext(Dispatchers.IO) {
                            gpioManager.waitForGpioState(expectedState = true)
                        }
                        if (result == 0) machine.processEvent(ButtonPressed)
                    }
                }
                transition<ButtonPressed> {
                    targetState = States.PaymentRequest
                }
            }
            addState(States.PaymentRequest) {
                onEntry {
                    _state.update { TransactionState.WAITING_FOR_TRANSACTION_RESULT }
                    setNeopixelColor(Color.Green)
                    displayOnLcd("TOTAL: $15, Tap or Insert to Pay Om nom nom\n" +
                            "Or press the BUTTON to cancel")
                }
                transition<ButtonPressed> {
                    targetState = States.Advertise
                }
                // Relying on the UI layer to handle this request
                transitionConditionally<PaymentFinished> {
                    direction = {
                        when(event) {
                            is PaymentFinished.Failed -> targetState(States.PaymentFailed)
                            PaymentFinished.Success -> targetState(States.DispenseCookie)
                        }
                    }
                }
            }
            addState(States.PaymentFailed) {
                onEntry {
                    _state.update { TransactionState.TRANSACTION_FAIL }
                    setNeopixelColor(Color.Yellow)
                    val triggerEvent = it.event
                    if (triggerEvent is PaymentFinished.Failed) {
                        val reason = triggerEvent.failureReason
                        displayOnLcd("Oops Error: $reason")
                    }
                }
            }
            addState(States.DispenseCookie) {
                onEntry {
                    _state.update { TransactionState.TRANSACTION_SUCCESS }
                    setNeopixelColor(Color.Blue)
                    unlockPrizeDispenser()
                    displayOnLcd("Turn handle CLOCKWISE to receive SNACK")
                    delay(5.seconds)
                    lockPrizeDispenser()
                }
                // Do not disrupt this process by allowing entrance to maintenance
                transitionConditionally<EnterMaintenance> {
                    direction = {
                        noTransition()
                    }
                }
            }
            addState(States.Maintenance) {
                onEntry {
                    _state.update { TransactionState.MAINTENANCE }
                    resetHardwareState()
                    setNeopixelColor(Color.Magenta)
                }
                onExit {
                    resetHardwareState()
                }
                transition<ExitMaintenance> {
                    targetState = States.Advertise
                }
            }
        }
        stateMachine.start()
    }

    fun setGpioState(pin: Int, state: Boolean) {
        gpioManager.setGpioState(pin, state)
    }

    fun setNeopixelColor(color: Color) {
        gpioManager.setNeopixelColor(color)
    }

    suspend fun advertise() {
        while (currentCoroutineContext().isActive) {
            displayOnLcd(adverts.random())
            showRainbowEffect(duration = 3.seconds)
        }
        setNeopixelColor(Color.Black)
    }

    suspend fun showRainbowEffect(duration: Duration) {
        val startTime = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            // Normalize time to a 0.0 - 1.0 range
            val elapsed = (System.currentTimeMillis() - startTime) % duration.inWholeMilliseconds
            val progress = elapsed.toFloat() / duration.inWholeMilliseconds.toFloat()
            // 0-360 represents the full rainbow
            val hue = progress * 360f
            val color = Color.hsv(hue, 1f, 1f)
            setNeopixelColor(color)
            // Update at roughly 30 FPS for smoothness
            delay(33.milliseconds)
        }
    }

    fun enterProdMode() {
        stateMachine.processEventByLaunch(ExitMaintenance)
    }

    fun exitProdMode() {
        stateMachine.processEventByLaunch(EnterMaintenance)
    }

    private fun cancelWaitForGpio() {
        gpioManager.cancelWaitForGpio()
    }

    private fun displayOnLcd(text: String) {
        gpioManager.displayOnLcd(text)
    }

    private fun unlockPrizeDispenser() {
        setGpioState(5, false)
    }

    private fun lockPrizeDispenser() {
        setGpioState(5, true)
    }

    private fun resetHardwareState() {
        cancelWaitForGpio()
        lockPrizeDispenser()
        setNeopixelColor(Color.Black)
    }

    private fun getRandomOpaqueColor(): Color {
        return Color(
            red = Random.nextInt(256),
            green = Random.nextInt(256),
            blue = Random.nextInt(256),
            alpha = 255
        )
    }

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPIO Service")
            .setContentText("Monitoring GPIO pins...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
                CHANNEL_ID,
                "GPIO Service",
                NotificationManager.IMPORTANCE_LOW
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}