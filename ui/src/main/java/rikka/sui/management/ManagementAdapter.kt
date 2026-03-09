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

import android.content.Context
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import rikka.recyclerview.BaseRecyclerViewAdapter
import rikka.recyclerview.ClassCreatorPool
import rikka.sui.model.AppInfo

class ManagementAdapter(
    context: Context,
) : BaseRecyclerViewAdapter<ClassCreatorPool>(),
    PopupTextProvider {

    private val adapterScope = MainScope()
    private var updateJob: Job? = null

    init {
        creatorPool.putRule(AppInfo::class.java, ManagementAppItemViewHolder.Creator())
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItemAt<Any>(position).hashCode().toLong()

    override fun onCreateCreatorPool(): ClassCreatorPool = ClassCreatorPool()

    fun updateData(data: List<AppInfo>) {
        updateJob?.cancel()

        val newData = java.util.ArrayList<Any>(data)

        updateJob = adapterScope.launch(Dispatchers.Default) {
            val oldData = withContext(Dispatchers.Main) {
                java.util.ArrayList(getItems<Any>())
            }

            val result = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldData.size

                override fun getNewListSize(): Int = newData.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldData[oldItemPosition]
                    val newItem = newData[newItemPosition]
                    if (oldItem is AppInfo && newItem is AppInfo) {
                        return oldItem.packageInfo.packageName == newItem.packageInfo.packageName &&
                            oldItem.packageInfo.applicationInfo?.uid == newItem.packageInfo.applicationInfo?.uid
                    }
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldData[oldItemPosition]
                    val newItem = newData[newItemPosition]
                    return oldItem == newItem
                }
            })

            withContext(Dispatchers.Main) {
                if (isActive) {
                    val itemsList = getItems<Any>()
                    itemsList.clear()
                    itemsList.addAll(newData)
                    result.dispatchUpdatesTo(this@ManagementAdapter)
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        updateJob?.cancel()
    }

    override fun getPopupText(
        view: View,
        position: Int,
    ): CharSequence = ""
}
