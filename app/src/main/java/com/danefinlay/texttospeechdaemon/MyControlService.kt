package com.danefinlay.texttospeechdaemon

import android.app.IntentService
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.support.v4.app.NotificationCompat

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 *
 *
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
class MyControlService : IntentService("MyControlService") {
    override fun onHandleIntent(intent: Intent?) {
        if ( intent == null) return
        val action = intent.action
        when ( action ) {
            STOP_APP -> {
                stopService(Intent(this, MyService::class.java))
                stopSelf()
            }
        }
    }

    companion object {
        const val STOP_APP = "com.danefinlay.texttospeechdaemon.STOP_APP"
    }
}
