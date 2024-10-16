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

import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Log
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.define.DebugFlags
import kotlin.math.abs
import kotlin.math.hypot

// This hack is applied to certain classes of tablets.
class BogusMoveEventDetector {
    private var mAccumulatedDistanceThreshold: Int = 0

    // Accumulated distance from actual and artificial down keys.
    /* package */
    var accumulatedDistanceFromDownKey: Int = 0
    private var mActualDownX: Int = 0
    private var mActualDownY: Int = 0

    fun setKeyboardGeometry(keyPaddedWidth: Int, keyPaddedHeight: Int) {
        val keyDiagonal: Float =
            hypot(keyPaddedWidth.toDouble(), keyPaddedHeight.toDouble()) as Float
        mAccumulatedDistanceThreshold =
            (keyDiagonal * BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD).toInt()
    }

    fun onActualDownEvent(x: Int, y: Int) {
        mActualDownX = x
        mActualDownY = y
    }

    fun onDownKey() {
        accumulatedDistanceFromDownKey = 0
    }

    fun onMoveKey(distance: Int) {
        accumulatedDistanceFromDownKey += distance
    }

    fun hasTraveledLongDistance(x: Int, y: Int): Boolean {
        if (!sNeedsProximateBogusDownMoveUpEventHack) {
            return false
        }
        val dx: Int = abs((x - mActualDownX).toDouble()).toInt()
        val dy: Int = abs((y - mActualDownY).toDouble()).toInt()
        // A bogus move event should be a horizontal movement. A vertical movement might be
        // a sloppy typing and should be ignored.
        return dx >= dy && accumulatedDistanceFromDownKey >= mAccumulatedDistanceThreshold
    }

    companion object {
        private val TAG: String = BogusMoveEventDetector::class.java.getSimpleName()
        private val DEBUG_MODE: Boolean = DebugFlags.DEBUG_ENABLED

        // Move these thresholds to resource.
        // These thresholds' unit is a diagonal length of a key.
        private const val BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD: Float = 0.53f

        private var sNeedsProximateBogusDownMoveUpEventHack: Boolean = false

        fun init(res: Resources) {
            // The proximate bogus down move up event hack is needed for a device such like,
            // 1) is large tablet, or 2) is small tablet and the screen density is less than hdpi.
            // Though it seems odd to use screen density as criteria of the quality of the touch
            // screen, the small table that has a less density screen than hdpi most likely has been
            // made with the touch screen that needs the hack.
            val screenMetrics: Int = res.getInteger(R.integer.config_screen_metrics)
            val isLargeTablet: Boolean = (screenMetrics == Constants.SCREEN_METRICS_LARGE_TABLET)
            val isSmallTablet: Boolean = (screenMetrics == Constants.SCREEN_METRICS_SMALL_TABLET)
            val densityDpi: Int = res.getDisplayMetrics().densityDpi
            val hasLowDensityScreen: Boolean = (densityDpi < DisplayMetrics.DENSITY_HIGH)
            val needsTheHack: Boolean = isLargeTablet || (isSmallTablet && hasLowDensityScreen)
            if (DEBUG_MODE) {
                val sw: Int = res.getConfiguration().smallestScreenWidthDp
                Log.d(
                    TAG, ("needsProximateBogusDownMoveUpEventHack=" + needsTheHack
                            + " smallestScreenWidthDp=" + sw + " densityDpi=" + densityDpi
                            + " screenMetrics=" + screenMetrics)
                )
            }
            sNeedsProximateBogusDownMoveUpEventHack = needsTheHack
        }
    }
}
