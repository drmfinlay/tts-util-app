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
        // Note: This onInit() handles only what an activity needs to.
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
            negativeButton(R.string.alert_negative_message)
            show()
        }
    }

    override fun requestSampleTTSText() {
        // Initialize the start activity intent.
        // Note: This action may be used to retrieve sample text for specific
        // localities.
        val intent = Intent(TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT)

        // Retrieve the current engine package name, if possible, and set it as
        // the target package.  This tells the system to exclude other TTS
        // engine packages installed.
        val packageName = myApplication.ttsEngineName
        if (packageName != null) intent.setPackage(packageName)

        // Start the appropriate activity for requesting TTS sample text from the
        // engine, falling back on ours if an exception occurs.
        try {
            startActivityForResult(intent, SAMPLE_TEXT_CODE)
        } catch (e: ActivityNotFoundException) {
            // Dispatch an event with the sample text.
            val sampleText = getString(R.string.sample_tts_sentence)
            val event = ActivityEvent.SampleTextReceivedEvent(sampleText)
            handleActivityEvent(event)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SAMPLE_TEXT_CODE -> {
                // Note: Sample text may be available if resultCode is
                // RESULT_CANCELLED.  Therefore, we do not check resultCode.
                // This apparent error may be explained by the peculiar nature of
                // the activity, which, for each engine this programmer has tried,
                // behaves like a trampoline.
                val key = TextToSpeech.Engine.EXTRA_SAMPLE_TEXT
                val sampleText = if (data != null && data.hasExtra(key)) {
                    // Retrieve the sample text.
                    data.getStringExtra(key)
                } else {
                    // Engine sample text unavailable.  Falling back on ours.
                    getString(R.string.sample_tts_sentence)
                }

                // Dispatch an event with the sample text.
                val event = ActivityEvent.SampleTextReceivedEvent(sampleText)
                handleActivityEvent(event)
            }
        }
    }

    fun startInstallTTSDataActivity() {
        // Initialize the start activity intent.
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

        // Retrieve the current engine package name, if possible, and set it as
        // the target package.  This tells the system to exclude other TTS
        // engine packages installed.
        val packageName = myApplication.ttsEngineName
        if (packageName != null) intent.setPackage(packageName)

        // Start the appropriate activity, displaying a warning message if no TTS
        // engine is available.
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.no_engine_available_message)
        }
    }
}
