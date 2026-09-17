#!/usr/bin/env python3
"""Recompute public benchmark summaries from retained raw samples; stdlib only."""

import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "docs" / "benchmarks" / "2026-09-08"


def read(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


def p95(values):
    ordered = sorted(values)
    return ordered[math.ceil(0.95 * len(ordered)) - 1]


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    summary = read("summary.json")
    backend = read("backend.json")
    rooms = [read(f"room-run-{i}.json") for i in range(1, 4)]
    players = [read(f"playback-run-{i}.json") for i in range(1, 4)]
    navigation = read("navigation.json")

    transitions = navigation["transitions"]
    check(len(transitions) == 80, "Expected 80 navigation checks")
    check(all(item["passed"] for item in transitions), "Navigation failure")
    check(len({item["cycle"] for item in transitions}) == 20, "Expected 20 cycles")
    check(navigation["screen_types"] == 3, "Expected three screen types")

    for room in rooms:
        check(room["fixture_records"] == room["restored_records"] == 1000,
              "Persistence record count mismatch")
        check(room["all_record_fields_equal"], "Persistence field mismatch")
        check(room["successful_mutations"] == 100, "Mutation count mismatch")
    state_samples = [v for room in rooms for v in room["synchronization"]["raw_ms"]]
    check(len(state_samples) == 300, "Expected 300 state samples")
    check(p95(state_samples) == summary["state_sync"]["p95_ms"], "State P95 mismatch")

    for player in players:
        check(player["successful_http_starts"] == player["successful_paused_seeks"] == 30,
              "Playback operation count mismatch")
        check(not player["playback_errors"], "Playback errors")
    playback_samples = [v for player in players for v in player["startup"]["raw_ms"]]
    seek_errors = [v for player in players for v in player["seek_position_error_ms"]]
    check(len(playback_samples) == len(seek_errors) == 90, "Expected 90 playback/seek samples")
    check(p95(playback_samples) == summary["playback_start"]["p95_ms"], "Playback P95 mismatch")
    check(all(v <= 50 for v in seek_errors), "Seek exceeded the position tolerance")

    check(len(backend["rounds"]) == 3, "Expected three HTTP rounds")
    for round_data in backend["rounds"]:
        check(round_data["requests"] == len(round_data["latency_ns"]) == 10000,
              "HTTP sample count mismatch")
        check(round_data["concurrency"] == 20 and round_data["errors"] == 0,
              "Unexpected HTTP concurrency or failures")
        check(p95(round_data["latency_ns"]) / 1e6 == round_data["p95_ms"],
              "HTTP round P95 mismatch")
    worst_p95 = max(r["p95_ms"] for r in backend["rounds"])
    check(worst_p95 == summary["backend"]["worst_round_p95_ms"], "Worst-round P95 mismatch")

    print("Verified: 80 navigation checks; 3 x 1,000 persistence records; 300 state mutations;")
    print("90 playback starts and seeks; 30,000 HTTP responses at concurrency 20.")
    print(f"State P95: {p95(state_samples):.6f} ms")
    print(f"Playback P95: {p95(playback_samples):.6f} ms")
    print(f"Worst HTTP round P95: {worst_p95:.6f} ms")
    print("This validates saved evidence; it does not rerun device or HTTP benchmarks.")


if __name__ == "__main__":
    main()
