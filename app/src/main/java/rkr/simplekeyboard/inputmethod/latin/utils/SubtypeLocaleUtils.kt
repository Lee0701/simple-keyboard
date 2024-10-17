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
package rkr.simplekeyboard.inputmethod.latin.utils

import android.content.res.Resources
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils
import java.util.Arrays
import java.util.Locale

/**
 * Utility methods for building subtypes for the supported locales.
 */
object SubtypeLocaleUtils {
    private const val LOCALE_AFRIKAANS = "af"
    private const val LOCALE_ARABIC = "ar"
    private const val LOCALE_AZERBAIJANI_AZERBAIJAN = "az_AZ"
    private const val LOCALE_BELARUSIAN_BELARUS = "be_BY"
    private const val LOCALE_BULGARIAN = "bg"
    private const val LOCALE_BENGALI_BANGLADESH = "bn_BD"
    private const val LOCALE_BENGALI_INDIA = "bn_IN"
    private const val LOCALE_CATALAN = "ca"
    private const val LOCALE_CZECH = "cs"
    private const val LOCALE_DANISH = "da"
    private const val LOCALE_GERMAN = "de"
    private const val LOCALE_GERMAN_SWITZERLAND = "de_CH"
    private const val LOCALE_GREEK = "el"
    private const val LOCALE_ENGLISH_INDIA = "en_IN"
    private const val LOCALE_ENGLISH_GREAT_BRITAIN = "en_GB"
    private const val LOCALE_ENGLISH_UNITED_STATES = "en_US"
    private const val LOCALE_ESPERANTO = "eo"
    private const val LOCALE_SPANISH = "es"
    private const val LOCALE_SPANISH_UNITED_STATES = "es_US"
    private const val LOCALE_SPANISH_LATIN_AMERICA = "es_419"
    private const val LOCALE_ESTONIAN_ESTONIA = "et_EE"
    private const val LOCALE_BASQUE_SPAIN = "eu_ES"
    private const val LOCALE_PERSIAN = "fa"
    private const val LOCALE_FINNISH = "fi"
    private const val LOCALE_FRENCH = "fr"
    private const val LOCALE_FRENCH_CANADA = "fr_CA"
    private const val LOCALE_FRENCH_SWITZERLAND = "fr_CH"
    private const val LOCALE_GALICIAN_SPAIN = "gl_ES"
    private const val LOCALE_HINDI = "hi"
    private const val LOCALE_CROATIAN = "hr"
    private const val LOCALE_HUNGARIAN = "hu"
    private const val LOCALE_ARMENIAN_ARMENIA = "hy_AM"

    // Java uses the deprecated "in" code instead of the standard "id" code for Indonesian.
    private const val LOCALE_INDONESIAN = "in"
    private const val LOCALE_ICELANDIC = "is"
    private const val LOCALE_ITALIAN = "it"
    private const val LOCALE_ITALIAN_SWITZERLAND = "it_CH"

    // Java uses the deprecated "iw" code instead of the standard "he" code for Hebrew.
    private const val LOCALE_HEBREW = "iw"
    private const val LOCALE_GEORGIAN_GEORGIA = "ka_GE"
    private const val LOCALE_KAZAKH = "kk"
    private const val LOCALE_KHMER_CAMBODIA = "km_KH"
    private const val LOCALE_KANNADA_INDIA = "kn_IN"
    private const val LOCALE_KYRGYZ = "ky"
    private const val LOCALE_LAO_LAOS = "lo_LA"
    private const val LOCALE_LITHUANIAN = "lt"
    private const val LOCALE_LATVIAN = "lv"
    private const val LOCALE_MACEDONIAN = "mk"
    private const val LOCALE_MALAYALAM_INDIA = "ml_IN"
    private const val LOCALE_MONGOLIAN_MONGOLIA = "mn_MN"
    private const val LOCALE_MARATHI_INDIA = "mr_IN"
    private const val LOCALE_MALAY_MALAYSIA = "ms_MY"
    private const val LOCALE_NORWEGIAN_BOKMAL = "nb" // Norwegian Bokmål
    private const val LOCALE_NEPALI_NEPAL = "ne_NP"
    private const val LOCALE_DUTCH = "nl"
    private const val LOCALE_DUTCH_BELGIUM = "nl_BE"
    private const val LOCALE_POLISH = "pl"
    private const val LOCALE_PORTUGUESE_BRAZIL = "pt_BR"
    private const val LOCALE_PORTUGUESE_PORTUGAL = "pt_PT"
    private const val LOCALE_ROMANIAN = "ro"
    private const val LOCALE_RUSSIAN = "ru"
    private const val LOCALE_SLOVAK = "sk"
    private const val LOCALE_SLOVENIAN = "sl"
    private const val LOCALE_SERBIAN = "sr"
    private const val LOCALE_SERBIAN_LATIN = "sr_ZZ"
    private const val LOCALE_SWEDISH = "sv"
    private const val LOCALE_SWAHILI = "sw"
    private const val LOCALE_TAMIL_INDIA = "ta_IN"
    private const val LOCALE_TAMIL_SINGAPORE = "ta_SG"
    private const val LOCALE_TELUGU_INDIA = "te_IN"
    private const val LOCALE_THAI = "th"
    private const val LOCALE_TAGALOG = "tl"
    private const val LOCALE_TURKISH = "tr"
    private const val LOCALE_UKRAINIAN = "uk"
    private const val LOCALE_URDU = "ur"
    private const val LOCALE_UZBEK_UZBEKISTAN = "uz_UZ"
    private const val LOCALE_VIETNAMESE = "vi"
    private const val LOCALE_ZULU = "zu"

