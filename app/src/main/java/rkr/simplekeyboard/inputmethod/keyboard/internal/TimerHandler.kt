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
package rkr.simplekeyboard.inputmethod.keyboard.internal

import android.os.Message
import android.view.ViewConfiguration
import rkr.simplekeyboard.inputmethod.keyboard.Key
import rkr.simplekeyboard.inputmethod.keyboard.PointerTracker
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.utils.LeakGuardHandlerWrapper

class TimerHandler(ownerInstance: DrawingProxy, ignoreAltCodeKeyTimeout: Int) :
    LeakGuardHandlerWrapper<DrawingProxy?>(ownerInstance), TimerProxy {
    private val mIgnoreAltCodeKeyTimeout: Int

    init {
        mIgnoreAltCodeKeyTimeout = ignoreAltCodeKeyTimeout
    }

    override fun handleMessage(msg: Message) {
        val drawingProxy: DrawingProxy = ownerInstance ?: return
        when (msg.what) {
            MSG_REPEAT_KEY -> {
                val tracker1: PointerTracker = msg.obj as PointerTracker
                tracker1.onKeyRepeat(msg.arg1,  /* code */msg.arg2 /* repeatCount */)
            }

            MSG_LONGPRESS_KEY, MSG_LONGPRESS_SHIFT_KEY -> {
                cancelLongPressTimers()
                val tracker2: PointerTracker = msg.obj as PointerTracker
                tracker2.onLongPressed()
            }

            MSG_DISMISS_KEY_PREVIEW -> drawingProxy.onKeyReleased(
                msg.obj as Key,
                false /* withAnimation */
            )
        }
    }

    override fun startKeyRepeatTimerOf(
        tracker: PointerTracker, repeatCount: Int,
        delay: Int
    ) {
        val key: Key? = tracker.key
        if (key == null || delay == 0) {
            return
        }
        sendMessageDelayed(
            obtainMessage(MSG_REPEAT_KEY, key.code, repeatCount, tracker), delay.toLong()
        )
    }

    private fun cancelKeyRepeatTimerOf(tracker: PointerTracker) {
        removeMessages(MSG_REPEAT_KEY, tracker)
    }

    fun cancelKeyRepeatTimers() {
        removeMessages(MSG_REPEAT_KEY)
    }

    val isInKeyRepeat: Boolean
        // TODO: Suppress layout changes in key repeat mode
        get() {
            return hasMessages(MSG_REPEAT_KEY)
        }

    override fun startLongPressTimerOf(tracker: PointerTracker, delay: Int) {
        val key: Key? = tracker.key
        if (key == null) {
            return
        }
        // Use a separate message id for long pressing shift key, because long press shift key
        // timers should be canceled when other key is pressed.
        val messageId: Int = if ((key.code == Constants.CODE_SHIFT))
            MSG_LONGPRESS_SHIFT_KEY
        else
            MSG_LONGPRESS_KEY
        sendMessageDelayed(obtainMessage(messageId, tracker), delay.toLong())
    }

    override fun cancelLongPressTimersOf(tracker: PointerTracker?) {
        removeMessages(MSG_LONGPRESS_KEY, tracker)
        removeMessages(MSG_LONGPRESS_SHIFT_KEY, tracker)
    }

    override fun cancelLongPressShiftKeyTimer() {
        removeMessages(MSG_LONGPRESS_SHIFT_KEY)
    }

    fun cancelLongPressTimers() {
        removeMessages(MSG_LONGPRESS_KEY)
        removeMessages(MSG_LONGPRESS_SHIFT_KEY)
    }

    override fun startTypingStateTimer(typedKey: Key) {
        if (typedKey.isModifier || typedKey.altCodeWhileTyping()) {
            return
        }

        val isTyping: Boolean = isTypingState
        removeMessages(MSG_TYPING_STATE_EXPIRED)
        val drawingProxy: DrawingProxy? = ownerInstance
        if (drawingProxy == null) {
            return
        }

        // When user hits the space or the enter key, just cancel the while-typing timer.
        val typedCode: Int = typedKey.code
        if (typedCode == Constants.CODE_SPACE || typedCode == Constants.CODE_ENTER) {
            if (isTyping) {
                drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_IN)
            }
            return
        }

        sendMessageDelayed(
            obtainMessage(MSG_TYPING_STATE_EXPIRED), mIgnoreAltCodeKeyTimeout.toLong()
        )
        if (isTyping) {
            return
        }
        drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_OUT)
    }

    override val isTypingState: Boolean
        get() {
            return hasMessages(MSG_TYPING_STATE_EXPIRED)
        }

    override fun startDoubleTapShiftKeyTimer() {
        sendMessageDelayed(
            obtainMessage(MSG_DOUBLE_TAP_SHIFT_KEY),
            ViewConfiguration.getDoubleTapTimeout().toLong()
        )
    }

    override fun cancelDoubleTapShiftKeyTimer() {
        removeMessages(MSG_DOUBLE_TAP_SHIFT_KEY)
    }

    override val isInDoubleTapShiftKeyTimeout: Boolean
        get() {
            return hasMessages(MSG_DOUBLE_TAP_SHIFT_KEY)
        }

    override fun cancelKeyTimersOf(tracker: PointerTracker) {
        cancelKeyRepeatTimerOf(tracker)
        cancelLongPressTimersOf(tracker)
    }

    fun cancelAllKeyTimers() {
        cancelKeyRepeatTimers()
        cancelLongPressTimers()
    }

    override fun cancelUpdateBatchInputTimer(tracker: PointerTracker?) {
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker)
    }

    override fun cancelAllUpdateBatchInputTimers() {
        removeMessages(MSG_UPDATE_BATCH_INPUT)
    }

    fun postDismissKeyPreview(key: Key?, delay: Long) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, key), delay)
    }

    fun cancelAllMessages() {
        cancelAllKeyTimers()
        cancelAllUpdateBatchInputTimers()
        removeMessages(MSG_DISMISS_KEY_PREVIEW)
        removeMessages(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT)
    }

    companion object {
        private const val MSG_TYPING_STATE_EXPIRED: Int = 0
        private const val MSG_REPEAT_KEY: Int = 1
        private const val MSG_LONGPRESS_KEY: Int = 2
        private const val MSG_LONGPRESS_SHIFT_KEY: Int = 3
        private const val MSG_DOUBLE_TAP_SHIFT_KEY: Int = 4
        private const val MSG_UPDATE_BATCH_INPUT: Int = 5
        private const val MSG_DISMISS_KEY_PREVIEW: Int = 6
        private const val MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT: Int = 7
    }
}
