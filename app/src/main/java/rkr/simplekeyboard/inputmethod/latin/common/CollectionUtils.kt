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
package rkr.simplekeyboard.inputmethod.latin.common

/**
 * Utility methods for working with collections.
 */
object CollectionUtils {
    /**
     * Converts a sub-range of the given array to an ArrayList of the appropriate type.
     * @param array Array to be converted.
     * @param start First index inclusive to be converted.
     * @param end Last index exclusive to be converted.
     * @throws IllegalArgumentException if start or end are out of range or start &gt; end.
     */
    fun <E> arrayAsList(
        array: Array<E>, start: Int,
        end: Int
    ): ArrayList<E> {
        require(!(start < 0 || start > end || end > array.size)) {
            ("Invalid start: " + start + " end: " + end
                    + " with array.length: " + array.size)
        }

        val list = ArrayList<E>(end - start)
        for (i in start until end) {
            list.add(array[i])
        }
        return list
    }
}
