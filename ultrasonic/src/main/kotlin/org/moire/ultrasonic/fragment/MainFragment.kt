/*
 * MainFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.OFFLINE_DB_ID
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

/**
 * Entry point for the complete collection. Home helps the listener continue listening; Library
 * instead acts as a stable index into personal collections and the server catalogue.
 */
class MainFragment : ScopeFragment(), KoinScopeComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.primary, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val manageButton = view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.library_manage_button
        )
        val activeCollection = activeServerProvider.getActiveServer()
        manageButton.text = if (activeCollection.id == OFFLINE_DB_ID) {
            getString(R.string.library_downloaded_music)
        } else {
            activeCollection.name
        }
        manageButton.setOnClickListener {
            (activity as? NavigationActivity)?.showLibraryHub(it)
        }
        view.findViewById<View>(R.id.library_liked_songs).setOnClickListener {
            findNavController().navigate(
                NavigationGraphDirections.toTrackCollection(
                    getStarred = true,
                    name = getString(R.string.library_liked_songs)
                )
            )
        }
        view.findViewById<View>(R.id.library_playlists).setOnClickListener {
            findNavController().navigate(R.id.playlistsFragment)
        }
        view.findViewById<View>(R.id.library_albums).setOnClickListener {
            findNavController().navigate(
                NavigationGraphDirections.toAlbumList(AlbumListType.SORTED_BY_NAME)
            )
        }
        view.findViewById<View>(R.id.library_artists).setOnClickListener {
            findNavController().navigate(NavigationGraphDirections.toArtistList())
        }
        view.findViewById<View>(R.id.library_songs).setOnClickListener {
            findNavController().navigate(
                NavigationGraphDirections.toTrackCollection(libraryRoot = true)
            )
        }
        view.findViewById<View>(R.id.library_genres).setOnClickListener {
            findNavController().navigate(NavigationGraphDirections.toGenreList())
        }
    }
}

interface FilterableFragment {
    fun setLayoutType(newType: org.moire.ultrasonic.util.LayoutType) {}
    fun setOrderType(newOrder: SortOrder)
    fun getOrderType(): SortOrder? = null
    fun onPrimaryAction() {}
    var viewCapabilities: ViewCapabilities
}