    private val sSupportedLocales = arrayOf(
        LOCALE_ENGLISH_UNITED_STATES,
        LOCALE_ENGLISH_GREAT_BRITAIN,
        LOCALE_AFRIKAANS,
        LOCALE_ARABIC,
        LOCALE_AZERBAIJANI_AZERBAIJAN,
        LOCALE_BELARUSIAN_BELARUS,
        LOCALE_BULGARIAN,
        LOCALE_BENGALI_BANGLADESH,
        LOCALE_BENGALI_INDIA,
        LOCALE_CATALAN,
        LOCALE_CZECH,
        LOCALE_DANISH,
        LOCALE_GERMAN,
        LOCALE_GERMAN_SWITZERLAND,
        LOCALE_GREEK,
        LOCALE_ENGLISH_INDIA,
        LOCALE_ESPERANTO,
        LOCALE_SPANISH,
        LOCALE_SPANISH_UNITED_STATES,
        LOCALE_SPANISH_LATIN_AMERICA,
        LOCALE_ESTONIAN_ESTONIA,
        LOCALE_BASQUE_SPAIN,
        LOCALE_PERSIAN,
        LOCALE_FINNISH,
        LOCALE_FRENCH,
        LOCALE_FRENCH_CANADA,
        LOCALE_FRENCH_SWITZERLAND,
        LOCALE_GALICIAN_SPAIN,
        LOCALE_HINDI,
        LOCALE_CROATIAN,
        LOCALE_HUNGARIAN,
        LOCALE_ARMENIAN_ARMENIA,
        LOCALE_INDONESIAN,
        LOCALE_ICELANDIC,
        LOCALE_ITALIAN,
        LOCALE_ITALIAN_SWITZERLAND,
        LOCALE_HEBREW,
        LOCALE_GEORGIAN_GEORGIA,
        LOCALE_KAZAKH,
        LOCALE_KHMER_CAMBODIA,
        LOCALE_KANNADA_INDIA,
        LOCALE_KYRGYZ,
        LOCALE_LAO_LAOS,
        LOCALE_LITHUANIAN,
        LOCALE_LATVIAN,
        LOCALE_MACEDONIAN,
        LOCALE_MALAYALAM_INDIA,
        LOCALE_MONGOLIAN_MONGOLIA,
        LOCALE_MARATHI_INDIA,
        LOCALE_MALAY_MALAYSIA,
        LOCALE_NORWEGIAN_BOKMAL,
        LOCALE_NEPALI_NEPAL,
        LOCALE_DUTCH,
        LOCALE_DUTCH_BELGIUM,
        LOCALE_POLISH,
        LOCALE_PORTUGUESE_BRAZIL,
        LOCALE_PORTUGUESE_PORTUGAL,
        LOCALE_ROMANIAN,
        LOCALE_RUSSIAN,
        LOCALE_SLOVAK,
        LOCALE_SLOVENIAN,
        LOCALE_SERBIAN,
        LOCALE_SERBIAN_LATIN,
        LOCALE_SWEDISH,
        LOCALE_SWAHILI,
        LOCALE_TAMIL_INDIA,
        LOCALE_TAMIL_SINGAPORE,
        LOCALE_TELUGU_INDIA,
        LOCALE_THAI,
        LOCALE_TAGALOG,
        LOCALE_TURKISH,
        LOCALE_UKRAINIAN,
        LOCALE_URDU,
        LOCALE_UZBEK_UZBEKISTAN,
        LOCALE_VIETNAMESE,
        LOCALE_ZULU
    )

