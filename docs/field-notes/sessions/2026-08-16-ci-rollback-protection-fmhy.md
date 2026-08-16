# Session digest — 2026-08-16 — CI rollback, branch protection, FMHY-only sources

## Problems solved
- **P** `ci/sources-fmhy-site` was a wrong push: it carried a 10-commit
  "golden wave" (EngineViewModel single-authority refactor, tool approvals,
  skills UI, integrity, dataset export, resume) that had nothing to do with
  FMHY.
  cause: a previous run conflated a large UX/engine refactor with the small
  "FMHY as a knowledge source" change; the branch name implied sources scope
  but ~90% of the diff was unrelated.
  solution: delete the branch locally and remotely (merge-base == main HEAD,
  so nothing was lost — main never merged it). main stays the source of
  truth. Rule: one branch = one scoped feature; never mix engine refactors
  into a sources/CI branch.
  section: A
  tags: [git, branch-hygiene, scope, workflow]
- **P** GitHub showed "Your main branch isn't protected" on the Branches
  page; force-push/delete and unchecked merges were possible.
  cause: no branch-protection rule existed on the default branch
  (API: `404 Branch not protected`).
  solution: classic protection on main — required status check `build`,
  `allow_force_pushes=false`, `allow_deletions=false`,
  `enforce_admins=false` (admin emergency override stays possible). Direct
  pushes remain allowed (phone-first workflow); PRs not required.
  section: A
  tags: [github, branch-protection, ci, safety]
- **P** main's seed catalog still shipped LiteRT/Termux/llama.cpp/uBlock/
  MemPalace and no FMHY, contradicting "sources = external knowledge only".
  cause: the SITE-type FMHY implementation only existed on the deleted
  branch; main has no `SourceType.SITE`.
  solution: `assets/sources.json` → FMHY (`GITHUB_REPO fmhy/FMHY`, the
  site's content repo) + playbook only; ROADMAP S4 line updated; CI green
  on main (`fd3e899`, run 31930055139).
  section: A
  tags: [sources, catalog, fmhy, assets]

## Architecture notes
- Branch protection + green-CI-on-main is the merge gate now: any future
  feature branch must pass `build` before landing on main.
- FMHY as a GITHUB_REPO (not SITE) keeps the change minimal and works with
  main's existing GitHub ingestion path.
