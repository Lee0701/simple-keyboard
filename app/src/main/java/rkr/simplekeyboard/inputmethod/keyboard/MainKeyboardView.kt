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

import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView
import rkr.simplekeyboard.inputmethod.keyboard.internal.DrawingPreviewPlacerView
import rkr.simplekeyboard.inputmethod.keyboard.internal.DrawingProxy
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyPreviewChoreographer
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyPreviewDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyPreviewView
import rkr.simplekeyboard.inputmethod.keyboard.internal.MoreKeySpec
import rkr.simplekeyboard.inputmethod.keyboard.internal.NonDistinctMultitouchHelper
import rkr.simplekeyboard.inputmethod.keyboard.internal.TimerHandler
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.common.CoordinateUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LanguageOnSpacebarUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.TypefaceUtils
import java.util.WeakHashMap

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextRatio
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFinalAlpha
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator
 * @attr ref R.styleable#MainKeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdTime
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdDistance
 * @attr ref R.styleable#MainKeyboardView_keySelectionByDraggingFinger
 * @attr ref R.styleable#MainKeyboardView_keyRepeatStartTimeout
 * @attr ref R.styleable#MainKeyboardView_keyRepeatInterval
 * @attr ref R.styleable#MainKeyboardView_longPressKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_longPressShiftKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_ignoreAltCodeKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLayout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewOffset
 * @attr ref R.styleable#MainKeyboardView_keyPreviewHeight
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewDismissAnimator
 * @attr ref R.styleable#MainKeyboardView_moreKeysKeyboardLayout
 * @attr ref R.styleable#MainKeyboardView_backgroundDimAlpha
 * @attr ref R.styleable#MainKeyboardView_showMoreKeysKeyboardAtTouchPoint
 * @attr ref R.styleable#MainKeyboardView_gestureFloatingPreviewTextLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping
 * @attr ref R.styleable#MainKeyboardView_gestureDetectFastMoveSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicThresholdDecayDuration
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureSamplingMinimumDistance
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionMinimumTime
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration
 */
class MainKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int = R.attr.mainKeyboardViewStyle
) :
    KeyboardView(context, attrs, defStyle), MoreKeysPanel.Controller,
    DrawingProxy {
    /** Listener for [KeyboardActionListener].  */
    private var mKeyboardActionListener: KeyboardActionListener?

    /* Space key and its icon and background. */
    private var mSpaceKey: Key? = null

    // Stuff to draw language name on spacebar.
    private val mLanguageOnSpacebarFinalAlpha: Int
    private val mLanguageOnSpacebarFadeoutAnimator: ObjectAnimator?
    private var mLanguageOnSpacebarFormatType: Int = 0
    private var mLanguageOnSpacebarAnimAlpha: Int = Constants.Color.ALPHA_OPAQUE
    private val mLanguageOnSpacebarTextRatio: Float
    private var mLanguageOnSpacebarTextSize: Float = 0f
    private val mLanguageOnSpacebarTextColor: Int

    // Stuff to draw altCodeWhileTyping keys.
    private val mAltCodeKeyWhileTypingFadeoutAnimator: ObjectAnimator?
    private val mAltCodeKeyWhileTypingFadeinAnimator: ObjectAnimator?
    private val mAltCodeKeyWhileTypingAnimAlpha: Int = Constants.Color.ALPHA_OPAQUE

    // Drawing preview placer view
    private val mDrawingPreviewPlacerView: DrawingPreviewPlacerView
    private val mOriginCoords: IntArray = CoordinateUtils.newInstance()

    // Key preview
    private val mKeyPreviewDrawParams: KeyPreviewDrawParams
    private val mKeyPreviewChoreographer: KeyPreviewChoreographer

    // More keys keyboard
    private val mBackgroundDimAlphaPaint: Paint = Paint()
    private val mMoreKeysKeyboardContainer: View
    private val mMoreKeysKeyboardCache: WeakHashMap<Key, Keyboard> = WeakHashMap()
    private val mConfigShowMoreKeysKeyboardAtTouchedPoint: Boolean

    // More keys panel (used by both more keys keyboard and more suggestions view)
    // TODO: Consider extending to support multiple more keys panels
    private var mMoreKeysPanel: MoreKeysPanel? = null

    private val mKeyDetector: KeyDetector
    private val mNonDistinctMultitouchHelper: NonDistinctMultitouchHelper?

    private val mTimerHandler: TimerHandler
    private val mLanguageOnSpacebarHorizontalMargin: Int

    init {
        val drawingPreviewPlacerView: DrawingPreviewPlacerView =
            DrawingPreviewPlacerView(context, attrs)

        val mainKeyboardViewAttr: TypedArray = context.obtainStyledAttributes(
            attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView
        )
        val ignoreAltCodeKeyTimeout: Int = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0
        )
        mTimerHandler = TimerHandler(this, ignoreAltCodeKeyTimeout)

        val keyHysteresisDistance: Float = mainKeyboardViewAttr.getDimension(
            R.styleable.MainKeyboardView_keyHysteresisDistance, 0.0f
        )
        val keyHysteresisDistanceForSlidingModifier: Float = mainKeyboardViewAttr.getDimension(
            R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0.0f
        )
        mKeyDetector = KeyDetector(
            keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier
        )

        PointerTracker.init(mainKeyboardViewAttr, mTimerHandler, this /* DrawingProxy */)

        val hasDistinctMultitouch: Boolean = context.getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        mNonDistinctMultitouchHelper = if (hasDistinctMultitouch)
            null
        else
            NonDistinctMultitouchHelper()

        val backgroundDimAlpha: Int = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_backgroundDimAlpha, 0
        )
        mBackgroundDimAlphaPaint.setColor(Color.BLACK)
        mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha)
        mLanguageOnSpacebarTextRatio = mainKeyboardViewAttr.getFraction(
            R.styleable.MainKeyboardView_languageOnSpacebarTextRatio, 1, 1, 1.0f
        )
        mLanguageOnSpacebarTextColor = mainKeyboardViewAttr.getColor(
            R.styleable.MainKeyboardView_languageOnSpacebarTextColor, 0
        )
        mLanguageOnSpacebarFinalAlpha = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
            Constants.Color.ALPHA_OPAQUE
        )
        val languageOnSpacebarFadeoutAnimatorResId: Int = mainKeyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0
        )
        val altCodeKeyWhileTypingFadeoutAnimatorResId: Int = mainKeyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0
        )
        val altCodeKeyWhileTypingFadeinAnimatorResId: Int = mainKeyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0
        )

        mKeyPreviewDrawParams = KeyPreviewDrawParams(mainKeyboardViewAttr)
        mKeyPreviewChoreographer = KeyPreviewChoreographer(mKeyPreviewDrawParams)

        val moreKeysKeyboardLayoutId: Int = mainKeyboardViewAttr.getResourceId(
            R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0
        )
        mConfigShowMoreKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(
            R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false
        )

        mainKeyboardViewAttr.recycle()

        mDrawingPreviewPlacerView = drawingPreviewPlacerView

        val inflater: LayoutInflater = LayoutInflater.from(getContext())
        mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null)
        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
            languageOnSpacebarFadeoutAnimatorResId, this
        )
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
            altCodeKeyWhileTypingFadeoutAnimatorResId, this
        )
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
            altCodeKeyWhileTypingFadeinAnimatorResId, this
        )

        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER

        mLanguageOnSpacebarHorizontalMargin = getResources().getDimension(
            R.dimen.config_language_on_spacebar_horizontal_margin
        ).toInt()
    }

    private fun loadObjectAnimator(resId: Int, target: Any): ObjectAnimator? {
        if (resId == 0) {
            // TODO: Stop returning null.
            return null
        }
        val animator: ObjectAnimator? = AnimatorInflater.loadAnimator(
            getContext(), resId
        ) as ObjectAnimator?
        if (animator != null) {
            animator.setTarget(target)
        }
        return animator
    }

    // Implements {@link DrawingProxy#startWhileTypingAnimation(int)}.
    /**
     * Called when a while-typing-animation should be started.
     * @param fadeInOrOut [DrawingProxy.FADE_IN] starts while-typing-fade-in animation.
     * [DrawingProxy.FADE_OUT] starts while-typing-fade-out animation.
     */
    override fun startWhileTypingAnimation(fadeInOrOut: Int) {
        when (fadeInOrOut) {
            DrawingProxy.FADE_IN -> cancelAndStartAnimators(
                mAltCodeKeyWhileTypingFadeoutAnimator, mAltCodeKeyWhileTypingFadeinAnimator
            )

            DrawingProxy.FADE_OUT -> cancelAndStartAnimators(
                mAltCodeKeyWhileTypingFadeinAnimator, mAltCodeKeyWhileTypingFadeoutAnimator
            )
        }
    }

    fun setLanguageOnSpacebarAnimAlpha(alpha: Int) {
        mLanguageOnSpacebarAnimAlpha = alpha
        invalidateKey(mSpaceKey)
    }

    fun setKeyboardActionListener(listener: KeyboardActionListener?) {
        mKeyboardActionListener = listener
        PointerTracker.setKeyboardActionListener(listener)
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    fun getKeyX(x: Int): Int {
        return if (Constants.isValidCoordinate(x)) mKeyDetector.getTouchX(x) else x
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    fun getKeyY(y: Int): Int {
        return if (Constants.isValidCoordinate(y)) mKeyDetector.getTouchY(y) else y
    }

    override var keyboard: Keyboard?
        get() = super.keyboard
        /**
         * Attaches a keyboard to this view. The keyboard can be switched at any time and the
         * view will re-layout itself to accommodate the keyboard.
         * @see Keyboard
         *
         * @see .getKeyboard
         * @param keyboard the keyboard to display in this view
         */
        set(keyboard) {
            // Remove any pending messages, except dismissing preview and key repeat.
            mTimerHandler.cancelLongPressTimers()
            super.keyboard = keyboard
            mKeyDetector.setKeyboard(
                keyboard!!, -getPaddingLeft().toFloat(), -getPaddingTop() + verticalCorrection
            )
            PointerTracker.setKeyDetector(mKeyDetector)
            mMoreKeysKeyboardCache.clear()

            mSpaceKey =
                keyboard.getKey(Constants.CODE_SPACE)
            val keyHeight: Int = keyboard.mMostCommonKeyHeight
            mLanguageOnSpacebarTextSize = keyHeight * mLanguageOnSpacebarTextRatio
        }

    /**
     * Enables or disables the key preview popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * @param previewEnabled whether or not to enable the key feedback preview
     * @param delay the delay after which the preview is dismissed
     */
    fun setKeyPreviewPopupEnabled(previewEnabled: Boolean, delay: Int) {
        mKeyPreviewDrawParams.setPopupEnabled(previewEnabled, delay)
    }

    private fun locatePreviewPlacerView() {
        getLocationInWindow(mOriginCoords)
        mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords)
    }

    private fun installPreviewPlacerView() {
        val rootView: View? = getRootView()
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view")
            return
        }
        val windowContentView: ViewGroup? =
            rootView.findViewById<View>(android.R.id.content) as ViewGroup?
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView")
            return
        }
        windowContentView.addView(mDrawingPreviewPlacerView)
    }

    // Implements {@link DrawingProxy#onKeyPressed(Key,boolean)}.
    override fun onKeyPressed(key: Key, withPreview: Boolean) {
        key.onPressed()
        invalidateKey(key)
        if (withPreview && !key.noKeyPreview()) {
            showKeyPreview(key)
        }
    }

    private fun showKeyPreview(key: Key) {
        val keyboard: Keyboard = keyboard ?: return
        val previewParams: KeyPreviewDrawParams = mKeyPreviewDrawParams
        if (!previewParams.isPopupEnabled) {
            previewParams.visibleOffset = -Math.round(keyboard.mVerticalGap)
            return
        }

        locatePreviewPlacerView()
        getLocationInWindow(mOriginCoords)
        mKeyPreviewChoreographer.placeAndShowKeyPreview(
            key, keyboard.mIconsSet, keyDrawParams,
            mOriginCoords, mDrawingPreviewPlacerView, isHardwareAccelerated()
        )
    }

    private fun dismissKeyPreviewWithoutDelay(key: Key) {
        mKeyPreviewChoreographer.dismissKeyPreview(key, false /* withAnimation */)
        invalidateKey(key)
    }

    // Implements {@link DrawingProxy#onKeyReleased(Key,boolean)}.
    override fun onKeyReleased(key: Key, withAnimation: Boolean) {
        key.onReleased()
        invalidateKey(key)
        if (!key.noKeyPreview()) {
            if (withAnimation) {
                dismissKeyPreview(key)
            } else {
                dismissKeyPreviewWithoutDelay(key)
            }
        }
    }

    private fun dismissKeyPreview(key: Key) {
        if (isHardwareAccelerated()) {
            mKeyPreviewChoreographer.dismissKeyPreview(key, true /* withAnimation */)
            return
        }
        // TODO: Implement preference option to control key preview method and duration.
        mTimerHandler.postDismissKeyPreview(key, mKeyPreviewDrawParams.lingerTimeout.toLong())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installPreviewPlacerView()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mDrawingPreviewPlacerView.removeAllViews()
    }

    // Implements {@link DrawingProxy@showMoreKeysKeyboard(Key,PointerTracker)}.
    //@Override
    override fun showMoreKeysKeyboard(
        key: Key,
        tracker: PointerTracker
    ): MoreKeysPanel? {
        val moreKeys: Array<MoreKeySpec?>? = key.moreKeys
        if (moreKeys == null) {
            return null
        }
        var moreKeysKeyboard: Keyboard? = mMoreKeysKeyboardCache.get(key)
        if (moreKeysKeyboard == null) {
            // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
            // {@link KeyPreviewChoreographer#placeKeyPreview(Key,TextView,KeyboardIconsSet,KeyDrawParams,int,int[]},
            // though there may be some chances that the value is zero. <code>width == 0</code>
            // will cause zero-division error at
            // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
            val isSingleMoreKeyWithPreview: Boolean = mKeyPreviewDrawParams.isPopupEnabled
                    && !key.noKeyPreview() && moreKeys.size == 1 && mKeyPreviewDrawParams.visibleWidth > 0
            val builder: MoreKeysKeyboard.Builder = MoreKeysKeyboard.Builder(
                getContext(), key, keyboard!!, isSingleMoreKeyWithPreview,
                mKeyPreviewDrawParams.visibleWidth,
                mKeyPreviewDrawParams.visibleHeight, newLabelPaint(key)
            )
            moreKeysKeyboard = builder.build()
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard)
        }

        val moreKeysKeyboardView: MoreKeysKeyboardView =
            mMoreKeysKeyboardContainer.findViewById(R.id.more_keys_keyboard_view)
        moreKeysKeyboardView.keyboard = moreKeysKeyboard
        mMoreKeysKeyboardContainer.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val lastCoords: IntArray = CoordinateUtils.newInstance()
        tracker.getLastCoordinates(lastCoords)
        val keyPreviewEnabled: Boolean = mKeyPreviewDrawParams.isPopupEnabled
                && !key.noKeyPreview()
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        val pointX: Int = if ((mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled))
            CoordinateUtils.x(lastCoords)
        else
            key.x + key.width / 2
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        val pointY: Int = (key.y + mKeyPreviewDrawParams.visibleOffset
                + Math.round(moreKeysKeyboard.mBottomPadding))
        moreKeysKeyboardView.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener)
        return moreKeysKeyboardView
    }

    val isInDraggingFinger: Boolean
        get() {
            if (isShowingMoreKeysPanel) {
                return true
            }
            return PointerTracker.isAnyInDraggingFinger
        }

    override fun onShowMoreKeysPanel(panel: MoreKeysPanel) {
        locatePreviewPlacerView()
        // Dismiss another {@link MoreKeysPanel} that may be being showed.
        onDismissMoreKeysPanel()
        // Dismiss all key previews that may be being showed.
        PointerTracker.setReleasedKeyGraphicsToAllKeys()
        // Dismiss sliding key input preview that may be being showed.
        panel.showInParent(mDrawingPreviewPlacerView)
        mMoreKeysPanel = panel
    }

    val isShowingMoreKeysPanel: Boolean
        get() {
            return mMoreKeysPanel != null && mMoreKeysPanel!!.isShowingInParent
        }

    override fun onCancelMoreKeysPanel() {
        PointerTracker.dismissAllMoreKeysPanels()
    }

    override fun onDismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel) {
            mMoreKeysPanel!!.removeFromParent()
            mMoreKeysPanel = null
        }
    }

    fun startDoubleTapShiftKeyTimer() {
        mTimerHandler.startDoubleTapShiftKeyTimer()
    }

    fun cancelDoubleTapShiftKeyTimer() {
        mTimerHandler.cancelDoubleTapShiftKeyTimer()
    }

    val isInDoubleTapShiftKeyTimeout: Boolean
        get() {
            return mTimerHandler.isInDoubleTapShiftKeyTimeout
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (keyboard == null) {
            return false
        }
        if (mNonDistinctMultitouchHelper != null) {
            if (event.getPointerCount() > 1 && mTimerHandler.isInKeyRepeat) {
                // Key repeating timer will be canceled if 2 or more keys are in action.
                mTimerHandler.cancelKeyRepeatTimers()
            }
            // Non distinct multitouch screen support
            mNonDistinctMultitouchHelper.processMotionEvent(event, mKeyDetector)
            return true
        }
        return processMotionEvent(event)
    }

    fun processMotionEvent(event: MotionEvent): Boolean {
        val index: Int = event.getActionIndex()
        val id: Int = event.getPointerId(index)
        val tracker: PointerTracker = PointerTracker.getPointerTracker(id)
        // When a more keys panel is showing, we should ignore other fingers' single touch events
        // other than the finger that is showing the more keys panel.
        if (isShowingMoreKeysPanel && !tracker.isShowingMoreKeysPanel
            && PointerTracker.activePointerTrackerCount == 1
        ) {
            return true
        }
        tracker.processMotionEvent(event, mKeyDetector)
        return true
    }

    fun cancelAllOngoingEvents() {
        mTimerHandler.cancelAllMessages()
        PointerTracker.setReleasedKeyGraphicsToAllKeys()
        PointerTracker.dismissAllMoreKeysPanels()
        PointerTracker.cancelAllPointerTrackers()
    }

    fun closing() {
        cancelAllOngoingEvents()
        mMoreKeysKeyboardCache.clear()
    }

    fun onHideWindow() {
        onDismissMoreKeysPanel()
    }

    fun startDisplayLanguageOnSpacebar(
        subtypeChanged: Boolean,
        languageOnSpacebarFormatType: Int
    ) {
        if (subtypeChanged) {
            KeyPreviewView.clearTextCache()
        }
        mLanguageOnSpacebarFormatType = languageOnSpacebarFormatType
        val animator: ObjectAnimator? = mLanguageOnSpacebarFadeoutAnimator
        if (animator == null) {
            mLanguageOnSpacebarFormatType = LanguageOnSpacebarUtils.FORMAT_TYPE_NONE
        } else {
            if (subtypeChanged
                && languageOnSpacebarFormatType != LanguageOnSpacebarUtils.FORMAT_TYPE_NONE
            ) {
                setLanguageOnSpacebarAnimAlpha(Constants.Color.ALPHA_OPAQUE)
                if (animator.isStarted()) {
                    animator.cancel()
                }
                animator.start()
            } else {
                if (!animator.isStarted()) {
                    mLanguageOnSpacebarAnimAlpha = mLanguageOnSpacebarFinalAlpha
                }
            }
        }
        invalidateKey(mSpaceKey)
    }

    override fun onDrawKeyTopVisuals(
        key: Key, canvas: Canvas, paint: Paint,
        params: KeyDrawParams
    ) {
        if (key.altCodeWhileTyping()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha
        }
        super.onDrawKeyTopVisuals(key, canvas, paint, params)
        val code: Int = key.code
        if (code == Constants.CODE_SPACE) {
            // If more than one language is enabled in current input method
            val imm: RichInputMethodManager = RichInputMethodManager.instance
            if (imm.hasMultipleEnabledSubtypes()) {
                drawLanguageOnSpacebar(key, canvas, paint)
            }
        }
    }

    private fun fitsTextIntoWidth(width: Int, text: String, paint: Paint): Boolean {
        val maxTextWidth: Int = width - mLanguageOnSpacebarHorizontalMargin * 2
        paint.setTextScaleX(1.0f)
        val textWidth: Float = TypefaceUtils.getStringWidth(text, paint)
        if (textWidth < width) {
            return true
        }

        val scaleX: Float = maxTextWidth / textWidth
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) {
            return false
        }

        paint.setTextScaleX(scaleX)
        return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth
    }

    // Layout language name on spacebar.
    private fun layoutLanguageOnSpacebar(
        paint: Paint,
        subtype: Subtype, width: Int
    ): String {
        // Choose appropriate language name to fit into the width.
        if (mLanguageOnSpacebarFormatType == LanguageOnSpacebarUtils.FORMAT_TYPE_FULL_LOCALE) {
            val fullText: String =
                LocaleResourceUtils.getLocaleDisplayNameInLocale(subtype.locale)
            if (fitsTextIntoWidth(width, fullText, paint)) {
                return fullText
            }
        }

        val middleText: String =
            LocaleResourceUtils.getLanguageDisplayNameInLocale(subtype.locale)
        if (fitsTextIntoWidth(width, middleText, paint)) {
            return middleText
        }

        return ""
    }

    private fun drawLanguageOnSpacebar(key: Key, canvas: Canvas, paint: Paint) {
        val keyboard: Keyboard? = keyboard
        if (keyboard == null) {
            return
        }
        val width: Int = key.width
        val height: Int = key.height
        paint.setTextAlign(Align.CENTER)
        paint.setTypeface(Typeface.DEFAULT)
        paint.setTextSize(mLanguageOnSpacebarTextSize)
        val language: String = layoutLanguageOnSpacebar(
            paint,
            keyboard.mId!!.mSubtype!!, width
        )
        // Draw language text with shadow
        val descent: Float = paint.descent()
        val textHeight: Float = -paint.ascent() + descent
        val baseline: Float = height / 2 + textHeight / 2
        paint.setColor(mLanguageOnSpacebarTextColor)
        paint.setAlpha(mLanguageOnSpacebarAnimAlpha)
        canvas.drawText(language, (width / 2).toFloat(), baseline - descent, paint)
        paint.clearShadowLayer()
        paint.setTextScaleX(1.0f)
    }

    companion object {
        private val TAG: String = MainKeyboardView::class.java.getSimpleName()

        // The minimum x-scale to fit the language name on spacebar.
        private const val MINIMUM_XSCALE_OF_LANGUAGE_NAME: Float = 0.8f

        private fun cancelAndStartAnimators(
            animatorToCancel: ObjectAnimator?,
            animatorToStart: ObjectAnimator?
        ) {
            if (animatorToCancel == null || animatorToStart == null) {
                // TODO: Stop using null as a no-operation animator.
                return
            }
            var startFraction: Float = 0.0f
            if (animatorToCancel.isStarted()) {
                animatorToCancel.cancel()
                startFraction = 1.0f - animatorToCancel.getAnimatedFraction()
            }
            val startTime: Long = (animatorToStart.getDuration() * startFraction).toLong()
            animatorToStart.start()
            animatorToStart.setCurrentPlayTime(startTime)
        }
    }
}
