/*
 * SubsonicAPIDefinition.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.api.subsonic

import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction
import org.moire.ultrasonic.api.subsonic.response.BookmarksResponse
import org.moire.ultrasonic.api.subsonic.response.ChatMessagesResponse
import org.moire.ultrasonic.api.subsonic.response.GenresResponse
import org.moire.ultrasonic.api.subsonic.response.GetAlbumInfo2Response
import org.moire.ultrasonic.api.subsonic.response.GetAlbumList2Response
import org.moire.ultrasonic.api.subsonic.response.GetAlbumListResponse
import org.moire.ultrasonic.api.subsonic.response.GetAlbumResponse
import org.moire.ultrasonic.api.subsonic.response.GetArtistInfo2Response
import org.moire.ultrasonic.api.subsonic.response.GetArtistResponse
import org.moire.ultrasonic.api.subsonic.response.GetArtistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetIndexesResponse
import org.moire.ultrasonic.api.subsonic.response.GetLyricsBySongIdResponse
import org.moire.ultrasonic.api.subsonic.response.GetLyricsResponse
import org.moire.ultrasonic.api.subsonic.response.GetMusicDirectoryResponse
import org.moire.ultrasonic.api.subsonic.response.GetOpenSubsonicExtensionsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPodcastsResponse
import org.moire.ultrasonic.api.subsonic.response.GetRandomSongsResponse
import org.moire.ultrasonic.api.subsonic.response.GetSongsByGenreResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredTwoResponse
import org.moire.ultrasonic.api.subsonic.response.GetTopSongsResponse
import org.moire.ultrasonic.api.subsonic.response.GetUserResponse
import org.moire.ultrasonic.api.subsonic.response.JukeboxResponse
import org.moire.ultrasonic.api.subsonic.response.LicenseResponse
import org.moire.ultrasonic.api.subsonic.response.MusicFoldersResponse
import org.moire.ultrasonic.api.subsonic.response.SearchResponse
import org.moire.ultrasonic.api.subsonic.response.SearchThreeResponse
import org.moire.ultrasonic.api.subsonic.response.SearchTwoResponse
import org.moire.ultrasonic.api.subsonic.response.SharesResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.response.VideosResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Subsonic API calls.
 *
 * For methods description see [http://www.subsonic.org/pages/api.jsp].
 */
@Suppress("TooManyFunctions", "LongParameterList")
interface SubsonicAPIDefinition {
    @GET("ping.view")
    fun ping(): Call<SubsonicResponse>

    @GET("ping.view")
    suspend fun pingSuspend(): SubsonicResponse

    @GET("getLicense.view")
    fun getLicense(): Call<LicenseResponse>

    @GET("getMusicFolders.view")
    fun getMusicFolders(): Call<MusicFoldersResponse>

    @GET("getIndexes.view")
    fun getIndexes(
        @Query("musicFolderId") musicFolderId: String?,
        @Query("ifModifiedSince") ifModifiedSince: Long?
    ): Call<GetIndexesResponse>

    @GET("getMusicDirectory.view")
    fun getMusicDirectory(@Query("id") id: String): Call<GetMusicDirectoryResponse>

    @GET("getArtists.view")
    fun getArtists(@Query("musicFolderId") musicFolderId: String?): Call<GetArtistsResponse>