    val supportedLocales: List<String>
        /**
         * Get a list of all of the currently supported subtype locales.
         * @return a list of subtype strings in the format of "ll_cc_variant" where "ll" is a language
         * code, "cc" is a country code.
         */
        get() = Arrays.asList(*sSupportedLocales)

    const val LAYOUT_ARABIC: String = "arabic"
    const val LAYOUT_ARMENIAN_PHONETIC: String = "armenian_phonetic"
    const val LAYOUT_AZERTY: String = "azerty"
    const val LAYOUT_BENGALI: String = "bengali"
    const val LAYOUT_BENGALI_AKKHOR: String = "bengali_akkhor"
    const val LAYOUT_BENGALI_UNIJOY: String = "bengali_unijoy"
    const val LAYOUT_BULGARIAN: String = "bulgarian"
    const val LAYOUT_BULGARIAN_BDS: String = "bulgarian_bds"
    const val LAYOUT_EAST_SLAVIC: String = "east_slavic"
    const val LAYOUT_FARSI: String = "farsi"
    const val LAYOUT_GEORGIAN: String = "georgian"
    const val LAYOUT_GREEK: String = "greek"
    const val LAYOUT_HEBREW: String = "hebrew"
    const val LAYOUT_HINDI: String = "hindi"
    const val LAYOUT_HINDI_COMPACT: String = "hindi_compact"
    const val LAYOUT_KANNADA: String = "kannada"
    const val LAYOUT_KHMER: String = "khmer"
    const val LAYOUT_LAO: String = "lao"
    const val LAYOUT_MACEDONIAN: String = "macedonian"
    const val LAYOUT_MALAYALAM: String = "malayalam"
    const val LAYOUT_MARATHI: String = "marathi"
    const val LAYOUT_MONGOLIAN: String = "mongolian"
    const val LAYOUT_NEPALI_ROMANIZED: String = "nepali_romanized"
    const val LAYOUT_NEPALI_TRADITIONAL: String = "nepali_traditional"
    const val LAYOUT_NORDIC: String = "nordic"
    const val LAYOUT_QWERTY: String = "qwerty"
    const val LAYOUT_QWERTZ: String = "qwertz"
    const val LAYOUT_SERBIAN: String = "serbian"
    const val LAYOUT_SERBIAN_QWERTZ: String = "serbian_qwertz"
    const val LAYOUT_SPANISH: String = "spanish"
    const val LAYOUT_SWISS: String = "swiss"
    const val LAYOUT_TAMIL: String = "tamil"
    const val LAYOUT_TELUGU: String = "telugu"
    const val LAYOUT_THAI: String = "thai"
    const val LAYOUT_TURKISH_Q: String = "turkish_q"
    const val LAYOUT_TURKISH_F: String = "turkish_f"
    const val LAYOUT_URDU: String = "urdu"
    const val LAYOUT_UZBEK: String = "uzbek"

    /**
     * Get a list of all of the supported subtypes for a locale.
     * @param locale the locale string for the subtypes to look up.
     * @param resources the resources to use.
     * @return the list of subtypes for the specified locale.
     */
    fun getSubtypes(locale: String?, resources: Resources): List<Subtype> {
        return SubtypeBuilder(locale, true, resources).subtypes
    }

    /**
     * Get the default subtype for a locale.
     * @param locale the locale string for the subtype to look up.
     * @param resources the resources to use.
     * @return the default subtype for the specified locale or null if the locale isn't supported.
     */
    fun getDefaultSubtype(locale: String?, resources: Resources): Subtype? {
        val subtypes: List<Subtype> = SubtypeBuilder(locale, true, resources).subtypes
        return if (subtypes.size == 0) null else subtypes[0]
    }

    /**
     * Get a subtype for a specific locale and keyboard layout.
     * @param locale the locale string for the subtype to look up.
     * @param layoutSet the keyboard layout set name for the subtype.
     * @param resources the resources to use.
     * @return the subtype for the specified locale and layout or null if it isn't supported.
     */
    fun getSubtype(
        locale: String?, layoutSet: String?,
        resources: Resources
    ): Subtype? {
        val subtypes: List<Subtype> =
            SubtypeBuilder(locale, layoutSet, resources).subtypes
        return if (subtypes.size == 0) null else subtypes[0]
    }

