/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.View.OnApplyWindowInsetsListener
import android.view.WindowInsets
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyVisualAttributes
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.settings.Settings
import rkr.simplekeyboard.inputmethod.latin.utils.TypefaceUtils
import kotlin.math.max
import kotlin.math.min

/**
 * A view that renders a virtual [Keyboard].
 *
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_functionalKeyBackground
 * @attr ref R.styleable#KeyboardView_spacebarBackground
 * @attr ref R.styleable#KeyboardView_spacebarIconWidthRatio
 * @attr ref R.styleable#Keyboard_Key_keyLabelFlags
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintPadding
 * @attr ref R.styleable#KeyboardView_keyTextShadowRadius
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#Keyboard_Key_keyTypeface
 * @attr ref R.styleable#Keyboard_Key_keyLetterSize
 * @attr ref R.styleable#Keyboard_Key_keyLabelSize
 * @attr ref R.styleable#Keyboard_Key_keyLargeLetterRatio
 * @attr ref R.styleable#Keyboard_Key_keyLargeLabelRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLetterRatio
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelRatio
 * @attr ref R.styleable#Keyboard_Key_keyLabelOffCenterRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelOffCenterRatio
 * @attr ref R.styleable#Keyboard_Key_keyPreviewTextRatio
 * @attr ref R.styleable#Keyboard_Key_keyTextColor
 * @attr ref R.styleable#Keyboard_Key_keyTextColorDisabled
 * @attr ref R.styleable#Keyboard_Key_keyTextShadowColor
 * @attr ref R.styleable#Keyboard_Key_keyHintLetterColor
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelColor
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintInactivatedColor
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintActivatedColor
 * @attr ref R.styleable#Keyboard_Key_keyPreviewTextColor
 */
