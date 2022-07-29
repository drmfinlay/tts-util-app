/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
 *
 * Copyright (C) 2022 Dane Finlay
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

import androidx.appcompat.app.AppCompatActivity
import com.danefinlay.ttsutil.ApplicationEx

abstract class MyAppCompatActivity : AppCompatActivity() {
    protected val myApplication: ApplicationEx
        get() = application as ApplicationEx

    override fun onPause() {
        super.onPause()

        // Enable notifications when our activities are inactive.
        myApplication.enableNotifications()
    }

    override fun onResume() {
        super.onResume()

        // Disable notifications when one of our activities is active.
        myApplication.disableNotifications()
    }
}
