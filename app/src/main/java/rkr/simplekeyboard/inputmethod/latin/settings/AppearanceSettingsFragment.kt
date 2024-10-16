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
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment() {
    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference(Settings.Companion.PREF_MATCHING_NAVBAR_COLOR)
        }

        setupKeyboardHeightSettings()
        setupBottomOffsetPortraitSettings()
        setupKeyboardColorSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        refreshSettings()
    }

    private fun refreshSettings() {
        ThemeSettingsFragment.Companion.updateKeyboardThemeSummary(findPreference(Settings.Companion.SCREEN_THEME))

        val prefs = sharedPreferences
        val theme: KeyboardTheme = KeyboardTheme.Companion.getKeyboardTheme(prefs!!)
        val isSystemTheme = theme.mThemeId != KeyboardTheme.Companion.THEME_ID_SYSTEM
                && theme.mThemeId != KeyboardTheme.Companion.THEME_ID_SYSTEM_BORDER
        setPreferenceEnabled(Settings.Companion.PREF_KEYBOARD_COLOR, isSystemTheme)
    }

    private fun setupKeyboardHeightSettings() {
        val pref = findPreference(
            Settings.Companion.PREF_KEYBOARD_HEIGHT
        ) as SeekBarDialogPreference
        if (pref == null) {
            return
        }
        val prefs = sharedPreferences
        val res = resources
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            private val PERCENTAGE_FLOAT = 100.0f

            fun getValueFromPercentage(percentage: Int): Float {
                return percentage / PERCENTAGE_FLOAT
            }

            fun getPercentageFromValue(floatValue: Float): Int {
                return Math.round(floatValue * PERCENTAGE_FLOAT)
            }

            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putFloat(key, getValueFromPercentage(value)).apply()
            }

            override fun writeDefaultValue(key: String?) {
                prefs!!.edit().remove(key).apply()
            }

            override fun readValue(key: String?): Int {
                return getPercentageFromValue(
                    Settings.Companion.readKeyboardHeight(
                        prefs!!, 1f
                    )
                )
            }

            override fun readDefaultValue(key: String?): Int {
                return getPercentageFromValue(1f)
            }

            override fun getValueText(value: Int): String {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default)
                }
                return res.getString(R.string.abbreviation_unit_percent, value)
            }

            override fun feedbackValue(value: Int) {}
        })
    }

    private fun setupBottomOffsetPortraitSettings() {
        val pref = findPreference(
            Settings.Companion.PREF_BOTTOM_OFFSET_PORTRAIT
        ) as SeekBarDialogPreference
        if (pref == null) {
            return
        }
        val prefs = sharedPreferences
        val res = resources
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putInt(key, value).apply()
            }

            override fun writeDefaultValue(key: String?) {
                prefs!!.edit().remove(key).apply()
            }

            override fun readValue(key: String?): Int {
                return Settings.Companion.readBottomOffsetPortrait(
                    prefs!!
                )
            }

            override fun readDefaultValue(key: String?): Int {
                return Settings.Companion.DEFAULT_BOTTOM_OFFSET
            }

            override fun getValueText(value: Int): String {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default)
                }
                return res.getString(R.string.abbreviation_unit_dp, value)
            }

            override fun feedbackValue(value: Int) {}
        })
    }

    private fun setupKeyboardColorSettings() {
        val pref = findPreference(
            Settings.Companion.PREF_KEYBOARD_COLOR
        ) as ColorDialogPreference
        if (pref == null) {
            return
        }
        val prefs = sharedPreferences
        val context = this.activity.applicationContext
        pref.setInterface(object : ColorDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putInt(key, value).apply()
            }

            override fun readValue(key: String?): Int {
                return Settings.Companion.readKeyboardColor(
                    prefs!!, context
                )
            }

            override fun writeDefaultValue(key: String?) {
                Settings.Companion.removeKeyboardColor(
                    prefs!!
                )
            }
        })
    }
}
