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

import android.text.TextUtils
import java.util.Arrays
import java.util.Locale

object StringUtils {
    private const val EMPTY_STRING = ""

    fun codePointCount(text: CharSequence?): Int {
        if (TextUtils.isEmpty(text)) {
            return 0
        }
        return Character.codePointCount(text, 0, text!!.length)
    }

    fun newSingleCodePointString(codePoint: Int): String {
        if (Character.charCount(codePoint) == 1) {
            // Optimization: avoid creating a temporary array for characters that are
            // represented by a single char value
            return codePoint.toChar().toString()
        }
        // For surrogate pair
        return String(Character.toChars(codePoint))
    }

    fun containsInArray(
        text: String,
        array: Array<String>
    ): Boolean {
        for (element in array) {
            if (text == element) {
                return true
            }
        }
        return false
    }

    /**
     * Comma-Splittable Text is similar to Comma-Separated Values (CSV) but has much simpler syntax.
     * Unlike CSV, Comma-Splittable Text has no escaping mechanism, so that the text can't contain
     * a comma character in it.
     */
    private const val SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT = ","

    fun containsInCommaSplittableText(
        text: String,
        extraValues: String
    ): Boolean {
        if (TextUtils.isEmpty(extraValues)) {
            return false
        }
        return containsInArray(
            text,
            extraValues.split(SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        )
    }

    fun removeFromCommaSplittableTextIfExists(
        text: String,
        extraValues: String
    ): String {
        if (TextUtils.isEmpty(extraValues)) {
            return EMPTY_STRING
        }
        val elements = extraValues.split(SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT.toRegex())
            .dropLastWhile { it.isEmpty() }.toTypedArray()
        if (!containsInArray(text, elements)) {
            return extraValues
        }
        val result = ArrayList<String?>(elements.size - 1)
        for (element in elements) {
            if (text != element) {
                result.add(element)
            }
        }
        return TextUtils.join(SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT, result)
    }

    fun capitalizeFirstCodePoint(
        s: String,
        locale: Locale
    ): String {
        if (s.length <= 1) {
            return s.uppercase(getLocaleUsedForToTitleCase(locale))
        }
        // Please refer to the comment below in
        // {@link #capitalizeFirstAndDowncaseRest(String,Locale)} as this has the same shortcomings
        val cutoff = s.offsetByCodePoints(0, 1)
        return (s.substring(0, cutoff).uppercase(getLocaleUsedForToTitleCase(locale))
                + s.substring(cutoff))
    }

    private val EMPTY_CODEPOINTS = intArrayOf()

    /**
     * Converts a range of a string to an array of code points.
     * @param charSequence the source string.
     * @param startIndex the start index inside the string in java chars, inclusive.
     * @param endIndex the end index inside the string in java chars, exclusive.
     * @return a new array of code points. At most endIndex - startIndex, but possibly less.
     */
    @JvmOverloads
    fun toCodePointArray(
        charSequence: CharSequence,
        startIndex: Int = 0, endIndex: Int = charSequence.length
    ): IntArray {
        val length = charSequence.length
        if (length <= 0) {
            return EMPTY_CODEPOINTS
        }
        val codePoints =
            IntArray(Character.codePointCount(charSequence, startIndex, endIndex))
        copyCodePointsAndReturnCodePointCount(
            codePoints, charSequence, startIndex, endIndex,
            false /* downCase */
        )
        return codePoints
    }

    /**
     * Copies the codepoints in a CharSequence to an int array.
     *
     * This method assumes there is enough space in the array to store the code points. The size
     * can be measured with Character#codePointCount(CharSequence, int, int) before passing to this
     * method. If the int array is too small, an ArrayIndexOutOfBoundsException will be thrown.
     * Also, this method makes no effort to be thread-safe. Do not modify the CharSequence while
     * this method is running, or the behavior is undefined.
     * This method can optionally downcase code points before copying them, but it pays no attention
     * to locale while doing so.
     *
     * @param destination the int array.
     * @param charSequence the CharSequence.
     * @param startIndex the start index inside the string in java chars, inclusive.
     * @param endIndex the end index inside the string in java chars, exclusive.
     * @param downCase if this is true, code points will be downcased before being copied.
     * @return the number of copied code points.
     */
    fun copyCodePointsAndReturnCodePointCount(
        destination: IntArray,
        charSequence: CharSequence, startIndex: Int, endIndex: Int,
        downCase: Boolean
    ): Int {
        var destIndex = 0
        var index = startIndex
        while (index < endIndex
        ) {
            val codePoint = Character.codePointAt(charSequence, index)
            // TODO: stop using this, as it's not aware of the locale and does not always do
            // the right thing.
            destination[destIndex] = if (downCase) codePoint.toChar().lowercase().toInt() else codePoint
            destIndex++
            index = Character.offsetByCodePoints(charSequence, index, 1)
        }
        return destIndex
    }

    fun toSortedCodePointArray(string: String): IntArray {
        val codePoints = toCodePointArray(string)
        Arrays.sort(codePoints)
        return codePoints
    }

    fun isIdenticalAfterUpcase(text: String): Boolean {
        val length = text.length
        var i = 0
        while (i < length) {
            val codePoint = text.codePointAt(i)
            if (Character.isLetter(codePoint) && !Character.isUpperCase(codePoint)) {
                return false
            }
            i += Character.charCount(codePoint)
        }
        return true
    }

    fun isIdenticalAfterDowncase(text: String): Boolean {
        val length = text.length
        var i = 0
        while (i < length) {
            val codePoint = text.codePointAt(i)
            if (Character.isLetter(codePoint) && !Character.isLowerCase(codePoint)) {
                return false
            }
            i += Character.charCount(codePoint)
        }
        return true
    }

    fun isIdenticalAfterCapitalizeEachWord(text: String): Boolean {
        var needsCapsNext = true
        val len = text.length
        var i = 0
        while (i < len) {
            val codePoint = text.codePointAt(i)
            if (Character.isLetter(codePoint)) {
                if ((needsCapsNext && !Character.isUpperCase(codePoint))
                    || (!needsCapsNext && !Character.isLowerCase(codePoint))
                ) {
                    return false
                }
            }
            // We need a capital letter next if this is a whitespace.
            needsCapsNext = Character.isWhitespace(codePoint)
            i = text.offsetByCodePoints(i, 1)
        }
        return true
    }

    // TODO: like capitalizeFirst*, this does not work perfectly for Dutch because of the IJ digraph
    // which should be capitalized together in *some* cases.
    fun capitalizeEachWord(text: String, locale: Locale): String {
        val builder = StringBuilder()
        var needsCapsNext = true
        val len = text.length
        var i = 0
        while (i < len) {
            val nextChar = text.substring(i, text.offsetByCodePoints(i, 1))
            if (needsCapsNext) {
                builder.append(nextChar.uppercase(locale))
            } else {
                builder.append(nextChar.lowercase(locale))
            }
            // We need a capital letter next if this is a whitespace.
            needsCapsNext = Character.isWhitespace(nextChar.codePointAt(0))
            i = text.offsetByCodePoints(i, 1)
        }
        return builder.toString()
    }

    private const val LANGUAGE_GREEK = "el"

    private fun getLocaleUsedForToTitleCase(locale: Locale): Locale {
        // In Greek locale {@link String#toUpperCase(Locale)} eliminates accents from its result.
        // In order to get accented upper case letter, {@link Locale#ROOT} should be used.
        if (LANGUAGE_GREEK == locale.language) {
            return Locale.ROOT
        }
        return locale
    }

    fun toTitleCaseOfKeyLabel(
        label: String?,
        locale: Locale
    ): String {
        if (label == null) {
            return label!!
        }
        return label.uppercase(getLocaleUsedForToTitleCase(locale))
    }

    fun toTitleCaseOfKeyCode(code: Int, locale: Locale): Int {
        if (!Constants.isLetterCode(code)) {
            return code
        }
        val label = newSingleCodePointString(code)
        val titleCaseLabel = toTitleCaseOfKeyLabel(label, locale)
        return if (codePointCount(titleCaseLabel) == 1)
            titleCaseLabel.codePointAt(0)
        else
            Constants.CODE_UNSPECIFIED
    }
}
