# Core EQ v1.1 — reach every player

*Status: in progress — M5–M7 and M8's UI half landed; the rest is named below.*
Builds on `docs/CORE_EQ_PLAN.md` §4 (the ladder), §8 (milestones) and §9.2
(*decided: v1.1*): rung 2 of the capability ladder lands in v1.1 as an
optional, clearly-labelled mode. This plan adds one scope decision made with
the maintainer: **v1.1 also lands the `DynamicsProcessing` engine** plan §4
specified but v1.0 did not ship (v1.0 applies through
`android.media.audiofx.Equalizer` only).

---

## 1. What v1.1 is

Two deliverables, one promise: *the correction reaches the players it can,
and says exactly which ones it reached.*

**A. Ladder rung 2 — DUMP-assisted session discovery.** Players that never
announce an audio session (Netflix, YouTube, most streaming apps) are
invisible to rung 1. With a one-time grant of `android.permission.DUMP`
(a development permission, grantable via `pm grant`), Core EQ can read
`dumpsys media.audio_flinger` / `dumpsys audio` itself, discover the session
ids those players are playing on, and attach the equaliser to them —
Wavelet-style, as the ladder table predicted.

**B. The `DynamicsProcessing` engine.** Plan §4 chose it for API 28+: it has
its own band layout (so the UI draws what the engine really does) and a
limiter that can be switched **on** (so the correction cannot clip where the
platform `Equalizer`, which has neither, would). v1.0 shipped `Equalizer`
only; v1.1 completes the engine choice.

## 2. Scope

**In**
- DUMP grant plumbing: detection of the grant, both grant paths (§3), and
  honest fallback to rungs 1/3/4 when it is absent or lost.
- Session discovery: parsing, uid→package mapping, attach rules (§4).
- `DynamicsProcessing` apply path with limiter on and a band mapping that
  matches the profile maths (§5).
- UI: Capability screen row for the discovery mode with the exact grant
  action for this device; status-line and correction-indicator wording that
  names the rung in use (polish allowed per maintainer).
- Release: version 1.1.0, `coreeq/CHANGELOG.md`, `coreeq-v1.1.0` tag wiring.

**Explicitly out (v1.2 and later, per `CORE_EQ_PLAN.md` §9)**
- USB measurement microphones (UMIK-1) — *decided: after v1.1.*
- Multi-seat averaging and modal classification (research pass 6).
- Shizuku integration — *decided: no.* The grant is ours to obtain.
- Any claim of correcting system sounds, notifications, or the TV's own UI.

## 3. Grant paths (decided: PC ADB + on-device pairing)

One permission: `android.permission.DUMP` (`signature|privileged|development`
— `pm grant` accepts it). It survives reboots and app updates; it is lost only
on uninstall, and the UI says so.

1. **PC ADB (always available).** The Capability screen shows the exact
   command, with this device's serial/IP where it can find it:
   `adb shell pm grant tv.corebuilds.eq android.permission.DUMP`
2. **On-device pairing (where the TV exposes it).** Android 11+ wireless
   debugging, paired from the device itself — the flow Shizuku users know,
   implemented here as our own minimal ADB client (NsdManager discovery of
   `_adb-tls-pairing._tcp`, the ADB TLS pairing handshake, then
   `pm grant` over our own connection). No third-party component.
3. **Neither works?** The mode stays off, the verdict card names what is
   missing, and rungs 1/3/4 behave exactly as v1.0. Nothing is degraded.

Path 2 is the largest technical object in v1.1 (§9, risk 3) and is built
behind a spike milestone; path 1 alone is enough to ship the rung.

## 4. Discovery design

- **Source.** `dumpsys media.audio_flinger` (per-thread client tables carry
  pid/uid, session id, usage) and `dumpsys audio` (player list: piid, uid,
  session, state) — parsed together, each a fallback for the other's gaps.
- **Mapping.** uid → packages via `PackageManager.getPackagesForUid`; a
  session is attachable when its usage is media/game and its package is not
  Core EQ itself (the measurement sweep never corrects itself).
