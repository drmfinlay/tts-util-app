package com.danefinlay.ttsutil

import android.app.IntentService
import android.content.Context
import android.content.Intent
import org.jetbrains.anko.ctx
import org.jetbrains.anko.longToast
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
class SpeakerIntentService : IntentService("SpeakerIntentService") {

    private val myApplication: ApplicationEx
        get() = application as ApplicationEx

    private val speaker: Speaker?
        get() = myApplication.speaker

    override fun onHandleIntent(intent: Intent?) {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        when (intent?.action) {
            ACTION_READ_TEXT -> handleActionReadText(text)
            ACTION_EDIT_READ_TEXT -> handleActionEditReadText(text)
            ACTION_READ_CLIPBOARD -> handleActionReadClipboard()
            ACTION_EDIT_READ_CLIPBOARD -> handleActionEditReadClipboard()
            ACTION_STOP_SPEAKING -> handleActionStopSpeaking()
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

        // Speak the text.
        speaker?.speak(text)
    }

    /**
     * Handle action EditReadText in the provided background thread with the
     * provided parameters.
     */
    private fun handleActionEditReadText(text: String) {
        TODO("Handle action EditReadText")
    }

    /**
     * Handle action ReadClipboard in the provided background thread.
     */
    private fun handleActionReadClipboard() {
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
    private fun handleActionStopSpeaking() {
        speaker?.stopSpeech()
    }

    companion object {
        /**
         * Starts this service to perform action ReadText. If the service is already
         * performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionReadText(ctx: Context, text: String?) {
            val intent = Intent(ctx, SpeakerIntentService::class.java).apply {
                action = ACTION_READ_TEXT
                putExtra(EXTRA_TEXT, text)
            }
            ctx.startService(intent)
        }

        /**
         * Starts this service to perform action EditReadText. If the service is
         * already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionEditReadText(ctx: Context, text: String) {
            val intent = Intent(ctx, SpeakerIntentService::class.java).apply {
                action = ACTION_EDIT_READ_TEXT
                putExtra(EXTRA_TEXT, text)
            }
            ctx.startService(intent)
        }

        /**
         * Starts this service to perform action ReadClipboard. If the service is
         * already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionReadClipboard(ctx: Context) {
            val intent = Intent(ctx, SpeakerIntentService::class.java).apply {
                action = ACTION_READ_CLIPBOARD
            }
            ctx.startService(intent)
        }

        /**
         * Starts this service to perform action EditReadClipboard. If the service
         * is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionEditReadClipboard(ctx: Context) {
            val intent = Intent(ctx, SpeakerIntentService::class.java).apply {
                action = ACTION_EDIT_READ_CLIPBOARD
            }
            ctx.startService(intent)
        }

        /**
         * Starts this service to perform action StopSpeaking. If the service is
         * already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionStopSpeaking(ctx: Context) {
            val intent = Intent(ctx, SpeakerIntentService::class.java).apply {
                action = ACTION_STOP_SPEAKING
            }
            ctx.startService(intent)
        }
    }
}
