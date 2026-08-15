#!/usr/bin/env bash
# On-device benchmark sweep for native-ai-op7 (playbook Phase 7/8).
#
# Drives the headless bench mode in MainActivity over the matrix in
# config.sh (threads x context x gpu_layers), one cold activity launch per
# cell, and appends one JSONL row per measured run to an output file.
#
# Transport: every device command routes through a `remote()` shim.
#   - auto (default): prefer a real adb binary on PATH, else the shizuku
#     wrapper (UID 2000, same identity as adb shell) for proot environments.
#   - adb:      require a real adb (adb version reports Android Debug Bridge).
#   - shizuku:  require the shizuku wrapper (device-local, no serial).
#
# Usage: benchmarks/sweep.sh [--run-id ID] [--label idle|contended]
#                            [--transport auto|adb|shizuku]
#                            [--apk PATH] [--adb SERIAL] [--out PATH]
#                            [--threads "2 3 4 6"] [--ctx "512 1024 2048"]
#                            [--gpu-layers "0"] [--tokens N]
#                            [--categories a,b] [--repeats N] [--timeout-s N]
#                            [--dry-run]
#
# On-device protocol (playbook docs/07-on-device-benchmarking.md):
#   - one COLD launch per cell (force-stop, verify pid gone, am start -W)
#   - runs are labelled idle/contended; label every captured row
#   - 3-5 repeats for a real baseline; the harness reports the raw rows and
#     compare.sh decides regressions (median over repeats).
set -euo pipefail
# shellcheck source=config.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"

RUN_ID=""
LABEL="idle"
APK_PATH=""
OUT_FILE=""
ADB_SERIAL=""
DRY_RUN=0
THREADS="$BENCH_THREADS"
CTX_VALUES="$BENCH_CTX"
GPU_LAYERS="$BENCH_GPU_LAYERS"
TOKENS="$BENCH_TOKENS"
CATEGORIES="$BENCH_CATEGORIES"
REPEATS="$BENCH_REPEATS"
CELL_TIMEOUT_S="$BENCH_CELL_TIMEOUT_S"
POLL_INTERVAL_S="$BENCH_POLL_INTERVAL_S"

die() { echo "error: $*" >&2; exit 1; }

usage() {
    sed -n '2,22p' "$0" | sed 's/^# \?//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        --run-id) RUN_ID="${2:?missing value}"; shift 2 ;;
        --label) LABEL="${2:?missing value}"; shift 2 ;;
        --transport) BENCH_TRANSPORT="${2:?missing value}"; shift 2 ;;
        --apk) APK_PATH="${2:?missing value}"; shift 2 ;;
        --out) OUT_FILE="${2:?missing value}"; shift 2 ;;
        --adb) ADB_SERIAL="${2:?missing value}"; shift 2 ;;
        --threads) THREADS="${2:?missing value}"; shift 2 ;;
        --ctx) CTX_VALUES="${2:?missing value}"; shift 2 ;;
        --gpu-layers) GPU_LAYERS="${2:?missing value}"; shift 2 ;;
        --tokens) TOKENS="${2:?missing value}"; shift 2 ;;
        --categories) CATEGORIES="${2:?missing value}"; shift 2 ;;
        --repeats) REPEATS="${2:?missing value}"; shift 2 ;;
        --timeout-s) CELL_TIMEOUT_S="${2:?missing value}"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown argument: $1 (try --help)" ;;
    esac
done

case "$LABEL" in
    idle|contended) ;;
    *) die "--label must be idle or contended (playbook 07 labeling rule)" ;;
esac

# ---- transport resolution -------------------------------------------------

TRANSPORT=""
resolve_transport() {
    local adb_version=""
    case "$BENCH_TRANSPORT" in
        auto)
            if command -v adb >/dev/null 2>&1; then
                adb_version="$(adb version 2>/dev/null || true)"
            fi
            if [ -n "$adb_version" ] && [[ "$adb_version" == *"Android Debug Bridge"* ]]; then
                TRANSPORT=adb
            elif command -v shizuku >/dev/null 2>&1; then
                TRANSPORT=shizuku
            else
                die "no usable transport: need a real adb or the shizuku wrapper"
            fi
            ;;
        adb)
            command -v adb >/dev/null 2>&1 || die "--transport adb but adb is not on PATH"
            TRANSPORT=adb
            ;;
        shizuku)
            command -v shizuku >/dev/null 2>&1 || die "--transport shizuku but shizuku is not on PATH"
            TRANSPORT=shizuku
            ;;
        *) die "--transport must be auto, adb or shizuku" ;;
    esac
}

# Runs one command on the device with the resolved transport.
remote() {
    local -a args=()
    case "$TRANSPORT" in
        adb)
            if [ -n "$ADB_SERIAL" ]; then args+=("-s" "$ADB_SERIAL"); fi
            adb "${args[@]}" "$@"
            ;;
        shizuku)
            shizuku "$@"
            ;;
        *) die "transport not resolved (bug)" ;;
    esac
}

