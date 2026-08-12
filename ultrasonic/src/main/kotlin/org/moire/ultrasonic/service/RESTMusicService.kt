/*
 * RESTMusicService.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import java.io.IOException
import java.io.InputStream
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.shouldUseId3Tags
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
import org.moire.ultrasonic.domain.toArtistList
import org.moire.ultrasonic.domain.toDomainEntitiesList
import org.moire.ultrasonic.domain.toDomainEntity
import org.moire.ultrasonic.domain.toDomainEntityList
import org.moire.ultrasonic.domain.toIndexList
import org.moire.ultrasonic.domain.toMusicDirectoryDomainEntity
import org.moire.ultrasonic.domain.toTrackEntity
import org.moire.ultrasonic.domain.toTrackList
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.toSubsonicMaxBitRate
import timber.log.Timber

private const val SIMILAR_ARTIST_COUNT = 12
private const val OPENSUBSONIC_EXTENSION_SONG_LYRICS = "songLyrics"

/**
 * This Music Service implementation connects to a server using the Subsonic REST API
 */
@Suppress("LargeClass")
open class RESTMusicService(
    private val subsonicAPIClient: SubsonicAPIClient,
    private val activeServerProvider: ActiveServerProvider
) : MusicService {

    // Shortcut to the API
    @Suppress("VariableNaming", "PropertyName")
    val API = subsonicAPIClient.api

    // Fase 4: avoids probing OpenSubsonic extensions once per feature use -- see
    // OpenSubsonicExtensionsCache's doc comment.
    private val openSubsonicExtensions = OpenSubsonicExtensionsCache()

    @Throws(Exception::class)
    override fun ping() {
        API.ping().execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun isLicenseValid(): Boolean {
        val response = API.getLicense().execute().throwOnFailure()

        return response.body()!!.license.valid
    }

    @Throws(Exception::class)
    override suspend fun getMusicFolders(refresh: Boolean): List<MusicFolder> {
        val response = API.getMusicFoldersSuspend().throwOnFailure()

        return response.musicFolders.toDomainEntityList(activeServerId)
    }

    /**
     *  Retrieves the artists for a given music folder     *
     *
     *  On [refresh], sends the server's own last known `lastModified` timestamp as
     *  `ifModifiedSince` (Fase 3: incremental sync) -- if the server confirms nothing changed,
     *  it returns no index/shortcut entries, and this returns null so the caller keeps using
     *  its existing data instead of overwriting it with an apparently-empty result. Servers
     *  that don't honor `ifModifiedSince` just keep returning the full list every time, same
     *  as before this change.
     */
    @Throws(Exception::class)
    override suspend fun getIndexes(musicFolderId: String?, refresh: Boolean): List<Index>? {
        val serverId = ActiveServerProvider.getActiveServerId()
        val ifModifiedSince = if (refresh) Settings.getIndexesLastModified(serverId) else null

        val response = API.getIndexesSuspend(musicFolderId, ifModifiedSince).throwOnFailure()
        val indexes = response.indexes

        if (ifModifiedSince != null &&
            indexes.indexList.isEmpty() &&
            indexes.shortcutList.isEmpty()
        ) {
            return null
        }

        Settings.setIndexesLastModified(serverId, indexes.lastModified)

        return indexes.toIndexList(serverId, musicFolderId)
    }

    @Throws(Exception::class)
    override suspend fun getArtists(refresh: Boolean): List<Artist> {
        val response = API.getArtistsSuspend(null).throwOnFailure()

        return response.indexes.toArtistList(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun star(id: String?, albumId: String?, artistId: String?) {
        API.starSuspend(id, albumId, artistId).throwOnFailure()
    }

    @Throws(Exception::class)
    override suspend fun unstar(id: String?, albumId: String?, artistId: String?) {
        API.unstarSuspend(id, albumId, artistId).throwOnFailure()
    }

    @Throws(Exception::class)
    override fun setRating(id: String, rating: Int) {
        API.setRating(id, rating).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override suspend fun getMusicDirectory(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = API.getMusicDirectorySuspend(id).throwOnFailure()

        return response.musicDirectory.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getAlbumsOfArtist(
        id: String,
        name: String?,
        refresh: Boolean
    ): List<Album> {
        val response = API.getArtistSuspend(id).throwOnFailure()

        return response.artist.toDomainEntityList(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getArtistInfo(id: String): ArtistInfo {
        val response = API.getArtistInfo2Suspend(
            id = id,
            count = SIMILAR_ARTIST_COUNT,
            includeNotPresent = false
        ).throwOnFailure()

        return response.artistInfo.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getTopSongs(artistName: String, count: Int): List<Track> {
        val response = API.getTopSongsSuspend(artistName, count).throwOnFailure()
        return response.songsList.toTrackList(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getAlbumAsDir(
        id: String,
        name: String?,
        refresh: Boolean
    ): MusicDirectory {
        val response = API.getAlbumSuspend(id).throwOnFailure()

        return response.album.toMusicDirectoryDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getAlbum(id: String, name: String?, refresh: Boolean): Album {
        val response = API.getAlbumSuspend(id).throwOnFailure()

        return response.album.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getAlbumInfo(id: String): AlbumInfo {
        val response = API.getAlbumInfo2Suspend(id).throwOnFailure()

        return response.albumInfo.toDomainEntity()
    }

    // Search is fully migrated to suspend (Fase 7 of TAKI_CODE_OPTIMIZATION_PLAN.md): unlike
    // most of this class, these no longer go through a blocking .execute() call, so cancelling
    // the caller's coroutine (e.g. the user typed again, or left the Search screen) now actually
    // interrupts the in-flight request instead of just discarding its result once it eventually
    // finishes -- see SearchListModel.search().
    @Throws(Exception::class)
    override suspend fun search(criteria: SearchCriteria): SearchResult = try {
        if (shouldUseId3Tags()) {
            search3(criteria)
        } else {
            search2(criteria)
        }
    } catch (ignored: ApiNotSupportedException) {
        // Ensure backward compatibility with REST 1.3.
        searchOld(criteria)
    }

    /**
     * Search using the "search" REST method.
     */
    @Throws(Exception::class)
    private suspend fun searchOld(criteria: SearchCriteria): SearchResult {
        val response = API.searchSuspend(
            null,
            null,
            null,
            criteria.query,
            criteria.songCount,
            criteria.songOffset,
            null
        ).throwOnFailure()

        return response.searchResult.toDomainEntity(activeServerId)
    }

    /**
     * Search using the "search2" REST method, available in 1.4.0 and later.
     */
    @Throws(Exception::class)
    private suspend fun search2(criteria: SearchCriteria): SearchResult {
        requireNotNull(criteria.query) { "Query param is null" }
        val response = API.search2Suspend(
            criteria.query,
            criteria.artistCount,
            criteria.artistOffset,
            criteria.albumCount,
            criteria.albumOffset,
            criteria.songCount,
            criteria.songOffset,
            criteria.musicFolderId
        ).throwOnFailure()

        return response.searchResult.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    private suspend fun search3(criteria: SearchCriteria): SearchResult {
        requireNotNull(criteria.query) { "Query param is null" }
        val response = API.search3Suspend(
            criteria.query,
            criteria.artistCount,
            criteria.artistOffset,
            criteria.albumCount,
            criteria.albumOffset,
            criteria.songCount,
            criteria.songOffset,
            criteria.musicFolderId
        ).throwOnFailure()

        return response.searchResult.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getPlaylist(id: String, name: String): MusicDirectory {
        val response = API.getPlaylistSuspend(id).throwOnFailure()

        val playlist = response.playlist.toMusicDirectoryDomainEntity(activeServerId)
        savePlaylist(name, playlist)

        return playlist
    }

    @Throws(IOException::class)
    private fun savePlaylist(name: String, playlist: MusicDirectory) {
        val playlistFile = FileUtil.getPlaylistFile(
            activeServerProvider.getActiveServer().name,
            name
        )

        FileUtil.savePlaylist(playlistFile, playlist, name)
    }

    @Throws(Exception::class)
    override suspend fun getPlaylists(refresh: Boolean): List<Playlist> {
        val response = API.getPlaylistsSuspend(null).throwOnFailure()

        return response.playlists.toDomainEntitiesList()
    }

    /**
     * Either ID or String is required.
     * ID is required when updating
     * String is required when creating
     */
    @Throws(Exception::class)
    override suspend fun createPlaylist(id: String?, name: String?, tracks: List<Track>) {
        require(id != null || name != null) { "Either id or name is required." }
        val pSongIds: MutableList<String> = ArrayList(tracks.size)

        for ((id1) in tracks) {
            pSongIds.add(id1)
        }

        API.createPlaylistSuspend(id, name, pSongIds.toList()).throwOnFailure()
    }

    @Throws(Exception::class)
    override suspend fun deletePlaylist(id: String) {
        API.deletePlaylistSuspend(id).throwOnFailure()
    }

    @Throws(Exception::class)
    override suspend fun updatePlaylist(id: String, name: String?, comment: String?, pub: Boolean) {
        API.updatePlaylistSuspend(id, name, comment, pub, null, null).throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getPodcastsChannels(refresh: Boolean): List<PodcastsChannel> {
        val response = API.getPodcasts(false, null).execute().throwOnFailure()

        return response.body()!!.podcastChannels.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun getPodcastEpisodes(podcastChannelId: String?): MusicDirectory {
        val response = API.getPodcasts(true, podcastChannelId).execute().throwOnFailure()

        val podcastEntries = response.body()!!.podcastChannels[0].episodeList
        val musicDirectory = MusicDirectory()

        for (podcastEntry in podcastEntries) {
            if (
                "skipped" != podcastEntry.status &&
                "error" != podcastEntry.status
            ) {
                val entry = podcastEntry.toTrackEntity(activeServerId)
                entry.track = null
                musicDirectory.add(entry)
            }
        }

        return musicDirectory
    }

    @Throws(Exception::class)
    override fun getLyrics(artist: String, title: String): Lyrics {
        val response = API.getLyrics(artist, title).execute().throwOnFailure()

        return response.body()!!.lyrics.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getLyricsBySongId(id: String): Lyrics? {
        val supported = openSubsonicExtensions.supports(
            OPENSUBSONIC_EXTENSION_SONG_LYRICS,
            System.currentTimeMillis()
        ) { fetchOpenSubsonicExtensions() }

        if (!supported) return null

        val response = API.getLyricsBySongId(id).execute().throwOnFailure()

        return response.body()!!.lyricsList.toDomainEntity()
    }

    /**
     * Probes which OpenSubsonic extensions the server supports (see
     * TAKI_CODE_OPTIMIZATION_PLAN.md Fase 4). Only a cleanly parsed response -- successful or
     * not, empty extension list or not -- counts as [OpenSubsonicExtensionsCache.ProbeResult
     * .Success]; a plain Subsonic server confirms it that way (its own 200 OK with an empty/
     * absent extensions field, or a well-formed Subsonic protocol error). Anything that isn't a
     * clean parse -- HTTP failure, a rejected/malformed request, a network exception -- is a
     * [OpenSubsonicExtensionsCache.ProbeResult.Failure]: it means "couldn't tell this time", not
     * "confirmed unsupported", so the caller only memoizes it briefly instead of for a full day.
     * Never logs the request URL, credentials, token or salt -- only the HTTP code / exception
     * type, which is enough to tell the failure kinds apart without leaking anything sensitive.
     */
    private fun fetchOpenSubsonicExtensions(): OpenSubsonicExtensionsCache.ProbeResult {
        return try {
            val response = API.getOpenSubsonicExtensions().execute()
            if (!response.isSuccessful) {
                Timber.i(
                    "OpenSubsonic extensions request failed (HTTP %d), will retry soon",
                    response.code()
                )
                return OpenSubsonicExtensionsCache.ProbeResult.Failure
            }
            OpenSubsonicExtensionsCache.ProbeResult.Success(
                response.throwOnFailure().body()!!.openSubsonicExtensions
                    .map { it.name }
                    .toSet()
            )
        } catch (e: SubsonicRESTException) {
            Timber.i(
                e,
                "Server rejected the OpenSubsonic extensions request (code %d), will retry soon",
                e.code
            )
            OpenSubsonicExtensionsCache.ProbeResult.Failure
        } catch (e: Exception) {
            Timber.w(e, "Could not check OpenSubsonic extensions, will retry soon")
            OpenSubsonicExtensionsCache.ProbeResult.Failure
        }
    }

    @Throws(Exception::class)
    override fun scrobble(id: String, submission: Boolean) {
        API.scrobble(id, null, submission).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override suspend fun getAlbumList(
        type: AlbumListType,
        size: Int,
        offset: Int,
        musicFolderId: String?
    ): List<Album> {
        val response = API.getAlbumListSuspend(
            type,
            size,
            offset,
            null,
            null,
            null,
            musicFolderId
        ).throwOnFailure()

        return response.albumList.toDomainEntityList(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getAlbumList2(
        type: AlbumListType,
        size: Int,
        offset: Int,
        genre: String?,
        musicFolderId: String?
    ): List<Album> {
        val response = API.getAlbumList2Suspend(
            type,
            size,
            offset,
            null,
            null,
            genre,
            musicFolderId
        ).throwOnFailure()

        return response.albumList.toDomainEntityList(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getRandomSongs(size: Int): MusicDirectory {
        val response = API.getRandomSongsSuspend(
            size,
            null,
            null,
            null,
            null
        ).throwOnFailure()

        val result = MusicDirectory()
        result.addAll(response.songsList.toDomainEntityList(activeServerId))

        return result
    }

    @Throws(Exception::class)
    override suspend fun getStarred(): SearchResult {
        val response = API.getStarredSuspend(null).throwOnFailure()

        return response.starred.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getStarred2(): SearchResult {
        val response = API.getStarred2Suspend(null).throwOnFailure()

        return response.starred2.toDomainEntity(activeServerId)
    }

    @Throws(Exception::class)
    override fun getDownloadInputStream(
        song: Track,
        offset: Long,
        maxBitrate: Int?,
        save: Boolean
    ): Pair<InputStream, Boolean> {
        val songOffset = if (offset < 0) 0 else offset
        val subsonicMaxBitRate = maxBitrate?.toSubsonicMaxBitRate()

        // Use semantically correct call
        val response = if (save) {
            API.download(song.id, subsonicMaxBitRate, offset = songOffset)
                .execute().toStreamResponse()
        } else {
            API.stream(song.id, subsonicMaxBitRate, offset = songOffset)
                .execute().toStreamResponse()
        }

        response.throwOnFailure()

        if (response.stream == null) {
            throw IOException("Null stream response")
        }

        val partial = response.responseHttpCode == 206
        return Pair(response.stream!!, partial)
    }

    /**
     * We currently don't handle video playback in the app, but just create an Intent which video
     * players can respond to. For this intent we need the full URL of the stream, including the
     * authentication params. This is a bit tricky, because we want to avoid actually executing the
     * call because that could take a long time.
     */
    @Throws(Exception::class)
    override fun getStreamUrl(id: String, maxBitRate: Int?, format: String?): String {
        Timber.i("Start")

        // Get the request from Retrofit, but don't execute it!
        val request = API.stream(id, maxBitRate?.toSubsonicMaxBitRate(), format).request()

        // Create a new call with the request, and execute ist on our custom client
        val response = streamClient.newCall(request).execute()

        // The complete url :)
        val url = response.request.url

        Timber.i("Done")

        return url.toString()
    }

    private val streamClient by lazy {
        // Create a new modified okhttp client to intercept the URL
        val builder = subsonicAPIClient.okHttpClient.newBuilder()

        builder.addInterceptor { chain ->
            // Returns a dummy response
            Response.Builder()
                .code(100)
                .body("".toResponseBody(null))
                .protocol(Protocol.HTTP_2)
                .message("Empty response")
                .request(chain.request())
                .build()
        }

        // Create a new Okhttp client
        builder.build()
    }

    override fun isJukeboxAvailable(): Boolean {
        val username = activeServerProvider.getActiveServer().userName
        return getUser(username).jukeboxRole
    }

    @Throws(Exception::class)
    override fun updateJukeboxPlaylist(ids: List<String>): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.SET, null, null, ids, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun skipJukebox(index: Int, offsetSeconds: Int): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.SKIP, index, offsetSeconds, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun stopJukebox(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.STOP, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun clearJukebox(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.CLEAR, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun startJukebox(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.START, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getJukeboxStatus(): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.STATUS, null, null, null, null)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun setJukeboxGain(gain: Float): JukeboxStatus {
        val response = API.jukeboxControl(JukeboxAction.SET_GAIN, null, null, null, gain)
            .execute().throwOnFailure()

        return response.body()!!.jukebox.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getShares(refresh: Boolean): List<Share> {
        val response = API.getShares().execute().throwOnFailure()

        return response.body()!!.shares.toDomainEntitiesList(activeServerId)
    }

    @Throws(Exception::class)
    override suspend fun getGenres(refresh: Boolean): List<Genre> {
        val response = API.getGenresSuspend().throwOnFailure()

        return response.genresList.toDomainEntityList()
    }

    @Throws(Exception::class)
    override suspend fun getSongsByGenre(genre: String, count: Int, offset: Int): MusicDirectory {
        val response = API.getSongsByGenreSuspend(genre, count, offset, null).throwOnFailure()

        val result = MusicDirectory()
        result.addAll(response.songsList.toDomainEntityList(activeServerId))

        return result
    }

    @Throws(Exception::class)
    override fun getUser(username: String): UserInfo {
        val response = API.getUser(username).execute().throwOnFailure()

        return response.body()!!.user.toDomainEntity()
    }

    @Throws(Exception::class)
    override fun getChatMessages(since: Long?): List<ChatMessage> {
        val response = API.getChatMessages(since).execute().throwOnFailure()

        return response.body()!!.chatMessages.toDomainEntitiesList()
    }

    @Throws(Exception::class)
    override fun addChatMessage(message: String) {
        API.addChatMessage(message).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getBookmarks(): List<Bookmark> {
        val response = API.getBookmarks().execute().throwOnFailure()

        return response.body()!!.bookmarkList.toDomainEntitiesList(activeServerId)
    }

    @Throws(Exception::class)
    override fun createBookmark(id: String, position: Int) {
        API.createBookmark(id, position.toLong(), null).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun deleteBookmark(id: String) {
        API.deleteBookmark(id).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun getVideos(refresh: Boolean): MusicDirectory {
        val response = API.getVideos().execute().throwOnFailure()

        val musicDirectory = MusicDirectory()
        musicDirectory.addAll(response.body()!!.videosList.toDomainEntityList(activeServerId))

        return musicDirectory
    }

    @Throws(Exception::class)
    override fun createShare(ids: List<String>, description: String?, expires: Long?): List<Share> {
        val response = API.createShare(ids, description, expires).execute().throwOnFailure()

        return response.body()!!.shares.toDomainEntitiesList(activeServerId)
    }

    @Throws(Exception::class)
    override fun deleteShare(id: String) {
        API.deleteShare(id).execute().throwOnFailure()
    }

    @Throws(Exception::class)
    override fun updateShare(id: String, description: String?, expires: Long?) {
        var expiresValue: Long? = expires
        if (expires != null && expires == 0L) {
            expiresValue = null
        }

        API.updateShare(id, description, expiresValue).execute().throwOnFailure()
    }

    private val activeServerId: Int
        get() = ActiveServerProvider.getActiveServerId()

    init {
        // The client will notice if the minimum supported API version has changed
        // By registering a callback we ensure this info is saved in the database as well
        subsonicAPIClient.onProtocolChange = {
            Timber.i("Server minimum API version set to %s", it)
            activeServerProvider.setMinimumApiVersion(it.restApiVersion)
        }
    }
}
