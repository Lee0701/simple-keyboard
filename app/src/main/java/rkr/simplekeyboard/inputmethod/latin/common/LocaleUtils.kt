/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rkr.simplekeyboard.inputmethod.latin.common

import android.content.res.Resources
import android.os.Build
import android.text.TextUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import java.util.Locale

/**
 * A class to help with handling Locales in string form.
 *
 * This file has the same meaning and features (and shares all of its code) with the one with the
 * same name in Latin IME. They need to be kept synchronized; for any update/bugfix to
 * this file, consider also updating/fixing the version in Latin IME.
 */
object LocaleUtils {
    // Locale match level constants.
    // A higher level of match is guaranteed to have a higher numerical value.
    // Some room is left within constants to add match cases that may arise necessary
    // in the future, for example differentiating between the case where the countries
    // are both present and different, and the case where one of the locales does not
    // specify the countries. This difference is not needed now.
    private val sLocaleCache = HashMap<String, Locale>()

    /**
     * Creates a locale from a string specification.
     * @param localeString a string specification of a locale, in a format of "ll_cc_variant" where
     * "ll" is a language code, "cc" is a country code.
     */
    fun constructLocaleFromString(localeString: String): Locale? {
        synchronized(sLocaleCache) {
            if (sLocaleCache.containsKey(localeString)) {
                return sLocaleCache[localeString]
            }
            val elements = localeString.split("_".toRegex(), limit = 3).toTypedArray()
            val locale = if (elements.size == 1) {
                Locale(elements[0] /* language */)
            } else if (elements.size == 2) {
                Locale(elements[0],  /* language */elements[1] /* country */)
            } else { // localeParams.length == 3
                Locale(
                    elements[0],  /* language */elements[1],  /* country */
                    elements[2] /* variant */
                )
            }
            sLocaleCache[localeString] =
                locale
            return locale
        }
    }

    /**
     * Creates a string specification for a locale.
     * @param locale the locale.
     * @return a string specification of a locale, in a format of "ll_cc_variant" where "ll" is a
     * language code, "cc" is a country code.
     */
    fun getLocaleString(locale: Locale): String {
        if (!TextUtils.isEmpty(locale.variant)) {
            return locale.language + "_" + locale.country + "_" + locale.variant
        }
        if (!TextUtils.isEmpty(locale.country)) {
            return locale.language + "_" + locale.country
        }
        return locale.language
    }

    /**
     * Get the closest matching locale. This searches by:
     * 1. [Locale.equals]
     * 2. Language, Country, and Variant match
     * 3. Language and Country match
     * 4. Language matches
     * @param localeToMatch the locale to match.
     * @param options a collection of locales to find the best match.
     * @return the locale from the collection that is the best match for the specified locale or
     * null if nothing matches.
     */
    fun findBestLocale(
        localeToMatch: Locale,
        options: Collection<Locale>
    ): Locale? {
        // Find the best subtype based on a straightforward matching algorithm.
        // TODO: Use LocaleList#getFirstMatch() instead.
        for (locale in options) {
            if (locale == localeToMatch) {
                return locale
            }
        }
        for (locale in options) {
            if (locale.language == localeToMatch.language &&
                locale.country == localeToMatch.country &&
                locale.variant == localeToMatch.variant
            ) {
                return locale
            }
        }
        for (locale in options) {
            if (locale.language == localeToMatch.language &&
                locale.country == localeToMatch.country
            ) {
                return locale
            }
        }
        for (locale in options) {
            if (locale.language == localeToMatch.language) {
                return locale
            }
        }
        return null
    }

    val systemLocales: List<Locale>
        /**
         * Get the list of locales enabled in the system.
         * @return the list of locales enabled in the system.
         */
        get() {
            val locales =
                ArrayList<Locale>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList =
                    Resources.getSystem().configuration.locales
                for (i in 0 until localeList.size()) {
                    locales.add(localeList[i])
                }
            } else {
                locales.add(Resources.getSystem().configuration.locale)
            }
            return locales
        }

    /**
     * Comparator for [Locale] to order them alphabetically
     * first.
     */
    class LocaleComparator : Comparator<Locale> {
        override fun compare(a: Locale, b: Locale): Int {
            if (a == b) {
                // ensure that this is consistent with equals
                return 0
            }
            val aDisplay =
                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(getLocaleString(a))
            val bDisplay =
                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(getLocaleString(b))
            val result = aDisplay.compareTo(bDisplay, ignoreCase = true)
            if (result != 0) {
                return result
            }
            // ensure that non-equal objects are distinguished to be consistent with equals
            return if (a.hashCode() > b.hashCode()) 1 else -1
        }
    }
}
