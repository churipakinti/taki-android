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
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeActivity
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.LocaleHelper
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
    private var contentBackButton: View? = null
    private var contentNavigationHeader: View? = null
    private var toolbar: Toolbar? = null
    private var host: NavHostFragment? = null

    private lateinit var appBarConfiguration: AppBarConfiguration

    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var currentFragmentId: Int = 0
    private var imeVisible = false
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
        contentBackButton = findViewById(R.id.content_back_button)
        contentNavigationHeader = findViewById(R.id.content_navigation_header)
        toolbar = findViewById(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigation_root)) { _, insets ->
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
                R.id.searchFragment,
                R.id.downloadsFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        bottomNavigation?.setupWithNavController(navController)
        bottomNavigation?.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id != item.itemId) {
                if (!navController.popBackStack(item.itemId, false)) {
                    navController.navigate(item.itemId)
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, arguments ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (ignored: Resources.NotFoundException) {
                destination.id.toString()
            }
            Timber.d("Navigated to $dest")

            currentFragmentId = destination.id
            val isLibraryTrackCollection = destination.id == R.id.trackCollectionFragment &&
                (arguments?.getBoolean("libraryRoot") == true ||
                    arguments?.getBoolean("getStarred") == true)
            val isAlbumDetail = destination.id == R.id.trackCollectionFragment &&
                arguments?.getBoolean("isAlbum") == true
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
                R.id.aboutFragment
            ) || isLibraryTrackCollection || isAlbumDetail ||
                destination.id == R.id.settingsFragment ||
                destination.id == R.id.equalizerFragment
            contentNavigationHeader?.visibility =
                if (showsContentBackButton) View.VISIBLE else View.GONE
            invalidateOptionsMenu()
            updateChromeVisibility()
        }

        // Determine if this is a first run
        val showWelcomeScreen = UApp.instance!!.isFirstRun

        // On first run, invite the listener to connect a collection. Taki deliberately ships
        // without a bundled demo or third-party credentials.
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
                .setMessage(R.string.main_welcome_text)
                .setNegativeButton(R.string.main_welcome_not_now) { dialog, _ ->
                    UApp.instance!!.setupDialogDisplayed = true
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.main_welcome_add_collection) { dialog, _ ->
                    UApp.instance!!.setupDialogDisplayed = true
                    findNavController(R.id.nav_host_fragment).navigate(
                        R.id.editServerFragment,
                        Bundle().apply { putInt("index", -1) }
                    )
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
                }
            } else {
                hideNowPlaying()
            }
        }
    }

    private fun hideNowPlaying() {
        nowPlayingView?.visibility = View.GONE
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
    }

    private fun updateBottomNavigationAvailability() {
        val isOnline = !ActiveServerProvider.isOffline()
        bottomNavigation?.menu?.findItem(R.id.downloadsFragment)?.isVisible = isOnline
    }
}
