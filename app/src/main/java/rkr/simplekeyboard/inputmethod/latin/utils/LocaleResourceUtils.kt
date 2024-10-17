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
package rkr.simplekeyboard.inputmethod.latin.utils

import android.content.Context
import android.content.res.Resources
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import java.util.Locale
import kotlin.concurrent.Volatile

/**
 * A helper class to deal with displaying locales.
 */
object LocaleResourceUtils {
    // This reference class {@link R} must be located in the same package as LatinIME.java.
    private val RESOURCE_PACKAGE_NAME: String = R::class.java.getPackage()!!.name

    @Volatile
    private var sInitialized = false
    private val sInitializeLock = Any()
    private lateinit var sResources: Resources

    // Exceptional locale whose name should be displayed in Locale.ROOT.
    private val sExceptionalLocaleDisplayedInRootLocale = HashMap<String?, Int>()

    // Exceptional locale to locale name resource id map.
    private val sExceptionalLocaleToNameIdsMap = HashMap<String, Int>()
    private const val LOCALE_NAME_RESOURCE_PREFIX = "string/locale_name_"
    private const val LOCALE_NAME_RESOURCE_IN_ROOT_LOCALE_PREFIX =
        "string/locale_name_in_root_locale_"

    // Note that this initialization method can be called multiple times.
    fun init(context: Context) {
        synchronized(sInitializeLock) {
            if (!sInitialized) {
                initLocked(context)
                sInitialized = true
            }
        }
    }

    private fun initLocked(context: Context) {
        val res = context.resources
        sResources = res

        val exceptionalLocaleInRootLocale = res.getStringArray(
            R.array.locale_displayed_in_root_locale
        )
        for (i in exceptionalLocaleInRootLocale.indices) {
            val localeString = exceptionalLocaleInRootLocale[i]
            val resourceName = LOCALE_NAME_RESOURCE_IN_ROOT_LOCALE_PREFIX + localeString
            val resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME)
            sExceptionalLocaleDisplayedInRootLocale[localeString] = resId
        }

        val exceptionalLocales = res.getStringArray(R.array.locale_exception_keys)
        for (i in exceptionalLocales.indices) {
            val localeString = exceptionalLocales[i]
            val resourceName = LOCALE_NAME_RESOURCE_PREFIX + localeString
            val resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME)
            sExceptionalLocaleToNameIdsMap[localeString] = resId
        }
    }

    private fun getDisplayLocale(localeString: String): Locale {
        if (sExceptionalLocaleDisplayedInRootLocale.containsKey(localeString)) {
            return Locale.ROOT
        }
        return LocaleUtils.constructLocaleFromString(localeString)
    }

    /**
     * Get the full display name of the locale in the system's locale.
     * For example in an English system, en_US: "English (US)", fr_CA: "French (Canada)"
     * @param localeString the locale to display.
     * @return the full display name of the locale.
     */
    fun getLocaleDisplayNameInSystemLocale(
        localeString: String
    ): String {
        val displayLocale = sResources.configuration.locale
        return getLocaleDisplayNameInternal(localeString, displayLocale)
    }

    /**
     * Get the full display name of the locale in its locale.
     * For example, en_US: "English (US)", fr_CA: "Français (Canada)"
     * @param localeString the locale to display.
     * @return the full display name of the locale.
     */
    fun getLocaleDisplayNameInLocale(localeString: String): String {
        val displayLocale = getDisplayLocale(localeString)
        return getLocaleDisplayNameInternal(localeString, displayLocale)
    }

    /**
     * Get the display name of the language in the system's locale.
     * For example in an English system, en_US: "English", fr_CA: "French"
     * @param localeString the locale to display.
     * @return the display name of the language.
     */
    fun getLanguageDisplayNameInSystemLocale(
        localeString: String
    ): String {
        val displayLocale = sResources.configuration.locale
        val languageString =
            if (sExceptionalLocaleDisplayedInRootLocale.containsKey(localeString)) {
                localeString
            } else {
                LocaleUtils.constructLocaleFromString(
                    localeString
                ).language
            }
        return getLocaleDisplayNameInternal(languageString, displayLocale)
    }

    /**
     * Get the display name of the language in its locale.
     * For example, en_US: "English", fr_CA: "Français"
     * @param localeString the locale to display.
     * @return the display name of the language.
     */
    fun getLanguageDisplayNameInLocale(localeString: String): String {
        val displayLocale = getDisplayLocale(localeString)
        val languageString = if (sExceptionalLocaleDisplayedInRootLocale.containsKey(
                localeString
            )
        ) {
            localeString
        } else {
            LocaleUtils.constructLocaleFromString(
                localeString
            ).language
        }
        return getLocaleDisplayNameInternal(languageString, displayLocale)
    }

    private fun getLocaleDisplayNameInternal(
        localeString: String,
        displayLocale: Locale
    ): String {
        val exceptionalNameResId = if (displayLocale == Locale.ROOT
            && sExceptionalLocaleDisplayedInRootLocale.containsKey(localeString)
        ) {
            sExceptionalLocaleDisplayedInRootLocale[localeString]
        } else if (sExceptionalLocaleToNameIdsMap.containsKey(localeString)) {
            sExceptionalLocaleToNameIdsMap[localeString]
        } else {
            null
        }
        val displayName = if (exceptionalNameResId != null) {
            sResources.getString(exceptionalNameResId)
        } else {
            LocaleUtils.constructLocaleFromString(
                localeString
            ).getDisplayName(displayLocale)
        }
        return StringUtils.capitalizeFirstCodePoint(displayName, displayLocale)
    }
}
