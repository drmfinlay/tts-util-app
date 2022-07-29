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

package com.danefinlay.ttsutil

import android.text.Editable
import android.text.TextWatcher

/**
 * This abstract TextWatcher class is used to read input text changes with
 * text-to-speech.
 *
 * Implementations should define `readChangedText(CharSequence)' method.
 *
 * Text is read according to the following rules:
 *  1. If only one character was inserted and it was not a delimiter (see below),
 *     read it as if a word was being spelt out character-by-character.
 *  2. If only one character was inserted and it was a delimiter, read the word
 *     before it, if there is one, which we assume has just been spelt out.
 *  3. If two delimiters appear after a word, only read the word for the first one
 *     inserted.
 *  4. If the character sequence has entirely changed, read from the start to the
 *     end normally.
 *
 * Spaces, full stops, ellipses, commas, colons, semicolons, exclamation marks,
 * question marks and quotation marks are the "delimiters" mentioned in the first
 * three rules.  The Unicode "Halfwidth and Fullwidth Forms" of these symbols are
 * also treated as delimiters.
 *
 */
 abstract class InputTextReader : TextWatcher {

    abstract fun readChangedText(text: CharSequence)

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int,
                                   after: Int) {}

    protected open fun charIsDelimiter(char: Char): Boolean {
        // Note: Since it is not too much trouble, we include the Unicode
        // "Halfwidth and Fullwidth Forms" for each specific delimiter.
        val cp = char.code
        return     cp == 0x0021 || cp == 0xff01 // Exclamation marks.
                || cp == 0x0022 || cp == 0xff02 // Quotation marks.
                || cp == 0x002c || cp == 0xff0c // Commas.
                || cp == 0xff64
                || cp == 0x002e || cp == 0xff0e // Full stops.
                || cp == 0xff61
                || cp == 0x003a || cp == 0xff1a // Colons.
                || cp == 0x003b || cp == 0xff1b // Semicolons.
                || cp == 0x003f || cp == 0xff1f // Question marks.
                || cp == 0x2026                 // Ellipses.
                || char.isWhitespace()
    }

    override fun onTextChanged(s: CharSequence?, start: Int,
                               before: Int, count: Int) {
        // Do nothing if the character sequence is null or if a deletion occurred.
        if (s == null || count < before) return

        val insertedText = s.subSequence(start, start + count)
        val startChar = insertedText[0]
        val startsWithDelimiter = charIsDelimiter(startChar)

        // Rule one (see documentation above).
        if (count == 1 && !startsWithDelimiter) {
            readChangedText(insertedText)
        }

        // Rule two and three.
        else if (count == 1 && startsWithDelimiter) {
            val builder = StringBuilder()
            builder.append(startChar)
            for (i in start-1 downTo 0) {
                val char = s[i]
                if (charIsDelimiter(char)) break
                builder.append(char)
            }
            if (builder.length > 1) {
                readChangedText(builder.reverse().toString())
            }
        }

        // Rule four.
        else if (start == 0 && count >= before) {
            readChangedText(insertedText)
        }
    }

    override fun afterTextChanged(e: Editable?) {}
}