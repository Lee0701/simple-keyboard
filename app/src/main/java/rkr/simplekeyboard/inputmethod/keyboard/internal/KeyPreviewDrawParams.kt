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

import android.animation.Animator
import android.animation.AnimatorInflater
import android.content.res.TypedArray
import android.view.View
import android.view.animation.AccelerateInterpolator
import rkr.simplekeyboard.inputmethod.R
import kotlin.math.max

class KeyPreviewDrawParams(mainKeyboardViewAttr: TypedArray) {
    // XML attributes of {@link MainKeyboardView}.
    val mPreviewOffset: Int
    val mPreviewHeight: Int
    val mMinPreviewWidth: Int
    val mPreviewBackgroundResId: Int
    private val mDismissAnimatorResId: Int
    var lingerTimeout: Int
        private set
    var isPopupEnabled: Boolean = true
        private set

    // The graphical geometry of the key preview.
    // <-width->
    // +-------+   ^
    // |       |   |
    // |preview| height (visible)
    // |       |   |
    // +       + ^ v
    //  \     /  |offset
    // +-\   /-+ v
    // |  +-+  |
    // |parent |
    // |    key|
    // +-------+
    // The background of a {@link TextView} being used for a key preview may have invisible
    // paddings. To align the more keys keyboard panel's visible part with the visible part of
    // the background, we need to record the width and height of key preview that don't include
    // invisible paddings.
    var visibleWidth: Int = 0
        private set
    var visibleHeight: Int = 0
        private set

    // The key preview may have an arbitrary offset and its background that may have a bottom
    // padding. To align the more keys keyboard and the key preview we also need to record the
    // offset between the top edge of parent key and the bottom of the visible part of key
    // preview background.
    var visibleOffset: Int = 0

    fun setGeometry(previewTextView: View) {
        val previewWidth: Int =
            max(
                previewTextView.getMeasuredWidth().toDouble(),
                mMinPreviewWidth.toDouble()
            ).toInt()

        // The width and height of visible part of the key preview background. The content marker
        // of the background 9-patch have to cover the visible part of the background.
        visibleWidth = (previewWidth - previewTextView.getPaddingLeft()
                - previewTextView.getPaddingRight())
        visibleHeight = (mPreviewHeight - previewTextView.getPaddingTop()
                - previewTextView.getPaddingBottom())
        // The distance between the top edge of the parent key and the bottom of the visible part
        // of the key preview background.
        visibleOffset = mPreviewOffset - previewTextView.getPaddingBottom()
    }

    fun setPopupEnabled(enabled: Boolean, lingerTimeout: Int) {
        isPopupEnabled = enabled
        this.lingerTimeout = lingerTimeout
    }

    init {
        mPreviewOffset = mainKeyboardViewAttr.getDimensionPixelOffset(
            R.styleable.MainKeyboardView_keyPreviewOffset, 0
        )
        mPreviewHeight = mainKeyboardViewAttr.getDimensionPixelSize(
            R.styleable.MainKeyboardView_keyPreviewHeight, 0
        )
        mMinPreviewWidth = mainKeyboardViewAttr.getDimensionPixelSize(
            R.styleable.MainKeyboardView_keyPreviewWidth, 0
        )
        mPreviewBackgroundResId = mainKeyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_keyPreviewBackground, 0
        )
        lingerTimeout = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_keyPreviewLingerTimeout, 0
        )
        mDismissAnimatorResId = mainKeyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_keyPreviewDismissAnimator, 0
        )
    }

    fun createDismissAnimator(target: View): Animator {
        val animator: Animator = AnimatorInflater.loadAnimator(
            target.getContext(), mDismissAnimatorResId
        )
        animator.setTarget(target)
        animator.setInterpolator(ACCELERATE_INTERPOLATOR)
        return animator
    }

    companion object {
        private val ACCELERATE_INTERPOLATOR: AccelerateInterpolator = AccelerateInterpolator()
    }
}
