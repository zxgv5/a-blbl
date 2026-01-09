package blbl.cat3399.feature.category

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import blbl.cat3399.core.model.Zone
import blbl.cat3399.feature.video.VideoGridFragment

class CategoryPagerAdapter(
    fragment: Fragment,
    private val zones: List<Zone>,
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = zones.size

    override fun createFragment(position: Int): Fragment {
        val zone = zones[position]
        return if (zone.tid == null) {
            VideoGridFragment.newPopular()
        } else {
            VideoGridFragment.newRegion(zone.tid)
        }
    }
}

