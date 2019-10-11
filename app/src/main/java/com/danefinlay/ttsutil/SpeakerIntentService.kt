package com.danefinlay.ttsutil

import android.app.IntentService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.jetbrains.anko.longToast
import org.jetbrains.anko.runOnUiThread

// IntentService actions.
private const val ACTION_READ_TEXT = "${APP_NAME}.action.READ_TEXT"
private const val ACTION_EDIT_READ_TEXT = "${APP_NAME}.action.EDIT_READ_TEXT"
private const val ACTION_READ_FILE = "${APP_NAME}.action.READ_FILE"
const val ACTION_STOP_SPEAKING = "${APP_NAME}.action.STOP_SPEAKING"
const val ACTION_READ_CLIPBOARD = "${APP_NAME}.action.READ_CLIPBOARD"

// Parameter constants (for Intent extras).
private const val EXTRA_TEXT = "${APP_NAME}.extra.TEXT"
private const val EXTRA_FILE_URI = "${APP_NAME}.extra.FILE_URI"

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
            ACTION_READ_FILE -> {
                val uri = intent.getParcelableExtra<Uri>(EXTRA_FILE_URI)
                handleActionReadFile(uri)
            }
            ACTION_READ_CLIPBOARD -> handleActionReadClipboard()
            ACTION_STOP_SPEAKING -> handleActionStopSpeaking()
        }
    }

    /**
     * Handle action ReadText in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionReadText(text: String) {
        // Display a message if 'text' is blank/empty.
        if (text.isBlank()) {
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
        // Get the primary ClipData object from the manager.
        // Return early if there is no clipboard data.
        val clipboardManager = (getSystemService(Context.CLIPBOARD_SERVICE) as
                ClipboardManager)
        val clipData = clipboardManager.primaryClip
        if (clipData == null || !clipboardManager.hasPrimaryClip()) {
            runOnUiThread {
                longToast(R.string.cannot_speak_empty_text_msg)
            }
            return
        }

        // Find the first clipboard Item that coerces successfully to text.
        var text = ""
        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i)
            val itemText = item?.text
            if (!itemText.isNullOrBlank()) {
                text = itemText.toString()
                break
            }
        }

        // Read text.
        handleActionReadText(text)
    }

    /**
     * Handle action ReadFile in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionReadFile(text: Uri) {
        TODO("Handle action ReadFile")
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
        fun startActionReadText(ctx: Context, text: String) {
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
         * Starts this service to perform action ReadFile. If the service is already
         * performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionReadFile(ctx: Context, fileUri: Uri) {
            val intent = Intent(ctx, SpeakerIntentService::class.java).apply {
                action = ACTION_READ_FILE
                putExtra(EXTRA_FILE_URI, fileUri)
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
