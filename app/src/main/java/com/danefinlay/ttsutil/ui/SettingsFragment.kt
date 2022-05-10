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

import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import android.speech.tts.Voice
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.view.ContextThemeWrapper
import com.danefinlay.ttsutil.*
import org.jetbrains.anko.toast

/**
 * A [Fragment] subclass for app and TTS settings.
 */
class SettingsFragment : PreferenceFragmentCompat(), FragmentInterface {

    private val Fragment.myApplication: ApplicationEx
        get() = requireContext().applicationContext as ApplicationEx

    private var sampleTextEvent: ActivityEvent.SampleTextReceivedEvent? = null
    private var onFinishSample: (() -> Unit)? = null
    private var sampleInProgress = false

    override fun onCreatePreferences(savedInstanceState: Bundle?,
                                     rootKey: String?) {
        // Load the preferences from an XML resource.
        setPreferencesFromResource(R.xml.prefs, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        // Handle TTS engine preferences.
        if (handleTtsEnginePrefs(preference)) return true

        return super.onPreferenceTreeClick(preference)
    }

    private fun reinitialiseTTS(preferredEngine: String?,
                                preInitFunction: (() -> Unit)? = null) {
        // Refuse to reinitialise TTS if there is a running task.
        // It is okay if the running task is our sample text.
        if (myApplication.taskInProgress && !sampleInProgress) {
            myApplication.handleTTSOperationResult(TTS_BUSY)
            return
        }

        val listener = activity as TextToSpeech.OnInitListener
        preInitFunction?.invoke()
        myApplication.reinitialiseTTS(listener, preferredEngine)
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        // Handle events.
        val app = myApplication
        when (event) {
            is ActivityEvent.SampleTextReceivedEvent -> {
                // Save this event for next time.
                sampleTextEvent = event

                // Speak sample text and handle the result.
                // We use QUEUE_FLUSH because it is more appropriate.
                val result = app.enqueueReadInputTask(event.sampleText, QUEUE_FLUSH)
                if (result == SUCCESS) {
                    sampleInProgress = true
                } else if (result == TTS_NOT_READY) {
                    app.handleTTSOperationResult(result)
                }
            }
            is ActivityEvent.StatusUpdateEvent -> {
                val progress = event.progress
                val taskId = event.taskId
                val finished = progress == 100 || progress == -1
                if (finished && taskId == TASK_ID_READ_TEXT) {
                    // Invoke onFinishSample().
                    sampleInProgress = false
                    onFinishSample?.invoke()
                    onFinishSample = null
                }
            }
            else -> {}
        }
    }

    private fun playSampleText(onFinishSample: () -> Unit) {
        // Do nothing if a file synthesis task is in progress.
        if (myApplication.fileSynthesisTaskInProgress) return

        // Set onFinishSample().
        this.onFinishSample = onFinishSample

        // Do nothing further if other text is being read.
        if (myApplication.readingTaskInProgress && !sampleInProgress) return

        // Handle the last sample text event, if it is available.
        // If it isn't, request it.  This should result in the sample text being
        // spoken.
        val event = sampleTextEvent
        if (event != null) handleActivityEvent(event)
        else (activity as? ActivityInterface)?.requestSampleTTSText()
    }

    private fun playSampleWithVoice(tts: TextToSpeech, currentVoice: Voice?,
                                    voice: Voice) {
        // Temporarily set the voice.
        tts.voiceEx = voice

        // Display a message if it is not installed.  This way, the user can, if
        // they so wish, try the voice until the engine installs it.
        val ctx = requireContext()
        val unavailableFlag = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED
        if (unavailableFlag in voice.features) {
            ctx.toast(R.string.voice_not_installed)
            tts.voiceEx = currentVoice
        }

        // Otherwise, speak sample text with the voice and set it back
        // afterwards.
        else {
            val onFinishSample: () -> Unit = { tts.voiceEx = currentVoice }
            playSampleText(onFinishSample)
        }
    }

    private fun handleTtsEnginePrefs(preference: Preference?): Boolean {
        val key = preference?.key

        // Handle opening the system settings.
        val app = myApplication
        val ctx = requireContext()
        if (key == "pref_tts_system_settings") {
            app.openSystemTTSSettings(ctx)
            return true
        }

        val tts = app.mTTS
        if (tts == null || !app.ttsReady) {
            app.handleTTSOperationResult(TTS_NOT_READY)
            return true
        }

        // Handle setting preferences.
        // It is here noted that these selection menus may be used to change the TTS
        // voice, pitch and/or speech rate during long-running reading or file
        // synthesis tasks.
        return when (key) {
            "pref_tts_engine" -> handleSetTtsEngine(key, tts)
            "pref_tts_voice" -> handleSetTtsVoice(key, tts)
            "pref_tts_pitch" -> handleSetTtsPitch(key, tts)
            "pref_tts_speech_rate" -> handleSetTtsSpeechRate(key, tts)
            else -> false  // not a TTS engine preference.
        }
    }

    private fun buildAlertDialog(title: Int, items: List<String>,
                                 checkedItem: Int,
                                 onItemSelected: (index: Int) -> Unit,
                                 onClickPositive: (index: Int) -> Unit):
            AlertDialog.Builder {
        val context = ContextThemeWrapper(context, R.style.AlertDialogTheme)
        return AlertDialog.Builder(context).apply {
            setTitle(title)
            var selection = checkedItem
            setSingleChoiceItems(items.toTypedArray(), checkedItem) { _, index ->
                selection = index
                onItemSelected(index)
            }
            setPositiveButton(R.string.alert_positive_message_1) { _, _ ->
                if (selection >= 0 && selection < items.size) {
                    onClickPositive(selection)
                }
            }
        }
    }

    private fun AlertDialog.Builder.setCancelButton(onCancel: () -> Unit):
            AlertDialog.Builder {
        setNegativeButton(R.string.alert_negative_message_1) { _, _ -> }

        // Note: The OnDismissListener will be notified: when the negative button is
        // pressed; when the alert is cancelled; and when the alert is dismissed.
        setOnDismissListener { onCancel() }
        return this
    }

    private fun AlertDialog.Builder.setUseDefaultButton(onClick: () -> Unit):
            AlertDialog.Builder {
        setNeutralButton(R.string.use_default_tts_preference) { _, _ -> onClick() }
        return this
    }

    private fun handleSetTtsEngine(preferenceKey: String,
                                   tts: TextToSpeech): Boolean {
        // Get a list of the available TTS engines.
        val engines = tts.engines?.toList()?.sortedBy { it.label } ?: return true
        val engineNames = engines.map { it.label }
        val enginePackages = engines.map { it.name }

        // Get the previous or default engine.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getString(preferenceKey, tts.defaultEngine)
        val currentIndex = enginePackages.indexOf(currentValue)

        // Show a list alert dialog of the available TTS engines.
        val dialogTitle = R.string.pref_tts_engine_summary
        val onItemSelected = { _: Int -> }
        val onClickPositive = { index: Int ->
            // Get the package name from the index of the selected item and
            // use it to set the current engine.
            val packageName = engines.map { it.name }[index]
            reinitialiseTTS(packageName, preInitFunction = {
                // Set the engine's name in the preferences.
                prefs.edit().putString(preferenceKey, packageName).apply()

                // Since it may be different for the selected engine, invalidate the
                // sample text.
                sampleTextEvent = null
            })
        }
        val onClickUseDefault: () -> Unit = {
            // Remove the preferred engine's name from the preferences and set the
            // default engine by reinitialising.
            reinitialiseTTS(null, preInitFunction = {
                prefs.edit().remove(preferenceKey).apply()
            })
        }

        // Build and show the dialog.
        val builder = buildAlertDialog(dialogTitle, engineNames, currentIndex,
                onItemSelected, onClickPositive)
                .setUseDefaultButton(onClickUseDefault)
                .setCancelButton {}
        builder.show()
        return true
    }

    private fun chooseVoiceSubSelection(prefs: SharedPreferences,
                                        tts: TextToSpeech,
                                        preferenceKey: String,
                                        currentVoice: Voice?,
                                        voiceSelection: List<Voice>) {
        // Get a list of display names, adding a number after each non-distinct
        // name.
        // TODO Add "(network required)" before the number for voices that require
        //  network connectivity (isNetworkConnectionRequired).
        //  This should also be done for the first menu and the string should be
        //  placed in strings.xml.
        val displayName = voiceSelection[0].locale.displayName
        val displayNames = voiceSelection
                .mapIndexed { i, _ -> "$displayName ${i + 1}" }

        // Define callbacks.
        val onClickPositive = { index: Int ->
            // Set the selected voice.
            val selectedVoice = voiceSelection[index]
            tts.voiceEx = selectedVoice

            // Set the voice's name in the preferences.
            prefs.edit().putString(preferenceKey, selectedVoice.name)
                    .apply()
        }
        val onItemSelected = { index: Int ->
            // Play sample text with the selected voice, if possible.
            playSampleWithVoice(tts, currentVoice, voiceSelection[index])
        }
        val onCancel: () -> Unit = { tts.voiceEx = currentVoice }

        // Set the current index as either the current voice, if present, or the
        // first voice on the list.
        val currentVoiceIndex = voiceSelection.indexOf(currentVoice)
        val currentIndex = if (currentVoiceIndex > 0) currentVoiceIndex else 0

        // Build and show the dialog.
        val dialogTitle = R.string.pref_tts_voice_summary
        val builder = buildAlertDialog(dialogTitle, displayNames, currentIndex,
                onItemSelected, onClickPositive)
                .setCancelButton(onCancel)
        builder.show()
    }

    private fun handleSetTtsVoice(preferenceKey: String,
                                  tts: TextToSpeech): Boolean {
        // Get the set of available TTS voices.
        // Return early if the engine returned no voices.
        val voices = tts.voicesEx
        if (voices.isEmpty()) {
            context?.toast(R.string.no_tts_voices_msg)
            return true
        }

        // Get a list of voices.
        val voicesList = voices.toList().filterNotNull()

        // Retrieve the previous voice, falling back on the current or default.
        val defaultVoice = tts.defaultVoiceEx
        var currentVoice: Voice? = tts.voiceEx ?: defaultVoice
        val prefs = preferenceManager.sharedPreferences
        val prevVoiceName = prefs.getString(preferenceKey, currentVoice?.name)
        val prevVoice = voicesList.find { it.name == prevVoiceName }
        if (prevVoice != null) currentVoice = prevVoice

        // Get a list of voice display names sorted by language for the user to
        // select from.
        val voicesByDisplayName = voicesList.groupBy { it.locale.displayName }
        val displayNames = voicesByDisplayName.map { it.value[0].locale }
                .sortedBy { it.displayLanguage }.map { it.displayName }
        val currentIndex = displayNames.indexOf(currentVoice?.locale?.displayName)

        // Define callbacks.
        val onItemSelected = { index: Int ->
            // Play sample text with the selected voice, if possible.
            val item = displayNames[index]
            val selectedVoice = voicesByDisplayName[item]!![0]
            playSampleWithVoice(tts, currentVoice, selectedVoice)
        }
        val onClickPositive: (Int) -> Unit = { index: Int ->
            // Retrieve the list of voices for the selected display name and handle
            // selecting one.
            val item = displayNames[index]
            val voiceSelection = voicesByDisplayName[item]!!

            // It is noted here that, at least if Google's TTS engine is used, this
            // may provide greater user choice than the system settings.

            if (voiceSelection.size > 1) {
                // Display another dialog to the user for selection.
                chooseVoiceSubSelection(prefs, tts, preferenceKey, currentVoice,
                        voiceSelection)
            } else {
                // There is no sense in showing another dialog for only one option.
                // Set the first and only voice in the selection.
                val selectedVoice = voiceSelection[0]
                tts.voiceEx = selectedVoice

                // Set the voice's name in the preferences.
                prefs.edit().putString(preferenceKey, selectedVoice.name)
                        .apply()
            }
        }
        val onClickUseDefault = {
            // Use the default TTS voice/language.
            if (defaultVoice != null) {
                tts.voiceEx = defaultVoice
            } else {
                tts.language = currentSystemLocale
            }

            // Remove the current voice's name from the preferences.
            prefs.edit().remove(preferenceKey).apply()
        }
        val onCancel: () -> Unit = { tts.voiceEx = currentVoice }

        // Build and show the dialog.
        val dialogTitle = R.string.pref_tts_voice_summary
        val builder = buildAlertDialog(dialogTitle, displayNames, currentIndex,
                onItemSelected, onClickPositive)
                .setUseDefaultButton(onClickUseDefault)
                .setCancelButton(onCancel)
        builder.show()
        return true
    }

    private fun handleSetTtsPitch(preferenceKey: String,
                                  tts: TextToSpeech): Boolean {
        // Define a list of pitch values and their string representations.
        val pitches = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val pitchStrings = pitches.map { it.toString() }

        // Get the previous or default pitch value.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getFloat(preferenceKey, 1.0f)
        val currentIndex = pitches.indexOf(currentValue)

        // Show a list alert dialog of pitch choices.
        val onItemSelected = { index: Int ->
            // Play sample text with the selected pitch, if possible.
            // The current pitch is restored afterward.
            tts.setPitch(pitches[index])
            val onFinishSample: () -> Unit = { tts.setPitch(currentValue) }
            playSampleText(onFinishSample)
        }
        val onClickPositive = { index: Int ->
            // Get the pitch from the index of the selected item and
            // use it to set the current voice pitch.
            val pitch = pitches[index]
            tts.setPitch(pitch)

            // Set the pitch in the preferences.
            prefs.edit().putFloat(preferenceKey, pitch).apply()
        }
        val onClickUseDefault = {
            // Reinitialise the TTS engine so it uses the pitch as set in the system
            // TTS settings.
            reinitialiseTTS(null, preInitFunction = {
                // Remove the preferred pitch from the preferences.
                prefs.edit().remove(preferenceKey).apply()
            })
        }
        val onCancel: () -> Unit = { tts.setPitch(currentValue) }

        // Build and show the dialog.
        val dialogTitle = R.string.pref_tts_pitch_summary
        val builder = buildAlertDialog(dialogTitle, pitchStrings, currentIndex,
                onItemSelected, onClickPositive)
                .setUseDefaultButton(onClickUseDefault)
                .setCancelButton(onCancel)
        builder.show()
        return true
    }

    private fun handleSetTtsSpeechRate(preferenceKey: String,
                                       tts: TextToSpeech): Boolean {
        // Define a list of speech rate values and their string representations.
        val speechRates = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f,
                4.0f, 5.0f)
        val speechRateStrings = speechRates.map { it.toString() }

        // Get the previous or default speech rate value.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getFloat(preferenceKey, 1.0f)
        val currentIndex = speechRates.indexOf(currentValue)

        // Show a list alert dialog of speech rate choices.
        val onItemSelected = { index: Int ->
            // Play sample text with the selected speech rate, if possible.
            // The current rate is restored afterward.
            tts.setSpeechRate(speechRates[index])
            val onFinishSample: () -> Unit = { tts.setSpeechRate(currentValue) }
            playSampleText(onFinishSample)
        }
        val onClickPositive = { index: Int ->
            // Get the speech rate from the index of the selected item and
            // use it to set the current speech rate.
            val speechRate = speechRates[index]
            tts.setSpeechRate(speechRate)

            // Set the speech rate in the preferences.
            prefs.edit().putFloat(preferenceKey, speechRate).apply()
        }
        val onClickUseDefault = {
            // Reinitialise the TTS engine so it uses the speech rate as set in the
            // system TTS settings.
            reinitialiseTTS(null, preInitFunction = {
                // Remove the preferred speech rate from the preferences.
                prefs.edit().remove(preferenceKey).apply()
            })
        }
        val onCancel: () -> Unit = { tts.setSpeechRate(currentValue) }

        // Build and show the dialog.
        val dialogTitle = R.string.pref_tts_speech_rate_summary
        val builder = buildAlertDialog(dialogTitle, speechRateStrings, currentIndex,
                onItemSelected,onClickPositive)
                .setUseDefaultButton(onClickUseDefault)
                .setCancelButton(onCancel)
        builder.show()
        return true
    }
}
