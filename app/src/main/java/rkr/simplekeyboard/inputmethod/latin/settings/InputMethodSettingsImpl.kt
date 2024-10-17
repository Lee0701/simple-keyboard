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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceScreen
import android.text.TextUtils
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager

/* package private */
internal class InputMethodSettingsImpl {
    private var mSubtypeEnablerPreference: Preference? = null
    private var mRichImm: RichInputMethodManager? = null

    /**
     * Initialize internal states of this object.
     * @param context the context for this application.
     * @param prefScreen a PreferenceScreen of PreferenceActivity or PreferenceFragment.
     * @return true if this application is an IME and has two or more subtypes, false otherwise.
     */
    fun init(context: Context, prefScreen: PreferenceScreen): Boolean {
        RichInputMethodManager.init(context)
        mRichImm = RichInputMethodManager

        mSubtypeEnablerPreference = Preference(context)
        mSubtypeEnablerPreference!!.setTitle(R.string.select_language)
        mSubtypeEnablerPreference!!.fragment = LanguagesSettingsFragment::class.java.name
        prefScreen.addPreference(mSubtypeEnablerPreference)
        updateEnabledSubtypeList()
        return true
    }

    fun updateEnabledSubtypeList() {
        if (mSubtypeEnablerPreference != null) {
            val summary = getEnabledSubtypesLabel(mRichImm)
            if (!TextUtils.isEmpty(summary)) {
                mSubtypeEnablerPreference!!.summary = summary
            }
        }
    }

    companion object {
        private fun getEnabledSubtypesLabel(richImm: RichInputMethodManager?): String? {
            if (richImm == null) {
                return null
            }

            val subtypes = richImm.getEnabledSubtypes(true)

            val sb = StringBuilder()
            for (subtype in subtypes) {
                if (sb.length > 0) {
                    sb.append(", ")
                }
                sb.append(subtype.name)
            }
            return sb.toString()
        }
    }
}
