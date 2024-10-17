/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import rkr.simplekeyboard.inputmethod.latin.common.UnicodeSurrogate
import rkr.simplekeyboard.inputmethod.latin.settings.SpacingAndPunctuations
import rkr.simplekeyboard.inputmethod.latin.utils.CapsModeUtils
import rkr.simplekeyboard.inputmethod.latin.utils.DebugLogUtils
import kotlin.math.min

/**
 * Enrichment class for InputConnection to simplify interaction and add functionality.
 *
 * This class serves as a wrapper to be able to simply add hooks to any calls to the underlying
 * InputConnection. It also keeps track of a number of things to avoid having to call upon IPC
 * all the time to find out what text is in the buffer, when we need it to determine caps mode
 * for example.
 */
class RichInputConnection(parent: InputMethodService) {
    /**
     * This variable contains an expected value for the selection start position. This is where the
     * cursor or selection start may end up after all the keyboard-triggered updates have passed. We
     * keep this to compare it to the actual selection start to guess whether the move was caused by
     * a keyboard command or not.
     * It's not really the selection start position: the selection start may not be there yet, and
     * in some cases, it may never arrive there.
     */
    var expectedSelectionStart: Int = INVALID_CURSOR_POSITION // in chars, not code points
        private set

    /**
     * The expected selection end.  Only differs from mExpectedSelStart if a non-empty selection is
     * expected.  The same caveats as mExpectedSelStart apply.
     */
    var expectedSelectionEnd: Int = INVALID_CURSOR_POSITION // in chars, not code points
        private set

    /**
     * This contains the committed text immediately preceding the cursor and the composing
     * text, if any. It is refreshed when the cursor moves by calling upon the TextView.
     */
    private val mCommittedTextBeforeComposingText: StringBuilder = StringBuilder()

    /**
     * This variable is a temporary object used in [.commitText]
     * to avoid object creation.
     */
    private val mTempObjectForCommitText: SpannableStringBuilder = SpannableStringBuilder()

    private val mParent: InputMethodService
    private var mIC: InputConnection?
    private var mNestLevel: Int

    init {
        mParent = parent
        mIC = null
        mNestLevel = 0
    }

    val isConnected: Boolean
        get() {
            return mIC != null
        }

    private fun checkConsistencyForDebug() {
        val r: ExtractedTextRequest = ExtractedTextRequest()
        r.hintMaxChars = 0
        r.hintMaxLines = 0
        r.token = 1
        r.flags = 0
        val et: ExtractedText? = mIC!!.getExtractedText(r, 0)
        val beforeCursor: CharSequence? = getTextBeforeCursor(
            Constants.EDITOR_CONTENTS_CACHE_SIZE,
            0
        )
        val internal: StringBuilder = StringBuilder(mCommittedTextBeforeComposingText)
        if (null == et || null == beforeCursor) return
        val actualLength: Int =
            min(beforeCursor.length.toDouble(), internal.length.toDouble()).toInt()
        if (internal.length > actualLength) {
            internal.delete(0, internal.length - actualLength)
        }
        val reference: String = if ((beforeCursor.length <= actualLength))
            beforeCursor.toString()
        else
            beforeCursor.subSequence(
                beforeCursor.length - actualLength,
                beforeCursor.length
            ).toString()
        if (et.selectionStart != expectedSelectionStart
            || !(reference == internal.toString())
        ) {
            val context: String = ("Expected selection start = " + expectedSelectionStart
                    + "\nActual selection start = " + et.selectionStart
                    + "\nExpected text = " + internal.length + " " + internal
                    + "\nActual text = " + reference.length + " " + reference)
            (mParent as LatinIME).debugDumpStateAndCrashWithException(context)
        } else {
            Log.e(TAG, DebugLogUtils.getStackTrace(2))
            Log.e(TAG, "Exp <> Actual : " + expectedSelectionStart + " <> " + et.selectionStart)
        }
    }

