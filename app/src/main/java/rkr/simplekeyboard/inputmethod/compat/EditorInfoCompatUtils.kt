/*
 * Copyright (C) 2011 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.compat

import android.os.Build
import android.os.LocaleList
import android.view.inputmethod.EditorInfo
import java.util.Locale

object EditorInfoCompatUtils {
    fun imeActionName(imeOptions: Int): String {
        val actionId: Int = imeOptions and EditorInfo.IME_MASK_ACTION
        when (actionId) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> return "actionUnspecified"
            EditorInfo.IME_ACTION_NONE -> return "actionNone"
            EditorInfo.IME_ACTION_GO -> return "actionGo"
            EditorInfo.IME_ACTION_SEARCH -> return "actionSearch"
            EditorInfo.IME_ACTION_SEND -> return "actionSend"
            EditorInfo.IME_ACTION_NEXT -> return "actionNext"
            EditorInfo.IME_ACTION_DONE -> return "actionDone"
            EditorInfo.IME_ACTION_PREVIOUS -> return "actionPrevious"
            else -> return "actionUnknown(" + actionId + ")"
        }
    }

    fun getPrimaryHintLocale(editorInfo: EditorInfo?): Locale? {
        if (editorInfo == null) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList: LocaleList? = editorInfo.hintLocales
            if (localeList != null && !localeList.isEmpty()) return localeList.get(0)
        }
        return null
    }
}
