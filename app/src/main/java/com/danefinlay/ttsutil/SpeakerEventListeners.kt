package com.danefinlay.ttsutil

import android.app.Notification
import android.content.Context
import android.speech.tts.UtteranceProgressListener
import org.jetbrains.anko.*


abstract class SpeakerEventListener(private val ctx: Context):
        UtteranceProgressListener() {

    protected abstract val notificationId: Int
    protected abstract val notification: Notification

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        // Display a toast message.
        ctx.runOnUiThread {
            longToast(R.string.text_synthesis_error_msg)
        }
    }

    protected fun startNotification() {
        ctx.notificationManager.notify(notificationId, notification)
    }

    protected fun cancelNotification() {
        ctx.notificationManager.cancel(notificationId)
    }
}


class SpeakingEventListener(private val app: ApplicationEx):
        SpeakerEventListener(app) {

    override val notificationId = SPEAKING_NOTIFICATION_ID
    override val notification =
            buildSpeakerNotification(app, notificationId)

    var finalUtteranceId: String? = null
    private var audioFocusRequestGranted = false

    override fun onStart(utteranceId: String?) {
        // Only request audio focus if we don't already have it.
        if (!audioFocusRequestGranted) {
            audioFocusRequestGranted = app.requestAudioFocus()
            startNotification()
        }
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        super.onError(utteranceId, errorCode)
        cancelNotification()
    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        if (interrupted) {
            app.releaseAudioFocus()
            cancelNotification()
        }
    }

    override fun onDone(utteranceId: String?) {
        if (utteranceId == finalUtteranceId || finalUtteranceId == null) {
            app.releaseAudioFocus()
            cancelNotification()
        }
    }
}

class SynthesisEventListener(private val ctx: Context,
                             private val filename: String):
        SpeakerEventListener(ctx) {

    override val notificationId = SYNTHESIS_NOTIFICATION_ID
    override val notification =
            buildSpeakerNotification(ctx, notificationId)
    private var notificationStarted = false

    override fun onStart(utteranceId: String?) {
        if (!notificationStarted) {
            startNotification()
            notificationStarted = true
        }
    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        if (interrupted) {
            cancelNotification()
            ctx.runOnUiThread {
                toast(getString(R.string.file_synthesis_interrupted_msg))
            }
        }
    }

    override fun onDone(utteranceId: String?) {
        ctx.runOnUiThread {
            AlertDialogBuilder(ctx).apply {
                title(R.string.write_files_fragment_label)
                val msgPart1 = ctx.getString(
                        R.string.write_to_file_alert_message_success)
                val fullMsg = "$msgPart1 \"$filename\""
                message(fullMsg)
                positiveButton(R.string.alert_positive_message) {}
                show()
            }
        }
        cancelNotification()
    }
}
