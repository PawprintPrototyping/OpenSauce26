package com.pawprint.gachapaw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class GpioService : LifecycleService() {

    companion object {
        private const val TAG = "GpioService"
        private const val NOTIFICATION_ID = 1;
        private const val CHANNEL_ID = "gpio_service_channel"
    }
    private val gpioManager = GpioManager()

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
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