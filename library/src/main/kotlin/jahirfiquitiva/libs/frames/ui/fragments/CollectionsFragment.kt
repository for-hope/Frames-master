/*
 * Copyright (c) 2018. Jahir Fiquitiva
 *
 * Licensed under the CreativeCommons Attribution-ShareAlike
 * 4.0 International License. You may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *    http://creativecommons.org/licenses/by-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jahirfiquitiva.libs.frames.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.GridLayoutManager
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.pluscubed.recyclerfastscroll.RecyclerFastScroller
import jahirfiquitiva.libs.frames.R
import jahirfiquitiva.libs.frames.data.models.Collection
import jahirfiquitiva.libs.frames.data.models.Wallpaper
import jahirfiquitiva.libs.frames.helpers.extensions.jfilter
import jahirfiquitiva.libs.frames.helpers.extensions.maxPreload
import jahirfiquitiva.libs.frames.helpers.utils.FL
import jahirfiquitiva.libs.frames.helpers.utils.MAX_COLLECTIONS_LOAD
import jahirfiquitiva.libs.frames.ui.activities.CollectionActivity
import jahirfiquitiva.libs.frames.ui.activities.base.FavsDbManager
import jahirfiquitiva.libs.frames.ui.adapters.CollectionsAdapter
import jahirfiquitiva.libs.frames.ui.adapters.viewholders.CollectionHolder
import jahirfiquitiva.libs.frames.ui.adapters.viewholders.FramesViewClickListener
import jahirfiquitiva.libs.frames.ui.fragments.base.BaseFramesFragment
import jahirfiquitiva.libs.frames.ui.widgets.EmptyViewRecyclerView
import jahirfiquitiva.libs.frames.ui.widgets.EndlessRecyclerViewScrollListener
import jahirfiquitiva.libs.kext.extensions.accentColor
import jahirfiquitiva.libs.kext.extensions.activity
import jahirfiquitiva.libs.kext.extensions.boolean
import jahirfiquitiva.libs.kext.extensions.cardBackgroundColor
import jahirfiquitiva.libs.kext.extensions.hasContent
import jahirfiquitiva.libs.kext.extensions.isInHorizontalMode
import jahirfiquitiva.libs.kext.extensions.isLowRamDevice
import jahirfiquitiva.libs.kext.extensions.notNull
import jahirfiquitiva.libs.kext.ui.decorations.GridSpacingItemDecoration

internal class CollectionsFragment : BaseFramesFragment<Collection, CollectionHolder>() {
    
    private var hasChecker = false
    
    private var swipeToRefresh: SwipeRefreshLayout? = null
    private var recyclerView: EmptyViewRecyclerView? = null
    private var fastScroller: RecyclerFastScroller? = null
    
    private val provider: ViewPreloadSizeProvider<Wallpaper> by lazy {
        ViewPreloadSizeProvider<Wallpaper>()
    }
    
    val collsAdapter: CollectionsAdapter by lazy {
        CollectionsAdapter(
            context?.let { Glide.with(it) },
            boolean(R.bool.enable_filled_collection_preview), provider,
            object : FramesViewClickListener<Collection, CollectionHolder>() {
                override fun onSingleClick(item: Collection, holder: CollectionHolder) {
                    onItemClicked(item, false)
                }
            })
    }
    
    override fun initUI(content: View) {
        swipeToRefresh = content.findViewById(R.id.swipe_to_refresh)
        recyclerView = content.findViewById(R.id.list_rv)
        fastScroller = content.findViewById(R.id.fast_scroller)
        
        swipeToRefresh?.let {
            with(it) {
                setProgressBackgroundColorSchemeColor(context.cardBackgroundColor)
                setColorSchemeColors(context.accentColor)
                setOnRefreshListener { reloadData(0) }
            }
        }
        
        recyclerView?.let { recyclerView ->
            with(recyclerView) {
                textView = content.findViewById(R.id.empty_text)
                emptyView = content.findViewById(R.id.empty_view)
                setEmptyImage(R.drawable.empty_section)
                setEmptyText(R.string.empty_section)
                loadingView = content.findViewById(R.id.loading_view)
                setLoadingText(R.string.loading_section)
                val spanCount = if (context.isInHorizontalMode) 2 else 1
                layoutManager = GridLayoutManager(
                    context, spanCount, GridLayoutManager.VERTICAL, false)
                addItemDecoration(GridSpacingItemDecoration(spanCount, 2, false))
                itemAnimator = if (context.isLowRamDevice) null else DefaultItemAnimator()
                setHasFixedSize(true)
                
                activity {
                    addOnScrollListener(
                        RecyclerViewPreloader(it, collsAdapter, provider, context.maxPreload))
                }
                
                layoutManager.notNull {
                    addOnScrollListener(
                        EndlessRecyclerViewScrollListener(it) { _, view ->
                            if (userVisibleHint) {
                                view.post { collsAdapter.allowMoreItemsLoad() }
                            }
                        })
                }
                
                setItemViewCacheSize((MAX_COLLECTIONS_LOAD * 1.5).toInt())
                adapter = collsAdapter
            }
        }
        
        fastScroller?.attachSwipeRefreshLayout(swipeToRefresh)
        recyclerView?.let { fastScroller?.attachRecyclerView(it) }
    }
    
    override fun getContentLayout(): Int = R.layout.section_with_swipe_refresh
    
    override fun scrollToTop() {
        recyclerView?.post { recyclerView?.scrollToPosition(0) }
    }
    
    @SuppressLint("RestrictedApi")
    override fun onItemClicked(item: Collection, longClick: Boolean) {
        super.onItemClicked(item, longClick)
        val intent = Intent(activity, CollectionActivity::class.java)
        intent.putExtra("item", item)
        intent.putExtra("checker", hasChecker)
        startActivityForResult(intent, 11)
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11) {
            data?.let {
                try {
                    val nFavs = it.getSerializableExtra("nFavs") as? ArrayList<Wallpaper>
                    nFavs?.let {
                        if (it.isNotEmpty())
                            (activity as? FavsDbManager)?.setNewFavorites(it)
                    }
                } catch (e: Exception) {
                    FL.e("Error", e)
                }
            }
        }
    }
    
    override fun onItemClicked(item: Collection, holder: CollectionHolder) {}
    
    override fun loadDataFromViewModel() {
        recyclerView?.state = EmptyViewRecyclerView.State.LOADING
        super.loadDataFromViewModel()
    }
    
    override fun enableRefresh(enable: Boolean) {
        swipeToRefresh?.isEnabled = enable
    }
    
    override fun reloadData(section: Int) {
        val isRefreshing = swipeToRefresh?.isRefreshing ?: false
        if (isRefreshing) swipeToRefresh?.isRefreshing = false
        recyclerView?.state = EmptyViewRecyclerView.State.LOADING
        super.reloadData(section)
        swipeToRefresh?.isRefreshing = true
    }
    
    override fun applyFilter(filter: String, closed: Boolean) {
        val list = ArrayList(collectionsModel?.getData().orEmpty())
        if (filter.hasContent()) {
            recyclerView?.setEmptyImage(R.drawable.no_results)
            recyclerView?.setEmptyText(R.string.search_no_results)
            collsAdapter.setItems(list.jfilter { it.name.contains(filter, true) })
        } else {
            recyclerView?.setEmptyImage(R.drawable.empty_section)
            recyclerView?.setEmptyText(R.string.empty_section)
            collsAdapter.setItems(list)
        }
        if (!closed)
            scrollToTop()
    }
    
    override fun doOnFavoritesChange(data: ArrayList<Wallpaper>) {
        super.doOnFavoritesChange(data)
        swipeToRefresh?.isRefreshing = false
    }
    
    override fun doOnWallpapersChange(data: ArrayList<Wallpaper>, fromCollectionActivity: Boolean) {
        super.doOnWallpapersChange(data, fromCollectionActivity)
        swipeToRefresh?.isRefreshing = false
    }
    
    override fun doOnCollectionsChange(data: ArrayList<Collection>) {
        super.doOnCollectionsChange(data)
        swipeToRefresh?.isRefreshing = false
        collsAdapter.setItems(data)
    }
    
    override fun autoStartLoad(): Boolean = true
    override fun fromCollectionActivity(): Boolean = false
    override fun fromFavorites(): Boolean = false
    
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && !allowReloadAfterVisibleToUser()) recyclerView?.updateEmptyState()
    }
    
    companion object {
        fun create(hasChecker: Boolean): CollectionsFragment =
            CollectionsFragment().apply { this.hasChecker = hasChecker }
    }
}