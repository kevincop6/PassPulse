package com.ulpro.passpulse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ulpro.passpulse.databinding.ItemKeyBinding
import java.text.DateFormat
import java.util.Date

class KeyAdapter(private val items: List<VaultEntry>, private val onClick: (VaultEntry) -> Unit) : RecyclerView.Adapter<KeyAdapter.Holder>() {
    class Holder(val binding: ItemKeyBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemKeyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val isWebsite = item.kind == "website" || item.uris.isNotEmpty()
        holder.binding.entryTypeIcon.setImageResource(if (isWebsite) R.drawable.ic_web else R.drawable.ic_app)
        holder.binding.entryTypeIcon.contentDescription = holder.itemView.context.getString(if (isWebsite) R.string.website_entry else R.string.application_entry)
        holder.binding.titleText.text = item.name
        holder.binding.maskedText.text = if (item.username.isBlank()) "••••••••••••" else item.username
        holder.binding.dateText.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.updatedAt))
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.copyKey.setOnClickListener { onClick(item) }
    }
}
