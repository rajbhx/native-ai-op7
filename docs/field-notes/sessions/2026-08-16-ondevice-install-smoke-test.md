# Session digest — 2026-08-16 — On-device install + golden-wave smoke test (GM1901)

## Problems solved
- **P** Installing the CI APK onto the OP7 from this environment is not
  straightforward: proot's `/sdcard` and `/storage/emulated/0` are isolated
  empty dirs, the `shizuku` wrapper forwards no stdin and shares no paths,
  and base64-via-argv hits the OS argument limit.
  cause: proot rootfs is isolated from the real Android filesystem except
  the shared network namespace; `host-bridge` shizuku only accepts a JSON
  command string.
  solution: serve the APK with `python3 -m http.server` bound to 0.0.0.0 in
  proot, then `shizuku sh -c 'curl -s -o /sdcard/... <http://LAN-IP:8765/...>'`
  (device and proot share wlan0 192.168.1.3). Verified SHA-256 first.
  section: A
  tags: [install, shizuku, proot, apk, transfer]
- **P** `pm install -r` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE:
  signatures do not match`.
  cause: the new CI keystore rotation changed the signing cert (installed
  signature `6085c8a0` -> new `415940f0`).
  solution: uninstall + reinstall is the correct signature-rotation path;
  the 491 MB GGUF models live in `/sdcard/Download` (scoped storage), so
  they survive the uninstall (`nativeai-model-backup.gguf`,
  `chatgpt-5-q8_0.gguf`). Stage APK in `/data/local/tmp` before pm install.
  section: A
  tags: [install, signature, keystore, scoped-storage]
- **P** Golden-standard hardware check: the app header hardcoded
  `SD855 \u00b7 8 GB`.
  cause: `EngineScreen.kt:505` literal; GOLD-STANDARD-SPEC says RAM is
  `6-8 GB device variants`.
  solution: change the chip to `SD855 \u00b7 6-8 GB` (README + spec already
  correct).
  section: A
  tags: [hardware, golden-standard, ui, spec]

## On-device smoke test (2026-08-16, GM1901 / Android 10 / SDK 29)
- Install: fresh install OK (signature rotation), app launches, no FATAL.
- Main screen: NEVER SETTLE header + READY state; model card with honest
  metadata (`FREE \u00b7 2048 ctx \u00b7 gguf`); FALLBACK transparency
  showed a real rate-limit reason for remote opencode-zen; empty trace state.
- Engine settings: TOOL INVENTORY with permission tiers; terminal honestly
  DISABLED/NOT INSTALLED; SKILLS panel present.
- SOURCES: FMHY + Playbook both INDEXED; FMHY refresh works (`updated just
  now` + `Source updated`); ADD dialog shows all 6 source types (FlowRow
  fix verified); search box present. FMHY repo genuinely has 2 files
  (README.md + fmhy.md) so `2 files` is correct.
- MEMORY: budget UI shows measured weights (468 MB) + WITHIN CAP 632/1536.
- ERRORS tab: honest `0 errors` empty state with CLEAR/REFRESH/COPY.
- Earlier build (pre-install) showed `ERRORS \u00b7 6` from prior session
  errors; fresh install resets.
