package org.moire.ultrasonic.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import org.moire.ultrasonic.domain.Track

@Dao
@Entity(tableName = "tracks")
interface TrackDao : GenericDao<Track> {
    /**
     * Clear the whole database
     */
    @Query("DELETE FROM tracks")
    fun clear()

    /**
     * Get all tracks
     */
    @Query("SELECT * FROM tracks")
    fun get(): List<Track>

    /**
     * Get a single track by id
     */
    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    fun get(id: String): Track?

    /**
     * Get tracks by album, in disc/track order
     */
    @Query("SELECT * FROM tracks WHERE albumId LIKE :id ORDER BY discNumber, track")
    fun byAlbum(id: String): List<Track>

    /**
     * Clear tracks by album
     */
    @Query("DELETE FROM tracks WHERE albumId LIKE :id")
    fun clearByAlbum(id: String)

    /**
     * Get tracks by artist
     */
    @Query("SELECT * FROM tracks WHERE artistId LIKE :id")
    fun byArtist(id: String): List<Track>

    /**
     * Get tracks by genre
     */
    @Query("SELECT * FROM tracks WHERE genre LIKE :id ORDER BY title ASC LIMIT :offset,:size")
    fun byGenre(id: String, size: Int, offset: Int = 0): List<Track>
}
