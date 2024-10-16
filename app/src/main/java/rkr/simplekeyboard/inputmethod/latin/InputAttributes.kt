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
package rkr.simplekeyboard.inputmethod.latin

import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import rkr.simplekeyboard.inputmethod.latin.utils.InputTypeUtils

/**
 * Class to hold attributes of the input field.
 */
class InputAttributes(editorInfo: EditorInfo?, isFullscreenMode: Boolean) {
    private val TAG: String = InputAttributes::class.java.getSimpleName()

    val mTargetApplicationPackageName: String?
    var mInputTypeNoAutoCorrect: Boolean
    val mIsPasswordField: Boolean
    var mShouldShowSuggestions: Boolean
    var mApplicationSpecifiedCompletionOn: Boolean
    var mShouldInsertSpacesAutomatically: Boolean

    /**
     * Whether the floating gesture preview should be disabled. If true, this should override the
     * corresponding keyboard settings preference, always suppressing the floating preview text.
     */
    private val mInputType: Int

    init {
        mTargetApplicationPackageName = if (null != editorInfo) editorInfo.packageName else null
        val inputType: Int = if (null != editorInfo) editorInfo.inputType else 0
        val inputClass: Int = inputType and InputType.TYPE_MASK_CLASS
        mInputType = inputType
        mIsPasswordField = InputTypeUtils.isPasswordInputType(inputType)
                || InputTypeUtils.isVisiblePasswordInputType(inputType)
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            // If we are not looking at a TYPE_CLASS_TEXT field, the following strange
            // cases may arise, so we do a couple sanity checks for them. If it's a
            // TYPE_CLASS_TEXT field, these special cases cannot happen, by construction
            // of the flags.
            if (null == editorInfo) {
                Log.w(TAG, "No editor info for this field. Bug?")
            } else if (InputType.TYPE_NULL == inputType) {
                // TODO: We should honor TYPE_NULL specification.
                Log.i(TAG, "InputType.TYPE_NULL is specified")
            } else if (inputClass == 0) {
                // TODO: is this check still necessary?
                Log.w(
                    TAG, String.format(
                        "Unexpected input class: inputType=0x%08x"
                                + " imeOptions=0x%08x", inputType, editorInfo.imeOptions
                    )
                )
            }
            mShouldShowSuggestions = false
            mInputTypeNoAutoCorrect = false
            mApplicationSpecifiedCompletionOn = false
            mShouldInsertSpacesAutomatically = false
        } else {
            // inputClass == InputType.TYPE_CLASS_TEXT
            val variation: Int = inputType and InputType.TYPE_MASK_VARIATION
            val flagNoSuggestions: Boolean =
                0 != (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            val flagMultiLine: Boolean =
                0 != (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            val flagAutoCorrect: Boolean =
                0 != (inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            val flagAutoComplete: Boolean =
                0 != (inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)

            // TODO: Have a helper method in InputTypeUtils
            // Make sure that passwords are not displayed in {@link SuggestionStripView}.
            val shouldSuppressSuggestions: Boolean = mIsPasswordField
                    || InputTypeUtils.isEmailVariation(variation)
                    || InputType.TYPE_TEXT_VARIATION_URI == variation || InputType.TYPE_TEXT_VARIATION_FILTER == variation || flagNoSuggestions
                    || flagAutoComplete
            mShouldShowSuggestions = !shouldSuppressSuggestions

            mShouldInsertSpacesAutomatically = InputTypeUtils.isAutoSpaceFriendlyType(inputType)

            // If it's a browser edit field and auto correct is not ON explicitly, then
            // disable auto correction, but keep suggestions on.
            // If NO_SUGGESTIONS is set, don't do prediction.
            // If it's not multiline and the autoCorrect flag is not set, then don't correct
            mInputTypeNoAutoCorrect =
                (variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT && !flagAutoCorrect)
                        || flagNoSuggestions
                        || (!flagAutoCorrect && !flagMultiLine)

            mApplicationSpecifiedCompletionOn = flagAutoComplete && isFullscreenMode
        }
    }

    val isTypeNull: Boolean
        get() {
            return InputType.TYPE_NULL == mInputType
        }

    fun isSameInputType(editorInfo: EditorInfo): Boolean {
        return editorInfo.inputType == mInputType
    }

    // Pretty print
    override fun toString(): String {
        return String.format(
            "%s: inputType=0x%08x%s%s%s%s%s targetApp=%s\n", javaClass.getSimpleName(),
            mInputType,
            (if (mInputTypeNoAutoCorrect) " noAutoCorrect" else ""),
            (if (mIsPasswordField) " password" else ""),
            (if (mShouldShowSuggestions) " shouldShowSuggestions" else ""),
            (if (mApplicationSpecifiedCompletionOn) " appSpecified" else ""),
            (if (mShouldInsertSpacesAutomatically) " insertSpaces" else ""),
            mTargetApplicationPackageName
        )
    }

    companion object {
        fun inPrivateImeOptions(
            packageName: String?, key: String,
            editorInfo: EditorInfo?
        ): Boolean {
            if (editorInfo == null) return false
            val findingKey: String = if ((packageName != null)) packageName + "." + key else key
            return StringUtils.containsInCommaSplittableText(
                findingKey,
                editorInfo.privateImeOptions
            )
        }
    }
}
