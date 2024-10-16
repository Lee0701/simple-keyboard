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
internal open class ModifierKeyState(name: String) {
    protected val mName: String
    protected var mState: Int = RELEASING

    init {
        mName = name
    }

    fun onPress() {
        mState = PRESSING
    }

    fun onRelease() {
        mState = RELEASING
    }

    open fun onOtherKeyPressed() {
        val oldState: Int = mState
        if (oldState == PRESSING) mState = CHORDING
        if (DEBUG) Log.d(TAG, mName + ".onOtherKeyPressed: " + toString(oldState) + " > " + this)
    }

    val isPressing: Boolean
        get() {
            return mState == PRESSING
        }

    val isReleasing: Boolean
        get() {
            return mState == RELEASING
        }

    val isChording: Boolean
        get() {
            return mState == CHORDING
        }

    override fun toString(): String {
        return toString(mState)
    }

    protected open fun toString(state: Int): String {
        when (state) {
            RELEASING -> return "RELEASING"
            PRESSING -> return "PRESSING"
            CHORDING -> return "CHORDING"
            else -> return "UNKNOWN"
        }
    }

    companion object {
        @JvmStatic protected val TAG: String = ModifierKeyState::class.java.getSimpleName()
        @JvmStatic protected val DEBUG: Boolean = false

        @JvmStatic protected val RELEASING: Int = 0
        @JvmStatic protected val PRESSING: Int = 1
        @JvmStatic protected val CHORDING: Int = 2
    }
}
