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

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN

class IncompatibleWaveFileException(message: String): RuntimeException(message)

class WaveFile(stream: InputStream) {
    // Define a few convenient byte-related extension functions.
    private fun InputStream.read(n: Int): ByteArray {
        return (1..n).map { read().toByte() }.toByteArray()
    }

    private fun ByteArray.toLEByteBuffer(): ByteBuffer {
        return ByteBuffer.wrap(this).order(LITTLE_ENDIAN)
    }

    private fun ByteArray.toAsciiString(): String {
        return fold("") { acc, i -> acc + i.toChar() }
    }

    private fun ByteArray.toInt() = toLEByteBuffer().int
    private fun ByteArray.toShort() = toLEByteBuffer().short

    private fun ByteBuffer.putArrays(vararg arrays: ByteArray): ByteBuffer {
        arrays.forEach { put(it) }
        return this
    }

    // Read the RIFF chunk descriptor fields.
    val bChunkId = stream.read(4)
    val bChunkSize = stream.read(4)
    val bFormat = stream.read(4)
    val chunkId = bChunkId.toAsciiString()
    val chunkSize = bChunkSize.toInt()
    val format = bFormat.toAsciiString()

    // Read the "fmt " sub-chunk.
    val bSubChunk1Id = stream.read(4)
    val bSubChunk1Size = stream.read(4)
    val bAudioFormat = stream.read(2)
    val bNumChannels = stream.read(2)
    val bSampleRate = stream.read(4)
    val bByteRate = stream.read(4)
    val bBlockAlign = stream.read(2)
    val bBitsPerSample = stream.read(2)
    val subChunk1Id = bSubChunk1Id.toAsciiString()
    val subChunk1Size = bSubChunk1Size.toInt()
    val audioFormat = bAudioFormat.toShort()
    val numChannels = bNumChannels.toShort()
    val sampleRate = bSampleRate.toInt()
    val byteRate = bByteRate.toInt()
    val blockAlign = bBlockAlign.toShort()
    val bitsPerSample = bBitsPerSample.toShort()

    // Read the "data" sub-chunk.
    val bSubChunk2Id = stream.read(4)
    val subChunk2Id = {
        val result = bSubChunk2Id.toAsciiString()

        // We do not support non-PCM wave files with extra parameters.
        if (result != "data") {
            throw IncompatibleWaveFileException("Non-PCM wave files with extra " +
                    "parameters are not unsupported")
        }

        // PCM wave file.
        result
    }()
    val bSubChunk2Size = stream.read(4)
    val subChunk2Size = bSubChunk2Size.toInt()

    // The rest of the file is the actual sound data.
    val soundData = stream.readBytes()

    override fun toString(): String {
        return "${javaClass.simpleName}(" +
                "chunkId=$chunkId, chunkSize=$chunkSize, format=$format, " +
                "subChunk1Id=$subChunk1Id, subChunk1Size=$subChunk1Size, " +
                "audioFormat=$audioFormat, numChannels=$numChannels, " +
                "sampleRate=$sampleRate, byteRate=$byteRate, " +
                "blockAlign=$blockAlign, bitsPerSample=$bitsPerSample, " +
                "subChunk2Id=$subChunk2Id, subChunk2Size=$subChunk2Size" +
                ")"
    }

    fun getRiffHeader(chunkSize: Int): ByteArray {
        val header = ByteBuffer.allocate(12)
        val bChunkSize = ByteBuffer.allocate(4).putInt(chunkSize).array()
        return header.putArrays(bChunkId, bChunkSize, bFormat).array()
    }

    fun getSubChunk1(): ByteArray {
        return ByteBuffer.allocate(24).putArrays(bSubChunk1Id, bSubChunk1Size,
                bAudioFormat, bNumChannels, bSampleRate, bByteRate, bBlockAlign,
                bBitsPerSample).array()
    }

    fun compatibleWith(other: WaveFile): Boolean {
        return chunkId == other.chunkId &&
                // Do not compare the chunkSize fields because they include the size
                // of sub-chunk 2, the data chunk.
                // chunkSize == other.chunkSize &&
                format == other.format && subChunk1Id == other.subChunk1Id &&
                subChunk1Size == other.subChunk1Size &&
                audioFormat == other.audioFormat &&
                numChannels == other.numChannels &&
                sampleRate == other.sampleRate &&
                byteRate == other.byteRate &&
                blockAlign == other.blockAlign &&
                bitsPerSample == other.bitsPerSample
    }
}

/**
 * Function for taking wave files and writing a joined wave file.
 *
 * I note here that although all TTS engines I've tested have used wave files,
 * Android's TextToSpeech documentation makes no specific reference to them:
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech
 *
 * This, of course, has no bearing if the reader wishes to use this code on other
 * platforms.
 *
 * @param   inFiles     List of WAVE files.
 * @param   outFile     Output file where the joined wave file will be written.
 * @exception   IncompatibleWaveFileException   Raised for invalid/incompatible Wave
 * files.
 */
fun joinWaveFiles(inFiles: List<File>, outFile: File) {
    // Handle special case: empty list.
    if (inFiles.isEmpty()) return
    // Handle special case: single input file.
    // This also handles single non-wave input files.
    if (inFiles.size == 1) {
        outFile.writeBytes(inFiles.first().readBytes())
        return
    }

    val waveFiles = mutableListOf<WaveFile>()
    for (file in inFiles) {
        // Read the wave file.
        // Errors will be thrown if the file is NOT a wave sound file as defined by
        // the following specification: http://soundfile.sapp.org/doc/WaveFormat/
        val waveFile = FileInputStream(file).use { WaveFile(it) }
        if (waveFiles.isNotEmpty()) {
            val firstWaveFile = waveFiles.first()
            if (!waveFile.compatibleWith(firstWaveFile)) {
                throw IncompatibleWaveFileException("wave files with " +
                        "incompatible headers are not supported: " +
                        "$waveFile ~ $firstWaveFile")
            }
        }

        // Add the wave file to the list.
        waveFiles.add(waveFile)
    }

    // Construct a new wave file using header data.
    // Use the first wave file for fields with the same values.
    val firstWaveFile = waveFiles.first()

    // Calculate the SubChunk2Size and ChunkSize.
    val subChunk2Size = waveFiles.fold(0) { acc, waveFile ->
        acc + waveFile.subChunk2Size
    }
    val chunkSize = 4 + 8 + firstWaveFile.subChunk1Size + 8 + subChunk2Size

    // Open the output file for writing.
    FileOutputStream(outFile).use {
        // Write the RIFF header.
        it.write(firstWaveFile.getRiffHeader(chunkSize))

        // Write sub-chunk 1 ("fmt ").
        it.write(firstWaveFile.getSubChunk1())

        // Begin writing sub-chunk 2 ("data").
        it.write(firstWaveFile.bSubChunk2Id)
        it.write(ByteBuffer.allocate(4).putInt(subChunk2Size).array())

        // Write sound data from each file.
        for (waveFile in waveFiles) {
            it.write(waveFile.soundData)
        }
    }
}
