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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.content.Context
import android.os.Bundle
import android.preference.PreferenceFragment
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat

/**
 * This is a helper class for an IME's settings preference fragment. It's recommended for every
 * IME to have its own settings preference fragment which inherits this class.
 */
abstract class InputMethodSettingsFragment : PreferenceFragment() {
    private val mSettings = InputMethodSettingsImpl()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = activity
        preferenceScreen = preferenceManager.createPreferenceScreen(
            PreferenceManagerCompat.getDeviceContext(context)
        )
        mSettings.init(context, preferenceScreen)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        mSettings.updateEnabledSubtypeList()
    }
}
