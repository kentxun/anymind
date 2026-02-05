package com.anymind.anymind.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anymind.anymind.R
import com.anymind.anymind.data.GroupSummary

class GroupAdapter(
    private val onClick: (GroupSummary?) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    private val items = mutableListOf<GroupSummary>()
    private var includeAll = true

    fun submitList(list: List<GroupSummary>, includeAllOption: Boolean = true) {
        items.clear()
        items.addAll(list)
        includeAll = includeAllOption
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size + if (includeAll) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        if (includeAll && position == 0) {
            holder.bind("All", null)
            holder.itemView.setOnClickListener { onClick(null) }
        } else {
            val index = if (includeAll) position - 1 else position
            val item = items[index]
            holder.bind(item.id, item.count)
            holder.itemView.setOnClickListener { onClick(item) }
        }
    }

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.group_title)
        private val count: TextView = itemView.findViewById(R.id.group_count)

        fun bind(name: String, countValue: Int?) {
            title.text = name
            count.text = countValue?.toString() ?: ""
        }
    }
}
