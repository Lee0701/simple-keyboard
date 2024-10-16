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

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.Key
import rkr.simplekeyboard.inputmethod.keyboard.Key.Spacer
import rkr.simplekeyboard.inputmethod.keyboard.Keyboard
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardId
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import rkr.simplekeyboard.inputmethod.latin.utils.ResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils.IllegalAttribute
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils.IllegalEndTag
import rkr.simplekeyboard.inputmethod.latin.utils.XmlParseUtils.IllegalStartTag
import java.io.IOException
import java.util.Locale

/**
 * Keyboard Building helper.
 *
 * This class parses Keyboard XML file and eventually build a Keyboard.
 * The Keyboard XML file looks like:
 * <pre>
 * &lt;!-- xml/keyboard.xml --&gt;
 * &lt;Keyboard keyboard_attributes*&gt;
 * &lt;!-- Keyboard Content --&gt;
 * &lt;Row row_attributes*&gt;
 * &lt;!-- Row Content --&gt;
 * &lt;Key key_attributes* /&gt;
 * &lt;Spacer horizontalGap="32.0dp" /&gt;
 * &lt;include keyboardLayout="@xml/other_keys"&gt;
 * ...
 * &lt;/Row&gt;
 * &lt;include keyboardLayout="@xml/other_rows"&gt;
 * ...
 * &lt;/Keyboard&gt;
</pre> *
 * The XML file which is included in other file must have &lt;merge&gt; as root element,
 * such as:
 * <pre>
 * &lt;!-- xml/other_keys.xml --&gt;
 * &lt;merge&gt;
 * &lt;Key key_attributes* /&gt;
 * ...
 * &lt;/merge&gt;
</pre> *
 * and
 * <pre>
 * &lt;!-- xml/other_rows.xml --&gt;
 * &lt;merge&gt;
 * &lt;Row row_attributes*&gt;
 * &lt;Key key_attributes* /&gt;
 * &lt;/Row&gt;
 * ...
 * &lt;/merge&gt;
</pre> *
 * You can also use switch-case-default tags to select Rows and Keys.
 * <pre>
 * &lt;switch&gt;
 * &lt;case case_attribute*&gt;
 * &lt;!-- Any valid tags at switch position --&gt;
 * &lt;/case&gt;
 * ...
 * &lt;default&gt;
 * &lt;!-- Any valid tags at switch position --&gt;
 * &lt;/default&gt;
 * &lt;/switch&gt;
</pre> *
 * You can declare Key style and specify styles within Key tags.
 * <pre>
 * &lt;switch&gt;
 * &lt;case mode="email"&gt;
 * &lt;key-style styleName="f1-key" parentStyle="modifier-key"
 * keyLabel=".com"
 * /&gt;
 * &lt;/case&gt;
 * &lt;case mode="url"&gt;
 * &lt;key-style styleName="f1-key" parentStyle="modifier-key"
 * keyLabel="http://"
 * /&gt;
 * &lt;/case&gt;
 * &lt;/switch&gt;
 * ...
 * &lt;Key keyStyle="shift-key" ... /&gt;
</pre> *
 */
// TODO: Write unit tests for this class.
open class KeyboardBuilder<KP : KeyboardParams?>(context: Context, params: KP) {
    protected val mParams: KP
    protected val mContext: Context
    protected val mResources: Resources

    private var mCurrentY: Float = 0f
    private var mCurrentRow: KeyboardRow? = null
    private var mPreviousKeyInRow: Key? = null
    private var mKeyboardDefined: Boolean = false

    fun setAllowRedundantMoreKes(enabled: Boolean) {
        mParams!!.mAllowRedundantMoreKeys = enabled
    }

