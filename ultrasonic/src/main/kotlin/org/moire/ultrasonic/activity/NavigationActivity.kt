/*
 * NavigationActivity.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.activity

import android.app.SearchManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.media.AudioManager
import android.os.Bundle
import android.provider.MediaStore
import android.provider.SearchRecentSuggestions
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeActivity
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSettingDao
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.LocaleHelper
import org.moire.ultrasonic.util.PerfMetrics
import org.moire.ultrasonic.util.RecentSearches
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShortcutUtil
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.UncaughtExceptionHandler
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * The main (and only) Activity of Ultrasonic which loads all other screens as Fragments.
 * Because this is the only Activity we have to manage the apps lifecycle through this activity
 * onCreate/onResume/onDestroy methods...
 */
@Suppress("TooManyFunctions")
class NavigationActivity : ScopeActivity() {
    private var nowPlayingView: FragmentContainerView? = null
    private var nowPlayingHidden = false
    private var bottomNavigation: BottomNavigationView? = null
    private var navHostContainer: View? = null
    private var navHostFragmentView: View? = null
    private var contentBackButton: View? = null
    private var contentNavigationHeader: View? = null
    private var toolbar: Toolbar? = null
    private var host: NavHostFragment? = null

    private lateinit var appBarConfiguration: AppBarConfiguration

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverSettingDao: ServerSettingDao by inject()

    private var currentFragmentId: Int = 0
    private var imeVisible = false
    private var navigationBarBottomInset = 0

    // The live bottom inset a scrollable screen should reserve so its last item clears whatever
    // floating chrome (system nav bar + bottom nav + mini-player band) is currently visible.
    // Updated by applyBottomInset(); consumed reactively by bindFloatingChromeInset() and by
    // Compose Home so the reserve grows/shrinks smoothly as the mini-player appears/disappears.
    private val _contentBottomInset = MutableStateFlow(0)
    val contentBottomInset: StateFlow<Int> = _contentBottomInset.asStateFlow()

    // Removed in onDestroy() -- never releasing it left the NavController (owned by this
    // Activity's NavHostFragment) holding a listener that closes over `this`, which on repeated
    // rotation showed up as a genuine, linearly growing StrictMode InstanceCountViolation for
    // NavigationActivity (confirmed with rotations paced 3s apart, well past any GC lag: instance
    // count climbed by exactly 1 per rotation, not just a transient blip).
    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        PerfMetrics.mark("nav_activity_create_start")
        Timber.d("onCreate called")

        // First check if Koin has been started
        if (UApp.instance != null && !UApp.instance!!.initiated) {
            Timber.d("Starting Koin")
            UApp.instance!!.startKoin()
        } else {
            Timber.d("No need to start Koin")
        }

