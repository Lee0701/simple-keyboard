/*
 * Copyright (C) 2014 The Android Open Source Project
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

import rkr.simplekeyboard.inputmethod.keyboard.Key
import rkr.simplekeyboard.inputmethod.keyboard.MoreKeysPanel
import rkr.simplekeyboard.inputmethod.keyboard.PointerTracker

interface DrawingProxy {
    /**
     * Called when a key is being pressed.
     * @param key the [Key] that is being pressed.
     * @param withPreview true if key popup preview should be displayed.
     */
    fun onKeyPressed(key: Key, withPreview: Boolean)

    /**
     * Called when a key is being released.
     * @param key the [Key] that is being released.
     * @param withAnimation when true, key popup preview should be dismissed with animation.
     */
    fun onKeyReleased(key: Key, withAnimation: Boolean)

    /**
     * Start showing more keys keyboard of a key that is being long pressed.
     * @param key the [Key] that is being long pressed and showing more keys keyboard.
     * @param tracker the [PointerTracker] that detects this long pressing.
     * @return [MoreKeysPanel] that is being shown. null if there is no need to show more keys
     * keyboard.
     */
    fun showMoreKeysKeyboard(key: Key, tracker: PointerTracker): MoreKeysPanel?

    /**
     * Start a while-typing-animation.
     * @param fadeInOrOut [.FADE_IN] starts while-typing-fade-in animation.
     * [.FADE_OUT] starts while-typing-fade-out animation.
     */
    fun startWhileTypingAnimation(fadeInOrOut: Int)

    companion object {
        const val FADE_IN: Int = 0
        const val FADE_OUT: Int = 1
    }
}
