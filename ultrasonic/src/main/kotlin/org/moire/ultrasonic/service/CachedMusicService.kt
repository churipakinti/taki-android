/*
 * CachedMusicService.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.Pair
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.MetaDatabase
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.AlbumInfo
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistInfo
import org.moire.ultrasonic.domain.Bookmark
import org.moire.ultrasonic.domain.ChatMessage
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.domain.Lyrics
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.PodcastsChannel
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Share
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.domain.UserInfo
import org.moire.ultrasonic.util.LRUCache
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.TimeLimitedCache
import org.moire.ultrasonic.util.Util
import timber.log.Timber

@Suppress("TooManyFunctions")
class CachedMusicService(private val musicService: MusicService) :
    MusicService,
    KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()
    private var metaDatabase: MetaDatabase = activeServerProvider.getActiveMetaDatabase()

    // Old style TimeLimitedCache
    private val cachedMusicDirectories: LRUCache<String, TimeLimitedCache<MusicDirectory?>>
    private val cachedUserInfo: LRUCache<String, TimeLimitedCache<UserInfo?>>
    private val cachedLicenseValid = TimeLimitedCache<Boolean>(120, TimeUnit.SECONDS)
    private val cachedPlaylists = TimeLimitedCache<List<Playlist>?>(3600, TimeUnit.SECONDS)
    private val cachedPodcastsChannels =
        TimeLimitedCache<List<PodcastsChannel>?>(3600, TimeUnit.SECONDS)
    private val cachedGenres = TimeLimitedCache<List<Genre>>(10 * 3600, TimeUnit.SECONDS)

    // New Room Database
    private var cachedArtists = metaDatabase.artistDao()
    private var cachedAlbums = metaDatabase.albumDao()
    private var cachedIndexes = metaDatabase.indexDao()
    private var cachedTracks = metaDatabase.trackDao()
    private val cachedMusicFolders = metaDatabase.musicFoldersDao()

    // Single-flight locks (Fase 3): two concurrent callers for the same album (e.g. the main UI
    // and Android Auto/MediaLibrarySessionCallback browsing the same album at once) share one
    // network call instead of both firing it. Separate locks per method since getAlbum() and
    // getAlbumAsDir() read/write different Room tables for the same id and shouldn't serialize
    // against each other.
    private val albumFetchLock = KeyedLock()
    private val albumDirFetchLock = KeyedLock()

    private var restUrl: String? = null
    private var cachedMusicFolderId: String? = null

    @Throws(Exception::class)
    override fun ping() {
        checkSettingsChanged()
        musicService.ping()
    }

    @Throws(Exception::class)
    override fun isLicenseValid(): Boolean {
        checkSettingsChanged()
        var isValid = cachedLicenseValid.get()
        if (isValid == null) {
            isValid = musicService.isLicenseValid()
            cachedLicenseValid.set(isValid)
        }
        return isValid
    }

    @Throws(Exception::class)
    override suspend fun getMusicFolders(refresh: Boolean): List<MusicFolder> {
        checkSettingsChanged()
        if (refresh) {
            cachedMusicFolders.clear()
        }
        var result = cachedMusicFolders.get()

        if (result.isEmpty()) {
            result = musicService.getMusicFolders(refresh)
            cachedMusicFolders.set(result)
        }
        return result
    }

    /*
     * On refresh, getIndexes() may return null to mean "server confirms nothing changed since
     * last time" (see RESTMusicService) -- in that case the existing cached indexes are kept
     * as-is instead of being cleared, so a pull-to-refresh that finds no changes doesn't
     * needlessly wipe the local index (or the unrelated folder-listing cache) while waiting on
     * a full re-fetch that was never actually necessary.
     *
     * Incremental sync (ifModifiedSince) is only requested when there's already a non-empty
     * cache for this exact musicFolderId to fall back to -- e.g. switching to a music folder
     * that was never fetched before must always do a full fetch, even though the server's
     * stored lastModified (saved per-server, not per-folder, since it reflects library-wide
     * changes) might say "nothing changed" relative to some *other* folder's last check. See
     * TAKI_CODE_OPTIMIZATION_PLAN.md Fase 3 ("invalidar correctamente después de cambios que
     * afecten el índice").
     */
    @Throws(Exception::class)
    override suspend fun getIndexes(musicFolderId: String?, refresh: Boolean): List<Index> {
        checkSettingsChanged()

        var indexes: List<Index> = if (musicFolderId == null) {
            cachedIndexes.get()
        } else {
            cachedIndexes.get(musicFolderId)
        }

        if (refresh || indexes.isEmpty()) {
            val useIncrementalSync = refresh && indexes.isNotEmpty()
            val fetched = musicService.getIndexes(musicFolderId, useIncrementalSync)
            if (fetched != null) {
                // Only clear the folder that was actually re-fetched -- other folders' cached
                // indexes weren't part of this call and must survive it untouched.
                if (musicFolderId == null) {
                    cachedIndexes.clear()
                } else {
                    cachedIndexes.clearByFolder(musicFolderId)
                }
                cachedIndexes.upsert(fetched)
                cachedMusicDirectories.clear()
                indexes = fetched
            }
        }

        return indexes
    }

    @Throws(Exception::class)
    override suspend fun getArtists(refresh: Boolean): List<Artist> {
        checkSettingsChanged()

        if (refresh) {
            cachedArtists.clear()
        }

        var result = cachedArtists.get()

        if (result.isEmpty()) {
            result = musicService.getArtists(refresh)
            cachedArtists.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override suspend fun getMusicDirectory(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        checkSettingsChanged()
        var cache = if (refresh) null else cachedMusicDirectories[id]
        var dir = cache?.get()
        if (dir == null) {
            dir = musicService.getMusicDirectory(id, name, refresh)
            cache = TimeLimitedCache(
                Settings.DIRECTORY_CACHE_TIME.toLong(),
                TimeUnit.SECONDS
            )
            cache.set(dir)
            cachedMusicDirectories.put(id, cache)
        }
        return dir
    }

    /*
     * Retrieves all albums of the provided artist.
     * Cached in the RoomDB
     */
    @Throws(Exception::class)
    override suspend fun getAlbumsOfArtist(
        id: String,
        name: String?,
        refresh: Boolean
    ): List<Album> {
        checkSettingsChanged()

        var result: List<Album>

        result = if (refresh) {
            cachedAlbums.clearByArtist(id)
            listOf()
        } else {
            cachedAlbums.byArtist(id)
        }

        if (result.isEmpty()) {
            result = musicService.getAlbumsOfArtist(id, name, refresh)
            cachedAlbums.upsert(result)
        }
        return result
    }

    override suspend fun getArtistInfo(id: String): ArtistInfo? = musicService.getArtistInfo(id)

    override suspend fun getAlbumInfo(id: String): AlbumInfo? = musicService.getAlbumInfo(id)

    override suspend fun getTopSongs(artistName: String, count: Int): List<Track> =
        musicService.getTopSongs(artistName, count)

    /*
     * Retrieves the track listing of the given album.
     * Cached in the RoomDB, same as getAlbum() -- getAlbumAsDir() is backed by the same
     * getAlbum.view endpoint (see RESTMusicService), which per the Subsonic API only ever
     * returns <song> children, so it's safe to persist as plain Tracks (unlike
     * getMusicDirectory(), which can mix in sub-folders and stays on the old TimeLimitedCache).
     */
    @Throws(Exception::class)
    override suspend fun getAlbumAsDir(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory = albumDirFetchLock.withLock(id) {
        checkSettingsChanged()

        if (refresh) {
            cachedTracks.clearByAlbum(id)
        }

        var tracks = cachedTracks.byAlbum(id)

        if (tracks.isEmpty()) {
            val dir = musicService.getAlbumAsDir(id, name, refresh)
            tracks = dir.getTracks()
            if (tracks.isNotEmpty()) cachedTracks.upsert(tracks)
            return@withLock dir
        }

        MusicDirectory().apply {
            this.name = name
            addAll(tracks)
        }
    }

    @Throws(Exception::class)
    override suspend fun getAlbum(id: String, name: String?, refresh: Boolean): Album? =
        albumFetchLock.withLock(id) {
            checkSettingsChanged()
            var cache = if (refresh) null else cachedAlbums.get(id)
            if (cache == null) {
                try {
                    cache = musicService.getAlbum(id, name, refresh)
                } catch (e: Exception) {
                    // Falls back to no album data (same as before); the difference is that the
                    // failure is now on record instead of vanishing silently. See
                    // TAKI_CODE_OPTIMIZATION_PLAN.md Fase 2.
                    Timber.w(e, "getAlbum failed for id=%s, falling back to cached/null", id)
                }

                cache?.let { cachedAlbums.upsert(it) }
            }
            cache
        }

    @Throws(Exception::class)
    override suspend fun search(criteria: SearchCriteria): SearchResult? =
        musicService.search(criteria)

    @Throws(Exception::class)
    override suspend fun getPlaylist(id: String, name: String): MusicDirectory =
        musicService.getPlaylist(id, name)

    @Throws(Exception::class)
    override fun getPodcastsChannels(refresh: Boolean): List<PodcastsChannel> {
        checkSettingsChanged()
        var result = if (refresh) null else cachedPodcastsChannels.get()
        if (result == null) {
            result = musicService.getPodcastsChannels(refresh)
            cachedPodcastsChannels.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override fun getPodcastEpisodes(podcastChannelId: String?): MusicDirectory? =
        musicService.getPodcastEpisodes(podcastChannelId)

    @Throws(Exception::class)
    override suspend fun getPlaylists(refresh: Boolean): List<Playlist> {
        checkSettingsChanged()
        var result = if (refresh) null else cachedPlaylists.get()
        if (result == null) {
            result = musicService.getPlaylists(refresh)
            cachedPlaylists.set(result)
        }
        return result
    }

    @Throws(Exception::class)
    override suspend fun createPlaylist(id: String?, name: String?, tracks: List<Track>) {
        cachedPlaylists.clear()
        musicService.createPlaylist(id, name, tracks)
    }

    @Throws(Exception::class)
    override suspend fun deletePlaylist(id: String) {
        musicService.deletePlaylist(id)
    }

    @Throws(Exception::class)
    override suspend fun updatePlaylist(id: String, name: String?, comment: String?, pub: Boolean) {
        musicService.updatePlaylist(id, name, comment, pub)
    }

    @Throws(Exception::class)
    override fun getLyrics(artist: String, title: String): Lyrics? =
        musicService.getLyrics(artist, title)

    @Throws(Exception::class)
    override fun getLyricsBySongId(id: String): Lyrics? = musicService.getLyricsBySongId(id)

    @Throws(Exception::class)
    override fun scrobble(id: String, submission: Boolean) {
        musicService.scrobble(id, submission)
    }

    @Throws(Exception::class)
    override suspend fun getAlbumList(
        type: AlbumListType,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): List<Album> = musicService.getAlbumList(type, size, offset, musicFolderId)

    @Throws(Exception::class)
    override suspend fun getAlbumList2(
        type: AlbumListType,
        size: Int,
        offset: Int,
        genre: String?,
        musicFolderId: String?
    ): List<Album> = musicService.getAlbumList2(type, size, offset, genre, musicFolderId)

    @Throws(Exception::class)
    override suspend fun getRandomSongs(size: Int): MusicDirectory =
        musicService.getRandomSongs(size)

    @Throws(Exception::class)
    override suspend fun getStarred(): SearchResult = musicService.getStarred()

    @Throws(Exception::class)
    override suspend fun getStarred2(): SearchResult = musicService.getStarred2()

    @Throws(Exception::class)
    override fun getDownloadInputStream(
        song: Track,
        offset: Long,
        maxBitrate: Int?,
        save: Boolean
    ): Pair<InputStream, Boolean> =
        musicService.getDownloadInputStream(song, offset, maxBitrate, save)

    @Throws(Exception::class)
    override fun getStreamUrl(id: String, maxBitRate: Int?, format: String?): String? =
        musicService.getStreamUrl(id, maxBitRate, format)

    override fun isJukeboxAvailable(): Boolean = musicService.isJukeboxAvailable()

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(ids: List<String>): JukeboxStatus =
        musicService.updateJukeboxPlaylist(ids)

    @Throws(Exception::class)
    override fun skipJukebox(index: Int, offsetSeconds: Int): JukeboxStatus =
        musicService.skipJukebox(index, offsetSeconds)

    @Throws(Exception::class)
    override fun stopJukebox(): JukeboxStatus = musicService.stopJukebox()

    @Throws(Exception::class)
    override fun clearJukebox(): JukeboxStatus = musicService.clearJukebox()

    @Throws(Exception::class)
    override fun startJukebox(): JukeboxStatus = musicService.startJukebox()

    @Throws(Exception::class)
    override fun getJukeboxStatus(): JukeboxStatus = musicService.getJukeboxStatus()

    @Throws(Exception::class)
    override fun setJukeboxGain(gain: Float): JukeboxStatus = musicService.setJukeboxGain(gain)

    @Synchronized
    private fun checkSettingsChanged() {
        val newUrl = activeServerProvider.getRestUrl(null)
        val newFolderId = activeServerProvider.getActiveServer().musicFolderId
        if (!Util.equals(newUrl, restUrl) || !Util.equals(cachedMusicFolderId, newFolderId)) {
            // Switch database
            metaDatabase = activeServerProvider.getActiveMetaDatabase()
            cachedArtists = metaDatabase.artistDao()
            cachedAlbums = metaDatabase.albumDao()
            cachedIndexes = metaDatabase.indexDao()
            cachedTracks = metaDatabase.trackDao()

            // Clear in memory caches
            cachedMusicDirectories.clear()
            cachedLicenseValid.clear()
            cachedPlaylists.clear()
            cachedGenres.clear()
            cachedUserInfo.clear()

            // Set the cache keys
            restUrl = newUrl
            cachedMusicFolderId = newFolderId
        }
    }

    @Throws(Exception::class)
    override suspend fun star(id: String?, albumId: String?, artistId: String?) {
        musicService.star(id, albumId, artistId)
    }

    @Throws(Exception::class)
    override suspend fun unstar(id: String?, albumId: String?, artistId: String?) {
        musicService.unstar(id, albumId, artistId)
    }

    @Throws(Exception::class)
    override fun setRating(id: String, rating: Int) {
        musicService.setRating(id, rating)
    }

    @Throws(Exception::class)
    override suspend fun getGenres(refresh: Boolean): List<Genre> {
        checkSettingsChanged()
        if (refresh) {
            cachedGenres.clear()
        }
        var result = cachedGenres.get()
        if (result == null) {
            result = musicService.getGenres(refresh)
            cachedGenres.set(result)
        }

        val sorted = result.toMutableList()
        sorted.sortWith { genre, genre2 ->
            genre.name.compareTo(
                genre2.name,
                ignoreCase = true
            )
        }
        return sorted
    }

    @Throws(Exception::class)
    override suspend fun getSongsByGenre(genre: String, count: Int, offset: Int): MusicDirectory =
        musicService.getSongsByGenre(genre, count, offset)

    @Throws(Exception::class)
    override fun getShares(refresh: Boolean): List<Share> = musicService.getShares(refresh)

    @Throws(Exception::class)
    override fun getChatMessages(since: Long?): List<ChatMessage?>? =
        musicService.getChatMessages(since)

    @Throws(Exception::class)
    override fun addChatMessage(message: String) {
        musicService.addChatMessage(message)
    }

    @Throws(Exception::class)
    override fun getBookmarks(): List<Bookmark> = musicService.getBookmarks()

    @Throws(Exception::class)
    override fun deleteBookmark(id: String) {
        musicService.deleteBookmark(id)
    }

    @Throws(Exception::class)
    override fun createBookmark(id: String, position: Int) {
        musicService.createBookmark(id, position)
    }

    @Throws(Exception::class)
    override fun getVideos(refresh: Boolean): MusicDirectory? {
        checkSettingsChanged()
        var cache =
            if (refresh) null else cachedMusicDirectories[CACHE_KEY_VIDEOS]
        var dir = cache?.get()
        if (dir == null) {
            dir = musicService.getVideos(refresh)
            cache = TimeLimitedCache(
                Settings.DIRECTORY_CACHE_TIME.toLong(),
                TimeUnit.SECONDS
            )
            cache.set(dir)
            cachedMusicDirectories.put(CACHE_KEY_VIDEOS, cache)
        }
        return dir
    }

    @Throws(Exception::class)
    override fun getUser(username: String): UserInfo {
        checkSettingsChanged()
        var cache = cachedUserInfo[username]
        var userInfo = cache?.get()
        if (userInfo == null) {
            userInfo = musicService.getUser(username)
            cache = TimeLimitedCache(
                Settings.DIRECTORY_CACHE_TIME.toLong(),
                TimeUnit.SECONDS
            )
            cache.set(userInfo)
            cachedUserInfo.put(username, cache)
        }
        return userInfo
    }

    @Throws(Exception::class)
    override fun createShare(ids: List<String>, description: String?, expires: Long?): List<Share> =
        musicService.createShare(ids, description, expires)

    @Throws(Exception::class)
    override fun deleteShare(id: String) {
        musicService.deleteShare(id)
    }

    @Throws(Exception::class)
    override fun updateShare(id: String, description: String?, expires: Long?) {
        musicService.updateShare(id, description, expires)
    }

    companion object {
        private const val MUSIC_DIR_CACHE_SIZE = 100
        const val CACHE_KEY_VIDEOS = "VIDEOS"
    }

    init {
        cachedMusicDirectories = LRUCache(MUSIC_DIR_CACHE_SIZE)
        cachedUserInfo = LRUCache(MUSIC_DIR_CACHE_SIZE)
    }
}
