# Icon Logo Fidelity Research — Handover to Claude

## What this is
A deep-research audit of how each of the **921 mapped apps** in the Core Builds
Icon Pack looks versus what each **official logo actually is** (i.e. how the
pack glyphs are *not* what the logos are meant to look like). It's the
reference for any further fidelity pass.

## Files
- **`ICON_LOGO_RESEARCH.md`** — the full deliverable:
  1. Deep-dive section (~93 recognisable brands) with researched official mark vs pack divergence.
  2. Full 921-row per-icon audit table.
  3. Coverage metrics + priority ranking for the next pass.
  4. Handover / working notes.
- **`generate_research.py`** — reproducible generator (reads `catalog.json`,
  writes the MD). Edit the `FACTS` dict to add/adjust brand research and re-run.
- **`catalog-snapshot.json`** — point-in-time snapshot of `tools/catalog.json`
  (2026-09-03). The live source of truth is `tools/catalog.json` at the repo root.

## Regenerating
```
python3 generate_research.py     # writes ICON_LOGO_RESEARCH.md
```

## Key numbers
- 921 icons audited.
- ~300 icons on a custom bespoke pack glyph.
- 621 icons still on a consistent contained-letter tile (abbreviation, not a mark).
- 93 brands with researched official-logo facts.

## Resolution rule
Recognise a brand only where it has a real public emblem; otherwise keep the
consistent tile — never invent a vendor logo. Keep marks evocative, not literal
traces (historical pack guide avoided tracing vendor logos; recognisability was
user-approved for the highest-profile set).

## Regenerate the pack (if glyphs change)
```
cd tools && python3 build_icons.py && python3 build_banners.py
python3 validate.py
```
