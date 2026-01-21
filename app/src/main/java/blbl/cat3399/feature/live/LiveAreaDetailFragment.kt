package blbl.cat3399.feature.live

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.tv.TvMode
import blbl.cat3399.core.ui.UiScale
import blbl.cat3399.databinding.FragmentLiveAreaDetailBinding
import kotlin.math.roundToInt

class LiveAreaDetailFragment : Fragment() {
    private var _binding: FragmentLiveAreaDetailBinding? = null
    private val binding get() = _binding!!

    private val parentAreaId: Int by lazy { requireArguments().getInt(ARG_PARENT_AREA_ID, 0) }
    private val areaId: Int by lazy { requireArguments().getInt(ARG_AREA_ID, 0) }
    private val parentTitle: String by lazy { requireArguments().getString(ARG_PARENT_TITLE).orEmpty() }
    private val areaTitle: String by lazy { requireArguments().getString(ARG_AREA_TITLE).orEmpty() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveAreaDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.tvTitle.text =
            when {
                parentTitle.isBlank() -> areaTitle
                areaTitle.isBlank() -> parentTitle
                parentTitle == areaTitle -> areaTitle
                else -> "$parentTitle · $areaTitle"
            }
        applyBackButtonSizing()

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                    binding.contentContainer.id,
                    LiveGridFragment.newArea(
                        parentAreaId = parentAreaId,
                        areaId = areaId,
                        title = areaTitle.ifBlank { parentTitle },
                        enableTabFocus = false,
                    ),
                )
                .commit()

            // Match "MyFavFolderDetail": enter detail then focus first content card automatically.
            binding.contentContainer.post {
                if (_binding == null || !isAdded) return@post
                runCatching { childFragmentManager.executePendingTransactions() }
                val page = childFragmentManager.findFragmentById(binding.contentContainer.id)
                (page as? LivePageFocusTarget)?.requestFocusFirstCardFromContentSwitch()
                    ?: (childFragmentManager.fragments.firstOrNull { it is LivePageFocusTarget } as? LivePageFocusTarget)
                        ?.requestFocusFirstCardFromContentSwitch()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        applyBackButtonSizing()
    }

    private fun applyBackButtonSizing() {
        val b = _binding ?: return
        val tvMode = TvMode.isEnabled(requireContext())
        val sidebarScale =
            (UiScale.factor(requireContext(), tvMode, BiliClient.prefs.sidebarSize) * if (tvMode) 1.0f else 1.20f)
                .coerceIn(0.60f, 1.40f)
        fun px(id: Int): Int = b.root.resources.getDimensionPixelSize(id)
        fun scaledPx(id: Int): Int = (px(id) * sidebarScale).roundToInt().coerceAtLeast(0)

        val sizePx =
            scaledPx(if (tvMode) blbl.cat3399.R.dimen.sidebar_settings_size_tv else blbl.cat3399.R.dimen.sidebar_settings_size).coerceAtLeast(1)
        val padPx =
            scaledPx(if (tvMode) blbl.cat3399.R.dimen.sidebar_settings_padding_tv else blbl.cat3399.R.dimen.sidebar_settings_padding)

        val lp = b.btnBack.layoutParams
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx
            lp.height = sizePx
            b.btnBack.layoutParams = lp
        }
        if (
            b.btnBack.paddingLeft != padPx ||
            b.btnBack.paddingTop != padPx ||
            b.btnBack.paddingRight != padPx ||
            b.btnBack.paddingBottom != padPx
        ) {
            b.btnBack.setPadding(padPx, padPx, padPx, padPx)
        }
    }

    companion object {
        private const val ARG_PARENT_AREA_ID = "parent_area_id"
        private const val ARG_PARENT_TITLE = "parent_title"
        private const val ARG_AREA_ID = "area_id"
        private const val ARG_AREA_TITLE = "area_title"

        fun newInstance(parentAreaId: Int, parentTitle: String, areaId: Int, areaTitle: String): LiveAreaDetailFragment =
            LiveAreaDetailFragment().apply {
                arguments =
                    Bundle().apply {
                        putInt(ARG_PARENT_AREA_ID, parentAreaId)
                        putString(ARG_PARENT_TITLE, parentTitle)
                        putInt(ARG_AREA_ID, areaId)
                        putString(ARG_AREA_TITLE, areaTitle)
                    }
            }
    }
}
