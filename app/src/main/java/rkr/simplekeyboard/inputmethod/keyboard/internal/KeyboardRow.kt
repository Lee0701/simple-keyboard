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
package rkr.simplekeyboard.inputmethod.keyboard.internal

import android.content.res.Resources
import android.content.res.TypedArray
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.Key
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/**
 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
 * Some of the key size defaults can be overridden per row from what the [Keyboard]
 * defines.
 */
class KeyboardRow(
    res: Resources, params: KeyboardParams,
    parser: XmlPullParser?, y: Float
) {
    private val mParams: KeyboardParams

    /** The y coordinate of the top edge of all keys in the row, excluding the top padding.  */
    val keyY: Float

    /** The height of this row and all keys in it, including the top and bottom padding.  */
    val rowHeight: Float

    /** The top padding of all of the keys in the row.  */
    var keyTopPadding: Float = 0f

    /** The bottom padding of all of the keys in the row.  */
    var keyBottomPadding: Float = 0f

    /** A tracker for where the next key should start, excluding padding.  */
    private var mNextKeyXPos: Float

    /** The x coordinate of the left edge of the current key, excluding the left padding.  */
    var keyX: Float = 0f
        private set

    /** The width of the current key excluding the left and right padding.  */
    var keyWidth: Float = 0f
        private set

    /** The left padding of the current key.  */
    var keyLeftPadding: Float = 0f
        private set

    /** The right padding of the current key.  */
    var keyRightPadding: Float = 0f
        private set

    /** Flag indicating whether the previous key in the row was a spacer.  */
    private var mLastKeyWasSpacer: Boolean = false

    /** The x coordinate of the right edge of the previous key, excluding the right padding.  */
    private var mLastKeyRightEdge: Float = 0f

    private val mRowAttributesStack: ArrayDeque<RowAttributes> = ArrayDeque()

    // TODO: Add keyActionFlags.
    private class RowAttributes {
        /** Default padded width of a key in this row.  */
        val mDefaultKeyPaddedWidth: Float

        /** Default keyLabelFlags in this row.  */
        val mDefaultKeyLabelFlags: Int

        /** Default backgroundType for this row  */
        val mDefaultBackgroundType: Int

        /**
         * Parse and create key attributes. This constructor is used to parse Row tag.
         *
         * @param keyAttr an attributes array of Row tag.
         * @param defaultKeyPaddedWidth a default padded key width.
         * @param keyboardWidth the keyboard width that is required to calculate keyWidth attribute.
         */
        constructor(
            keyAttr: TypedArray, defaultKeyPaddedWidth: Float,
            keyboardWidth: Float
        ) {
            mDefaultKeyPaddedWidth = ResourceUtils.getFraction(
                keyAttr,
                R.styleable.Keyboard_Key_keyWidth, keyboardWidth, defaultKeyPaddedWidth
            )
            mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0)
            mDefaultBackgroundType = keyAttr.getInt(
                R.styleable.Keyboard_Key_backgroundType,
                Key.Companion.BACKGROUND_TYPE_NORMAL
            )
        }

        /**
         * Parse and update key attributes using default attributes. This constructor is used
         * to parse include tag.
         *
         * @param keyAttr an attributes array of include tag.
         * @param defaultRowAttr default Row attributes.
         * @param keyboardWidth the keyboard width that is required to calculate keyWidth attribute.
         */
        constructor(
            keyAttr: TypedArray, defaultRowAttr: RowAttributes,
            keyboardWidth: Float
        ) {
            mDefaultKeyPaddedWidth = ResourceUtils.getFraction(
                keyAttr,
                R.styleable.Keyboard_Key_keyWidth, keyboardWidth,
                defaultRowAttr.mDefaultKeyPaddedWidth
            )
            mDefaultKeyLabelFlags = (keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0)
                    or defaultRowAttr.mDefaultKeyLabelFlags)
            mDefaultBackgroundType = keyAttr.getInt(
                R.styleable.Keyboard_Key_backgroundType,
                defaultRowAttr.mDefaultBackgroundType
            )
        }
    }

    init {
        mParams = params
        val keyboardAttr: TypedArray = res.obtainAttributes(
            Xml.asAttributeSet(parser),
            R.styleable.Keyboard
        )
        if (y < FLOAT_THRESHOLD) {
            // The top row should use the keyboard's top padding instead of the vertical gap
            keyTopPadding = params.mTopPadding
        } else {
            // All of the vertical gap will be used as bottom padding rather than split between the
            // top and bottom because it is probably more likely for users to click below a key
            keyTopPadding = 0f
        }
        val baseRowHeight: Float = ResourceUtils.getDimensionOrFraction(
            keyboardAttr,
            R.styleable.Keyboard_rowHeight, params.mBaseHeight, params.mDefaultRowHeight
        )
        var keyHeight: Float = baseRowHeight - params.mVerticalGap
        val rowEndY: Float = y + keyTopPadding + keyHeight + params.mVerticalGap
        val keyboardBottomEdge: Float = params.mOccupiedHeight - params.mBottomPadding
        if (rowEndY > keyboardBottomEdge - FLOAT_THRESHOLD) {
            // The bottom row's padding should go to the bottom of the keyboard (this might be
            // slightly more than the keyboard's bottom padding if the rows don't add up to 100%).
            // We'll consider it the bottom row as long as the row's normal bottom padding overlaps
            // with the keyboard's bottom padding any amount.
            val keyEndY: Float = y + keyTopPadding + keyHeight
            val keyOverflow: Float = keyEndY - keyboardBottomEdge
            if (keyOverflow > FLOAT_THRESHOLD) {
                if (Math.round(keyOverflow) > 0) {
                    // Only bother logging an error when expected rounding wouldn't resolve this
                    Log.e(
                        TAG, ("The row is too tall to fit in the keyboard (" + keyOverflow
                                + " px). The height was reduced to fit.")
                    )
                }
                keyHeight =
                    max((keyboardBottomEdge - y - keyTopPadding).toDouble(), 0.0).toFloat()
            }
            keyBottomPadding =
                max((params.mOccupiedHeight - keyEndY).toDouble(), 0.0).toFloat()
        } else {
            keyBottomPadding = params.mVerticalGap
        }
        rowHeight = keyTopPadding + keyHeight + keyBottomPadding
        keyboardAttr.recycle()
        val keyAttr: TypedArray = res.obtainAttributes(
            Xml.asAttributeSet(parser),
            R.styleable.Keyboard_Key
        )
        mRowAttributesStack.push(
            RowAttributes(
                keyAttr, params.mDefaultKeyPaddedWidth, params.mBaseWidth
            )
        )
        keyAttr.recycle()

        keyY = y + keyTopPadding
        mLastKeyRightEdge = 0f
        mNextKeyXPos = params.mLeftPadding
    }

    fun pushRowAttributes(keyAttr: TypedArray) {
        val newAttributes: RowAttributes = RowAttributes(
            keyAttr, mRowAttributesStack.peek(), mParams.mBaseWidth
        )
        mRowAttributesStack.push(newAttributes)
    }

    fun popRowAttributes() {
        mRowAttributesStack.pop()
    }

    private val defaultKeyPaddedWidth: Float
        get() {
            return mRowAttributesStack.peek().mDefaultKeyPaddedWidth
        }

    val defaultKeyLabelFlags: Int
        get() {
            return mRowAttributesStack.peek().mDefaultKeyLabelFlags
        }

    val defaultBackgroundType: Int
        get() {
            return mRowAttributesStack.peek().mDefaultBackgroundType
        }

    /**
     * Update the x position for the next key based on what is set in the keyXPos attribute.
     * @param keyAttr the Key XML attributes array.
     */
    fun updateXPos(keyAttr: TypedArray?) {
        if (keyAttr == null || !keyAttr.hasValue(R.styleable.Keyboard_Key_keyXPos)) {
            return
        }

        // keyXPos is based on the base width, but we need to add in the keyboard padding to
        // determine the actual position in the keyboard.
        val keyXPos: Float = ResourceUtils.getFraction(
            keyAttr, R.styleable.Keyboard_Key_keyXPos,
            mParams.mBaseWidth, 0f
        ) + mParams.mLeftPadding
        // keyXPos shouldn't be less than mLastKeyRightEdge or this key will overlap the adjacent
        // key on its left hand side.
        if (keyXPos + FLOAT_THRESHOLD < mLastKeyRightEdge) {
            Log.e(
                TAG, ("The specified keyXPos (" + keyXPos
                        + ") is smaller than the next available x position (" + mLastKeyRightEdge
                        + "). The x position was increased to avoid overlapping keys.")
            )
            mNextKeyXPos = mLastKeyRightEdge
        } else {
            mNextKeyXPos = keyXPos
        }
    }

    /**
     * Determine the next key's dimensions so they can be retrieved using [.getKeyX],
     * [.getKeyWidth], etc.
     * @param keyAttr the Key XML attributes array.
     * @param isSpacer flag indicating if the key is a spacer.
     */
    fun setCurrentKey(keyAttr: TypedArray?, isSpacer: Boolean) {
        // Split gap on both sides of key
        val defaultGap: Float = mParams.mHorizontalGap / 2

        updateXPos(keyAttr)
        val keyboardRightEdge: Float = mParams.mOccupiedWidth - mParams.mRightPadding
        var keyWidth: Float
        if (isSpacer) {
            val leftGap: Float = min(
                (mNextKeyXPos - mLastKeyRightEdge - defaultGap).toDouble(),
                defaultGap.toDouble()
            ).toFloat()
            // Spacers don't have horizontal gaps but should include that space in its width
            keyX = mNextKeyXPos - leftGap
            keyWidth = getKeyWidth(keyAttr) + leftGap
            if (keyX + keyWidth + FLOAT_THRESHOLD < keyboardRightEdge) {
                // Add what is normally the default right gap for non-edge spacers
                keyWidth += defaultGap
            }
            keyLeftPadding = 0f
            keyRightPadding = 0f
        } else {
            keyX = mNextKeyXPos
            if (mLastKeyRightEdge < FLOAT_THRESHOLD || mLastKeyWasSpacer) {
                // The first key in the row and a key next to a spacer should have a left padding
                // that spans the available distance
                keyLeftPadding = keyX - mLastKeyRightEdge
            } else {
                // Split the gap between the adjacent keys
                keyLeftPadding = (keyX - mLastKeyRightEdge) / 2
            }
            keyWidth = getKeyWidth(keyAttr)
            // We can't know this before seeing the next key, so just use the default. The key can
            // be updated later.
            keyRightPadding = defaultGap
        }
        val keyOverflow: Float = keyX + keyWidth - keyboardRightEdge
        if (keyOverflow > FLOAT_THRESHOLD) {
            if (Math.round(keyOverflow) > 0) {
                // Only bother logging an error when expected rounding wouldn't resolve this
                Log.e(
                    TAG, ("The " + (if (isSpacer) "spacer" else "key")
                            + " is too wide to fit in the keyboard (" + keyOverflow
                            + " px). The width was reduced to fit.")
                )
            }
            keyWidth = max((keyboardRightEdge - keyX).toDouble(), 0.0).toFloat()
        }

        this.keyWidth = keyWidth

        // Calculations for the current key are done. Prep for the next key.
        mLastKeyRightEdge = keyX + keyWidth
        mLastKeyWasSpacer = isSpacer
        // Set the next key's default position. Spacers only add half because their width includes
        // what is normally the horizontal gap.
        mNextKeyXPos = mLastKeyRightEdge + (if (isSpacer) defaultGap else mParams.mHorizontalGap)
    }

    fun getKeyWidth(keyAttr: TypedArray?): Float {
        if (keyAttr == null) {
            return defaultKeyPaddedWidth - mParams.mHorizontalGap
        }
        val widthType: Int = ResourceUtils.getEnumValue(
            keyAttr,
            R.styleable.Keyboard_Key_keyWidth, KEYWIDTH_NOT_ENUM
        )
        when (widthType) {
            KEYWIDTH_FILL_RIGHT -> {
                // If keyWidth is fillRight, the actual key width will be determined to fill
                // out the area up to the right edge of the keyboard.
                val keyboardRightEdge: Float = mParams.mOccupiedWidth - mParams.mRightPadding
                return keyboardRightEdge - keyX
            }

            else -> return ResourceUtils.getFraction(
                keyAttr, R.styleable.Keyboard_Key_keyWidth,
                mParams.mBaseWidth, defaultKeyPaddedWidth
            ) - mParams.mHorizontalGap
        }
    }

    val keyHeight: Float
        get() {
            return rowHeight - keyTopPadding - keyBottomPadding
        }

    companion object {
        private val TAG: String = KeyboardRow::class.java.getSimpleName()
        private const val FLOAT_THRESHOLD: Float = 0.0001f

        // keyWidth enum constants
        private const val KEYWIDTH_NOT_ENUM: Int = 0
        private val KEYWIDTH_FILL_RIGHT: Int = -1
    }
}
