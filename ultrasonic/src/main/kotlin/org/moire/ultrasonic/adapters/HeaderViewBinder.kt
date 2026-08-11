package org.moire.ultrasonic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import java.lang.ref.WeakReference
import java.util.Random
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Util

/**
 * This Binder can bind a list of entries into a Header
 */
class HeaderViewBinder(context: Context) :
    ItemViewBinder<AlbumHeader, HeaderViewBinder.ViewHolder>(),
    KoinComponent {

    private val weakContext: WeakReference<Context> = WeakReference(context)
    private val random: Random = Random()
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    // Set our layout files
    val layout = R.layout.list_header_album

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder =
        ViewHolder(inflater.inflate(layout, parent, false))

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverArtView: ImageView = itemView.findViewById(R.id.select_album_art)
        val titleView: TextView = itemView.findViewById(R.id.select_album_title)
        val artistView: TextView = itemView.findViewById(R.id.select_album_artist)
        val metaView: TextView = itemView.findViewById(R.id.select_album_meta)
    }

    override fun onBindViewHolder(holder: ViewHolder, item: AlbumHeader) {
        val context = weakContext.get() ?: return
        val resources = context.resources

        val artworkSelection = random.nextInt(item.childCount)
        val size = Util.getAlbumImageSize(context)

        imageLoaderProvider.executeOn {
            it.loadImage(
                holder.coverArtView,
                item.entries[artworkSelection],
                false,
                size
            )
        }

        if (item.name != null) {
            holder.titleView.isVisible = true
            holder.titleView.text = item.name
        } else {
            holder.titleView.isVisible = false
        }

        // Don't show a header if all entries are videos
        if (item.isAllVideo) {
            return
        }

        val artist: String = when {
            item.artists.size == 1 -> item.artists.iterator().next()
            item.grandParents.size == 1 -> item.grandParents.iterator().next()
            else -> context.resources.getString(R.string.common_various_artists)
        }
        holder.artistView.text = artist

        val genre: String = if (item.genres.size == 1) {
            item.genres.iterator().next()
        } else {
            context.resources.getString(R.string.common_multiple_genres)
        }

        val year: String = if (item.years.size == 1) {
            item.years.iterator().next().toString()
        } else {
            resources.getString(R.string.common_multiple_years)
        }

        val songs = resources.getQuantityString(
            R.plurals.n_songs,
            item.childCount,
            item.childCount
        )

        val duration = Util.formatTotalDuration(item.totalDuration)

        holder.metaView.text = listOf(genre, year, songs, duration).joinToString(" · ")
    }
}
