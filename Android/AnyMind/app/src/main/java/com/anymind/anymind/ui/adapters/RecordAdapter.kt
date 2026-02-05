package com.anymind.anymind.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anymind.anymind.R
import com.anymind.anymind.data.RecordSummary
import com.anymind.anymind.util.DateCodec
import com.anymind.anymind.util.SyncStatus

class RecordAdapter(
    private val onClick: (RecordSummary) -> Unit
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    private val items = mutableListOf<RecordSummary>()

    fun submitList(list: List<RecordSummary>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val preview: TextView = itemView.findViewById(R.id.record_preview)
        private val tags: TextView = itemView.findViewById(R.id.record_tags)
        private val updated: TextView = itemView.findViewById(R.id.record_updated)
        private val syncIcon: ImageView = itemView.findViewById(R.id.record_sync_icon)

        fun bind(item: RecordSummary) {
            preview.text = item.contentPreview
            tags.text = if (item.tags.isEmpty()) "" else item.tags.joinToString(" ")
            updated.text = DateCodec.formatDisplay(item.updatedAt)
            val icon = SyncStatus.icon(item.updatedAt, item.lastSyncAt, item.syncEnabled)
            syncIcon.setImageResource(icon.iconRes)
            syncIcon.imageTintList = ContextCompat.getColorStateList(itemView.context, icon.tintRes)
            syncIcon.contentDescription = icon.label
        }
    }
}
