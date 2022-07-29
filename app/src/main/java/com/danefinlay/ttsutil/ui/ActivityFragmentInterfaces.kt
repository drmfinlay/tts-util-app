/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
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

import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi

/**
 * Interface between fragments and their activity.
 */
interface FragmentInterface {
    /**
     * This method is called for activity events.
     */
    fun handleActivityEvent(event: ActivityEvent)
}

/**
 * Interface for classes that prompt the user to pick a file for some purpose.
 */
interface ActivityInterface {
    /**
     * Show a screen where the user can choose a file.
     */
    fun showFileChooser()

    /**
     * Show a screen where the user can choose a directory.
     *
     * This is only possible on Android version 5 and above.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun showDirChooser(requestCode: Int)

    /**
     * Request that TTS be (re-)initialized.
     */
    fun initializeTTS(initListener: TextToSpeech.OnInitListener?)

    /**
     * Function to request sample TTS text from the current TTS engine.
     */
    fun requestSampleTTSText()

    /**
     * Use attached fragments to handle an event.
     */
    fun handleActivityEvent(event: ActivityEvent)

    /**
     * Get the most recent status update event.
     *
     * TASK_ID_IDLE is used if there have been no updates yet.
     */
    fun getLastStatusUpdate(): ActivityEvent.StatusUpdateEvent

    /**
     * Get the most recent directory chosen event, if any.
     */
    fun getLastDirChosenEvent(): ActivityEvent.ChosenFileEvent?

    /**
     * Get the most recent file chosen event, if any.
     */
    fun getLastFileChosenEvent(): ActivityEvent.ChosenFileEvent?
}
