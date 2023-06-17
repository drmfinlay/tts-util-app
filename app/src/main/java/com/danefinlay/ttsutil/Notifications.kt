/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
 *
 * Copyright (C) 2019 Dane Finlay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.danefinlay.ttsutil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.danefinlay.ttsutil.ui.MainActivity
import org.jetbrains.anko.notificationManager


fun getNotificationBuilder(ctx: Context): NotificationCompat.Builder {
    // Define PendingIntent flags.
    val pendingIntentFlags: Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            PendingIntent.FLAG_IMMUTABLE
        }
        else -> 0
    }

    // Create an Intent and PendingIntent for when the user clicks on the
    // notification. This should just open/re-open MainActivity.
    val onClickIntent = Intent(ctx, MainActivity::class.java).apply {
        addFlags(START_ACTIVITY_FLAGS)
    }
    val contentPendingIntent = PendingIntent.getActivity(
            ctx, 0, onClickIntent, pendingIntentFlags
    )

    // Create an Intent and PendingIntent to be used for the 'Stop' button.
    val onStopIntent = Intent(ctx, TTSIntentService::class.java).apply {
        action = ACTION_STOP_TASK
    }
    val onStopPendingIntent = PendingIntent.getService(
            ctx, 0, onStopIntent, pendingIntentFlags
    )


    // Set up the notification, using the correct notification builder method.
    val notificationBuilder = when {
        Build.VERSION.SDK_INT >= 26 -> {
            val id = ctx.getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            ctx.notificationManager.createNotificationChannel(
                    NotificationChannel(id, id, importance)
            )
            NotificationCompat.Builder(ctx, id)
        }
        else -> {
            @Suppress("DEPRECATION")
            NotificationCompat.Builder(ctx)
        }
    }
    return notificationBuilder.apply {
        // Set the icon for the notification
        setSmallIcon(android.R.drawable.ic_btn_speak_now)

        // Set the ticker name.
        setTicker(ctx.getString(R.string.app_name))

        // Set the pending intent for clicking on the notification.
        setContentIntent(contentPendingIntent)

        // Make it so the notification stays around after it's clicked on.
        setAutoCancel(false)

        // Set the notification to ongoing.  The user should have to press 'Stop'.
        setOngoing(true)

        // Add a notification action for stop speaking.
        // Re-use the delete intent.
        addAction(
                android.R.drawable.ic_delete,
                ctx.getString(R.string.stop_button),
                onStopPendingIntent
        )
    }
}
