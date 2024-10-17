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
package rkr.simplekeyboard.inputmethod.latin

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Debug
import android.os.IBinder
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.util.PrintWriterPrinter
import android.util.Printer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.EditorInfoCompatUtils
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat
import rkr.simplekeyboard.inputmethod.compat.ViewOutlineProviderCompatUtils
import rkr.simplekeyboard.inputmethod.compat.ViewOutlineProviderCompatUtils.InsetsUpdater
import rkr.simplekeyboard.inputmethod.event.Event
import rkr.simplekeyboard.inputmethod.event.InputTransaction
import rkr.simplekeyboard.inputmethod.keyboard.Keyboard
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardActionListener
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardId
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardSwitcher
import rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager.SubtypeChangedListener
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.define.DebugFlags
import rkr.simplekeyboard.inputmethod.latin.inputlogic.InputLogic
import rkr.simplekeyboard.inputmethod.latin.settings.Settings
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues
import rkr.simplekeyboard.inputmethod.latin.utils.ApplicationUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LeakGuardHandlerWrapper
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.ViewLayoutUtils
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
class LatinIME : InputMethodService(), KeyboardActionListener, SubtypeChangedListener {
    val mSettings: Settings
    var currentLayoutLocale: Locale? = null
        private set
    private var mOriginalNavBarColor: Int = 0
    private var mOriginalNavBarFlags: Int = 0
    val mInputLogic: InputLogic = InputLogic(this /* LatinIME */)

    // TODO: Move these {@link View}s to {@link KeyboardSwitcher}.
    private var mInputView: View? = null
    private var mInsetsUpdater: InsetsUpdater? = null

    private var mRichImm: RichInputMethodManager? = null
    val mKeyboardSwitcher: KeyboardSwitcher

    private var mOptionsDialog: AlertDialog? = null

    val mHandler: UIHandler = UIHandler(this)

    class UIHandler(ownerInstance: LatinIME) : LeakGuardHandlerWrapper<LatinIME?>(ownerInstance) {
        private var mDelayInMillisecondsToUpdateShiftState: Int = 0

        fun onCreate() {
            val latinIme: LatinIME? = ownerInstance
            if (latinIme == null) {
                return
            }
            val res: Resources = latinIme.resources
            mDelayInMillisecondsToUpdateShiftState = res.getInteger(
                R.integer.config_delay_in_milliseconds_to_update_shift_state
            )
        }

        override fun handleMessage(msg: Message) {
            val latinIme: LatinIME? = ownerInstance
            if (latinIme == null) {
                return
            }
            val switcher: KeyboardSwitcher = latinIme.mKeyboardSwitcher
            when (msg.what) {
                MSG_UPDATE_SHIFT_STATE -> switcher.requestUpdatingShiftState(
                    latinIme.currentAutoCapsState,
                    latinIme.currentRecapitalizeState
                )

                MSG_RESET_CACHES -> {
                    val settingsValues: SettingsValues? = latinIme.mSettings.current
                    if (latinIme.mInputLogic.retryResetCachesAndReturnSuccess(
                            msg.arg1 == ARG1_TRUE,  /* tryResumeSuggestions */
                            msg.arg2,  /* remainingTries */this /* handler */
                        )
                    ) {
                        // If we were able to reset the caches, then we can reload the keyboard.
                        // Otherwise, we'll do it when we can.
                        latinIme.mKeyboardSwitcher.loadKeyboard(
                            latinIme.currentInputEditorInfo,
                            settingsValues!!, latinIme.currentAutoCapsState,
                            latinIme.currentRecapitalizeState
                        )
                    }
                }

                MSG_WAIT_FOR_DICTIONARY_LOAD -> Log.i(TAG, "Timeout waiting for dictionary load")
                MSG_DEALLOCATE_MEMORY -> latinIme.deallocateMemory()
            }
        }

        fun postResetCaches(tryResumeSuggestions: Boolean, remainingTries: Int) {
            removeMessages(MSG_RESET_CACHES)
            sendMessage(
                obtainMessage(
                    MSG_RESET_CACHES, if (tryResumeSuggestions) 1 else 0,
                    remainingTries, null
                )
            )
        }

