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

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.speech.tts.TextToSpeech
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.audioManager
import org.jetbrains.anko.longToast
import org.jetbrains.anko.notificationManager

class ApplicationEx : Application() {

    var speaker: Speaker? = null
        private set

    var errorMessageId: Int? = null

    private val audioFocusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    private val audioFocusRequest: AudioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            AudioFocusRequest.Builder(audioFocusGain)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                    .build()
        } else
            throw RuntimeException("should not use AudioFocusRequest below SDK v26")
    }

    private val onAudioFocusChangeListener = OnAudioFocusChangeListener {
        focusChange ->
        when ( focusChange ) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Stop speaking.
                speaker?.stopSpeech()
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        val audioManager = audioManager
        val success = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("deprecation")
            audioManager.requestAudioFocus(
                    onAudioFocusChangeListener, AudioManager.STREAM_ALARM,
                    audioFocusGain)
        } else {
            audioManager.requestAudioFocus(audioFocusRequest)
        }

        // Check the success value.
        return success == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun releaseAudioFocus() {
        // abandon the audio focus using the same context and AudioFocusChangeListener used
        // in requestAudioFocusResult to request it
        val audioManager = audioManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("deprecation")
            audioManager.abandonAudioFocus(onAudioFocusChangeListener)
        } else {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    fun cleanupFiles() {
        // Note: Exclude persistent data files here if the application ever requires
        // them.

        // Clean up no longer needed internal files.
        (filesDir.listFiles() + cacheDir.listFiles()).forEach { f ->
            if (f.isFile && f.canWrite()) {
                f.delete()
            }
        }
    }

    fun startSpeaker(initListener: TextToSpeech.OnInitListener,
                     preferredEngine: String?) {
        if (speaker == null) {
            // Try to get the preferred engine package from shared preferences if
            // it is null.
            val engineName = preferredEngine ?:
            PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("pref_tts_engine", null)

            // Initialise the Speaker object.
            speaker = Speaker(this, true, initListener,
                    engineName)
        }
    }

    fun openSystemTTSSettings(ctx: Context) {
        // Got this from: https://stackoverflow.com/a/8688354
        val intent = Intent()
        intent.action = "com.android.settings.TTS_SETTINGS"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        ctx.startActivity(intent)
    }

    /**
     * Show the speaker error message (if set) or the default speaker not ready
     * message.
     */
    fun showSpeakerNotReadyMessage() {
        val defaultMessageId = R.string.speaker_not_ready_message
        val errorMessageId = errorMessageId
        longToast(errorMessageId ?: defaultMessageId)
    }

    fun freeSpeaker() {
        speaker?.free()
        speaker = null

        // Cancel any TTS notifications present.
        notificationManager.cancel(SPEAKING_NOTIFICATION_ID)
        notificationManager.cancel(SYNTHESIS_NOTIFICATION_ID)
    }

    fun reinitialiseSpeaker(initListener: TextToSpeech.OnInitListener,
                            preferredEngine: String?) {
        freeSpeaker()
        startSpeaker(initListener, preferredEngine)
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Stop and free the current text-to-speech engine.
        freeSpeaker()
    }
}