    fun beginBatchEdit() {
        if (++mNestLevel == 1) {
            mIC = mParent.getCurrentInputConnection()
            if (isConnected) {
                mIC?.beginBatchEdit()
            }
        } else {
            if (DBG) {
                throw RuntimeException("Nest level too deep")
            }
            Log.e(TAG, "Nest level too deep : " + mNestLevel)
        }
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun endBatchEdit() {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!") // TODO: exception instead

        if (--mNestLevel == 0 && isConnected) {
            mIC!!.endBatchEdit()
        }
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    /**
     * Reset the cached text and retrieve it again from the editor.
     *
     * This should be called when the cursor moved. It's possible that we can't connect to
     * the application when doing this; notably, this happens sometimes during rotation, probably
     * because of a race condition in the framework. In this case, we just can't retrieve the
     * data, so we empty the cache and note that we don't know the new cursor position, and we
     * return false so that the caller knows about this and can retry later.
     *
     * @param newSelStart the new position of the selection start, as received from the system.
     * @param newSelEnd the new position of the selection end, as received from the system.
     * @return true if we were able to connect to the editor successfully, false otherwise. When
     * this method returns false, the caches could not be correctly refreshed so they were only
     * reset: the caller should try again later to return to normal operation.
     */
    fun resetCachesUponCursorMoveAndReturnSuccess(
        newSelStart: Int,
        newSelEnd: Int
    ): Boolean {
        expectedSelectionStart = newSelStart
        expectedSelectionEnd = newSelEnd
        val didReloadTextSuccessfully: Boolean = reloadTextCache()
        if (!didReloadTextSuccessfully) {
            Log.d(TAG, "Will try to retrieve text later.")
            return false
        }
        return true
    }

    /**
     * Reload the cached text from the InputConnection.
     *
     * @return true if successful
     */
    private fun reloadTextCache(): Boolean {
        mCommittedTextBeforeComposingText.setLength(0)
        mIC = mParent.getCurrentInputConnection()
        // Call upon the inputconnection directly since our own method is using the cache, and
        // we want to refresh it.
        val textBeforeCursor: CharSequence? = getTextBeforeCursorAndDetectLaggyConnection(
            OPERATION_RELOAD_TEXT_CACHE,
            SLOW_INPUT_CONNECTION_ON_FULL_RELOAD_MS,
            Constants.EDITOR_CONTENTS_CACHE_SIZE,
            0 /* flags */
        )
        if (null == textBeforeCursor) {
            // For some reason the app thinks we are not connected to it. This looks like a
            // framework bug... Fall back to ground state and return false.
            expectedSelectionStart = INVALID_CURSOR_POSITION
            expectedSelectionEnd = INVALID_CURSOR_POSITION
            Log.e(TAG, "Unable to connect to the editor to retrieve text.")
            return false
        }
        mCommittedTextBeforeComposingText.append(textBeforeCursor)
        return true
    }

    private fun checkBatchEdit() {
        if (mNestLevel != 1) {
            // TODO: exception instead
            Log.e(TAG, "Batch edit level incorrect : " + mNestLevel)
            Log.e(TAG, DebugLogUtils.getStackTrace(4))
        }
    }

    /**
     * Calls [InputConnection.commitText].
     *
     * @param text The text to commit. This may include styles.
     * @param newCursorPosition The new cursor position around the text.
     */
    fun commitText(text: CharSequence, newCursorPosition: Int) {
        RichInputMethodManager.resetSubtypeCycleOrder()
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
        mCommittedTextBeforeComposingText.append(text)
        // TODO: the following is exceedingly error-prone. Right now when the cursor is in the
        // middle of the composing word mComposingText only holds the part of the composing text
        // that is before the cursor, so this actually works, but it's terribly confusing. Fix this.
        if (hasCursorPosition()) {
            expectedSelectionStart += text.length
            expectedSelectionEnd = expectedSelectionStart
        }
        if (isConnected) {
            mTempObjectForCommitText.clear()
            mTempObjectForCommitText.append(text)
            val spans: Array<CharacterStyle> = mTempObjectForCommitText.getSpans(
                0, text.length, CharacterStyle::class.java
            )
            for (span: CharacterStyle? in spans) {
                val spanStart: Int = mTempObjectForCommitText.getSpanStart(span)
                val spanEnd: Int = mTempObjectForCommitText.getSpanEnd(span)
                val spanFlags: Int = mTempObjectForCommitText.getSpanFlags(span)
                // We have to adjust the end of the span to include an additional character.
                // This is to avoid splitting a unicode surrogate pair.
                // See rkr.simplekeyboard.inputmethod.latin.common.Constants.UnicodeSurrogate
                // See https://b.corp.google.com/issues/19255233
                if (0 < spanEnd && spanEnd < mTempObjectForCommitText.length) {
                    val spanEndChar: Char = mTempObjectForCommitText.get(spanEnd - 1)
                    val nextChar: Char = mTempObjectForCommitText.get(spanEnd)
                    if (UnicodeSurrogate.isLowSurrogate(spanEndChar)
                        && UnicodeSurrogate.isHighSurrogate(nextChar)
                    ) {
                        mTempObjectForCommitText.setSpan(span, spanStart, spanEnd + 1, spanFlags)
                    }
                }
            }
            mIC!!.commitText(mTempObjectForCommitText, newCursorPosition)
        }
    }

    fun getSelectedText(flags: Int): CharSequence? {
        return if (isConnected) mIC!!.getSelectedText(flags) else null
    }

    fun canDeleteCharacters(): Boolean {
        return expectedSelectionStart > 0
    }

    /**
     * Gets the caps modes we should be in after this specific string.
     *
     * This returns a bit set of TextUtils#CAP_MODE_*, masked by the inputType argument.
     * This method also supports faking an additional space after the string passed in argument,
     * to support cases where a space will be added automatically, like in phantom space
     * state for example.
     * Note that for English, we are using American typography rules (which are not specific to
     * American English, it's just the most common set of rules for English).
     *
     * @param inputType a mask of the caps modes to test for.
     * @param spacingAndPunctuations the values of the settings to use for locale and separators.
     * @return the caps modes that should be on as a set of bits
     */
    fun getCursorCapsMode(inputType: Int, spacingAndPunctuations: SpacingAndPunctuations): Int {
        mIC = mParent.getCurrentInputConnection()
        if (!isConnected) {
            return Constants.TextUtils.CAP_MODE_OFF
        }
        // TODO: this will generally work, but there may be cases where the buffer contains SOME
        // information but not enough to determine the caps mode accurately. This may happen after
        // heavy pressing of delete, for example DEFAULT_TEXT_CACHE_SIZE - 5 times or so.
        // getCapsMode should be updated to be able to return a "not enough info" result so that
        // we can get more context only when needed.
        if (TextUtils.isEmpty(mCommittedTextBeforeComposingText) && 0 != expectedSelectionStart) {
            if (!reloadTextCache()) {
                Log.w(
                    TAG, "Unable to connect to the editor. "
                            + "Setting caps mode without knowing text."
                )
            }
        }
        // This never calls InputConnection#getCapsMode - in fact, it's a static method that
        // never blocks or initiates IPC.
        // TODO: don't call #toString() here. Instead, all accesses to
        // mCommittedTextBeforeComposingText should be done on the main thread.
        return CapsModeUtils.getCapsMode(
            mCommittedTextBeforeComposingText.toString(), inputType,
            spacingAndPunctuations
        )
    }

    val codePointBeforeCursor: Int
        get() {
            val length: Int = mCommittedTextBeforeComposingText.length
            if (length < 1) return Constants.NOT_A_CODE
            return Character.codePointBefore(mCommittedTextBeforeComposingText, length)
        }

    fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        val cachedLength: Int = mCommittedTextBeforeComposingText.length
        // If we have enough characters to satisfy the request, or if we have all characters in
        // the text field, then we can return the cached version right away.
        // However, if we don't have an expected cursor position, then we should always
        // go fetch the cache again (as it happens, INVALID_CURSOR_POSITION < 0, so we need to
        // test for this explicitly)
        if (INVALID_CURSOR_POSITION != expectedSelectionStart
            && (cachedLength >= n || cachedLength >= expectedSelectionStart)
        ) {
            val s: StringBuilder = StringBuilder(mCommittedTextBeforeComposingText)
            // We call #toString() here to create a temporary object.
            // In some situations, this method is called on a worker thread, and it's possible
            // the main thread touches the contents of mComposingText while this worker thread
            // is suspended, because mComposingText is a StringBuilder. This may lead to crashes,
            // so we call #toString() on it. That will result in the return value being strictly
            // speaking wrong, but since this is used for basing bigram probability off, and
            // it's only going to matter for one getSuggestions call, it's fine in the practice.
            if (s.length > n) {
                s.delete(0, s.length - n)
            }
            return s
        }
        return getTextBeforeCursorAndDetectLaggyConnection(
            OPERATION_GET_TEXT_BEFORE_CURSOR,
            SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS,
            n, flags
        )
    }

    fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        return getTextAfterCursorAndDetectLaggyConnection(
            OPERATION_GET_TEXT_AFTER_CURSOR,
            SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS,
            n, flags
        )
    }

