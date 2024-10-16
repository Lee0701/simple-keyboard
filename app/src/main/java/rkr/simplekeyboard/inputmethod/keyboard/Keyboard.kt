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
package rkr.simplekeyboard.inputmethod.keyboard

import android.util.SparseArray
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyVisualAttributes
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardIconsSet
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardParams
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 *
 * The layout file for a keyboard contains XML that looks like the following snippet:
 * <pre>
 * &lt;Keyboard
 * latin:keyWidth="10%p"
 * latin:rowHeight="50px"
 * latin:horizontalGap="2%p"
 * latin:verticalGap="2%p" &gt;
 * &lt;Row latin:keyWidth="10%p" &gt;
 * &lt;Key latin:keyLabel="A" /&gt;
 * ...
 * &lt;/Row&gt;
 * ...
 * &lt;/Keyboard&gt;
</pre> *
 */
open class Keyboard(params: KeyboardParams) {
    val mId: KeyboardId?

    /** Total height of the keyboard, including the padding and keys  */
    val mOccupiedHeight: Int

    /** Total width of the keyboard, including the padding and keys  */
    val mOccupiedWidth: Int

    /** The padding below the keyboard  */
    val mBottomPadding: Float

    /** Default gap between rows  */
    val mVerticalGap: Float

    /** Default gap between columns  */
    val mHorizontalGap: Float

    /** Per keyboard key visual parameters  */
    val mKeyVisualAttributes: KeyVisualAttributes?

    val mMostCommonKeyHeight: Int
    val mMostCommonKeyWidth: Int

    /** More keys keyboard template  */
    val mMoreKeysTemplate: Int

    /**
     * Return the sorted list of keys of this keyboard.
     * The keys are sorted from top-left to bottom-right order.
     * The list may contain [Key.Spacer] object as well.
     * @return the sorted unmodifiable list of [Key]s of this keyboard.
     */
    /** List of keys in this keyboard  */
    val sortedKeys: List<Key>
    val mShiftKeys: List<Key?>
    val mAltCodeKeysWhileTyping: List<Key?>
    val mIconsSet: KeyboardIconsSet

    private val mKeyCache: SparseArray<Key?> = SparseArray()

    private val mProximityInfo: ProximityInfo

    init {
        mId = params.mId
        mOccupiedHeight = params.mOccupiedHeight
        mOccupiedWidth = params.mOccupiedWidth
        mMostCommonKeyHeight = params.mMostCommonKeyHeight
        mMostCommonKeyWidth = params.mMostCommonKeyWidth
        mMoreKeysTemplate = params.mMoreKeysTemplate
        mKeyVisualAttributes = params.mKeyVisualAttributes
        mBottomPadding = params.mBottomPadding
        mVerticalGap = params.mVerticalGap
        mHorizontalGap = params.mHorizontalGap

        sortedKeys = Collections.unmodifiableList(ArrayList(params.mSortedKeys))
        mShiftKeys = Collections.unmodifiableList(params.mShiftKeys)
        mAltCodeKeysWhileTyping = Collections.unmodifiableList(params.mAltCodeKeysWhileTyping)
        mIconsSet = params.mIconsSet

        mProximityInfo = ProximityInfo(
            params.mGridWidth, params.mGridHeight,
            mOccupiedWidth, mOccupiedHeight, sortedKeys
        )
    }

    fun getKey(code: Int): Key? {
        if (code == Constants.CODE_UNSPECIFIED) {
            return null
        }
        synchronized(mKeyCache) {
            val index: Int = mKeyCache.indexOfKey(code)
            if (index >= 0) {
                return mKeyCache.valueAt(index)
            }

            for (key: Key in sortedKeys) {
                if (key.code == code) {
                    mKeyCache.put(code, key)
                    return key
                }
            }
            mKeyCache.put(code, null)
            return null
        }
    }

    fun hasKey(aKey: Key): Boolean {
        if (mKeyCache.indexOfValue(aKey) >= 0) {
            return true
        }

        for (key: Key in sortedKeys) {
            if (key === aKey) {
                mKeyCache.put(key.code, key)
                return true
            }
        }
        return false
    }

    override fun toString(): String {
        return mId.toString()
    }

    /**
     * Returns the array of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the list of the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    fun getNearestKeys(x: Int, y: Int): List<Key?>? {
        // Avoid dead pixels at edges of the keyboard
        val adjustedX: Int =
            max(0.0, min(x.toDouble(), (mOccupiedWidth - 1).toDouble())).toInt()
        val adjustedY: Int =
            max(0.0, min(y.toDouble(), (mOccupiedHeight - 1).toDouble())).toInt()
        return mProximityInfo.getNearestKeys(adjustedX, adjustedY)
    }
}
