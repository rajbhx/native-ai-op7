#!/usr/bin/env bash
# Sweep matrix + defaults shared by the on-device benchmark harness
# (playbook Phase 7/8). Sourced by sweep.sh; every value can be overridden
# with an environment variable or a sweep.sh CLI flag.
# shellcheck disable=SC2034 # sourced by sweep.sh, not executed standalone

# App under test.
BENCH_PKG="com.engine.nativeai"
BENCH_ACTIVITY="${BENCH_PKG}/.MainActivity"

# logcat tag the app emits structured NATIVEAI_BENCH rows under.
BENCH_TAG="NATIVEAI_BENCH"

# Sweep matrix (space-separated lists, playbook 07 + ROADMAP Phase 7).
BENCH_THREADS="${BENCH_THREADS:-2 3 4 6}"
BENCH_CTX="${BENCH_CTX:-512 1024 2048}"
BENCH_GPU_LAYERS="${BENCH_GPU_LAYERS:-0}"

# Per-cell benchmark parameters.
BENCH_TOKENS="${BENCH_TOKENS:-128}"
BENCH_CATEGORIES="${BENCH_CATEGORIES:-reasoning,context}"
BENCH_REPEATS="${BENCH_REPEATS:-1}"

# Capture / process limits.
BENCH_CELL_TIMEOUT_S="${BENCH_CELL_TIMEOUT_S:-600}"
BENCH_POLL_INTERVAL_S="${BENCH_POLL_INTERVAL_S:-2}"
BENCH_RSS_CEILING_MB="${BENCH_RSS_CEILING_MB:-1536}"
