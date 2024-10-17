/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme
import rkr.simplekeyboard.inputmethod.latin.AudioAndHapticFeedbackManager
import rkr.simplekeyboard.inputmethod.latin.InputAttributes
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils
import java.util.concurrent.locks.ReentrantLock

object Settings : OnSharedPreferenceChangeListener {
    private var mRes: Resources? = null
    private var mPrefs: SharedPreferences? = null

    // TODO: Remove this method and add proxy method to SettingsValues.
    var current: SettingsValues? = null
        private set
    private val mSettingsValuesLock = ReentrantLock()

    fun init(context: Context) {
        mRes = context.resources
        mPrefs = PreferenceManagerCompat.getDeviceSharedPreferences(context)
        mPrefs!!.registerOnSharedPreferenceChangeListener(this)
    }

    fun onDestroy() {
        mPrefs!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        mSettingsValuesLock.lock()
        try {
            if (current == null) {
                // TODO: Introduce a static function to register this class and ensure that
                // loadSettings must be called before "onSharedPreferenceChanged" is called.
                Log.w(TAG, "onSharedPreferenceChanged called before loadSettings.")
                return
            }
            loadSettings(current!!.mInputAttributes)
        } finally {
            mSettingsValuesLock.unlock()
        }
    }

    fun loadSettings(inputAttributes: InputAttributes?) {
        current = SettingsValues(mPrefs!!, mRes!!, inputAttributes)
    }

    private val TAG: String = Settings::class.java.simpleName

    // Settings screens
    const val SCREEN_THEME: String = "screen_theme"

    // In the same order as xml/prefs.xml
    const val PREF_AUTO_CAP: String = "auto_cap"
    const val PREF_VIBRATE_ON: String = "vibrate_on"
    const val PREF_SOUND_ON: String = "sound_on"
    const val PREF_POPUP_ON: String = "popup_on"
    const val PREF_HIDE_LANGUAGE_SWITCH_KEY: String = "pref_hide_language_switch_key"
    const val PREF_ENABLE_IME_SWITCH: String = "pref_enable_ime_switch"
    const val PREF_ENABLED_SUBTYPES: String = "pref_enabled_subtypes"
    const val PREF_VIBRATION_DURATION_SETTINGS: String = "pref_vibration_duration_settings"
    const val PREF_KEYPRESS_SOUND_VOLUME: String = "pref_keypress_sound_volume"
    const val PREF_KEY_LONGPRESS_TIMEOUT: String = "pref_key_longpress_timeout"
    const val PREF_KEYBOARD_HEIGHT: String = "pref_keyboard_height"
    const val PREF_BOTTOM_OFFSET_PORTRAIT: String = "pref_bottom_offset_portrait"
    const val PREF_KEYBOARD_COLOR: String = "pref_keyboard_color"
    const val PREF_HIDE_SPECIAL_CHARS: String = "pref_hide_special_chars"
    const val PREF_SHOW_NUMBER_ROW: String = "pref_show_number_row"
    const val PREF_SPACE_SWIPE: String = "pref_space_swipe"
    const val PREF_DELETE_SWIPE: String = "pref_delete_swipe"
    const val PREF_MATCHING_NAVBAR_COLOR: String = "pref_matching_navbar_color"

    private const val UNDEFINED_PREFERENCE_VALUE_FLOAT = -1.0f
    private const val UNDEFINED_PREFERENCE_VALUE_INT = -1

    // Accessed from the settings interface, hence public
    fun readKeypressSoundEnabled(
        prefs: SharedPreferences,
        res: Resources
    ): Boolean {
        return prefs.getBoolean(
            PREF_SOUND_ON,
            res.getBoolean(R.bool.config_default_sound_enabled)
        )
    }

    fun readVibrationEnabled(
        prefs: SharedPreferences,
        res: Resources
    ): Boolean {
        val hasVibrator: Boolean =
            AudioAndHapticFeedbackManager.hasVibrator()
        return hasVibrator && prefs.getBoolean(
            PREF_VIBRATE_ON,
            res.getBoolean(R.bool.config_default_vibration_enabled)
        )
    }

    fun readKeyPreviewPopupEnabled(
        prefs: SharedPreferences,
        res: Resources
    ): Boolean {
        val defaultKeyPreviewPopup = res.getBoolean(
            R.bool.config_default_key_preview_popup
        )
        return prefs.getBoolean(PREF_POPUP_ON, defaultKeyPreviewPopup)
    }

    fun readShowLanguageSwitchKey(prefs: SharedPreferences): Boolean {
        return !prefs.getBoolean(PREF_HIDE_LANGUAGE_SWITCH_KEY, false)
    }

