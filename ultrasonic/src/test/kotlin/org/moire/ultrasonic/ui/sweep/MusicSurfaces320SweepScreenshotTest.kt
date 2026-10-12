/*
 * MusicSurfaces320SweepScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.sweep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.album.AlbumDetailActions
import org.moire.ultrasonic.ui.album.AlbumDetailRow
import org.moire.ultrasonic.ui.album.AlbumDetailScreen
import org.moire.ultrasonic.ui.album.AlbumDetailUiState
import org.moire.ultrasonic.ui.albumlist.AlbumListActions
import org.moire.ultrasonic.ui.albumlist.AlbumListRow
import org.moire.ultrasonic.ui.albumlist.AlbumListScreen
import org.moire.ultrasonic.ui.albumlist.AlbumListUiState
import org.moire.ultrasonic.ui.artist.ArtistAlbumUi
import org.moire.ultrasonic.ui.artist.ArtistDetailActions
import org.moire.ultrasonic.ui.artist.ArtistDetailScreen
import org.moire.ultrasonic.ui.artist.ArtistDetailUiState
import org.moire.ultrasonic.ui.artist.ArtistTrackUi
import org.moire.ultrasonic.ui.artistlist.ArtistListActions
import org.moire.ultrasonic.ui.artistlist.ArtistListRow
import org.moire.ultrasonic.ui.artistlist.ArtistListScreen
import org.moire.ultrasonic.ui.artistlist.ArtistListUiState
import org.moire.ultrasonic.ui.downloads.DownloadedAlbumRow
import org.moire.ultrasonic.ui.downloads.DownloadsActions
import org.moire.ultrasonic.ui.downloads.DownloadsScreen
import org.moire.ultrasonic.ui.downloads.DownloadsUiState
import org.moire.ultrasonic.ui.genrelist.GenreListActions
import org.moire.ultrasonic.ui.genrelist.GenreListRow
import org.moire.ultrasonic.ui.genrelist.GenreListScreen
import org.moire.ultrasonic.ui.genrelist.GenreListUiState
import org.moire.ultrasonic.ui.lyrics.LyricsActions
import org.moire.ultrasonic.ui.lyrics.LyricsContent
import org.moire.ultrasonic.ui.lyrics.LyricsLineUi
import org.moire.ultrasonic.ui.lyrics.LyricsScreen
import org.moire.ultrasonic.ui.lyrics.LyricsUiState
import org.moire.ultrasonic.ui.playback.PlaybackPhase
import org.moire.ultrasonic.ui.playback.PlaybackProgress
import org.moire.ultrasonic.ui.playback.PlayerUiState
import org.moire.ultrasonic.ui.playback.RepeatMode
import org.moire.ultrasonic.ui.player.NowPlayingActions
import org.moire.ultrasonic.ui.player.NowPlayingScreen
import org.moire.ultrasonic.ui.playlistlist.PlaylistListActions
import org.moire.ultrasonic.ui.playlistlist.PlaylistListRow
import org.moire.ultrasonic.ui.playlistlist.PlaylistListScreen
import org.moire.ultrasonic.ui.playlistlist.PlaylistListUiState
import org.moire.ultrasonic.ui.playlistlist.PlaylistRowDownloadStatus
import org.moire.ultrasonic.ui.search.SearchActions
import org.moire.ultrasonic.ui.search.SearchAlbumUi
import org.moire.ultrasonic.ui.search.SearchArtistUi
import org.moire.ultrasonic.ui.search.SearchScreen
import org.moire.ultrasonic.ui.search.SearchSongUi
import org.moire.ultrasonic.ui.search.SearchUiState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.ui.tracklist.TrackListActions
import org.moire.ultrasonic.ui.tracklist.TrackListRow
import org.moire.ultrasonic.ui.tracklist.TrackListScreen
import org.moire.ultrasonic.ui.tracklist.TrackListUiState
import org.moire.ultrasonic.ui.upnext.UpNextActions
import org.moire.ultrasonic.ui.upnext.UpNextCurrentUi
import org.moire.ultrasonic.ui.upnext.UpNextScreen
import org.moire.ultrasonic.ui.upnext.UpNextTrackUi
import org.moire.ultrasonic.ui.upnext.UpNextUiState
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #26 phase 5C.4 narrow-width sign-off: the music surfaces (all six Library list
 * destinations now carrying their own Compose header, Search results, Album/Artist Detail, Now
 * Playing, Up Next, Lyrics, Downloads) in the harshest combination - Spanish strings, a 320dp
 * phone and a 1.3x font scale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-w320dp-h1200dp-xxhdpi")
class MusicSurfaces320SweepScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(tag: String, height: Int = 1200, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                TakiTheme {
                    Box(
                        modifier = Modifier
                            .testTag(tag)
                            .width(320.dp)
                            .heightIn(max = height.dp)
                            .background(TakiTheme.colors.black),
                    ) { content() }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private val inset = 0.dp

    @Test
    fun albums() = capture("s320_albums", 900) {
        val rows = persistentListOf(
            AlbumListRow("a1", "OK Computer", "Radiohead", null),
            AlbumListRow("a2", "The Well-Tempered Clavier, Book I", "Johann Sebastian Bach", null),
            AlbumListRow("a3", "Abbey Road", "The Beatles", null),
        )
        AlbumListScreen(
            AlbumListUiState(
                isLoading = false,
                rows = rows,
                availableSortOrders = persistentListOf(SortOrder.NEWEST, SortOrder.RECENT, SortOrder.BY_NAME),
            ),
            AlbumListActions.Noop,
            inset,
            title = stringResource(R.string.main_albums_title),
        )
    }

    @Test
    fun artists() = capture("s320_artists", 900) {
        val rows = persistentListOf(
            ArtistListRow("r1", "AC/DC", null, false),
            ArtistListRow("r2", "Johann Sebastian Bach", null, false),
        )
        ArtistListScreen(
            ArtistListUiState(isLoading = false, rows = rows, availableSortOrders = persistentListOf(SortOrder.BY_NAME)),
            ArtistListActions.Noop,
            inset,
            title = stringResource(R.string.main_artists_title),
        )
    }

    @Test
    fun genres() = capture("s320_genres", 900) {
        val rows = persistentListOf(
            GenreListRow("Alternative & Indie", null),
            GenreListRow("Heavy Metal", null),
            GenreListRow("Música clásica", null),
            GenreListRow("Jazz", null),
        )
        GenreListScreen(
            GenreListUiState(isLoading = false, rows = rows),
            GenreListActions.Noop,
            inset,
            title = stringResource(R.string.main_genres_title),
        )
    }

    private val trackRows = persistentListOf(
        TrackListRow("t1", "OK Computer", "Radiohead · OK Computer", null, false),
        TrackListRow("t2", "Preludio y Fuga n.º 1 en do mayor, BWV 846", "Johann Sebastian Bach · El clave bien temperado", null, true),
    )

    @Test
    fun songs() = capture("s320_songs", 900) {
        TrackListScreen(
            TrackListUiState(
                isLoading = false,
                showControls = true,
                headerTitle = stringResource(R.string.main_songs_title),
                sortOrder = SortOrder.ALL_SONGS,
                availableSortOrders = persistentListOf(SortOrder.ALL_SONGS, SortOrder.RANDOM, SortOrder.STARRED),
                rows = trackRows,
            ),
            TrackListActions.Noop,
            inset,
        )
    }

    @Test
    fun likedSongs() = capture("s320_liked_songs", 900) {
        TrackListScreen(
            TrackListUiState(
                isLoading = false,
                showHeart = true,
                headerTitle = stringResource(R.string.library_liked_songs),
                rows = trackRows,
            ),
            TrackListActions.Noop,
            inset,
        )
    }

    @Test
    fun playlists() = capture("s320_playlists", 900) {
        val rows = persistentListOf(
            PlaylistListRow("p1", "Viaje por carretera", 24, null, PlaylistRowDownloadStatus.DOWNLOADED),
            PlaylistListRow("p2", "Día de lluvia", 12, null, PlaylistRowDownloadStatus.NOT_DOWNLOADED),
        )
        PlaylistListScreen(
            PlaylistListUiState(isLoading = false, online = true, rows = rows),
            PlaylistListActions.Noop,
            inset,
            title = stringResource(R.string.playlist_label),
        )
    }

    @Test
    fun downloads() = capture("s320_downloads", 900) {
        val rows = persistentListOf(
            DownloadedAlbumRow("d1", "Un álbum con un título deliberadamente muy largo para probar", "Varios artistas", 24),
            DownloadedAlbumRow("d2", "Kind of Blue", "Miles Davis", 5),
        )
        DownloadsScreen(DownloadsUiState(isLoading = false, rows = rows), DownloadsActions.Noop, inset)
    }

    @Test
    fun searchResults() = capture("s320_search", 1400) {
        SearchScreen(
            SearchUiState(
                query = "bach",
                submitted = true,
                artists = persistentListOf(SearchArtistUi("ar1", "Johann Sebastian Bach", isIndex = false)),
                albums = persistentListOf(SearchAlbumUi("al1", "Las suites para violonchelo", "Yo-Yo Ma", null)),
                songs = persistentListOf(SearchSongUi("s1", "Aria sobre la cuerda de sol", "Johann Sebastian Bach", null)),
                artistsHaveMore = true,
            ),
            SearchActions.Noop,
            bottomContentInset = inset,
        )
    }

    @Test
    fun albumDetail() = capture("s320_album_detail", 1400) {
        val state = AlbumDetailUiState(
            isLoading = false,
            title = "Kind of Blue",
            artist = "Miles Davis",
            artistId = "ar1",
            year = "1959",
            genre = "Jazz",
            songCount = 5,
            totalDuration = "45:44",
            starVisible = true,
            rows = persistentListOf(AlbumDetailRow.Track("1", null, "So What", null, "3:00", false)),
        )
        AlbumDetailScreen(state = state, actions = AlbumDetailActions.Noop, currentTrackId = null)
    }

    @Test
    fun artistDetail() = capture("s320_artist_detail", 1400) {
        val state = ArtistDetailUiState(
            isLoading = false,
            artistId = "ar1",
            artistName = "Héroes del Silencio",
            albumCount = 3,
            popularTracks = persistentListOf(ArtistTrackUi("t1", "1", "Entre dos Tierras", "Senderos de Traición", "5:36", false)),
            albums = persistentListOf(ArtistAlbumUi("a1", null, "El Espíritu del Vino", "Héroes del Silencio", null)),
        )
        ArtistDetailScreen(state = state, actions = ArtistDetailActions.Noop)
    }

    @Test
    fun nowPlaying() = capture("s320_now_playing", 900) {
        val state = PlayerUiState(
            hasCurrentTrack = true,
            trackId = "t1",
            title = "Preludio y Fuga n.º 1 en do mayor, BWV 846",
            artist = "Johann Sebastian Bach",
            isPlaying = false,
            phase = PlaybackPhase.Ready,
            durationMs = 383_000L,
            canSeekToPrevious = true,
            canSeekToNext = true,
        )
        NowPlayingScreen(
            state = state,
            progress = PlaybackProgress(140_000L, 383_000L, 55),
            sleepTimerState = SleepTimerState.Off,
            actions = NowPlayingActions.Noop,
        )
    }

    @Test
    fun upNext() = capture("s320_up_next", 900) {
        val current = UpNextCurrentUi("Paranoid Android", "Radiohead", null, true)
        val upcoming = listOf("Karma Police", "Everything In Its Right Place With A Long Title").mapIndexed { i, t ->
            UpNextTrackUi("k$i", t, "Radiohead", 4 + i, emptyList())
        }
        UpNextScreen(
            state = UpNextUiState(current, upcoming),
            progressFraction = { 0.42f },
            artworkFor = { null },
            actions = UpNextActions.Noop,
        )
    }

    @Test
    fun lyrics() = capture("s320_lyrics", 900) {
        val lines = listOf(
            LyricsLineUi(0L, "¿Hasta cuándo de alto es esta estela?"),
            LyricsLineUi(4_000L, "Que siempre hay más y yo más quiero subir"),
            LyricsLineUi(8_000L, "¿Hasta dónde llegará esta pendiente?"),
        )
        LyricsScreen(
            state = LyricsUiState(
                trackId = "t1",
                title = "Hasta Cuándo (En Vivo)",
                artist = "La Renga",
                content = LyricsContent.Synced(lines),
            ),
            positionMs = { 5_000L },
            actions = LyricsActions({}, {}, {}),
        )
    }
}
