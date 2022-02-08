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

// TODO Adjust our fragments to inherit from a class that registers and unregisters
//  in the onAttach() and onDetach() callbacks.

/**
 * Interface between fragments and their activity.
 */
interface FragmentInterface {
    /**
     * This method is called for activity events.
     */
    fun onActivityEvent(event: ActivityEvent)
}

/**
 * Activity event classes.
 */
sealed class ActivityEvent {
    class FileChosenEvent(val uri: Uri) : ActivityEvent()
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
     * Attach a fragment (interface).
     *
     * This registers the fragment for events.
     */
    fun attachFragment(fragment: FragmentInterface)

    /**
     * Detach a fragment (interface), if it is attached.
     */
    fun detachFragment(fragment: FragmentInterface)

    /**
     * Notify attached fragments of an event.
     */
    fun notifyFragments(event: ActivityEvent)
}
