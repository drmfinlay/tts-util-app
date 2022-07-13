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

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.danefinlay.ttsutil.ACTION_EDIT_READ_CLIPBOARD
import com.danefinlay.ttsutil.R

class EditReadActivity : TTSActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_read)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            // Instantiate the appropriate fragment.
            val fragment: ReadTextFragmentBase = when (intent?.action) {
                Intent.ACTION_SEND -> ReadTextFragment()
                ACTION_EDIT_READ_CLIPBOARD -> ReadClipboardFragment()
                else -> return
            }

            // Show the instantiated fragment.
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        // If this is a status update event, save it.
        if (event is ActivityEvent.StatusUpdateEvent) {
            mLastStatusUpdate = event
        }

        val fragments = supportFragmentManager.fragments
        handleActivityEvent(event, fragments)
    }
}
