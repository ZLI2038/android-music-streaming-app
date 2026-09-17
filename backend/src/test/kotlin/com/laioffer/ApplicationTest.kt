package com.laioffer

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `root route matches the document`() = testApplication {
        application { module() }

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello World!", response.bodyAsText())
    }

    @Test
    fun `feed exposes the documented field names and albums`() = testApplication {
        application { module() }

        val response = client.get("/feed")
        val sections = Json.decodeFromString(
            ListSerializer(Section.serializer()),
            response.bodyAsText()
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(sections.flatMap { it.albums }.any { it.name == "Still Fantasy" })
        assertTrue(sections.flatMap { it.albums }.any { it.name == "Hexagonal" })
    }

    @Test
    fun `playlist collection and detail use the same data`() = testApplication {
        application { module() }

        val allResponse = client.get("/playlists")
        val playlists = Json.decodeFromString(
            ListSerializer(Playlist.serializer()),
            allResponse.bodyAsText()
        )
        val detailResponse = client.get("/playlist/1")
        val detail = Json.decodeFromString(Playlist.serializer(), detailResponse.bodyAsText())

        assertEquals(HttpStatusCode.OK, allResponse.status)
        assertEquals(playlists.first { it.id == 1 }, detail)
        assertNotNull(detail.songs.firstOrNull())
    }

    @Test
    fun `song resources are served from the documented path`() = testApplication {
        application { module() }

        val response = client.get("/songs/solo.mp3")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Audio.MPEG, response.contentType()?.withoutParameters())
    }
}
