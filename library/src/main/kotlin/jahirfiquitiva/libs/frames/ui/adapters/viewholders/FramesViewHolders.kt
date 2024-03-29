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
package jahirfiquitiva.libs.frames.ui.adapters.viewholders

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import android.support.v4.view.ViewCompat
import android.support.v7.graphics.Palette
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ca.allanwang.kau.utils.gone
import ca.allanwang.kau.utils.tint
import ca.allanwang.kau.utils.visible
import ca.allanwang.kau.utils.withAlpha
import com.bumptech.glide.RequestManager
import com.bumptech.glide.util.ViewPreloadSizeProvider
import jahirfiquitiva.libs.frames.R
import jahirfiquitiva.libs.frames.data.models.Collection
import jahirfiquitiva.libs.frames.data.models.Wallpaper
import jahirfiquitiva.libs.frames.helpers.extensions.createHeartIcon
import jahirfiquitiva.libs.frames.helpers.extensions.tilesColor
import jahirfiquitiva.libs.frames.helpers.glide.GlidePaletteListener
import jahirfiquitiva.libs.frames.helpers.glide.clearFromGlide
import jahirfiquitiva.libs.frames.helpers.glide.loadPicture
import jahirfiquitiva.libs.frames.helpers.glide.preloadPicture
import jahirfiquitiva.libs.frames.helpers.glide.smoothAnimator
import jahirfiquitiva.libs.kext.extensions.bestSwatch
import jahirfiquitiva.libs.kext.extensions.bind
import jahirfiquitiva.libs.kext.extensions.boolean
import jahirfiquitiva.libs.kext.extensions.context
import jahirfiquitiva.libs.kext.extensions.drawable
import jahirfiquitiva.libs.kext.extensions.getActiveIconsColorFor
import jahirfiquitiva.libs.kext.extensions.getPrimaryTextColorFor
import jahirfiquitiva.libs.kext.extensions.getSecondaryTextColorFor
import jahirfiquitiva.libs.kext.extensions.hasContent
import jahirfiquitiva.libs.kext.extensions.isLowRamDevice
import jahirfiquitiva.libs.kext.extensions.notNull
import org.jetbrains.anko.doAsync

const val DETAILS_OPACITY = 0.85F
const val COLLECTION_DETAILS_OPACITY = 0.4F

abstract class FramesViewClickListener<in T, in VH> {
    abstract fun onSingleClick(item: T, holder: VH)
    open fun onLongClick(item: T) {}
    open fun onHeartClick(view: ImageView, item: T, @ColorInt color: Int) {}
}

abstract class FramesWallpaperHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    internal var wallpaper: Wallpaper? = null
    internal abstract val img: ImageView?
    
    internal val gradPrimText: Int by lazy { Color.parseColor("#ffffffff") }
    internal val gradSecText: Int by lazy { Color.parseColor("#b3ffffff") }
    
    private var animator: ValueAnimator? = null
    private val listener: GlidePaletteListener by lazy {
        object : GlidePaletteListener() {
            override fun onLoadSucceed(resource: Drawable, model: Any?, isFirst: Boolean): Boolean {
                stopLoading()
                return super.onLoadSucceed(resource, model, isFirst)
            }
            
            override fun onLoadFailed(): Boolean {
                stopLoading()
                return super.onLoadFailed()
            }
            
            override fun onPaletteReady(palette: Palette?) {
                stopLoading()
                doWithPalette(palette)
            }
        }
    }
    
    internal fun startLoading() {
        if (context.isLowRamDevice) {
            stopLoading()
            return
        }
        animator = smoothAnimator(context.tilesColor.withAlpha(0.4F), context.tilesColor) {
            itemView.setBackgroundColor(it)
        }
    }
    
    private fun stopLoading() {
        animator?.cancel()
        itemView.setBackgroundColor(context.tilesColor)
    }
    
    internal fun loadImage(manager: RequestManager?, url: String, thumbUrl: String) {
        img?.loadPicture(manager, url, thumbUrl, listener = listener)
    }
    
    internal abstract fun doWithPalette(palette: Palette?)
    
    fun unbind() {
        stopLoading()
        img?.clearFromGlide()
    }
}

class CollectionHolder(itemView: View) : FramesWallpaperHolder(itemView) {
    override val img: ImageView?
        get() = itemView.findViewById(R.id.collection_picture)
    
    private val detailsBg: LinearLayout? by bind(R.id.collection_details)
    private val title: TextView? by bind(R.id.collection_title)
    private val amount: TextView? by bind(R.id.collection_walls_number)
    
    fun setItem(
        manager: RequestManager?,
        provider: ViewPreloadSizeProvider<Wallpaper>,
        collection: Collection,
        listener: FramesViewClickListener<Collection, CollectionHolder>
               ) {
        itemView.background = null
        detailsBg?.background = null
        startLoading()
        if (this.wallpaper != collection.bestCover) this.wallpaper = collection.bestCover
        val rightCover = collection.bestCover ?: collection.wallpapers.first()
        val url = rightCover.url
        val thumb = rightCover.thumbUrl
        doAsync { img?.preloadPicture(manager, url, thumb) }
        with(itemView) {
            val filled = context.boolean(R.bool.enable_filled_collection_preview)
            title?.text = if (filled) collection.name.toUpperCase() else collection.name
            title?.setTextColor(context.getPrimaryTextColorFor(context.tilesColor))
            amount?.text = collection.wallpapers.size.toString()
            amount?.setTextColor(context.getSecondaryTextColorFor(context.tilesColor))
            loadImage(manager, url, thumb)
            img?.let { provider.setView(it) }
        }
        itemView.setOnClickListener { listener.onSingleClick(collection, this) }
    }
    
