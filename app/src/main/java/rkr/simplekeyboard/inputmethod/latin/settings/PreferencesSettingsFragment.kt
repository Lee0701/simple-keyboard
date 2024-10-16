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

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.SwitchPreference
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardLayoutSet

/**
 * "Preferences" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Auto-capitalization
 * - Show separate number row
 * - Hide special characters
 * - Hide language switch key
 * - Switch to other keyboards
 * - Space swipe cursor move
 * - Delete swipe
 */
class PreferencesSettingsFragment : SubScreenFragment() {
    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_preferences)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            removePreference(Settings.Companion.PREF_ENABLE_IME_SWITCH)
        } else {
            updateImeSwitchEnabledPref()
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        if (key == Settings.Companion.PREF_HIDE_SPECIAL_CHARS ||
            key == Settings.Companion.PREF_SHOW_NUMBER_ROW
        ) {
            KeyboardLayoutSet.Companion.onKeyboardThemeChanged()
        } else if (key == Settings.Companion.PREF_HIDE_LANGUAGE_SWITCH_KEY) {
            updateImeSwitchEnabledPref()
        }
    }

    /**
     * Enable the preference for switching IMEs only when the preference is set to not hide the
     * language switch key.
     */
    private fun updateImeSwitchEnabledPref() {
        val enableImeSwitch = findPreference(Settings.Companion.PREF_ENABLE_IME_SWITCH)
        val hideLanguageSwitchKey =
            findPreference(Settings.Companion.PREF_HIDE_LANGUAGE_SWITCH_KEY)
        if (enableImeSwitch == null || hideLanguageSwitchKey == null) {
            return
        }
        // depending on the version of Android, the preferences could be different types
        val hideLanguageSwitchKeyIsChecked = if (hideLanguageSwitchKey is CheckBoxPreference) {
            hideLanguageSwitchKey.isChecked
        } else if (hideLanguageSwitchKey is SwitchPreference) {
            hideLanguageSwitchKey.isChecked
        } else {
            // in case it can be something else, don't bother doing anything
            return
        }
        enableImeSwitch.isEnabled = !hideLanguageSwitchKeyIsChecked
    }
}
