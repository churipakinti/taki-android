package org.moire.ultrasonic.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.MenuInflater
import android.view.View
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.HeartRating
import androidx.media3.common.StarRating
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.themeColor

const val INDICATOR_THICKNESS_INDEFINITE = 5
const val INDICATOR_THICKNESS_DEFINITE = 10

/**
 * Used to display songs and videos in a `ListView`.
 */
class TrackViewHolder(val view: View) :
    RecyclerView.ViewHolder(view),
    Checkable,
    KoinComponent {

    // Recreated in dispose() (called on every recycle) rather than living for the
    // ViewHolder's whole lifetime - otherwise a coroutine launched for one bound song could
    // still be running, and update this recycled row's UI, after the row was reused for a
    // different song further down the list during fast scrolling.
    private var scope = CoroutineScope(Dispatchers.IO)

    var entry: Track? = null
        private set
    private var songLayout: LinearLayout = view.findViewById(R.id.song_layout)
    private val imageLoaderProvider: ImageLoaderProvider by inject()

    var check: CheckedTextView = view.findViewById(R.id.song_check)
    var drag: ImageView = view.findViewById(R.id.song_drag)
    var menu: View = view.findViewById(R.id.song_menu)
    var observableChecked = MutableLiveData(false)

    private var star: ImageView = view.findViewById(R.id.song_star)
    private var track: TextView = view.findViewById(R.id.song_track)
    private var title: TextView = view.findViewById(R.id.song_title)
    private val defaultTitleColors: ColorStateList = title.textColors
    private val albumArt: ImageView? = view.findViewById(R.id.song_album_art)
    private var artist: TextView = view.findViewById(R.id.song_artist)
    private var duration: TextView = view.findViewById(R.id.song_duration)
    private var statusImage: ImageView = view.findViewById(R.id.song_status_image)
    private var progressIndicator: CircularProgressIndicator =
        view.findViewById<CircularProgressIndicator>(R.id.song_status_progress).apply {
            this.max = 100
        }

    private var isMaximized = false
    private var cachedStatus = DownloadState.UNKNOWN

    // Read by TrackViewBinder to decide which of the Pin/Unpin/Download/Delete context menu
    // items actually apply to this row, without a second (and redundant) lookup -- setSong()
    // already resolves this asynchronously for the row's own status icon.
    val downloadState: DownloadState get() = cachedStatus
    private var isPlayingCached = false
    private var usesQueueStyle = false

    private var rxBusSubscription: CompositeDisposable? = null

    @Suppress("ComplexMethod", "LongParameterList")
    fun setSong(
        song: Track,
        checkable: Boolean,
        draggable: Boolean,
        isSelected: Boolean = false,
        showArtist: Boolean = true,
        showRating: Boolean = true,
        queueStyle: Boolean = false,
        trackNumberText: String? = null,
        showRowActions: Boolean = false
    ) {
        entry = song
        usesQueueStyle = queueStyle

        // Reset the recycled row's download-status indicators synchronously. The real status
        // is looked up async below (RxBus round-trip through DownloadService), so without this
        // a row reused for a different track kept showing whichever of statusImage/
        // progressIndicator the *previous* occupant last had visible until that lookup resolved
        // -- an intermittent rectangle flashing between duration and the menu button while
        // scrolling. Resetting cachedStatus too ensures updateStatus() below can't skip
        // re-applying the correct state just because it happens to match the previous track's.
        cachedStatus = DownloadState.UNKNOWN
        statusImage.isGone = true
        progressIndicator.isGone = true

        albumArt?.let { cover ->
            imageLoaderProvider.executeOn {
                it.loadImage(cover, song, false, 0, R.drawable.unknown_album)
            }
        }

        menu.isVisible = showRowActions

        // Create new Disposable for the new Subscriptions
        rxBusSubscription = CompositeDisposable()
        rxBusSubscription!! += RxBus.playerStateObservable.subscribe {
            val sameTrack = it.track?.id == song.id
            val sameQueueOccurrence = !usesQueueStyle || it.index == bindingAdapterPosition
            setPlayIcon(sameTrack && sameQueueOccurrence)
        }

        rxBusSubscription!! += RxBus.trackDownloadStateObservable.subscribe {
            if (it.id != song.id) return@subscribe
            updateStatus(it.state, it.progress)
        }

        // Listen for rating updates
        rxBusSubscription!! += RxBus.ratingPublishedObservable.subscribe {
            scope.launch(Dispatchers.Main) {
                // Ignore updates which are not for the current song
                if (it.id != song.id) return@launch

                if (it.rating is HeartRating) {
                    updateRatingDisplay(song.userRating, it.rating.isHeart)
                } else if (it.rating is StarRating) {
                    updateRatingDisplay(it.rating.starRating.toInt(), song.starred)
                }
            }
        }

        val entryDescription = Util.readableEntryDescription(song)

        artist.text = entryDescription.artist
        artist.isGone = !showArtist
        title.text = entryDescription.title
        duration.text = entryDescription.duration

        if (trackNumberText != null) {
            track.text = trackNumberText
            if (track.isGone) track.isGone = false
        } else if (Settings.SHOULD_SHOW_TRACK_NUMBER && song.track != null && song.track!! > 0) {
            track.text = entryDescription.trackNumber
        } else {
            if (!track.isGone) track.isGone = true
        }

        val checkValue = (checkable && !song.isVideo)
        if (check.isVisible != checkValue) check.isVisible = checkValue
        if (checkValue) initChecked(isSelected)
        if (drag.isVisible != draggable) drag.isVisible = draggable

        if (!showRating || ActiveServerProvider.isOffline()) {
            star.isGone = true
        } else {
            setupRating(song)
        }

        // Instead of blocking the UI thread while looking up the current state,
        // launch the request in an IO thread and propagate the result through RX
        scope.launch {
            val state = DownloadService.getDownloadState(song)
            RxBus.trackDownloadStatePublisher.onNext(
                RxBus.TrackDownloadState(song.id, state, null)
            )
        }

        updateRatingDisplay(entry!!.userRating, entry!!.starred)

        if (song.isVideo) {
            artist.isGone = true
            progressIndicator.isGone = true
        }
    }

    // This is called when the Holder is recycled and receives a new Song
    fun dispose() {
        rxBusSubscription?.dispose()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
    }

    private val queuePlayingIcon by lazy {
        ContextCompat.getDrawable(view.context, R.drawable.ic_queue_playing)!!
    }

    @Suppress("MagicNumber")
    private fun setPlayIcon(isPlaying: Boolean) {
        if (usesQueueStyle) title.isSelected = isPlaying
        if (isPlaying && !isPlayingCached) {
            isPlayingCached = true
            title.setCompoundDrawablesWithIntrinsicBounds(
                queuePlayingIcon,
                null,
                null,
                null
            )
            title.setTextColor(
                MaterialColors.getColor(
                    view,
                    androidx.appcompat.R.attr.colorPrimary
                )
            )
            songLayout.setBackgroundColor(Color.TRANSPARENT)
            songLayout.elevation = 0F
        } else if (!isPlaying && isPlayingCached) {
            isPlayingCached = false
            title.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                0,
                0
            )
            title.setTextColor(defaultTitleColors)
            songLayout.setBackgroundColor(Color.TRANSPARENT)
            songLayout.elevation = 0F
        }
    }

    private fun setupRating(track: Track) {
        star.isVisible = true
        updateRatingDisplay(track.userRating, track.starred)

        star.setOnClickListener { toggleHeart(track) }
        star.setOnLongClickListener { view -> showRatingPopup(view, track) }
    }

    private fun toggleHeart(track: Track) {
        track.starred = !track.starred
        updateRatingDisplay(track.userRating, track.starred)
        RxBus.ratingSubmitter.onNext(
            RatingUpdate(track.id, HeartRating(track.starred))
        )
    }

    @Suppress("MagicNumber")
    private fun showRatingPopup(view: View, track: Track): Boolean {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.rating, popup.menu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popup.setForceShowIcon(true)

        popup.setOnMenuItemClickListener {
            val rating = when (it.itemId) {
                R.id.popup_rate_1 -> 1
                R.id.popup_rate_2 -> 2
                R.id.popup_rate_3 -> 3
                R.id.popup_rate_4 -> 4
                R.id.popup_rate_5 -> 5
                else -> 0
            }
            track.userRating = rating
            updateRatingDisplay(track.userRating, track.starred)
            RxBus.ratingSubmitter.onNext(
                RatingUpdate(track.id, StarRating(5, rating.toFloat()))
            )
            true
        }
        popup.show()
        return true
    }

    @Suppress("MagicNumber")
    private fun updateRatingDisplay(rating: Int?, starred: Boolean) {
        val ratingDrawable = when (rating) {
            1 -> R.drawable.rating_star_1

            2 -> R.drawable.rating_star_2

            3 -> R.drawable.rating_star_3

            4 -> R.drawable.rating_star_4

            5 -> R.drawable.rating_star_5

            else -> {
                R.drawable.rating_star_0
            }
        }

        val layers = if (starred) {
            arrayOf(
                ResourcesCompat.getDrawable(view.resources, ratingDrawable, null)!!,
                ResourcesCompat.getDrawable(
                    view.resources,
                    R.drawable.rating_heart_mini_overlay,
                    null
                )!!
            )
        } else {
            arrayOf(
                ResourcesCompat.getDrawable(view.resources, ratingDrawable, null)!!
            )
        }

        val ratingDisplay = LayerDrawable(layers)
        ratingDisplay.getDrawable(0).setTint(
            view.context.themeColor(com.google.android.material.R.attr.colorOnBackground)
        )
        if (starred) {
            ratingDisplay.getDrawable(1).setTint(
                view.context.themeColor(com.google.android.material.R.attr.colorTertiary)
            )
        }

        star.setImageDrawable(ratingDisplay)
    }

    private fun updateStatus(status: DownloadState, progress: Int?) {
        progressIndicator.progress = progress ?: 0

        if (status == cachedStatus) return
        cachedStatus = status

        when (status) {
            DownloadState.DONE -> {
                showStatusImage(R.drawable.ic_downloaded_circle)
            }

            DownloadState.PINNED -> {
                showStatusImage(R.drawable.ic_menu_pin)
            }

            DownloadState.FAILED -> {
                showStatusImage(R.drawable.ic_baseline_error)
            }

            DownloadState.DOWNLOADING -> {
                showProgress()
            }

            DownloadState.RETRYING,
            DownloadState.QUEUED
            -> {
                showIndefiniteProgress()
            }

            else -> {
                // This handles CANCELLED too.
                // Usually it means no error, just that the track wasn't downloaded
                showStatusImage(null)
            }
        }
    }

    private fun showStatusImage(image: Int?) {
        progressIndicator.isGone = true
        statusImage.isVisible = true
        if (image != null) {
            statusImage.setImageResource(image)
        } else {
            statusImage.setImageDrawable(null)
        }
    }

    private fun showIndefiniteProgress() {
        statusImage.isGone = true
        progressIndicator.isVisible = true
        progressIndicator.isIndeterminate = true
        progressIndicator.indicatorDirection =
            CircularProgressIndicator.INDICATOR_DIRECTION_COUNTERCLOCKWISE
        progressIndicator.trackThickness = INDICATOR_THICKNESS_INDEFINITE
    }

    private fun showProgress() {
        statusImage.isGone = true
        progressIndicator.isVisible = true
        progressIndicator.isIndeterminate = false
        progressIndicator.indicatorDirection =
            CircularProgressIndicator.INDICATOR_DIRECTION_CLOCKWISE
        progressIndicator.trackThickness = INDICATOR_THICKNESS_DEFINITE
    }

    /*
     * Set the checked value and re-init the MutableLiveData.
     * If we would post a new value, there might be a short glitch where the track is shown with its
     * old selection status before the posted value has been processed.
     */
    private fun initChecked(newStatus: Boolean) {
        observableChecked = MutableLiveData(newStatus)
        check.isChecked = newStatus
    }

    /*
     * To be correct, this method doesn't directly set the checked status.
     * It only notifies the observable. If the selection tracker accepts the selection
     *  (might be false for Singular SelectionTrackers) then it will cause the actual modification.
     */
    override fun setChecked(newStatus: Boolean) {
        observableChecked.postValue(newStatus)
    }

    override fun isChecked(): Boolean = check.isChecked

    override fun toggle() {
        isChecked = isChecked
    }

    fun maximizeOrMinimize() {
        isMaximized = !isMaximized

        title.isSingleLine = !isMaximized
        artist.isSingleLine = !isMaximized
    }
}
