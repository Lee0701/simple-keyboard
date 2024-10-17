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
package rkr.simplekeyboard.inputmethod.latin

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils
import rkr.simplekeyboard.inputmethod.latin.settings.Settings
import rkr.simplekeyboard.inputmethod.latin.utils.DialogUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypePreferenceUtils
import java.util.Collections
import java.util.Locale
import java.util.TreeSet
import java.util.concurrent.Executors

/**
 * Enrichment class for InputMethodManager to simplify interaction and add functionality.
 */
// non final for easy mocking.
object RichInputMethodManager {
    private var mImmService: InputMethodManager? = null

    private var mSubtypeList: SubtypeList? = null

    private val isInitialized: Boolean
        get() {
            return mImmService != null
        }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw RuntimeException(TAG + " is used before initialization")
        }
    }

    fun init(context: Context) {
        if (isInitialized) {
            return
        }
        mImmService = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

        LocaleResourceUtils.init(context)

        // Initialize the virtual subtypes
        mSubtypeList = SubtypeList(context)
    }

    /**
     * Add a listener to be called when the virtual subtype changes.
     * @param listener the listener to call when the subtype changes.
     */
    fun setSubtypeChangeHandler(listener: SubtypeChangedListener?) {
        mSubtypeList!!.setSubtypeChangeHandler(listener)
    }

    /**
     * Interface used to allow some code to run when the virtual subtype changes.
     */
    interface SubtypeChangedListener {
        fun onCurrentSubtypeChanged()
    }

    /**
     * Manager for the list of enabled subtypes that also handles which one is currently in use.
     * Only one of these should be created to avoid conflicts.
     */
    private class SubtypeList(context: Context) {
        /** The list of enabled subtypes ordered by how they should be cycled through when moving to
         * the next subtype. When a subtype is actually in use, it should be moved to the beginning
         * of the list so that the next time the user uses the switch to next subtype button, all
         * of the subtypes can be iterated through before potentially switching to a different
         * input method.  */
        private var mSubtypes: MutableList<Subtype>? = null

        /** The index of the currently selected subtype. This is used for tracking the status of
         * cycling through subtypes. When actually using the keyboard, the subtype should be moved
         * to the beginning of the list, so this should normally be 0.  */
        private var mCurrentSubtypeIndex: Int

        private val mPrefs: SharedPreferences?
        private var mSubtypeChangedListener: SubtypeChangedListener? = null

        /**
         * Create the manager for the virtual subtypes.
         * @param context the context for this application.
         */
        init {
            mPrefs = PreferenceManagerCompat.getDeviceSharedPreferences(context)

            val prefSubtypes: String = Settings.readPrefSubtypes(mPrefs)
            val subtypes: MutableList<Subtype> = SubtypePreferenceUtils.createSubtypesFromPref(
                prefSubtypes, context.getResources()
            )
            if (subtypes == null || subtypes.size < 1) {
                mSubtypes = SubtypeLocaleUtils.getDefaultSubtypes(context.getResources())
            } else {
                mSubtypes = subtypes
            }
            mCurrentSubtypeIndex = 0
        }

        /**
         * Add a listener to be called when the virtual subtype changes.
         * @param listener the listener to call when the subtype changes.
         */
        fun setSubtypeChangeHandler(listener: SubtypeChangedListener?) {
            mSubtypeChangedListener = listener
        }

        /**
         * Call the subtype changed handler to indicate that the virtual subtype has changed.
         */
        fun notifySubtypeChanged() {
            if (mSubtypeChangedListener != null) {
                mSubtypeChangedListener!!.onCurrentSubtypeChanged()
            }
        }

        @get:Synchronized
        val allLocales: Set<Locale?>
            /**
             * Get all of the enabled languages.
             * @return the enabled languages.
             */
            get() {
                val locales: MutableSet<Locale?> =
                    HashSet()
                for (subtype: Subtype in mSubtypes!!) {
                    locales.add(subtype.localeObject)
                }
                return locales
            }

        /**
         * Get all of the enabled subtypes for language.
         * @param locale filter by Locale.
         * @return the enabled subtypes.
         */
        @Synchronized
        fun getAllForLocale(locale: String?): Set<Subtype> {
            val subtypes: MutableSet<Subtype> = HashSet()
            for (subtype: Subtype in mSubtypes!!) {
                if (subtype.locale == locale) subtypes.add(subtype)
            }
            return subtypes
        }

        /**
         * Get all of the enabled subtypes.
         * @param sortForDisplay whether the subtypes should be sorted alphabetically by the display
         * name as opposed to having no particular order.
         * @return the enabled subtypes.
         */
        @Synchronized
        fun getAll(sortForDisplay: Boolean): Set<Subtype> {
            val subtypes: MutableSet<Subtype>
            if (sortForDisplay) {
                subtypes = TreeSet(object : Comparator<Subtype> {
                    override fun compare(a: Subtype, b: Subtype): Int {
                        if (a == b) {
                            // ensure that this is consistent with equals
                            return 0
                        }
                        val result: Int = a.name.compareTo(b.name, ignoreCase = true)
                        if (result != 0) {
                            return result
                        }
                        // ensure that non-equal objects are distinguished to be consistent with
                        // equals
                        return if (a.hashCode() > b.hashCode()) 1 else -1
                    }
                })
            } else {
                subtypes = HashSet()
            }
            subtypes.addAll(mSubtypes!!)
            return subtypes
        }

        /**
         * Get the number of enabled subtypes.
         * @return the number of enabled subtypes.
         */
        @Synchronized
        fun size(): Int {
            return mSubtypes!!.size
        }

        /**
         * Update the preference for the list of enabled subtypes.
         */
        fun saveSubtypeListPref() {
            val prefSubtypes: String = SubtypePreferenceUtils.createPrefSubtypes(mSubtypes)
            Settings.writePrefSubtypes(
                mPrefs!!, prefSubtypes
            )
        }

        /**
         * Add a subtype to the list.
         * @param subtype the subtype to add.
         * @return whether the subtype was added to the list (or already existed in the list).
         */
        @Synchronized
        fun addSubtype(subtype: Subtype): Boolean {
            val mSubtypes = mSubtypes!!
            if (mSubtypes.contains(subtype)) {
                // don't allow duplicates, but since it's already in the list this can be considered
                // successful
                return true
            }
            if (!mSubtypes.add(subtype)) {
                return false
            }
            saveSubtypeListPref()
            return true
        }

        /**
         * Remove a subtype from the list.
         * @param subtype the subtype to remove.
         * @return whether the subtype was removed (or wasn't even in the list).
         */
        @Synchronized
        fun removeSubtype(subtype: Subtype): Boolean {
            val mSubtypes = mSubtypes!!
            if (mSubtypes.size == 1) {
                // there needs to be at least one subtype
                return false
            }

            val index: Int = mSubtypes.indexOf(subtype)
            if (index < 0) {
                // nothing to remove
                return true
            }

            val subtypeChanged: Boolean
            if (mCurrentSubtypeIndex == index) {
                mCurrentSubtypeIndex = 0
                subtypeChanged = true
            } else {
                if (mCurrentSubtypeIndex > index) {
                    // make sure the current subtype is still pointed to when the other subtype is
                    // removed
                    mCurrentSubtypeIndex--
                }
                subtypeChanged = false
            }

            mSubtypes.removeAt(index)
            saveSubtypeListPref()
            if (subtypeChanged) {
                notifySubtypeChanged()
            }
            return true
        }

        /**
         * Move the current subtype to the beginning of the list to allow the rest of the subtypes
         * to be cycled through before possibly switching to a separate input method. This should be
         * called whenever the user is done cycling through subtypes (eg: when a subtype is actually
         * used or the keyboard is closed).
         */
        @Synchronized
        fun resetSubtypeCycleOrder() {
            if (mCurrentSubtypeIndex == 0) {
                return
            }

            // move the current subtype to the top of the list and shift everything above it down
            Collections.rotate(mSubtypes!!.subList(0, mCurrentSubtypeIndex + 1), 1)
            mCurrentSubtypeIndex = 0
            saveSubtypeListPref()
        }

        /**
         * Set the current subtype to a specific subtype.
         * @param subtype the subtype to set as current.
         * @return whether the current subtype was set to the requested subtype.
         */
        @Synchronized
        fun setCurrentSubtype(subtype: Subtype?): Boolean {
            if (currentSubtype == subtype) {
                // nothing to do
                return true
            }
            val mSubtypes = mSubtypes!!
            for (i in mSubtypes.indices) {
                if (mSubtypes.get(i) == subtype) {
                    setCurrentSubtype(i)
                    return true
                }
            }
            return false
        }

        /**
         * Set the current subtype to match a specified locale.
         * @param locale the locale to use.
         * @return whether the current subtype was set to the requested locale.
         */
        @Synchronized
        fun setCurrentSubtype(locale: Locale): Boolean {
            val mSubtypes = mSubtypes!!
            val enabledLocales: ArrayList<Locale?> = ArrayList(
                mSubtypes.size
            )
            for (subtype: Subtype in mSubtypes) {
                enabledLocales.add(subtype.localeObject)
            }
            val bestLocale: Locale? = LocaleUtils.findBestLocale(locale, enabledLocales)
            if (bestLocale != null) {
                // get the first subtype (most recently used) with a matching locale
                for (i in mSubtypes.indices) {
                    val subtype: Subtype = mSubtypes.get(i)
                    if (bestLocale == subtype.localeObject) {
                        setCurrentSubtype(i)
                        return true
                    }
                }
            }
            return false
        }

        /**
         * Set the current subtype to a specified index. This should only be used when setting the
         * subtype to something specific (not when just iterating through the subtypes).
         * @param index the index of the subtype to set as current.
         */
        fun setCurrentSubtype(index: Int) {
            if (mCurrentSubtypeIndex == index) {
                // nothing to do
                return
            }
            mCurrentSubtypeIndex = index
            if (index != 0) {
                // since the subtype was selected directly, the cycle should be reset so switching
                // to the next subtype can iterate through all of the rest of the subtypes
                resetSubtypeCycleOrder()
            }
            notifySubtypeChanged()
        }

        /**
         * Switch to the next subtype in the list.
         * @param notifyChangeOnCycle whether the subtype changed handler should be notified if the
         * end of the list is passed and the next subtype would go back to
         * the first in the list.
         * @return whether the subtype changed listener was called.
         */
        @Synchronized
        fun switchToNextSubtype(notifyChangeOnCycle: Boolean): Boolean {
            val nextIndex: Int = mCurrentSubtypeIndex + 1
            if (nextIndex >= mSubtypes!!.size) {
                mCurrentSubtypeIndex = 0
                if (!notifyChangeOnCycle) {
                    return false
                }
            } else {
                mCurrentSubtypeIndex = nextIndex
            }
            notifySubtypeChanged()
            return true
        }

        @get:Synchronized
        val currentSubtype: Subtype
            /**
             * Get the subtype that is currently in use (or will be once the keyboard is opened).
             * @return the current subtype.
             */
            get() {
                return mSubtypes!!.get(mCurrentSubtypeIndex)
            }
    }

    /**
     * Get all of the enabled subtypes.
     * @param sortForDisplay whether the subtypes should be sorted alphabetically by the display
     * name as opposed to having no particular order.
     * @return the enabled subtypes.
     */
    fun getEnabledSubtypes(sortForDisplay: Boolean): Set<Subtype> {
        return mSubtypeList!!.getAll(sortForDisplay)
    }

    val enabledLocales: Set<Locale?>
        /**
         * Get all of the enabled languages.
         * @return the enabled languages.
         */
        get() {
            return mSubtypeList!!.allLocales
        }

    /**
     * Get all of the enabled subtypes for language.
     * @param locale filter by Locale.
     * @return the enabled subtypes.
     */
    fun getEnabledSubtypesForLocale(locale: String?): Set<Subtype> {
        return mSubtypeList!!.getAllForLocale(locale)
    }

    /**
     * Check if there are multiple enabled subtypes.
     * @return whether there are multiple subtypes.
     */
    fun hasMultipleEnabledSubtypes(): Boolean {
        return mSubtypeList!!.size() > 1
    }

    /**
     * Enable a new subtype.
     * @param subtype the subtype to add.
     * @return whether the subtype was added.
     */
    fun addSubtype(subtype: Subtype?): Boolean {
        return mSubtypeList!!.addSubtype(subtype!!)
    }

    /**
     * Disable a subtype.
     * @param subtype the subtype to remove.
     * @return whether the subtype was removed.
     */
    fun removeSubtype(subtype: Subtype?): Boolean {
        return mSubtypeList!!.removeSubtype(subtype!!)
    }

    /**
     * Move the current subtype to the beginning of the list to allow the rest of the subtypes
     * to be cycled through before possibly switching to a separate input method.
     */
    fun resetSubtypeCycleOrder() {
        mSubtypeList!!.resetSubtypeCycleOrder()
    }

    /**
     * Set the current subtype to a specific subtype.
     * @param subtype the subtype to set as current.
     * @return whether the current subtype was set to the requested subtype.
     */
    fun setCurrentSubtype(subtype: Subtype?): Boolean {
        return mSubtypeList!!.setCurrentSubtype(subtype)
    }

    /**
     * Set the current subtype to match a specified locale.
     * @param locale the locale to use.
     * @return whether the current subtype was set to the requested locale.
     */
    fun setCurrentSubtype(locale: Locale): Boolean {
        return mSubtypeList!!.setCurrentSubtype(locale)
    }

    /**
     * Switch to the next subtype of this IME or optionally to another IME if all of the subtypes of
     * this IME have already been iterated through.
     * @param token supplies the identifying token given to an input method when it was started,
     * which allows it to perform this operation on itself.
     * @param onlyCurrentIme whether to only switch virtual subtypes or also switch to other input
     * methods.
     * @return whether the switch was successful.
     */
    fun switchToNextInputMethod(token: IBinder?, onlyCurrentIme: Boolean): Boolean {
        if (onlyCurrentIme) {
            if (!hasMultipleEnabledSubtypes()) {
                return false
            }
            return mSubtypeList!!.switchToNextSubtype(true)
        }
        if (mSubtypeList!!.switchToNextSubtype(false)) {
            return true
        }
        // switch to a different IME
        if (mImmService!!.switchToNextInputMethod(token, false)) {
            return true
        }
        if (hasMultipleEnabledSubtypes()) {
            // the virtual subtype should have been reset to the first item to prepare for switching
            // back to this IME, but we skipped notifying the change because we expected to switch
            // to a different IME, but since that failed, we just need to notify the listener
            mSubtypeList!!.notifySubtypeChanged()
            return true
        }
        return false
    }

    val currentSubtype: Subtype
        /**
         * Get the subtype that is currently in use.
         * @return the current subtype.
         */
        get() {
            return mSubtypeList!!.currentSubtype
        }

    /**
     * Check if the IME should offer ways to switch to a next input method (eg: a globe key).
     * @param binder supplies the identifying token given to an input method when it was started,
     * which allows it to perform this operation on itself.
     * @return whether the IME should offer ways to switch to a next input method.
     */
    fun shouldOfferSwitchingToOtherInputMethods(binder: IBinder?): Boolean {
        // Use the default value instead on Jelly Bean MR2 and previous where
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} isn't yet available
        // and on KitKat where the API is still just a stub to return true always.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return false
        }
        return mImmService!!.shouldOfferSwitchingToNextInputMethod(binder)
    }

    /**
     * Show a popup to pick the current subtype.
     * @param context the context for this application.
     * @param windowToken identifier for the window.
     * @param inputMethodService the input method service for this IME.
     * @return the dialog that was created.
     */
    fun showSubtypePicker(
        context: Context, windowToken: IBinder?,
        inputMethodService: InputMethodService
    ): AlertDialog? {
        if (windowToken == null) {
            return null
        }
        val title: CharSequence = context.getString(R.string.change_keyboard)

        val subtypeInfoList: List<SubtypeInfo> = getEnabledSubtypeInfoOfAllImes(context)
        if (subtypeInfoList.size < 2) {
            // if there aren't multiple options, there is no reason to show the picker
            return null
        }

        val items: Array<CharSequence?> = arrayOfNulls(subtypeInfoList.size)
        val currentSubtype: Subtype = currentSubtype
        var currentSubtypeIndex: Int = 0
        var i: Int = 0
        for (subtypeInfo: SubtypeInfo in subtypeInfoList) {
            if (subtypeInfo.virtualSubtype != null
                && subtypeInfo.virtualSubtype == currentSubtype
            ) {
                currentSubtypeIndex = i
            }

            val itemTitle: SpannableString
            val itemSubtitle: SpannableString
            if (!TextUtils.isEmpty(subtypeInfo.subtypeName)) {
                itemTitle = SpannableString(subtypeInfo.subtypeName)
                itemSubtitle = SpannableString("\n" + subtypeInfo.imeName)
            } else {
                itemTitle = SpannableString(subtypeInfo.imeName)
                itemSubtitle = SpannableString("")
            }
            itemTitle.setSpan(
                RelativeSizeSpan(0.9f), 0, itemTitle.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            itemSubtitle.setSpan(
                RelativeSizeSpan(0.85f), 0, itemSubtitle.length,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE
            )

            items[i++] = SpannableStringBuilder().append(itemTitle).append(itemSubtitle)
        }
        val listener: DialogInterface.OnClickListener = object : DialogInterface.OnClickListener {
            override fun onClick(di: DialogInterface, position: Int) {
                di.dismiss()
                var i: Int = 0
                for (subtypeInfo: SubtypeInfo in subtypeInfoList) {
                    if (i == position) {
                        if (subtypeInfo.virtualSubtype != null) {
                            setCurrentSubtype(subtypeInfo.virtualSubtype)
                        } else {
                            switchToTargetIme(
                                subtypeInfo.imiId, subtypeInfo.systemSubtype,
                                inputMethodService
                            )
                        }
                        break
                    }
                    i++
                }
            }
        }
        val builder: AlertDialog.Builder = AlertDialog.Builder(
            DialogUtils.getPlatformDialogThemeContext(context)
        )
        builder.setSingleChoiceItems(items, currentSubtypeIndex, listener).setTitle(title)
        val dialog: AlertDialog = builder.create()
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val window: Window? = dialog.getWindow()
        val lp: WindowManager.LayoutParams = window!!.getAttributes()
        lp.token = windowToken
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.setAttributes(lp)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

        dialog.show()
        return dialog
    }

    /**
     * Get info for all of virtual subtypes of this IME and system subtypes of all other IMEs.
     * @param context the context for this application.
     * @return a list with info for all of the subtypes.
     */
    private fun getEnabledSubtypeInfoOfAllImes(context: Context): List<SubtypeInfo> {
        val subtypeInfoList: MutableList<SubtypeInfo> = ArrayList()
        val packageManager: PackageManager = context.getPackageManager()

        val imiList: MutableSet<InputMethodInfo> =
            TreeSet<InputMethodInfo>(object : Comparator<InputMethodInfo> {
                override fun compare(a: InputMethodInfo, b: InputMethodInfo): Int {
                    if (a == b) {
                        // ensure that this is consistent with equals
                        return 0
                    }
                    val labelA: String = a.loadLabel(packageManager).toString()
                    val labelB: String = b.loadLabel(packageManager).toString()
                    val result: Int = labelA.compareTo(labelB, ignoreCase = true)
                    if (result != 0) {
                        return result
                    }
                    // ensure that non-equal objects are distinguished to be consistent with
                    // equals
                    return if (a.hashCode() > b.hashCode()) 1 else -1
                }
            })
        imiList.addAll(mImmService!!.getEnabledInputMethodList())

        for (imi: InputMethodInfo in imiList) {
            val imeName: CharSequence = imi.loadLabel(packageManager)
            val imiId: String = imi.getId()
            val packageName: String = imi.getPackageName()

            if (packageName == context.getPackageName()) {
                for (subtype: Subtype in getEnabledSubtypes(true)) {
                    val subtypeInfo: SubtypeInfo = SubtypeInfo()
                    subtypeInfo.virtualSubtype = subtype
                    subtypeInfo.subtypeName = subtype.name
                    subtypeInfo.imeName = imeName
                    subtypeInfo.imiId = imiId
                    subtypeInfoList.add(subtypeInfo)
                }
                continue
            }

            val subtypes: List<InputMethodSubtype> =
                mImmService!!.getEnabledInputMethodSubtypeList(imi, true)
            // IMEs that have no subtypes should still be returned
            if (subtypes.isEmpty()) {
                val subtypeInfo: SubtypeInfo = SubtypeInfo()
                subtypeInfo.imeName = imeName
                subtypeInfo.imiId = imiId
                subtypeInfoList.add(subtypeInfo)
                continue
            }

            val applicationInfo: ApplicationInfo = imi.getServiceInfo().applicationInfo
            for (subtype: InputMethodSubtype in subtypes) {
                if (subtype.isAuxiliary()) {
                    continue
                }
                val subtypeInfo: SubtypeInfo = SubtypeInfo()
                subtypeInfo.systemSubtype = subtype
                if (!subtype.overridesImplicitlyEnabledSubtype()) {
                    subtypeInfo.subtypeName = subtype.getDisplayName(
                        context, packageName,
                        applicationInfo
                    )
                }
                subtypeInfo.imeName = imeName
                subtypeInfo.imiId = imiId
                subtypeInfoList.add(subtypeInfo)
            }
        }

        return subtypeInfoList
    }

    /**
     * Info for a virtual or system subtype.
     */
    private class SubtypeInfo {
        var systemSubtype: InputMethodSubtype? = null
        var virtualSubtype: Subtype? = null
        var subtypeName: CharSequence? = null
        var imeName: CharSequence? = null
        var imiId: String? = null
    }

    /**
     * Switch to a different input method.
     * @param imiId the ID for the input method to be switched to.
     * @param subtype the subtype for the input method to be switched to.
     * @param context the input method service for this IME.
     */
    private fun switchToTargetIme(
        imiId: String?, subtype: InputMethodSubtype?,
        context: InputMethodService
    ) {
        val token: IBinder? = context.getWindow().getWindow()!!.getAttributes().token
        if (token == null) {
            return
        }
        val imm: InputMethodManager? = mImmService
        Executors.newSingleThreadExecutor().execute(object : Runnable {
            override fun run() {
                imm!!.setInputMethodAndSubtype(token, imiId, subtype)
            }
        })
    }

    private val TAG: String = RichInputMethodManager::class.java.getSimpleName()
}
