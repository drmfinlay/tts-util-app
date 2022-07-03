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

import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED
import android.speech.tts.Voice
import android.support.annotation.RequiresApi
//import android.util.Log
import java.util.*

/**
 * Extension property for safe use of the TextToSpeech.getVoice and
 * TextToSpeech.setVoice methods.
 *
 * This catches errors sometimes raised by the remote process.
 *
 * @return Voice instance used by the client, or {@code null} if not set or on
 * error.
 *
 * @see TextToSpeech.getVoice
 * @see TextToSpeech.setVoice
 * @see Voice
 */
var TextToSpeech.voiceEx: Voice?
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    get() {
        return try {
            // Try to retrieve the current voice.
            // Depending on the engine package, this can raise a
            // NullPointerException.
            voice
        }
        catch (error: NullPointerException) {
            null
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    set(value) {
        try {
            // Try to retrieve the current voice.
            // This can sometimes raise a NullPointerException.
            voice = value
            // Log.e(TAG, "Setting voice to ${value?.name}")
            // val result = setVoice(value)
            // Log.e(TAG, "Voice set to ${voiceEx?.name}, result=$result")
        }
        catch (error: NullPointerException) {}
    }

/**
 * Read-only extension property for safe use of the TextToSpeech.getDefaultVoice
 * method.
 *
 * This catches errors sometimes raised by the remote process.
 *
 * @return default Voice instance used by the client, or {@code null} if not set
 * or on error.
 *
 * @see TextToSpeech.getDefaultVoice
 * @see Voice
 */
val TextToSpeech.defaultVoiceEx: Voice?
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    get() {
        return try {
            // Try to retrieve the current voice.
            // This can sometimes raise a NullPointerException.
            defaultVoice
        }
        catch (error: NullPointerException) {
            null
        }
    }

/**
 * Read-only extension property for safe use of the TextToSpeech.getVoices method.
 *
 * This catches errors sometimes raised by the remote process.
 *
 * @return set of available Voice instances.
 *
 * @see TextToSpeech.getVoices
 * @see Voice
 */
val TextToSpeech.voicesEx: MutableSet<Voice?>
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    get() {
        return try {
            // Try to retrieve the set of available voices.
            // This can sometimes raise a NullPointerException.
            voices
        }
        catch (error: NullPointerException) {
            return mutableSetOf()
        }
    }

/**
 * Read-only extension property for getting the TTS locale.
 *
 * @see currentLocale
 */
val TextToSpeech.currentLocale: Locale?
    get() {
        @Suppress("deprecation")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            voiceEx?.locale ?: language
        } else {
            language
        }
    }

/**
 * Count available voices matching a given locale.
 *
 * The [locale] of a [Voice] is considered a match if its *language* and *country*
 * fields are the same.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun TextToSpeech.countAvailableVoices(locale: Locale): Int {
    // Find the number of matching voices by folding the list of voices.
    val voicesList = voicesEx.toList().filterNotNull()
    return voicesList.fold(0) { acc, voice ->
        val vLocale = voice.locale
        val match = locale.language == vLocale.language &&
                locale.country == vLocale.country
        val dataInstalled = KEY_FEATURE_NOT_INSTALLED !in voice.features
        acc + if (match && dataInstalled) 1 else 0
    }
}

/**
 * Find an acceptable TTS language based on a given locale.
 *
 * Note: LANG_MISSING_DATA is considered acceptable, but not available.
 */
fun TextToSpeech.findAcceptableTTSLanguage(locale: Locale): Locale? {
    return when (isLanguageAvailable(locale)) {
        TextToSpeech.LANG_MISSING_DATA,
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
            // This locale is acceptable.
            locale
        }
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
            // Use the country language if the variant is unavailable.
            Locale(locale.language, locale.country)
        }
        TextToSpeech.LANG_AVAILABLE -> {
            // Use the general language if the country is unavailable.
            Locale(locale.language)
        }
        TextToSpeech.LANG_NOT_SUPPORTED -> {
            null
        }
        else -> null
    }
}