    /**
     * Get the list subtypes corresponding to the system's languages.
     * @param resources the resources to use.
     * @return the default list of subtypes based on the system's languages.
     */
    fun getDefaultSubtypes(resources: Resources): MutableList<Subtype> {
        val supportedLocales = ArrayList<Locale?>(sSupportedLocales.size)
        for (localeString in sSupportedLocales) {
            supportedLocales.add(LocaleUtils.constructLocaleFromString(localeString))
        }

        val systemLocales = LocaleUtils.systemLocales

        val subtypes = ArrayList<Subtype>()
        val addedLocales = HashSet<Locale>()
        for (systemLocale in systemLocales) {
            val bestLocale = LocaleUtils.findBestLocale(
                systemLocale, supportedLocales
            )
            if (bestLocale != null && !addedLocales.contains(bestLocale)) {
                addedLocales.add(bestLocale)
                val bestLocaleString = LocaleUtils.getLocaleString(bestLocale)
                subtypes.add(getDefaultSubtype(bestLocaleString, resources)!!)
            }
        }
        if (subtypes.size == 0) {
            // there needs to be at least one default subtype
            subtypes.add(
                getSubtypes(
                    LOCALE_ENGLISH_UNITED_STATES,
                    resources
                )[0]
            )
        }
        return subtypes
    }

    /**
     * Utility for building the supported subtype objects. [.getSubtypes] sets up the full
     * list of available subtypes for a locale, but not all of the subtypes that it requests always
     * get returned. The parameters passed in the constructor limit what subtypes are actually built
     * and returned. This allows for a central location for indicating what subtypes are available
     * for each locale without always needing to build them all.
     */
    private class SubtypeBuilder {
        private val mResources: Resources
        private val mAllowMultiple: Boolean
        private val mLocale: String?
        private val mExpectedLayoutSet: String?
        private var mSubtypes: MutableList<Subtype>? = null

        /**
         * Builder for single subtype with a specific locale and layout.
         * @param locale the locale string for the subtype to build.
         * @param layoutSet the keyboard layout set name for the subtype.
         * @param resources the resources to use.
         */
        constructor(
            locale: String?, layoutSet: String?,
            resources: Resources
        ) {
            mLocale = locale
            mExpectedLayoutSet = layoutSet
            mAllowMultiple = false
            mResources = resources
        }

        /**
         * Builder for one or all subtypes with a specific locale.
         * @param locale the locale string for the subtype to build.
         * @param all true to get all of the subtypes for the locale or false for just the default.
         * @param resources the resources to use.
         */
        constructor(locale: String?, all: Boolean, resources: Resources) {
            mLocale = locale
            mExpectedLayoutSet = null
            mAllowMultiple = all
            mResources = resources
        }

