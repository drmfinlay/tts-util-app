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

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
    set(value) {
        try {
            // Try to retrieve the current voice.
            // This can sometimes raise a NullPointerException.
            voice = value
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
