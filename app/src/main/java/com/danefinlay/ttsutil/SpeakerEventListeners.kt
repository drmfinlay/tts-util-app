package com.danefinlay.ttsutil

import android.content.Context
import android.speech.tts.UtteranceProgressListener
import org.jetbrains.anko.AlertDialogBuilder
import org.jetbrains.anko.ctx
import org.jetbrains.anko.runOnUiThread


abstract class SpeakerEventListener: UtteranceProgressListener() {
    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }
    override fun onError(utteranceId: String?, errorCode: Int) {}
}


class SpeakingEventListener(private val app: ApplicationEx):
        SpeakerEventListener() {

    var finalUtteranceId: String? = null
    private var audioFocusRequestGranted = false

    override fun onStart(utteranceId: String?) {
        // Only request audio focus if we don't already have it.
        if (!audioFocusRequestGranted) {
            audioFocusRequestGranted = app.requestAudioFocus()
        }
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        super.onError(utteranceId, errorCode)
        // TODO display a toast message?
    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        if (interrupted) app.releaseAudioFocus()
    }

    override fun onDone(utteranceId: String?) {
        if (utteranceId == finalUtteranceId || finalUtteranceId == null) {
            app.releaseAudioFocus()
        }
    }
}

class SynthesisEventListener(private val ctx: Context,
                             private val filename: String):
        SpeakerEventListener() {

    override fun onStart(utteranceId: String?) {

    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        if (interrupted) {
            // TODO Display a toast message?

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

    }
}
