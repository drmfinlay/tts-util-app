package com.danefinlay.ttsutil

import android.content.Context
import android.speech.tts.UtteranceProgressListener
import org.jetbrains.anko.*


abstract class SpeakerEventListener(private val ctx: Context):
        UtteranceProgressListener() {

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        // Display a toast message.
        ctx.runOnUiThread {
            longToast(R.string.text_synthesis_error_msg)
        }
    }
}


class SpeakingEventListener(private val app: ApplicationEx):
        SpeakerEventListener(app) {

    private val notificationId = SPEAKING_NOTIFICATION_ID
    private val notification =
            buildSpeakerNotification(app, notificationId)

    var finalUtteranceId: String? = null
    private var audioFocusRequestGranted = false

    private fun startNotification() {
        app.notificationManager.notify(notificationId, notification)
    }

    private fun cancelNotification() {
        app.notificationManager.cancel(notificationId)
    }

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

    override fun onStart(utteranceId: String?) {

    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        if (interrupted) {
            ctx.runOnUiThread {
                toast(getString(R.string.file_synthesis_interrupted_msg))
            }
        }
    }

    override fun onDone(utteranceId: String?) {
        ctx.runOnUiThread {
            AlertDialogBuilder(ctx).apply {
                title(R.string.file_activity_description2)
                val msgPart1 = ctx.getString(
                        R.string.write_to_file_alert_message_success)
                val fullMsg = "$msgPart1 \"$filename\""
                message(fullMsg)
                positiveButton(R.string.alert_positive_message) {}
                show()
            }
        }
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        // TODO Display a toast message?
        super.onError(utteranceId, errorCode)
    }
}
