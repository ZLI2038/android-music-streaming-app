# Benchmark methodology

The recorded measurements were collected on September 8, 2026. Raw samples and the summary are in [benchmarks/2026-09-08](benchmarks/2026-09-08/). The measured application code was not modified to improve the numbers.

## Environment

- Host: Apple M2 Pro, 16 GiB RAM, JDK 17.0.11.
- Device: Pixel 3a AVD, Android 14 / API 34, arm64-v8a, 1080 x 2220, 440 dpi, swangle_indirect rendering.
- Android build: debug. These are emulator measurements, not physical-device results.
- API benchmark: HTTP/1.1 over host loopback to a separate Netty instance on port 18080.
- Android audio and API calls: host backend at `10.0.2.2:8080`.

## What was measured

| Measurement | Procedure | Important limit |
| --- | --- | --- |
| Navigation | 20 cycles; click a Home album, select Favorites, click a favorite album, return Home; assert destination text is displayed after each transition | 80 checks on the bundled demo catalog |
| Persistence | Sequentially insert 1,000 synthetic albums into a separate file-backed Room database, close/reopen it, compare every record and field; repeat three times | Verifies database reopen, not device reboot or process-kill recovery |
| State propagation | Starting with 1,000 records, toggle one album 100 times per round through the production PlaylistViewModel; wait for both PlaylistViewModel and FavoriteViewModel StateFlows to match | 2 ms observation interval; includes scheduling, excludes rendered frames |
| HTTP latency | 600 warmups, then three rounds of 10,000 requests with 20 threads; rotate through feed, collection, two playlist IDs, and two audio URLs; validate every response body and status | Small fixtures, local network, and short runs; not sustained production capacity |
| Playback startup | Three rounds of 30 HTTP starts; alternate original audio fixtures; time load/play until player and ViewModel report playing and playback position advances | Reuses a player within each round; 3-second synthetic audio; not audible output latency |
| Seeking | Pause, seek alternately to 500 or 1,500 ms, wait 150 ms, compare reported player position and ViewModel state | 90 correctness checks with a 50 ms tolerance; not decoder seek latency |

The HTTP latency timer includes reading the complete response body and stops before the payload correctness assertion. Fixture bodies are validated against the bundled resources before measurement. Response sizes are 714, 457, 231, 251, 12,760, and 12,864 bytes.

## Results

| Metric | Result |
| --- | --- |
| Navigation | 80/80 checks passed |
| Persistence | 1,000/1,000 full records restored in each of three rounds |
| State propagation, 300 samples | P50 9.829 ms; P95 17.113 ms; maximum 29.352 ms |
| Playback startup, 90 samples | P50 151.584 ms; P95 412.264 ms; maximum 1,058.851 ms |
| Paused seeking | 90/90 checks passed |
| HTTP responses | 30,000 expected responses, no failures |

| HTTP round | Requests | Concurrency | Duration | P95 |
| --- | --- | --- | --- | --- |
| 1 | 10,000 | 20 | 1.047 s | 4.158 ms |
| 2 | 10,000 | 20 | 0.814 s | 3.072 ms |
| 3 | 10,000 | 20 | 0.689 s | 2.538 ms |

Percentiles use nearest rank: the sorted sample at `ceil(p * N)`. State and playback percentiles pool all three rounds. The README uses the worst HTTP round P95, rounded to one decimal place. Slow samples are retained; P95 is not a bound on every operation.

Eight distinct tests passed in the original verification: four backend contract tests, one Netty benchmark, two Android data/playback tests, and one Android navigation test. Repeated rounds are not counted as additional distinct tests.

The first navigation attempt was blocked by an Android System UI unresponsive dialog taking window focus. After dismissing the system dialog, the complete 20-cycle test was rerun and passed all 80 checks. That environment-blocked attempt is disclosed and is not counted as a passing navigation run. The three data/playback rounds retain all their samples.

## Reproduce

Configure JDK 17 and the Android SDK. Start the normal backend on port 8080 for Android tests, and start an emulator. Leave port 18080 free for the separate HTTP benchmark.

```bash
# Run backend contracts and build app/test APKs
./gradlew :backend:test --tests com.laioffer.ApplicationTest :app:assembleDebug :app:assembleDebugAndroidTest

# Run the HTTP benchmark separately from device latency tests
./gradlew :backend:test --tests com.laioffer.ResumeBenchmarkTest --rerun-tasks

# Install the Android app and tests on the emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Repeat this class three times to match the recorded measurement protocol
adb shell am instrument -w -r -e class com.laioffer.spotify.ResumeDataPlayerBenchmarkTest com.laioffer.spotify.test/androidx.test.runner.AndroidJUnitRunner

# Run the navigation test
adb shell am instrument -w -r -e class com.laioffer.spotify.ResumeNavigationBenchmarkTest com.laioffer.spotify.test/androidx.test.runner.AndroidJUnitRunner

# Recalculate the saved evidence without running the application
python3 scripts/verify_benchmark_evidence.py
```

The HTTP test writes `backend/build/resume-metrics/backend.json`. Android tests write JSON files under the target app's private `files/resume-metrics/` directory. Export each round before running the next because filenames are reused:

```bash
adb exec-out run-as com.laioffer.spotify cat files/resume-metrics/room.json > room-run-1.json
adb exec-out run-as com.laioffer.spotify cat files/resume-metrics/playback.json > playback-run-1.json
adb exec-out run-as com.laioffer.spotify cat files/resume-metrics/navigation.json > navigation.json
```

Use distinct output filenames for subsequent rounds. Device and host load affect results; reproductions need not match the original timings exactly.

The suite checks a demo application. It does not establish user capacity, production availability, all error paths, or a before/after performance improvement. Home's intentional three-second delay is still present and is separate from API response latency.
