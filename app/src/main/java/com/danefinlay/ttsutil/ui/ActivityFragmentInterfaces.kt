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

import android.net.Uri

/**
 * Activity event classes.
 */
sealed class ActivityEvent {
    class FileChosenEvent(val uri: Uri) : ActivityEvent()
    class StatusUpdateEvent(val progress: Int,
                            val taskId: Int) : ActivityEvent()
    class SampleTextReceivedEvent(val sampleText: String) : ActivityEvent()
}

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
}
