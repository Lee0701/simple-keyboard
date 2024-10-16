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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.View
import android.view.ViewGroup
import rkr.simplekeyboard.inputmethod.keyboard.Key
import rkr.simplekeyboard.inputmethod.latin.common.CoordinateUtils
import rkr.simplekeyboard.inputmethod.latin.utils.ViewLayoutUtils
import java.util.ArrayDeque
import kotlin.math.max

/**
 * This class controls pop up key previews. This class decides:
 * - what kind of key previews should be shown.
 * - where key previews should be placed.
 * - how key previews should be shown and dismissed.
 */
class KeyPreviewChoreographer(params: KeyPreviewDrawParams) {
    // Free {@link KeyPreviewView} pool that can be used for key preview.
    private val mFreeKeyPreviewViews: ArrayDeque<KeyPreviewView> = ArrayDeque()

    // Map from {@link Key} to {@link KeyPreviewView} that is currently being displayed as key
    // preview.
    private val mShowingKeyPreviewViews: HashMap<Key, KeyPreviewView> = HashMap()

    private val mParams: KeyPreviewDrawParams

    init {
        mParams = params
    }

    fun getKeyPreviewView(key: Key, placerView: ViewGroup): KeyPreviewView {
        var keyPreviewView: KeyPreviewView? = mShowingKeyPreviewViews.remove(key)
        if (keyPreviewView != null) {
            keyPreviewView.setScaleX(1f)
            keyPreviewView.setScaleY(1f)
            return keyPreviewView
        }
        keyPreviewView = mFreeKeyPreviewViews.poll()
        if (keyPreviewView != null) {
            keyPreviewView.setScaleX(1f)
            keyPreviewView.setScaleY(1f)
            return keyPreviewView
        }
        val context: Context = placerView.getContext()
        keyPreviewView = KeyPreviewView(context, null /* attrs */)
        keyPreviewView.setBackgroundResource(mParams.mPreviewBackgroundResId)
        placerView.addView(keyPreviewView, ViewLayoutUtils.newLayoutParam(placerView, 0, 0))
        return keyPreviewView
    }

    fun dismissKeyPreview(key: Key?, withAnimation: Boolean) {
        if (key == null) {
            return
        }
        val keyPreviewView: KeyPreviewView? = mShowingKeyPreviewViews.get(key)
        if (keyPreviewView == null) {
            return
        }
        val tag: Any = keyPreviewView.getTag()
        if (withAnimation) {
            if (tag is KeyPreviewAnimators) {
                tag.startDismiss()
                return
            }
        }
        // Dismiss preview without animation.
        mShowingKeyPreviewViews.remove(key)
        if (tag is Animator) {
            tag.cancel()
        }
        keyPreviewView.setTag(null)
        keyPreviewView.setVisibility(View.INVISIBLE)
        mFreeKeyPreviewViews.add(keyPreviewView)
    }

    fun placeAndShowKeyPreview(
        key: Key, iconsSet: KeyboardIconsSet,
        drawParams: KeyDrawParams?, keyboardOrigin: IntArray,
        placerView: ViewGroup, withAnimation: Boolean
    ) {
        val keyPreviewView: KeyPreviewView = getKeyPreviewView(key, placerView)
        placeKeyPreview(
            key, keyPreviewView, iconsSet, drawParams!!, keyboardOrigin
        )
        showKeyPreview(key, keyPreviewView, withAnimation)
    }

    private fun placeKeyPreview(
        key: Key, keyPreviewView: KeyPreviewView,
        iconsSet: KeyboardIconsSet, drawParams: KeyDrawParams,
        originCoords: IntArray
    ) {
        keyPreviewView.setPreviewVisual(key, iconsSet, drawParams)
        keyPreviewView.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mParams.setGeometry(keyPreviewView)
        val previewWidth: Int = max(
            keyPreviewView.getMeasuredWidth().toDouble(),
            mParams.mMinPreviewWidth.toDouble()
        ).toInt()
        val previewHeight: Int = mParams.mPreviewHeight
        val keyWidth: Int = key.getWidth()
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        val previewX: Int = (key.getX() - (previewWidth - keyWidth) / 2
                + CoordinateUtils.x(originCoords))
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        val previewY: Int = (key.getY() - previewHeight + mParams.mPreviewOffset
                + CoordinateUtils.y(originCoords))

        ViewLayoutUtils.placeViewAt(
            keyPreviewView, previewX, previewY, previewWidth, previewHeight
        )
        //keyPreviewView.setPivotX(previewWidth / 2.0f);
        //keyPreviewView.setPivotY(previewHeight);
    }

    fun showKeyPreview(
        key: Key, keyPreviewView: KeyPreviewView,
        withAnimation: Boolean
    ) {
        if (!withAnimation) {
            keyPreviewView.setVisibility(View.VISIBLE)
            mShowingKeyPreviewViews.put(key, keyPreviewView)
            return
        }

        // Show preview with animation.
        val dismissAnimator: Animator = createDismissAnimator(key, keyPreviewView)
        val animators: KeyPreviewAnimators = KeyPreviewAnimators(dismissAnimator)
        keyPreviewView.setTag(animators)
        showKeyPreview(key, keyPreviewView, false /* withAnimation */)
    }

    private fun createDismissAnimator(key: Key, keyPreviewView: KeyPreviewView): Animator {
        val dismissAnimator: Animator = mParams.createDismissAnimator(keyPreviewView)
        dismissAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                dismissKeyPreview(key, false /* withAnimation */)
            }
        })
        return dismissAnimator
    }

    private class KeyPreviewAnimators(dismissAnimator: Animator) :
        AnimatorListenerAdapter() {
        private val mDismissAnimator: Animator

        init {
            mDismissAnimator = dismissAnimator
        }

        fun startDismiss() {
            mDismissAnimator.start()
        }
    }
}
