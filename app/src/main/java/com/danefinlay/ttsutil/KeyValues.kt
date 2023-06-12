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

package com.danefinlay.ttsutil

import android.content.Intent

const val APP_NAME = "com.danefinlay.ttsutil"
const val START_ACTIVITY_FLAGS = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
const val TAG = "TTSUtil"

// Activity request codes.
const val FILE_SELECT_CODE = 0
const val DIR_SELECT_CODE = 1
const val DIR_SELECT_CONT_CODE = 2
const val SAMPLE_TEXT_CODE = 3
const val REQUEST_EXTERNAL_STORAGE = 4

// Task result codes.
const val SUCCESS = 0
const val TTS_NOT_READY = -1
const val UNAVAILABLE_INPUT_SRC = -2
const val UNAVAILABLE_OUT_DIR = -3
const val UNWRITABLE_OUT_DIR = -4
const val TTS_BUSY = -5
const val ZERO_LENGTH_INPUT = -6

// Task identifiers.
const val TASK_ID_IDLE = 0
const val TASK_ID_READ_TEXT = 1
const val TASK_ID_WRITE_FILE = 2
const val TASK_ID_PROCESS_FILE = 3

// Misc.
const val CHOSEN_FILE_URI_KEY = "$APP_NAME.CHOSEN_FILE_URI_KEY"
const val CHOSEN_FILE_NAME_KEY = "$APP_NAME.CHOSEN_FILE_NAME_KEY"
const val CHOSEN_FILE_LOCALE_KEY = "$APP_NAME.CHOSEN_FILE_LOCALE_KEY"
const val CHOSEN_DIR_URI_KEY = "$APP_NAME.CHOSEN_DIR_URI_KEY"
const val CHOSEN_DIR_NAME_KEY = "$APP_NAME.CHOSEN_DIR_NAME_KEY"
const val CHOSEN_DIR_LOCALE_KEY = "$APP_NAME.CHOSEN_DIR_LOCALE_KEY"
