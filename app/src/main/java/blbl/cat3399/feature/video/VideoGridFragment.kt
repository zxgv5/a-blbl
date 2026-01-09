package blbl.cat3399.feature.video

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.databinding.FragmentVideoGridBinding
import blbl.cat3399.feature.player.PlayerActivity
import kotlinx.coroutines.launch

class VideoGridFragment : Fragment() {
    private var _binding: FragmentVideoGridBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoCardAdapter
    private var preDrawListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
    private var firstDrawLogged: Boolean = false
    private var initialLoadTriggered: Boolean = false

    private val source: String by lazy { requireArguments().getString(ARG_SOURCE) ?: SRC_POPULAR }
    private val rid: Int by lazy { requireArguments().getInt(ARG_RID, 0) }

    private val loadedBvids = HashSet<String>()
    private var isLoadingMore: Boolean = false
    private var endReached: Boolean = false

    private var page: Int = 1
    private var recommendFetchRow: Int = 1

    private var requestToken: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVideoGridBinding.inflate(inflater, container, false)
        AppLog.d("VideoGrid", "onCreateView source=$source rid=$rid t=${SystemClock.uptimeMillis()}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AppLog.d("VideoGrid", "onViewCreated source=$source rid=$rid t=${SystemClock.uptimeMillis()}")
        if (!::adapter.isInitialized) {
            adapter = VideoCardAdapter { card ->
                AppLog.i("VideoGrid", "click bvid=${card.bvid} cid=${card.cid}")
                startActivity(
                    Intent(requireContext(), PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_BVID, card.bvid)
                        .putExtra(PlayerActivity.EXTRA_CID, card.cid ?: -1L),
                )
            }
        }
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.layoutManager = StaggeredGridLayoutManager(spanCountForWidth(), StaggeredGridLayoutManager.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
        }
        (binding.recycler.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recycler.clearOnScrollListeners()
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                private val tmp = IntArray(8)

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    if (isLoadingMore || endReached) return

                    val lm = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPositions(tmp).maxOrNull() ?: return
                    val total = adapter.itemCount
                    if (total <= 0) return

                    if (total - lastVisible - 1 <= 8) {
                        AppLog.d("VideoGrid", "near end source=$source rid=$rid t=${SystemClock.uptimeMillis()}")
                        loadNextPage()
                    }
                }
            },
        )

        binding.swipeRefresh.setOnRefreshListener { resetAndLoad() }

        if (preDrawListener == null) {
            preDrawListener =
                android.view.ViewTreeObserver.OnPreDrawListener {
                    if (!firstDrawLogged) {
                        firstDrawLogged = true
                        AppLog.d(
                            "VideoGrid",
                            "first preDraw source=$source rid=$rid t=${SystemClock.uptimeMillis()}",
                        )
                    }
                    true
                }
            binding.recycler.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.d("VideoGrid", "onResume source=$source rid=$rid t=${SystemClock.uptimeMillis()}")
        (binding.recycler.layoutManager as? StaggeredGridLayoutManager)?.spanCount = spanCountForWidth()
        maybeTriggerInitialLoad()
    }

    private fun maybeTriggerInitialLoad() {
        if (initialLoadTriggered) return
        if (!this::adapter.isInitialized) return
        if (adapter.itemCount != 0) {
            initialLoadTriggered = true
            return
        }
        if (binding.swipeRefresh.isRefreshing) return
        binding.swipeRefresh.isRefreshing = true
        resetAndLoad()
        initialLoadTriggered = true
    }

    private fun resetAndLoad() {
        AppLog.d("VideoGrid", "resetAndLoad source=$source rid=$rid t=${SystemClock.uptimeMillis()}")
        loadedBvids.clear()
        endReached = false
        isLoadingMore = false
        page = 1
        recommendFetchRow = 1
        requestToken++
        loadNextPage(isRefresh = true)
    }

    private fun loadNextPage(isRefresh: Boolean = false) {
        if (isLoadingMore || endReached) return
        val token = requestToken
        isLoadingMore = true
        val startAt = SystemClock.uptimeMillis()
        AppLog.d(
            "VideoGrid",
            "loadNextPage start source=$source rid=$rid page=$page refresh=$isRefresh t=$startAt",
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ps = 24
                val cards = when (source) {
                    SRC_RECOMMEND -> BiliApi.recommend(freshIdx = page, ps = ps, fetchRow = recommendFetchRow)
                    SRC_REGION -> BiliApi.regionLatest(rid = rid, pn = page, ps = ps)
                    else -> BiliApi.popular(pn = page, ps = ps)
                }

                if (token != requestToken) return@launch

                if (cards.isEmpty()) {
                    endReached = true
                    return@launch
                }

                val filtered = cards.filter { loadedBvids.add(it.bvid) }
                if (page == 1) {
                    adapter.submit(filtered)
                } else {
                    adapter.append(filtered)
                }

                if (source == SRC_RECOMMEND) {
                    recommendFetchRow += cards.size
                }
                page++

                if (filtered.isEmpty()) {
                    endReached = true
                }

                AppLog.i(
                    "VideoGrid",
                    "load ok source=$source rid=$rid page=${page - 1} add=${filtered.size} total=${adapter.itemCount} cost=${SystemClock.uptimeMillis() - startAt}ms",
                )
            } catch (t: Throwable) {
                AppLog.e("VideoGrid", "load failed source=$source rid=$rid page=$page", t)
                Toast.makeText(requireContext(), "加载失败，可查看 Logcat(标签 BLBL)", Toast.LENGTH_SHORT).show()
            } finally {
                if (isRefresh && token == requestToken) binding.swipeRefresh.isRefreshing = false
                isLoadingMore = false
                AppLog.d(
                    "VideoGrid",
                    "loadNextPage end source=$source rid=$rid page=$page refresh=$isRefresh t=${SystemClock.uptimeMillis()}",
                )
            }
        }
    }

    private fun spanCountForWidth(): Int {
        val override = blbl.cat3399.core.net.BiliClient.prefs.gridSpanCount
        if (override > 0) return override.coerceIn(1, 6)
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        return when {
            widthDp >= 1100 -> 4
            widthDp >= 800 -> 3
            else -> 2
        }
    }

    override fun onDestroyView() {
        AppLog.d("VideoGrid", "onDestroyView source=$source rid=$rid t=${SystemClock.uptimeMillis()}")
        preDrawListener?.let { listener ->
            if (binding.recycler.viewTreeObserver.isAlive) {
                binding.recycler.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
        preDrawListener = null
        firstDrawLogged = false
        initialLoadTriggered = false
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SOURCE = "source"
        private const val ARG_RID = "rid"

        const val SRC_RECOMMEND = "recommend"
        const val SRC_POPULAR = "popular"
        const val SRC_REGION = "region"

        fun newRecommend() = VideoGridFragment().apply { arguments = Bundle().apply { putString(ARG_SOURCE, SRC_RECOMMEND) } }
        fun newPopular() = VideoGridFragment().apply { arguments = Bundle().apply { putString(ARG_SOURCE, SRC_POPULAR) } }
        fun newRegion(rid: Int) = VideoGridFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SOURCE, SRC_REGION)
                putInt(ARG_RID, rid)
            }
        }
    }
}
