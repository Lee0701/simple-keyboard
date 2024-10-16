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

import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.view.inputmethod.EditorInfo
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.InputAttributes

// Non-final for testing via mock library.
class SettingsValues(
    prefs: SharedPreferences, res: Resources,
    inputAttributes: InputAttributes?
) {
    // From resources:
    // Get the resources
    val mSpacingAndPunctuations: SpacingAndPunctuations = SpacingAndPunctuations(res)

    // From configuration:
    val mHasHardwareKeyboard: Boolean =
        Settings.readHasHardwareKeyboard(res.configuration)
    val mDisplayOrientation: Int
    // From preferences, in the same order as xml/prefs.xml:

    // Get the settings preferences
    val mAutoCap: Boolean = prefs.getBoolean(Settings.PREF_AUTO_CAP, true)
    val mVibrateOn: Boolean = Settings.readVibrationEnabled(prefs, res)
    val mSoundOn: Boolean = Settings.readKeypressSoundEnabled(prefs, res)
    val mKeyPreviewPopupOn: Boolean = Settings.readKeyPreviewPopupEnabled(prefs, res)
    val mShowsLanguageSwitchKey: Boolean = Settings.readShowLanguageSwitchKey(prefs)
    val mImeSwitchEnabled: Boolean = Settings.readEnableImeSwitch(prefs)

    // Compute other readable settings
    val mKeyLongpressTimeout: Int = Settings.readKeyLongpressTimeout(prefs, res)
    val mHideSpecialChars: Boolean
    val mShowNumberRow: Boolean
    val mSpaceSwipeEnabled: Boolean
    val mDeleteSwipeEnabled: Boolean
    val mUseMatchingNavbarColor: Boolean

    // From the input box

    // Store the input attributes
    val mInputAttributes: InputAttributes? = inputAttributes

    // Deduced settings
    val mKeypressVibrationDuration: Int =
        Settings.readKeypressVibrationDuration(prefs, res)
    val mKeypressSoundVolume: Float = Settings.readKeypressSoundVolume(prefs, res)
    val mKeyPreviewPopupDismissDelay: Int =
        res.getInteger(R.integer.config_key_preview_linger_timeout)

    // Debug settings
    val mKeyboardHeightScale: Float

    val mBottomOffsetPortrait: Int

    init {
        mKeyboardHeightScale = Settings.readKeyboardHeight(prefs, DEFAULT_SIZE_SCALE)
        mBottomOffsetPortrait = Settings.readBottomOffsetPortrait(prefs)
        mDisplayOrientation = res.configuration.orientation
        mHideSpecialChars = Settings.readHideSpecialChars(prefs)
        mShowNumberRow = Settings.readShowNumberRow(prefs)
        mSpaceSwipeEnabled = Settings.readSpaceSwipeEnabled(prefs)
        mDeleteSwipeEnabled = Settings.readDeleteSwipeEnabled(prefs)
        mUseMatchingNavbarColor = Settings.readUseMatchingNavbarColor(prefs)
    }

    fun isWordSeparator(code: Int): Boolean {
        return mSpacingAndPunctuations.isWordSeparator(code)
    }

    val isLanguageSwitchKeyDisabled: Boolean
        get() = !mShowsLanguageSwitchKey

    fun isSameInputType(editorInfo: EditorInfo): Boolean {
        return mInputAttributes!!.isSameInputType(editorInfo)
    }

    fun hasSameOrientation(configuration: Configuration): Boolean {
        return mDisplayOrientation == configuration.orientation
    }

    companion object {
        const val DEFAULT_SIZE_SCALE: Float = 1.0f // 100%
    }
}
