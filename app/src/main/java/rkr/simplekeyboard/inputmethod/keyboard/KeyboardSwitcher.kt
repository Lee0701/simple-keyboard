/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.event.Event
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardState
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardState.SwitchActions
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardTextsSet
import rkr.simplekeyboard.inputmethod.latin.InputView
import rkr.simplekeyboard.inputmethod.latin.LatinIME
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.settings.Settings
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues
import rkr.simplekeyboard.inputmethod.latin.utils.CapsModeUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LanguageOnSpacebarUtils
import rkr.simplekeyboard.inputmethod.latin.utils.RecapitalizeStatus
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils

class KeyboardSwitcher private constructor() : SwitchActions {
    private var mCurrentInputView: InputView? = null
    private var mCurrentUiMode: Int = 0
    private var mCurrentTextColor: Int = 0x0
    private var mMainKeyboardFrame: View? = null
    var mainKeyboardView: MainKeyboardView? = null
        private set
    private var mLatinIME: LatinIME? = null
    private var mRichImm: RichInputMethodManager? = null

    private var mState: KeyboardState? = null

    private var mKeyboardLayoutSet: KeyboardLayoutSet? = null

    // TODO: The following {@link KeyboardTextsSet} should be in {@link KeyboardLayoutSet}.
    private val mKeyboardTextsSet: KeyboardTextsSet = KeyboardTextsSet()

    private var mKeyboardTheme: KeyboardTheme? = null
    private var mThemeContext: Context? = null

    private fun initInternal(latinIme: LatinIME) {
        mLatinIME = latinIme
        mRichImm = RichInputMethodManager.instance
        mState = KeyboardState(this)
    }

    fun updateKeyboardTheme(uiMode: Int) {
        val themeUpdated: Boolean = updateKeyboardThemeAndContextThemeWrapper(
            mLatinIME!!, KeyboardTheme.getKeyboardTheme(mLatinIME)!!, uiMode
        )
        if (themeUpdated && mainKeyboardView != null) {
            mLatinIME!!.setInputView(onCreateInputView(uiMode)!!)
        }
    }

    private fun updateKeyboardThemeAndContextThemeWrapper(
        context: Context,
        keyboardTheme: KeyboardTheme, uiMode: Int
    ): Boolean {
        var newTextColor: Int = 0x0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            newTextColor = context.getResources().getColor(R.color.key_text_color_lxx_system)
        }

