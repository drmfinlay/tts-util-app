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
 * Interface for classes to observe a chosen file event.
 */
interface ChosenFileObserver {
    /**
     * This method is called whenever a file is chosen.
     */
    fun onFileChosen(uri: Uri)
}

/**
 * Interface for classes that prompt the user to pick a file for some purpose.
 */
interface ObservableFileChooser {
    /**
     * Show a screen where the user can choose a file.
     */
    fun showFileChooser()

    /**
     * Adds an observer to the set of observers to notify of chosen file events.
     */
    fun addObserver(observer: ChosenFileObserver)

    /**
     * Adds an observer to the set of observers to notify of chosen file events.
     */
    fun deleteObserver(observer: ChosenFileObserver)

    /**
     * Notify all observers of a chosen file event.
     */
    fun notifyObservers(uri: Uri)
}