    fun load(xmlId: Int, id: KeyboardId?): KeyboardBuilder<KP> {
        mParams!!.mId = id
        val parser: XmlResourceParser = mResources.getXml(xmlId)
        try {
            parseKeyboard(parser, false)
            if (!mKeyboardDefined) {
                throw XmlParseUtils.ParseException("No " + TAG_KEYBOARD + " tag was found")
            }
        } catch (e: XmlPullParserException) {
            Log.w(BUILDER_TAG, "keyboard XML parse error", e)
            throw IllegalArgumentException(e.message, e)
        } catch (e: IOException) {
            Log.w(BUILDER_TAG, "keyboard XML parse error", e)
            throw RuntimeException(e.message, e)
        } finally {
            parser.close()
        }
        return this
    }

    open fun build(): Keyboard {
        return Keyboard(mParams)
    }

    private var mIndent: Int = 0

    init {
        mContext = context
        val res: Resources = context.getResources()
        mResources = res

        mParams = params

        params!!.mGridWidth = res.getInteger(R.integer.config_keyboard_grid_width)
        params.mGridHeight = res.getInteger(R.integer.config_keyboard_grid_height)
    }

    private fun startTag(format: String, vararg args: Any) {
        Log.d(BUILDER_TAG, String.format(spaces(++mIndent * 2) + format, *args))
    }

    private fun endTag(format: String, vararg args: Any) {
        Log.d(BUILDER_TAG, String.format(spaces(mIndent-- * 2) + format, *args))
    }

