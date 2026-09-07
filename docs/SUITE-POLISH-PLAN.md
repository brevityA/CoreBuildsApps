# CoreBuildsApps polish plan

Download this file from GitHub as the working plan for the next three weeks. The rule is honesty at the edges: keep the current architecture, make every public claim true, and do not move the release targets users already depend on.

## Non-negotiables

- Do **not** merge the six standalone Gradle roots.
- Do **not** split this GitHub repository.
- Do **not** touch or rename the floating Downloader release tags: `iconpack`, `pixel-neon`, `coreline`, `shift`, `motion`, `doctor`.
- Do **not** change package IDs without a dedicated migration/reinstall plan.

## Package-ID freeze

| Product | Gradle root | Package ID | Release tags | Stable asset |
|---|---|---|---|---|
| Icon Pack | `/` + `app/` | `tv.corebuilds.iconpack` | `v*`, `iconpack` | `iconpack-release.apk` + legacy `app-release.apk` |
| Pixel Neon | `pixel-neon/` | `tv.corebuilds.pixelneon` | `pixel-neon-v*`, `pixel-neon` | `pixel-neon-release.apk` |
| Core Line | `ticker/android/` | `dev.corebuilds.line` | `coreline-v*`, `coreline` | `coreline-release.apk` |
| Core Shift | `shift/` | `dev.corebuilds.shift` | `shift-v*`, `shift` | `coreshift-release.apk` |
| Core Motion | `motion-plugin/` | `tv.corebuilds.motion` | `motion-v*`, `motion` | `coremotion-release.apk` |
| Core Doctor | `doctor/` | `dev.corebuilds.doctor` | `doctor-v*`, `doctor` | `coredoctor-release.apk` |

## Target layout, after the truth pass is boringly green

Do not start folder moves in this PR. The target can be introduced only after Week 1 is green for several days:

```text
apps/
  icon-pack/        # current app/ + root Gradle project only after migration plan
  pixel-neon/       # current pixel-neon/
  core-line/        # current ticker/android + ticker web assets after path audit
  core-shift/       # current shift/
  core-motion/      # current motion-plugin/
  core-doctor/      # current doctor/
content/
  motion/           # current Motion/
  wallpapers/       # current Wallpapers/
tooling/
  icon-pack/        # current tools/ once generator paths are migrated
```

Until then, current paths are canonical.

## Week 1 — Truth first

1. **Version-truth CI**
   - `tools/catalog.json`, `app/build.gradle.kts`, `Latestrelease/version.json`, `docs/IconPackList.md`, and `README.md` must agree on Icon Pack `1.8.2` and `924` icons.
   - README must list six products, current versions, stable release tags, and Downloader status.
   - CI must fail if README/agent docs drift back to stale counts or versions.
2. **Kill stale agent docs**
   - `AGENTS.md` must describe the six registered apps, 924 icons, 1098 mapped components, and the current validator receipt.
   - Remove old “40 icons / 78 components / four apps” language from agent-facing docs.
3. **README as suite product page**
   - Six rows: Icon Pack, Pixel Neon, Core Line, Core Shift, Core Motion, Core Doctor.
   - Motion gets its own row and section, not a footnote inside Shift.
   - Downloader codes are explicit; if a code is not assigned, say `[USER TO SUPPLY]` rather than inventing it.

Exit criteria: suite CI green, existing app workflows green, no stale version/count claims in product/agent docs.

## Week 2 — Docs attic + GitHub release drafts

1. Create a docs attic index for historical research that is intentionally stale.
2. Move or label old research docs that still say 921 icons so agents do not treat them as product truth.
3. Add GitHub Release draft templates per product with:
   - current version
   - stable asset name
   - updater metadata file
   - smoke checklist
   - rollback notes
4. Keep floating tags unchanged; drafts target versioned tags only.

Exit criteria: stale historical docs are clearly marked as historical, release drafts exist, and updater metadata contract tests still pass.

## Week 3 — Living-room polish

1. **Core Line first-run**
   - First launch explains what it does: scores/RSS ticker, not a player.
   - One D-pad path to add feeds or use demo slate.
   - No blank screen when network/feed fails.
2. **Core Shift → Core Motion pointer**
   - Shift should plainly route users: Monet/Aerial path in Shift; Projectivy path in Core Motion.
   - Motion install status/help should be visible without guessing.
3. **Doctor suite health**
   - Keep it local-only.
   - Make sibling app/update-feed status readable and redacted.

Exit criteria: TV smoke passes for Line first-run and Shift screensaver enter/exit; Doctor report stays redacted; no new telemetry.

## Done checklist

- [x] README has six app rows with current versions and Downloader status.
- [x] `tools/audit_contract.py` fails on stale Icon Pack catalog/README/agent-doc truth.
- [x] `AGENTS.md` replaces stale agent docs.
- [x] Existing floating Downloader releases are untouched.
- [x] Package IDs are unchanged.
- [ ] Suite CI green.
- [ ] Existing per-app workflows green.
- [ ] No folder moves before Week 1 is boringly green.
