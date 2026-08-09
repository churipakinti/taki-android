/*
 * MainFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.lang.ref.SoftReference
import kotlin.collections.HashMap
import kotlin.collections.hashMapOf
import kotlin.collections.set
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.fragment.legacy.SelectGenreFragment
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.EMPTY_CAPABILITIES
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities
import timber.log.Timber

class MainFragment :
    ScopeFragment(),
    KoinScopeComponent {

    private var filterButtonBar: FilterButtonBar? = null
    private var layoutType: LayoutType = LayoutType.COVER
    private var binding: View? = null

    private lateinit var musicCollectionAdapter: MusicCollectionAdapter
    private lateinit var viewPager: ViewPager2
    private val rxBusSubscription = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.i("onCreate")
        binding = inflater.inflate(R.layout.primary, container, false)
        return binding!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentTitle.setTitle(this, R.string.music_library_label)

        view.findViewById<View>(R.id.library_manage_button).setOnClickListener {
            (activity as? NavigationActivity)?.showLibraryHub(it)
        }

        // Load last layout from settings
        layoutType = LayoutType.from(Settings.lastViewType)

        // Init ViewPager2
        musicCollectionAdapter = MusicCollectionAdapter(this, layoutType)
        viewPager = binding!!.findViewById(R.id.pager)
        viewPager.adapter = musicCollectionAdapter

        filterButtonBar = binding!!.findViewById(R.id.filter_button_bar)
        musicCollectionAdapter.filterButtonBar = filterButtonBar

        filterButtonBar!!.setOnLayoutTypeChangedListener {
            updateLayoutTypeOnCurrentFragment(it)
        }

        filterButtonBar!!.setOnPrimaryActionClickListener {
            val currentFragment = findCurrentFragment()
            if (currentFragment is FilterableFragment) {
                currentFragment.onPrimaryAction()
            }
        }

        filterButtonBar!!.setOnOrderChangedListener {
            updateSortOrderOnCurrentFragment(it)
        }

        // Set layout toggle Chip to correct state
        filterButtonBar!!.setLayoutType(layoutType)

        // Reconfigure available capabilities when we switch between online and offline mode.
        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            val frag = findCurrentFragment()
            filterButtonBar!!.configureWithCapabilitiesFromFragment(frag)
        }

        // Listen to changes in the current page (=fragment)
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                Timber.i("On Page changed $position")

                // This is a bit tricky. We need to configure the FilterButtonBar based on the
                // fragments capabilities. But this function can be called before the fragment has
                // been created, and the ViewPager might create the fragments in arbitrary order.
                // Therefore we store a flag in the Adapter, to signal that the next created
                // fragment of the given position should propagate its capabilities
                val frag = findFragmentAtPosition(childFragmentManager, position)
                if (frag != null) {
                    filterButtonBar!!.configureWithCapabilitiesFromFragment(frag)
                } else {
                    musicCollectionAdapter.propagateCapabilitiesMatcher = position
                }
            }
        })

        // The TabLayoutMediator manages the names of the Tabs (Albums, Artists, etc)
        val tabLayout: TabLayout = binding!!.findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = musicCollectionAdapter.getTitleForFragment(position, requireContext())
        }.attach()
    }

    private fun updateLayoutTypeOnCurrentFragment(it: LayoutType) {
        val curFrag = findCurrentFragment()

        if (curFrag is FilterableFragment) {
            curFrag.setLayoutType(it)
        }

        layoutType = it
        Settings.lastViewType = it.value
    }

    private fun updateSortOrderOnCurrentFragment(it: SortOrder) {
        val curFrag = findCurrentFragment()

        if (curFrag is FilterableFragment) {
            curFrag.setOrderType(it)
        }
    }

    private fun findCurrentFragment(): Fragment? =
        findFragmentAtPosition(childFragmentManager, viewPager.currentItem)

    private fun findFragmentAtPosition(fragmentManager: FragmentManager, position: Int): Fragment? {
        // If a fragment was recently created and never shown the fragment manager might not
        // hold a reference to it. Fallback on the WeakMap instead.
        return fragmentManager.findFragmentByTag("f$position")
            ?: musicCollectionAdapter.fragmentMap[position]?.get()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rxBusSubscription.dispose()
    }
}

private fun FilterButtonBar.configureWithCapabilitiesFromFragment(frag: Fragment?) {
    if (frag is FilterableFragment) {
        Timber.w("Setting kapas: ${frag.viewCapabilities}")
        this.configureWithCapabilities(frag.viewCapabilities, frag.getOrderType())
    } else {
        Timber.w("Setting kapas: $EMPTY_CAPABILITIES")
        this.configureWithCapabilities(EMPTY_CAPABILITIES)
    }
}

@Suppress("MagicNumber")
class MusicCollectionAdapter(fragment: Fragment, initialType: LayoutType = LayoutType.LIST) :
    FragmentStateAdapter(fragment) {

    var filterButtonBar: FilterButtonBar? = null
    private var layoutType: LayoutType = initialType

    var propagateCapabilitiesMatcher: Int? = null

    // viewPager.findFragmentAtPosition(childFragmentManager, position) is sometimes delayed..
    var fragmentMap: HashMap<Int, SoftReference<Fragment>> = hashMapOf()

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        Timber.i("Creating new fragment at position: $position")

        val action = when (position) {
            0 -> NavigationGraphDirections.toArtistList()

            1 -> NavigationGraphDirections.toAlbumList(
                AlbumListType.NEWEST,
                size = Settings.maxAlbums
            )

            2 -> NavigationGraphDirections.toTrackCollection()

            else -> NavigationGraphDirections.toGenreList()
        }

        val fragment = when (position) {
            0 -> ArtistListFragment(layoutType)
            1 -> AlbumListFragment(layoutType)
            2 -> TrackCollectionFragment(SortOrder.ALL_SONGS)
            else -> SelectGenreFragment()
        }

        fragmentMap[position] = SoftReference(fragment)
        fragment.arguments = action.arguments

        // See comment in onPageSelected
        if (propagateCapabilitiesMatcher == position) {
            Timber.w("Setting capacities while creating, $position")
            propagateCapabilitiesMatcher = null
            filterButtonBar!!.configureWithCapabilitiesFromFragment(fragment)
        }

        return fragment
    }

    fun getTitleForFragment(pos: Int, context: Context): String = when (pos) {
        0 -> context.getString(R.string.main_artists_title)
        1 -> context.getString(R.string.main_albums_title)
        2 -> context.getString(R.string.main_songs_title)
        3 -> context.getString(R.string.main_genres_title)
        else -> "Unknown"
    }
}

interface FilterableFragment {
    fun setLayoutType(newType: LayoutType) {}
    fun setOrderType(newOrder: SortOrder)
    fun getOrderType(): SortOrder? = null
    fun onPrimaryAction() {}
    var viewCapabilities: ViewCapabilities
}
