package com.laioffer

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reproducible local benchmark of the unchanged application module and bundled fixtures. */
class ResumeBenchmarkTest {
    @Test
    fun realNettyMixedRouteLoad() {
        val server = embeddedServer(Netty, port = 18080, host = "127.0.0.1", module = Application::module)
        server.start(wait = false)
        val workers = Executors.newFixedThreadPool(20)
        val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build()
        val paths = listOf("/feed", "/playlists", "/playlist/0", "/playlist/1",
            "/songs/solo.mp3", "/songs/LeeSSang_Let_s_Meet_Now.mp3")
        val requests = paths.map { HttpRequest.newBuilder(URI("http://127.0.0.1:18080$it"))
            .timeout(Duration.ofSeconds(10)).GET().build() }
        val results = mutableListOf<JsonElement>()
        try {
            val baseline = requests.map { client.send(it, HttpResponse.BodyHandlers.ofByteArray()) }
            baseline.forEach { assertEquals(200, it.statusCode()) }
            val resource = { name: String -> javaClass.classLoader.getResource(name)!!.readBytes() }
            assertEquals(Json.parseToJsonElement(String(resource("feed.json"))), Json.parseToJsonElement(String(baseline[0].body())))
            val playlists = Json.parseToJsonElement(String(resource("playlists.json"))).jsonArray
            assertEquals(playlists, Json.parseToJsonElement(String(baseline[1].body())))
            for (id in 0..1) assertEquals(playlists.first { it.jsonObject["id"]!!.jsonPrimitive.int == id },
                Json.parseToJsonElement(String(baseline[id + 2].body())))
            for (index in 4..5) {
                assertEquals(resource("static${paths[index]}").toList(), baseline[index].body().toList())
                assertEquals("audio/mpeg", baseline[index].headers().firstValue("content-type").get())
            }
            val expected = baseline.map { it.body() }
            fun request(index: Int): Long {
                val started = System.nanoTime()
                val response = client.send(requests[index], HttpResponse.BodyHandlers.ofByteArray())
                val duration = System.nanoTime() - started
                check(response.statusCode() == 200 && response.body().contentEquals(expected[index])) {
                    "Response failure or payload mismatch: ${paths[index]} ${response.statusCode()}"
                }
                return duration
            }
            repeat(600) { request(it % paths.size) }
            repeat(3) { round ->
                val ready = CountDownLatch(20)
                val go = CountDownLatch(1)
                val futures = (0 until 20).map { worker -> workers.submit(Callable {
                    ready.countDown(); go.await()
                    LongArray(500) { n -> request((n + worker) % paths.size) }
                }) }
                ready.await()
                val started = System.nanoTime(); go.countDown()
                val raw = futures.flatMap { it.get().toList() }
                val seconds = (System.nanoTime() - started) / 1e9
                val sorted = raw.sorted()
                fun percentile(p: Double) = sorted[(ceil(p * sorted.size).toInt() - 1).coerceAtLeast(0)] / 1e6
                val result = buildJsonObject {
                    put("round", round + 1); put("requests", raw.size); put("concurrency", 20)
                    put("errors", 0); put("seconds", seconds); put("requests_per_second", raw.size / seconds)
                    put("p50_ms", percentile(.50)); put("p95_ms", percentile(.95)); put("p99_ms", percentile(.99))
                    put("latency_ns", JsonArray(raw.map { JsonPrimitive(it) }))
                }
                results.add(result)
                println("RESUME_BACKEND round=${round + 1} requests=${raw.size} rps=${raw.size / seconds} p95_ms=${percentile(.95)}")
            }
            val file = File("build/resume-metrics/backend.json")
            file.parentFile.mkdirs()
            file.writeText(buildJsonObject {
                put("benchmark", "loopback HTTP/1.1; original fixture payloads; 600 warmups; no think time")
                put("java", System.getProperty("java.version")); put("os", System.getProperty("os.name"))
                put("paths", JsonArray(paths.map { JsonPrimitive(it) }))
                put("response_bytes", JsonArray(expected.map { JsonPrimitive(it.size) }))
                put("rounds", JsonArray(results))
            }.toString())
        } finally {
            workers.shutdownNow()
            server.stop(1000, 5000)
        }
    }
}
