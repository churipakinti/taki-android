/*
 * GenericListModel.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.CommunicationError

/**
 * An abstract Model, which can be extended to retrieve a list of items from the API
 */
open class GenericListModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    val activeServerProvider: ActiveServerProvider by inject()

    val activeServer: ServerSetting
        get() = activeServerProvider.getActiveServer()

    val context: Context
        get() = getApplication<Application>().applicationContext

    var currentListIsSortable = true
    var showHeader = true

    val musicFolders: MutableLiveData<List<MusicFolder>> = MutableLiveData(listOf())

    // Guards against a load started against a previous server (or a previous rapid refresh tap)
    // landing after a newer one and overwriting its results - same shape as the already-fixed
    // stale-response bugs in filter/sort/pagination.
    private var loadJob: Job? = null

    open fun showSelectFolderHeader(): Boolean = false

    /**
     * Helper function to check online status
     */
    fun isOffline(): Boolean = ActiveServerProvider.isOffline()

    /**
     * Refreshes the cached items from the server
     */
    fun refresh(swipe: SwipeRefreshLayout) {
        backgroundLoadFromServer(true, swipe)
    }

    /**
     * Trigger a load() and notify the UI that we are loading
     */
    fun backgroundLoadFromServer(refresh: Boolean, swipe: SwipeRefreshLayout) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            swipe.isRefreshing = true
            try {
                loadFromServer(refresh, swipe)
            } finally {
                swipe.isRefreshing = false
            }
        }
    }

    /**
     * Calls the load() function with error handling
     */
    private suspend fun loadFromServer(refresh: Boolean, swipe: SwipeRefreshLayout) {
        withContext(Dispatchers.IO) {
            val musicService = MusicServiceFactory.getMusicService()
            val isOffline = ActiveServerProvider.isOffline()
            val useId3Tags = ActiveServerProvider.shouldUseId3Tags()

            try {
                load(isOffline, useId3Tags, musicService, refresh)
            } catch (all: Exception) {
                handleException(all, swipe.context)
            }
        }
    }

    private fun handleException(exception: Exception, context: Context) {
        Handler(Looper.getMainLooper()).post {
            CommunicationError.handleError(exception, context)
        }
    }

    /**
     * This is the central function you need to implement if you want to extend this class
     */
    open suspend fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean
    ) {
        // Update the list of available folders if enabled
        @Suppress("ComplexCondition")
        if (showSelectFolderHeader() && !isOffline && !useId3Tags && refresh) {
            musicFolders.postValue(
                musicService.getMusicFolders(refresh)
            )
        }
    }
}