    @GET("star.view")
    fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): Call<SubsonicResponse>

    @GET("unstar.view")
    fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): Call<SubsonicResponse>

    @GET("setRating.view")
    fun setRating(@Query("id") id: String, @Query("rating") rating: Int): Call<SubsonicResponse>

    @GET("getArtist.view")
    fun getArtist(@Query("id") id: String): Call<GetArtistResponse>

    @GET("getArtistInfo2.view")
    fun getArtistInfo2(
        @Query("id") id: String,
        @Query("count") count: Int? = null,
        @Query("includeNotPresent") includeNotPresent: Boolean? = null
    ): Call<GetArtistInfo2Response>

    @GET("getTopSongs.view")
    fun getTopSongs(
        @Query("artist") artist: String,
        @Query("count") count: Int? = null
    ): Call<GetTopSongsResponse>

    @GET("getAlbum.view")
    fun getAlbum(@Query("id") id: String): Call<GetAlbumResponse>

    @GET("getAlbumInfo2.view")
    fun getAlbumInfo2(@Query("id") id: String): Call<GetAlbumInfo2Response>

    // The Search vertical is fully migrated to suspend (Fase 7 of
    // TAKI_CODE_OPTIMIZATION_PLAN.md) - unlike most other endpoints in this file, there is no
    // Call<T>-returning version to keep alongside these: RESTMusicService.search() (their only
    // caller) migrated in the same change, so there was nothing left for a blocking version to
    // serve.
    @GET("search.view")
    suspend fun searchSuspend(
        @Query("artist") artist: String? = null,
        @Query("album") album: String? = null,
        @Query("title") title: String? = null,
        @Query("any") any: String? = null,
        @Query("count") count: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("newerThan") newerThan: Long? = null
    ): SearchResponse

    @GET("search2.view")
    suspend fun search2Suspend(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int? = null,
        @Query("artistOffset") artistOffset: Int? = null,
        @Query("albumCount") albumCount: Int? = null,
        @Query("albumOffset") albumOffset: Int? = null,
        @Query("songCount") songCount: Int? = null,
        @Query("songOffset") songOffset: Int? = null,
        @Query("musicFolderId") musicFolderId: String? = null
    ): SearchTwoResponse

    @GET("search3.view")
    suspend fun search3Suspend(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int? = null,
        @Query("artistOffset") artistOffset: Int? = null,
        @Query("albumCount") albumCount: Int? = null,
        @Query("albumOffset") albumOffset: Int? = null,
        @Query("songCount") songCount: Int? = null,
        @Query("songOffset") songOffset: Int? = null,
        @Query("musicFolderId") musicFolderId: String? = null
    ): SearchThreeResponse

    @GET("getPlaylist.view")
    fun getPlaylist(@Query("id") id: String): Call<GetPlaylistResponse>

    @GET("getPlaylists.view")
    fun getPlaylists(@Query("username") username: String? = null): Call<GetPlaylistsResponse>

    @GET("createPlaylist.view")
    fun createPlaylist(
        @Query("playlistId") id: String? = null,
        @Query("name") name: String? = null,
        @Query("songId") songIds: List<String>? = null
    ): Call<SubsonicResponse>

    @GET("deletePlaylist.view")
    fun deletePlaylist(@Query("id") id: String): Call<SubsonicResponse>

    @GET("updatePlaylist.view")
    fun updatePlaylist(
        @Query("playlistId") id: String,
        @Query("name") name: String? = null,
        @Query("comment") comment: String? = null,
        @Query("public") public: Boolean? = null,
        @Query("songIdToAdd") songIdsToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexesToRemove: List<Int>? = null
    ): Call<SubsonicResponse>

    @GET("getPodcasts.view")
    fun getPodcasts(
        @Query("includeEpisodes") includeEpisodes: Boolean? = null,
        @Query("id") id: String? = null
    ): Call<GetPodcastsResponse>

    @GET("getPodcasts.view")
    suspend fun getPodcastsSuspend(
        @Query("includeEpisodes") includeEpisodes: Boolean? = null,
        @Query("id") id: String? = null
    ): GetPodcastsResponse

    @GET("getLyrics.view")
    fun getLyrics(
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null
    ): Call<GetLyricsResponse>

    /**
     * OpenSubsonic extension. Returns time-synced lyrics when the server/track has them,
     * plain unsynced lines otherwise. Not part of the core Subsonic protocol, so it isn't
     * gated by [ApiVersionCheckWrapper] like the versioned calls above. Callers should check
     * [getOpenSubsonicExtensions] first and fall back to [getLyrics] when the server doesn't
     * advertise support for it.
     */
    @GET("getLyricsBySongId.view")
    fun getLyricsBySongId(@Query("id") id: String): Call<GetLyricsBySongIdResponse>

    /**
     * OpenSubsonic extension (https://opensubsonic.netlify.app/docs/extensions/). Lists which
     * OpenSubsonic-only extensions the server implements, e.g. `songLyrics` for
     * [getLyricsBySongId]. Plain Subsonic servers don't have this endpoint at all and will fail
     * the call (HTTP error or a Subsonic protocol error, depending on the server) - callers must
     * treat any failure here as "no extensions available", not as a fatal error.
     */
    @GET("getOpenSubsonicExtensions.view")
    fun getOpenSubsonicExtensions(): Call<GetOpenSubsonicExtensionsResponse>

    @GET("scrobble.view")
    fun scrobble(
        @Query("id") id: String,
        @Query("time") time: Long? = null,
        @Query("submission") submission: Boolean? = null
    ): Call<SubsonicResponse>

    @GET("getAlbumList.view")
    fun getAlbumList(
        @Query("type") type: AlbumListType,
        @Query("size") size: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("musicFolderId") musicFolderId: String? = null
    ): Call<GetAlbumListResponse>

    @GET("getAlbumList2.view")
    fun getAlbumList2(
        @Query("type") type: AlbumListType,
        @Query("size") size: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("musicFolderId") musicFolderId: String? = null
    ): Call<GetAlbumList2Response>

    @GET("getRandomSongs.view")
    fun getRandomSongs(
        @Query("size") size: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("musicFolderId") musicFolderId: String? = null
    ): Call<GetRandomSongsResponse>

    @GET("getStarred.view")
    fun getStarred(@Query("musicFolderId") musicFolderId: String? = null): Call<GetStarredResponse>

    @GET("getStarred2.view")
    fun getStarred2(
        @Query("musicFolderId") musicFolderId: String? = null
    ): Call<GetStarredTwoResponse>

    @Streaming
    @GET("getCoverArt.view")
    fun getCoverArt(@Query("id") id: String, @Query("size") size: Long? = null): Call<ResponseBody>

    @Streaming
    @GET("stream.view")
    fun stream(
        @Query("id") id: String,
        @Query("maxBitRate") maxBitRate: Int? = null,
        @Query("format") format: String? = null,
        @Query("timeOffset") timeOffset: Int? = null,
        @Query("size") videoSize: String? = null,
        @Query("estimateContentLength") estimateContentLength: Boolean? = null,
        @Query("converted") converted: Boolean? = null,
        @Header("Range") offset: Long? = null
    ): Call<ResponseBody>

    @Streaming
    @GET("download.view")
    fun download(
        @Query("id") id: String,
        @Query("maxBitRate") maxBitRate: Int? = null,
        @Query("format") format: String? = null,
        @Query("timeOffset") timeOffset: Int? = null,
        @Query("size") videoSize: String? = null,
        @Query("estimateContentLength") estimateContentLength: Boolean? = null,
        @Query("converted") converted: Boolean? = null,
        @Header("Range") offset: Long? = null
    ): Call<ResponseBody>

    @GET("jukeboxControl.view")
    fun jukeboxControl(
        @Query("action") action: JukeboxAction,
        @Query("index") index: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("id") ids: List<String>? = null,
        @Query("gain") gain: Float? = null
    ): Call<JukeboxResponse>

    @GET("getShares.view")
    fun getShares(): Call<SharesResponse>

    @GET("getShares.view")
    suspend fun getSharesSuspend(): SharesResponse

    @GET("createShare.view")
    fun createShare(
        @Query("id") idsToShare: List<String>,
        @Query("description") description: String? = null,
        @Query("expires") expires: Long? = null
    ): Call<SharesResponse>

    @GET("deleteShare.view")
    fun deleteShare(@Query("id") id: String): Call<SubsonicResponse>

    @GET("updateShare.view")
    fun updateShare(
        @Query("id") id: String,
        @Query("description") description: String? = null,
        @Query("expires") expires: Long? = null
    ): Call<SubsonicResponse>

    @GET("getGenres.view")
    fun getGenres(): Call<GenresResponse>

    @GET("getSongsByGenre.view")
    fun getSongsByGenre(
        @Query("genre") genre: String,
        @Query("count") count: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("musicFolderId") musicFolderId: String? = null
    ): Call<GetSongsByGenreResponse>

    @GET("getUser.view")
    fun getUser(@Query("username") username: String): Call<GetUserResponse>

    @GET("getUser.view")
    suspend fun getUserSuspend(@Query("username") username: String): GetUserResponse

    @GET("getChatMessages.view")
    fun getChatMessages(@Query("since") since: Long? = null): Call<ChatMessagesResponse>

    @GET("getChatMessages.view")
    suspend fun getChatMessagesSuspend(@Query("since") since: Long? = null): ChatMessagesResponse

    @GET("addChatMessage.view")
    fun addChatMessage(@Query("message") message: String): Call<SubsonicResponse>

    @GET("getBookmarks.view")
    fun getBookmarks(): Call<BookmarksResponse>

    @GET("getBookmarks.view")
    suspend fun getBookmarksSuspend(): BookmarksResponse

    @GET("createBookmark.view")
    fun createBookmark(
        @Query("id") id: String,
        @Query("position") position: Long,
        @Query("comment") comment: String? = null
    ): Call<SubsonicResponse>

    @GET("deleteBookmark.view")
    fun deleteBookmark(@Query("id") id: String): Call<SubsonicResponse>

    @GET("getVideos.view")
    fun getVideos(): Call<VideosResponse>

    @GET("getVideos.view")
    suspend fun getVideosSuspend(): VideosResponse

    @GET("getAvatar.view")
    fun getAvatar(@Query("username") username: String): Call<ResponseBody>
}
