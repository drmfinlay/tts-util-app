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
import com.danefinlay.ttsutil.ACTION_READ_CLIPBOARD
import com.danefinlay.ttsutil.SpeakerIntentService
import com.danefinlay.ttsutil.isReady

/**
 * Activity to quickly read text from an input source.
 * Reading text is done via SpeakerIntentService.
 */
abstract class QuickShareActivity : SpeakerActivity() {

    abstract fun startServiceAction()

    private fun initialize() {
        // Start the appropriate service action if the Speaker is ready. Otherwise,
        // show the speaker not ready message.
        if (speaker.isReady()) startServiceAction()
        else showSpeakerNotReadyMessage()

        // Finish the activity.
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onInit(status: Int) {
        super.onInit(status)
        initialize()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        initialize()
    }

    override fun onDoNotInstallTTSData() {
        // Finish the quick share activity if the user doesn't want TTS data
        // installed.
        finish()
    }

    override fun onTTSInstallFailureDialogExit() {
        // Finish the quick share activity after informing the user that installing
        // TTS data failed.
        finish()
    }
}

class ReadClipboardActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == ACTION_READ_CLIPBOARD) {
            SpeakerIntentService.startActionReadClipboard(this)
        }
    }
}


class ReadTextActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            SpeakerIntentService.startActionReadText(this, text)
        }
    }
}
