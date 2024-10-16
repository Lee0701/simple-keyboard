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

import java.util.Collections
import kotlin.math.min

class ProximityInfo internal constructor(
    gridWidth: Int, gridHeight: Int, minWidth: Int, height: Int,
    sortedKeys: List<Key>
) {
    private val mGridWidth: Int
    private val mGridHeight: Int
    private val mGridSize: Int
    private val mCellWidth: Int
    private val mCellHeight: Int

    // TODO: Find a proper name for mKeyboardMinWidth
    private val mKeyboardMinWidth: Int
    private val mKeyboardHeight: Int
    private val mSortedKeys: List<Key>
    private val mGridNeighbors: Array<List<Key?>>

    init {
        mGridWidth = gridWidth
        mGridHeight = gridHeight
        mGridSize = mGridWidth * mGridHeight
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth
        mCellHeight = (height + mGridHeight - 1) / mGridHeight
        mKeyboardMinWidth = minWidth
        mKeyboardHeight = height
        mSortedKeys = sortedKeys
        mGridNeighbors = arrayOfNulls<List<*>>(mGridSize)
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be more keys keyboard.
            return
        }
        computeNearestNeighbors()
    }

    private fun computeNearestNeighbors() {
        val keyCount: Int = mSortedKeys.size
        val gridSize: Int = mGridNeighbors.size
        val maxKeyRight: Int = mGridWidth * mCellWidth
        val maxKeyBottom: Int = mGridHeight * mCellHeight

        // For large layouts, 'neighborsFlatBuffer' is about 80k of memory: gridSize is usually 512,
        // keycount is about 40 and a pointer to a Key is 4 bytes. This contains, for each cell,
        // enough space for as many keys as there are on the keyboard. Hence, every
        // keycount'th element is the start of a new cell, and each of these virtual subarrays
        // start empty with keycount spaces available. This fills up gradually in the loop below.
        // Since in the practice each cell does not have a lot of neighbors, most of this space is
        // actually just empty padding in this fixed-size buffer.
        val neighborsFlatBuffer: Array<Key?> = arrayOfNulls(gridSize * keyCount)
        val neighborCountPerCell: IntArray = IntArray(gridSize)
        for (key: Key in mSortedKeys) {
            if (key.isSpacer()) continue

            // Iterate through all of the cells that overlap with the clickable region of the
            // current key and add the key as a neighbor.
            val keyX: Int = key.getX()
            val keyY: Int = key.getY()
            val keyTop: Int = keyY - key.getTopPadding()
            val keyBottom: Int = min(
                (keyY + key.getHeight() + key.getBottomPadding()).toDouble(),
                maxKeyBottom.toDouble()
            ).toInt()
            val keyLeft: Int = keyX - key.getLeftPadding()
            val keyRight: Int = min(
                (keyX + key.getWidth() + key.getRightPadding()).toDouble(),
                maxKeyRight.toDouble()
            ).toInt()
            val yDeltaToGrid: Int = keyTop % mCellHeight
            val xDeltaToGrid: Int = keyLeft % mCellWidth
            val yStart: Int = keyTop - yDeltaToGrid
            val xStart: Int = keyLeft - xDeltaToGrid
            var baseIndexOfCurrentRow: Int =
                (yStart / mCellHeight) * mGridWidth + (xStart / mCellWidth)
            var cellTop: Int = yStart
            while (cellTop < keyBottom) {
                var index: Int = baseIndexOfCurrentRow
                var cellLeft: Int = xStart
                while (cellLeft < keyRight) {
                    neighborsFlatBuffer.get(index * keyCount + neighborCountPerCell.get(index)) =
                        key
                    ++neighborCountPerCell.get(index)
                    ++index
                    cellLeft += mCellWidth
                }
                baseIndexOfCurrentRow += mGridWidth
                cellTop += mCellHeight
            }
        }

        for (i in 0 until gridSize) {
            val indexStart: Int = i * keyCount
            val indexEnd: Int = indexStart + neighborCountPerCell.get(i)
            val neighbors: ArrayList<Key?> = ArrayList(indexEnd - indexStart)
            for (index in indexStart until indexEnd) {
                neighbors.add(neighborsFlatBuffer.get(index))
            }
            mGridNeighbors.get(i) = Collections.unmodifiableList(neighbors)
        }
    }

    fun getNearestKeys(x: Int, y: Int): List<Key?> {
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            val index: Int = (y / mCellHeight) * mGridWidth + (x / mCellWidth)
            if (index < mGridSize) {
                return mGridNeighbors.get(index)
            }
        }
        return EMPTY_KEY_LIST
    }

    companion object {
        private val EMPTY_KEY_LIST: List<Key?> = emptyList<Key>()
    }
}
