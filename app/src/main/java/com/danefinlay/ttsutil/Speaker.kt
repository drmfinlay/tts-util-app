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

    /**
     * Whether the object is ready for speech synthesis.
     *
     * This should be changed by the OnInitListener.
     * */
    var ready = false

    /**
     * Whether TTS synthesis has been or is in the process of being stopped.
     * */
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

    private fun splitLongLines(lines: List<String>): List<String> {
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        val result = mutableListOf<String>()
        for (line in lines) {
            if (line.length < maxLength) {
                result.add(line)
                continue
            }

            // Split long lines into multiple strings of reasonable length.
            val shorterLines = mutableListOf("")
            line.forEach {
                val lastString = shorterLines.last()
                if (lastString.length < maxLength) {
                    // Separate on whitespace close to the maximum length where
                    // possible.
                    if (it.isWhitespace() && lastString.length > maxLength - 50)
                        shorterLines.add(it.toString())

                    // Add to the last string.
                    else {
                        val newLine = lastString + it.toString()
                        shorterLines[shorterLines.lastIndex] = newLine
                    }
                }

                // Add a new string.
                else shorterLines.add(it.toString())
            }

            // Add the shorter lines to the result list.
            result.addAll(shorterLines)
        }

        return result
    }

    fun speak(lines: List<String?>) {
        if (!(ready && speechAllowed)) {
            return
        }

        // Reset the stopping speech flag.
        stoppingSpeech = false

        // Stop possible file synthesis before speaking.
        if (lastUtteranceWasFileSynthesis) {
            stopSpeech()
            lastUtteranceWasFileSynthesis = false
        }

        // Handle lines that are null, blank or too long.
        val inputLines = splitLongLines(
                lines.mapNotNull { it }.filter { !it.isBlank() }
        )

        // Set the listener.
        val listener = SpeakingEventListener(appCtx)
        tts.setOnUtteranceProgressListener(listener)

        // Get Android's TTS framework to speak each line.
        // This is, quite typically, different in some versions of Android.
        val streamKey = TextToSpeech.Engine.KEY_PARAM_STREAM
        val firstUtteranceId = "$currentUtteranceId"
        inputLines.forEach {
            // Get the next utterance ID.
            val utteranceId = getUtteranceId()

            // Add this utterance to the queue.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val bundle = Bundle()
                bundle.putInt(streamKey, AudioManager.STREAM_MUSIC)
                tts.speak(it, TextToSpeech.QUEUE_ADD, bundle, utteranceId)
            } else {
                val streamValue = AudioManager.STREAM_MUSIC.toString()
                val map = hashMapOf(streamKey to streamValue,
                        KEY_PARAM_UTTERANCE_ID to utteranceId)

                @Suppress("deprecation")  // handled above.
                tts.speak(it, TextToSpeech.QUEUE_ADD, map)
            }

            // Add a short pause after each utterance.
            pause(100)
        }

        // Set the listener's first and final utterance IDs.
        listener.firstUtteranceId = firstUtteranceId
        listener.finalUtteranceId = "${currentUtteranceId - 1}"
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

    fun synthesizeToFile(text: String, listener: SynthesisEventListener) {
        // Stop speech before synthesizing.
        if (tts.isSpeaking) {
            stopSpeech()
            context.runOnUiThread {
                toast(getString(R.string.pre_file_synthesis_msg))
            }
        }

        // Reset the stopping speech flag.
        stoppingSpeech = false

        // Handle lines that are too long.
        val inputLines = splitLongLines(listOf(text))

        // Set the listener.
        tts.setOnUtteranceProgressListener(listener)

        // Get Android's TTS framework to synthesise each input line.
        val filesDir = appCtx.filesDir
        val firstUtteranceId = "$currentUtteranceId"
        inputLines.forEach {
            // Get the next utterance ID.
            val utteranceId = getUtteranceId()

            // Create a wave file for this utterance.
            val file = File(filesDir, "$utteranceId.wav")

            // Add this utterance to the queue.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.synthesizeToFile(it, null, file, utteranceId)
            } else {
                @Suppress("deprecation")
                tts.synthesizeToFile(
                        it, hashMapOf(KEY_PARAM_UTTERANCE_ID to utteranceId),
                        file.absolutePath)
            }
        }

        // Set the listener's first and final utterance IDs.
        listener.firstUtteranceId = firstUtteranceId
        listener.finalUtteranceId = "${currentUtteranceId - 1}"

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
