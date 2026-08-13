/*
 * AlbumInfoBottomSheetFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.subsonic.ImageLoaderProvider

/**
 * Editorial/metadata info for Album Detail (docs/TAKI_ALBUM_INFO_MUSIC_FIRST.md), reached via the
 * header's Information action. Takes already-resolved data as arguments rather than fetching its
 * own copy: [TrackCollectionFragment] already fetches album notes in the background as soon as
 * the album opens (unchanged - see loadAlbumInfo()/TrackCollectionModel.getAlbumInfo(), which
 * caches by album id), purely to decide whether the Information button has anything to show. By
 * the time that button is visible/tappable, the notes and the rest of this data are already
 * resolved, so this sheet never needs its own loading or error state, and never re-fetches
 * anything - no playback, data-loading, or API code is touched by this fragment.
 */
class AlbumInfoBottomSheetFragment : BottomSheetDialogFragment(), KoinComponent {

    private val imageLoaderProvider: ImageLoaderProvider by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.album_info_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()

        val coverArtId = args.getString(ARG_COVER_ART_ID)
        val coverArtKey = args.getString(ARG_COVER_ART_KEY)
        val art = view.findViewById<ImageView>(R.id.album_info_art)
        imageLoaderProvider.executeOn {
            it.loadImage(art, coverArtId, coverArtKey, false, 0)
        }

        view.findViewById<TextView>(R.id.album_info_name).text = args.getString(ARG_ALBUM_NAME)
        view.findViewById<TextView>(R.id.album_info_artist).text = args.getString(ARG_ARTIST)

        val songCount = args.getInt(ARG_SONG_COUNT)
        val discCount = args.getInt(ARG_DISC_COUNT)
        val secondaryParts = listOfNotNull(
            args.getString(ARG_YEAR),
            if (songCount > 0) {
                resources.getQuantityString(R.plurals.n_songs, songCount, songCount)
            } else {
                null
            },
            if (discCount > 1) {
                resources.getQuantityString(R.plurals.n_discs, discCount, discCount)
            } else {
                null
            }
        )
        view.findViewById<TextView>(R.id.album_info_secondary).text =
            secondaryParts.joinToString(" · ")

        view.findViewById<TextView>(R.id.album_info_description).text =
            args.getString(ARG_DESCRIPTION)

        // Edge-to-edge: the description is the only part that can scroll under the nav bar, so
        // only it needs the inset added to its bottom padding - the sticky header above never
        // reaches that far down.
        val description = view.findViewById<TextView>(R.id.album_info_description)
        val descriptionBasePadding = description.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            description.updatePadding(bottom = descriptionBasePadding + bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        // Cap the sheet at ~85% of screen height (docs/TAKI_ALBUM_INFO_MUSIC_FIRST.md) - a short
        // description still sizes to content since this is a ceiling, not a fixed height.
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        BottomSheetBehavior.from(bottomSheet).maxHeight =
            (resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
    }

    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_ALBUM_NAME = "album_name"
        private const val ARG_ARTIST = "artist"
        private const val ARG_YEAR = "year"
        private const val ARG_SONG_COUNT = "song_count"
        private const val ARG_DISC_COUNT = "disc_count"
        private const val ARG_COVER_ART_ID = "cover_art_id"
        private const val ARG_COVER_ART_KEY = "cover_art_key"
        private const val MAX_HEIGHT_FRACTION = 0.85

        const val TAG = "AlbumInfoBottomSheet"

        @Suppress("LongParameterList")
        fun newInstance(
            description: String?,
            albumName: String?,
            artist: String?,
            year: String?,
            songCount: Int,
            discCount: Int,
            coverArtId: String?,
            coverArtKey: String?
        ): AlbumInfoBottomSheetFragment = AlbumInfoBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_DESCRIPTION, description)
                putString(ARG_ALBUM_NAME, albumName)
                putString(ARG_ARTIST, artist)
                putString(ARG_YEAR, year)
                putInt(ARG_SONG_COUNT, songCount)
                putInt(ARG_DISC_COUNT, discCount)
                putString(ARG_COVER_ART_ID, coverArtId)
                putString(ARG_COVER_ART_KEY, coverArtKey)
            }
        }
    }
}
