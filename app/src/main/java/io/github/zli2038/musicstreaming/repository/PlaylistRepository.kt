package io.github.zli2038.musicstreaming.repository

import io.github.zli2038.musicstreaming.datamodel.Playlist
import io.github.zli2038.musicstreaming.network.NetworkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaylistRepository @Inject constructor(private val networkApi: NetworkApi) {
    suspend fun getPlaylist(id: Int): Playlist = withContext(Dispatchers.IO) {
        networkApi.getPlaylist(id).execute().body()!!
    }
}
