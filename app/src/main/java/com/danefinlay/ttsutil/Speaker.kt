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

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.toast
import java.io.File

class Speaker(private val context: Context,
              var speechAllowed: Boolean,
              initListener: TextToSpeech.OnInitListener) {

    val tts = TextToSpeech(context.applicationContext, initListener)

    private val appCtx: ApplicationEx
        get() = context.applicationContext as ApplicationEx

    /** This should be changed by the OnInitListener. */
    var ready = false

    /**
     * Whether TTS synthesis has been or is in the process of being stopped.
     * */
    /**  */
    var stoppingSpeech = false
        private set

    private var lastUtteranceWasFileSynthesis: Boolean = false

    private var currentUtteranceId: Long = 0
    private fun getUtteranceId(): String {
        val id = currentUtteranceId
        currentUtteranceId += 1
        return "$id"
    }

    fun speak(string: String?) {
        // Split the text on any new lines to get a list. Utterance pauses will be
        // inserted this way.
        val lines = string?.split("\n") ?: return
        speak(lines)
    }

    fun speak(lines: List<String?>) {
        if (!(ready && speechAllowed)) {
            return
        }

        // Reset the stopping speech flag.
        stoppingSpeech = false

        // Stop possible file synthesis before speaking.
        if (lastUtteranceWasFileSynthesis) {
            tts.stop()
            lastUtteranceWasFileSynthesis = false
        }

        // Set the listener.
        val listener = SpeakingEventListener(appCtx)
        tts.setOnUtteranceProgressListener(listener)

        // Get Android's TTS framework to speak each non-null line.
        // This is, quite typically, different in some versions of Android.
        val nonEmptyLines = lines.mapNotNull { it }.filter { !it.isBlank() }
        val streamKey = TextToSpeech.Engine.KEY_PARAM_STREAM
        var utteranceId: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bundle = Bundle()
            bundle.putInt(streamKey, AudioManager.STREAM_MUSIC)
            nonEmptyLines.forEach {
                utteranceId = getUtteranceId()
                tts.speak(it, TextToSpeech.QUEUE_ADD, bundle, utteranceId)
                pause(100)
            }
        } else {
            val streamValue = AudioManager.STREAM_MUSIC.toString()
            nonEmptyLines.forEach {
                utteranceId = getUtteranceId()
                val map = hashMapOf(streamKey to streamValue,
                        KEY_PARAM_UTTERANCE_ID to utteranceId)

                @Suppress("deprecation")  // handled above.
                tts.speak(it, TextToSpeech.QUEUE_ADD, map)
                pause(100)
            }
        }

        // Set the listener's final utterance ID.
        listener.finalUtteranceId = utteranceId
    }

    fun pause(duration: Long, listener: SpeakerEventListener) {
        // Set the listener.
        tts.setOnUtteranceProgressListener(listener)
        pause(duration)
    }

    @Suppress("SameParameterValue")
    private fun pause(duration: Long) {
        val utteranceId = getUtteranceId()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(duration, TextToSpeech.QUEUE_ADD,
                    utteranceId)
        } else {
            @Suppress("deprecation")
            tts.playSilence(duration, TextToSpeech.QUEUE_ADD,
                    hashMapOf(KEY_PARAM_UTTERANCE_ID to utteranceId))
        }

    }

    fun synthesizeToFile(text: String, outFile: File,
                         listener: SpeakerEventListener) {
        // Stop speech before synthesizing.
        if (tts.isSpeaking) {
            tts.stop()
            context.runOnUiThread {
                toast(getString(R.string.pre_file_synthesis_msg))
            }
        }

        // Reset the stopping speech flag.
        stoppingSpeech = false

        // Set the listener.
        tts.setOnUtteranceProgressListener(listener)

        // Get an utterance ID.
        val utteranceId = getUtteranceId()

        if (Build.VERSION.SDK_INT >= 21) {
            tts.synthesizeToFile(text, null, outFile, utteranceId)
        } else {
            @Suppress("deprecation")
            tts.synthesizeToFile(
                    text, hashMapOf(KEY_PARAM_UTTERANCE_ID to utteranceId),
                    outFile.absolutePath)
        }

        // Set an internal variable for keeping track of file synthesis.
        lastUtteranceWasFileSynthesis = true
    }

    fun stopSpeech() {
        if ( tts.isSpeaking ) {
            // Set the stopping speech flag.
            stoppingSpeech = true

            // Tell the TTS engine to stop speech synthesis.
            tts.stop()
        }
    }

    fun free() {
        // Stop speech and free resources.
        stopSpeech()
        tts.shutdown()
    }
}

/**
 * Returns `true` if the Speaker is initialised and ready to speak.
 */
fun Speaker?.isReady(): Boolean {
    return this != null && this.ready && this.speechAllowed
}
