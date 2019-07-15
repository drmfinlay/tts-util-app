package com.danefinlay.texttospeechdaemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver that receives broadcasts at boot time and starts MyService.
 * Created by dane on 05/06/17.
 */
class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            val serviceIntent = Intent(context, MyService::class.java)
            context.startService(serviceIntent)
        }
    }
}
