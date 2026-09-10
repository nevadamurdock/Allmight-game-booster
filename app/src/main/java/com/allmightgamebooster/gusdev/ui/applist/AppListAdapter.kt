package com.allmightgamebooster.gusdev.ui.applist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.allmightgamebooster.gusdev.R
import com.allmightgamebooster.gusdev.data.BoostConfigStore

class AppListAdapter(
    private val onClick: (AppListActivity.AppInfo) -> Unit
) : ListAdapter<AppListActivity.AppInfo, AppListAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppListActivity.AppInfo>() {
            override fun areItemsTheSame(a: AppListActivity.AppInfo, b: AppListActivity.AppInfo) =
                a.packageName == b.packageName
            override fun areContentsTheSame(a: AppListActivity.AppInfo, b: AppListActivity.AppInfo) =
                a == b
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val tvPkg: TextView = view.findViewById(R.id.tvPackageName)
        val tvPreset: TextView = view.findViewById(R.id.tvPreset)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        val ctx = holder.itemView.context

        holder.tvName.text = app.label
        holder.tvPkg.text = app.packageName

        try {
            val icon = ctx.packageManager.getApplicationIcon(app.packageName)
            holder.ivIcon.setImageDrawable(icon)
        } catch (_: Exception) {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        val config = BoostConfigStore.getConfig(ctx, app.packageName)
        holder.tvPreset.text = config.preset.label

        holder.itemView.setOnClickListener { onClick(app) }
    }
}
