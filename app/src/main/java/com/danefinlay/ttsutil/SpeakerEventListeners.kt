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
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech.*
import android.speech.tts.UtteranceProgressListener
import org.jetbrains.anko.*
import java.io.File


abstract class SpeakerEventListener(protected val app: ApplicationEx):
        UtteranceProgressListener() {

    protected abstract val notificationId: Int
    protected abstract val notification: Notification
    var finalUtteranceId: String? = null

    override fun onError(utteranceId: String?) { // deprecated
        onError(utteranceId, -1)
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        // Return early if synthesis is only stopping.
        val speaker = app.speaker
        if (speaker?.stoppingSpeech == true) {
            return
        }

        // Schedule the notification to be cancelled and audio focus released in
        // roughly 300 ms. This is being done asynchronously because it is possible
        // that neither the notification has been started nor the audio focus has
        // been acquired by the time this is run.
        app.doAsync {
            Thread.sleep(300)
            cancelNotification()
            app.releaseAudioFocus()
        }

        // Get the matching error message string for errorCode.
        val errorMsg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Handle error messages available in SDK version 21 and above.
            when (errorCode) {
                ERROR_SYNTHESIS -> R.string.synthesis_error_msg_synthesis
                ERROR_SERVICE -> R.string.synthesis_error_msg_service
                ERROR_OUTPUT -> R.string.synthesis_error_msg_output
                ERROR_NETWORK -> R.string.synthesis_error_msg_network
                ERROR_NETWORK_TIMEOUT -> R.string.synthesis_error_msg_net_timeout
                ERROR_INVALID_REQUEST -> R.string.synthesis_error_msg_invalid_req
                ERROR_NOT_INSTALLED_YET -> R.string.synthesis_error_msg_voice_data
                else -> R.string.synthesis_error_msg_generic
            }
        } else {
            // Use the generic error message for version 20.
            R.string.synthesis_error_msg_generic
        }

        // Display the error message.
        app.runOnUiThread {
            longToast(errorMsg)
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

    private var audioFocusRequestGranted = false

    override fun onStart(utteranceId: String?) {
        // Only request audio focus if we don't already have it.
        if (!audioFocusRequestGranted) {
            audioFocusRequestGranted = app.requestAudioFocus()
            startNotification()
        }
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

class SynthesisEventListener(app: ApplicationEx, private val filename: String,
                             private val uiCtx: Context, private val outFile: File):
        SpeakerEventListener(app) {

    override val notificationId = SYNTHESIS_NOTIFICATION_ID
    override val notification =
            buildSpeakerNotification(app, notificationId)
    private var notificationStarted = false
    private val utteranceIds = mutableSetOf<String>()
    private val inWaveFiles: List<File>
        get() {
            val filesDir = app.filesDir
            return utteranceIds.map { File(filesDir, "$it.wav") }
        }

    private fun deleteWaveFiles() {
        inWaveFiles.forEach {
            if (it.isFile && it.canWrite()) {
                it.delete()
            }
        }
    }

    override fun onStart(utteranceId: String?) {
        if (!notificationStarted) {
            startNotification()
            notificationStarted = true
        }

        // Store the utterance ID.
        if (utteranceId != null) utteranceIds.add(utteranceId)
    }

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
        super.onStop(utteranceId, interrupted)
        if (interrupted) {
            cancelNotification()
            deleteWaveFiles()
            app.runOnUiThread {
                toast(getString(R.string.file_synthesis_interrupted_msg))
            }
        }
    }

    override fun onError(utteranceId: String?, errorCode: Int) {
        super.onError(utteranceId, errorCode)
        deleteWaveFiles()
    }

    override fun onDone(utteranceId: String?) {
        // Only continue if this is the final utterance.
        if (finalUtteranceId != null && utteranceId != finalUtteranceId)
            return

        // Join each utterance's wave file into one wave file. Use the output
        // file passed to this listener.
        joinWaveFiles(inWaveFiles, outFile)

        // Delete the temporary wave files afterwards.
        deleteWaveFiles()

        // Build and show an alert dialog once finished.
        app.runOnUiThread {
            // Use the given UI context.
            AlertDialogBuilder(uiCtx).apply {
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
