/*
 * Copyright (C) 2014 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.latin.utils

import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.Subtype

/**
 * This class determines that the language name on the spacebar should be displayed in what format.
 */
object LanguageOnSpacebarUtils {
    const val FORMAT_TYPE_NONE: Int = 0
    const val FORMAT_TYPE_LANGUAGE_ONLY: Int = 1
    const val FORMAT_TYPE_FULL_LOCALE: Int = 2

    fun getLanguageOnSpacebarFormatType(subtype: Subtype): Int {
        val locale = subtype.localeObject ?: return FORMAT_TYPE_NONE
        val keyboardLanguage = locale.language
        val keyboardLayout = subtype.keyboardLayoutSet
        var sameLanguageAndLayoutCount = 0
        val enabledSubtypes: Set<Subtype> =
            RichInputMethodManager.instance.getEnabledSubtypes(false)
        for (enabledSubtype in enabledSubtypes) {
            val language = enabledSubtype.localeObject?.language
            if (keyboardLanguage == language
                && keyboardLayout == enabledSubtype.keyboardLayoutSet
            ) {
                sameLanguageAndLayoutCount++
            }
        }
        // Display full locale name only when there are multiple subtypes that have the same
        // locale and keyboard layout. Otherwise displaying language name is enough.
        return if (sameLanguageAndLayoutCount > 1)
            FORMAT_TYPE_FULL_LOCALE
        else
            FORMAT_TYPE_LANGUAGE_ONLY
    }
}
