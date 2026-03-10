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
import android.content.res.ColorStateList
import android.content.res.Resources
import android.media.AudioManager
import android.os.Bundle
import android.provider.MediaStore
import android.provider.SearchRecentSuggestions
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeActivity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSettingDao
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
import org.moire.ultrasonic.util.ServerColor
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
    private var videoMenuItem: MenuItem? = null
    private var chatMenuItem: MenuItem? = null
    private var bookmarksMenuItem: MenuItem? = null
    private var sharesMenuItem: MenuItem? = null
    private var podcastsMenuItem: MenuItem? = null
    private var playlistsMenuItem: MenuItem? = null
    private var downloadsMenuItem: MenuItem? = null

    private var nowPlayingView: FragmentContainerView? = null
    private var nowPlayingHidden = false
    private var navigationView: NavigationView? = null
    private var drawerLayout: DrawerLayout? = null
    private var host: NavHostFragment? = null
    private var selectServerButton: MaterialButton? = null
    private var selectServerDropdownImage: ImageView? = null
    private var headerBackgroundImage: ImageView? = null

    // We store the last search string in this variable.
    // Seems a bit like a hack, is there a better way?
    var searchQuery: String? = null

    private lateinit var appBarConfiguration: AppBarConfiguration

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val activeServerProvider: ActiveServerProvider by inject()
    private val serverRepository: ServerSettingDao by inject()

    private var currentFragmentId: Int = 0
    private var cachedServerCount: Int = 0

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
        navigationView = findViewById(R.id.nav_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        setupDrawerLayout(drawerLayout!!)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment? ?: return

        val navController = host!!.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.mainFragment,
                R.id.mediaLibraryFragment,
                R.id.searchFragment,
                R.id.playlistsFragment,
                R.id.downloadsFragment,
                R.id.sharesFragment,
                R.id.bookmarksFragment,
                R.id.chatFragment,
                R.id.podcastFragment,
                R.id.settingsFragment,
                R.id.aboutFragment,
                R.id.playerFragment
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        setupNavigationMenu(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (ignored: Resources.NotFoundException) {
                destination.id.toString()
            }
            Timber.d("Navigated to $dest")

            currentFragmentId = destination.id
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

        rxBusSubscription += RxBus.themeChangedEventObservable.subscribe {
            recreate()
        }

        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            updateNavigationHeaderForServer()
            setMenuForServerCapabilities()
        }

        serverRepository.liveServerCount().observe(this) { count ->
            cachedServerCount = count ?: 0
            updateNavigationHeaderForServer()
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
    }

    private val searchMenuProvider: MenuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            setupSearchField(menu)
        }

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.search_view_menu, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean = false
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
    }

    private fun setupDrawerLayout(drawerLayout: DrawerLayout) {
        // Set initial state passed on drawer state
        closeNavigationDrawerOnBack.isEnabled = drawerLayout.isOpen

        // Add the back press listener
        onBackPressedDispatcher.addCallback(this, closeNavigationDrawerOnBack)

        // Listen to changes in the drawer state and enable the back press listener accordingly.
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Nothing
            }

            override fun onDrawerOpened(drawerView: View) {
                closeNavigationDrawerOnBack.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                closeNavigationDrawerOnBack.isEnabled = false
            }

            override fun onDrawerStateChanged(newState: Int) {
                // Nothing
            }
        })
    }

    override fun onResume() {
        Timber.d("onResume called")
        super.onResume()

        Storage.reset()

        lifecycleScope.launch(Dispatchers.IO) {
            Storage.checkForErrorsWithCustomRoot()
        }

        setMenuForServerCapabilities()

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

    private fun updateNavigationHeaderForServer() {
        val activeServer = activeServerProvider.getActiveServer()

        if (cachedServerCount == 0) {
            selectServerButton?.text = getString(R.string.main_setup_server, activeServer.name)
        } else {
            selectServerButton?.text = activeServer.name
        }

        val foregroundColor =
            ServerColor.getForegroundColor(this, activeServer.color)
        val backgroundColor =
            ServerColor.getBackgroundColor(this, activeServer.color)

        if (activeServer.index == 0) {
            selectServerButton?.icon =
                ContextCompat.getDrawable(this, R.drawable.ic_menu_screen_on_off)
        } else {
            selectServerButton?.icon =
                ContextCompat.getDrawable(this, R.drawable.ic_menu_select_server)
        }

        selectServerButton?.iconTint = ColorStateList.valueOf(foregroundColor)
        selectServerButton?.setTextColor(foregroundColor)
        selectServerDropdownImage?.imageTintList = ColorStateList.valueOf(foregroundColor)
        headerBackgroundImage?.setBackgroundColor(backgroundColor)
    }

    private fun setupNavigationMenu(navController: NavController) {
        navigationView?.setupWithNavController(navController)

        // The fragments which expect SafeArgs need to be navigated to with SafeArgs (even when
        // they are empty)!
        navigationView?.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.mediaLibraryFragment -> {
                    navController.navigate(NavigationGraphDirections.toMediaLibrary())
                }

                R.id.bookmarksFragment -> {
                    navController.navigate(NavigationGraphDirections.toBookmarks())
                }

                R.id.trackCollectionFragment -> {
                    navController.navigate(
                        NavigationGraphDirections.toTrackCollection(
                            getVideos = true
                        )
                    )
                }

                R.id.menu_exit -> {
                    setResult(Constants.RESULT_CLOSE_ALL)
                    mediaPlayerManager.onDestroy()
                    finish()
                    exit()
                }

                else -> navController.navigate(it.itemId)
            }
            drawerLayout?.closeDrawer(GravityCompat.START)
            true
        }

        chatMenuItem = navigationView?.menu?.findItem(R.id.chatFragment)
        bookmarksMenuItem = navigationView?.menu?.findItem(R.id.bookmarksFragment)
        sharesMenuItem = navigationView?.menu?.findItem(R.id.sharesFragment)
        podcastsMenuItem = navigationView?.menu?.findItem(R.id.podcastFragment)
        playlistsMenuItem = navigationView?.menu?.findItem(R.id.playlistsFragment)
        downloadsMenuItem = navigationView?.menu?.findItem(R.id.downloadsFragment)
        videoMenuItem = navigationView?.menu?.findItem(R.id.trackCollectionFragment)

        selectServerButton =
            navigationView?.getHeaderView(0)?.findViewById(R.id.header_select_server)
        selectServerDropdownImage =
            navigationView?.getHeaderView(0)?.findViewById(R.id.edit_server_button)

        val onClick: (View) -> Unit = {
            if (drawerLayout?.isDrawerVisible(GravityCompat.START) == true) {
                this.drawerLayout?.closeDrawer(GravityCompat.START)
            }
            navController.navigate(R.id.serverSelectorFragment)
        }

        selectServerButton?.setOnClickListener(onClick)
        selectServerDropdownImage?.setOnClickListener(onClick)

        headerBackgroundImage =
            navigationView?.getHeaderView(0)?.findViewById(R.id.img_header_bg)
    }

    private val closeNavigationDrawerOnBack = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            drawerLayout?.closeDrawer(GravityCompat.START)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val retValue = super.onCreateOptionsMenu(menu)
        if (navigationView == null) {
            menuInflater.inflate(R.menu.navigation_drawer, menu)
            return true
        }
        return retValue
    }

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
        // delegated — either navigating "up" in the back stack, or opening the drawer if
        // we're at a top-level destination.
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
                    findNavController(R.id.nav_host_fragment).navigate(R.id.mainFragment)
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

    private fun setMenuForServerCapabilities() {
        val isOnline = !ActiveServerProvider.isOffline()
        val activeServer = activeServerProvider.getActiveServer()

        // Note: Offline capabilities are defined in ActiveServerProvider, OFFLINE_DB.
        // If you add Offline support for some of these features you need
        // to switch the boolean to true there.
        chatMenuItem?.isVisible = activeServer.chatSupport != false
        bookmarksMenuItem?.isVisible = activeServer.bookmarkSupport != false
        sharesMenuItem?.isVisible = activeServer.shareSupport != false
        podcastsMenuItem?.isVisible = activeServer.podcastSupport != false
        playlistsMenuItem?.isVisible = isOnline
        downloadsMenuItem?.isVisible = isOnline
        videoMenuItem?.isVisible = activeServer.videoSupport != false
    }
}