# Retried read: the host-bridge shizuku wrapper can race and drop stdout
# (exit 0, empty output), so reads retry until they yield output.
remote_read() {
    local tries="${1:?missing try count}"
    shift
    local out=""
    for ((i = 0; i < tries; i++)); do
        out="$(remote "$@" 2>&1 | tr -d '
')" || true
        [ -n "$out" ] && break
        sleep 1
    done
    printf '%s' "$out"
}

resolve_transport
echo "transport: ${TRANSPORT}"

if [ -z "$RUN_ID" ]; then RUN_ID="run-$(date -u +%Y%m%d-%H%M%S)"; fi
if [ -z "$OUT_FILE" ]; then OUT_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/results/${RUN_ID}.jsonl"; fi
mkdir -p "$(dirname "$OUT_FILE")"

TOTAL_CELLS=0
for _th in $THREADS; do
    for _c in $CTX_VALUES; do
        for _g in $GPU_LAYERS; do
            for ((_r = 1; _r <= REPEATS; _r++)); do TOTAL_CELLS=$((TOTAL_CELLS + 1)); done
        done
    done
done

if [ "$DRY_RUN" = 1 ]; then
    echo "dry-run: transport=${TRANSPORT} matrix would run ${TOTAL_CELLS} cell(s) -> ${OUT_FILE}"
    for _th in $THREADS; do
        for _c in $CTX_VALUES; do
            for _g in $GPU_LAYERS; do
                echo "  remote am start -W -n ${BENCH_ACTIVITY} --es bench 1 --es threads ${_th} --es ctx ${_c} --es gpu_layers ${_g} --es bench_tokens ${TOKENS} --es bench_categories ${CATEGORIES}"
            done
        done
    done
    exit 0
fi

if [ "$TRANSPORT" = shizuku ] && [ -n "$APK_PATH" ]; then
    die "--apk install needs the adb transport (shizuku cannot push); install the CI debug APK on the device instead"
fi

# ---- device checks --------------------------------------------------------

if [ "$TRANSPORT" = adb ]; then
    mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    if [ -n "$ADB_SERIAL" ]; then
        case " ${DEVICES[*]} " in
            *" $ADB_SERIAL "*) ;;
            *) die "serial $ADB_SERIAL is not an attached adb device" ;;
        esac
    elif [ "${#DEVICES[@]}" -eq 1 ]; then
        ADB_SERIAL="${DEVICES[0]}"
    elif [ "${#DEVICES[@]}" -gt 1 ]; then
        die "multiple adb devices attached; pick one with --adb <serial>: ${DEVICES[*]}"
    else
        die "no adb device attached"
    fi
else
    if [ -n "$ADB_SERIAL" ]; then
        echo "note: --adb serial ignored (shizuku transport is device-local)"
    fi
    if [ -z "$(remote_read 3 getprop ro.serialno)" ]; then
        die "shizuku not reachable (getprop returned nothing; is Shizuku running?)"
    fi
fi

DEVICE_SERIAL="$(remote_read 3 getprop ro.serialno)"
DEVICE_MODEL="$(remote_read 3 getprop ro.product.model)"
ANDROID_RELEASE="$(remote_read 3 getprop ro.build.version.release)"

APK_SHA=""
if [ -n "$APK_PATH" ]; then
    [ -f "$APK_PATH" ] || die "apk not found: $APK_PATH"
    APK_SHA="$(sha256sum "$APK_PATH" | awk '{ print $1 }')"
    echo "installing ${APK_PATH} (${APK_SHA:0:12}) on ${ADB_SERIAL:-device}"
    remote install -r "$APK_PATH"
fi

if [ -z "$(remote_read 3 pm path "$BENCH_PKG")" ]; then
    die "app ${BENCH_PKG} is not installed; install it (--apk with adb transport, or the in-app GGUF downloader)"
fi

# Header row: run metadata so every result file is self-describing.
RUN_ID="$RUN_ID" DEVICE_MODEL="$DEVICE_MODEL" DEVICE_SERIAL="$DEVICE_SERIAL" \
ANDROID_RELEASE="$ANDROID_RELEASE" LABEL="$LABEL" THREADS="$THREADS" \
CTX_VALUES="$CTX_VALUES" GPU_LAYERS="$GPU_LAYERS" TOKENS="$TOKENS" \
CATEGORIES="$CATEGORIES" REPEATS="$REPEATS" APK_SHA="$APK_SHA" \
TRANSPORT="$TRANSPORT" \
python3 - >>"$OUT_FILE" <<'PY'
import datetime
import json
import os

meta = {
    "kind": "meta",
    "run_id": os.environ["RUN_ID"],
    "device": os.environ["DEVICE_MODEL"],
    "serial": os.environ["DEVICE_SERIAL"],
    "android": os.environ["ANDROID_RELEASE"],
    "label": os.environ["LABEL"],
    "threads": os.environ["THREADS"].split(),
    "ctx": os.environ["CTX_VALUES"].split(),
    "gpu_layers": os.environ["GPU_LAYERS"].split(),
    "tokens": int(os.environ["TOKENS"]),
    "categories": os.environ["CATEGORIES"],
    "repeats": int(os.environ["REPEATS"]),
    "transport": os.environ["TRANSPORT"],
    "apk_sha256": os.environ["APK_SHA"],
    "date_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
}
print(json.dumps(meta, sort_keys=True))
PY

