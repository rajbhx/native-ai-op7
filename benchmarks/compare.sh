#!/usr/bin/env bash
# Regression comparison for on-device benchmark results (playbook Phase 8).
#
# Reads two JSONL result files (produced by benchmarks/sweep.sh), groups the
# kind=bench rows by (provider, category, threads, ctx, gpu_layers), compares
# median tokens/sec, median first-token latency and peak RSS, and exits:
#   0 = no regression    1 = regression(s) found    2 = usage/data error
#
# Usage: benchmarks/compare.sh [--tolerance-pct N] [--rss-ceiling-mb N]
#                              [--rss-tolerance-pct N] [--first-token-pct N]
#                              [--self-test] <baseline.jsonl> <candidate.jsonl>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOLERANCE_PCT=10
RSS_CEILING_MB=1536
RSS_TOLERANCE_PCT=5
FIRST_TOKEN_PCT=20
SELF_TEST=0

die() { echo "error: $*" >&2; exit 2; }

usage() {
    sed -n '2,10p' "$0" | sed 's/^# \?//'
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --tolerance-pct) TOLERANCE_PCT="${2:?missing value}"; shift 2 ;;
        --rss-ceiling-mb) RSS_CEILING_MB="${2:?missing value}"; shift 2 ;;
        --rss-tolerance-pct) RSS_TOLERANCE_PCT="${2:?missing value}"; shift 2 ;;
        --first-token-pct) FIRST_TOKEN_PCT="${2:?missing value}"; shift 2 ;;
        --self-test) SELF_TEST=1; shift ;;
        -h|--help) usage ;;
        -*) die "unknown argument: $1 (try --help)" ;;
        *) break ;;
    esac
done

