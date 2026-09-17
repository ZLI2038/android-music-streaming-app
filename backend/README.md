# Ktor Music Backend

A Ktor/Netty service supplying the Android demo's album feed, playlists, and synthetic audio resources.

| Method | Path | Response |
| --- | --- | --- |
| GET | `/` | Plain-text `Hello World!` |
| GET | `/feed` | Album sections from `feed.json` |
| GET | `/playlists` | Playlist collection from `playlists.json` |
| GET | `/playlist/{id}` | Matching playlist, or JSON `null` when no ID matches |
| GET | `/songs/{file}` | Static MP3 resource |

From the project root, with JDK 17 configured:

```bash
./gradlew :backend:run
```

The service listens on `0.0.0.0:8080`. The Android emulator accesses it through `10.0.2.2:8080`, which is also used in the fixture audio URLs.

## Verification

```bash
./gradlew :backend:test --tests com.laioffer.ApplicationTest
./gradlew :backend:test --tests com.laioffer.ResumeBenchmarkTest --rerun-tasks
```

The first class checks the greeting, JSON feed, playlist consistency, and audio content type. The second starts a separate Netty server on `127.0.0.1:18080`, validates the fixture responses, then records three 10,000-request rounds with 20 concurrent threads after 600 warmups.

Measurements are written to `backend/build/resume-metrics/backend.json`. Historical data and its limitations are described in [the benchmark notes](../docs/BENCHMARKS.md).

## Audio fixtures

`solo.mp3` and `LeeSSang_Let_s_Meet_Now.mp3` are three-second synthetic test sounds generated with FFmpeg. They test HTTP resource delivery and ExoPlayer integration; they are not copies of the named songs.
