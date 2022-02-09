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

import android.content.Intent

const val APP_NAME = "com.danefinlay.ttsutil"
const val START_ACTIVITY_FLAGS = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
const val TAG = "TTSUtil"

// TTS operation result flags.
const val SUCCESS = 0
const val TTS_NOT_READY = -1
const val INVALID_FILE_URI = -2

// Task identifiers.
const val TASK_ID_IDLE = 0
const val TASK_ID_READ_TEXT = 1
const val TASK_ID_WRITE_FILE = 2
const val TASK_ID_PROCESS_FILE = 3
