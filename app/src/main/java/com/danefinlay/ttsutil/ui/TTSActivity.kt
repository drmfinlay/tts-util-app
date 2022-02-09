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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.AlertDialogBuilder
import org.jetbrains.anko.toast
import java.util.*

/**
 * Abstract activity class inherited from classes that use text-to-speech in some
 * way.
 */
abstract class TTSActivity: MyAppCompatActivity(), TextToSpeech.OnInitListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup TTS, if necessary.
        // Note: It would be nice if this could be done in the background; this
        // causes a noticeable delay when the application starts.
        if (savedInstanceState == null && !myApplication.ttsReady) {
            myApplication.setupTTS(this, null)
        }
    }

    override fun onInit(status: Int) {
        // Note: This onInit() handles only what the activity needs to.
        // ApplicationEx does most of the setup originally done here.
        val tts = myApplication.mTTS ?: return

        // Install missing voice data if required.
        // Note: countAvailableVoices() is more reliable when voice data is not yet
        // downloaded, at least with Google's text-to-speech engine, which, in this
        // case, defaults to an available voice.
        val language = tts.currentLocale
        val languageUnavailable = (
                language == null ||
                tts.isLanguageAvailable(language) == LANG_MISSING_DATA ||
                tts.countAvailableVoices(language) == 0
        )
        if (languageUnavailable) {
            runOnUiThread { showNoTTSDataDialog(tts, language) }
        }
    }

    private fun showNoTTSDataDialog(tts: TextToSpeech, language: Locale?) {
        AlertDialogBuilder(this).apply {
            // Set the title.
            title(R.string.no_tts_data_alert_title)

            // Get the engine info.
            // Note: An engine should be available at this point.
            val engine = tts.engines.find { it.name == tts.defaultEngine }!!

            // Set the message text.
            val messageText = getString(R.string.no_tts_data_alert_message,
                    language?.displayName, engine.label
            )
            message(messageText)

            // Set buttons and show the dialog.
            positiveButton(R.string.alert_positive_message) {
                startInstallTTSDataActivity()
            }
            negativeButton(R.string.alert_negative_message1)
            show()
        }
    }

    fun startInstallTTSDataActivity() {
        val install = Intent()
        install.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
        try {
            startActivity(install)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.no_engine_available_message)
        }
    }

}
