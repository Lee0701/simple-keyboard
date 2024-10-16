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

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.text.InputType
import android.util.Log
import android.util.SparseArray
import android.util.Xml
import android.view.inputmethod.EditorInfo
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardBuilder
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.UniqueKeysCache
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.utils.InputTypeUtils
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils.IllegalEndTag
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils.IllegalStartTag
import java.io.IOException
import java.lang.ref.SoftReference

/**
 * This class represents a set of keyboard layouts. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * [KeyboardLayoutSet] are related to each other.
 * A [KeyboardLayoutSet] needs to be created for each
 * [android.view.inputmethod.EditorInfo].
 */
class KeyboardLayoutSet internal constructor(context: Context, params: Params) {
    private val mContext: Context
    private val mParams: Params

    class KeyboardLayoutSetException(cause: Throwable?, keyboardId: KeyboardId) :
        RuntimeException(cause) {
        val mKeyboardId: KeyboardId

        init {
            mKeyboardId = keyboardId
        }
    }

    class ElementParams {
        var mKeyboardXmlId: Int = 0
        var mAllowRedundantMoreKeys: Boolean = false
    }

    class Params {
        var mKeyboardLayoutSetName: String? = null
        var mMode: Int = 0

        // TODO: Use {@link InputAttributes} instead of these variables.
        var mEditorInfo: EditorInfo? = null
        var mNoSettingsKey: Boolean = false
        var mLanguageSwitchKeyEnabled: Boolean = false
        var mSubtype: Subtype? = null
        var mKeyboardThemeId: Int = 0
        var mKeyboardWidth: Int = 0
        var mKeyboardHeight: Int = 0
        var mKeyboardBottomOffset: Int = 0
        var mShowMoreKeys: Boolean = false
        var mShowNumberRow: Boolean = false

        // Sparse array of KeyboardLayoutSet element parameters indexed by element's id.
        val mKeyboardLayoutSetElementIdToParamsMap: SparseArray<ElementParams> = SparseArray()
    }

    init {
        mContext = context
        mParams = params
    }

