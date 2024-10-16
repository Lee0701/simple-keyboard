/*
 * Copyright (C) 2015 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.keyboard

import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import rkr.simplekeyboard.inputmethod.compat.EditorInfoCompatUtils
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.utils.InputTypeUtils
import java.util.Locale

/**
 * Unique identifier for each keyboard type.
 */
class KeyboardId(elementId: Int, params: KeyboardLayoutSet.Params) {
    val mSubtype: Subtype?
    val mThemeId: Int
    val mWidth: Int
    val mHeight: Int
    val mBottomOffset: Int
    val mMode: Int
    val mElementId: Int
    val mEditorInfo: EditorInfo?
    val mClobberSettingsKey: Boolean
    val mLanguageSwitchKeyEnabled: Boolean
    val mCustomActionLabel: String?
    val mShowMoreKeys: Boolean
    val mShowNumberRow: Boolean

    private val mHashCode: Int

    init {
        mSubtype = params.mSubtype
        mThemeId = params.mKeyboardThemeId
        mWidth = params.mKeyboardWidth
        mHeight = params.mKeyboardHeight
        mBottomOffset = params.mKeyboardBottomOffset
        mMode = params.mMode
        mElementId = elementId
        mEditorInfo = params.mEditorInfo
        mClobberSettingsKey = params.mNoSettingsKey
        mLanguageSwitchKeyEnabled = params.mLanguageSwitchKeyEnabled
        mCustomActionLabel = if ((mEditorInfo!!.actionLabel != null))
            mEditorInfo.actionLabel.toString()
        else
            null
        mShowMoreKeys = params.mShowMoreKeys
        mShowNumberRow = params.mShowNumberRow

        mHashCode = computeHashCode(this)
    }

    private fun equals(other: KeyboardId): Boolean {
        if (other === this) return true
        return other.mElementId == mElementId && other.mMode == mMode && other.mWidth == mWidth && other.mHeight == mHeight && other.mBottomOffset == mBottomOffset && other.passwordInput() == passwordInput() && other.mClobberSettingsKey == mClobberSettingsKey && other.mLanguageSwitchKeyEnabled == mLanguageSwitchKeyEnabled && other.isMultiLine == isMultiLine && other.imeAction() == imeAction() && TextUtils.equals(
            other.mCustomActionLabel,
            mCustomActionLabel
        )
                && other.navigateNext() == navigateNext() && other.navigatePrevious() == navigatePrevious() && other.mSubtype == mSubtype
                && other.mThemeId == mThemeId
    }

    val isAlphabetKeyboard: Boolean
        get() {
            return isAlphabetKeyboard(mElementId)
        }

