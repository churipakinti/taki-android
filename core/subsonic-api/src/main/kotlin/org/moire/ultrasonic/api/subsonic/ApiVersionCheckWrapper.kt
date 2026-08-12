/*
 * ApiVersionCheckWrapper.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.api.subsonic

import okhttp3.ResponseBody
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_11_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_12_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_13_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_14_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_2_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_3_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_4_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_5_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_6_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_7_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_8_0
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions.V1_9_0
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
import org.moire.ultrasonic.api.subsonic.response.GetLyricsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistsResponse
import org.moire.ultrasonic.api.subsonic.response.GetPodcastsResponse
import org.moire.ultrasonic.api.subsonic.response.GetRandomSongsResponse
import org.moire.ultrasonic.api.subsonic.response.GetSongResponse
import org.moire.ultrasonic.api.subsonic.response.GetSongsByGenreResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredResponse
import org.moire.ultrasonic.api.subsonic.response.GetStarredTwoResponse
import org.moire.ultrasonic.api.subsonic.response.GetTopSongsResponse
import org.moire.ultrasonic.api.subsonic.response.GetUserResponse
import org.moire.ultrasonic.api.subsonic.response.JukeboxResponse
import org.moire.ultrasonic.api.subsonic.response.SearchThreeResponse
import org.moire.ultrasonic.api.subsonic.response.SearchTwoResponse
import org.moire.ultrasonic.api.subsonic.response.SharesResponse
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse
import org.moire.ultrasonic.api.subsonic.response.VideosResponse
import retrofit2.Call

/**
 * Special wrapper for [SubsonicAPIDefinition] that checks if [currentApiVersion] is suitable
 * for this call.
 */
