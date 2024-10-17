/*
 * Copyright (C) 2013 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PorterDuff
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import rkr.simplekeyboard.inputmethod.R
import java.util.Locale

class ColorDialogPreference(context: Context?, attrs: AttributeSet?) :
    DialogPreference(context, attrs), OnSeekBarChangeListener {
    interface ValueProxy {
        fun readValue(key: String?): Int
        fun writeDefaultValue(key: String?)
        fun writeValue(value: Int, key: String?)
    }

    private var mValueView: TextView? = null
    private var mSeekBarRed: SeekBar? = null
    private var mSeekBarGreen: SeekBar? = null
    private var mSeekBarBlue: SeekBar? = null

    private var mValueProxy: ValueProxy? = null

    init {
        dialogLayoutResource = R.layout.color_dialog
    }

    fun setInterface(proxy: ValueProxy?) {
        mValueProxy = proxy
    }

    override fun onCreateDialogView(): View {
        val view = super.onCreateDialogView()
        val mSeekBarRed = view.findViewById<View>(R.id.seek_bar_dialog_bar_red) as SeekBar
        mSeekBarRed.max = 255
        mSeekBarRed.setOnSeekBarChangeListener(this)
        mSeekBarRed.progressDrawable.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        mSeekBarRed.thumb.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        val mSeekBarGreen = view.findViewById<View>(R.id.seek_bar_dialog_bar_green) as SeekBar
        mSeekBarGreen.max = 255
        mSeekBarGreen.setOnSeekBarChangeListener(this)
        mSeekBarGreen.thumb.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
        mSeekBarGreen.progressDrawable.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
        val mSeekBarBlue = view.findViewById<View>(R.id.seek_bar_dialog_bar_blue) as SeekBar
        mSeekBarBlue.max = 255
        mSeekBarBlue.setOnSeekBarChangeListener(this)
        mSeekBarBlue.thumb.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN)
        mSeekBarBlue.progressDrawable.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN)
        mValueView = view.findViewById<View>(R.id.seek_bar_dialog_value) as TextView
        this.mSeekBarRed = mSeekBarRed
        this.mSeekBarGreen = mSeekBarGreen
        this.mSeekBarBlue = mSeekBarBlue
        return view
    }

    @SuppressLint("MissingSuperCall")
    override fun onBindDialogView(view: View) {
        val color = mValueProxy?.readValue(key) ?: return
        mSeekBarRed?.progress = Color.red(color)
        mSeekBarGreen?.progress = Color.green(color)
        mSeekBarBlue?.progress = Color.blue(color)
        setHeaderText(color)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)
            .setNeutralButton(R.string.button_default, this)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        super.onClick(dialog, which)
        val key = key
        if (which == DialogInterface.BUTTON_POSITIVE) {
            super.onClick(dialog, which)
            val value = Color.rgb(
                mSeekBarRed!!.progress,
                mSeekBarGreen!!.progress,
                mSeekBarBlue!!.progress
            )
            mValueProxy?.writeValue(value, key)
            return
        }
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            super.onClick(dialog, which)
            mValueProxy?.writeDefaultValue(key)
            return
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        val color = Color.rgb(
            mSeekBarRed!!.progress,
            mSeekBarGreen!!.progress,
            mSeekBarBlue!!.progress
        )
        setHeaderText(color)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
    }

    private fun setHeaderText(color: Int) {
        mValueView?.text = getValueText(color)
        val bright = Color.red(color) + Color.green(color) + Color.blue(color) > 128 * 3
        mValueView?.setTextColor(if (bright) Color.BLACK else Color.WHITE)
        mValueView?.setBackgroundColor(color)
    }

    private fun getValueText(value: Int): String {
        var temp = Integer.toHexString(value)
        while (temp.length < 8) {
            temp = "0$temp"
        }
        return temp.substring(2).uppercase(Locale.getDefault())
    }
}
