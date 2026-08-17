
## Golden Rules (from field sessions)

- **NEVER build locally** — CI only
- **NEVER push builds without permission** — `workflow_dispatch` only
- **Don't trigger CI builds** — only code + push. User handles builds.
- **Push is separate from build** — user sometimes wants code only pushed, not built
- **Code only first, then build on request** — don't auto-build
- **Hardware: RAM 6–8 GB variants** (not "8 GB")
- **Playbook**: `rajbhx/op7-special-build-playbook` — field-notes sync after every push
- **No local SDK** — the container has JDK 17 + Gradle 8.9 cached but NO Android SDK
- **Use Shizuku for device interaction** — adb broken
- **Use golden standard for UI/UX** — bottom nav, always visible actions, proper empty states
- **When stuck, give problem list** — don't stop working
