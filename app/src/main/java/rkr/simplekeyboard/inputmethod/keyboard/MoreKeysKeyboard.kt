/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context
import android.graphics.Paint
import android.util.Log
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.MoreKeysKeyboardView
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardBuilder
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.MoreKeySpec
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import rkr.simplekeyboard.inputmethod.latin.utils.TypefaceUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MoreKeysKeyboard internal constructor(params: MoreKeysKeyboardParams) : Keyboard(params) {
    val defaultCoordX: Int

    init {
        defaultCoordX = Math.round(
            (params.defaultKeyCoordX + params.mOffsetX
                    + (params.mDefaultKeyPaddedWidth - params.mHorizontalGap) / 2)
        )
    }

    class MoreKeysKeyboardParams : KeyboardParams() {
        var mIsMoreKeysFixedOrder: Boolean = false

        /* package */
        var mTopRowAdjustment: Int = 0
        var mNumRows: Int = 0
        var mNumColumns: Int = 0
        var mTopKeys: Int = 0
        var mLeftKeys: Int = 0
        var mRightKeys: Int = 0 // includes default key.
        var mColumnWidth: Float = 0f
        var mOffsetX: Float = 0f

        /**
         * Set keyboard parameters of more keys keyboard.
         *
         * @param numKeys number of keys in this more keys keyboard.
         * @param numColumn number of columns of this more keys keyboard.
         * @param keyPaddedWidth more keys keyboard key width in pixel, including horizontal gap.
         * @param rowHeight more keys keyboard row height in pixel, including vertical gap.
         * @param coordXInParent coordinate x of the key preview in parent keyboard.
         * @param parentKeyboardWidth parent keyboard width in pixel.
         * @param isMoreKeysFixedColumn true if more keys keyboard should have
         * `numColumn` columns. Otherwise more keys keyboard should have
         * `numColumn` columns at most.
         * @param isMoreKeysFixedOrder true if the order of more keys is determined by the order in
         * the more keys' specification. Otherwise the order of more keys is automatically
         * determined.
         */
        fun setParameters(
            numKeys: Int, numColumn: Int,
            keyPaddedWidth: Float, rowHeight: Float,
            coordXInParent: Float, parentKeyboardWidth: Int,
            isMoreKeysFixedColumn: Boolean,
            isMoreKeysFixedOrder: Boolean
        ) {
            // Add the horizontal padding because there is no horizontal gap on the outside edge,
            // but it is included in the key width, so this compensates for simple division and
            // comparison.
            val availableWidth: Float = (parentKeyboardWidth - mLeftPadding - mRightPadding
                    + mHorizontalGap)
            require(!(availableWidth < keyPaddedWidth)) {
                ("Keyboard is too small to hold more keys: "
                        + availableWidth + " " + keyPaddedWidth)
            }
            mIsMoreKeysFixedOrder = isMoreKeysFixedOrder
            mDefaultKeyPaddedWidth = keyPaddedWidth
            mDefaultRowHeight = rowHeight

            val maxColumns: Int = getMaxKeys(availableWidth, keyPaddedWidth)
            if (isMoreKeysFixedColumn) {
                val requestedNumColumns: Int =
                    min(numKeys.toDouble(), numColumn.toDouble()).toInt()
                if (maxColumns < requestedNumColumns) {
                    Log.e(
                        TAG, ("Keyboard is too small to hold the requested more keys columns: "
                                + availableWidth + " " + keyPaddedWidth + " " + numKeys + " "
                                + requestedNumColumns + ". The number of columns was reduced.")
                    )
                    mNumColumns = maxColumns
                } else {
                    mNumColumns = requestedNumColumns
                }
                mNumRows = getNumRows(numKeys, mNumColumns)
            } else {
                val defaultNumColumns: Int =
                    min(maxColumns.toDouble(), numColumn.toDouble()).toInt()
                mNumRows = getNumRows(numKeys, defaultNumColumns)
                mNumColumns = getOptimizedColumns(numKeys, defaultNumColumns, mNumRows)
            }
            val topKeys: Int = numKeys % mNumColumns
            mTopKeys = if (topKeys == 0) mNumColumns else topKeys

            val numLeftKeys: Int = (mNumColumns - 1) / 2
            val numRightKeys: Int = mNumColumns - numLeftKeys // including default key.
            // Determine the maximum number of keys we can lay out on both side of the left edge of
            // a key centered on the parent key. Also, account for horizontal padding because there
            // is no horizontal gap on the outside edge.
            val leftWidth: Float = max(
                (coordXInParent - mLeftPadding - keyPaddedWidth / 2
                        + mHorizontalGap / 2).toDouble(), 0.0
            ).toFloat()
            val rightWidth: Float = max(
                (parentKeyboardWidth - coordXInParent
                        + keyPaddedWidth / 2 - mRightPadding + mHorizontalGap / 2).toDouble(), 0.0
            ).toFloat()
            var maxLeftKeys: Int = getMaxKeys(leftWidth, keyPaddedWidth)
            var maxRightKeys: Int = getMaxKeys(rightWidth, keyPaddedWidth)
            // Handle the case where the number of columns fits but doesn't have enough room
            // for the default key to be centered on the parent key.
            if (numKeys >= mNumColumns && mNumColumns == maxColumns && maxLeftKeys + maxRightKeys < maxColumns) {
                val extraLeft: Float = leftWidth - maxLeftKeys * keyPaddedWidth
                val extraRight: Float = rightWidth - maxRightKeys * keyPaddedWidth
                // Put the extra key on whatever side has more space
                if (extraLeft > extraRight) {
                    maxLeftKeys++
                } else {
                    maxRightKeys++
                }
            }

            val leftKeys: Int
            val rightKeys: Int
            if (numLeftKeys > maxLeftKeys) {
                leftKeys = maxLeftKeys
                rightKeys = mNumColumns - leftKeys
            } else if (numRightKeys > maxRightKeys) {
                // Make sure the default key is included even if it doesn't exactly fit (the default
                // key just won't be completely centered on the parent key)
                rightKeys = max(maxRightKeys.toDouble(), 1.0).toInt()
                leftKeys = mNumColumns - rightKeys
            } else {
                leftKeys = numLeftKeys
                rightKeys = numRightKeys
            }
            mLeftKeys = leftKeys
            mRightKeys = rightKeys

            // Adjustment of the top row.
            mTopRowAdjustment = topRowAdjustment
            mColumnWidth = mDefaultKeyPaddedWidth
            mBaseWidth = mNumColumns * mColumnWidth
            // Need to subtract the right most column's gutter only.
            mOccupiedWidth = Math.round(mBaseWidth + mLeftPadding + mRightPadding - mHorizontalGap)
            mBaseHeight = mNumRows * mDefaultRowHeight
            // Need to subtract the bottom row's gutter only.
            mOccupiedHeight = Math.round(mBaseHeight + mTopPadding + mBottomPadding - mVerticalGap)

            // The proximity grid size can be reduced because the more keys keyboard is probably
            // smaller and doesn't need extra precision from smaller cells.
            mGridWidth = min(mGridWidth.toDouble(), mNumColumns.toDouble()).toInt()
            mGridHeight = min(mGridHeight.toDouble(), mNumRows.toDouble()).toInt()
        }

        private val topRowAdjustment: Int
            get() {
                val numOffCenterKeys: Int =
                    abs((mRightKeys - 1 - mLeftKeys).toDouble()).toInt()
                // Don't center if there are more keys in the top row than can be centered around the
                // default more key or if there is an odd number of keys in the top row (already will
                // be centered).
                if (mTopKeys > mNumColumns - numOffCenterKeys || mTopKeys % 2 == 1) {
                    return 0
                }
                return -1
            }

        // Return key position according to column count (0 is default).
        /* package */
        fun getColumnPos(n: Int): Int {
            return if (mIsMoreKeysFixedOrder) getFixedOrderColumnPos(n) else getAutomaticColumnPos(n)
        }

        private fun getFixedOrderColumnPos(n: Int): Int {
            val col: Int = n % mNumColumns
            val row: Int = n / mNumColumns
            if (!isTopRow(row)) {
                return col - mLeftKeys
            }
            val rightSideKeys: Int = mTopKeys / 2
            val leftSideKeys: Int = mTopKeys - (rightSideKeys + 1)
            val pos: Int = col - leftSideKeys
            val numLeftKeys: Int = mLeftKeys + mTopRowAdjustment
            val numRightKeys: Int = mRightKeys - 1
            if (numRightKeys >= rightSideKeys && numLeftKeys >= leftSideKeys) {
                return pos
            } else if (numRightKeys < rightSideKeys) {
                return pos - (rightSideKeys - numRightKeys)
            } else { // numLeftKeys < leftSideKeys
                return pos + (leftSideKeys - numLeftKeys)
            }
        }

        private fun getAutomaticColumnPos(n: Int): Int {
            val col: Int = n % mNumColumns
            val row: Int = n / mNumColumns
            var leftKeys: Int = mLeftKeys
            if (isTopRow(row)) {
                leftKeys += mTopRowAdjustment
            }
            if (col == 0) {
                // default position.
                return 0
            }

            var pos: Int = 0
            var right: Int = 1 // include default position key.
            var left: Int = 0
            var i: Int = 0
            while (true) {
                // Assign right key if available.
                if (right < mRightKeys) {
                    pos = right
                    right++
                    i++
                }
                if (i >= col) break
                // Assign left key if available.
                if (left < leftKeys) {
                    left++
                    pos = -left
                    i++
                }
                if (i >= col) break
            }
            return pos
        }

        val defaultKeyCoordX: Float
            get() {
                return mLeftKeys * mColumnWidth + mLeftPadding
            }

        fun getX(n: Int, row: Int): Float {
            val x: Float = getColumnPos(n) * mColumnWidth + defaultKeyCoordX
            if (isTopRow(row)) {
                return x + mTopRowAdjustment * (mColumnWidth / 2)
            }
            return x
        }

        fun getY(row: Int): Float {
            return (mNumRows - 1 - row) * mDefaultRowHeight + mTopPadding
        }

        private fun isTopRow(rowCount: Int): Boolean {
            return mNumRows > 1 && rowCount == mNumRows - 1
        }

        companion object {
            private fun getTopRowEmptySlots(numKeys: Int, numColumns: Int): Int {
                val remainings: Int = numKeys % numColumns
                return if (remainings == 0) 0 else numColumns - remainings
            }

            private fun getOptimizedColumns(
                numKeys: Int, maxColumns: Int,
                numRows: Int
            ): Int {
                var numColumns: Int =
                    min(numKeys.toDouble(), maxColumns.toDouble()).toInt()
                while (getTopRowEmptySlots(numKeys, numColumns) >= numRows) {
                    numColumns--
                }
                return numColumns
            }

            private fun getNumRows(numKeys: Int, numColumn: Int): Int {
                return (numKeys + numColumn - 1) / numColumn
            }

            private fun getMaxKeys(keyboardWidth: Float, keyPaddedWidth: Float): Int {
                // This is effectively the same as returning (int)(keyboardWidth / keyPaddedWidth)
                // except this handles floating point errors better since rounding in the wrong
                // directing here doesn't cause an issue, but truncating incorrectly from an error
                // could be a problem (eg: the keyboard width is an exact multiple of the key width
                // could return one less than the expected number).
                val maxKeys: Int = Math.round(keyboardWidth / keyPaddedWidth)
                if (maxKeys * keyPaddedWidth > keyboardWidth + FLOAT_THRESHOLD) {
                    return maxKeys - 1
                }
                return maxKeys
            }
        }
    }

    class Builder(
        context: Context, key: Key, keyboard: Keyboard,
        isSingleMoreKeyWithPreview: Boolean, keyPreviewVisibleWidth: Int,
        keyPreviewVisibleHeight: Int, paintToMeasure: Paint
    ) :
        KeyboardBuilder<MoreKeysKeyboardParams>(context, MoreKeysKeyboardParams()) {
        private val mParentKey: Key

        /**
         * The builder of MoreKeysKeyboard.
         * @param context the context of [MoreKeysKeyboardView].
         * @param key the [Key] that invokes more keys keyboard.
         * @param keyboard the [Keyboard] that contains the parentKey.
         * @param isSingleMoreKeyWithPreview true if the `key` has just a single
         * "more key" and its key popup preview is enabled.
         * @param keyPreviewVisibleWidth the width of visible part of key popup preview.
         * @param keyPreviewVisibleHeight the height of visible part of key popup preview
         * @param paintToMeasure the [Paint] object to measure a "more key" width
         */
        init {
            load(keyboard.mMoreKeysTemplate, keyboard.mId)

            // TODO: More keys keyboard's vertical gap is currently calculated heuristically.
            // Should revise the algorithm.
            mParams!!.mVerticalGap = keyboard.mVerticalGap / 2
            // This {@link MoreKeysKeyboard} is invoked from the <code>key</code>.
            mParentKey = key

            val keyPaddedWidth: Float
            val rowHeight: Float
            if (isSingleMoreKeyWithPreview) {
                // Use pre-computed width and height if this more keys keyboard has only one key to
                // mitigate visual flicker between key preview and more keys keyboard.
                // The bottom paddings don't need to be considered because the vertical positions
                // of both backgrounds and the keyboard were already adjusted with their bottom
                // paddings deducted. The keyboard's left/right/top paddings do need to be deducted
                // so the key including the paddings matches the key preview.
                val keyboardHorizontalPadding: Float = (mParams.mLeftPadding
                        + mParams.mRightPadding)
                val baseKeyPaddedWidth: Float = keyPreviewVisibleWidth + mParams.mHorizontalGap
                if (keyboardHorizontalPadding > baseKeyPaddedWidth - FLOAT_THRESHOLD) {
                    // If the padding doesn't fit we'll just add it outside of the key preview.
                    keyPaddedWidth = baseKeyPaddedWidth
                } else {
                    keyPaddedWidth = baseKeyPaddedWidth - keyboardHorizontalPadding
                    // Keep the more keys keyboard with uneven padding lined up with the key
                    // preview rather than centering the more keys keyboard's key with the parent
                    // key.
                    mParams.mOffsetX = (mParams.mRightPadding - mParams.mLeftPadding) / 2
                }
                val baseKeyPaddedHeight: Float = keyPreviewVisibleHeight + mParams.mVerticalGap
                if (mParams.mTopPadding > baseKeyPaddedHeight - FLOAT_THRESHOLD) {
                    // If the padding doesn't fit we'll just add it outside of the key preview.
                    rowHeight = baseKeyPaddedHeight
                } else {
                    rowHeight = baseKeyPaddedHeight - mParams.mTopPadding
                }
            } else {
                val defaultKeyWidth: Float = (mParams.mDefaultKeyPaddedWidth
                        - mParams.mHorizontalGap)
                val padding: Float = (context.getResources().getDimension(
                    R.dimen.config_more_keys_keyboard_key_horizontal_padding
                )
                        + (if (key.hasLabelsInMoreKeys())
                    defaultKeyWidth * LABEL_PADDING_RATIO
                else
                    0.0f))
                keyPaddedWidth = (getMaxKeyWidth(key, defaultKeyWidth, padding, paintToMeasure)
                        + mParams.mHorizontalGap)
                rowHeight = keyboard.mMostCommonKeyHeight + keyboard.mVerticalGap
            }
            val moreKeys: Array<MoreKeySpec?>? = key.moreKeys
            mParams.setParameters(
                moreKeys!!.size, key.moreKeysColumnNumber, keyPaddedWidth,
                rowHeight, key.x + key.width / 2f, keyboard.mId!!.mWidth,
                key.isMoreKeysFixedColumn, key.isMoreKeysFixedOrder
            )
        }

        override fun build(): MoreKeysKeyboard {
            val params: MoreKeysKeyboardParams = mParams
            val moreKeyFlags: Int = mParentKey.moreKeyLabelFlags
            val moreKeys: Array<MoreKeySpec?>? = mParentKey.moreKeys
            for (n in moreKeys!!.indices) {
                val moreKeySpec: MoreKeySpec? = moreKeys[n]
                val row: Int = n / params.mNumColumns
                val width: Float = params.mDefaultKeyPaddedWidth - params.mHorizontalGap
                val height: Float = params.mDefaultRowHeight - params.mVerticalGap
                val keyLeftEdge: Float = params.getX(n, row)
                val keyTopEdge: Float = params.getY(row)
                val keyRightEdge: Float = keyLeftEdge + width
                val keyBottomEdge: Float = keyTopEdge + height

                val keyboardLeftEdge: Float = params.mLeftPadding
                val keyboardRightEdge: Float = params.mOccupiedWidth - params.mRightPadding
                val keyboardTopEdge: Float = params.mTopPadding
                val keyboardBottomEdge: Float = params.mOccupiedHeight - params.mBottomPadding

                val keyLeftPadding: Float = if (keyLeftEdge < keyboardLeftEdge + FLOAT_THRESHOLD)
                    params.mLeftPadding
                else
                    params.mHorizontalGap / 2
                val keyRightPadding: Float = if (keyRightEdge > keyboardRightEdge - FLOAT_THRESHOLD)
                    params.mRightPadding
                else
                    params.mHorizontalGap / 2
                val keyTopPadding: Float = if (keyTopEdge < keyboardTopEdge + FLOAT_THRESHOLD)
                    params.mTopPadding
                else
                    params.mVerticalGap / 2
                val keyBottomPadding: Float =
                    if (keyBottomEdge > keyboardBottomEdge - FLOAT_THRESHOLD)
                        params.mBottomPadding
                    else
                        params.mVerticalGap / 2

                val key = moreKeySpec?.buildKey(
                    keyLeftEdge, keyTopEdge, width, height,
                    keyLeftPadding, keyRightPadding, keyTopPadding, keyBottomPadding,
                    moreKeyFlags
                )
                params.onAddKey(key)
            }
            return MoreKeysKeyboard(params)
        }

        companion object {
            private const val LABEL_PADDING_RATIO: Float = 0.2f

            private fun getMaxKeyWidth(
                parentKey: Key, minKeyWidth: Float,
                padding: Float, paint: Paint
            ): Float {
                var maxWidth: Float = minKeyWidth
                for (spec: MoreKeySpec? in parentKey.moreKeys!!) {
                    val label: String? = spec?.mLabel
                    // If the label is single letter, minKeyWidth is enough to hold the label.
                    if (label != null && StringUtils.codePointCount(label) > 1) {
                        maxWidth = max(
                            maxWidth.toDouble(),
                            (TypefaceUtils.getStringWidth(label, paint) + padding).toDouble()
                        ).toFloat()
                    }
                }
                return maxWidth
            }
        }
    }

    companion object {
        private val TAG: String = MoreKeysKeyboard::class.java.getSimpleName()
        private const val FLOAT_THRESHOLD: Float = 0.0001f
    }
}