    private fun startEndTag(format: String, vararg args: Any) {
        Log.d(BUILDER_TAG, String.format(spaces(++mIndent * 2) + format, *args))
        mIndent--
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseKeyboard(parser: XmlPullParser, skip: Boolean) {
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            val event: Int = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag: String = parser.getName()
                if (TAG_KEYBOARD == tag) {
                    if (DEBUG) startTag(
                        "<%s> %s%s", TAG_KEYBOARD,
                        mParams!!.mId!!,
                        if (skip) " skipped" else ""
                    )
                    if (!skip) {
                        if (mKeyboardDefined) {
                            throw XmlParseUtils.ParseException(
                                ("Only one " + TAG_KEYBOARD
                                        + " tag can be defined"), parser
                            )
                        }
                        mKeyboardDefined = true
                        parseKeyboardAttributes(parser)
                        startKeyboard()
                    }
                    parseKeyboardContent(parser, skip)
                } else if (TAG_SWITCH == tag) {
                    parseSwitchKeyboard(parser, skip)
                } else {
                    throw IllegalStartTag(parser, tag, TAG_KEYBOARD)
                }
            } else if (event == XmlPullParser.END_TAG) {
                val tag: String = parser.getName()
                if (DEBUG) endTag("</%s>", tag)
                if (TAG_CASE == tag || TAG_DEFAULT == tag) {
                    return
                }
                throw IllegalEndTag(parser, tag, TAG_ROW)
            }
        }
    }

    private fun parseKeyboardAttributes(parser: XmlPullParser) {
        val attr: AttributeSet = Xml.asAttributeSet(parser)
        val keyboardAttr: TypedArray = mContext.obtainStyledAttributes(
            attr, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard
        )
        val keyAttr: TypedArray = mResources.obtainAttributes(attr, R.styleable.Keyboard_Key)
        try {
            val params: KeyboardParams = mParams
            val height: Int = params.mId!!.mHeight
            val width: Int = params.mId!!.mWidth
            val bottomOffset: Int = params.mId!!.mBottomOffset
            // The bonus height isn't used to determine the other dimensions (gap/padding) to allow
            // those to stay consistent between layouts with and without the bonus height added.
            val bonusHeight: Int = keyboardAttr.getFraction(
                R.styleable.Keyboard_bonusHeight,
                height, height, 0f
            ).toInt()
            params.mOccupiedHeight = height + bonusHeight + bottomOffset
            params.mOccupiedWidth = width
            params.mTopPadding = ResourceUtils.getDimensionOrFraction(
                keyboardAttr,
                R.styleable.Keyboard_keyboardTopPadding, height, 0f
            )
            params.mBottomPadding = ResourceUtils.getDimensionOrFraction(
                keyboardAttr,
                R.styleable.Keyboard_keyboardBottomPadding, height, 0f
            )
            params.mLeftPadding = ResourceUtils.getDimensionOrFraction(
                keyboardAttr,
                R.styleable.Keyboard_keyboardLeftPadding, width, 0f
            )
            params.mRightPadding = ResourceUtils.getDimensionOrFraction(
                keyboardAttr,
                R.styleable.Keyboard_keyboardRightPadding, width, 0f
            )

            params.mHorizontalGap = keyboardAttr.getFraction(
                R.styleable.Keyboard_horizontalGap, width, width, 0f
            )
            val baseWidth: Float = (params.mOccupiedWidth - params.mLeftPadding
                    - params.mRightPadding) + params.mHorizontalGap
            params.mBaseWidth = baseWidth
            params.mDefaultKeyPaddedWidth = ResourceUtils.getFraction(
                keyAttr,
                R.styleable.Keyboard_Key_keyWidth, baseWidth,
                baseWidth / DEFAULT_KEYBOARD_COLUMNS
            )
            // TODO: Fix keyboard geometry calculation clearer. Historically vertical gap between
            // rows are determined based on the entire keyboard height including top and bottom
            // paddings.
            params.mVerticalGap = keyboardAttr.getFraction(
                R.styleable.Keyboard_verticalGap, height, height, 0f
            )
            val baseHeight: Float = (params.mOccupiedHeight - params.mTopPadding
                    - params.mBottomPadding) + params.mVerticalGap - bottomOffset
            params.mBaseHeight = baseHeight
            params.mDefaultRowHeight = ResourceUtils.getDimensionOrFraction(
                keyboardAttr,
                R.styleable.Keyboard_rowHeight, baseHeight, baseHeight / DEFAULT_KEYBOARD_ROWS
            )

            params.mKeyVisualAttributes = KeyVisualAttributes.Companion.newInstance(keyAttr)

            params.mMoreKeysTemplate = keyboardAttr.getResourceId(
                R.styleable.Keyboard_moreKeysTemplate, 0
            )
            params.mMaxMoreKeysKeyboardColumn = keyAttr.getInt(
                R.styleable.Keyboard_Key_maxMoreKeysColumn, 5
            )

            params.mIconsSet.loadIcons(keyboardAttr)
            params.mTextsSet.setLocale(params.mId.getLocale(), mContext)
        } finally {
            keyAttr.recycle()
            keyboardAttr.recycle()
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseKeyboardContent(parser: XmlPullParser, skip: Boolean) {
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            val event: Int = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag: String = parser.getName()
                if (TAG_ROW == tag) {
                    val row: KeyboardRow = parseRowAttributes(parser)
                    if (DEBUG) startTag("<%s>%s", TAG_ROW, if (skip) " skipped" else "")
                    if (!skip) {
                        startRow(row)
                    }
                    parseRowContent(parser, row, skip)
                } else if (TAG_INCLUDE == tag) {
                    parseIncludeKeyboardContent(parser, skip)
                } else if (TAG_SWITCH == tag) {
                    parseSwitchKeyboardContent(parser, skip)
                } else if (TAG_KEY_STYLE == tag) {
                    parseKeyStyle(parser, skip)
                } else {
                    throw IllegalStartTag(parser, tag, TAG_ROW)
                }
            } else if (event == XmlPullParser.END_TAG) {
                val tag: String = parser.getName()
                if (DEBUG) endTag("</%s>", tag)
                if (TAG_KEYBOARD == tag) {
                    endKeyboard()
                    return
                }
                if (TAG_CASE == tag || TAG_DEFAULT == tag || TAG_MERGE == tag) {
                    return
                }
                throw IllegalEndTag(parser, tag, TAG_ROW)
            }
        }
    }

    @Throws(XmlPullParserException::class)
    private fun parseRowAttributes(parser: XmlPullParser): KeyboardRow {
        val attr: AttributeSet = Xml.asAttributeSet(parser)
        val keyboardAttr: TypedArray = mResources.obtainAttributes(attr, R.styleable.Keyboard)
        try {
            if (keyboardAttr.hasValue(R.styleable.Keyboard_horizontalGap)) {
                throw IllegalAttribute(parser, TAG_ROW, "horizontalGap")
            }
            if (keyboardAttr.hasValue(R.styleable.Keyboard_verticalGap)) {
                throw IllegalAttribute(parser, TAG_ROW, "verticalGap")
            }
            return KeyboardRow(mResources, mParams, parser, mCurrentY)
        } finally {
            keyboardAttr.recycle()
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseRowContent(
        parser: XmlPullParser, row: KeyboardRow,
        skip: Boolean
    ) {
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            val event: Int = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag: String = parser.getName()
                if (TAG_KEY == tag) {
                    parseKey(parser, row, skip)
                } else if (TAG_SPACER == tag) {
                    parseSpacer(parser, row, skip)
                } else if (TAG_INCLUDE == tag) {
                    parseIncludeRowContent(parser, row, skip)
                } else if (TAG_SWITCH == tag) {
                    parseSwitchRowContent(parser, row, skip)
                } else if (TAG_KEY_STYLE == tag) {
                    parseKeyStyle(parser, skip)
                } else {
                    throw IllegalStartTag(parser, tag, TAG_ROW)
                }
            } else if (event == XmlPullParser.END_TAG) {
                val tag: String = parser.getName()
                if (DEBUG) endTag("</%s>", tag)
                if (TAG_ROW == tag) {
                    if (!skip) {
                        endRow(row)
                    }
                    return
                }
                if (TAG_CASE == tag || TAG_DEFAULT == tag || TAG_MERGE == tag) {
                    return
                }
                throw IllegalEndTag(parser, tag, TAG_ROW)
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseKey(parser: XmlPullParser, row: KeyboardRow, skip: Boolean) {
        if (skip) {
            XmlParseUtils.checkEndTag(TAG_KEY, parser)
            if (DEBUG) startEndTag("<%s /> skipped", TAG_KEY)
            return
        }
        val keyAttr: TypedArray = mResources.obtainAttributes(
            Xml.asAttributeSet(parser), R.styleable.Keyboard_Key
        )
        val keyStyle: KeyStyle? = mParams!!.mKeyStyles.getKeyStyle(keyAttr, parser)
        val keySpec: String? = keyStyle!!.getString(keyAttr, R.styleable.Keyboard_Key_keySpec)
        if (TextUtils.isEmpty(keySpec)) {
            throw XmlParseUtils.ParseException("Empty keySpec", parser)
        }
        val key: Key = Key(
            keySpec, keyAttr,
            keyStyle, mParams, row
        )
        keyAttr.recycle()
        if (DEBUG) {
            startEndTag("<%s %s moreKeys=%s />", TAG_KEY, key, key.getMoreKeys().contentToString())
        }
        XmlParseUtils.checkEndTag(TAG_KEY, parser)
        endKey(key, row)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseSpacer(parser: XmlPullParser, row: KeyboardRow, skip: Boolean) {
        if (skip) {
            XmlParseUtils.checkEndTag(TAG_SPACER, parser)
            if (DEBUG) startEndTag("<%s /> skipped", TAG_SPACER)
            return
        }
        val keyAttr: TypedArray = mResources.obtainAttributes(
            Xml.asAttributeSet(parser), R.styleable.Keyboard_Key
        )
        val keyStyle: KeyStyle? = mParams!!.mKeyStyles.getKeyStyle(keyAttr, parser)
        val spacer: Key = Spacer(
            keyAttr,
            keyStyle!!, mParams, row
        )
        keyAttr.recycle()
        if (DEBUG) startEndTag("<%s />", TAG_SPACER)
        XmlParseUtils.checkEndTag(TAG_SPACER, parser)
        endKey(spacer, row)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseIncludeKeyboardContent(parser: XmlPullParser, skip: Boolean) {
        parseIncludeInternal(parser, null, skip)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseIncludeRowContent(
        parser: XmlPullParser,
        row: KeyboardRow,
        skip: Boolean
    ) {
        parseIncludeInternal(parser, row, skip)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseIncludeInternal(
        parser: XmlPullParser, row: KeyboardRow?,
        skip: Boolean
    ) {
        if (skip) {
            XmlParseUtils.checkEndTag(TAG_INCLUDE, parser)
            if (DEBUG) startEndTag("</%s> skipped", TAG_INCLUDE)
            return
        }
        val attr: AttributeSet = Xml.asAttributeSet(parser)
        val keyboardAttr: TypedArray = mResources.obtainAttributes(
            attr, R.styleable.Keyboard_Include
        )
        val includeAttr: TypedArray = mResources.obtainAttributes(
            attr, R.styleable.Keyboard
        )
        mParams!!.mDefaultRowHeight = ResourceUtils.getDimensionOrFraction(
            includeAttr,
            R.styleable.Keyboard_rowHeight, mParams.mBaseHeight, mParams.mDefaultRowHeight
        )

        val keyAttr: TypedArray = mResources.obtainAttributes(attr, R.styleable.Keyboard_Key)
        var keyboardLayout: Int = 0
        try {
            XmlParseUtils.checkAttributeExists(
                keyboardAttr, R.styleable.Keyboard_Include_keyboardLayout, "keyboardLayout",
                TAG_INCLUDE, parser
            )
            keyboardLayout = keyboardAttr.getResourceId(
                R.styleable.Keyboard_Include_keyboardLayout, 0
            )
            if (row != null) {
                // Override current x coordinate.
                row.updateXPos(keyAttr)
                // Push current Row attributes and update with new attributes.
                row.pushRowAttributes(keyAttr)
            }
        } finally {
            keyboardAttr.recycle()
            keyAttr.recycle()
            includeAttr.recycle()
        }

        XmlParseUtils.checkEndTag(TAG_INCLUDE, parser)
        if (DEBUG) {
            startEndTag(
                "<%s keyboardLayout=%s />", TAG_INCLUDE,
                mResources.getResourceEntryName(keyboardLayout)
            )
        }
        val parserForInclude: XmlResourceParser = mResources.getXml(keyboardLayout)
        try {
            parseMerge(parserForInclude, row, skip)
        } finally {
            if (row != null) {
                // Restore Row attributes.
                row.popRowAttributes()
            }
            parserForInclude.close()
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseMerge(parser: XmlPullParser, row: KeyboardRow?, skip: Boolean) {
        if (DEBUG) startTag("<%s>", TAG_MERGE)
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            val event: Int = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag: String = parser.getName()
                if (TAG_MERGE == tag) {
                    if (row == null) {
                        parseKeyboardContent(parser, skip)
                    } else {
                        parseRowContent(parser, row, skip)
                    }
                    return
                }
                throw XmlParseUtils.ParseException(
                    "Included keyboard layout must have <merge> root element", parser
                )
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseSwitchKeyboard(parser: XmlPullParser, skip: Boolean) {
        parseSwitchInternal(parser, true, null, skip)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseSwitchKeyboardContent(parser: XmlPullParser, skip: Boolean) {
        parseSwitchInternal(parser, false, null, skip)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseSwitchRowContent(
        parser: XmlPullParser,
        row: KeyboardRow,
        skip: Boolean
    ) {
        parseSwitchInternal(parser, false, row, skip)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseSwitchInternal(
        parser: XmlPullParser, parseKeyboard: Boolean,
        row: KeyboardRow?, skip: Boolean
    ) {
        if (DEBUG) startTag(
            "<%s> %s", TAG_SWITCH,
            mParams!!.mId!!
        )
        var selected: Boolean = false
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            val event: Int = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag: String = parser.getName()
                if (TAG_CASE == tag) {
                    selected = selected or parseCase(parser, parseKeyboard, row, selected || skip)
                } else if (TAG_DEFAULT == tag) {
                    selected =
                        selected or parseDefault(parser, parseKeyboard, row, selected || skip)
                } else {
                    throw IllegalStartTag(parser, tag, TAG_SWITCH)
                }
            } else if (event == XmlPullParser.END_TAG) {
                val tag: String = parser.getName()
                if (TAG_SWITCH == tag) {
                    if (DEBUG) endTag("</%s>", TAG_SWITCH)
                    return
                }
                throw IllegalEndTag(parser, tag, TAG_SWITCH)
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseCase(
        parser: XmlPullParser, parseKeyboard: Boolean,
        row: KeyboardRow?, skip: Boolean
    ): Boolean {
        val selected: Boolean = parseCaseCondition(parser)
        if (parseKeyboard) {
            // Processing Keyboard root.
            parseKeyboard(parser, !selected || skip)
        } else if (row == null) {
            // Processing Rows.
            parseKeyboardContent(parser, !selected || skip)
        } else {
            // Processing Keys.
            parseRowContent(parser, row, !selected || skip)
        }
        return selected
    }

    private fun parseCaseCondition(parser: XmlPullParser): Boolean {
        val id: KeyboardId? = mParams!!.mId
        if (id == null) {
            return true
        }
        val attr: AttributeSet = Xml.asAttributeSet(parser)
        val caseAttr: TypedArray = mResources.obtainAttributes(attr, R.styleable.Keyboard_Case)
        if (DEBUG) startTag("<%s>", TAG_CASE)
        try {
            val keyboardLayoutSetMatched: Boolean = matchString(
                caseAttr,
                R.styleable.Keyboard_Case_keyboardLayoutSet,
                id.mSubtype.getKeyboardLayoutSet()
            )
            val keyboardLayoutSetElementMatched: Boolean = matchTypedValue(
                caseAttr,
                R.styleable.Keyboard_Case_keyboardLayoutSetElement, id.mElementId,
                KeyboardId.Companion.elementIdToName(id.mElementId)
            )
            val keyboardThemeMatched: Boolean = matchTypedValue(
                caseAttr,
                R.styleable.Keyboard_Case_keyboardTheme, id.mThemeId,
                KeyboardTheme.Companion.getKeyboardThemeName(id.mThemeId)
            )
            val modeMatched: Boolean = matchTypedValue(
                caseAttr,
                R.styleable.Keyboard_Case_mode, id.mMode, KeyboardId.Companion.modeName(id.mMode)
            )
            val navigateNextMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_navigateNext, id.navigateNext()
            )
            val navigatePreviousMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_navigatePrevious, id.navigatePrevious()
            )
            val passwordInputMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_passwordInput, id.passwordInput()
            )
            val clobberSettingsKeyMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_clobberSettingsKey, id.mClobberSettingsKey
            )
            val languageSwitchKeyEnabledMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_languageSwitchKeyEnabled,
                id.mLanguageSwitchKeyEnabled
            )
            val isMultiLineMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_isMultiLine, id.isMultiLine()
            )
            val imeActionMatched: Boolean = matchInteger(
                caseAttr,
                R.styleable.Keyboard_Case_imeAction, id.imeAction()
            )
            val isIconDefinedMatched: Boolean = isIconDefined(
                caseAttr,
                R.styleable.Keyboard_Case_isIconDefined, mParams.mIconsSet
            )
            val locale: Locale? = id.getLocale()
            val localeCodeMatched: Boolean = matchLocaleCodes(
                caseAttr,
                locale!!
            )
            val languageCodeMatched: Boolean = matchLanguageCodes(
                caseAttr,
                locale
            )
            val countryCodeMatched: Boolean = matchCountryCodes(
                caseAttr,
                locale
            )
            val showMoreKeysMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_showExtraChars, id.mShowMoreKeys
            )
            val showNumberRowMatched: Boolean = matchBoolean(
                caseAttr,
                R.styleable.Keyboard_Case_showNumberRow, id.mShowNumberRow
            )
            val selected: Boolean = keyboardLayoutSetMatched && keyboardLayoutSetElementMatched
                    && keyboardThemeMatched && modeMatched && navigateNextMatched
                    && navigatePreviousMatched && passwordInputMatched
                    && languageSwitchKeyEnabledMatched && clobberSettingsKeyMatched
                    && isMultiLineMatched && imeActionMatched && isIconDefinedMatched
                    && localeCodeMatched && languageCodeMatched && countryCodeMatched
                    && showMoreKeysMatched && showNumberRowMatched

            return selected
        } finally {
            caseAttr.recycle()
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseDefault(
        parser: XmlPullParser, parseKeyboard: Boolean,
        row: KeyboardRow?, skip: Boolean
    ): Boolean {
        if (DEBUG) startTag("<%s>", TAG_DEFAULT)
        if (parseKeyboard) {
            parseKeyboard(parser, skip)
        } else if (row == null) {
            parseKeyboardContent(parser, skip)
        } else {
            parseRowContent(parser, row, skip)
        }
        return true
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseKeyStyle(parser: XmlPullParser, skip: Boolean) {
        val attr: AttributeSet = Xml.asAttributeSet(parser)
        val keyStyleAttr: TypedArray = mResources.obtainAttributes(
            attr, R.styleable.Keyboard_KeyStyle
        )
        val keyAttrs: TypedArray = mResources.obtainAttributes(attr, R.styleable.Keyboard_Key)
        try {
            if (!keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_styleName)) {
                throw XmlParseUtils.ParseException(
                    ("<" + TAG_KEY_STYLE
                            + "/> needs styleName attribute"), parser
                )
            }
            if (DEBUG) {
                startEndTag(
                    "<%s styleName=%s />%s", TAG_KEY_STYLE,
                    keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_styleName)!!,
                    if (skip) " skipped" else ""
                )
            }
            if (!skip) {
                mParams!!.mKeyStyles.parseKeyStyleAttributes(keyStyleAttr, keyAttrs, parser)
            }
        } finally {
            keyStyleAttr.recycle()
            keyAttrs.recycle()
        }
        XmlParseUtils.checkEndTag(TAG_KEY_STYLE, parser)
    }

    private fun startKeyboard() {
    }

    private fun startRow(row: KeyboardRow) {
        mCurrentRow = row
        mPreviousKeyInRow = null
    }

    private fun endRow(row: KeyboardRow) {
        if (mCurrentRow == null) {
            throw RuntimeException("orphan end row tag")
        }
        if (mPreviousKeyInRow != null && !mPreviousKeyInRow!!.isSpacer()) {
            setKeyHitboxRightEdge(mPreviousKeyInRow!!, mParams!!.mOccupiedWidth.toFloat())
            mPreviousKeyInRow = null
        }
        mCurrentY += row.getRowHeight()
        mCurrentRow = null
    }

    private fun endKey(key: Key, row: KeyboardRow) {
        mParams!!.onAddKey(key)
        if (mPreviousKeyInRow != null && !mPreviousKeyInRow!!.isSpacer()) {
            // Make the last key span the gap so there isn't un-clickable space. The current key's
            // hitbox left edge is based on the previous key, so this will make the gap between
            // them split evenly.
            setKeyHitboxRightEdge(mPreviousKeyInRow!!, row.getKeyX() - row.getKeyLeftPadding())
        }
        mPreviousKeyInRow = key
    }

    private fun setKeyHitboxRightEdge(key: Key, xPos: Float) {
        val keyRight: Int = key.getX() + key.getWidth()
        val padding: Float = xPos - keyRight
        key.setHitboxRightEdge(Math.round(padding) + keyRight)
    }

    private fun endKeyboard() {
        mParams!!.removeRedundantMoreKeys()
    }

    companion object {
        private const val BUILDER_TAG: String = "Keyboard.Builder"
        private const val DEBUG: Boolean = false

        // Keyboard XML Tags
        private const val TAG_KEYBOARD: String = "Keyboard"
        private const val TAG_ROW: String = "Row"
        private const val TAG_KEY: String = "Key"
        private const val TAG_SPACER: String = "Spacer"
        private const val TAG_INCLUDE: String = "include"
        private const val TAG_MERGE: String = "merge"
        private const val TAG_SWITCH: String = "switch"
        private const val TAG_CASE: String = "case"
        private const val TAG_DEFAULT: String = "default"
        const val TAG_KEY_STYLE: String = "key-style"

        private const val DEFAULT_KEYBOARD_COLUMNS: Int = 10
        private const val DEFAULT_KEYBOARD_ROWS: Int = 4

        private const val SPACES: String = "                                             "

        private fun spaces(count: Int): String {
            return if ((count < SPACES.length)) SPACES.substring(0, count) else SPACES
        }

        private fun matchLocaleCodes(caseAttr: TypedArray, locale: Locale): Boolean {
            return matchString(caseAttr, R.styleable.Keyboard_Case_localeCode, locale.toString())
        }

        private fun matchLanguageCodes(caseAttr: TypedArray, locale: Locale): Boolean {
            return matchString(
                caseAttr,
                R.styleable.Keyboard_Case_languageCode,
                locale.getLanguage()
            )
        }

        private fun matchCountryCodes(caseAttr: TypedArray, locale: Locale): Boolean {
            return matchString(caseAttr, R.styleable.Keyboard_Case_countryCode, locale.getCountry())
        }

        private fun matchInteger(a: TypedArray, index: Int, value: Int): Boolean {
            // If <case> does not have "index" attribute, that means this <case> is wild-card for
            // the attribute.
            return !a.hasValue(index) || a.getInt(index, 0) == value
        }

        private fun matchBoolean(a: TypedArray, index: Int, value: Boolean): Boolean {
            // If <case> does not have "index" attribute, that means this <case> is wild-card for
            // the attribute.
            return !a.hasValue(index) || a.getBoolean(index, false) == value
        }

        private fun matchString(a: TypedArray, index: Int, value: String?): Boolean {
            // If <case> does not have "index" attribute, that means this <case> is wild-card for
            // the attribute.
            return !a.hasValue(index)
                    || StringUtils.containsInArray(
                value!!, a.getString(
                    index
                )!!
                    .split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            )
        }

        private fun matchTypedValue(
            a: TypedArray, index: Int, intValue: Int,
            strValue: String?
        ): Boolean {
            // If <case> does not have "index" attribute, that means this <case> is wild-card for
            // the attribute.
            val v: TypedValue? = a.peekValue(index)
            if (v == null) {
                return true
            }
            if (ResourceUtils.isIntegerValue(v)) {
                return intValue == a.getInt(index, 0)
            }
            if (ResourceUtils.isStringValue(v)) {
                return StringUtils.containsInArray(
                    strValue!!, a.getString(
                        index
                    )!!
                        .split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                )
            }
            return false
        }

        private fun isIconDefined(
            a: TypedArray, index: Int,
            iconsSet: KeyboardIconsSet
        ): Boolean {
            if (!a.hasValue(index)) {
                return true
            }
            val iconName: String? = a.getString(index)
            val iconId: Int = KeyboardIconsSet.Companion.getIconId(iconName)
            return iconsSet.getIconDrawable(iconId) != null
        }
    }
}