append_row() { printf '%s\n' "$1" >>"$OUT_FILE"; }

FAILED_CELLS=0
for th in $THREADS; do
    for ctx_n in $CTX_VALUES; do
        for gl in $GPU_LAYERS; do
            for ((rep = 1; rep <= REPEATS; rep++)); do
                cell="t${th}-ctx${ctx_n}-g${gl}-r${rep}"
                run_id="${RUN_ID}-${cell}"
                echo "== cell ${cell} (${run_id}) =="

                # 1) force-stop, then verify the pid is really gone.
                remote am force-stop "$BENCH_PKG"
                for ((i = 0; i < 10; i++)); do
                    pid="$(remote pidof "$BENCH_PKG" 2>&1 | tr -d '\r' || true)"
                    [ -z "$pid" ] && break
                    sleep 1
                done
                pid="$(remote pidof "$BENCH_PKG" 2>&1 | tr -d '\r' || true)"
                if [ -n "$pid" ]; then
                    echo "  warn: force-stop did not stop ${BENCH_PKG} (pid=${pid}); continuing"
                fi

                # 2) fresh logcat so rows from previous cells never leak in.
                remote logcat -c

                # 3) cold launch with the cell config.
                start_out="$(remote am start -W -n "$BENCH_ACTIVITY" 2>&1 \
                    --es bench 1 --es run_id "$run_id" \
                    --es threads "$th" --es ctx "$ctx_n" --es gpu_layers "$gl" \
                    --es bench_tokens "$TOKENS" --es bench_categories "$CATEGORIES" || true)"
                start_out="$(printf '%s\n' "$start_out" | tr -d '\r')"
                launch_state="$(printf '%s\n' "$start_out" | sed -n 's/^LaunchState: //p' | tail -1)"
                total_time="$(printf '%s\n' "$start_out" | sed -n 's/^TotalTime: //p' | tail -1)"
                [ -n "$total_time" ] || echo "  note: am start -W reported no TotalTime (see playbook 07 pitfall)"

                # 4) best-effort whole-app PSS while the bench is running.
                pss_kb=""
                if pss_out="$(remote dumpsys meminfo "$BENCH_PKG" 2>&1)"; then
                    pss_kb="$(printf '%s\n' "$pss_out" | tr -d '\r' |
                        awk '/^TOTAL PSS:/ { print $3; exit } /^TOTAL[[:space:]]+[0-9]/ { print $2; exit }')"
                fi

                # 5) wait for the app's done marker.
                done_row=""
                polls=$((CELL_TIMEOUT_S / POLL_INTERVAL_S))
                for ((i = 0; i < polls; i++)); do
                    done_row="$(remote logcat -d -s "$BENCH_TAG:I" 2>&1 |
                        grep "\"run_id\":\"${run_id}\"" | grep '"kind":"done"' | tail -1 || true)"
                    [ -n "$done_row" ] && break
                    sleep "$POLL_INTERVAL_S"
                done

                if [ -z "$done_row" ]; then
                    echo "  FAIL: no done marker within ${CELL_TIMEOUT_S}s"
                    append_row "{\"kind\":\"cell\",\"run_id\":\"${run_id}\",\"status\":\"timeout\",\"launch_state\":\"${launch_state:-unknown}\",\"cold_start_ms\":${total_time:-null},\"pss_kb\":${pss_kb:-null}}"
                    FAILED_CELLS=$((FAILED_CELLS + 1))
                    continue
                fi

                # 6) copy this cell's structured rows (bench + cell metadata);
                #    retry the dump because the bridge can race and drop it.
                rows=""
                for ((i = 0; i < 5; i++)); do
                    rows="$(remote logcat -d -s "$BENCH_TAG:I" 2>&1 |
                        grep "\"run_id\":\"${run_id}\"" |
                        sed -E 's/^.*NATIVEAI_BENCH: //' |
                        tr -d '\r' || true)"
                    [ -n "$rows" ] && break
                    sleep 1
                done
                if [ -n "$rows" ]; then
                    printf '%s\n' "$rows" >>"$OUT_FILE"
                else
                    echo "  warn: no bench rows captured for ${run_id}"
                fi
                append_row "{\"kind\":\"cell\",\"run_id\":\"${run_id}\",\"status\":\"done\",\"launch_state\":\"${launch_state:-unknown}\",\"cold_start_ms\":${total_time:-null},\"pss_kb\":${pss_kb:-null}}"
                echo "  ok (launch=${launch_state:-unknown}, cold_start_ms=${total_time:-n/a}, pss_kb=${pss_kb:-n/a})"
            done
        done
    done
done

echo
echo "sweep complete: ${TOTAL_CELLS} cell(s), ${FAILED_CELLS} failed"
echo "results: ${OUT_FILE}"
[ "$FAILED_CELLS" -eq 0 ] || exit 1
