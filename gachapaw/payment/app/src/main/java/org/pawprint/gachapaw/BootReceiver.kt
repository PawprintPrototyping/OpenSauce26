package org.pawprint.gachapaw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Target your MainActivity explicitly
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                // Crucial flag: allows launching an activity outside an existing task stack
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}