open class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int = R.attr.keyboardViewStyle
) :
    View(context, attrs, defStyle) {
    // XML attributes
    private val mKeyVisualAttributes: KeyVisualAttributes?

    // Default keyLabelFlags from {@link KeyboardTheme}.
    // Currently only "alignHintLabelToBottom" is supported.
    private val mDefaultKeyLabelFlags: Int
    private val mKeyHintLetterPadding: Float
    private val mKeyShiftedLetterHintPadding: Float
    private val mKeyTextShadowRadius: Float
    protected val verticalCorrection: Float
    private val mKeyBackground: Drawable?
    private val mFunctionalKeyBackground: Drawable
    private val mSpacebarBackground: Drawable
    private val mSpacebarIconWidthRatio: Float
    private val mKeyBackgroundPadding: Rect = Rect()
    var mCustomColor: Int = 0
    private var mSystemBarHeight: Int = 0

    // Main keyboard
    // TODO: Consider having a dummy keyboard object to make this @NonNull
    private var mKeyboard: Keyboard? = null
    protected val keyDrawParams: KeyDrawParams = KeyDrawParams()

    // Drawing
    /** True if all keys should be drawn  */
    private var mInvalidateAllKeys: Boolean = false

    /** The keys that should be drawn  */
    private val mInvalidatedKeys: HashSet<Key> = HashSet()

    /** The working rectangle for clipping  */
    private val mClipRect: Rect = Rect()

    /** The keyboard bitmap buffer for faster updates  */
    private var mOffscreenBuffer: Bitmap? = null

    /** The canvas for the above mutable keyboard bitmap  */
    private val mOffscreenCanvas: Canvas = Canvas()
    private val mPaint: Paint = Paint()
    private val mFontMetrics: Paint.FontMetrics = Paint.FontMetrics()

    init {
        val keyboardViewAttr: TypedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.KeyboardView, defStyle, R.style.KeyboardView
        )
        mKeyBackground = keyboardViewAttr.getDrawable(R.styleable.KeyboardView_keyBackground)
        mKeyBackground!!.getPadding(mKeyBackgroundPadding)
        val functionalKeyBackground: Drawable? = keyboardViewAttr.getDrawable(
            R.styleable.KeyboardView_functionalKeyBackground
        )
        mFunctionalKeyBackground = if ((functionalKeyBackground != null))
            functionalKeyBackground
        else
            mKeyBackground
        val spacebarBackground: Drawable? = keyboardViewAttr.getDrawable(
            R.styleable.KeyboardView_spacebarBackground
        )
        mSpacebarBackground =
            if ((spacebarBackground != null)) spacebarBackground else mKeyBackground
        mSpacebarIconWidthRatio = keyboardViewAttr.getFloat(
            R.styleable.KeyboardView_spacebarIconWidthRatio, 1.0f
        )
        mKeyHintLetterPadding = keyboardViewAttr.getDimension(
            R.styleable.KeyboardView_keyHintLetterPadding, 0.0f
        )
        mKeyShiftedLetterHintPadding = keyboardViewAttr.getDimension(
            R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0.0f
        )
        mKeyTextShadowRadius = keyboardViewAttr.getFloat(
            R.styleable.KeyboardView_keyTextShadowRadius, KET_TEXT_SHADOW_RADIUS_DISABLED
        )
        verticalCorrection = keyboardViewAttr.getDimension(
            R.styleable.KeyboardView_verticalCorrection, 0.0f
        )
        keyboardViewAttr.recycle()

        val keyAttr: TypedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView
        )
        mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0)
        mKeyVisualAttributes = KeyVisualAttributes.Companion.newInstance(keyAttr)
        keyAttr.recycle()

        mPaint.setAntiAlias(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener { v: View?, windowInsets: WindowInsets ->
                val insets: Insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
                mSystemBarHeight = insets.bottom
                WindowInsets.CONSUMED
            })
        }
    }

    open var keyboard: Keyboard?
        /**
         * Returns the current keyboard being displayed by this view.
         * @return the currently attached keyboard
         * @see .setKeyboard
         */
        get() {
            return mKeyboard
        }
        /**
         * Attaches a keyboard to this view. The keyboard can be switched at any time and the
         * view will re-layout itself to accommodate the keyboard.
         * @see Keyboard
         *
         * @see .getKeyboard
         * @param keyboard the keyboard to display in this view
         */
        set(keyboard) {
            mKeyboard = keyboard
            val keyHeight: Int = keyboard!!.mMostCommonKeyHeight
            keyDrawParams.updateParams(keyHeight, mKeyVisualAttributes)
            keyDrawParams.updateParams(keyHeight, keyboard.mKeyVisualAttributes)
            val prefs: SharedPreferences? =
                PreferenceManagerCompat.getDeviceSharedPreferences(getContext())
            mCustomColor =
                Settings.Companion.readKeyboardColor(
                    prefs!!,
                    getContext()
                )
            invalidateAllKeys()
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard: Keyboard? = keyboard
        if (keyboard == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width: Int = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight()
        val height: Int =
            keyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom() + mSystemBarHeight
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (canvas.isHardwareAccelerated()) {
            onDrawKeyboard(canvas)
            return
        }

        val bufferNeedsUpdates: Boolean = mInvalidateAllKeys || !mInvalidatedKeys.isEmpty()
        if (bufferNeedsUpdates || mOffscreenBuffer == null) {
            if (maybeAllocateOffscreenBuffer()) {
                mInvalidateAllKeys = true
                // TODO: Stop using the offscreen canvas even when in software rendering
                mOffscreenCanvas.setBitmap(mOffscreenBuffer)
            }
            onDrawKeyboard(mOffscreenCanvas)
        }
        canvas.drawBitmap(mOffscreenBuffer!!, 0.0f, 0.0f, null)
    }

    private fun maybeAllocateOffscreenBuffer(): Boolean {
        val width: Int = getWidth()
        val height: Int = getHeight()
        if (width == 0 || height == 0) {
            return false
        }
        if (mOffscreenBuffer != null && mOffscreenBuffer!!.getWidth() == width && mOffscreenBuffer!!.getHeight() == height) {
            return false
        }
        freeOffscreenBuffer()
        mOffscreenBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return true
    }

    private fun freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null)
        mOffscreenCanvas.setMatrix(null)
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer!!.recycle()
            mOffscreenBuffer = null
        }
    }

    private fun onDrawKeyboard(canvas: Canvas) {
        val keyboard: Keyboard? = keyboard
        if (keyboard == null) {
            return
        }

        val paint: Paint = mPaint
        val background: Drawable? = getBackground()
        if (Color.alpha(mCustomColor) > 0 && keyboard.getKey(Constants.CODE_SPACE) != null) {
            setBackgroundColor(mCustomColor)
        }
        // Calculate clip region and set.
        val drawAllKeys: Boolean = mInvalidateAllKeys || mInvalidatedKeys.isEmpty()
        val isHardwareAccelerated: Boolean = canvas.isHardwareAccelerated()
        // TODO: Confirm if it's really required to draw all keys when hardware acceleration is on.
        if (drawAllKeys || isHardwareAccelerated) {
            if (!isHardwareAccelerated && background != null) {
                // Need to draw keyboard background on {@link #mOffscreenBuffer}.
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                background.draw(canvas)
            }
            // Draw all keys.
            for (key: Key in keyboard.getSortedKeys()) {
                onDrawKey(key, canvas, paint)
            }
        } else {
            for (key: Key in mInvalidatedKeys) {
                if (!keyboard.hasKey(key)) {
                    continue
                }
                if (background != null) {
                    // Need to redraw key's background on {@link #mOffscreenBuffer}.
                    val x: Int = key.getX() + getPaddingLeft()
                    val y: Int = key.getY() + getPaddingTop()
                    mClipRect.set(x, y, x + key.getWidth(), y + key.getHeight())
                    canvas.save()
                    canvas.clipRect(mClipRect)
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                    background.draw(canvas)
                    canvas.restore()
                }
                onDrawKey(key, canvas, paint)
            }
        }

        mInvalidatedKeys.clear()
        mInvalidateAllKeys = false
    }

    private fun onDrawKey(
        key: Key, canvas: Canvas,
        paint: Paint
    ) {
        val keyDrawX: Int = key.getX() + getPaddingLeft()
        val keyDrawY: Int = key.getY() + getPaddingTop()
        canvas.translate(keyDrawX.toFloat(), keyDrawY.toFloat())

        val attr: KeyVisualAttributes? = key.getVisualAttributes()
        val params: KeyDrawParams = keyDrawParams.mayCloneAndUpdateParams(key.getHeight(), attr)
        params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE

        if (!key.isSpacer()) {
            val background: Drawable = key.selectBackgroundDrawable(
                mKeyBackground!!, mFunctionalKeyBackground, mSpacebarBackground
            )
            if (background != null) {
                onDrawKeyBackground(key, canvas, background)
            }
        }
        onDrawKeyTopVisuals(key, canvas, paint, params)

        canvas.translate(-keyDrawX.toFloat(), -keyDrawY.toFloat())
    }

    // Draw key background.
    protected fun onDrawKeyBackground(
        key: Key, canvas: Canvas,
        background: Drawable
    ) {
        val keyWidth: Int = key.getWidth()
        val keyHeight: Int = key.getHeight()
        val padding: Rect = mKeyBackgroundPadding
        val bgWidth: Int = keyWidth + padding.left + padding.right
        val bgHeight: Int = keyHeight + padding.top + padding.bottom
        val bgX: Int = -padding.left
        val bgY: Int = -padding.top
        val bounds: Rect = background.getBounds()
        if (bgWidth != bounds.right || bgHeight != bounds.bottom) {
            background.setBounds(0, 0, bgWidth, bgHeight)
        }
        canvas.translate(bgX.toFloat(), bgY.toFloat())
        background.draw(canvas)
        canvas.translate(-bgX.toFloat(), -bgY.toFloat())
    }

    // Draw key top visuals.
    protected open fun onDrawKeyTopVisuals(
        key: Key, canvas: Canvas,
        paint: Paint, params: KeyDrawParams
    ) {
        val keyWidth: Int = key.getWidth()
        val keyHeight: Int = key.getHeight()
        val centerX: Float = keyWidth * 0.5f
        val centerY: Float = keyHeight * 0.5f

        // Draw key label.
        val keyboard: Keyboard? = keyboard
        val icon: Drawable? = if ((keyboard == null))
            null
        else
            key.getIcon(keyboard.mIconsSet, params.mAnimAlpha)
        var labelX: Float = centerX
        var labelBaseline: Float = centerY
        val label: String? = key.getLabel()
        if (label != null) {
            paint.setTypeface(key.selectTypeface(params))
            paint.setTextSize(key.selectTextSize(params).toFloat())
            val labelCharHeight: Float = TypefaceUtils.getReferenceCharHeight(paint)
            val labelCharWidth: Float = TypefaceUtils.getReferenceCharWidth(paint)

            // Vertical label text alignment.
            labelBaseline = centerY + labelCharHeight / 2.0f

            // Horizontal label text alignment
            if (key.isAlignLabelOffCenter()) {
                // The label is placed off center of the key. Used mainly on "phone number" layout.
                labelX = centerX + params.mLabelOffCenterRatio * labelCharWidth
                paint.setTextAlign(Align.LEFT)
            } else {
                labelX = centerX
                paint.setTextAlign(Align.CENTER)
            }
            if (key.needsAutoXScale()) {
                val ratio: Float = min(
                    1.0,
                    ((keyWidth * MAX_LABEL_RATIO) /
                            TypefaceUtils.getStringWidth(label, paint)).toDouble()
                ).toFloat()
                if (key.needsAutoScale()) {
                    val autoSize: Float = paint.getTextSize() * ratio
                    paint.setTextSize(autoSize)
                } else {
                    paint.setTextScaleX(ratio)
                }
            }

            paint.setColor(key.selectTextColor(params))
            // Set a drop shadow for the text if the shadow radius is positive value.
            if (mKeyTextShadowRadius > 0.0f) {
                paint.setShadowLayer(mKeyTextShadowRadius, 0.0f, 0.0f, params.mTextShadowColor)
            } else {
                paint.clearShadowLayer()
            }

            blendAlpha(paint, params.mAnimAlpha)
            canvas.drawText(label, 0, label.length, labelX, labelBaseline, paint)
            // Turn off drop shadow and reset x-scale.
            paint.clearShadowLayer()
            paint.setTextScaleX(1.0f)
        }

        // Draw hint label.
        val hintLabel: String? = key.getHintLabel()
        if (hintLabel != null) {
            paint.setTextSize(key.selectHintTextSize(params).toFloat())
            paint.setColor(key.selectHintTextColor(params))
            // TODO: Should add a way to specify type face for hint letters
            paint.setTypeface(Typeface.DEFAULT_BOLD)
            blendAlpha(paint, params.mAnimAlpha)
            val labelCharHeight: Float = TypefaceUtils.getReferenceCharHeight(paint)
            val labelCharWidth: Float = TypefaceUtils.getReferenceCharWidth(paint)
            val hintX: Float
            val hintBaseline: Float
            if (key.hasHintLabel()) {
                // The hint label is placed just right of the key label. Used mainly on
                // "phone number" layout.
                hintX = labelX + params.mHintLabelOffCenterRatio * labelCharWidth
                if (key.isAlignHintLabelToBottom(mDefaultKeyLabelFlags)) {
                    hintBaseline = labelBaseline
                } else {
                    hintBaseline = centerY + labelCharHeight / 2.0f
                }
                paint.setTextAlign(Align.LEFT)
            } else if (key.hasShiftedLetterHint()) {
                // The hint label is placed at top-right corner of the key. Used mainly on tablet.
                hintX = keyWidth - mKeyShiftedLetterHintPadding - labelCharWidth / 2.0f
                paint.getFontMetrics(mFontMetrics)
                hintBaseline = -mFontMetrics.top
                paint.setTextAlign(Align.CENTER)
            } else { // key.hasHintLetter()
                // The hint letter is placed at top-right corner of the key. Used mainly on phone.
                val hintDigitWidth: Float = TypefaceUtils.getReferenceDigitWidth(paint)
                val hintLabelWidth: Float = TypefaceUtils.getStringWidth(hintLabel, paint)
                hintX = ((keyWidth - mKeyHintLetterPadding
                        - max(
                    hintDigitWidth.toDouble(),
                    hintLabelWidth.toDouble()
                ) / 2.0f).toFloat())
                hintBaseline = -paint.ascent()
                paint.setTextAlign(Align.CENTER)
            }
            val adjustmentY: Float = params.mHintLabelVerticalAdjustment * labelCharHeight
            canvas.drawText(
                hintLabel, 0, hintLabel.length, hintX, hintBaseline + adjustmentY, paint
            )
        }

        // Draw key icon.
        if (label == null && icon != null) {
            val iconWidth: Int
            if (key.getCode() == Constants.CODE_SPACE && icon is NinePatchDrawable) {
                iconWidth = (keyWidth * mSpacebarIconWidthRatio).toInt()
            } else {
                iconWidth =
                    min(icon.getIntrinsicWidth().toDouble(), keyWidth.toDouble()).toInt()
            }
            val iconHeight: Int = icon.getIntrinsicHeight()
            val iconY: Int
            if (key.isAlignIconToBottom()) {
                iconY = keyHeight - iconHeight
            } else {
                iconY = (keyHeight - iconHeight) / 2 // Align vertically center.
            }
            val iconX: Int = (keyWidth - iconWidth) / 2 // Align horizontally center.
            drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight)
        }
    }

    fun newLabelPaint(key: Key?): Paint {
        val paint: Paint = Paint()
        paint.setAntiAlias(true)
        if (key == null) {
            paint.setTypeface(keyDrawParams.mTypeface)
            paint.setTextSize(keyDrawParams.mLabelSize.toFloat())
        } else {
            paint.setColor(key.selectTextColor(keyDrawParams))
            paint.setTypeface(key.selectTypeface(keyDrawParams))
            paint.setTextSize(key.selectTextSize(keyDrawParams).toFloat())
        }
        return paint
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [.invalidate] is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
     * draws the cached buffer.
     * @see .invalidateKey
     */
    fun invalidateAllKeys() {
        mInvalidatedKeys.clear()
        mInvalidateAllKeys = true
        invalidate()
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param key key in the attached [Keyboard].
     * @see .invalidateAllKeys
     */
    fun invalidateKey(key: Key?) {
        if (mInvalidateAllKeys || key == null) {
            return
        }
        mInvalidatedKeys.add(key)
        val x: Int = key.getX() + getPaddingLeft()
        val y: Int = key.getY() + getPaddingTop()
        invalidate(x, y, x + key.getWidth(), y + key.getHeight())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        freeOffscreenBuffer()
    }

    fun deallocateMemory() {
        freeOffscreenBuffer()
    }

    companion object {
        private val KET_TEXT_SHADOW_RADIUS_DISABLED: Float = -1.0f

        // The maximum key label width in the proportion to the key width.
        private const val MAX_LABEL_RATIO: Float = 0.90f

        private fun blendAlpha(paint: Paint, alpha: Int) {
            val color: Int = paint.getColor()
            paint.setARGB(
                (paint.getAlpha() * alpha) / Constants.Color.ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color)
            )
        }

        protected fun drawIcon(
            canvas: Canvas, icon: Drawable,
            x: Int, y: Int, width: Int, height: Int
        ) {
            canvas.translate(x.toFloat(), y.toFloat())
            icon.setBounds(0, 0, width, height)
            icon.draw(canvas)
            canvas.translate(-x.toFloat(), -y.toFloat())
        }
    }
}
