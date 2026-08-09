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
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeActivity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.LocaleHelper
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.ShortcutUtil
import org.moire.ultrasonic.util.Storage
import org.moire.ultrasonic.util.UncaughtExceptionHandler
import org.moire.ultrasonic.util.Util
import timber.log.Timber

// How long to wait after the last keystroke before firing a live search request against the
// server, and the shortest query worth sending -- both exist purely to avoid hammering the
// server with a request per keystroke.
private const val LIVE_SEARCH_DEBOUNCE_MS = 400L
private const val LIVE_SEARCH_MIN_QUERY_LENGTH = 2

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
    private var toolbar: Toolbar? = null
    private var host: NavHostFragment? = null

    // We store the last search string in this variable.
    // Seems a bit like a hack, is there a better way?
    var searchQuery: String? = null

    private var liveSearchJob: Job? = null
    private var lastLiveSearchQuery: String? = null

    private lateinit var appBarConfiguration: AppBarConfiguration

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var currentFragmentId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
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
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment? ?: return

        val navController = host!!.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.mainFragment,
                R.id.searchFragment,
                R.id.downloadsFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        bottomNavigation?.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (ignored: Resources.NotFoundException) {
                destination.id.toString()
            }
            Timber.d("Navigated to $dest")

            currentFragmentId = destination.id
            if (destination.id == R.id.homeFragment || destination.id == R.id.mainFragment) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
            bottomNavigation?.visibility = if (destination.id in setOf(
                    R.id.playerFragment,
                    R.id.settingsFragment,
                    R.id.aboutFragment,
                    R.id.serverSelectorFragment,
                    R.id.editServerFragment,
                    R.id.equalizerFragment,
                    R.id.lyricsFragment
                )
            ) View.GONE else View.VISIBLE
            invalidateOptionsMenu()
            // Handle the hiding of the NowPlaying fragment when the Player is active
            if (currentFragmentId == R.id.playerFragment) {
                hideNowPlaying()
            } else {
                if (!nowPlayingHidden) showNowPlaying()
            }
        }

        // Determine if this is a first run
        val showWelcomeScreen = UApp.instance!!.isFirstRun

        // This is a first run with only the demo entry inside the database
        // We set the active server to the demo one and show the welcome dialog
        if (showWelcomeScreen) {
            showWelcomeDialog()
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
            updateBottomNavigationAvailability()
        }

        // Setup app shortcuts on supported devices, but not on first start, when the server
        // is not configured yet.
        if (!UApp.instance!!.isFirstRun) {
            ShortcutUtil.registerShortcuts(this)
        }

        // Register our options menu
        addMenuProvider(
            searchMenuProvider,
            this,
            Lifecycle.State.RESUMED
        )
        addMenuProvider(
            libraryHubMenuProvider,
            this,
            Lifecycle.State.RESUMED
        )
    }

    private val searchMenuProvider: MenuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            val searchItem = menu.findItem(R.id.action_search) ?: return
            val isSearchDestination = currentFragmentId == R.id.searchFragment
            searchItem.isVisible = isSearchDestination
            if (isSearchDestination) {
                setupSearchField(menu)
                searchItem.expandActionView()
            }
        }

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.search_view_menu, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean = false
    }

    private val libraryHubMenuProvider: MenuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            menu.findItem(R.id.action_library_hub)?.isVisible = currentFragmentId in setOf(
                R.id.downloadsFragment
            )
        }

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.library_hub_action, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            if (item.itemId != R.id.action_library_hub) return false
            showLibraryHub()
            return true
        }
    }

    fun showLibraryHub(anchorView: View? = null) {
        val currentToolbar = toolbar
        val anchor = anchorView
            ?: currentToolbar?.findViewById(R.id.action_library_hub)
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

    fun setupSearchField(menu: Menu) {
        Timber.i("Recreating search field")
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        val searchableInfo = searchManager.getSearchableInfo(this.componentName)
        searchView.setSearchableInfo(searchableInfo)
        searchView.setIconifiedByDefault(false)

        if (searchQuery != null) {
            Timber.e("Found existing search query")
            searchItem.expandActionView()
            searchView.isIconified = false
            searchView.setQuery(searchQuery, false)
            searchView.clearFocus()
            // Restore search text only once!
            searchQuery = null
        }

        // Live filtering as the user types, debounced so we don't hammer the server on every
        // keystroke. Explicit submit (IME search key / voice search) is untouched -- it still
        // goes through the existing ACTION_SEARCH intent flow below, which also saves the query
        // to recent suggestions; live keystrokes deliberately don't, or every partial fragment
        // typed ("b", "be", "bea"...) would pollute that history.
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                scheduleLiveSearch(newText)
                return true
            }
        })
    }

    private fun scheduleLiveSearch(text: String?) {
        val query = text?.trim().orEmpty()
        liveSearchJob?.cancel()

        // An empty field means the user cleared the search -- forget the last query so
        // re-entering the exact same text later (a fresh visit, not a live edit) fires again
        // instead of being silently deduped against a stale value from a previous visit.
        if (query.isEmpty()) {
            lastLiveSearchQuery = null
            return
        }

        if (query.length < LIVE_SEARCH_MIN_QUERY_LENGTH || query == lastLiveSearchQuery) return

        liveSearchJob = lifecycleScope.launch {
            delay(LIVE_SEARCH_DEBOUNCE_MS)
            lastLiveSearchQuery = query

            val navController = findNavController(R.id.nav_host_fragment)
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.searchFragment, true)
                .build()
            navController.navigate(
                NavigationGraphDirections.toSearchFragment(query, false),
                options
            )
        }
    }

    override fun onResume() {
        Timber.d("onResume called")
        super.onResume()

        Storage.reset()

        lifecycleScope.launch(Dispatchers.IO) {
            Storage.checkForErrorsWithCustomRoot()
        }

        updateBottomNavigationAvailability()

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
                searchQuery = intent.getStringExtra(SearchManager.QUERY)
                handleSearchIntent(searchQuery, false)
            }

            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                searchQuery = intent.getStringExtra(SearchManager.QUERY)
                handleSearchIntent(searchQuery, true)
            }
        }
    }

    private fun handleSearchIntent(query: String?, autoPlay: Boolean) {
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
        val service = MusicServiceFactory.getMusicService()
        val musicDirectory = service.getRandomSongs(Settings.maxSongs)

        mediaPlayerManager.addToPlaylist(
            songs = musicDirectory.getTracks(),
            autoPlay = true,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR
        )

        if (Settings.shouldTransitionOnPlayback) {
            currentFragment.findNavController().popBackStack(R.id.playerFragment, true)
            currentFragment.findNavController().navigate(R.id.playerFragment)
        }

        return
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

    private fun exit() {
        Timber.d("User choose to exit the app")

        // Broadcast that the service is being stopped
        RxBus.stopServiceCommandPublisher.onNext(Unit)

        // Broadcast that the app is being shutdown
        RxBus.shutdownCommandPublisher.onNext(Unit)

        finishAndRemoveTask()
    }

    private fun showWelcomeDialog() {
        if (!UApp.instance!!.setupDialogDisplayed) {
            Settings.firstInstalledVersion = Util.getVersionCode(UApp.applicationContext())

            InfoDialog.Builder(this)
                .setTitle(R.string.main_welcome_title)
                .setMessage(R.string.main_welcome_text_demo)
                .setNegativeButton(R.string.main_welcome_cancel) { dialog, _ ->
                    UApp.instance!!.setupDialogDisplayed = true
                    // Go to the settings screen
                    dialog.dismiss()
                    findNavController(R.id.nav_host_fragment).navigate(R.id.serverSelectorFragment)
                }
                .setPositiveButton(R.string.common_ok) { dialog, _ ->
                    UApp.instance!!.setupDialogDisplayed = true
                    // Add the demo server
                    val activeServerProvider: ActiveServerProvider by inject()
                    val demoIndex = serverSettingsModel.addDemoServer()
                    activeServerProvider.setActiveServerByIndex(demoIndex)
                    findNavController(R.id.nav_host_fragment).navigate(R.id.homeFragment)
                    dialog.dismiss()
                }.show()
        }
    }

    private fun setUncaughtExceptionHandler() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        if (handler !is UncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler(this))
        }
    }

    private fun showNowPlaying() {
        if (!Settings.showNowPlaying) {
            hideNowPlaying()
            return
        }

        // The logic for nowPlayingHidden is that the user can dismiss NowPlaying with a gesture,
        // and when the MediaPlayerService requests that it should be shown, it returns
        nowPlayingHidden = false
        // Do not show for Player fragment
        if (currentFragmentId == R.id.playerFragment) {
            hideNowPlaying()
            return
        }

        if (nowPlayingView != null) {
            val playerState: Int = mediaPlayerManager.playbackState
            if (playerState == STATE_BUFFERING || playerState == STATE_READY) {
                val item: MediaItem? = mediaPlayerManager.currentMediaItem
                if (item != null) {
                    nowPlayingView?.visibility = View.VISIBLE
                }
            } else {
                hideNowPlaying()
            }
        }
    }

    private fun hideNowPlaying() {
        nowPlayingView?.visibility = View.GONE
    }

    private fun updateBottomNavigationAvailability() {
        val isOnline = !ActiveServerProvider.isOffline()
        bottomNavigation?.menu?.findItem(R.id.downloadsFragment)?.isVisible = isOnline
    }
}
