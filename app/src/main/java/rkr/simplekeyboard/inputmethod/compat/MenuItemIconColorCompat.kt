/*
 * Copyright (C) 2021 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.compat

import android.app.ActionBar
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.view.View
import android.widget.TextView

object MenuItemIconColorCompat {
    /**
     * Set a menu item's icon to matching text color.
     * @param menuItem the menu item that should change colors.
     * @param actionBar target ActionBar.
     */
    fun matchMenuIconColor(view: View, menuItem: MenuItem?, actionBar: ActionBar) {
        val views: ArrayList<View> = ArrayList()

        view.getRootView().findViewsWithText(views, actionBar.getTitle(), View.FIND_VIEWS_WITH_TEXT)
        if (views.size == 1 && views.get(0) is TextView) {
            val color: Int = (views.get(0) as TextView).getCurrentTextColor()
            setIconColor(menuItem, color)
        }
    }

    /**
     * Set a menu item's icon to specific color.
     * @param menuItem the menu item that should change colors.
     * @param color the color that the icon should be changed to.
     */
    private fun setIconColor(menuItem: MenuItem?, color: Int) {
        if (menuItem != null) {
            val drawable: Drawable? = menuItem.getIcon()
            if (drawable != null) {
                drawable.mutate()
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            }
        }
    }
}
