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
import com.danefinlay.ttsutil.TTSIntentService
import com.danefinlay.ttsutil.TTS_NOT_READY

/**
 * Activity to quickly read text from an input source.
 * Reading text is done via TTSIntentService.
 */
abstract class QuickShareActivity : TTSActivity() {

    abstract fun startServiceAction()

    private fun initialize() {
        if (!myApplication.ttsReady) {
            myApplication.handleTTSOperationResult(TTS_NOT_READY)
            return
        }

        // TTS is ready.  Start the appropriate service action and finish the
        // activity.
        startServiceAction()
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

    override fun handleActivityEvent(event: ActivityEvent) {}
}

class ReadClipboardActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == ACTION_READ_CLIPBOARD) {
            TTSIntentService.startActionReadClipboard(this)
        }
    }
}


class ReadTextActivity : QuickShareActivity() {
    override fun startServiceAction() {
        val intent = intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            TTSIntentService.startActionReadText(this, text)
        }
    }
}
