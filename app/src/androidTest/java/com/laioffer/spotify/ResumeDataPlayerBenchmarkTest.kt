package com.laioffer.spotify

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.laioffer.spotify.database.AppDatabase
import com.laioffer.spotify.datamodel.Album
import com.laioffer.spotify.datamodel.Song
import com.laioffer.spotify.network.NetworkModule
import com.laioffer.spotify.player.PlayerViewModel
import com.laioffer.spotify.repository.FavoriteAlbumRepository
import com.laioffer.spotify.repository.PlaylistRepository
import com.laioffer.spotify.ui.favorite.FavoriteViewModel
import com.laioffer.spotify.ui.playlist.PlaylistViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class ResumeDataPlayerBenchmarkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun <T : ViewModel> retained(store: ViewModelStore, type: Class<T>, create: () -> T): T =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        }).get(type)
    private fun <T> main(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun await(label: String, timeout: Long = 10000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < end) { "Timed out: $label" }
            SystemClock.sleep(2)
        }
    }
    private fun save(name: String, value: JSONObject) {
        val file = File(context.filesDir, "resume-metrics/$name.json")
        file.parentFile!!.mkdirs(); file.writeText(value.toString(2))
        println("RESUME_METRIC $name $value")
    }
    private fun stats(values: List<Double>) = JSONObject().apply {
        val sorted = values.sorted()
        put("n", values.size); put("raw_ms", JSONArray(values))
        put("p50_ms", sorted[ceil(sorted.size * .5).toInt() - 1])
        put("p95_ms", sorted[ceil(sorted.size * .95).toInt() - 1])
        put("max_ms", sorted.last())
    }

    @Test fun roomPersistenceAndViewModelSynchronization() {
        val databaseName = "resume-benchmark-${System.nanoTime()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        var db = open()
        val store = ViewModelStore()
        try {
            val albums = (1000 until 2000).map {
                Album(it, "Benchmark Album $it", "2026", "", "Synthetic Artist $it", "Synthetic persistence fixture")
            }
            var repository = FavoriteAlbumRepository(db.databaseDao())
            val writeStart = System.nanoTime()
            runBlocking { albums.forEach { repository.favoriteAlbum(it) } }
            val writeMs = (System.nanoTime() - writeStart) / 1e6
            assertEquals(albums.toSet(), runBlocking { repository.fetchFavoriteAlbums().first() }.toSet())
            db.close()
            val reopenStart = System.nanoTime()
            db = open(); repository = FavoriteAlbumRepository(db.databaseDao())
            val restored = runBlocking { repository.fetchFavoriteAlbums().first() }
            val reopenMs = (System.nanoTime() - reopenStart) / 1e6
            assertEquals(albums.size, restored.size)
            assertEquals(albums.toSet(), restored.toSet())

            val api = NetworkModule.provideApi(NetworkModule.provideRetrofit())
            val album = api.getHomeFeed().execute().body()!!.first().albums.first { it.id == 0 }
            val favorite = main { retained(store, FavoriteViewModel::class.java) { FavoriteViewModel(repository) } }
            val playlist = main { retained(store, PlaylistViewModel::class.java) {
                PlaylistViewModel(PlaylistRepository(api), repository)
            }.also { it.fetchPlaylist(album) } }
            await("initial snapshots") { favorite.uiState.value.albums.size == 1000 && playlist.uiState.value.playlist.isNotEmpty() }
            val latencies = mutableListOf<Double>()
            repeat(100) { iteration ->
                val expected = iteration % 2 == 0
                val start = System.nanoTime()
                main { playlist.toggleFavorite(expected) }
                await("both ViewModels after mutation $iteration") {
                    playlist.uiState.value.isFavorite == expected &&
                        favorite.uiState.value.albums.any { it.id == album.id } == expected
                }
                latencies.add((System.nanoTime() - start) / 1e6)
            }
            assertEquals(1000, runBlocking { repository.fetchFavoriteAlbums().first() }.size)
            save("room", JSONObject().apply {
                put("fixture_records", 1000); put("restored_records", restored.size)
                put("all_record_fields_equal", true); put("sequential_insert_ms", writeMs)
                put("database_reopen_and_read_ms", reopenMs)
                put("synchronization", stats(latencies)); put("successful_mutations", latencies.size)
                put("measurement", "toggleFavorite call through Room and Flow until both existing ViewModel StateFlows match; 1000 synthetic records; excludes rendered frames")
            })
        } finally { main { store.clear() }; db.close(); context.deleteDatabase(databaseName) }
    }

    @Test fun actualHttpPlaybackAndSeek() {
        val store = ViewModelStore()
        val player = main { ExoPlayer.Builder(context).build() }
        val viewModel = main { retained(store, PlayerViewModel::class.java) { PlayerViewModel(player) } }
        val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
        main { player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { errors.add(error.toString()) }
        }) }
        val starts = mutableListOf<Double>()
        val seekErrors = mutableListOf<Long>()
        val album = Album(0, "Benchmark", "2026", "", "Test", "Original bundled 3-second audio")
        try {
            repeat(30) { iteration ->
                val path = if (iteration % 2 == 0) "solo.mp3" else "LeeSSang_Let_s_Meet_Now.mp3"
                val song = Song(path, "", "http://10.0.2.2:8080/songs/$path", "0:03")
                val start = System.nanoTime()
                main { viewModel.load(song, album); viewModel.play() }
                await("playing HTTP audio $iteration") {
                    check(errors.isEmpty()) { errors.toString() }
                    main { player.isPlaying && player.currentPosition > 0 && viewModel.uiState.value.isPlaying }
                }
                starts.add((System.nanoTime() - start) / 1e6)
                main { viewModel.pause() }
                await("pause") { main { !player.isPlaying } }
                val target = if (iteration % 2 == 0) 1500L else 500L
                main { viewModel.seekTo(target) }
                // Seek requests update the API position synchronously; verify while paused after settling.
                // This is a correctness check, deliberately not a decoder seek-latency benchmark.
                SystemClock.sleep(150)
                val error = main { kotlin.math.abs(player.currentPosition - target) }
                assertTrue("Seek outside tolerance: $error", error <= 50)
                assertEquals(target, viewModel.uiState.value.currentMs)
                seekErrors.add(error)
            }
            assertTrue(errors.isEmpty())
            save("playback", JSONObject().apply {
                put("startup", stats(starts)); put("successful_http_starts", starts.size)
                put("successful_paused_seeks", seekErrors.size); put("seek_position_error_ms", JSONArray(seekErrors))
                put("playback_errors", JSONArray(errors)); put("audio_sources", 2)
                put("measurement", "reused player; original local HTTP 3-second fixtures; load+play until isPlaying and currentPosition>0; not audible output latency; seeks checked after 150ms while paused")
            })
        } finally { main { store.clear(); player.release() } }
    }
}
