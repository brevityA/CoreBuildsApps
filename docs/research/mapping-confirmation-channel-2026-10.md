# Crowd-sourced mapping confirmation — design note (2026-10, for 2.5.0)

**Status:** proposal, not built. Written while shipping 2.4.0 so the next
release can start from a decision rather than a blank page.

## The problem

Of the pack's component mappings, 28 came from issue reports and 402 from the
Projectivy cross-check ([COVERAGE.md](../COVERAGE.md)). A reported mapping is
confirmed on exactly one TV: the reporter's. CHANGELOG entries keep saying
*"the component is not yet confirmed on a second TV"* (Binged, Buzz TV 6 in
2.4.0) because there is no cheap way for a second user to say *"that one
works here too"* — or, more usefully, *"that app is installed here and the
icon does not apply"*.

The pack already knows, on-device, the two facts that matter:

1. **Which mapped apps are installed** (`AppAudit.scan` walks the launcher
   intent list against the bundled appfilter).
2. **Whether the mapping resolves** — Projectivy picks the icon by component
   name, so if the installed activity differs from the appfilter's
   `ComponentInfo{…}` the icon silently falls back to palette.

Today only fact 2's *negative* reaches us, and only when a user bothers to
file *Icon not applying*. Positive confirmations never do.

## Constraints that shape the design

- **No backend.** The suite has no server and no telemetry; the update check
  is a GitHub Releases fetch. Any channel must terminate in GitHub.
- **No silent upload.** The pack's privacy posture is "nothing leaves the TV
  unless you press a button that says so" (the auditor's QR flow). Keep it.
- **D-pad UX.** No typing on a TV. One press, or a QR scan on a phone.
- **Signal must be deduplicable.** 200 identical "works for me" issues is
  worse than none.

## Options considered

| | Channel | Pros | Cons |
|---|---|---|---|
| A | **Issue per confirmation** via the existing prefill URL | zero new infra | floods the tracker; duplicates; maintainer cost scales with users |
| B | **Reactions on a pinned per-release "confirm" issue** | GitHub-native dedup (one 👍 per account); counts visible to all | needs GitHub login on the phone; one issue per release to keep tidy; cannot say *which* mapping was confirmed without a comment |
| C | **One issue per unconfirmed mapping, auto-opened by CI at release**, confirmations = 👍 on *that* issue, failures = a comment prefilled from the TV | per-mapping signal, deduped per account, closes itself when 👍 ≥ N; the TV only ever opens a URL the user chose | a few dozen open issues per release at worst; still needs a phone with GitHub |
| D | **Discussions poll** | lighter than issues | API for polls is read-only; cannot prefill; no per-mapping granularity |

**Pick C.** It reuses everything that already exists (issue prefills, QR flow,
`gh api` in CI) and gives a per-mapping count the catalogue can read back.

## How C works end to end

### Release time (CI, `tools/open_confirmation_issues.py` — new)

For each mapping whose source is `issue` and whose `confirmed_on` count is
below 2 (new field in the catalogue CSV, default 1 for issue-sourced,
`∞`/skip for Projectivy-sourced), open — or find — an issue titled
`Confirm: <App name> (<package>)` with label `needs-confirmation`, body:

> This icon was mapped from one TV's report. If **<App>** shows the Core
> Builds icon on your TV, add 👍. If it is installed and still shows the
> palette fallback, press *Report* in the pack's Missing icons screen — the
> comment it prefills carries the component your TV actually runs.

Idempotent: keyed on the package name in the title.

### On the TV (2.5.0 auditor change)

`AuditorActivity` gains a third list section, **Confirm**, between *Missing*
and the footer: the installed apps whose mapping is in the
`needs-confirmation` set (shipped in a tiny JSON asset at build time, like
`manifest/wallpapers.json`). Each row:

- Title: app name · sub: *"Mapped from one report — does the icon show here?"*
- Press → the existing report-or-QR path with the issue URL
  `…/issues/<n>#issuecomment-new` plus a prefilled comment body:
  `Confirmed on <device model> · <launcher> · pack <version>` **or**, when
  `AppAudit` sees the installed component differs from the appfilter's,
  `Not applying · installed component: <component>` (this is the valuable
  negative — it hands the fix to the maintainer in one line).

Nothing is sent without the press; the phone's GitHub session does the post.

### Back in the repo (CI, nightly or on `issues` webhook)

`tools/harvest_confirmations.py` (new) reads 👍 counts and comments on
`needs-confirmation` issues:

- 👍 ≥ 2 **or** 1 comment matching `^Confirmed on` from a different author →
  bump `confirmed_on`, drop the label, close with *"Confirmed on N TVs"*,
  and strip the "not yet confirmed" caveat from the next CHANGELOG via the
  existing release notes builder.
- Any `Not applying · installed component:` comment → relabel
  `mapping-mismatch`, add the reported component to the issue body as a
  candidate `<item>` line, and surface it in `docs/COVERAGE.md`'s first-fix
  table. A human still merges the mapping; the script never edits
  appfilter.

## What is explicitly out of scope

- Automatic appfilter edits from crowd input (poisoning risk, and one wrong
  component breaks an icon for everyone).
- Any background network call from the TV.
- Counting *installs* — nothing records who has the app; only who pressed.

## Cost estimate

- `open_confirmation_issues.py` + `harvest_confirmations.py`: ~250 lines,
  both `gh api`-only, both dry-runnable, both with tests against recorded
  JSON like the existing `tests/test_update_checker.py` fixtures.
- Catalogue: one new CSV column (`confirmed_on`), defaulted by a one-off
  migration; `tools/check_catalog.py` learns to validate it.
- App: one list section and one prefill format in `AuditorActivity`, plus a
  ~2 KB asset. No new permission, no new screen, no new string beyond four.
- Issue template: a hidden `confirmation` form so the prefilled comment URL
  has somewhere stable to land if GitHub changes the comment anchor.

## Decision needed before 2.5.0 starts

1. Threshold: 2 confirmations (proposed) or 3?
2. Should Projectivy-sourced mappings (402) ever enter the set? Proposed:
   no — they are confirmed by Projectivy's own user base.
3. Whether a confirmed mapping ever re-enters the set (app update changes
   its launcher activity). Proposed: yes, when an *Icon not applying* issue
   arrives for it, the harvester reopens the confirm issue.
