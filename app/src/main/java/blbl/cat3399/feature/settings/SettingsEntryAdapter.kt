package blbl.cat3399.feature.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.databinding.ItemSettingEntryBinding

class SettingsEntryAdapter(
    private val onClick: (SettingEntry) -> Unit,
) : RecyclerView.Adapter<SettingsEntryAdapter.Vh>() {
    private val items = ArrayList<SettingEntry>()

    fun submit(list: List<SettingEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemSettingEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemSettingEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingEntry, onClick: (SettingEntry) -> Unit) {
            binding.tvTitle.text = item.title
            binding.tvValue.text = item.value
            if (item.desc.isNullOrBlank()) {
                binding.tvDesc.visibility = android.view.View.GONE
            } else {
                binding.tvDesc.visibility = android.view.View.VISIBLE
                binding.tvDesc.text = item.desc
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}

