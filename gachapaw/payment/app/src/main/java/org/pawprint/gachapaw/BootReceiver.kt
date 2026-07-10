package org.pawprint.gachapaw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.pawprint.gachapaw.model.LogSeverity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val loggingRepository = (context.applicationContext as PawApplication).loggingRepository
        loggingRepository.addLog("BootReceiver: Received intent ${intent.action}", LogSeverity.INFO)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            loggingRepository.addLog("BootReceiver: Launching MainActivity", LogSeverity.INFO)
            context.startActivity(launchIntent)
        }
    }
}
