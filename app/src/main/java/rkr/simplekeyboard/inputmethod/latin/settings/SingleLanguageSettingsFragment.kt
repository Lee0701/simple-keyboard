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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.content.Context
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.SwitchPreference
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils

/**
 * Settings sub screen for a specific language.
 */
class SingleLanguageSettingsFragment : PreferenceFragment() {
    private var mRichImm: RichInputMethodManager? = null
    private var mSubtypePreferences: MutableList<SubtypePreference>? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        RichInputMethodManager.Companion.init(activity)
        mRichImm = RichInputMethodManager.Companion.getInstance()
        addPreferencesFromResource(R.xml.empty_settings)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val context: Context = activity

        val args = arguments
        if (args != null) {
            val locale = arguments.getString(LOCALE_BUNDLE_KEY)
            buildContent(locale, context)
        }

        super.onActivityCreated(savedInstanceState)
    }

    /**
     * Build the preferences and them to this settings screen.
     * @param locale the locale string of the locale to display content for.
     * @param context the context for this application.
     */
    private fun buildContent(locale: String?, context: Context) {
        if (locale == null) {
            return
        }
        val group: PreferenceGroup = preferenceScreen
        group.removeAll()

        val mainCategory = PreferenceCategory(context)
        val localeName = LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(locale)
        mainCategory.title = context.getString(R.string.generic_language_layouts, localeName)
        group.addPreference(mainCategory)

        buildSubtypePreferences(locale, group, context)
    }

    /**
     * Build preferences for all of the available subtypes for a locale and them to the settings
     * screen.
     * @param locale the locale string of the locale to add subtypes for.
     * @param group the preference group to add preferences to.
     * @param context the context for this application.
     */
    private fun buildSubtypePreferences(
        locale: String, group: PreferenceGroup,
        context: Context
    ) {
        val enabledSubtypes = mRichImm!!.getEnabledSubtypes(false)
        val subtypes =
            SubtypeLocaleUtils.getSubtypes(locale, context.resources)
        mSubtypePreferences = ArrayList()
        for (subtype in subtypes!!) {
            val isChecked = enabledSubtypes.contains(subtype)
            val pref = createSubtypePreference(subtype, isChecked, context)
            group.addPreference(pref)
            mSubtypePreferences.add(pref)
        }

        // if there is only one subtype that is checked, the preference for it should be disabled to
        // prevent all of the subtypes for the language from being removed
        val checkedPrefs =
            checkedSubtypePreferences
        if (checkedPrefs.size == 1) {
            checkedPrefs[0].isEnabled = false
        }
    }

    /**
     * Create a preference for a keyboard layout subtype.
     * @param subtype the subtype that the preference enables.
     * @param checked whether the preference should be initially checked.
     * @param context the context for this application.
     * @return the preference that was created.
     */
    private fun createSubtypePreference(
        subtype: Subtype,
        checked: Boolean,
        context: Context
    ): SubtypePreference {
        val pref = SubtypePreference(context, subtype)
        pref.title = subtype.layoutDisplayName
        pref.isChecked = checked

        pref.onPreferenceChangeListener = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
                if (newValue !is Boolean) {
                    return false
                }
                val pref = preference as SubtypePreference
                val checkedPrefs: List<SubtypePreference> =
                    this.checkedSubtypePreferences
                if (checkedPrefs.size == 1) {
                    checkedPrefs[0].isEnabled = false
                }
                if (newValue) {
                    val added = mRichImm!!.addSubtype(pref.getSubtype())
                    // if only one subtype was checked before, the preference would have been
                    // disabled, but now that there are two, it can be enabled to allow it to be
                    // unchecked
                    if (added && checkedPrefs.size == 1) {
                        checkedPrefs[0].isEnabled = true
                    }
                    return added
                } else {
                    val removed = mRichImm!!.removeSubtype(pref.getSubtype())
                    // if there is going to be only one subtype that is checked, the preference for
                    // it should be disabled to prevent all of the subtypes for the language from
                    // being removed
                    if (removed && checkedPrefs.size == 2) {
                        val onlyCheckedPref = if (checkedPrefs[0] == pref) {
                            checkedPrefs[1]
                        } else {
                            checkedPrefs[0]
                        }
                        onlyCheckedPref.isEnabled = false
                    }
                    return removed
                }
            }
        }

        return pref
    }

    private val checkedSubtypePreferences: List<SubtypePreference>
        /**
         * Get a list of all of the subtype preferences that are currently checked.
         * @return a list of all of the subtype preferences that are checked.
         */
        get() {
            val prefs: MutableList<SubtypePreference> =
                ArrayList()
            for (pref in mSubtypePreferences!!) {
                if (pref.isChecked) {
                    prefs.add(pref)
                }
            }
            return prefs
        }

    /**
     * Preference for a keyboard layout.
     */
    private class SubtypePreference(
        context: Context?,
        /**
         * Get the subtype that this preference represents.
         * @return the subtype.
         */
        val subtype: Subtype
    ) :
        SwitchPreference(context) {
    }

    companion object {
        const val LOCALE_BUNDLE_KEY: String = "LOCALE"
    }
}