        val subtypes: List<Subtype>
            /**
             * Get the requested subtypes.
             * @return the list of subtypes that were built.
             */
            get() {
                var mSubtypes = mSubtypes
                if (mSubtypes != null) {
                    // in case this gets called again for some reason, the subtypes should only be built
                    // once
                    return mSubtypes
                }
                mSubtypes = ArrayList()
                this.mSubtypes = mSubtypes
                // This should call to build all of the available for each supported locale. The private
                // helper functions will handle skipping building the subtypes that weren't requested.
                // The first subtype that is specified to be built here for each locale will be
                // considered the default.
                when (mLocale) {
                    LOCALE_AFRIKAANS, LOCALE_AZERBAIJANI_AZERBAIJAN, LOCALE_ENGLISH_INDIA, LOCALE_ENGLISH_GREAT_BRITAIN, LOCALE_ENGLISH_UNITED_STATES, LOCALE_FRENCH_CANADA, LOCALE_INDONESIAN, LOCALE_ICELANDIC, LOCALE_ITALIAN, LOCALE_LITHUANIAN, LOCALE_LATVIAN, LOCALE_MALAY_MALAYSIA, LOCALE_DUTCH, LOCALE_POLISH, LOCALE_PORTUGUESE_BRAZIL, LOCALE_PORTUGUESE_PORTUGAL, LOCALE_ROMANIAN, LOCALE_SLOVAK, LOCALE_SWAHILI, LOCALE_VIETNAMESE, LOCALE_ZULU -> {
                        addLayout(LAYOUT_QWERTY)
                        addGenericLayouts()
                    }

                    LOCALE_CZECH, LOCALE_GERMAN, LOCALE_CROATIAN, LOCALE_HUNGARIAN, LOCALE_SLOVENIAN -> {
                        addLayout(LAYOUT_QWERTZ)
                        addGenericLayouts()
                    }

                    LOCALE_FRENCH, LOCALE_DUTCH_BELGIUM -> {
                        addLayout(LAYOUT_AZERTY)
                        addGenericLayouts()
                    }

                    LOCALE_CATALAN, LOCALE_SPANISH, LOCALE_SPANISH_UNITED_STATES, LOCALE_SPANISH_LATIN_AMERICA, LOCALE_BASQUE_SPAIN, LOCALE_GALICIAN_SPAIN, LOCALE_TAGALOG -> {
                        addLayout(LAYOUT_SPANISH)
                        addGenericLayouts()
                    }

                    LOCALE_ESPERANTO -> addLayout(LAYOUT_SPANISH)
                    LOCALE_DANISH, LOCALE_ESTONIAN_ESTONIA, LOCALE_FINNISH, LOCALE_NORWEGIAN_BOKMAL, LOCALE_SWEDISH -> {
                        addLayout(LAYOUT_NORDIC)
                        addGenericLayouts()
                    }

                    LOCALE_GERMAN_SWITZERLAND, LOCALE_FRENCH_SWITZERLAND, LOCALE_ITALIAN_SWITZERLAND -> {
                        addLayout(LAYOUT_SWISS)
                        addGenericLayouts()
                    }

                    LOCALE_TURKISH -> {
                        addLayout(LAYOUT_QWERTY)
                        addLayout(LAYOUT_TURKISH_Q, R.string.subtype_q)
                        addLayout(LAYOUT_TURKISH_F, R.string.subtype_f)
                        addGenericLayouts()
                    }

                    LOCALE_UZBEK_UZBEKISTAN -> {
                        addLayout(LAYOUT_UZBEK)
                        addGenericLayouts()
                    }

                    LOCALE_ARABIC -> addLayout(LAYOUT_ARABIC)
                    LOCALE_BELARUSIAN_BELARUS, LOCALE_KAZAKH, LOCALE_KYRGYZ, LOCALE_RUSSIAN, LOCALE_UKRAINIAN -> addLayout(
                        LAYOUT_EAST_SLAVIC
                    )

                    LOCALE_BULGARIAN -> {
                        addLayout(LAYOUT_BULGARIAN)
                        addLayout(LAYOUT_BULGARIAN_BDS, R.string.subtype_bds)
                    }

                    LOCALE_BENGALI_BANGLADESH -> {
                        addLayout(LAYOUT_BENGALI_UNIJOY)
                        addLayout(
                            LAYOUT_BENGALI_AKKHOR,
                            R.string.subtype_akkhor
                        )
                    }

                    LOCALE_BENGALI_INDIA -> addLayout(LAYOUT_BENGALI)
                    LOCALE_GREEK -> addLayout(LAYOUT_GREEK)
                    LOCALE_PERSIAN -> addLayout(LAYOUT_FARSI)
                    LOCALE_HINDI -> {
                        addLayout(LAYOUT_HINDI)
                        addLayout(
                            LAYOUT_HINDI_COMPACT,
                            R.string.subtype_compact
                        )
                    }

                    LOCALE_ARMENIAN_ARMENIA -> addLayout(LAYOUT_ARMENIAN_PHONETIC)
                    LOCALE_HEBREW -> addLayout(LAYOUT_HEBREW)
                    LOCALE_GEORGIAN_GEORGIA -> addLayout(LAYOUT_GEORGIAN)
                    LOCALE_KHMER_CAMBODIA -> addLayout(LAYOUT_KHMER)
                    LOCALE_KANNADA_INDIA -> addLayout(LAYOUT_KANNADA)
                    LOCALE_LAO_LAOS -> addLayout(LAYOUT_LAO)
                    LOCALE_MACEDONIAN -> addLayout(LAYOUT_MACEDONIAN)
                    LOCALE_MALAYALAM_INDIA -> addLayout(LAYOUT_MALAYALAM)
                    LOCALE_MONGOLIAN_MONGOLIA -> addLayout(LAYOUT_MONGOLIAN)
                    LOCALE_MARATHI_INDIA -> addLayout(LAYOUT_MARATHI)
                    LOCALE_NEPALI_NEPAL -> {
                        addLayout(LAYOUT_NEPALI_ROMANIZED)
                        addLayout(
                            LAYOUT_NEPALI_TRADITIONAL,
                            R.string.subtype_traditional
                        )
                    }

                    LOCALE_SERBIAN -> addLayout(LAYOUT_SERBIAN)
                    LOCALE_SERBIAN_LATIN -> {
                        addLayout(LAYOUT_SERBIAN_QWERTZ)
                        addGenericLayouts()
                    }

                    LOCALE_TAMIL_INDIA, LOCALE_TAMIL_SINGAPORE -> addLayout(
                        LAYOUT_TAMIL
                    )

                    LOCALE_TELUGU_INDIA -> addLayout(LAYOUT_TELUGU)
                    LOCALE_THAI -> addLayout(LAYOUT_THAI)
                    LOCALE_URDU -> addLayout(LAYOUT_URDU)
                }
                return mSubtypes
            }

