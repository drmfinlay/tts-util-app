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

import android.app.Notification
import android.speech.tts.UtteranceProgressListener
import org.jetbrains.anko.*


abstract class SpeakerEventListener(protected val app: ApplicationEx):
        UtteranceProgressListener() {

    protected abstract val notificationId: Int
    protected abstract val notification: Notification

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        // Return early if synthesis is only stopping.
        val speaker = app.speaker
        if (speaker?.stoppingSpeech == true) {
            return
        }

        // Display a toast message.
        app.runOnUiThread {
            longToast(R.string.text_synthesis_error_msg)
        }
    }

    protected fun startNotification() {
        app.notificationManager.notify(notificationId, notification)
    }

    protected fun cancelNotification() {
        app.notificationManager.cancel(notificationId)
    }
}


class SpeakingEventListener(app: ApplicationEx): SpeakerEventListener(app) {

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

class SynthesisEventListener(app: ApplicationEx, private val filename: String):
        SpeakerEventListener(app) {

    override val notificationId = SYNTHESIS_NOTIFICATION_ID
    override val notification =
            buildSpeakerNotification(app, notificationId)
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
            app.runOnUiThread {
                toast(getString(R.string.file_synthesis_interrupted_msg))
            }
        }
    }

    override fun onDone(utteranceId: String?) {
        app.runOnUiThread {
            AlertDialogBuilder(app).apply {
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
