package blbl.cat3399.feature.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.databinding.ItemPlayerSettingBinding

class PlayerSettingsAdapter(
    private val onClick: (SettingItem) -> Unit,
) : RecyclerView.Adapter<PlayerSettingsAdapter.Vh>() {
    data class SettingItem(
        val title: String,
        val subtitle: String,
    )

    private val items = ArrayList<SettingItem>()

    fun submit(list: List<SettingItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemPlayerSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemPlayerSettingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingItem, onClick: (SettingItem) -> Unit) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}

