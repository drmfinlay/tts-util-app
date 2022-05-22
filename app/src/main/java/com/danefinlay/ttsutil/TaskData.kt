/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
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

package com.danefinlay.ttsutil

import java.io.File

sealed class TaskData(var taskId: Int, var progress: Int) {
    class ReadInputTaskData(taskId: Int, progress: Int,
                            val inputSource: InputSource,
                            val queueMode: Int) :
            TaskData(taskId, progress)
    class FileSynthesisTaskData(taskId: Int, progress: Int,
                                val inputSource: InputSource,
                                val outDirectory: Directory,
                                val waveFilename: String,
                                val inWaveFiles: MutableList<File>) :
            TaskData(taskId, progress)
    class JoinWaveFilesTaskData(taskId: Int, progress: Int,
                                val prevTaskData: FileSynthesisTaskData) :
            TaskData(taskId, progress)
}
