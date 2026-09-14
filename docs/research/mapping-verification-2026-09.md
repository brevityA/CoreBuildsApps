# Mapping verification against Projectivy 1.1.9 — 2026-09

The catalog carries one honesty flag per component mapping: an entry without
evidence stays in `unverified` (design-upgrade §6, guardrail 4 — "do not
inflate counts with invented activities or merged lookalikes"). At the start
of this tranche the pack carried **533** unverified components across 445
icons. Most of them are not guesses at all — they are the reference-pack
inheritances carried over when the mapping was seeded from Projectivy, and
they have hard evidence that was never recorded per entry.

## The evidence source

`tools/reference/projectivy-1.1.9-appfilter.xml` is the appfilter decoded
from the published Projectivy Icon Pack 1.1.9
(https://github.com/SicMundus86/ProjectivyIconPack/releases/tag/1.1.9). The
pack already treats it as the factual mapping baseline: `validate.py`
asserts reference coverage (955 canonical components) against the shipped
appfilter. This tranche reads it the other way — from reference to catalog.

## The protocol

A catalog component is **corroborated** when the reference appfilter contains
that component's identity, parsed from the `ComponentInfo{package/activity}`
form. The pack emits both spellings of every component — leading-dot
shorthand and fully-qualified twin, which `validate.py` §5a2 enforces as a
pair — so the reference corroborates a component when it contains **either**
spelling of the identity. Matching is verbatim: no package fuzzy-matching,
no activity-pattern inference, no "same brand" shortcuts. If the reference
maps the component to a drawable it does not ship, that does not matter —
the evidence is that the component exists and is a real launcher entry, not
which icon Projectivy chose for it.

The one entry corroborated only by its twin spelling (Flix Vision's
`flix.com.vision/.activities.SplashScreenActivity`, which the reference stores
fully-qualified) is included: the twin is literally in our shipped appfilter,
so the reference string is verbatim against what the pack emits.

## The result

| Measure | Before | After |
|---|---:|---:|
| Unverified components | 533 | **69** |
| Corroborated (cleared) | — | 464 |
| Icons carrying the flag | 445 | 30 |

The clearing is mechanical and repeatable: `tools/verify_mappings.py --clear`
drops every corroborated entry from `unverified` (the component itself stays
mapped and emitted — only the honesty flag goes). `--report` lists what would
change without touching the catalog.

## What the 69 survivors are

They fail the verbatim test for a reason, and the reason is the next tranche's
worklist:

- **The suite itself** (3): Core Doctor, Core Line, Core Shift — our own
  stubs, no third party can corroborate them; they clear when each app is
  installed and observed.
- **Companion components, device-build dependent** (4): Nintendo Switch,
  PlayStation (remote play + PS App), Xbox Game Pass — documented in
  icon-craft §3 as hardware-variant activities.
- **Platform and namespace inferences** (the rest): the Foxtel family
  (Binge, Kayo, Streamotion builds on the shared `foxsports.martian.tv`
  main activity), the Synology DS family and Drive, Launcher Manager's
  `com.wolf.lms` build, Stremize's mobile/TV split, HDMI Source's
  guided-actions activities, and a handful of activity guesses recorded
  against Play-listing evidence (Hi Browser, Screen Recording App, BitTV).
  Each has a `color_source`/mapping note saying why it is plausible; none
  has a verbatim reference entry.

## The ratchet

`tests/test_mapping_hygiene.py` pins the outcome three ways:

1. every `unverified` entry parses as `package/activity`;
2. **no** `unverified` entry is corroborated by the reference — a future
   catalog edit that re-adds a corroborated component as unverified fails
   the suite;
3. the total count sits under a ceiling (**69**) that may only move down.

The next tranche (hardware verification on the surviving 69, or a newer
Projectivy reference when one ships) lowers the ceiling further. It cannot
raise it.
