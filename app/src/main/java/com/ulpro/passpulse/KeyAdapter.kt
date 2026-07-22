package com.ulpro.passpulse
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ulpro.passpulse.databinding.ItemKeyBinding
import java.text.DateFormat
import java.util.Date
class KeyAdapter(private val items: List<StoredKey>, private val onClick: (StoredKey) -> Unit) : RecyclerView.Adapter<KeyAdapter.Holder>() {
    class Holder(val binding: ItemKeyBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemKeyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) { val item = items[position]; holder.binding.dateText.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt)); holder.binding.root.setOnClickListener { onClick(item) }; holder.binding.copyKey.setOnClickListener { onClick(item) } }
}
