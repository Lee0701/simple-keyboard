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
package rkr.simplekeyboard.inputmethod.keyboard.internal

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import rkr.simplekeyboard.inputmethod.keyboard.Key

/**
 * The pop up key preview view.
 */
class KeyPreviewView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) :
    TextView(context, attrs, defStyleAttr) {
    private val mBackgroundPadding: Rect = Rect()

    init {
        setGravity(Gravity.CENTER)
    }

    fun setPreviewVisual(
        key: Key, iconsSet: KeyboardIconsSet,
        drawParams: KeyDrawParams
    ) {
        // What we show as preview should match what we show on a key top in onDraw().
        val iconId: Int = key.iconId
        if (iconId != KeyboardIconsSet.ICON_UNDEFINED) {
            setCompoundDrawables(null, null, null, key.getPreviewIcon(iconsSet))
            setText(null)
            return
        }

        setCompoundDrawables(null, null, null, null)
        setTextColor(drawParams.mPreviewTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, key.selectPreviewTextSize(drawParams).toFloat())
        setTypeface(key.selectPreviewTypeface(drawParams))
        // TODO Should take care of temporaryShiftLabel here.
        setTextAndScaleX(key.previewLabel)
    }

    private fun setTextAndScaleX(text: String?) {
        setTextScaleX(1.0f)
        setText(text)
        if (sNoScaleXTextSet.contains(text)) {
            return
        }
        // TODO: Override {@link #setBackground(Drawable)} that is supported from API 16 and
        // calculate maximum text width.
        val background: Drawable? = getBackground()
        if (background == null) {
            return
        }
        background.getPadding(mBackgroundPadding)
        val maxWidth: Int = (background.getIntrinsicWidth() - mBackgroundPadding.left
                - mBackgroundPadding.right)
        val width: Float = getTextWidth(text, getPaint())
        if (width <= maxWidth) {
            sNoScaleXTextSet.add(text)
            return
        }
        setTextScaleX(maxWidth / width)
    }

    companion object {
        private val sNoScaleXTextSet: HashSet<String?> = HashSet()

        fun clearTextCache() {
            sNoScaleXTextSet.clear()
        }

        private fun getTextWidth(text: String?, paint: TextPaint): Float {
            if (TextUtils.isEmpty(text)) {
                return 0.0f
            }
            val len: Int = text!!.length
            val widths: FloatArray = FloatArray(len)
            val count: Int = paint.getTextWidths(text, 0, len, widths)
            var width: Float = 0f
            for (i in 0 until count) {
                width += widths.get(i)
            }
            return width
        } /*public void setPreviewBackground(boolean customColorEnabled, int customColor) {
        final Drawable background = getBackground();
        if (customColorEnabled)
            background.setColorFilter(customColor, PorterDuff.Mode.OVERLAY);
    }*/
    }
}