    fun readEnableImeSwitch(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_ENABLE_IME_SWITCH, false)
    }

    fun readHideSpecialChars(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_HIDE_SPECIAL_CHARS, false)
    }

    fun readShowNumberRow(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_SHOW_NUMBER_ROW, false)
    }

    fun readSpaceSwipeEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_SPACE_SWIPE, false)
    }

    fun readDeleteSwipeEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_DELETE_SWIPE, false)
    }

    fun readPrefSubtypes(prefs: SharedPreferences): String {
        return prefs.getString(PREF_ENABLED_SUBTYPES, "")!!
    }

    fun writePrefSubtypes(prefs: SharedPreferences, prefSubtypes: String?) {
        prefs.edit().putString(PREF_ENABLED_SUBTYPES, prefSubtypes).apply()
    }

    fun readKeypressSoundVolume(
        prefs: SharedPreferences,
        res: Resources
    ): Float {
        val volume = prefs.getFloat(
            PREF_KEYPRESS_SOUND_VOLUME, UNDEFINED_PREFERENCE_VALUE_FLOAT
        )
        return if ((volume != UNDEFINED_PREFERENCE_VALUE_FLOAT))
            volume
        else
            readDefaultKeypressSoundVolume(res)
    }

    // Default keypress sound volume for unknown devices.
    // The negative value means system default.
    private val DEFAULT_KEYPRESS_SOUND_VOLUME: String = (-1.0f).toString()

    fun readDefaultKeypressSoundVolume(res: Resources): Float {
        return ResourceUtils.getDeviceOverrideValue(
            res,
            R.array.keypress_volumes,
            DEFAULT_KEYPRESS_SOUND_VOLUME
        )!!.toFloat()
    }

    fun readKeyLongpressTimeout(
        prefs: SharedPreferences,
        res: Resources
    ): Int {
        val milliseconds = prefs.getInt(
            PREF_KEY_LONGPRESS_TIMEOUT, UNDEFINED_PREFERENCE_VALUE_INT
        )
        return if ((milliseconds != UNDEFINED_PREFERENCE_VALUE_INT))
            milliseconds
        else
            readDefaultKeyLongpressTimeout(res)
    }

    fun readDefaultKeyLongpressTimeout(res: Resources): Int {
        return res.getInteger(R.integer.config_default_longpress_key_timeout)
    }

    fun readKeypressVibrationDuration(
        prefs: SharedPreferences,
        res: Resources
    ): Int {
        val milliseconds = prefs.getInt(
            PREF_VIBRATION_DURATION_SETTINGS, UNDEFINED_PREFERENCE_VALUE_INT
        )
        return if ((milliseconds != UNDEFINED_PREFERENCE_VALUE_INT))
            milliseconds
        else
            readDefaultKeypressVibrationDuration(res)
    }

    // Default keypress vibration duration for unknown devices.
    // The negative value means system default.
    private val DEFAULT_KEYPRESS_VIBRATION_DURATION: String = (-1).toString()

    fun readDefaultKeypressVibrationDuration(res: Resources): Int {
        return ResourceUtils.getDeviceOverrideValue(
            res,
            R.array.keypress_vibration_durations,
            DEFAULT_KEYPRESS_VIBRATION_DURATION
        )!!.toInt()
    }

    fun readKeyboardHeight(
        prefs: SharedPreferences,
        defaultValue: Float
    ): Float {
        return prefs.getFloat(PREF_KEYBOARD_HEIGHT, defaultValue)
    }

    fun readBottomOffsetPortrait(prefs: SharedPreferences): Int {
        return prefs.getInt(PREF_BOTTOM_OFFSET_PORTRAIT, DEFAULT_BOTTOM_OFFSET)
    }

    const val DEFAULT_BOTTOM_OFFSET: Int = 0

    fun readKeyboardDefaultColor(context: Context): Int {
        val keyboardThemeColors = context.resources.getIntArray(R.array.keyboard_theme_colors)
        val keyboardThemeIds = context.resources.getIntArray(R.array.keyboard_theme_ids)
        val themeId: Int = KeyboardTheme.getKeyboardTheme(context)!!.mThemeId
        for (index in keyboardThemeIds.indices) {
            if (themeId == keyboardThemeIds[index]) {
                return keyboardThemeColors[index]
            }
        }

        return Color.LTGRAY
    }

    fun readKeyboardColor(prefs: SharedPreferences, context: Context): Int {
        return prefs.getInt(PREF_KEYBOARD_COLOR, readKeyboardDefaultColor(context))
    }

    fun removeKeyboardColor(prefs: SharedPreferences) {
        prefs.edit().remove(PREF_KEYBOARD_COLOR).apply()
    }

    fun readUseFullscreenMode(res: Resources): Boolean {
        return res.getBoolean(R.bool.config_use_fullscreen_mode)
    }

    fun readHasHardwareKeyboard(conf: Configuration): Boolean {
        // The standard way of finding out whether we have a hardware keyboard. This code is taken
        // from InputMethodService#onEvaluateInputShown, which canonically determines this.
        // In a nutshell, we have a keyboard if the configuration says the type of hardware keyboard
        // is NOKEYS and if it's not hidden (e.g. folded inside the device).
        return conf.keyboard != Configuration.KEYBOARD_NOKEYS
                && conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    }

    fun readUseMatchingNavbarColor(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_MATCHING_NAVBAR_COLOR, false)
    }
}