        fun postUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE)
            sendMessageDelayed(
                obtainMessage(MSG_UPDATE_SHIFT_STATE),
                mDelayInMillisecondsToUpdateShiftState.toLong()
            )
        }

        fun postDeallocateMemory() {
            sendMessageDelayed(
                obtainMessage(MSG_DEALLOCATE_MEMORY),
                DELAY_DEALLOCATE_MEMORY_MILLIS
            )
        }

        fun cancelDeallocateMemory() {
            removeMessages(MSG_DEALLOCATE_MEMORY)
        }

        fun hasPendingDeallocateMemory(): Boolean {
            return hasMessages(MSG_DEALLOCATE_MEMORY)
        }

        // Working variables for the following methods.
        private var mIsOrientationChanging: Boolean = false
        private var mPendingSuccessiveImsCallback: Boolean = false
        private var mHasPendingStartInput: Boolean = false
        private var mHasPendingFinishInputView: Boolean = false
        private var mHasPendingFinishInput: Boolean = false
        private var mAppliedEditorInfo: EditorInfo? = null

        private fun resetPendingImsCallback() {
            mHasPendingFinishInputView = false
            mHasPendingFinishInput = false
            mHasPendingStartInput = false
        }

        private fun executePendingImsCallback(
            latinIme: LatinIME, editorInfo: EditorInfo?,
            restarting: Boolean
        ) {
            if (mHasPendingFinishInputView) {
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput)
            }
            if (mHasPendingFinishInput) {
                latinIme.onFinishInputInternal()
            }
            if (mHasPendingStartInput) {
                latinIme.onStartInputInternal(editorInfo, restarting)
            }
            resetPendingImsCallback()
        }

        fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the second onStartInput after orientation changed.
                mHasPendingStartInput = true
            } else {
                if (mIsOrientationChanging && restarting) {
                    // This is the first onStartInput after orientation changed.
                    mIsOrientationChanging = false
                    mPendingSuccessiveImsCallback = true
                }
                val latinIme: LatinIME? = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting)
                    latinIme.onStartInputInternal(editorInfo, restarting)
                }
            }
        }

        fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)
                && KeyboardId.equivalentEditorInfoForKeyboard(
                    editorInfo,
                    mAppliedEditorInfo
                )
            ) {
                // Typically this is the second onStartInputView after orientation changed.
                resetPendingImsCallback()
            } else {
                if (mPendingSuccessiveImsCallback) {
                    // This is the first onStartInputView after orientation changed.
                    mPendingSuccessiveImsCallback = false
                    resetPendingImsCallback()
                    sendMessageDelayed(
                        obtainMessage(MSG_PENDING_IMS_CALLBACK),
                        PENDING_IMS_CALLBACK_DURATION_MILLIS.toLong()
                    )
                }
                val latinIme: LatinIME? = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting)
                    latinIme.onStartInputViewInternal(editorInfo, restarting)
                    mAppliedEditorInfo = editorInfo
                }
                cancelDeallocateMemory()
            }
        }

        fun onFinishInputView(finishingInput: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true
            } else {
                val latinIme: LatinIME? = ownerInstance
                if (latinIme != null) {
                    latinIme.onFinishInputViewInternal(finishingInput)
                    mAppliedEditorInfo = null
                }
                if (!hasPendingDeallocateMemory()) {
                    postDeallocateMemory()
                }
            }
        }

        fun onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true
            } else {
                val latinIme: LatinIME? = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, null, false)
                    latinIme.onFinishInputInternal()
                }
            }
        }

        companion object {
            private const val MSG_UPDATE_SHIFT_STATE: Int = 0
            private const val MSG_PENDING_IMS_CALLBACK: Int = 1
            private const val MSG_RESET_CACHES: Int = 7
            private const val MSG_WAIT_FOR_DICTIONARY_LOAD: Int = 8
            private const val MSG_DEALLOCATE_MEMORY: Int = 9

            private const val ARG1_TRUE: Int = 1
        }
    }

    override fun onCreate() {
        Settings.init(this)
        DebugFlags.init(PreferenceManagerCompat.getDeviceSharedPreferences(this))
        RichInputMethodManager.init(this)
        mRichImm = RichInputMethodManager.instance
        mRichImm!!.setSubtypeChangeHandler(this)
        KeyboardSwitcher.init(this)
        AudioAndHapticFeedbackManager.init(this)
        super.onCreate()

        mHandler.onCreate()

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and
        // {@link #resetDictionaryFacilitatorIfNecessary()}.
        loadSettings()

        // Register to receive ringer mode change.
        val filter: IntentFilter = IntentFilter()
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        registerReceiver(mRingerModeChangeReceiver, filter)
    }

    private fun loadSettings() {
        currentLayoutLocale = mRichImm?.currentSubtype?.localeObject
        val editorInfo = currentInputEditorInfo
        val inputAttributes = InputAttributes(editorInfo, isFullscreenMode)
        mSettings.loadSettings(inputAttributes)
        val currentSettingsValues: SettingsValues? = mSettings.current
        AudioAndHapticFeedbackManager.instance
            .onSettingsChanged(currentSettingsValues)
    }

    override fun onDestroy() {
        mSettings.onDestroy()
        unregisterReceiver(mRingerModeChangeReceiver)
        super.onDestroy()
    }

    private val isImeSuppressedByHardwareKeyboard: Boolean
        get() {
            val switcher: KeyboardSwitcher = KeyboardSwitcher.instance
            return !onEvaluateInputViewShown() && switcher.isImeSuppressedByHardwareKeyboard(
                mSettings.current, switcher.keyboardSwitchState
            )
        }

    override fun onConfigurationChanged(conf: Configuration) {
        val settingsValues: SettingsValues? = mSettings.current
        if (settingsValues!!.mHasHardwareKeyboard != Settings.readHasHardwareKeyboard(conf)) {
            // If the state of having a hardware keyboard changed, then we want to reload the
            // settings to adjust for that.
            // TODO: we should probably do this unconditionally here, rather than only when we
            // have a change in hardware keyboard configuration.
            loadSettings()
        }

        mKeyboardSwitcher.updateKeyboardTheme(conf.uiMode)

        super.onConfigurationChanged(conf)
    }

    override fun onCreateInputView(): View {
        return mKeyboardSwitcher.onCreateInputView(resources.configuration.uiMode)!!
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        mInputView = view
        mInsetsUpdater = ViewOutlineProviderCompatUtils.setInsetsOutlineProvider(view)
        updateSoftInputWindowLayoutParameters()
    }

    override fun setCandidatesView(view: View) {
        // To ensure that CandidatesView will never be set.
    }

    override fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
        mHandler.onStartInput(editorInfo, restarting)
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        mHandler.onStartInputView(editorInfo, restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        mRichImm!!.resetSubtypeCycleOrder()
        mHandler.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        mHandler.onFinishInput()
    }

    override fun onCurrentSubtypeChanged() {
        mInputLogic.onSubtypeChanged()
        loadKeyboard()
    }

    fun onStartInputInternal(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)

        // If the primary hint language does not match the current subtype language, then try
        // to switch to the primary hint language.
        // TODO: Support all the locales in EditorInfo#hintLocales.
        val primaryHintLocale: Locale? = EditorInfoCompatUtils.getPrimaryHintLocale(editorInfo)
        if (primaryHintLocale == null) {
            return
        }
        mRichImm!!.setCurrentSubtype(primaryHintLocale)
    }

    fun onStartInputViewInternal(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)

        // Switch to the null consumer to handle cases leading to early exit below, for which we
        // also wouldn't be consuming gesture data.
        val switcher: KeyboardSwitcher = mKeyboardSwitcher
        switcher.updateKeyboardTheme(resources.configuration.uiMode)
        val mainKeyboardView: MainKeyboardView? = switcher.mainKeyboardView
        // If we are starting input in a different text field from before, we'll have to reload
        // settings, so currentSettingsValues can't be final.
        var currentSettingsValues: SettingsValues? = mSettings.current

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()")
            if (DebugFlags.DEBUG_ENABLED) {
                throw NullPointerException("Null EditorInfo in onStartInputView()")
            }
            return
        }
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(
                TAG, "onStartInputView: editorInfo:"
                        + String.format(
                    "inputType=0x%08x imeOptions=0x%08x",
                    editorInfo.inputType, editorInfo.imeOptions
                )
            )
            Log.d(
                TAG, ("All caps = "
                        + ((editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)
                        + ", sentence caps = "
                        + ((editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0)
                        + ", word caps = "
                        + ((editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0))
            )
        }
        Log.i(
            TAG, ("Starting input. Cursor position = "
                    + editorInfo.initialSelStart + "," + editorInfo.initialSelEnd)
        )

        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return
        }

        val inputTypeChanged: Boolean = !currentSettingsValues!!.isSameInputType(editorInfo)
        val isDifferentTextField: Boolean = !restarting || inputTypeChanged

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode()

        // ALERT: settings have not been reloaded and there is a chance they may be stale.
        // In the practice, if it is, we should have gotten onConfigurationChanged so it should
        // be fine, but this is horribly confusing and must be fixed AS SOON AS POSSIBLE.

        // In some cases the input connection has not been reset yet and we can't access it. In
        // this case we will need to call loadKeyboard() later, when it's accessible, so that we
        // can go into the correct mode, so we need to do some housekeeping here.
        val needToCallLoadKeyboardLater: Boolean
        if (!isImeSuppressedByHardwareKeyboard) {
            // The app calling setText() has the effect of clearing the composing
            // span, so we should reset our state unconditionally, even if restarting is true.
            // We also tell the input logic about the combining rules for the current subtype, so
            // it can adjust its combiners if needed.
            mInputLogic.startInput()

            // TODO[IL]: Can the following be moved to InputLogic#startInput?
            if (!mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                    editorInfo.initialSelStart, editorInfo.initialSelEnd
                )
            ) {
                // Sometimes, while rotating, for some reason the framework tells the app we are not
                // connected to it and that means we can't refresh the cache. In this case, schedule
                // a refresh later.
                // We try resetting the caches up to 5 times before giving up.
                mHandler.postResetCaches(isDifferentTextField, 5 /* remainingTries */)
                // mLastSelection{Start,End} are reset later in this method, no need to do it here
                needToCallLoadKeyboardLater = true
            } else {
                needToCallLoadKeyboardLater = false
            }
        } else {
            // If we have a hardware keyboard we don't need to call loadKeyboard later anyway.
            needToCallLoadKeyboardLater = false
        }

        if (isDifferentTextField ||
            !currentSettingsValues.hasSameOrientation(resources.configuration)
        ) {
            loadSettings()
        }
        if (isDifferentTextField) {
            mainKeyboardView.closing()
            currentSettingsValues = mSettings.current

            switcher.loadKeyboard(
                editorInfo, currentSettingsValues!!, currentAutoCapsState,
                currentRecapitalizeState
            )
            if (needToCallLoadKeyboardLater) {
                // If we need to call loadKeyboard again later, we need to save its state now. The
                // later call will be done in #retryResetCaches.
                switcher.saveKeyboardState()
            }
        } else if (restarting) {
            // TODO: Come up with a more comprehensive way to reset the keyboard layout when
            // a keyboard layout set doesn't get reloaded in this method.
            switcher.resetKeyboardStateToAlphabet(
                currentAutoCapsState,
                currentRecapitalizeState
            )
            // In apps like Talk, we come here when the text is sent and the field gets emptied and
            // we need to re-evaluate the shift state, but not the whole layout which would be
            // disruptive.
            // Space state must be updated before calling updateShiftState
            switcher.requestUpdatingShiftState(
                currentAutoCapsState,
                currentRecapitalizeState
            )
        }

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime")
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (isInputViewShown) setNavigationBarColor()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        val mainKeyboardView: MainKeyboardView? = mKeyboardSwitcher.mainKeyboardView
        if (mainKeyboardView != null) {
            mainKeyboardView.closing()
        }
        clearNavigationBarColor()
    }

    fun onFinishInputInternal() {
        super.onFinishInput()

        val mainKeyboardView: MainKeyboardView? = mKeyboardSwitcher.mainKeyboardView
        if (mainKeyboardView != null) {
            mainKeyboardView.closing()
        }
    }

    fun onFinishInputViewInternal(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
    }

    protected fun deallocateMemory() {
        mKeyboardSwitcher.deallocateMemory()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            composingSpanStart, composingSpanEnd
        )
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(
                TAG, ("onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                        + ", nss=" + newSelStart + ", nse=" + newSelEnd
                        + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd)
            )
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.
        if (isInputViewShown
            && mInputLogic.onUpdateSelection(newSelStart, newSelEnd)
        ) {
            mKeyboardSwitcher.requestUpdatingShiftState(
                currentAutoCapsState,
                currentRecapitalizeState
            )
        }
    }

    override fun hideWindow() {
        mKeyboardSwitcher.onHideWindow()

        if (TRACE) Debug.stopMethodTracing()
        if (isShowingOptionDialog) {
            mOptionsDialog!!.dismiss()
            mOptionsDialog = null
        }
        super.hideWindow()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // This method may be called before {@link #setInputView(View)}.
        if (mInputView == null) {
            return
        }
        val visibleKeyboardView: View? = mKeyboardSwitcher.visibleKeyboardView
        if (visibleKeyboardView == null) {
            return
        }
        val inputHeight: Int = mInputView!!.height
        if (isImeSuppressedByHardwareKeyboard && !visibleKeyboardView.isShown) {
            // If there is a hardware keyboard and a visible software keyboard view has been hidden,
            // no visual element will be shown on the screen.
            outInsets.contentTopInsets = inputHeight
            outInsets.visibleTopInsets = inputHeight
            mInsetsUpdater!!.setInsets(outInsets)
            return
        }
        val visibleTopY: Int = inputHeight - visibleKeyboardView.height
        // Need to set expanded touchable region only if a keyboard view is being shown.
        if (visibleKeyboardView.isShown) {
            val touchLeft: Int = 0
            val touchTop: Int = if (mKeyboardSwitcher.isShowingMoreKeysPanel) 0 else visibleTopY
            val touchRight: Int = visibleKeyboardView.width
            val touchBottom: Int = (inputHeight // Extend touchable region below the keyboard.
                    + EXTENDED_TOUCHABLE_REGION_HEIGHT)
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(touchLeft, touchTop, touchRight, touchBottom)
        }
        outInsets.contentTopInsets = visibleTopY
        outInsets.visibleTopInsets = visibleTopY
        mInsetsUpdater!!.setInsets(outInsets)
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (isImeSuppressedByHardwareKeyboard) {
            return true
        }
        return super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        if (isImeSuppressedByHardwareKeyboard) {
            // If there is a hardware keyboard, disable full screen mode.
            return false
        }
        // Reread resource value here, because this method is called by the framework as needed.
        val isFullscreenModeAllowed: Boolean = Settings.readUseFullscreenMode(
            resources
        )
        if (super.onEvaluateFullscreenMode() && isFullscreenModeAllowed) {
            // TODO: Remove this hack. Actually we should not really assume NO_EXTRACT_UI
            // implies NO_FULLSCREEN. However, the framework mistakenly does.  i.e. NO_EXTRACT_UI
            // without NO_FULLSCREEN doesn't work as expected. Because of this we need this
            // hack for now.  Let's get rid of this once the framework gets fixed.
            val ei: EditorInfo? = currentInputEditorInfo
            return !(ei != null && ((ei.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0))
        }
        return false
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        updateSoftInputWindowLayoutParameters()
    }

    private fun updateSoftInputWindowLayoutParameters() {
        // Override layout parameters to expand {@link SoftInputWindow} to the entire screen.
        // See {@link InputMethodService#setinputView(View)} and
        // {@link SoftInputWindow#updateWidthHeight(WindowManager.LayoutParams)}.
        val window: Window? = window.window
        ViewLayoutUtils.updateLayoutHeightOf(window!!, ViewGroup.LayoutParams.MATCH_PARENT)
        // This method may be called before {@link #setInputView(View)}.
        if (mInputView != null) {
            // In non-fullscreen mode, {@link InputView} and its parent inputArea should expand to
            // the entire screen and be placed at the bottom of {@link SoftInputWindow}.
            // In fullscreen mode, these shouldn't expand to the entire screen and should be
            // coexistent with {@link #mExtractedArea} above.
            // See {@link InputMethodService#setInputView(View) and
            // com.android.internal.R.layout.input_method.xml.
            val layoutHeight: Int = if (isFullscreenMode)
                ViewGroup.LayoutParams.WRAP_CONTENT
            else
                ViewGroup.LayoutParams.MATCH_PARENT
            val inputArea: View = window.findViewById(android.R.id.inputArea)
            ViewLayoutUtils.updateLayoutHeightOf(inputArea, layoutHeight)
            ViewLayoutUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM)
            ViewLayoutUtils.updateLayoutHeightOf(mInputView!!, layoutHeight)
        }
    }

    val currentAutoCapsState: Int
        get() = mInputLogic.getCurrentAutoCapsState(
            mSettings.current,
            mRichImm?.currentSubtype?.keyboardLayoutSet!!
        )

    val currentRecapitalizeState: Int
        get() {
            return mInputLogic.currentRecapitalizeState
        }

    override fun onCustomRequest(requestCode: Int): Boolean {
        when (requestCode) {
            Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER -> return showInputMethodPicker()
        }
        return false
    }

    private fun showInputMethodPicker(): Boolean {
        if (isShowingOptionDialog) {
            return false
        }
        mOptionsDialog = mRichImm!!.showSubtypePicker(
            this,
            mKeyboardSwitcher.mainKeyboardView?.windowToken, this
        )
        return mOptionsDialog != null
    }

    override fun onMovePointer(steps: Int) {
        var steps: Int = steps
        if (mInputLogic.mConnection.hasCursorPosition()) {
            if (TextUtils.getLayoutDirectionFromLocale(currentLayoutLocale) == View.LAYOUT_DIRECTION_RTL) steps =
                -steps

            steps = mInputLogic.mConnection.getUnicodeSteps(steps, true)
            val end: Int = mInputLogic.mConnection.expectedSelectionEnd + steps
            val start: Int =
                if (mInputLogic.mConnection.hasSelection()) mInputLogic.mConnection.expectedSelectionStart else end
            mInputLogic.mConnection.setSelection(start, end)
        } else {
            while (steps < 0) {
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
                steps++
            }
            while (steps > 0) {
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
                steps--
            }
        }
    }

    override fun onMoveDeletePointer(steps: Int) {
        var steps: Int = steps
        if (mInputLogic.mConnection.hasCursorPosition()) {
            steps = mInputLogic.mConnection.getUnicodeSteps(steps, false)
            val end: Int = mInputLogic.mConnection.expectedSelectionEnd
            val start: Int = mInputLogic.mConnection.expectedSelectionStart + steps
            if (start > end) return
            mInputLogic.mConnection.setSelection(start, end)
        } else {
            while (steps < 0) {
                mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                steps++
            }
        }
    }

    override fun onUpWithDeletePointerActive() {
        if (mInputLogic.mConnection.hasSelection()) mInputLogic.sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
    }

    private val isShowingOptionDialog: Boolean
        get() {
            return mOptionsDialog != null && mOptionsDialog!!.isShowing
        }

    fun switchToNextSubtype() {
        val token: IBinder = window.window!!.attributes.token
        mRichImm!!.switchToNextInputMethod(token, !shouldSwitchToOtherInputMethods(token))
    }

    // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
    // alphabetic shift and shift while in symbol layout and get rid of this method.
    private fun getCodePointForKeyboard(codePoint: Int): Int {
        if (Constants.CODE_SHIFT == codePoint) {
            val currentKeyboard: Keyboard? = mKeyboardSwitcher.keyboard
            if (null != currentKeyboard && currentKeyboard.mId!!.isAlphabetKeyboard) {
                return codePoint
            }
            return Constants.CODE_SYMBOL_SHIFT
        }
        return codePoint
    }

    // Implementation of {@link KeyboardActionListener}.
    override fun onCodeInput(
        codePoint: Int, x: Int, y: Int,
        isKeyRepeat: Boolean
    ) {
        // TODO: this processing does not belong inside LatinIME, the caller should be doing this.
        val mainKeyboardView: MainKeyboardView? = mKeyboardSwitcher.mainKeyboardView
        // x and y include some padding, but everything down the line (especially native
        // code) needs the coordinates in the keyboard frame.
        // TODO: We should reconsider which coordinate system should be used to represent
        // keyboard event. Also we should pull this up -- LatinIME has no business doing
        // this transformation, it should be done already before calling onEvent.
        val keyX: Int = mainKeyboardView!!.getKeyX(x)
        val keyY: Int = mainKeyboardView.getKeyY(y)
        val event: Event = createSoftwareKeypressEvent(
            getCodePointForKeyboard(codePoint),
            keyX, keyY, isKeyRepeat
        )
        onEvent(event)
    }

    // This method is public for testability of LatinIME, but also in the future it should
    // completely replace #onCodeInput.
    fun onEvent(event: Event) {
        val completeInputTransaction: InputTransaction =
            mInputLogic.onCodeInput(mSettings.current, event)
        updateStateAfterInputTransaction(completeInputTransaction)
        mKeyboardSwitcher.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    override fun onTextInput(rawText: String?) {
        // TODO: have the keyboard pass the correct key code when we need it.
        val event: Event =
            Event.createSoftwareTextEvent(rawText, Constants.CODE_OUTPUT_TEXT)
        val completeInputTransaction: InputTransaction =
            mInputLogic.onTextInput(mSettings.current, event)
        updateStateAfterInputTransaction(completeInputTransaction)
        mKeyboardSwitcher.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    override fun onFinishSlidingInput() {
        // User finished sliding input.
        mKeyboardSwitcher.onFinishSlidingInput(
            currentAutoCapsState,
            currentRecapitalizeState
        )
    }

    private fun loadKeyboard() {
        // Since we are switching languages, the most urgent thing is to let the keyboard graphics
        // update. LoadKeyboard does that, but we need to wait for buffer flip for it to be on
        // the screen. Anything we do right now will delay this, so wait until the next frame
        // before we do the rest, like reopening dictionaries and updating suggestions. So we
        // post a message.
        loadSettings()
        if (mKeyboardSwitcher.mainKeyboardView != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(
                currentInputEditorInfo, mSettings.current!!,
                currentAutoCapsState, currentRecapitalizeState
            )
        }
    }

    /**
     * After an input transaction has been executed, some state must be updated. This includes
     * the shift state of the keyboard and suggestions. This method looks at the finished
     * inputTransaction to find out what is necessary and updates the state accordingly.
     * @param inputTransaction The transaction that has been executed.
     */
    private fun updateStateAfterInputTransaction(inputTransaction: InputTransaction) {
        when (inputTransaction.requiredShiftUpdate) {
            InputTransaction.SHIFT_UPDATE_LATER -> mHandler.postUpdateShiftState()
            InputTransaction.SHIFT_UPDATE_NOW -> mKeyboardSwitcher.requestUpdatingShiftState(
                currentAutoCapsState,
                currentRecapitalizeState
            )

            else -> {}
        }
    }

    private fun hapticAndAudioFeedback(code: Int, repeatCount: Int) {
        val keyboardView: MainKeyboardView? = mKeyboardSwitcher.mainKeyboardView
        if (keyboardView != null && keyboardView.isInDraggingFinger) {
            // No need to feedback while finger is dragging.
            return
        }
        if (repeatCount > 0) {
            if (code == Constants.CODE_DELETE && !mInputLogic.mConnection.canDeleteCharacters()) {
                // No need to feedback when repeat delete key will have no effect.
                return
            }
            // TODO: Use event time that the last feedback has been generated instead of relying on
            // a repeat count to thin out feedback.
            if (repeatCount % PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT == 0) {
                return
            }
        }
        val feedbackManager: AudioAndHapticFeedbackManager =
            AudioAndHapticFeedbackManager.instance
        if (repeatCount == 0) {
            // TODO: Reconsider how to perform haptic feedback when repeating key.
            feedbackManager.performHapticFeedback(keyboardView)
        }
        feedbackManager.performAudioFeedback(code)
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is depressed;
    // release matching call is {@link #onReleaseKey(int,boolean)} below.
    override fun onPressKey(
        primaryCode: Int, repeatCount: Int,
        isSinglePointer: Boolean
    ) {
        mKeyboardSwitcher.onPressKey(
            primaryCode, isSinglePointer, currentAutoCapsState,
            currentRecapitalizeState
        )
        hapticAndAudioFeedback(primaryCode, repeatCount)
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is released;
    // press matching call is {@link #onPressKey(int,int,boolean)} above.
    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        mKeyboardSwitcher.onReleaseKey(
            primaryCode, withSliding, currentAutoCapsState,
            currentRecapitalizeState
        )
    }

    // receive ringer mode change.
    private val mRingerModeChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                AudioAndHapticFeedbackManager.instance.onRingerModeChanged()
            }
        }
    }

    init {
        mSettings = Settings.instance
        mKeyboardSwitcher = KeyboardSwitcher.instance
    }

    fun launchSettings() {
        requestHideSelf(0)
        val mainKeyboardView: MainKeyboardView? = mKeyboardSwitcher.mainKeyboardView
        if (mainKeyboardView != null) {
            mainKeyboardView.closing()
        }
        val intent: Intent = Intent()
        intent.setClass(this@LatinIME, SettingsActivity::class.java)
        intent.setFlags(
            (Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        startActivity(intent)
    }

    fun debugDumpStateAndCrashWithException(context: String?) {
        val settingsValues: SettingsValues? = mSettings.current
        val s: StringBuilder = StringBuilder(settingsValues.toString())
        s.append("\nAttributes : ").append(settingsValues!!.mInputAttributes)
            .append("\nContext : ").append(context)
        throw RuntimeException(s.toString())
    }

    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<String>) {
        super.dump(fd, fout, args)

        val p: Printer = PrintWriterPrinter(fout)
        p.println("LatinIME state :")
        p.println("  VersionCode = " + ApplicationUtils.getVersionCode(this))
        p.println("  VersionName = " + ApplicationUtils.getVersionName(this))
        val keyboard: Keyboard? = mKeyboardSwitcher.keyboard
        val keyboardMode: Int = if (keyboard != null) keyboard.mId!!.mMode else -1
        p.println("  Keyboard mode = $keyboardMode")
    }

    fun shouldSwitchToOtherInputMethods(token: IBinder?): Boolean {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        if (mSettings.current?.mImeSwitchEnabled != true) {
            return false
        }
        return mRichImm!!.shouldOfferSwitchingToOtherInputMethods(token)
    }

    fun shouldShowLanguageSwitchKey(): Boolean {
        if (mSettings.current?.isLanguageSwitchKeyDisabled == true) {
            return false
        }
        if (mRichImm!!.hasMultipleEnabledSubtypes()) {
            return true
        }

        val token: IBinder? = window.window!!.attributes.token
        if (token == null) {
            return false
        }
        return shouldSwitchToOtherInputMethods(token)
    }

    private fun setNavigationBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mSettings.current?.mUseMatchingNavbarColor == true) {
            val prefs: SharedPreferences = PreferenceManagerCompat.getDeviceSharedPreferences(this)
            val keyboardColor: Int = Settings.readKeyboardColor(
                prefs, this
            )
            val window: Window = window.window ?: return
            mOriginalNavBarColor = window.navigationBarColor
            window.navigationBarColor = keyboardColor

            val view: View = window.decorView
            mOriginalNavBarFlags = view.systemUiVisibility
            if (ResourceUtils.isBrightColor(keyboardColor)) {
                view.systemUiVisibility =
                    mOriginalNavBarFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                view.systemUiVisibility =
                    mOriginalNavBarFlags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    private fun clearNavigationBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mSettings.current?.mUseMatchingNavbarColor == true) {
            val window: Window = window.window ?: return
            window.navigationBarColor = mOriginalNavBarColor
            val view: View = window.decorView
            view.systemUiVisibility = mOriginalNavBarFlags
        }
    }

    companion object {
        val TAG: String = LatinIME::class.java.simpleName
        private const val TRACE: Boolean = false

        private const val EXTENDED_TOUCHABLE_REGION_HEIGHT: Int = 100
        private const val PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT: Int = 2
        private const val PENDING_IMS_CALLBACK_DURATION_MILLIS: Int = 800
        val DELAY_DEALLOCATE_MEMORY_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)

        // A helper method to split the code point and the key code. Ultimately, they should not be
        // squashed into the same variable, and this method should be removed.
        // public for testing, as we don't want to copy the same logic into test code
        fun createSoftwareKeypressEvent(
            keyCodeOrCodePoint: Int, keyX: Int,
            keyY: Int, isKeyRepeat: Boolean
        ): Event {
            val keyCode: Int
            val codePoint: Int
            if (keyCodeOrCodePoint <= 0) {
                keyCode = keyCodeOrCodePoint
                codePoint = Event.NOT_A_CODE_POINT
            } else {
                keyCode = Event.NOT_A_KEY_CODE
                codePoint = keyCodeOrCodePoint
            }
            return Event.createSoftwareKeypressEvent(
                codePoint,
                keyCode,
                keyX,
                keyY,
                isKeyRepeat
            )
        }
    }
}