    fun getKeyboard(baseKeyboardLayoutSetElementId: Int): Keyboard? {
        val keyboardLayoutSetElementId: Int
        when (mParams.mMode) {
            KeyboardId.Companion.MODE_PHONE -> if (baseKeyboardLayoutSetElementId == KeyboardId.Companion.ELEMENT_SYMBOLS) {
                keyboardLayoutSetElementId = KeyboardId.Companion.ELEMENT_PHONE_SYMBOLS
            } else {
                keyboardLayoutSetElementId = KeyboardId.Companion.ELEMENT_PHONE
            }

            KeyboardId.Companion.MODE_NUMBER, KeyboardId.Companion.MODE_DATE, KeyboardId.Companion.MODE_TIME, KeyboardId.Companion.MODE_DATETIME -> keyboardLayoutSetElementId =
                KeyboardId.Companion.ELEMENT_NUMBER

            else -> keyboardLayoutSetElementId = baseKeyboardLayoutSetElementId
        }

        var elementParams: ElementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
            keyboardLayoutSetElementId
        )
        if (elementParams == null) {
            elementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                KeyboardId.Companion.ELEMENT_ALPHABET
            )
        }

        // Note: The keyboard for each shift state, and mode are represented as an elementName
        // attribute in a keyboard_layout_set XML file.  Also each keyboard layout XML resource is
        // specified as an elementKeyboard attribute in the file.
        // The KeyboardId is an internal key for a Keyboard object.
        val id: KeyboardId = KeyboardId(keyboardLayoutSetElementId, mParams)
        return getKeyboard(elementParams, id)
    }

    private fun getKeyboard(elementParams: ElementParams, id: KeyboardId): Keyboard? {
        val ref: SoftReference<Keyboard?>? = sKeyboardCache.get(id)
        val cachedKeyboard: Keyboard? = if ((ref == null)) null else ref.get()
        if (cachedKeyboard != null) {
            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size + ": HIT  id=" + id)
            }
            return cachedKeyboard
        }

        val builder: KeyboardBuilder<KeyboardParams> =
            KeyboardBuilder(mContext, KeyboardParams(sUniqueKeysCache))
        sUniqueKeysCache.setEnabled(id.isAlphabetKeyboard())
        builder.setAllowRedundantMoreKes(elementParams.mAllowRedundantMoreKeys)
        val keyboardXmlId: Int = elementParams.mKeyboardXmlId
        builder.load(keyboardXmlId, id)
        val keyboard: Keyboard? = builder.build()
        sKeyboardCache.put(id, SoftReference(keyboard))
        if ((id.mElementId == KeyboardId.Companion.ELEMENT_ALPHABET
                    || id.mElementId == KeyboardId.Companion.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
        ) {
            // We only forcibly cache the primary, "ALPHABET", layouts.
            for (i in sForcibleKeyboardCache.size - 1 downTo 1) {
                sForcibleKeyboardCache.get(i) = sForcibleKeyboardCache.get(i - 1)
            }
            sForcibleKeyboardCache.get(0) = keyboard
            if (DEBUG_CACHE) {
                Log.d(TAG, "forcing caching of keyboard with id=" + id)
            }
        }
        if (DEBUG_CACHE) {
            Log.d(
                TAG, ("keyboard cache size=" + sKeyboardCache.size + ": "
                        + (if ((ref == null)) "LOAD" else "GCed") + " id=" + id)
            )
        }
        return keyboard
    }

    class Builder(context: Context, ei: EditorInfo?) {
        private val mContext: Context
        private val mResources: Resources

        private val mParams: Params = Params()

        init {
            mContext = context
            mResources = context.getResources()
            val params: Params = mParams

            val editorInfo: EditorInfo = if ((ei != null)) ei else EMPTY_EDITOR_INFO
            params.mMode = getKeyboardMode(editorInfo)
            // TODO: Consolidate those with {@link InputAttributes}.
            params.mEditorInfo = editorInfo

            val kgMgr: KeyguardManager =
                context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            params.mNoSettingsKey = kgMgr.isKeyguardLocked()
        }

        fun setKeyboardTheme(themeId: Int): Builder {
            mParams.mKeyboardThemeId = themeId
            return this
        }

        fun setKeyboardGeometry(
            keyboardWidth: Int, keyboardHeight: Int,
            keyboardBottomOffset: Int
        ): Builder {
            mParams.mKeyboardWidth = keyboardWidth
            mParams.mKeyboardHeight = keyboardHeight
            mParams.mKeyboardBottomOffset = keyboardBottomOffset
            return this
        }

        fun setSubtype(subtype: Subtype): Builder {
            // TODO: Consolidate with {@link InputAttributes}.
            mParams.mSubtype = subtype
            mParams.mKeyboardLayoutSetName = (KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX
                    + subtype.getKeyboardLayoutSet())
            return this
        }

        fun setLanguageSwitchKeyEnabled(enabled: Boolean): Builder {
            mParams.mLanguageSwitchKeyEnabled = enabled
            return this
        }

        fun setShowSpecialChars(enabled: Boolean): Builder {
            mParams.mShowMoreKeys = enabled
            return this
        }

        fun setShowNumberRow(enabled: Boolean): Builder {
            mParams.mShowNumberRow = enabled
            return this
        }

        fun build(): KeyboardLayoutSet {
            if (mParams.mSubtype == null) throw RuntimeException("KeyboardLayoutSet subtype is not specified")
            val xmlId: Int = getXmlId(mResources, mParams.mKeyboardLayoutSetName)
            try {
                parseKeyboardLayoutSet(mResources, xmlId)
            } catch (e: IOException) {
                throw RuntimeException(
                    e.message + " in " + mParams.mKeyboardLayoutSetName,
                    e
                )
            } catch (e: XmlPullParserException) {
                throw RuntimeException(
                    e.message + " in " + mParams.mKeyboardLayoutSetName,
                    e
                )
            }
            return KeyboardLayoutSet(mContext, mParams)
        }

        @Throws(XmlPullParserException::class, IOException::class)
        private fun parseKeyboardLayoutSet(res: Resources, resId: Int) {
            val parser: XmlResourceParser = res.getXml(resId)
            try {
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    val event: Int = parser.next()
                    if (event == XmlPullParser.START_TAG) {
                        val tag: String = parser.getName()
                        if (TAG_KEYBOARD_SET == tag) {
                            parseKeyboardLayoutSetContent(parser)
                        } else {
                            throw IllegalStartTag(parser, tag, TAG_KEYBOARD_SET)
                        }
                    }
                }
            } finally {
                parser.close()
            }
        }

        @Throws(XmlPullParserException::class, IOException::class)
        private fun parseKeyboardLayoutSetContent(parser: XmlPullParser) {
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                val event: Int = parser.next()
                if (event == XmlPullParser.START_TAG) {
                    val tag: String = parser.getName()
                    if (TAG_ELEMENT == tag) {
                        parseKeyboardLayoutSetElement(parser)
                    } else {
                        throw IllegalStartTag(parser, tag, TAG_KEYBOARD_SET)
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val tag: String = parser.getName()
                    if (TAG_KEYBOARD_SET == tag) {
                        break
                    }
                    throw IllegalEndTag(parser, tag, TAG_KEYBOARD_SET)
                }
            }
        }

        @Throws(XmlPullParserException::class, IOException::class)
        private fun parseKeyboardLayoutSetElement(parser: XmlPullParser) {
            val a: TypedArray = mResources.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.KeyboardLayoutSet_Element
            )
            try {
                XmlParseUtils.checkAttributeExists(
                    a,
                    R.styleable.KeyboardLayoutSet_Element_elementName, "elementName",
                    TAG_ELEMENT, parser
                )
                XmlParseUtils.checkAttributeExists(
                    a,
                    R.styleable.KeyboardLayoutSet_Element_elementKeyboard, "elementKeyboard",
                    TAG_ELEMENT, parser
                )
                XmlParseUtils.checkEndTag(TAG_ELEMENT, parser)

                val elementParams: ElementParams = ElementParams()
                val elementName: Int = a.getInt(
                    R.styleable.KeyboardLayoutSet_Element_elementName, 0
                )
                elementParams.mKeyboardXmlId = a.getResourceId(
                    R.styleable.KeyboardLayoutSet_Element_elementKeyboard, 0
                )
                elementParams.mAllowRedundantMoreKeys = a.getBoolean(
                    R.styleable.KeyboardLayoutSet_Element_allowRedundantMoreKeys, true
                )
                mParams.mKeyboardLayoutSetElementIdToParamsMap.put(elementName, elementParams)
            } finally {
                a.recycle()
            }
        }

        companion object {
            private val EMPTY_EDITOR_INFO: EditorInfo = EditorInfo()

            private fun getXmlId(resources: Resources, keyboardLayoutSetName: String?): Int {
                val packageName: String = resources.getResourcePackageName(
                    R.xml.keyboard_layout_set_qwerty
                )
                return resources.getIdentifier(keyboardLayoutSetName, "xml", packageName)
            }

            private fun getKeyboardMode(editorInfo: EditorInfo): Int {
                val inputType: Int = editorInfo.inputType
                val variation: Int = inputType and InputType.TYPE_MASK_VARIATION

                when (inputType and InputType.TYPE_MASK_CLASS) {
                    InputType.TYPE_CLASS_NUMBER -> return KeyboardId.Companion.MODE_NUMBER
                    InputType.TYPE_CLASS_DATETIME -> when (variation) {
                        InputType.TYPE_DATETIME_VARIATION_DATE -> return KeyboardId.Companion.MODE_DATE
                        InputType.TYPE_DATETIME_VARIATION_TIME -> return KeyboardId.Companion.MODE_TIME
                        else -> return KeyboardId.Companion.MODE_DATETIME
                    }

                    InputType.TYPE_CLASS_PHONE -> return KeyboardId.Companion.MODE_PHONE
                    InputType.TYPE_CLASS_TEXT -> if (InputTypeUtils.isEmailVariation(variation)) {
                        return KeyboardId.Companion.MODE_EMAIL
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                        return KeyboardId.Companion.MODE_URL
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                        return KeyboardId.Companion.MODE_IM
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                        return KeyboardId.Companion.MODE_TEXT
                    } else {
                        return KeyboardId.Companion.MODE_TEXT
                    }

                    else -> return KeyboardId.Companion.MODE_TEXT
                }
            }
        }
    }

    companion object {
        private val TAG: String = KeyboardLayoutSet::class.java.getSimpleName()
        private const val DEBUG_CACHE: Boolean = false

        private const val TAG_KEYBOARD_SET: String = "KeyboardLayoutSet"
        private const val TAG_ELEMENT: String = "Element"

        private const val KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX: String = "keyboard_layout_set_"

        // How many layouts we forcibly keep in cache. This only includes ALPHABET (default) and
        // ALPHABET_AUTOMATIC_SHIFTED layouts - other layouts may stay in memory in the map of
        // soft-references, but we forcibly cache this many alphabetic/auto-shifted layouts.
        private const val FORCIBLE_CACHE_SIZE: Int = 4

        // By construction of soft references, anything that is also referenced somewhere else
        // will stay in the cache. So we forcibly keep some references in an array to prevent
        // them from disappearing from sKeyboardCache.
        private val sForcibleKeyboardCache: Array<Keyboard?> = arrayOfNulls(FORCIBLE_CACHE_SIZE)
        private val sKeyboardCache: HashMap<KeyboardId, SoftReference<Keyboard?>> = HashMap()
        private val sUniqueKeysCache: UniqueKeysCache = UniqueKeysCache.Companion.newInstance()

        fun onSystemLocaleChanged() {
            clearKeyboardCache()
        }

        fun onKeyboardThemeChanged() {
            clearKeyboardCache()
        }

        private fun clearKeyboardCache() {
            sKeyboardCache.clear()
            sUniqueKeysCache.clear()
        }
    }
}
