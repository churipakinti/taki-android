/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package org.moire.ultrasonic.service

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.qualifier.named
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.di.OFFLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.ONLINE_MUSIC_SERVICE
import org.moire.ultrasonic.di.musicServiceModule
import org.moire.ultrasonic.util.PerfMetrics
import timber.log.Timber

/*
 * TODO: When resetMusicService is called, a large number of classes are completely newly instantiated,
 * which take quite a bit of time.
 *
 * Instead it would probably be faster to listen to Rx
 */
object MusicServiceFactory : KoinComponent {
    private var firstResolutionMeasured = false

    @JvmStatic
    fun getMusicService(): MusicService {
        val perfToken = if (!firstResolutionMeasured) {
            firstResolutionMeasured = true
            PerfMetrics.start("first_music_service_resolution")
        } else {
            null
        }
        val service = if (ActiveServerProvider.isOffline()) {
            get<MusicService>(named(OFFLINE_MUSIC_SERVICE))
        } else {
            get<MusicService>(named(ONLINE_MUSIC_SERVICE))
        }
        if (perfToken != null) PerfMetrics.end("first_music_service_resolution", perfToken)
        return service
    }

    /**
     * Resets [MusicService] to initial state, so on next call to [.getMusicService]
     * it will return updated instance of it.
     */
    @JvmStatic
    fun resetMusicService() {
        Timber.i("Regenerating Koin Music Service Module")
        val perfToken = PerfMetrics.start("library_switch")
        unloadKoinModules(musicServiceModule)
        loadKoinModules(musicServiceModule)
        PerfMetrics.end("library_switch", perfToken)
    }
}