        if (mThemeContext == null || keyboardTheme != mKeyboardTheme || mCurrentUiMode != uiMode || newTextColor != mCurrentTextColor) {
            mKeyboardTheme = keyboardTheme
            mCurrentUiMode = uiMode
            mCurrentTextColor = newTextColor
            mThemeContext = ContextThemeWrapper(context, keyboardTheme.mStyleId)
            KeyboardLayoutSet.onKeyboardThemeChanged()
            return true
        }
        return false
    }

    fun loadKeyboard(
        editorInfo: EditorInfo?, settingsValues: SettingsValues,
        currentAutoCapsState: Int, currentRecapitalizeState: Int
    ) {
        val builder: KeyboardLayoutSet.Builder = KeyboardLayoutSet.Builder(
            mThemeContext!!, editorInfo
        )
        val res: Resources = mThemeContext!!.getResources()
        val keyboardWidth: Int = mLatinIME!!.getMaxWidth()
        val keyboardHeight: Int = ResourceUtils.getKeyboardHeight(res, settingsValues)
        val keyboardBottomOffset: Int = ResourceUtils.getKeyboardBottomOffset(res, settingsValues)
        builder.setKeyboardTheme(mKeyboardTheme!!.mThemeId)
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight, keyboardBottomOffset)
        builder.setSubtype(mRichImm?.currentSubtype!!)
        builder.setLanguageSwitchKeyEnabled(mLatinIME!!.shouldShowLanguageSwitchKey())
        builder.setShowSpecialChars(!settingsValues.mHideSpecialChars)
        builder.setShowNumberRow(settingsValues.mShowNumberRow)
        mKeyboardLayoutSet = builder.build()
        try {
            mState!!.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState)
            mKeyboardTextsSet.setLocale(
                mRichImm?.currentSubtype?.localeObject!!,
                mThemeContext!!
            )
        } catch (e: KeyboardLayoutSetException) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.cause)
        }
    }

    fun saveKeyboardState() {
        if (keyboard != null) {
            mState!!.onSaveKeyboardState()
        }
    }

    fun onHideWindow() {
        if (mainKeyboardView != null) {
            mainKeyboardView!!.onHideWindow()
        }
    }

    private fun setKeyboard(
        keyboardId: Int,
        toggleState: KeyboardSwitchState
    ) {
        val currentSettingsValues: SettingsValues? = Settings.current
        setMainKeyboardFrame(currentSettingsValues!!, toggleState)
        // TODO: pass this object to setKeyboard instead of getting the current values.
        val keyboardView: MainKeyboardView? = mainKeyboardView
        val oldKeyboard: Keyboard? = keyboardView?.keyboard
        val newKeyboard: Keyboard? = mKeyboardLayoutSet!!.getKeyboard(keyboardId)
        keyboardView?.keyboard = newKeyboard
        keyboardView!!.setKeyPreviewPopupEnabled(
            currentSettingsValues.mKeyPreviewPopupOn,
            currentSettingsValues.mKeyPreviewPopupDismissDelay
        )
        val subtypeChanged: Boolean = (oldKeyboard == null)
                || newKeyboard!!.mId!!.mSubtype != oldKeyboard.mId!!.mSubtype
        val languageOnSpacebarFormatType: Int =
            LanguageOnSpacebarUtils.getLanguageOnSpacebarFormatType(
                newKeyboard!!.mId!!.mSubtype!!
            )
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType)
    }

    val keyboard: Keyboard?
        get() {
            if (mainKeyboardView != null) {
                return mainKeyboardView?.keyboard
            }
            return null
        }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    fun resetKeyboardStateToAlphabet(
        currentAutoCapsState: Int,
        currentRecapitalizeState: Int
    ) {
        mState!!.onResetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState)
    }

    fun onPressKey(
        code: Int, isSinglePointer: Boolean,
        currentAutoCapsState: Int, currentRecapitalizeState: Int
    ) {
        mState!!.onPressKey(code, isSinglePointer, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onReleaseKey(
        code: Int, withSliding: Boolean,
        currentAutoCapsState: Int, currentRecapitalizeState: Int
    ) {
        mState!!.onReleaseKey(code, withSliding, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onFinishSlidingInput(
        currentAutoCapsState: Int,
        currentRecapitalizeState: Int
    ) {
        mState!!.onFinishSlidingInput(currentAutoCapsState, currentRecapitalizeState)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetKeyboard() {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetManualShiftedKeyboard() {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetManualShiftedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetAutomaticShiftedKeyboard() {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetAutomaticShiftedKeyboard")
        }
        setKeyboard(
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
            KeyboardSwitchState.OTHER
        )
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setAlphabetShiftLockedKeyboard() {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetShiftLockedKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setSymbolsKeyboard() {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard")
        }
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS, KeyboardSwitchState.OTHER)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun setSymbolsShiftedKeyboard() {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard")
        }
        setKeyboard(
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED,
            KeyboardSwitchState.SYMBOLS_SHIFTED
        )
    }

    fun isImeSuppressedByHardwareKeyboard(
        settingsValues: SettingsValues?,
        toggleState: KeyboardSwitchState
    ): Boolean {
        return settingsValues?.mHasHardwareKeyboard == true && toggleState == KeyboardSwitchState.HIDDEN
    }

    private fun setMainKeyboardFrame(
        settingsValues: SettingsValues,
        toggleState: KeyboardSwitchState
    ) {
        val visibility: Int = if (isImeSuppressedByHardwareKeyboard(settingsValues, toggleState))
            View.GONE
        else
            View.VISIBLE
        mainKeyboardView!!.setVisibility(visibility)
        // The visibility of {@link #mKeyboardView} must be aligned with {@link #MainKeyboardFrame}.
        // @see #getVisibleKeyboardView() and
        // @see LatinIME#onComputeInset(android.inputmethodservice.InputMethodService.Insets)
        mMainKeyboardFrame!!.setVisibility(visibility)
    }

    enum class KeyboardSwitchState(keyboardId: Int) {
        HIDDEN(-1),
        SYMBOLS_SHIFTED(KeyboardId.ELEMENT_SYMBOLS_SHIFTED),
        OTHER(-1);

        val mKeyboardId: Int

        init {
            mKeyboardId = keyboardId
        }
    }

    val keyboardSwitchState: KeyboardSwitchState
        get() {
            val hidden: Boolean =
                mKeyboardLayoutSet == null || mainKeyboardView == null || !mainKeyboardView!!.isShown()
            if (hidden) {
                return KeyboardSwitchState.HIDDEN
            } else if (isShowingKeyboardId(KeyboardId.ELEMENT_SYMBOLS_SHIFTED)) {
                return KeyboardSwitchState.SYMBOLS_SHIFTED
            }
            return KeyboardSwitchState.OTHER
        }

    // Future method for requesting an updating to the shift state.
    override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: Int) {
        if (SwitchActions.DEBUG_ACTION) {
            Log.d(
                TAG, ("requestUpdatingShiftState: "
                        + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags)
                        + " recapitalizeMode=" + RecapitalizeStatus.modeToString(
                    recapitalizeMode
                ))
            )
        }
        mState!!.onUpdateShiftState(autoCapsFlags, recapitalizeMode)
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun startDoubleTapShiftKeyTimer() {
        if (SwitchActions.DEBUG_TIMER_ACTION) {
            Log.d(TAG, "startDoubleTapShiftKeyTimer")
        }
        val keyboardView: MainKeyboardView? = mainKeyboardView
        if (keyboardView != null) {
            keyboardView.startDoubleTapShiftKeyTimer()
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    override fun cancelDoubleTapShiftKeyTimer() {
        if (SwitchActions.DEBUG_TIMER_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard")
        }
        val keyboardView: MainKeyboardView? = mainKeyboardView
        if (keyboardView != null) {
            keyboardView.cancelDoubleTapShiftKeyTimer()
        }
    }

    override val isInDoubleTapShiftKeyTimeout: Boolean
        // Implements {@link KeyboardState.SwitchActions}.
        get() {
            if (SwitchActions.DEBUG_TIMER_ACTION) {
                Log.d(TAG, "isInDoubleTapShiftKeyTimeout")
            }
            val keyboardView: MainKeyboardView? = mainKeyboardView
            return keyboardView != null && keyboardView.isInDoubleTapShiftKeyTimeout
        }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    fun onEvent(
        event: Event, currentAutoCapsState: Int,
        currentRecapitalizeState: Int
    ) {
        mState!!.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    fun isShowingKeyboardId(vararg keyboardIds: Int): Boolean {
        if (mainKeyboardView == null || !mainKeyboardView!!.isShown()) {
            return false
        }
        val activeKeyboardId: Int = mainKeyboardView?.keyboard?.mId?.mElementId!!
        for (keyboardId: Int in keyboardIds) {
            if (activeKeyboardId == keyboardId) {
                return true
            }
        }
        return false
    }

    val isShowingMoreKeysPanel: Boolean
        get() {
            return mainKeyboardView!!.isShowingMoreKeysPanel
        }

    val visibleKeyboardView: View?
        get() {
            return mainKeyboardView
        }

    fun deallocateMemory() {
        if (mainKeyboardView != null) {
            mainKeyboardView!!.cancelAllOngoingEvents()
            mainKeyboardView!!.deallocateMemory()
        }
    }

    fun onCreateInputView(uiMode: Int): View? {
        if (mainKeyboardView != null) {
            mainKeyboardView!!.closing()
        }

        updateKeyboardThemeAndContextThemeWrapper(
            mLatinIME!!, KeyboardTheme.getKeyboardTheme(mLatinIME /* context */)!!, uiMode
        )
        mCurrentInputView = LayoutInflater.from(mThemeContext).inflate(
            R.layout.input_view, null
        ) as InputView?
        mMainKeyboardFrame = mCurrentInputView!!.findViewById(R.id.main_keyboard_frame)

        mainKeyboardView =
            mCurrentInputView!!.findViewById<View>(R.id.keyboard_view) as MainKeyboardView?
        mainKeyboardView!!.setKeyboardActionListener(mLatinIME)
        return mCurrentInputView
    }

    companion object {
        private val TAG: String = KeyboardSwitcher::class.java.getSimpleName()

        val instance: KeyboardSwitcher = KeyboardSwitcher()

        fun init(latinIme: LatinIME) {
            instance.initInternal(latinIme)
        }
    }
}
