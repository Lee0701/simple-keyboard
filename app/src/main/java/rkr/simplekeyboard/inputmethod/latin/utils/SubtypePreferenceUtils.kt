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
package rkr.simplekeyboard.inputmethod.latin.utils

import android.content.res.Resources
import android.text.TextUtils
import android.util.Log
import rkr.simplekeyboard.inputmethod.latin.Subtype

object SubtypePreferenceUtils {
    private val TAG: String = SubtypePreferenceUtils::class.java.simpleName

    private const val LOCALE_AND_LAYOUT_SEPARATOR = ":"
    private const val INDEX_OF_LOCALE = 0
    private const val INDEX_OF_KEYBOARD_LAYOUT = 1
    private const val PREF_ELEMENTS_LENGTH = (INDEX_OF_KEYBOARD_LAYOUT + 1)
    private const val PREF_SUBTYPE_SEPARATOR = ";"

    private fun getPrefString(subtype: Subtype): String {
        val localeString = subtype.locale
        val keyboardLayoutSetName = subtype.keyboardLayoutSet
        return localeString + LOCALE_AND_LAYOUT_SEPARATOR + keyboardLayoutSetName
    }

    fun createSubtypesFromPref(
        prefSubtypes: String,
        resources: Resources
    ): MutableList<Subtype> {
        if (TextUtils.isEmpty(prefSubtypes)) {
            return ArrayList()
        }
        val prefSubtypeArray =
            prefSubtypes.split(PREF_SUBTYPE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        val subtypesList = ArrayList<Subtype>(prefSubtypeArray.size)
        for (prefSubtype in prefSubtypeArray) {
            val elements = prefSubtype.split(LOCALE_AND_LAYOUT_SEPARATOR.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
            if (elements.size != PREF_ELEMENTS_LENGTH) {
                Log.w(
                    TAG, ("Unknown subtype specified: " + prefSubtype + " in "
                            + prefSubtypes)
                )
                continue
            }
            val localeString = elements[INDEX_OF_LOCALE]
            val keyboardLayoutSetName = elements[INDEX_OF_KEYBOARD_LAYOUT]
            val subtype =
                SubtypeLocaleUtils.getSubtype(localeString, keyboardLayoutSetName, resources)
                    ?: continue
            subtypesList.add(subtype)
        }
        return subtypesList
    }

    fun createPrefSubtypes(subtypes: List<Subtype>?): String {
        if (subtypes == null || subtypes.size == 0) {
            return ""
        }
        val sb = StringBuilder()
        for (subtype in subtypes) {
            if (sb.length > 0) {
                sb.append(PREF_SUBTYPE_SEPARATOR)
            }
            sb.append(getPrefString(subtype))
        }
        return sb.toString()
    }
}