if [ "$SELF_TEST" = 1 ]; then
    [ $# -eq 0 ] || die "--self-test takes no positional arguments"
    BASELINE_FIXTURE="${SCRIPT_DIR}/tests/fixtures/baseline.jsonl"
    CANDIDATE_OK="${SCRIPT_DIR}/tests/fixtures/candidate-ok.jsonl"
    CANDIDATE_REGRESS="${SCRIPT_DIR}/tests/fixtures/candidate-regress.jsonl"
    for f in "$BASELINE_FIXTURE" "$CANDIDATE_OK" "$CANDIDATE_REGRESS"; do
        [ -f "$f" ] || die "fixture missing: $f"
    done
    python3 - "$BASELINE_FIXTURE" "$CANDIDATE_OK" "$CANDIDATE_REGRESS" \
        "$TOLERANCE_PCT" "$RSS_CEILING_MB" "$RSS_TOLERANCE_PCT" "$FIRST_TOKEN_PCT" <<'PY'
import json
import sys

baseline, candidate_ok, candidate_regress, *opts = sys.argv[1:]


def analyze(baseline_path, candidate_path, opts):
    """Returns (exit_code, output_lines). Exit codes match the shell wrapper."""
    import statistics
    from collections import defaultdict

    tol_pct, rss_ceiling_mb, rss_tol_pct, first_pct = (
        float(opts[0]), int(opts[1]), float(opts[2]), float(opts[3])
    )
    rss_ceiling = rss_ceiling_mb * 1024 * 1024

    def load(path):
        rows = []
        try:
            fh = open(path)
        except FileNotFoundError:
            return None
        with fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if obj.get("kind") == "bench":
                    rows.append(obj)
        return rows

    def median(values):
        values = [v for v in values if v is not None]
        if not values:
            return None
        return statistics.median(values)

    baseline_rows = load(baseline_path)
    candidate_rows = load(candidate_path)
    if baseline_rows is None or candidate_rows is None:
        return 2, ["error: missing result file"]

    def group(rows):
        grouped = defaultdict(list)
        for r in rows:
            key = (
                r.get("provider_id"),
                r.get("category"),
                r.get("threads"),
                r.get("n_ctx"),
                r.get("gpu_layers"),
            )
            grouped[key].append(r)
        return grouped

    base = group(baseline_rows)
    cand = group(candidate_rows)

    def delta_pct(b, c):
        if not b:
            return None
        return (c - b) / b * 100.0

    def fmt(value, suffix=""):
        if value is None:
            return "n/a"
        return f"{value:.3f}{suffix}"

    def fmt_mi(value):
        if value is None:
            return "n/a"
        return f"{value / 1048576:.1f} MiB"

    lines = []
    lines.append(
        "provider|category|threads|ctx|gpu  base_tps  cand_tps  delta%   "
        "base_ft  cand_ft   base_rss      cand_rss"
    )
    problems = []
    for key in sorted(set(base) | set(cand)):
        provider, category, threads, ctx, gpu = key
        b_rows = base.get(key, [])
        c_rows = cand.get(key, [])
        b_tps = median([r.get("tokens_per_sec") for r in b_rows])
        c_tps = median([r.get("tokens_per_sec") for r in c_rows])
        b_first = median([r.get("first_token_ms") for r in b_rows])
        c_first = median([r.get("first_token_ms") for r in c_rows])
        b_rss = max((r.get("rss_bytes") or 0 for r in b_rows), default=0) or None
        c_rss = max((r.get("rss_bytes") or 0 for r in c_rows), default=0) or None
        tps_delta = delta_pct(b_tps, c_tps)
        first_delta = delta_pct(b_first, c_first)

        issues = []
        if c_tps is not None and tps_delta is not None and tps_delta < -tol_pct:
            issues.append(f"tokens/sec {tps_delta:+.1f}% (threshold -{tol_pct:.0f}%)")
        if c_first is not None and first_delta is not None and first_delta > first_pct:
            issues.append(f"first-token {first_delta:+.1f}% (threshold +{first_pct:.0f}%)")
        if c_rss is not None:
            if c_rss > rss_ceiling:
                issues.append(f"RSS {fmt_mi(c_rss)} over ceiling {rss_ceiling_mb} MiB")
            elif b_rss is not None and c_rss > b_rss * (1 + rss_tol_pct / 100):
                issues.append(
                    f"RSS {fmt_mi(c_rss)} > +{rss_tol_pct:.0f}% vs baseline {fmt_mi(b_rss)}"
                )
        if issues:
            problems.append((key, issues))

        key_label = f"{provider}|{category}|{threads}|{ctx}|{gpu}"
        lines.append(
            f"{key_label:<38} {fmt(b_tps):>8} {fmt(c_tps):>8} "
            f"{'n/a' if tps_delta is None else f'{tps_delta:+.1f}%':>7} "
            f"{fmt(b_first, 'ms'):>9} {fmt(c_first, 'ms'):>9} "
            f"{fmt_mi(b_rss):>13} {fmt_mi(c_rss):>13}"
        )
        if issues:
            lines.append(f"    REGRESSION: {'; '.join(issues)}")

    if problems:
        lines.append(f"verdict: FAIL — {len(problems)} config(s) regressed")
        return 1, lines
    lines.append("verdict: PASS — no regressions within tolerances")
    return 0, lines


def run_case(name, baseline_path, candidate_path, expected, opts):
    code, lines = analyze(baseline_path, candidate_path, opts)
    ok = code == expected
    print(f"[{'PASS' if ok else 'FAIL'}] {name} (expected exit {expected}, got {code})")
    if not ok:
        for line in lines[-5:]:
            print("   " + line)
    return ok


results = []
results.append(run_case("candidate-ok      -> exit 0", baseline, candidate_ok, 0, opts))
results.append(run_case("candidate-regress -> exit 1", baseline, candidate_regress, 1, opts))
results.append(run_case("missing candidate -> exit 2", baseline, "/nonexistent-benchmark.jsonl", 2, opts))
sys.exit(0 if all(results) else 1)
PY
    exit $?
fi

[ $# -eq 2 ] || usage
BASELINE="$1"
CANDIDATE="$2"
[ -f "$BASELINE" ] || die "baseline not found: $BASELINE"
[ -f "$CANDIDATE" ] || die "candidate not found: $CANDIDATE"

python3 - "$BASELINE" "$CANDIDATE" "$TOLERANCE_PCT" "$RSS_CEILING_MB" \
    "$RSS_TOLERANCE_PCT" "$FIRST_TOKEN_PCT" <<'PY'
import json
import statistics
import sys
from collections import defaultdict

baseline_path, candidate_path = sys.argv[1], sys.argv[2]
tol_pct, rss_ceiling_mb, rss_tol_pct, first_pct = (
    float(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5]), float(sys.argv[6])
)
rss_ceiling = rss_ceiling_mb * 1024 * 1024


def load(path):
    rows = []
    try:
        fh = open(path)
    except FileNotFoundError:
        print(f"error: missing result file: {path}", file=sys.stderr)
        sys.exit(2)
    with fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("kind") == "bench":
                rows.append(obj)
    return rows


def median(values):
    values = [v for v in values if v is not None]
    if not values:
        return None
    return statistics.median(values)


def group(rows):
    grouped = defaultdict(list)
    for r in rows:
        key = (
            r.get("provider_id"),
            r.get("category"),
            r.get("threads"),
            r.get("n_ctx"),
            r.get("gpu_layers"),
        )
        grouped[key].append(r)
    return grouped


base = group(load(baseline_path))
cand = group(load(candidate_path))


def delta_pct(b, c):
    if not b:
        return None
    return (c - b) / b * 100.0


def fmt(value, suffix=""):
    if value is None:
        return "n/a"
    return f"{value:.3f}{suffix}"


def fmt_mi(value):
    if value is None:
        return "n/a"
    return f"{value / 1048576:.1f} MiB"


print(f"baseline: {baseline_path}")
print(f"candidate: {candidate_path}")
print(
    "provider|category|threads|ctx|gpu  base_tps  cand_tps  delta%   "
    "base_ft  cand_ft   base_rss      cand_rss"
)
problems = []
for key in sorted(set(base) | set(cand)):
    provider, category, threads, ctx, gpu = key
    b_rows = base.get(key, [])
    c_rows = cand.get(key, [])
    b_tps = median([r.get("tokens_per_sec") for r in b_rows])
    c_tps = median([r.get("tokens_per_sec") for r in c_rows])
    b_first = median([r.get("first_token_ms") for r in b_rows])
    c_first = median([r.get("first_token_ms") for r in c_rows])
    b_rss = max((r.get("rss_bytes") or 0 for r in b_rows), default=0) or None
    c_rss = max((r.get("rss_bytes") or 0 for r in c_rows), default=0) or None
    tps_delta = delta_pct(b_tps, c_tps)
    first_delta = delta_pct(b_first, c_first)

    issues = []
    if c_tps is not None and tps_delta is not None and tps_delta < -tol_pct:
        issues.append(f"tokens/sec {tps_delta:+.1f}% (threshold -{tol_pct:.0f}%)")
    if c_first is not None and first_delta is not None and first_delta > first_pct:
        issues.append(f"first-token {first_delta:+.1f}% (threshold +{first_pct:.0f}%)")
    if c_rss is not None:
        if c_rss > rss_ceiling:
            issues.append(f"RSS {fmt_mi(c_rss)} over ceiling {rss_ceiling_mb} MiB")
        elif b_rss is not None and c_rss > b_rss * (1 + rss_tol_pct / 100):
            issues.append(
                f"RSS {fmt_mi(c_rss)} > +{rss_tol_pct:.0f}% vs baseline {fmt_mi(b_rss)}"
            )
    if issues:
        problems.append((key, issues))

    key_label = f"{provider}|{category}|{threads}|{ctx}|{gpu}"
    print(
        f"{key_label:<38} {fmt(b_tps):>8} {fmt(c_tps):>8} "
        f"{'n/a' if tps_delta is None else f'{tps_delta:+.1f}%':>7} "
        f"{fmt(b_first, 'ms'):>9} {fmt(c_first, 'ms'):>9} "
        f"{fmt_mi(b_rss):>13} {fmt_mi(c_rss):>13}"
    )
    for issue in issues:
        print(f"    REGRESSION: {issue}")

if problems:
    print(f"verdict: FAIL — {len(problems)} config(s) regressed")
    sys.exit(1)
print("verdict: PASS — no regressions within tolerances")
PY
