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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pawprint.gachapaw.PawApplication
import org.pawprint.gachapaw.model.LogSeverity
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
    private val loggingRepository by lazy { (applicationContext as PawApplication).loggingRepository }
    private lateinit var stateMachine: StateMachine

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        loggingRepository.addLog("GpioService: onCreate", LogSeverity.DEBUG)
        createNotificationChannel()
        setNeopixelColor(Color.Red)
        displayOnLcd("Disconnected...")
        lifecycleScope.launch {
            Log.i(TAG, "onCreate: Initializing")
            loggingRepository.addLog("GpioService: Initializing hardware", LogSeverity.INFO)
            initialize()
            gpioRepository.onServiceStarted(this@GpioService)
            loggingRepository.addLog("GpioService: Service started and connected to repository", LogSeverity.INFO)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        loggingRepository.addLog("GpioService: onDestroy", LogSeverity.DEBUG)
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
                loggingRepository.addLog("SM: Transition triggered: ${it.transition}", LogSeverity.DEBUG)
            }
            onTransitionComplete { activeStates, _ ->
                Log.d(TAG, "Active state: $activeStates")
                loggingRepository.addLog("SM: Active state: $activeStates", LogSeverity.INFO)
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
                    loggingRepository.addLog("SM: State -> Initialize", LogSeverity.DEBUG)
                }
                autoTransition {
                    targetState = States.Advertise
                }
            }
            addState(States.Advertise) {
                onEntry {
                    _state.update { TransactionState.WAITING_FOR_BUTTON_PRESS }
                    loggingRepository.addLog("SM: State -> Advertise", LogSeverity.DEBUG)
                    coroutineScope {
                        launch { advertise() }
                        val result = withContext(Dispatchers.IO) {
                            gpioManager.waitForGpioState(expectedState = true)
                        }
                        if (result == 0) {
                            loggingRepository.addLog("Hardware: Button pressed!", LogSeverity.INFO)
                            machine.processEvent(ButtonPressed)
                        }
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
                    loggingRepository.addLog("SM: State -> PaymentRequest", LogSeverity.DEBUG)
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
                    loggingRepository.addLog("SM: State -> PaymentFailed", LogSeverity.WARNING)
                    val triggerEvent = it.event
                    if (triggerEvent is PaymentFinished.Failed) {
                        val reason = triggerEvent.failureReason
                        loggingRepository.addLog("Payment: Failed with reason: $reason", LogSeverity.ERROR)
                        displayOnLcd("Oops Error: $reason")
                    }
                }
            }
            addState(States.DispenseCookie) {
                onEntry {
                    _state.update { TransactionState.TRANSACTION_SUCCESS }
                    setNeopixelColor(Color.Blue)
                    loggingRepository.addLog("SM: State -> DispenseCookie", LogSeverity.INFO)
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
                    loggingRepository.addLog("SM: State -> Maintenance", LogSeverity.INFO)
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

    fun setGpioState(pin: Int, state: Boolean, skipLog: Boolean = false) {
        gpioManager.setGpioState(pin, state)
        if (!skipLog) {
            loggingRepository.addLog("Hardware: GPIO pin $pin set to $state", LogSeverity.DEBUG)
        }
    }

    fun setNeopixelColor(color: Color, skipLog: Boolean = false) {
        gpioManager.setNeopixelColor(color)
        if (!skipLog) {
            loggingRepository.addLog("Hardware: Neopixel color set to $color", LogSeverity.DEBUG)
        }
    }

    suspend fun advertise() {
        while (currentCoroutineContext().isActive) {
            val advert = adverts.random()
            displayOnLcd(advert)
            showRainbowEffect(cycleDuration = 1.seconds, totalDuration = 5.seconds)
        }
        setNeopixelColor(Color.Black)
    }

    suspend fun showRainbowEffect(cycleDuration: Duration, totalDuration: Duration) {
        val startTime = System.currentTimeMillis()
        val totalMs = totalDuration.inWholeMilliseconds
        val cycleMs = cycleDuration.inWholeMilliseconds

        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val elapsedTotal = now - startTime

            // Exit the effect after the total duration has passed
            if (elapsedTotal >= totalMs) break

            // Use modulo to loop the rainbow progress (0.0 to 1.0) within the cycle duration
            val elapsedCycle = elapsedTotal % cycleMs
            val progress = elapsedCycle.toFloat() / cycleMs.toFloat()

            val hue = progress * 360f
            val color = Color.hsv(hue, 1f, 1f)
            setNeopixelColor(color, skipLog = true)

            // Update at roughly 30 FPS for smoothness
            delay(33.milliseconds)
        }
    }

    fun enterProdMode() {
        loggingRepository.addLog("Service: Exiting Maintenance (entering Prod)", LogSeverity.INFO)
        stateMachine.processEventByLaunch(ExitMaintenance)
    }

    fun exitProdMode() {
        loggingRepository.addLog("Service: Entering Maintenance (exiting Prod)", LogSeverity.INFO)
        stateMachine.processEventByLaunch(EnterMaintenance)
    }

    private fun cancelWaitForGpio() {
        gpioManager.cancelWaitForGpio()
        loggingRepository.addLog("Hardware: Cancelled wait for GPIO", LogSeverity.DEBUG)
    }

    private fun displayOnLcd(text: String) {
        gpioManager.displayOnLcd(text)
        loggingRepository.addLog("LCD: Displaying text: $text", LogSeverity.INFO)
    }

    private fun unlockPrizeDispenser() {
        loggingRepository.addLog("Hardware: Unlocking prize dispenser", LogSeverity.INFO)
        setGpioState(5, false)
    }

    private fun lockPrizeDispenser() {
        loggingRepository.addLog("Hardware: Locking prize dispenser", LogSeverity.INFO)
        setGpioState(5, true)
    }

    private fun resetHardwareState() {
        cancelWaitForGpio()
        lockPrizeDispenser()
        setNeopixelColor(Color.Black)
        loggingRepository.addLog("Hardware: Resetting hardware state", LogSeverity.DEBUG)
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