    fun navigateNext(): Boolean {
        return (mEditorInfo!!.imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0
                || imeAction() == EditorInfo.IME_ACTION_NEXT
    }

    fun navigatePrevious(): Boolean {
        return (mEditorInfo!!.imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0
                || imeAction() == EditorInfo.IME_ACTION_PREVIOUS
    }

    fun passwordInput(): Boolean {
        val inputType: Int = mEditorInfo!!.inputType
        return InputTypeUtils.isPasswordInputType(inputType)
                || InputTypeUtils.isVisiblePasswordInputType(inputType)
    }

    val isMultiLine: Boolean
        get() {
            return (mEditorInfo!!.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        }

    fun imeAction(): Int {
        return InputTypeUtils.getImeOptionsActionIdFromEditorInfo(mEditorInfo!!)
    }

    val locale: Locale?
        get() {
            return mSubtype.getLocaleObject()
        }

    override fun equals(other: Any?): Boolean {
        return other is KeyboardId && equals(other)
    }

    override fun hashCode(): Int {
        return mHashCode
    }

    override fun toString(): String {
        return String.format(
            Locale.ROOT, "[%s %s:%s %dx%d +%d %s %s%s%s%s%s%s %s]",
            elementIdToName(mElementId),
            mSubtype.getLocale(),
            mSubtype.getKeyboardLayoutSet(),
            mWidth, mHeight, mBottomOffset,
            modeName(mMode),
            actionName(imeAction()),
            (if (navigateNext()) " navigateNext" else ""),
            (if (navigatePrevious()) " navigatePrevious" else ""),
            (if (mClobberSettingsKey) " clobberSettingsKey" else ""),
            (if (passwordInput()) " passwordInput" else ""),
            (if (mLanguageSwitchKeyEnabled) " languageSwitchKeyEnabled" else ""),
            (if (isMultiLine) " isMultiLine" else ""),
            KeyboardTheme.Companion.getKeyboardThemeName(mThemeId)
        )
    }

    companion object {
        const val MODE_TEXT: Int = 0
        const val MODE_URL: Int = 1
        const val MODE_EMAIL: Int = 2
        const val MODE_IM: Int = 3
        const val MODE_PHONE: Int = 4
        const val MODE_NUMBER: Int = 5
        const val MODE_DATE: Int = 6
        const val MODE_TIME: Int = 7
        const val MODE_DATETIME: Int = 8

        const val ELEMENT_ALPHABET: Int = 0
        const val ELEMENT_ALPHABET_MANUAL_SHIFTED: Int = 1
        const val ELEMENT_ALPHABET_AUTOMATIC_SHIFTED: Int = 2
        const val ELEMENT_ALPHABET_SHIFT_LOCKED: Int = 3
        const val ELEMENT_SYMBOLS: Int = 5
        const val ELEMENT_SYMBOLS_SHIFTED: Int = 6
        const val ELEMENT_PHONE: Int = 7
        const val ELEMENT_PHONE_SYMBOLS: Int = 8
        const val ELEMENT_NUMBER: Int = 9

        private fun computeHashCode(id: KeyboardId): Int {
            return arrayOf(
                id.mElementId,
                id.mMode,
                id.mWidth,
                id.mHeight,
                id.mBottomOffset,
                id.passwordInput(),
                id.mClobberSettingsKey,
                id.mLanguageSwitchKeyEnabled,
                id.isMultiLine,
                id.imeAction(),
                id.mCustomActionLabel,
                id.navigateNext(),
                id.navigatePrevious(),
                id.mSubtype,
                id.mThemeId
            ).contentHashCode()
        }

        private fun isAlphabetKeyboard(elementId: Int): Boolean {
            return elementId < ELEMENT_SYMBOLS
        }

        fun equivalentEditorInfoForKeyboard(a: EditorInfo?, b: EditorInfo?): Boolean {
            if (a == null && b == null) return true
            if (a == null || b == null) return false
            return a.inputType == b.inputType && a.imeOptions == b.imeOptions && TextUtils.equals(
                a.privateImeOptions,
                b.privateImeOptions
            )
        }

        fun elementIdToName(elementId: Int): String? {
            when (elementId) {
                ELEMENT_ALPHABET -> return "alphabet"
                ELEMENT_ALPHABET_MANUAL_SHIFTED -> return "alphabetManualShifted"
                ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> return "alphabetAutomaticShifted"
                ELEMENT_ALPHABET_SHIFT_LOCKED -> return "alphabetShiftLocked"
                ELEMENT_SYMBOLS -> return "symbols"
                ELEMENT_SYMBOLS_SHIFTED -> return "symbolsShifted"
                ELEMENT_PHONE -> return "phone"
                ELEMENT_PHONE_SYMBOLS -> return "phoneSymbols"
                ELEMENT_NUMBER -> return "number"
                else -> return null
            }
        }

        fun modeName(mode: Int): String? {
            when (mode) {
                MODE_TEXT -> return "text"
                MODE_URL -> return "url"
                MODE_EMAIL -> return "email"
                MODE_IM -> return "im"
                MODE_PHONE -> return "phone"
                MODE_NUMBER -> return "number"
                MODE_DATE -> return "date"
                MODE_TIME -> return "time"
                MODE_DATETIME -> return "datetime"
                else -> return null
            }
        }

        fun actionName(actionId: Int): String {
            return if ((actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL))
                "actionCustomLabel"
            else
                EditorInfoCompatUtils.imeActionName(actionId)
        }
    }
}
