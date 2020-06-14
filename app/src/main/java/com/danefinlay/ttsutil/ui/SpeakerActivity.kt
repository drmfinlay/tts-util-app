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

package com.danefinlay.ttsutil.ui

import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v7.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.Speaker
import org.jetbrains.anko.AlertDialogBuilder
import org.jetbrains.anko.longToast

/**
 * Abstract activity class inherited from classes that use text-to-speech in some
 * way.
 */
abstract class SpeakerActivity: AppCompatActivity(), TextToSpeech.OnInitListener {
    val myApplication: ApplicationEx
        get() = application as ApplicationEx

    val speaker: Speaker?
        get() = myApplication.speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if ( savedInstanceState == null && speaker == null ) {
            // Start the speaker.
            myApplication.startSpeaker(this)
        }
    }

    override fun onInit(status: Int) {
        // Handle errors.
        val speaker = speaker
        val tts = speaker?.tts
        myApplication.errorMessageId = null
        if (status == TextToSpeech.ERROR || tts == null) {
            // Check the number of available TTS engines and set an appropriate
            // error message.
            val engines = speaker?.tts?.engines ?: listOf()
            val messageId = if (engines.isEmpty()) {
                // No usable TTS engines.
                R.string.no_engine_available_message
            } else {
                // General TTS initialisation failure.
                R.string.tts_initialisation_failure_msg
            }
            runOnUiThread { longToast(messageId) }

            // Save the error message ID for later use, free the Speaker and return.
            myApplication.errorMessageId = messageId
            myApplication.freeSpeaker()
            return
        }

        // Check if the language is available.
        val systemLocale = myApplication.currentSystemLocale
        @Suppress("deprecation")
        val language = when {
            Build.VERSION.SDK_INT >= LOLLIPOP -> tts.voice?.locale
            else -> tts.language
        } ?: tts.language ?: systemLocale
        when (tts.isLanguageAvailable(language)) {
            // Set the language if it is available and there is no current voice.
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                if (Build.VERSION.SDK_INT < LOLLIPOP ||
                        Build.VERSION.SDK_INT >= LOLLIPOP && tts.voice == null)
                    tts.language = language

                // The Speaker is now ready to process text into speech.
                speaker.ready = true
            }

            // Install missing voice data if required.
            TextToSpeech.LANG_MISSING_DATA -> {
                runOnUiThread {
                    showNoTTSDataDialog()
                }
            }

            // Inform the user that the selected language is not available.
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                // Attempt to fall back on the system language.
                when (tts.isLanguageAvailable(systemLocale)) {
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                        runOnUiThread {
                            longToast(R.string.tts_language_not_available_msg1)
                        }
                        tts.language = systemLocale

                        // The Speaker is now ready to process text into speech.
                        speaker.ready = true
                    }

                    else -> {
                        // Neither the selected nor the default languages are
                        // available.
                        val messageId = R.string.tts_language_not_available_msg2
                        myApplication.errorMessageId = messageId
                        runOnUiThread { longToast(messageId) }
                    }
                }
            }
        }
    }

    fun showSpeakerNotReadyMessage() {
        myApplication.showSpeakerNotReadyMessage()
    }

    private fun showNoTTSDataDialog() {
        AlertDialogBuilder(this).apply {
            title(R.string.no_tts_data_alert_title)
            message(R.string.no_tts_data_alert_message)
            positiveButton(R.string.alert_positive_message) {
                noTTSDataDialogPositiveButton()
            }
            negativeButton(R.string.alert_negative_message1) {
                onDoNotInstallTTSData()
            }
            show()
        }
    }

    open fun noTTSDataDialogPositiveButton() {
        val install = Intent()
        install.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
        startActivityForResult(install, INSTALL_TTS_DATA)
    }

    open fun onDoNotInstallTTSData() { }

    private fun showTTSInstallFailureDialog() {
        // Show a dialog if installing TTS data failed.
        AlertDialogBuilder(this).apply {
            title(R.string.failed_to_get_tts_data_title)
            message(R.string.failed_to_get_tts_data_msg)
            positiveButton(R.string.alert_positive_message) {
                onTTSInstallFailureDialogExit()
            }
            onCancel { onTTSInstallFailureDialogExit() }
            show()
        }
    }

    open fun onTTSInstallFailureDialogExit() { }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            INSTALL_TTS_DATA -> {
                if (resultCode == TextToSpeech.SUCCESS) {
                    speaker?.ready = true
                } else {
                    showTTSInstallFailureDialog()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val INSTALL_TTS_DATA = 1
    }
}
