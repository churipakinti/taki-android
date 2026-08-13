package org.moire.ultrasonic.data

import androidx.room.Dao
import androidx.room.Query
import org.moire.ultrasonic.domain.Album

@Dao
interface AlbumDao : GenericDao<Album> {
    /**
     * Clear the whole database
     */
    @Query("DELETE FROM albums")
    fun clear()

    /**
     * Get all albums
     */
    @Query("SELECT * FROM albums")
    fun get(): List<Album>

    /**
     * Get all albums in a specific range
     */
    @Query("SELECT * FROM albums LIMIT :offset,:size")
    fun get(size: Int, offset: Int = 0): List<Album>

    /**
     * Get all albums in a specific range in a certain order
     */
    @Query("SELECT * FROM albums ORDER BY artist ASC LIMIT :offset,:size ")
    fun orderedByArtist(size: Int, offset: Int = 0): List<Album>

    /**
     * Get all albums in a specific range in a certain order
     */
    @Query("SELECT * FROM albums ORDER BY created DESC LIMIT :offset,:size ")
    fun orderedByAge(size: Int, offset: Int = 0): List<Album>

    /**
     * Get all albums in a specific range in a certain order
     */
    @Query("SELECT * FROM albums ORDER BY title ASC LIMIT :offset,:size ")
    fun orderedByName(size: Int, offset: Int = 0): List<Album>

    /**
     * Get album by id
     */
    @Query("SELECT * FROM albums where id LIKE :albumId LIMIT 1")
    fun get(albumId: String): Album?

    /**
     * Get albums by artist
     */
    @Query("SELECT * FROM albums WHERE artistId LIKE :id")
    fun byArtist(id: String): List<Album>

    /**
     * Clear albums by artist
     */
    @Query("DELETE FROM albums WHERE artistId LIKE :id")
    fun clearByArtist(id: String)

    /**
     * Clear albums by artist
     */
    @Query("DELETE FROM albums WHERE id LIKE :id")
    fun delete(id: String)

    /**
     * Get albums by genre
     */
    @Query("SELECT * FROM albums WHERE genre LIKE :id ORDER BY title ASC LIMIT :offset,:size")
    fun byGenre(id: String, size: Int, offset: Int = 0): List<Album>

    /**
     * Get list of genres from albums.
     */
    @Query("SELECT DISTINCT genre FROM albums ORDER BY genre ASC")
    fun getGenres(): List<String>

    /**
     * Collections/Box Sets (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md). Updates only the
     * `grouping` column instead of a full upsert, since the caller (CachedMusicService.
     * getAlbumAsDir) only has the album's tracks at that point, not a full Album row to upsert -
     * see CachedMusicService for why this is derived from tracks rather than a second network
     * call. No-op if the album row doesn't exist yet (e.g. its Album entity was never separately
     * fetched/cached) - that's fine, CollectionResolver only needs this for albums it already
     * knows about.
     */
    @Query("UPDATE albums SET grouping = :grouping WHERE id = :albumId")
    fun updateGrouping(albumId: String, grouping: String)

    /**
     * Albums with a known, non-blank grouping - the input to [org.moire.ultrasonic.util.
     * CollectionResolver]. Room can't express "not null and not empty" with a single null check
     * since grouping is TEXT, hence the explicit `!= ''`.
     */
    @Query("SELECT * FROM albums WHERE grouping IS NOT NULL AND grouping != ''")
    fun withGrouping(): List<Album>
}
