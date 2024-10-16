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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceActivity
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.utils.FragmentUtils

class SettingsActivity : PreferenceActivity() {
    override fun onStart() {
        super.onStart()

        var enabled = false
        try {
            enabled = isInputMethodOfThisImeEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Exception in check if input method is enabled", e)
        }

        if (!enabled) {
            val context: Context = this
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.setup_message)
            builder.setPositiveButton(
                android.R.string.ok
            ) { dialog, id ->
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                dialog.dismiss()
            }
            builder.setNegativeButton(
                android.R.string.cancel
            ) { dialog, id -> finish() }
            builder.setCancelable(false)

            builder.create().show()
        }
    }

    private val isInputMethodOfThisImeEnabled: Boolean
        /**
         * Check if this IME is enabled in the system.
         * @return whether this IME is enabled in the system.
         */
        get() {
            val imm =
                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val imePackageName = packageName
            for (imi in imm.enabledInputMethodList) {
                if (imi.packageName == imePackageName) {
                    return true
                }
            }
            return false
        }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        val actionBar = actionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            super.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getIntent(): Intent {
        val intent = super.getIntent()
        val fragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)
        if (fragment == null) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT)
        }
        intent.putExtra(EXTRA_NO_HEADERS, true)
        return intent
    }

    public override fun isValidFragment(fragmentName: String): Boolean {
        return FragmentUtils.isValidFragment(fragmentName)
    }

    companion object {
        private val DEFAULT_FRAGMENT: String = SettingsFragment::class.java.name
        private val TAG: String = SettingsActivity::class.java.simpleName
    }
}
