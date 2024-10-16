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

/* package */
internal class ShiftKeyState(name: String) : ModifierKeyState(name) {
    override fun onOtherKeyPressed() {
        val oldState: Int = mState
        if (oldState == PRESSING) {
            mState = CHORDING
        } else if (oldState == PRESSING_ON_SHIFTED) {
            mState = IGNORING
        }
        if (DEBUG) Log.d(
            TAG,
            mName + ".onOtherKeyPressed: " + toString(oldState) + " > " + this
        )
    }

    fun onPressOnShifted() {
        mState = PRESSING_ON_SHIFTED
    }

    val isPressingOnShifted: Boolean
        get() {
            return mState == PRESSING_ON_SHIFTED
        }

    val isIgnoring: Boolean
        get() {
            return mState == IGNORING
        }

    override fun toString(): String {
        return toString(mState)
    }

    override fun toString(state: Int): String {
        when (state) {
            PRESSING_ON_SHIFTED -> return "PRESSING_ON_SHIFTED"
            IGNORING -> return "IGNORING"
            else -> return super.toString(state)
        }
    }

    companion object {
        private const val PRESSING_ON_SHIFTED: Int = 3 // both temporary shifted & shift locked
        private const val IGNORING: Int = 4
    }
}
