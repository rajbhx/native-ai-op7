# On-device benchmark harness (Phase 7 sweep + Phase 8 regression)

Scripted cold-start + sustained-inference sweep over the ROADMAP Phase 7
matrix, plus a regression comparator for baseline-vs-candidate decisions.
Methodology follows the playbook (`docs/07-on-device-benchmarking.md` in the
playbook repo): real device only, COLD launches, idle/contended labeling,
median-of-repeats, and the ±5% memory/stability decision rule.

## Files

| Path | Purpose |
|---|---|
| `benchmarks/config.sh` | Sweep matrix + defaults (threads, ctx, gpu layers, tokens, categories, repeats) |
| `benchmarks/sweep.sh` | adb orchestration: per-cell COLD launch, logcat capture, JSONL output |
| `benchmarks/compare.sh` | Baseline vs candidate regression check (exit 0/1/2) + `--self-test` |
| `app/src/main/java/com/engine/nativeai/BenchmarkReporter.kt` | JSONL row builder (pure Kotlin, unit-tested) |
| `app/src/main/java/com/engine/nativeai/MainActivity.kt` | Headless bench mode behind the `bench=1` intent extra |
| `benchmarks/tests/fixtures/` | Static result files the CI self-test runs against |

## JSONL schema (one JSON object per line)

- `{"kind":"meta", ...}` — run metadata (device, label, matrix, apk sha256).
- `{"kind":"bench", ...}` — one measured inference: `provider_id`, `category`,
  `threads`, `n_ctx`, `gpu_layers`, `tokens`, `duration_ms`, `tokens_per_sec`,
  `first_token_ms`, `rss_bytes`, `kv_type_k/v`, `ok`, optional `error`.
- `{"kind":"cell", ...}` — per-cell bookkeeping: `launch_state`,
  `cold_start_ms` (am start -W TotalTime), `pss_kb` (best-effort dumpsys).
- `{"kind":"done", ...}` — terminal marker the sweep polls for (logcat only,
  not written to the result file).

## Prerequisites

- OnePlus 7 on adb (`adb devices`), app installed, and a GGUF model present
  at `filesDir/models/model.gguf` (install via the in-app downloader first).
- Use the CI debug APK restored with the persistent keystore so installs
  update in place (no app-data wipe).
- Device state per playbook 07: phone idle ≥ 2 min before runs, fixed
  brightness, airplane mode for CPU/startup cells. If the device is in use,
  label the run `contended`.

## Usage

```bash
# sanity: what the matrix would launch (no device needed)
benchmarks/sweep.sh --dry-run

# one-cell smoke run (fast; ~1 min/cell with a local model)
benchmarks/sweep.sh --run-id smoke-$(date +%s) \
  --threads 4 --ctx 2048 --gpu-layers 0 \
  --tokens 32 --categories reasoning --repeats 1

# full baseline sweep (12 cells x repeats; hours on-device)
benchmarks/sweep.sh --run-id baseline-2026-08-15 --label idle --repeats 3

# regression check against a previous run
benchmarks/compare.sh benchmarks/results/baseline-2026-08-15.jsonl \
  benchmarks/results/candidate-...jsonl
```

Overrides: `--threads "2 3 4 6" --ctx "512 1024 2048" --gpu-layers "0"`,
`--tokens N` (generated tokens per category), `--categories a,b`,
`--repeats N`, `--timeout-s N`, `--apk PATH`, `--adb SERIAL`, `--out PATH`.
The same values can be set as env vars before sourcing `config.sh`.

## Regression rules (compare.sh)

- Grouped by `(provider, category, threads, ctx, gpu_layers)`; medians across
  repeats; RSS compared at peak.
- Defaults: fail when tokens/sec drops >10%, first-token latency rises >20%,
  peak RSS exceeds the 1536 MiB ceiling, or RSS grows >5% vs baseline.
- Tune with `--tolerance-pct`, `--first-token-pct`, `--rss-ceiling-mb`,
  `--rss-tolerance-pct`. Exit codes: 0 no regression, 1 regression, 2 error.
- Decision rule (playbook 07): keep an optimization only if the primary
  metric improves AND memory/battery/stability stay within ±5%; one metric
  improving while another regresses = reject. Never optimize on assumptions
  or fabricated numbers.

## Field-note loop (Phase 8)

After every measured sweep: commit `benchmarks/results/<run-id>.jsonl`, add a
session digest under `docs/field-notes/sessions/`, update
`docs/field-notes/log.yml` (A27+) for any problem hit, and flip the ROADMAP
Phase 7/8 status once the sweep + regression gate are green.