@Suppress("TooManyFunctions")
internal class ApiVersionCheckWrapper(
    val api: SubsonicAPIDefinition,
    var currentApiVersion: SubsonicAPIVersions,
    var isRealProtocolVersion: Boolean = false
) : SubsonicAPIDefinition by api {
    override suspend fun getArtistsSuspend(musicFolderId: String?): GetArtistsResponse {
        checkVersion(V1_8_0)
        return api.getArtistsSuspend(musicFolderId)
    }

    override suspend fun starSuspend(
        id: String?,
        albumId: String?,
        artistId: String?
    ): SubsonicResponse {
        checkVersion(V1_8_0)
        return api.starSuspend(id, albumId, artistId)
    }

    override suspend fun unstarSuspend(
        id: String?,
        albumId: String?,
        artistId: String?
    ): SubsonicResponse {
        checkVersion(V1_8_0)
        return api.unstarSuspend(id, albumId, artistId)
    }

    override suspend fun getArtistSuspend(id: String): GetArtistResponse {
        checkVersion(V1_8_0)
        return api.getArtistSuspend(id)
    }

    override suspend fun getArtistInfo2Suspend(
        id: String,
        count: Int?,
        includeNotPresent: Boolean?
    ): GetArtistInfo2Response {
        checkVersion(V1_11_0)
        return api.getArtistInfo2Suspend(id, count, includeNotPresent)
    }

    override suspend fun getTopSongsSuspend(artist: String, count: Int?): GetTopSongsResponse {
        checkVersion(V1_13_0)
        return api.getTopSongsSuspend(artist, count)
    }

    override suspend fun getAlbumSuspend(id: String): GetAlbumResponse {
        checkVersion(V1_8_0)
        return api.getAlbumSuspend(id)
    }

    override suspend fun getAlbumInfo2Suspend(id: String): GetAlbumInfo2Response {
        checkVersion(V1_14_0)
        return api.getAlbumInfo2Suspend(id)
    }

    override suspend fun getSongSuspend(id: String): GetSongResponse {
        checkVersion(V1_8_0)
        return api.getSongSuspend(id)
    }

    override suspend fun search2Suspend(
        query: String,
        artistCount: Int?,
        artistOffset: Int?,
        albumCount: Int?,
        albumOffset: Int?,
        songCount: Int?,
        songOffset: Int?,
        musicFolderId: String?
    ): SearchTwoResponse {
        checkVersion(V1_4_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.search2Suspend(
            query,
            artistCount,
            artistOffset,
            albumCount,
            albumOffset,
            songCount,
            songOffset,
            musicFolderId
        )
    }

    override suspend fun search3Suspend(
        query: String,
        artistCount: Int?,
        artistOffset: Int?,
        albumCount: Int?,
        albumOffset: Int?,
        songCount: Int?,
        songOffset: Int?,
        musicFolderId: String?
    ): SearchThreeResponse {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.search3Suspend(
            query,
            artistCount,
            artistOffset,
            albumCount,
            albumOffset,
            songCount,
            songOffset,
            musicFolderId
        )
    }

    override suspend fun getPlaylistsSuspend(username: String?): GetPlaylistsResponse {
        checkParamVersion(username, V1_8_0)
        return api.getPlaylistsSuspend(username)
    }

    override suspend fun createPlaylistSuspend(
        id: String?,
        name: String?,
        songIds: List<String>?
    ): SubsonicResponse {
        checkVersion(V1_2_0)
        return api.createPlaylistSuspend(id, name, songIds)
    }

    override suspend fun deletePlaylistSuspend(id: String): SubsonicResponse {
        checkVersion(V1_2_0)
        return api.deletePlaylistSuspend(id)
    }

    override suspend fun updatePlaylistSuspend(
        id: String,
        name: String?,
        comment: String?,
        public: Boolean?,
        songIdsToAdd: List<String>?,
        songIndexesToRemove: List<Int>?
    ): SubsonicResponse {
        checkVersion(V1_8_0)
        return api.updatePlaylistSuspend(
            id,
            name,
            comment,
            public,
            songIdsToAdd,
            songIndexesToRemove
        )
    }

    override fun getPodcasts(includeEpisodes: Boolean?, id: String?): Call<GetPodcastsResponse> {
        checkVersion(V1_6_0)
        checkParamVersion(includeEpisodes, V1_9_0)
        checkParamVersion(id, V1_9_0)
        return api.getPodcasts(includeEpisodes, id)
    }

    override fun getLyrics(artist: String?, title: String?): Call<GetLyricsResponse> {
        checkVersion(V1_2_0)
        return api.getLyrics(artist, title)
    }

    override fun scrobble(id: String, time: Long?, submission: Boolean?): Call<SubsonicResponse> {
        checkVersion(V1_5_0)
        checkParamVersion(time, V1_8_0)
        return api.scrobble(id, time, submission)
    }

    override suspend fun getAlbumListSuspend(
        type: AlbumListType,
        size: Int?,
        offset: Int?,
        fromYear: Int?,
        toYear: Int?,
        genre: String?,
        musicFolderId: String?
    ): GetAlbumListResponse {
        checkVersion(V1_2_0)
        checkParamVersion(musicFolderId, V1_11_0)
        return api.getAlbumListSuspend(type, size, offset, fromYear, toYear, genre, musicFolderId)
    }

    override suspend fun getAlbumList2Suspend(
        type: AlbumListType,
        size: Int?,
        offset: Int?,
        fromYear: Int?,
        toYear: Int?,
        genre: String?,
        musicFolderId: String?
    ): GetAlbumList2Response {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getAlbumList2Suspend(type, size, offset, fromYear, toYear, genre, musicFolderId)
    }

    override suspend fun getRandomSongsSuspend(
        size: Int?,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
        musicFolderId: String?
    ): GetRandomSongsResponse {
        checkVersion(V1_2_0)
        return api.getRandomSongsSuspend(size, genre, fromYear, toYear, musicFolderId)
    }

    override suspend fun getStarredSuspend(musicFolderId: String?): GetStarredResponse {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getStarredSuspend(musicFolderId)
    }

    override suspend fun getStarred2Suspend(musicFolderId: String?): GetStarredTwoResponse {
        checkVersion(V1_8_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getStarred2Suspend(musicFolderId)
    }

    override fun stream(
        id: String,
        maxBitRate: Int?,
        format: String?,
        timeOffset: Int?,
        videoSize: String?,
        estimateContentLength: Boolean?,
        converted: Boolean?,
        offset: Long?
    ): Call<ResponseBody> {
        checkParamVersion(maxBitRate, V1_2_0)
        checkParamVersion(format, V1_6_0)
        checkParamVersion(videoSize, V1_6_0)
        checkParamVersion(estimateContentLength, V1_8_0)
        checkParamVersion(converted, V1_14_0)
        return api.stream(
            id,
            maxBitRate,
            format,
            timeOffset,
            videoSize,
            estimateContentLength,
            converted
        )
    }

    override fun jukeboxControl(
        action: JukeboxAction,
        index: Int?,
        offset: Int?,
        ids: List<String>?,
        gain: Float?
    ): Call<JukeboxResponse> {
        checkVersion(V1_2_0)
        checkParamVersion(offset, V1_7_0)
        return api.jukeboxControl(action, index, offset, ids, gain)
    }

    override fun getShares(): Call<SharesResponse> {
        checkVersion(V1_6_0)
        return api.getShares()
    }

    override fun createShare(
        idsToShare: List<String>,
        description: String?,
        expires: Long?
    ): Call<SharesResponse> {
        checkVersion(V1_6_0)
        return api.createShare(idsToShare, description, expires)
    }

    override fun deleteShare(id: String): Call<SubsonicResponse> {
        checkVersion(V1_6_0)
        return api.deleteShare(id)
    }

    override fun updateShare(
        id: String,
        description: String?,
        expires: Long?
    ): Call<SubsonicResponse> {
        checkVersion(V1_6_0)
        return api.updateShare(id, description, expires)
    }

    override suspend fun getGenresSuspend(): GenresResponse {
        checkVersion(V1_9_0)
        return api.getGenresSuspend()
    }

    override suspend fun getSongsByGenreSuspend(
        genre: String,
        count: Int,
        offset: Int,
        musicFolderId: String?
    ): GetSongsByGenreResponse {
        checkVersion(V1_9_0)
        checkParamVersion(musicFolderId, V1_12_0)
        return api.getSongsByGenreSuspend(genre, count, offset, musicFolderId)
    }

    override fun getUser(username: String): Call<GetUserResponse> {
        checkVersion(V1_3_0)
        return api.getUser(username)
    }

    override fun getChatMessages(since: Long?): Call<ChatMessagesResponse> {
        checkVersion(V1_2_0)
        return api.getChatMessages(since)
    }

    override fun addChatMessage(message: String): Call<SubsonicResponse> {
        checkVersion(V1_2_0)
        return api.addChatMessage(message)
    }

    override fun getBookmarks(): Call<BookmarksResponse> {
        checkVersion(V1_9_0)
        return api.getBookmarks()
    }

    override fun createBookmark(
        id: String,
        position: Long,
        comment: String?
    ): Call<SubsonicResponse> {
        checkVersion(V1_9_0)
        return api.createBookmark(id, position, comment)
    }

    override fun deleteBookmark(id: String): Call<SubsonicResponse> {
        checkVersion(V1_9_0)
        return api.deleteBookmark(id)
    }

    override fun getVideos(): Call<VideosResponse> {
        checkVersion(V1_8_0)
        return api.getVideos()
    }

    override fun getAvatar(username: String): Call<ResponseBody> {
        checkVersion(V1_8_0)
        return api.getAvatar(username)
    }

    private fun checkVersion(expectedVersion: SubsonicAPIVersions) {
        // If it is true, it is probably the first call with this server
        if (!isRealProtocolVersion) return
        if (currentApiVersion < expectedVersion) {
            throw ApiNotSupportedException(currentApiVersion)
        }
    }

    private fun checkParamVersion(param: Any?, expectedVersion: SubsonicAPIVersions) {
        // If it is true, it is probably the first call with this server
        if (!isRealProtocolVersion) return
        if (param != null) {
            checkVersion(expectedVersion)
        }
    }
}
