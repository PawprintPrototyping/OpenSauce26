package org.pawprint.gachapaw.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import org.pawprint.gachapaw.PawApplication
import org.pawprint.gachapaw.MainActivity

class GpioService : LifecycleService() {

    companion object {
        private const val TAG = "GpioService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "gpio_service_channel"
    }

    private val gpioManager = GpioManager()
    private val gpioRepository by lazy { (applicationContext as PawApplication).gpioRepository }

    fun setGpioState(pin: Int, state: Boolean) {
        gpioManager.setGpioState(pin, state)
    }

    fun setNeopixelColor(color: Color) {
        gpioManager.setNeopixelColor(color)
    }

    suspend fun waitForGpioState(expectedState: Boolean): Int {
        return gpioManager.waitForGpioState(expectedState)
    }

    fun cancelWaitForGpio() {
        gpioManager.cancelWaitForGpio()
    }

    fun displayOnLcd(text: String) {
        gpioManager.displayOnLcd(text)
    }

    fun bringToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        createNotificationChannel()
        gpioManager.displayOnLcd("Initializing...")
        gpioManager.setNeopixelColor(Color.Red)
        gpioManager.setGpioState(5, false)
        gpioRepository.onServiceStarted(this)
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

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPIO Service")
            .setContentText("Monitoring GPIO pins...")
            .setSmallIcon(R.drawable.ic_menu_info_details)
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