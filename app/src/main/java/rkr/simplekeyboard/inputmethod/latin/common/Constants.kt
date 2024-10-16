/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rkr.simplekeyboard.inputmethod.latin.common

object Constants {
    const val NOT_A_CODE: Int = -1
    const val NOT_A_COORDINATE: Int = -1

    // A hint on how many characters to cache from the TextView. A good value of this is given by
    // how many characters we need to be able to almost always find the caps mode.
    const val EDITOR_CONTENTS_CACHE_SIZE: Int = 1024

    // How many characters we accept for the recapitalization functionality. This needs to be
    // large enough for all reasonable purposes, but avoid purposeful attacks. 100k sounds about
    // right for this.
    const val MAX_CHARACTERS_FOR_RECAPITALIZATION: Int = 1024 * 100

    fun isValidCoordinate(coordinate: Int): Boolean {
        // Detect {@link NOT_A_COORDINATE}, {@link SUGGESTION_STRIP_COORDINATE},
        // and {@link SPELL_CHECKER_COORDINATE}.
        return coordinate >= 0
    }

    /**
     * Custom request code used in
     * [rkr.simplekeyboard.inputmethod.keyboard.KeyboardActionListener.onCustomRequest].
     */
    // The code to show input method picker.
    const val CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER: Int = 1

    /**
     * Some common keys code. Must be positive.
     */
    const val CODE_ENTER: Int = '\n'.code
    const val CODE_TAB: Int = '\t'.code
    const val CODE_SPACE: Int = ' '.code
    const val CODE_PERIOD: Int = '.'.code
    const val CODE_COMMA: Int = ','.code
    const val CODE_SINGLE_QUOTE: Int = '\''.code
    const val CODE_DOUBLE_QUOTE: Int = '"'.code
    const val CODE_BACKSLASH: Int = '\\'.code
    const val CODE_VERTICAL_BAR: Int = '|'.code
    const val CODE_PERCENT: Int = '%'.code
    const val CODE_INVERTED_QUESTION_MARK: Int = 0xBF // ¿
    const val CODE_INVERTED_EXCLAMATION_MARK: Int = 0xA1 // ¡

    /**
     * Special keys code. Must be negative.
     * These should be aligned with constants in
     * [rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardCodesSet].
     */
    const val CODE_SHIFT: Int = -1
    const val CODE_CAPSLOCK: Int = -2
    const val CODE_SWITCH_ALPHA_SYMBOL: Int = -3
    const val CODE_OUTPUT_TEXT: Int = -4
    const val CODE_DELETE: Int = -5
    const val CODE_SETTINGS: Int = -6
    const val CODE_ACTION_NEXT: Int = -8
    const val CODE_ACTION_PREVIOUS: Int = -9
    const val CODE_LANGUAGE_SWITCH: Int = -10
    const val CODE_SHIFT_ENTER: Int = -11
    const val CODE_SYMBOL_SHIFT: Int = -12

    // Code value representing the code is not specified.
    const val CODE_UNSPECIFIED: Int = -13

    fun isLetterCode(code: Int): Boolean {
        return code >= CODE_SPACE
    }

    fun printableCode(code: Int): String {
        when (code) {
            CODE_SHIFT -> return "shift"
            CODE_CAPSLOCK -> return "capslock"
            CODE_SWITCH_ALPHA_SYMBOL -> return "symbol"
            CODE_OUTPUT_TEXT -> return "text"
            CODE_DELETE -> return "delete"
            CODE_SETTINGS -> return "settings"
            CODE_ACTION_NEXT -> return "actionNext"
            CODE_ACTION_PREVIOUS -> return "actionPrevious"
            CODE_LANGUAGE_SWITCH -> return "languageSwitch"
            CODE_SHIFT_ENTER -> return "shiftEnter"
            CODE_UNSPECIFIED -> return "unspec"
            CODE_TAB -> return "tab"
            CODE_ENTER -> return "enter"
            CODE_SPACE -> return "space"
            else -> {
                if (code < CODE_SPACE) return String.format("\\u%02X", code)
                if (code < 0x100) return String.format("%c", code)
                if (code < 0x10000) return String.format("\\u%04X", code)
                return String.format("\\U%05X", code)
            }
        }
    }

    /**
     * Screen metrics (a.k.a. Device form factor) constants of
     * [rkr.simplekeyboard.inputmethod.R.integer.config_screen_metrics].
     */
    const val SCREEN_METRICS_LARGE_TABLET: Int = 2
    const val SCREEN_METRICS_SMALL_TABLET: Int = 3

    object Color {
        /**
         * The alpha value for fully opaque.
         */
        const val ALPHA_OPAQUE: Int = 255
    }

    object TextUtils {
        /**
         * Capitalization mode for [android.text.TextUtils.getCapsMode]: don't capitalize
         * characters.  This value may be used with
         * [android.text.TextUtils.CAP_MODE_CHARACTERS],
         * [android.text.TextUtils.CAP_MODE_WORDS], and
         * [android.text.TextUtils.CAP_MODE_SENTENCES].
         */
        // TODO: Straighten this out. It's bizarre to have to use android.text.TextUtils.CAP_MODE_*
        // except for OFF that is in Constants.TextUtils.
        const val CAP_MODE_OFF: Int = 0
    }
}
