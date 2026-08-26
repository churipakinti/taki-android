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
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
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
        // later tap on Home into a no-op reselect). Found via bug report + on-device
        // testing (2026-08-11), see CHANGES.md. Popping back to the tab's root id (already
        // proven safe here, it's what the reselect handling below already did) is
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

            // The nav graph is flat, so AndroidX's setupWithNavController() only checks
            // destination.id against the 4 top-level menu items themselves - it can't know that
            // e.g. trackCollectionFragment(libraryRoot=true) is "Songs", reached only from
            // Library. For any destination it doesn't recognize it leaves the bottom nav's
            // selection exactly as it was, so browsing into Library's own sub-screens left
            // "Home" highlighted (whatever tab was last matched before, not where the content
            // actually lives) - found via on-device testing (2026-08-10), see CHANGES.md.
            // Only destinations with a single, unambiguous entry point are corrected here;
            // trackCollectionFragment/artistDetailFragment used for album/artist/genre/playlist
            // browsing are reachable from both Home and Library depending on how the user got
            // there, so they're deliberately left alone rather than guessed at.
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
            val usesContentHeader = destination.id in setOf(
                R.id.homeFragment,
                R.id.mainFragment,
                R.id.searchFragment,
                R.id.downloadsFragment,
                R.id.playlistsFragment,
                R.id.playerFragment,
                R.id.lyricsFragment,
                R.id.artistListFragment,
                R.id.albumListFragment,
                R.id.selectGenreFragment,
                R.id.serverSelectorFragment,
                R.id.editServerFragment,
                R.id.aboutFragment
            ) || isLibraryTrackCollection || isAlbumDetail ||
                destination.id == R.id.settingsFragment ||
                destination.id == R.id.equalizerFragment
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

    // bottomNavigation and nowPlayingView (the mini player) are the two views that can sit
    // directly on the display's bottom edge, depending on which one is visible on the current
    // destination. Edge-to-edge (enforced from Android 15/targetSdk 35 on) draws app content
    // behind the system navigation bar, so whichever of the two is currently the bottom-most
    // visible view must absorb that inset as its own padding, or its content/controls end up
    // rendered underneath the system's back/home/recents buttons - this was reported on Lyrics
    // (bottomNavigation hidden, mini player visible).
    // On destinations that hide both (Settings/About/Equalizer/ServerSelector/EditServer, when
    // nothing is playing so the mini player also isn't shown), neither absorbs the inset, and the
    // fragment's own bottom-anchored content is what's left exposed - found on EditServerFragment,
    // whose Test connection/Save buttons rendered underneath the system nav bar. In that case the
    // nav host container itself needs the padding instead.
    /*
     * How much a fragment's own scrollable content should pad its bottom by so the last item
     * can clear whichever of bottomNavigation/nowPlayingView is currently docked at the screen
     * edge -- those are separate views layered on top of the content, not something a
     * RecyclerView/GridView's own system-inset padding accounts for on its own. Each view's
     * height already includes the system nav bar inset when it is the bottom-most one (see
     * applyBottomInset()), so this must not add navigationBarBottomInset a second time.
     */
    fun getContentBottomInset(): Int {
        val bottomNavVisible = bottomNavigation?.visibility == View.VISIBLE
        val nowPlayingVisible = nowPlayingView?.visibility == View.VISIBLE
        val nowPlayingHeight = if (nowPlayingVisible) nowPlayingView?.height ?: 0 else 0
        val bottomNavHeight = if (bottomNavVisible) bottomNavigation?.height ?: 0 else 0
        return nowPlayingHeight + bottomNavHeight
    }

    private fun applyBottomInset() {
        val bottomNavVisible = bottomNavigation?.visibility == View.VISIBLE
        val nowPlayingVisible = nowPlayingView?.visibility == View.VISIBLE
        bottomNavigation?.updatePadding(
            bottom = if (bottomNavVisible) navigationBarBottomInset else 0
        )
        nowPlayingView?.updatePadding(
            bottom = if (!bottomNavVisible && nowPlayingVisible) navigationBarBottomInset else 0
        )
        navHostContainer?.updatePadding(
            bottom = if (!bottomNavVisible && !nowPlayingVisible) navigationBarBottomInset else 0
        )
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
}