        /**
         * Check if the layout should skip being built based on the request from the constructor.
         * @param keyboardLayoutSet the layout set for the subtype to potentially build.
         * @return whether the subtype should be skipped.
         */
        fun shouldSkipLayout(keyboardLayoutSet: String): Boolean {
            if (mAllowMultiple) {
                return false
            }
            if (mSubtypes!!.size > 0) {
                return true
            }
            if (mExpectedLayoutSet != null) {
                return mExpectedLayoutSet != keyboardLayoutSet
            }
            return false
        }

        /**
         * Add a single layout for the locale. This might not actually add the subtype to the list
         * depending on the original request.
         * @param keyboardLayoutSet the keyboard layout set name.
         */
        fun addLayout(keyboardLayoutSet: String) {
            if (shouldSkipLayout(keyboardLayoutSet)) {
                return
            }

            // if this is a generic layout, use that corresponding layout name
            val predefinedLayouts =
                mResources.getStringArray(R.array.predefined_layouts)
            val predefinedLayoutIndex =
                Arrays.asList(*predefinedLayouts).indexOf(keyboardLayoutSet)
            val layoutNameStr: String?
            if (predefinedLayoutIndex >= 0) {
                val predefinedLayoutDisplayNames = mResources.getStringArray(
                    R.array.predefined_layout_display_names
                )
                layoutNameStr = predefinedLayoutDisplayNames[predefinedLayoutIndex]
            } else {
                layoutNameStr = null
            }

            mSubtypes!!.add(
                Subtype(mLocale!!, keyboardLayoutSet, layoutNameStr, false, mResources)
            )
        }

        /**
         * Add a single layout for the locale. This might not actually add the subtype to the list
         * depending on the original request.
         * @param keyboardLayoutSet the keyboard layout set name.
         * @param layoutRes the resource ID to use for the display name of the keyboard layout. This
         * generally shouldn't include the name of the language.
         */
        fun addLayout(keyboardLayoutSet: String, layoutRes: Int) {
            if (shouldSkipLayout(keyboardLayoutSet)) {
                return
            }
            mSubtypes!!.add(
                Subtype(mLocale!!, keyboardLayoutSet, layoutRes, true, mResources)
            )
        }

        /**
         * Add the predefined layouts (eg: QWERTY, AZERTY, etc) for the locale. This might not
         * actually add all of the subtypes to the list depending on the original request.
         */
        fun addGenericLayouts() {
            if (mSubtypes!!.size > 0 && !mAllowMultiple) {
                return
            }
            val initialSize = mSubtypes!!.size
            val predefinedKeyboardLayoutSets = mResources.getStringArray(
                R.array.predefined_layouts
            )
            val predefinedKeyboardLayoutSetDisplayNames = mResources.getStringArray(
                R.array.predefined_layout_display_names
            )
            for (i in predefinedKeyboardLayoutSets.indices) {
                val predefinedLayout = predefinedKeyboardLayoutSets[i]
                if (shouldSkipLayout(predefinedLayout)) {
                    continue
                }

                var alreadyExists = false
                for (subtypeIndex in 0 until initialSize) {
                    val layoutSet = mSubtypes!![subtypeIndex].keyboardLayoutSet
                    if (layoutSet == predefinedLayout) {
                        alreadyExists = true
                        break
                    }
                }
                if (alreadyExists) {
                    continue
                }

                mSubtypes!!.add(
                    Subtype(
                        mLocale!!, predefinedLayout,
                        predefinedKeyboardLayoutSetDisplayNames[i], true, mResources
                    )
                )
            }
        }
    }
}