        setUncaughtExceptionHandler()
        Util.applyTheme(this)

        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_MUSIC
        setContentView(R.layout.navigation_activity)
        nowPlayingView = findViewById(R.id.now_playing_fragment)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        navHostContainer = findViewById(R.id.nav_host_container)
        navHostFragmentView = findViewById(R.id.nav_host_fragment)
        contentBackButton = findViewById(R.id.content_back_button)
        contentNavigationHeader = findViewById(R.id.content_navigation_header)
        toolbar = findViewById(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigation_root)) {
                view,
                insets
            ->
            view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            navigationBarBottomInset =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyBottomInset()
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible != isImeVisible) {
                imeVisible = isImeVisible
                updateChromeVisibility()
            }
            insets
        }
        setSupportActionBar(toolbar)

        host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment? ?: return

        val navController = host!!.navController
        contentBackButton?.setOnClickListener {
            if (!navController.navigateUp()) {
                navController.navigate(R.id.mainFragment)
            }
        }

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.mainFragment,
                R.id.searchFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        // setupWithNavController() is kept only for its side effect of auto-syncing the
        // checked bottom nav item against exact destination-id matches (homeFragment,
        // mainFragment, searchFragment) - its own click listener is replaced right below.
        bottomNavigation?.setupWithNavController(navController)

        // A tab tap must always land on that tab's own root, never on whatever sub-screen
        // (an artist, an album...) happened to be open there last. The nav graph is flat
        // (one graph, not one subgraph per tab), so the restoreState/saveState behavior
        // NavigationUI's default click listener uses -- designed for the common per-tab
        // subgraph setup -- doesn't apply cleanly here: it could intermittently restore a
        // stale sub-screen instead of the tab root, and leave the wrong item highlighted
        // afterwards, which made the *next* tap silently do nothing (Home ended up marked
        // as already-selected while a Library sub-screen was still on screen, turning a
        // later tap on Home into a no-op reselect). Popping back to the tab's root id
        // (already proven safe here, it's what the reselect handling below already did) is
        // deterministic: it always finds homeFragment (the graph's start destination, never
        // off the back stack) and, for any other tab, either the root already on the back
        // stack or nothing -- in which case it just navigates to it fresh.
        val switchToBottomNavTab: (MenuItem) -> Boolean = { item ->
            if (!navController.popBackStack(item.itemId, false)) {
                navController.navigate(item.itemId)
            }
            true
        }
        bottomNavigation?.setOnItemSelectedListener(switchToBottomNavTab)
        bottomNavigation?.setOnItemReselectedListener { switchToBottomNavTab(it) }

        // The floating-chrome insets depend on the bottom nav's real measured height (M3 sizes
        // it itself); re-apply once it (or a config/font-scale change) settles its height.
        bottomNavigation?.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) applyBottomInset()
        }

        destinationChangedListener = NavController.OnDestinationChangedListener { _, destination, arguments ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (ignored: Resources.NotFoundException) {
                destination.id.toString()
            }
            Timber.d("Navigated to $dest")

            currentFragmentId = destination.id
            val isLibraryTrackCollection = destination.id == R.id.trackCollectionFragment &&
                (
                    arguments?.getBoolean("libraryRoot") == true ||
                        arguments?.getBoolean("getStarred") == true
                    )
            val isAlbumDetail = destination.id == R.id.trackCollectionFragment &&
                arguments?.getBoolean("isAlbum") == true
            // Genre and Daily Mix (issue #10 shell-continuity fix): same "hide the shared
            // toolbar" treatment as isAlbumDetail/isLibraryTrackCollection, but the Fragment
            // draws its own back+title header (see hidesSupportActionBar's kdoc) instead of the
            // shared content_navigation_header, so this flag is deliberately NOT added to
            // libraryOnlyDestination/showsContentBackButton below.
            val isLightweightHeaderTrackCollection = destination.id == R.id.trackCollectionFragment &&
                (
                    arguments?.getBoolean("dailyMix") == true ||
                        !arguments?.getString("genreName").isNullOrEmpty()
                    )

            // The nav graph is flat, so AndroidX's setupWithNavController() only checks
            // destination.id against the 4 top-level menu items themselves - it can't know that
            // e.g. trackCollectionFragment(libraryRoot=true) is "Songs", reached only from
            // Library. For any destination it doesn't recognize it leaves the bottom nav's
            // selection exactly as it was, so browsing into Library's own sub-screens left
            // "Home" highlighted (whatever tab was last matched before, not where the content
            // actually lives). Only destinations with a single, unambiguous entry point are
            // corrected here; trackCollectionFragment/artistDetailFragment used for
            // album/artist/genre/playlist browsing are reachable from both Home and Library
            // depending on how the user got there, so they're deliberately left alone rather
            // than guessed at.
            // Downloads moved from its own bottom-nav tab into a Library row; it and the
            // downloaded-album detail screen it opens are reached only from Library now,
            // so Library stays highlighted.
            val libraryOnlyDestination = isLibraryTrackCollection || destination.id in setOf(
                R.id.playlistsFragment,
                R.id.albumListFragment,
                R.id.artistListFragment,
                R.id.selectGenreFragment,
                R.id.downloadsFragment,
                R.id.downloadedAlbumFragment
            )
            if (libraryOnlyDestination) {
                bottomNavigation?.menu?.findItem(R.id.mainFragment)?.isChecked = true
            }
            val usesContentHeader = hidesSupportActionBar(
                destination.id,
                isLibraryTrackCollection,
                isAlbumDetail,
                isLightweightHeaderTrackCollection,
            )
            if (usesContentHeader) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
            val showsContentBackButton = destination.id in setOf(
                R.id.playlistsFragment,
                R.id.artistListFragment,
                R.id.albumListFragment,
                R.id.selectGenreFragment,
                R.id.serverSelectorFragment,
                R.id.editServerFragment,
                R.id.aboutFragment,
                R.id.downloadsFragment
            ) || isLibraryTrackCollection || isAlbumDetail ||
                destination.id == R.id.settingsFragment ||
                destination.id == R.id.equalizerFragment
            contentNavigationHeader?.visibility =
                if (showsContentBackButton) View.VISIBLE else View.GONE
            invalidateOptionsMenu()
            updateChromeVisibility()
        }
        navController.addOnDestinationChangedListener(destinationChangedListener!!)

        // Go straight to Connect when no library has ever been configured, instead of landing
        // on Home behind a "Welcome" dialog decision. Taki deliberately ships without a bundled
        // demo or third-party credentials. Checks the real server count (not a one-time
        // "first run" flag) so this also covers every configured server having since been
        // removed. Mirrors exactly how the removed dialog's "Add collection" button already
        // navigated here -- Home stays underneath on the back stack so EditServerFragment's
        // own popBackStack(R.id.homeFragment, false) on a successful connect keeps working.
        lifecycleScope.launch {
            if (serverSettingDao.count() == 0) {
                navController.navigate(
                    R.id.editServerFragment,
                    Bundle().apply { putInt("index", -1) }
                )
            }
        }

        // Ask for permission to send notifications
        Util.ensurePermissionToPostNotification(this)

        rxBusSubscription += RxBus.dismissNowPlayingCommandObservable.subscribe {
            nowPlayingHidden = true
            hideNowPlaying()
        }

        rxBusSubscription += RxBus.playerStateObservable.subscribe {
            if (it.state == STATE_READY) {
                showNowPlaying()
            } else {
                hideNowPlaying()
            }
        }

        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            invalidateOptionsMenu()
        }

        // Setup app shortcuts on supported devices, but not on first start, when the server
        // is not configured yet.
        if (!UApp.instance!!.isFirstRun) {
            ShortcutUtil.registerShortcuts(this)
        }
    }

    fun showLibraryHub(anchorView: View? = null) {
        val currentToolbar = toolbar
        val anchor = anchorView
            ?: currentToolbar
            ?: return
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        // Align to the anchor's end edge - the overflow control always sits top-end, and the
        // Compose Home passes the full-width content root as the anchor.
        popup.gravity = android.view.Gravity.END
        popup.menuInflater.inflate(R.menu.library_hub_popup, popup.menu)
        popup.menu.findItem(R.id.library_hub_current).title = getString(
            R.string.library_hub_current_name,
            activeServerProvider.getActiveServer().name
        )
        popup.setOnMenuItemClickListener { item ->
            val navController = findNavController(R.id.nav_host_fragment)
            when (item.itemId) {
                R.id.library_hub_switch -> navController.navigate(R.id.serverSelectorFragment)

                R.id.library_hub_add -> navController.navigate(
                    R.id.editServerFragment,
                    Bundle().apply { putInt("index", -1) }
                )

                R.id.library_hub_settings -> navController.navigate(R.id.settingsFragment)

                R.id.library_hub_about -> navController.navigate(R.id.aboutFragment)

                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    override fun onResume() {
        Timber.d("onResume called")
        super.onResume()

        Storage.reset()

        lifecycleScope.launch(Dispatchers.IO) {
            Storage.checkForErrorsWithCustomRoot()
        }

        // Lifecycle support's constructor registers some event receivers so it should be created early
        lifecycleSupport.onCreate()

        if (!nowPlayingHidden) {
            showNowPlaying()
        } else {
            hideNowPlaying()
        }
    }

    /*
     * Attention: onDestroy does not mean that the app is necessarily being killed.
     * Also rotating the screen will call onDestroy() and then onCreate()
     */
    override fun onDestroy() {
        Timber.d("onDestroy called")
        rxBusSubscription.dispose()
        destinationChangedListener?.let { host?.navController?.removeOnDestinationChangedListener(it) }
        destinationChangedListener = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = super.onCreateOptionsMenu(menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        // Check if this item ID exists in the nav graph
        val destinationExists = navController.graph.findNode(item.itemId) != null
        return if (destinationExists) {
            item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
        } else {
            // Let the fragments handle their own menu items
            super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // This override is required by design when using setupActionBarWithNavController()
        // with an AppBarConfiguration. It ensures that the Up button behavior is correctly
        // delegated to the navigation back stack.
        return findNavController(R.id.nav_host_fragment).navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        when (intent.action) {
            Constants.INTENT_PLAY_RANDOM_SONGS -> {
                playRandomSongs()
            }

            Intent.ACTION_MAIN -> {
                if (intent.getBooleanExtra(Constants.INTENT_SHOW_PLAYER, false)) {
                    findNavController(R.id.nav_host_fragment).navigate(R.id.playerFragment)
                }
            }

            Intent.ACTION_SEARCH -> {
                handleSearchIntent(intent.getStringExtra(SearchManager.QUERY), false)
            }

            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                handleSearchIntent(intent.getStringExtra(SearchManager.QUERY), true)
            }
        }
    }

    private fun handleSearchIntent(query: String?, autoPlay: Boolean) {
        query?.let { RecentSearches(this).save(it) }
        val suggestions = SearchRecentSuggestions(
            this,
            SearchSuggestionProvider.AUTHORITY,
            SearchSuggestionProvider.MODE
        )
        suggestions.saveRecentQuery(query, null)

        val action = NavigationGraphDirections.toSearchFragment(query, autoPlay)
        findNavController(R.id.nav_host_fragment).navigate(action)
    }

    private fun playRandomSongs() {
        val currentFragment = host?.childFragmentManager?.fragments?.last() ?: return

        // getRandomSongs() is a suspend network call; onNewIntent() (this function's only
        // caller, via the "Play Random Songs" launcher shortcut) runs on the main thread, so
        // this must hop to Dispatchers.IO itself rather than assume an existing background
        // context.
        lifecycleScope.launch(CommunicationError.getHandler(this)) {
            val musicDirectory = withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().getRandomSongs(Settings.MAX_SONGS)
            }

            mediaPlayerManager.addToPlaylist(
                songs = musicDirectory.getTracks(),
                autoPlay = true,
                shuffle = false,
                insertionMode = MediaPlayerManager.InsertionMode.CLEAR
            )

            // Unlike in-app browsing actions, this is only ever reached from the "Play Random
            // Songs" launcher shortcut (see onNewIntent() above) -- there's no browsing screen
            // behind it to stay on, so always transition to Now Playing.
            currentFragment.findNavController().popBackStack(R.id.playerFragment, true)
            currentFragment.findNavController().navigate(R.id.playerFragment)
        }
    }

    /**
     * Apply the customized language settings if needed
     */
    override fun attachBaseContext(newBase: Context?) {
        val locale = Settings.overrideLanguage
        if (locale.isNotEmpty()) {
            val localeUpdatedContext: ContextWrapper = LocaleHelper.wrap(newBase, locale)
            super.attachBaseContext(localeUpdatedContext)
        } else {
            super.attachBaseContext(newBase)
        }
    }

    private fun setUncaughtExceptionHandler() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        if (handler !is UncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler(this))
        }
    }

    private fun showNowPlaying() {
        if (!Settings.SHOW_NOW_PLAYING) {
            hideNowPlaying()
            return
        }

        // The logic for nowPlayingHidden is that the user can dismiss NowPlaying with a gesture,
        // and when the MediaPlayerService requests that it should be shown, it returns
        nowPlayingHidden = false
        // Do not show for Player or while Search is using the IME.
        if (currentFragmentId == R.id.playerFragment ||
            (currentFragmentId == R.id.searchFragment && imeVisible)
        ) {
            hideNowPlaying()
            return
        }

        if (nowPlayingView != null) {
            val playerState: Int = mediaPlayerManager.playbackState
            if (playerState == STATE_BUFFERING || playerState == STATE_READY) {
                val item: MediaItem? = mediaPlayerManager.currentMediaItem
                if (item != null) {
                    nowPlayingView?.visibility = View.VISIBLE
                    applyBottomInset()
                }
            } else {
                hideNowPlaying()
            }
        }
    }

    private fun hideNowPlaying() {
        nowPlayingView?.visibility = View.GONE
        applyBottomInset()
    }

    private val miniPlayerEdgeMarginPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.mini_player_edge_margin)

    // The bottom nav's total footprint from the screen edge: its measured height once laid out
    // (which already includes the system nav-bar inset it carries as bottom padding), or a
    // pre-layout estimate of the M3 labelled height + the nav-bar inset.
    private val bottomNavFootprintPx: Int
        get() = bottomNavigation
            ?.takeIf { it.isLaidOut && it.height > 0 }
            ?.height
            ?: (
                resources.getDimensionPixelSize(R.dimen.bottom_nav_height) +
                    navigationBarBottomInset
                )

    // The mini-player band only: gap above content + mini_player_height + gap above the bottom
    // nav (== the content_inset_floating_chrome dimen the XML scroll views use as a fallback).
    private val floatingChromeInsetPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.content_inset_floating_chrome)

    /*
     * How much a scrollable screen should pad its bottom by so its last item clears whatever
     * floating chrome is visible. Both the bottom nav and the mini-player now overlay content
     * (navigation_activity.xml), so the reserve is the live stack below the content:
     *
     *   bottom nav + mini-player : bottomNavFootprint + (16 + 64 + 16)
     *   bottom nav only          : bottomNavFootprint + 16
     *   mini-player only          : navBar + (16 + 64 + 16)
     *   neither                   : navBar
     *
     * (bottomNavFootprint already includes the system nav-bar inset.) View fragments apply this
     * via bindFloatingChromeInset(); Compose Home reads the same value from contentBottomInset.
     */
    fun getContentBottomInset(): Int = computeContentBottomInset(
        bottomNavVisible = bottomNavigation?.visibility == View.VISIBLE,
        nowPlayingVisible = nowPlayingView?.visibility == View.VISIBLE,
    )

    private fun computeContentBottomInset(
        bottomNavVisible: Boolean,
        nowPlayingVisible: Boolean,
    ): Int {
        val belowMiniPlayer = if (bottomNavVisible) bottomNavFootprintPx else navigationBarBottomInset
        return when {
            bottomNavVisible && nowPlayingVisible -> belowMiniPlayer + floatingChromeInsetPx
            bottomNavVisible -> belowMiniPlayer + miniPlayerEdgeMarginPx
            nowPlayingVisible -> navigationBarBottomInset + floatingChromeInsetPx
            else -> navigationBarBottomInset
        }
    }

    /**
     * Keep [scrollView]'s bottom padding equal to the live floating-chrome inset for as long as
     * [owner] is at least STARTED, so its last item can scroll clear of the bottom nav and the
     * mini-player while the rest of its content still scrolls *behind* them. [scrollView] should
     * set `android:clipToPadding="false"`. Call once from a fragment's onViewCreated.
     */
    fun bindFloatingChromeInset(owner: LifecycleOwner, scrollView: View, extraBottomPx: Int = 0) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                contentBottomInset.collect { inset ->
                    val target = inset + extraBottomPx
                    if (scrollView.paddingBottom != target) {
                        scrollView.updatePadding(bottom = target)
                    }
                }
            }
        }
    }

    // Edge-to-edge (enforced from Android 15 / targetSdk 35 on) draws app content behind the
    // system navigation bar. The bottom nav's background fills that inset (its items sit above
    // it); the mini-player floats 16dp above the bottom nav, or 16dp above the system nav bar
    // when the bottom nav is hidden. Fragments with no scrollable content of their own
    // (Settings/About/EditServer) can't self-inset, so the nav host is padded for them; every
    // scrollable screen renders behind all layers and self-insets via bindFloatingChromeInset().
    private fun applyBottomInset() {
        val navBar = navigationBarBottomInset
        val bottomNavVisible = bottomNavigation?.visibility == View.VISIBLE
        val nowPlayingVisible = nowPlayingView?.visibility == View.VISIBLE

        bottomNavigation?.updatePadding(bottom = if (bottomNavVisible) navBar else 0)

        nowPlayingView?.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = (if (bottomNavVisible) bottomNavFootprintPx else navBar) +
                miniPlayerEdgeMarginPx
        }

        navHostFragmentView?.updatePadding(
            bottom = when {
                !bottomNavVisible && !nowPlayingVisible -> navBar
                !bottomNavVisible && nowPlayingVisible -> floatingChromeInsetPx + navBar
                else -> 0
            },
        )

        _contentBottomInset.value = computeContentBottomInset(bottomNavVisible, nowPlayingVisible)
    }

    private fun updateChromeVisibility() {
        val hideForDestination = currentFragmentId in setOf(
            R.id.playerFragment,
            R.id.settingsFragment,
            R.id.aboutFragment,
            R.id.serverSelectorFragment,
            R.id.editServerFragment,
            R.id.equalizerFragment,
            R.id.lyricsFragment
        )
        val hideForSearchIme = currentFragmentId == R.id.searchFragment && imeVisible
        bottomNavigation?.visibility =
            if (hideForDestination || hideForSearchIme) View.GONE else View.VISIBLE
        if (currentFragmentId == R.id.playerFragment || hideForSearchIme) {
            hideNowPlaying()
        } else if (!nowPlayingHidden) {
            showNowPlaying()
        }
        applyBottomInset()
    }

    companion object {
        /**
         * Whether a destination draws its own top chrome and the shared Material toolbar must
         * be hidden (`supportActionBar?.hide()`). Every Compose screen owns its header, so it
         * belongs here - including the Box Sets list (`collectionListFragment`),
         * `collectionDetailFragment` (issue #10 phase 4B) and `artistDetailFragment` (issue #10
         * phase 4C): each draws a lightweight Taki top row on the dark canvas, no toolbar.
         * `isLightweightHeaderTrackCollection` covers the same treatment for
         * `trackCollectionFragment`'s Genre and Daily Mix modes (issue #10 shell-continuity
         * fix): the legacy list/adapter stays exactly as-is, but the Fragment draws its own
         * [org.moire.ultrasonic.ui.components.TakiScreenHeader] instead of the Material toolbar,
         * same as it does for Compose Album Detail's `isAlbumDetail` - the difference is only
         * *where* the back+title row is drawn (Fragment-owned Compose view here, not the shared
         * `content_navigation_header`), because unlike `isAlbumDetail`/`isLibraryTrackCollection`
         * this mode needs a visible title next to the back arrow. Pure so
         * `NavigationChromeSelectionTest` can lock it; the Activity still applies it in
         * `onDestinationChanged` (only runtime validation proves the `ActionBar.hide()` call).
         */
        fun hidesSupportActionBar(
            destinationId: Int,
            isLibraryTrackCollection: Boolean,
            isAlbumDetail: Boolean,
            isLightweightHeaderTrackCollection: Boolean = false,
        ): Boolean = destinationId in setOf(
            R.id.homeFragment,
            R.id.mainFragment,
            R.id.searchFragment,
            R.id.downloadsFragment,
            R.id.playlistsFragment,
            R.id.playerFragment,
            R.id.lyricsFragment,
            R.id.artistListFragment,
            R.id.artistDetailFragment,
            R.id.albumListFragment,
            R.id.selectGenreFragment,
            R.id.collectionListFragment,
            R.id.collectionDetailFragment,
            R.id.serverSelectorFragment,
            R.id.editServerFragment,
            R.id.aboutFragment,
        ) || isLibraryTrackCollection || isAlbumDetail || isLightweightHeaderTrackCollection ||
            destinationId == R.id.settingsFragment ||
            destinationId == R.id.equalizerFragment
    }
}
