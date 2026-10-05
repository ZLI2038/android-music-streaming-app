package io.github.zli2038.musicstreaming.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.zli2038.musicstreaming.datamodel.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface DatabaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun favoriteAlbum(album: Album)

    @Query("SELECT EXISTS(SELECT * FROM Album WHERE id = :id)")
    fun isFavoriteAlbum(id: Int): Flow<Boolean>

    @Delete
    suspend fun unFavoriteAlbum(album: Album)

    @Query("select * from Album")
    fun fetchFavoriteAlbums(): Flow<List<Album>>
}
