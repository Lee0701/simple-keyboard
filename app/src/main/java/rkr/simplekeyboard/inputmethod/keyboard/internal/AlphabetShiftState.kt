/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.util.Log

class AlphabetShiftState {
    private var mState: Int = UNSHIFTED

    fun setShifted(newShiftState: Boolean) {
        val oldState: Int = mState
        if (newShiftState) {
            when (oldState) {
                UNSHIFTED -> mState = MANUAL_SHIFTED
                SHIFT_LOCKED -> mState = SHIFT_LOCK_SHIFTED
            }
        } else {
            when (oldState) {
                MANUAL_SHIFTED, AUTOMATIC_SHIFTED -> mState = UNSHIFTED
                SHIFT_LOCK_SHIFTED -> mState = SHIFT_LOCKED
            }
        }
        if (DEBUG) Log.d(
            TAG,
            "setShifted(" + newShiftState + "): " + toString(oldState) + " > " + this
        )
    }

    fun setAutomaticShifted() {
        mState = AUTOMATIC_SHIFTED
    }

    val isShiftedOrShiftLocked: Boolean
        get() {
            return mState != UNSHIFTED
        }

    var isShiftLocked: Boolean
        get() {
            return mState == SHIFT_LOCKED || mState == SHIFT_LOCK_SHIFTED
        }
        set(newShiftLockState) {
            val oldState: Int = mState
            if (newShiftLockState) {
                when (oldState) {
                    UNSHIFTED, MANUAL_SHIFTED, AUTOMATIC_SHIFTED -> mState =
                        SHIFT_LOCKED
                }
            } else {
                mState = UNSHIFTED
            }
            if (DEBUG) Log.d(
                TAG,
                ("setShiftLocked(" + newShiftLockState + "): " + toString(
                    oldState
                )
                        + " > " + this)
            )
        }

    val isShiftLockShifted: Boolean
        get() {
            return mState == SHIFT_LOCK_SHIFTED
        }

    val isAutomaticShifted: Boolean
        get() {
            return mState == AUTOMATIC_SHIFTED
        }

    val isManualShifted: Boolean
        get() {
            return mState == MANUAL_SHIFTED || mState == SHIFT_LOCK_SHIFTED
        }

    override fun toString(): String {
        return toString(mState)
    }

    companion object {
        private val TAG: String = AlphabetShiftState::class.java.getSimpleName()
        private const val DEBUG: Boolean = false

        private const val UNSHIFTED: Int = 0
        private const val MANUAL_SHIFTED: Int = 1
        private const val AUTOMATIC_SHIFTED: Int = 2
        private const val SHIFT_LOCKED: Int = 3
        private const val SHIFT_LOCK_SHIFTED: Int = 4

        private fun toString(state: Int): String {
            when (state) {
                UNSHIFTED -> return "UNSHIFTED"
                MANUAL_SHIFTED -> return "MANUAL_SHIFTED"
                AUTOMATIC_SHIFTED -> return "AUTOMATIC_SHIFTED"
                SHIFT_LOCKED -> return "SHIFT_LOCKED"
                SHIFT_LOCK_SHIFTED -> return "SHIFT_LOCK_SHIFTED"
                else -> return "UNKNOWN"
            }
        }
    }
}
