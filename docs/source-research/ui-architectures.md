# Source research — UI architectures

## Hermes WebUI / OpenClaw UI concepts
- Streaming agent event feed (task -> plan -> tool -> observation -> verify ->
  final) is the core "visibly agentic" pattern.
- Model/availability metadata shown as live data, never assumed.

## Native Android adaptation (NEVER SETTLE prompt)
- Design tokens: OnePlus red #EB0029, pitch black #121212, charcoal card
  #1E1E1E, slate text #A0A0A0, divider #2A2A2A.
- Screen 1 Model Hub: dynamic model cards (name, provider pill, Free tag,
  context/tools/ratings, availability), segmented mode selector
  [Auto | Free-First | Offline Only].
- Screen 2 Agent Trace: live ReAct feed with Horizon Light edge pulse during
  generation.
- No Compose dependency: plain XML + custom drawables keeps the APK small and
  the UI sub-100 ms responsive (playbook constraint).