    private fun getTextBeforeCursorAndDetectLaggyConnection(
        operation: Int, timeout: Long, n: Int, flags: Int
    ): CharSequence? {
        mIC = mParent.getCurrentInputConnection()
        if (!isConnected) {
            return null
        }
        val startTime: Long = SystemClock.uptimeMillis()
        val result: CharSequence? = mIC?.getTextBeforeCursor(n, flags)
        detectLaggyConnection(operation, timeout, startTime)
        return result
    }

    private fun getTextAfterCursorAndDetectLaggyConnection(
        operation: Int, timeout: Long, n: Int, flags: Int
    ): CharSequence? {
        mIC = mParent.getCurrentInputConnection()
        if (!isConnected) {
            return null
        }
        val startTime: Long = SystemClock.uptimeMillis()
        val result: CharSequence? = mIC?.getTextAfterCursor(n, flags)
        detectLaggyConnection(operation, timeout, startTime)
        return result
    }

    private fun detectLaggyConnection(operation: Int, timeout: Long, startTime: Long) {
        val duration: Long = SystemClock.uptimeMillis() - startTime
        if (duration >= timeout) {
            val operationName: String = OPERATION_NAMES.get(operation)
            Log.w(TAG, "Slow InputConnection: " + operationName + " took " + duration + " ms.")
        }
    }