- **Attach rules.** One correction path at a time, as today: never while the
  whole-TV `Equalizer` is active; announced sessions (rung 1) keep priority
  over discovered ones for the same session id; discovered sessions release
  when their player disappears from the dump.
- **Cost.** `dumpsys` is a heavy IPC: re-discovery is debounced (playback
  config changes coalesce over ~2 s) and never runs during measurement.
- **Firmware variance is the parser's problem, not the user's.** The parser is
  pure Kotlin (`apply/DumpsysSessions.kt`) pinned by fixture tests over
  captures from several firmware shapes (Google TV, Fire OS, AOSP). An
  unparseable dump is a *named* status ("This firmware's audio dump is not in
  the discovery table"), never a crash or a guess.

## 5. DynamicsProcessing engine

- API 28+ and supported by the probe: `DynamicsProcessing` with the pre-EQ
  stage in use on the engine's own band layout, `MBC` off, **limiter on**
  (conservative defaults: high threshold, fast release — protection, not
  loudness). Below 28 or on a refusal: the v1.0 `Equalizer` path, unchanged.
- Band mapping is pure and parity-tested: `profile.correctionAt` at the
  engine's reported band centres, headroom-shifted, clamped to the engine's
  range — the same maths `ApplyPathTest` pins for `Equalizer`.
- The band row on Home draws the engine's reported centres, whichever engine
  is live; the profile still stores the curve, not the bands.
- A spike (M6a) pins the exact `Config.Builder` / `Limiter` construction on
  real hardware before the apply path is written around it — the API's
  channel-wise setters are the kind of thing that silently no-ops.

## 6. UI (polish allowed)

- Capability screen gains a **Discovery (DUMP)** row: granted or not, which
  grant path this TV supports, the exact command or the pairing button, and
  the verdict it buys ("Most players, including Netflix and YouTube").
- The correction indicator and status line name the rung: `LIVE · CORRECTING`
  keeps its meaning; the status line below it distinguishes *announced*,
  *discovered (DUMP)* and *whole TV* in its own words.
- First-run honesty: the mode is opt-in wording, never a promise. A device
  without the grant looks exactly like v1.0.

## 7. Milestones

| | Ships | Verifiable without hardware |
|---|---|---|
| **M5** | Grant state + `DumpsysSessions` parser + fixtures + tests | yes |
| **M6** | DP band mapping + limiter config (pure part) + `DpMappingTest` | yes |
| **M6a** | DP spike on one device: config actually engages, limiter clamps, band centres read back | **needs hardware — named** |
| **M7** | `EqService` rung selection: discovery attach rules, single-path invariant, status/indicator wording | logic yes, behaviour on device |
| **M8** | Capability UI + on-device pairing helper (spike-gated) | pairing needs hardware |
| **M9** | Gates, changelog, version 1.1.0, release tag | CI |

M5 and M6 are written test-first, the suite's DSP rule: the parser and the
band maths exist and are pinned before the service is built on them.

## 8. Gates

Everything v1.0 runs (`core_eq_dsp.py --selftest`, `test_core_eq_dsp.py`,
`test_core_eq_parity.py`, `build_core_eq_mockups.py --check`, suite truth,
CI coverage, changelog contract, `:app:testDebugUnitTest`) plus:

- `DumpsysSessionsTest` — fixture table: Google TV / Fire OS / AOSP captures,
  media + game + excluded usages, Core EQ's own playback excluded, garbage
  input named not guessed.
- `DpMappingTest` — band interpolation, headroom, clamp parity with
  `ApplyPathTest`.
- A changelog `### Added` / `### Changed` entry that names the optional mode
  as optional.

## 9. Risk register (product risk, most → least) with mitigations

1. **Wrong-session attach or double correction.** Attaching an equaliser to
   the wrong session corrects the wrong stream; overlapping paths correct
   twice. *Mitigation:* one-path invariant kept and tested (whole TV XOR
   announced XOR discovered); attach only sessions the parser proves are
   media/game and not us; fixture tests pin the mapping; a wrong or
   unreadable parse attaches nothing and says so.
2. **`DynamicsProcessing` misconfiguration.** The wrong config can mean
   silence, distortion, or a limiter that pumps — "the EQ made it worse".
   *Mitigation:* spike M6a before the apply path is written; limiter as
   protection only; identical gain maths to the `Equalizer` path (same
   parity tests); instant fallback to `Equalizer` on any refusal; the
   capability verdict records which engine ran.
3. **On-device pairing protocol.** The ADB TLS pairing handshake is real
   crypto/protocol work; a broken helper is a worse user experience than no
   helper. *Mitigation:* path 2 is spike-gated and never blocks path 1;
   if the spike fails, v1.1 ships PC-ADB only and the verdict card says the
   on-device path is not available on this build — decided, not silent.
4. **Grant loss and dead ends.** Users uninstall, or a firmware update
   clears the grant, and the mode "stops working". *Mitigation:* grant state
   is probed on every Capability open and on service start; loss is a named
   status with the fix (the same command again); nothing else changes
   behaviour when the grant is gone.
5. **DUMP is a powerful permission.** Privacy and trust: parsing system
   dumps must not become a data leak. *Mitigation:* read only session ids,
   uid, package and usage; persist only the package names (the existing
   `noteSessionPackage` list); no dump text is stored or exported; the docs
   say exactly what is read. This is the brand's honesty pillar.
6. **Firmware variance of dump output.** Parsers trained on one build break
   on another. *Mitigation:* two sources (`media.audio_flinger` + `audio`)
   cross-checked; fixture corpus grows from every bug report; unparseable is
   a named state (risk 1's rule).
7. **Foreground-service and boot constraints.** Android 12+/15 already
   refuse some starts; v1.1 adds nothing that changes that, but discovery
   must never resurrect itself from the background. *Mitigation:* discovery
   runs only inside the existing foreground service; no new receivers.
8. **Release wiring.** A mistagged or half-declared 1.1.0 is a broken
   Downloader experience. *Mitigation:* suite gates + `test_changelog_contract`
   + the `coreeq-v*` tag pipeline, unchanged from v1.0's M4.

## 10. Open decisions

1. **Grant paths** — *decided: PC ADB + on-device wireless-debugging
   pairing where the TV exposes it; no Shizuku.*
2. **v1.1 boundary** — *decided: discovery + the DynamicsProcessing engine.
   USB mics, multi-seat and pass-6 measurement work stay v1.2+.*
3. **Pairing-helper cut line** — *decided: spike-gated; PC ADB alone is a
   complete v1.1 rung 2 if the spike fails.*
4. **Throttle for `dumpsys`** — *decided in implementation: debounce ~2 s on
   playback changes; never during measurement.*

## Receipt

```
python tools/core_eq_dsp.py --selftest        → all invariants hold
python tests/test_core_eq_dsp.py              → 68 passed
python tests/test_core_eq_parity.py           → OK
python tools/build_core_eq_mockups.py --check → mockups ok - 4 frames match the sources
suite gates (truth, changelog contract, CI coverage, envelope) → pass
:app:testDebugUnitTest                        → runs in CI (core-eq-apk.yml) — no SDK in the agent sandbox
```

Landed: **M5** (grant check, `DumpsysSessions` + shapes + `DumpsysSessionsTest`),
**M6 pure** (`BandMapping`, `LimiterSettings`, `DpMappingTest`),
**M7** (rung selection: discovery attach/release rules, announced-wins
upgrade, provenance in the status line, one-path invariant kept),
**M8 UI half** (Capability grant row with the exact command; the rail gained
a scroll boundary so the added row cannot clip a 540dp panel).

Named, still open: **M6a** (the `DynamicsProcessing` spike on real hardware —
the platform `Config.Builder`/`Limiter` construction is the part only a
device can prove), **M8 pairing helper** (spike-gated; PC ADB alone is a
complete rung 2 meanwhile), **M9** (version 1.1.0 + `coreeq-v1.1.0` tag).
