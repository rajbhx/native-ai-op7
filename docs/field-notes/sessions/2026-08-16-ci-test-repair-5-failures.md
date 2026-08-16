# Session digest — 2026-08-16 — CI unit-test repair (5 failures on the golden wave)

## Problems solved
- **P** 250 JVM tests ran but 5 failed on CI (context manager + memory budget
  `initializationError`, model-manifest sha mismatch, two SITE updater asserts).
  cause: tests appended after a premature class-closing brace sat outside the
  class (JUnit cannot reflect them); `ModelManifest.record` lowercases the
  sha while the test expected uppercase; `siteFilePath` used
  `path.contains('.')` on host+path so `fmhy.net/ai` never gained `.html`
  and `shouldIngest` rejected ext `net/ai`, making the site updater throw
  "no pages indexed".
  solution: re-close both test classes at EOF; assert `abc123`; extension
  detection on the final path segment only
  (`path.substringAfterLast('/', "")`). CI green on main (`4463f16`).
  section: A
  tags: [testing, kotlin, structure, sources, ci]
