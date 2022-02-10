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

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech.QUEUE_ADD
import com.danefinlay.ttsutil.ui.EditReadActivity
import org.jetbrains.anko.ctx
import org.jetbrains.anko.longToast
import org.jetbrains.anko.notificationManager
import org.jetbrains.anko.runOnUiThread

// IntentService actions.
private const val ACTION_READ_TEXT = "${APP_NAME}.action.READ_TEXT"
private const val ACTION_EDIT_READ_TEXT = "${APP_NAME}.action.EDIT_READ_TEXT"
const val ACTION_STOP_SPEAKING = "${APP_NAME}.action.STOP_SPEAKING"
const val ACTION_READ_CLIPBOARD = "${APP_NAME}.action.READ_CLIPBOARD"
const val ACTION_EDIT_READ_CLIPBOARD = "${APP_NAME}.action.EDIT_READ_CLIPBOARD"

// Parameter constants (for Intent extras).
private const val EXTRA_TEXT = "${APP_NAME}.extra.TEXT"

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
class TTSIntentService : IntentService("TTSIntentService") {

    private val myApplication: ApplicationEx
        get() = application as ApplicationEx

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        // Retrieve text to handle, if any.
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""

        // Handle actions.
        when (intent.action) {
            ACTION_EDIT_READ_TEXT -> handleActionEditReadText(text)
            ACTION_READ_CLIPBOARD -> handleActionReadClipboard()
            ACTION_EDIT_READ_CLIPBOARD -> handleActionEditReadClipboard()
            ACTION_STOP_SPEAKING -> handleActionStopSpeaking(intent)
            ACTION_READ_TEXT -> handleActionReadText(text)
        }
    }

    /**
     * Handle action ReadText in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionReadText(text: String?) {
        // Display a message if 'text' is blank/empty.
        if (text == null || text.isBlank()) {
            runOnUiThread {
                longToast(R.string.cannot_speak_empty_text_msg)
            }
            return
        }

        // Speak *text* and handle the result.
        val result = myApplication.speak(text, QUEUE_ADD)
        myApplication.handleTTSOperationResult(result)
    }

    /**
     * Handle action EditReadText in the provided background thread with the
     * provided parameters.
     */
    private fun handleActionEditReadText(text: String) {
        val intent = Intent(ctx, EditReadActivity::class.java).apply {
            addFlags(START_ACTIVITY_FLAGS)
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(intent)
    }

    /**
     * Handle action ReadClipboard in the provided background thread.
     */
    private fun handleActionReadClipboard() {
        // Show a warning toast message about this action on Android 10.
        if (Build.VERSION.SDK_INT >= 29) {
            runOnUiThread {
                longToast(R.string.cannot_read_clipboard_android_10_msg)
            }
            return
        }

        // Read clipboard text.
        handleActionReadText(ctx.getClipboardText())
    }

    /**
     * Handle action EditReadClipboard in the provided background thread.
     */
    private fun handleActionEditReadClipboard() {
        val text = ctx.getClipboardText() ?: ""
        handleActionEditReadText(text)
    }

    /**
     * Handle action StopSpeaking in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionStopSpeaking(intent: Intent) {
        // Stop speech synthesis.
        myApplication.stopSpeech()

        // Retrieve the ID of the notification to dismiss, if any.
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId == -1) return
        myApplication.notificationManager.cancel(notificationId)
    }

    companion object {
        private inline fun startAction(ctx: Context, actionString: String,
                                       block: Intent.() -> Unit) {
            val intent = Intent(ctx, TTSIntentService::class.java)
            intent.action = actionString
            intent.block()
            ctx.startService(intent)
        }

        private fun startTextAction(ctx: Context, actionString: String,
                                    text: String?) {
            startAction(ctx, actionString) { putExtra(EXTRA_TEXT, text) }
        }

        /**
         * Starts this service to perform action ReadText. If the service is already
         * performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionReadText(ctx: Context, text: String?) =
                startTextAction(ctx, ACTION_READ_TEXT, text)

        /**
         * Starts this service to perform action EditReadText. If the service is
         * already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionEditReadText(ctx: Context, text: String) =
                startTextAction(ctx, ACTION_EDIT_READ_TEXT, text)

        /**
         * Starts this service to perform action ReadClipboard. If the service is
         * already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionReadClipboard(ctx: Context) =
                startAction(ctx, ACTION_READ_CLIPBOARD) {}

        /**
         * Starts this service to perform action EditReadClipboard. If the service
         * is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionEditReadClipboard(ctx: Context) =
                startAction(ctx, ACTION_EDIT_READ_CLIPBOARD) {}

        /**
         * Starts this service to perform action StopSpeaking. If the service is
         * already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionStopSpeaking(ctx: Context, notificationId: Int) =
                startAction(ctx, ACTION_STOP_SPEAKING) {
                    putExtra("notificationId", notificationId)
                }
    }
}