    override fun doWithPalette(palette: Palette?) {
        if (context.boolean(R.bool.enable_colored_tiles)) {
            val color = try {
                palette?.bestSwatch?.rgb ?: context.tilesColor
            } catch (e: Exception) {
                context.tilesColor
            }
            
            val opacity =
                if (context.boolean(R.bool.enable_filled_collection_preview))
                    COLLECTION_DETAILS_OPACITY
                else DETAILS_OPACITY
            
            itemView.setBackgroundColor(color)
            detailsBg?.background = null
            detailsBg?.setBackgroundColor(color.withAlpha(opacity))
            title?.setTextColor(context.getPrimaryTextColorFor(color))
            amount?.setTextColor(context.getSecondaryTextColorFor(color))
        } else {
            itemView.setBackgroundColor(context.tilesColor)
            detailsBg?.setBackgroundColor(0)
            detailsBg?.background = context.drawable(R.drawable.gradient)
            title?.setTextColor(gradPrimText)
            amount?.setTextColor(gradSecText)
        }
    }
}

class WallpaperHolder(itemView: View, private val showFavIcon: Boolean) :
    FramesWallpaperHolder(itemView) {
    
    private var shouldCheck = false
    
    private var heartColor = context.getActiveIconsColorFor(context.tilesColor)
    
    override val img: ImageView?
        get() = itemView.findViewById(R.id.wallpaper_image)
    
    val name: TextView? by bind(R.id.wallpaper_name)
    val author: TextView? by bind(R.id.wallpaper_author)
    val heartIcon: ImageView? by bind(R.id.heart_icon)
    private val detailsBg: LinearLayout? by bind(R.id.wallpaper_details)
    
    fun setItem(
        manager: RequestManager?,
        provider: ViewPreloadSizeProvider<Wallpaper>,
        wallpaper: Wallpaper,
        check: Boolean,
        listener: FramesViewClickListener<Wallpaper, WallpaperHolder>
               ) {
        detailsBg?.background = null
        startLoading()
        if (this.wallpaper != wallpaper) this.wallpaper = wallpaper
        doAsync { img?.preloadPicture(manager, wallpaper.url, wallpaper.thumbUrl) }
        with(itemView) {
            heartIcon?.setImageDrawable(null)
            heartIcon?.gone()
            if (shouldCheck != check) shouldCheck = check
            
            img.notNull { ViewCompat.setTransitionName(it, "img_transition_$adapterPosition") }
            name.notNull { ViewCompat.setTransitionName(it, "name_transition_$adapterPosition") }
            author.notNull {
                ViewCompat.setTransitionName(it, "author_transition_$adapterPosition")
            }
            heartIcon.notNull {
                ViewCompat.setTransitionName(it, "fav_transition_$adapterPosition")
            }
            
            name?.text = wallpaper.name
            name?.setTextColor(context.getPrimaryTextColorFor(context.tilesColor))
            if (wallpaper.author.hasContent()) {
                author?.text = wallpaper.author
                author?.setTextColor(context.getSecondaryTextColorFor(context.tilesColor))
                author?.visible()
            } else {
                author?.gone()
            }
            
            if (showFavIcon) {
                heartIcon?.let { heart ->
                    heart.setImageDrawable(context.createHeartIcon(shouldCheck)?.tint(heartColor))
                    heart.setOnClickListener { listener.onHeartClick(heart, wallpaper, heartColor) }
                    heart.visible()
                }
            }
            
            loadImage(manager, wallpaper.url, wallpaper.thumbUrl)
            img?.let { provider.setView(it) }
        }
        itemView.setOnClickListener { listener.onSingleClick(wallpaper, this) }
        itemView.setOnLongClickListener { listener.onLongClick(wallpaper);true }
    }
    
    override fun doWithPalette(palette: Palette?) {
        if (context.boolean(R.bool.enable_colored_tiles)) {
            val color = try {
                palette?.bestSwatch?.rgb ?: context.tilesColor
            } catch (e: Exception) {
                context.tilesColor
            }
            itemView.setBackgroundColor(color)
            detailsBg?.background = null
            detailsBg?.setBackgroundColor(color.withAlpha(DETAILS_OPACITY))
            name?.setTextColor(context.getPrimaryTextColorFor(color))
            author?.setTextColor(context.getSecondaryTextColorFor(color))
            if (showFavIcon) {
                heartColor = context.getActiveIconsColorFor(color)
                heartIcon?.setImageDrawable(context.createHeartIcon(shouldCheck)?.tint(heartColor))
            }
        } else {
            itemView.setBackgroundColor(context.tilesColor)
            detailsBg?.setBackgroundColor(0)
            detailsBg?.background = context.drawable(R.drawable.gradient)
            name?.setTextColor(gradPrimText)
            author?.setTextColor(gradSecText)
            if (showFavIcon) {
                heartIcon?.setImageDrawable(
                    context.createHeartIcon(shouldCheck)?.tint(gradPrimText))
            }
        }
    }
}