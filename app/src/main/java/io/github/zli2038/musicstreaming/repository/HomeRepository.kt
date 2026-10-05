package io.github.zli2038.musicstreaming.repository

import io.github.zli2038.musicstreaming.datamodel.Section
import io.github.zli2038.musicstreaming.network.NetworkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

class HomeRepository @Inject constructor(private val networkApi: NetworkApi) {
    suspend fun getHomeSections(): List<Section> = withContext(Dispatchers.IO) {
        delay(3000)
        networkApi.getHomeFeed().execute().body()!!
    }
}