    fun replaceText(startPosition: Int, endPosition: Int, text: CharSequence?) {
        RichInputMethodManager.resetSubtypeCycleOrder()
        mIC!!.setComposingRegion(startPosition, endPosition)
        mIC!!.setComposingText(text, startPosition)
        mIC!!.finishComposingText()
    }

    fun performEditorAction(actionId: Int) {
        mIC = mParent.getCurrentInputConnection()
        if (isConnected) {
            mIC?.performEditorAction(actionId)
        }
    }

    fun sendKeyEvent(keyEvent: KeyEvent) {
        RichInputMethodManager.resetSubtypeCycleOrder()
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
            // This method is only called for enter or backspace when speaking to old applications
            // (target SDK <= 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)), or for digits.
            // When talking to new applications we never use this method because it's inherently
            // racy and has unpredictable results, but for backward compatibility we continue
            // sending the key events for only Enter and Backspace because some applications
            // mistakenly catch them to do some stuff.
            when (keyEvent.getKeyCode()) {
                KeyEvent.KEYCODE_ENTER -> {
                    mCommittedTextBeforeComposingText.append("\n")
                    if (hasCursorPosition()) {
                        expectedSelectionStart += 1
                        expectedSelectionEnd = expectedSelectionStart
                    }
                }

                KeyEvent.KEYCODE_DEL -> {
                    if (mCommittedTextBeforeComposingText.length > 0) {
                        mCommittedTextBeforeComposingText.delete(
                            mCommittedTextBeforeComposingText.length - 1,
                            mCommittedTextBeforeComposingText.length
                        )
                    }

                    if (expectedSelectionStart > 0 && expectedSelectionStart == expectedSelectionEnd) {
                        // TODO: Handle surrogate pairs.
                        expectedSelectionStart -= 1
                    }
                    expectedSelectionEnd = expectedSelectionStart
                }

                KeyEvent.KEYCODE_UNKNOWN -> if (null != keyEvent.getCharacters()) {
                    mCommittedTextBeforeComposingText.append(keyEvent.getCharacters())
                    if (hasCursorPosition()) {
                        expectedSelectionStart += keyEvent.getCharacters().length
                        expectedSelectionEnd = expectedSelectionStart
                    }
                }

                else -> {
                    val text: String =
                        StringUtils.newSingleCodePointString(keyEvent.getUnicodeChar())
                    mCommittedTextBeforeComposingText.append(text)
                    if (hasCursorPosition()) {
                        expectedSelectionStart += text.length
                        expectedSelectionEnd = expectedSelectionStart
                    }
                }
            }
        }
        if (isConnected) {
            mIC!!.sendKeyEvent(keyEvent)
        }
    }

    /**
     * Set the selection of the text editor.
     *
     * Calls through to [InputConnection.setSelection].
     *
     * @param start the character index where the selection should start.
     * @param end the character index where the selection should end.
     * @return Returns true on success, false on failure: either the input connection is no longer
     * valid when setting the selection or when retrieving the text cache at that point, or
     * invalid arguments were passed.
     */
    fun setSelection(start: Int, end: Int) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
        if (start < 0 || end < 0) {
            return
        }
        if (expectedSelectionStart == start && expectedSelectionEnd == end) {
            return
        }
        RichInputMethodManager.resetSubtypeCycleOrder()

        expectedSelectionStart = start
        expectedSelectionEnd = end
        if (isConnected) {
            val isIcValid: Boolean = mIC!!.setSelection(start, end)
            if (!isIcValid) {
                return
            }
        }
        reloadTextCache()
    }

    /**
     * @return whether there is a selection currently active.
     */
    fun hasSelection(): Boolean {
        return expectedSelectionEnd != expectedSelectionStart
    }

    fun hasCursorPosition(): Boolean {
        return expectedSelectionStart != INVALID_CURSOR_POSITION && expectedSelectionEnd != INVALID_CURSOR_POSITION
    }

    /**
     * Some chars, such as emoji consist of 2 chars (surrogate pairs). We should treat them as one character.
     */
    fun getUnicodeSteps(chars: Int, rightSidePointer: Boolean): Int {
        var chars: Int = chars
        var steps: Int = 0
        if (chars < 0) {
            val charsBeforeCursor: CharSequence? =
                if (rightSidePointer && hasSelection()) getSelectedText(0) else getTextBeforeCursor(
                    -chars * 2,
                    0
                )
            if (charsBeforeCursor != null) {
                var i: Int = charsBeforeCursor.length - 1
                while (i >= 0 && chars < 0) {
                    if (Character.isSurrogate(charsBeforeCursor.get(i))) {
                        steps--
                        i--
                    }
                    i--
                    chars++
                    steps--
                }
            }
        } else if (chars > 0) {
            val charsAfterCursor: CharSequence? =
                if (!rightSidePointer && hasSelection()) getSelectedText(0) else getTextAfterCursor(
                    chars * 2,
                    0
                )
            if (charsAfterCursor != null) {
                var i: Int = 0
                while (i < charsAfterCursor.length && chars > 0) {
                    if (Character.isSurrogate(charsAfterCursor.get(i))) {
                        steps++
                        i++
                    }
                    i++
                    chars--
                    steps++
                }
            }
        }
        return steps
    }

    companion object {
        private const val TAG: String = "RichInputConnection"
        private const val DBG: Boolean = false
        private const val DEBUG_PREVIOUS_TEXT: Boolean = false
        private const val DEBUG_BATCH_NESTING: Boolean = false
        private val INVALID_CURSOR_POSITION: Int = -1

        /**
         * The amount of time a [.reloadTextCache] call needs to take for the keyboard to enter
         */
        private const val SLOW_INPUT_CONNECTION_ON_FULL_RELOAD_MS: Long = 1000

        /**
         * The amount of time a [.getTextBeforeCursor] call needs
         */
        private const val SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS: Long = 200

        private const val OPERATION_GET_TEXT_BEFORE_CURSOR: Int = 0
        private const val OPERATION_GET_TEXT_AFTER_CURSOR: Int = 1
        private const val OPERATION_RELOAD_TEXT_CACHE: Int = 3
        private val OPERATION_NAMES: Array<String> = arrayOf(
            "GET_TEXT_BEFORE_CURSOR",
            "GET_TEXT_AFTER_CURSOR",
            "GET_WORD_RANGE_AT_CURSOR",
            "RELOAD_TEXT_CACHE"
        )
    }
}
