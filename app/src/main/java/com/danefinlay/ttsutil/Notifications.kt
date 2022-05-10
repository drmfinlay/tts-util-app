/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
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
import android.support.v4.app.NotificationCompat
import com.danefinlay.ttsutil.ui.MainActivity
import org.jetbrains.anko.notificationManager
import java.lang.RuntimeException

val notificationTasks = listOf(TASK_ID_READ_TEXT, TASK_ID_WRITE_FILE,
        TASK_ID_PROCESS_FILE)

fun getNotificationTitle(ctx: Context, taskId: Int): String {
    val stringId = when (taskId) {
        TASK_ID_READ_TEXT -> R.string.speaking_notification_title
        TASK_ID_WRITE_FILE -> R.string.synthesis_notification_title
        TASK_ID_PROCESS_FILE -> R.string.post_synthesis_notification_title
        else -> throw RuntimeException("Invalid task ID $taskId")
    }
    return ctx.getString(stringId)
}

fun getNotificationText(ctx: Context, taskId: Int, remainingTasks: Int): String {
    val textId = when (taskId) {
        TASK_ID_READ_TEXT, TASK_ID_WRITE_FILE, TASK_ID_PROCESS_FILE ->
            R.string.progress_notification_text
        else -> throw RuntimeException("Invalid task ID $taskId")
    }
    return ctx.getString(textId, remainingTasks,
            ctx.resources.getQuantityString(R.plurals.tasks, remainingTasks))
}

fun getNotificationBuilder(ctx: Context, taskId: Int): NotificationCompat.Builder {
    // Create an Intent and PendingIntent for when the user clicks on the
    // notification. This should just open/re-open MainActivity.
    val onClickIntent = Intent(ctx, MainActivity::class.java).apply {
        putExtra("taskId", taskId)
        addFlags(START_ACTIVITY_FLAGS)
    }
    val contentPendingIntent = PendingIntent.getActivity(
            ctx, 0, onClickIntent, 0)

    // Just stop speaking for the delete intent (notification dismissal).
    val onDeleteIntent = Intent(ctx, TTSIntentService::class.java).apply {
        action = ACTION_STOP_SPEAKING
        putExtra("taskId", taskId)
    }
    val onDeletePendingIntent = PendingIntent.getService(ctx,
            0, onDeleteIntent, 0)

    // Set up the notification
    // Use the correct notification builder method
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

        // Set pending intents.
        setContentIntent(contentPendingIntent)
        setDeleteIntent(onDeletePendingIntent)

        // Make it so the notification stays around after it's clicked on.
        setAutoCancel(false)

        // Add a notification action for stop speaking.
        // Re-use the delete intent.
        addAction(android.R.drawable.ic_delete,
                ctx.getString(R.string.stop_button),
                onDeletePendingIntent)
    }
}
