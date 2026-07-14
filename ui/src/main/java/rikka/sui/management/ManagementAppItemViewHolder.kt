/*
 * This file is part of Sui.
 *
 * Sui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sui.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2021-2026 Sui Contributors
 */
package rikka.sui.management

import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.get
import androidx.core.view.size
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import rikka.sui.R
import rikka.sui.databinding.ManagementAppItemBinding
import rikka.sui.ktx.resolveColor
import rikka.sui.model.AppInfo
import rikka.sui.server.SuiConfig
import rikka.sui.util.AppIconCache
import rikka.sui.util.BridgeServiceClient
import rikka.sui.util.MiuixPopupDimOverlay
import rikka.sui.util.MiuixPressHelper
import rikka.sui.util.MiuixSmoothCardDrawable
import rikka.sui.util.MiuixSquircleProvider
import rikka.sui.util.MonetSettings
import rikka.sui.util.UserHandleCompat
import rikka.sui.util.applyMiuixPopupStyle
import rikka.sui.util.colorCheckedItemsMiuixBlue

class ManagementAppItemViewHolder private constructor(
    private val binding: ManagementAppItemBinding,
) : RecyclerView.ViewHolder(binding.root) {

    companion object {
        private val SANS_SERIF_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private var lastMenuClickTime = 0L
        private var lastPopupDismissTime = 0L

        fun create(inflater: LayoutInflater, parent: ViewGroup): ManagementAppItemViewHolder = ManagementAppItemViewHolder(ManagementAppItemBinding.inflate(inflater, parent, false))
    }

    private val context get() = binding.root.context
    private lateinit var data: AppInfo
    private var adapter: ManagementAdapter? = null

    private inline val packageName get() = data.packageInfo.packageName
    private inline val ai get() = data.packageInfo.applicationInfo
    private inline val uid get() = ai?.uid ?: 0

    private var loadIconJob: Job? = null
    private var activePopupMenu: PopupMenu? = null

    private val icon get() = binding.icon
    private val name get() = binding.title
    private val pkg get() = binding.summary
    private val statusText get() = binding.button1

    private val textColorSecondary: ColorStateList
    private val textColorPrimary: ColorStateList
    private val iconSize: Int

    init {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        textColorSecondary = context.getColorStateList(typedValue.resourceId)

        context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        textColorPrimary = context.getColorStateList(typedValue.resourceId)

        var normalColor = context.getColor(R.color.miuix_card_normal)
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!isNight && MonetSettings.isMonetEnabled(context)) {
            val primaryColor = context.theme.resolveColor(androidx.appcompat.R.attr.colorPrimary)
            normalColor = ColorUtils.blendARGB(normalColor, primaryColor, 0.10f)
        }
        binding.root.background = MiuixSmoothCardDrawable.createSelectorWithOverlay(context, normalColor)
        binding.root.setOnClickListener { showPopupMenu() }
        binding.root.setOnTouchListener(MiuixPressHelper())

        iconSize = context.resources.getDimensionPixelSize(R.dimen.expected_app_icon_max_size)
        icon.outlineProvider = MiuixSquircleProvider(10.5f)
        icon.clipToOutline = true
    }

    fun bind(data: AppInfo, adapter: ManagementAdapter) {
        this.data = data
        this.adapter = adapter
        loadIconJob?.cancel()

        val userId = UserHandleCompat.getUserId(uid)
        val label = data.label
        name.text = if (userId == UserHandleCompat.myUserId()) label else "$label - ($userId)"
        pkg.text = packageName
        syncViewStateForFlags()

        val applicationInfo = ai ?: return
        loadIconJob = AppIconCache.loadIconBitmapAsync(context, applicationInfo, userId, icon, iconSize)
    }

    fun recycle() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    private fun showPopupMenu() {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastPopupDismissTime < 200 || currentTime - lastMenuClickTime < 300) return
        lastMenuClickTime = currentTime

        activePopupMenu?.let {
            it.dismiss()
            activePopupMenu = null
            return
        }

        val contextWrapper = ContextThemeWrapper(context, R.style.Theme_Sui_PopupMenu)
        val popupMenu = PopupMenu(contextWrapper, statusText, Gravity.END)
        activePopupMenu = popupMenu
        popupMenu.inflate(R.menu.app_item_options_menu)
        statusText.isActivated = true

        var activityContext = itemView.context
        while (activityContext is ContextWrapper) {
            if (activityContext is android.app.Activity) break
            activityContext = activityContext.baseContext
        }
        if (activityContext is android.app.Activity) {
            MiuixPopupDimOverlay.show(activityContext)
        }

        popupMenu.setOnDismissListener {
            statusText.isActivated = false
            MiuixPopupDimOverlay.hide()
            lastPopupDismissTime = SystemClock.elapsedRealtime()
            if (activePopupMenu === popupMenu) activePopupMenu = null
        }

        val explicitFlags = data.flags and SuiConfig.MASK_PERMISSION
        val currentSelection = when {
            explicitFlags and SuiConfig.FLAG_ALLOWED != 0 -> 0
            explicitFlags and SuiConfig.FLAG_ALLOWED_SHELL != 0 -> 1
            explicitFlags and SuiConfig.FLAG_DENIED != 0 -> 2
            explicitFlags and SuiConfig.FLAG_HIDDEN != 0 -> 3
            else -> 4
        }

        val menu = popupMenu.menu
        if (currentSelection < menu.size) {
            menu[currentSelection].isChecked = true
        }

        popupMenu.setOnMenuItemClickListener { item ->
            val newValue = when (item.itemId) {
                R.id.action_allow -> SuiConfig.FLAG_ALLOWED
                R.id.action_allow_shell -> SuiConfig.FLAG_ALLOWED_SHELL
                R.id.action_deny -> SuiConfig.FLAG_DENIED
                R.id.action_hidden -> SuiConfig.FLAG_HIDDEN
                R.id.action_default -> 0
                else -> return@setOnMenuItemClickListener false
            }

            try {
                BridgeServiceClient.getService().updateFlagsForUid(uid, SuiConfig.MASK_PERMISSION, newValue)
            } catch (e: Throwable) {
                Log.e("SuiSettings", "updateFlagsForUid", e)
            }

            data.flags = data.flags and SuiConfig.MASK_PERMISSION.inv() or newValue
            data.effectiveFlags = if (newValue != 0) newValue else data.defaultFlags and SuiConfig.MASK_PERMISSION
            syncViewStateForFlags()
            adapter?.notifyItemChanged(bindingAdapterPosition, Any())
            true
        }

        popupMenu.colorCheckedItemsMiuixBlue(itemView.context)
        popupMenu.applyMiuixPopupStyle()
    }

    private fun syncViewStateForFlags() {
        val explicitFlags = data.flags and SuiConfig.MASK_PERMISSION
        val effectiveFlags = data.effectiveFlags and SuiConfig.MASK_PERMISSION
        val allowed = effectiveFlags and SuiConfig.FLAG_ALLOWED != 0
        val allowedShell = effectiveFlags and SuiConfig.FLAG_ALLOWED_SHELL != 0
        if (allowed || allowedShell) {
            binding.title.setTextColor(textColorPrimary)
            binding.title.typeface = SANS_SERIF_MEDIUM
        } else {
            binding.title.setTextColor(textColorSecondary)
            binding.title.typeface = SANS_SERIF_MEDIUM
        }

        val explicitAllowed = explicitFlags and SuiConfig.FLAG_ALLOWED != 0
        val explicitAllowedShell = explicitFlags and SuiConfig.FLAG_ALLOWED_SHELL != 0
        val explicitDenied = explicitFlags and SuiConfig.FLAG_DENIED != 0
        val explicitHidden = explicitFlags and SuiConfig.FLAG_HIDDEN != 0

        val textRes = when {
            explicitAllowed -> R.string.permission_allowed
            explicitAllowedShell -> R.string.permission_allowed_shell
            explicitDenied -> R.string.permission_denied
            explicitHidden -> R.string.permission_hidden
            else -> R.string.permission_default
        }

        if (explicitAllowed || explicitAllowedShell) {
            statusText.setTextColor(context.theme.resolveColor(androidx.appcompat.R.attr.colorAccent))
        } else if (explicitDenied) {
            val isNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES != 0
            statusText.setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFFFF5252.toInt())
        } else if (explicitHidden) {
            statusText.setTextColor(context.theme.resolveColor(android.R.attr.colorForeground))
        } else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorTertiary, typedValue, true)
            statusText.setTextColor(context.getColorStateList(typedValue.resourceId))
        }

        statusText.setText(textRes)
    }
}
