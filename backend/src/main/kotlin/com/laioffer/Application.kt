package com.laioffer

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.http.content.staticBasePackage
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondNullable
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class Album(
    val id: Int,
    @SerialName("album")
    val name: String,
    val year: String,
    val cover: String,
    val artists: String,
    val description: String
)

@Serializable
data class Section(
    @SerialName("section_title")
    val sectionTitle: String,
    val albums: List<Album>
)

@Serializable
data class Playlist(
    val id: Int,
    val songs: List<Song>
)

@Serializable
data class Song(
    val name: String,
    val lyric: String,
    val src: String,
    val length: String
)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
            }
        )
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        get("/feed") {
            val jsonString = resourceText("feed.json")
            call.respondText(jsonString ?: "", ContentType.Application.Json)
        }

        get("/playlists") {
            val jsonString = resourceText("playlists.json")
            call.respondText(jsonString ?: "", ContentType.Application.Json)
        }

        get("/playlist/{id}") {
            resourceText("playlists.json")?.let { jsonString ->
                val playlists = Json.decodeFromString(
                    ListSerializer(Playlist.serializer()),
                    jsonString
                )
                val id = call.parameters["id"]
                val playlist = playlists.firstOrNull { it.id.toString() == id }
                call.respondNullable(playlist)
            } ?: call.respondText("null", ContentType.Application.Json)
        }

        static("/") {
            staticBasePackage = "static"
            static("songs") {
                resources("songs")
            }
        }
    }
}

private fun resourceText(name: String): String? =
    Thread.currentThread().contextClassLoader.getResource(name)?.readText()
