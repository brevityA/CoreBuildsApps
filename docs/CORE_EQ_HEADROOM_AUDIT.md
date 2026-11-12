# Core EQ headroom and limiter audit

**Audit basis:** source on `claude/new-session-9upf9g-coreeq-listening`, cut from
`main` at `19b8834f` (Core EQ `1.13.2`, versionCode `29`), 11 November 2026.
This audit covers code and configured values only. It is not a measurement:
nothing here played audio on a TV, read a true peak, or observed the order in
which a TV runs its audio effects. The numbers below are pinned by
`HeadroomAuditTest` (4 tests), `ListeningLayersTest`, `DpMappingTest`,
`BandMappingBypassTest` and `NightModeTest`, so a change to the code fails a test
before this page can go stale.

## Four kinds of statement on this page

They are easy to run together, and only the first two are established here.

| Kind | What it is about | Established by | Not established |
|---|---|---|---|
| Configured gain | The band gains Core EQ asks the TV for, after the headroom shift | Unit tests on the mapping; read-back after apply | That the TV's implementation produces them |
| Static curve | The limiter's output level for a steady input level, from its threshold and ratio, with a hard knee as a model | `NightModeTest`, computed, not played | The knee the TV really uses; Android has no knee setting |
| Signal peak | The level of the audio itself: material near 0 dBFS, response between band centres, filter overshoot, inter-sample peaks | Nothing here | Any of it; a 0 dB band passes near-full-scale material at near full scale |
| Transient behaviour | What gets through before gain reduction starts: protection attacks in `1 ms`, Night Light and Strong in `5 ms` | Nothing here | How far a fast transient overshoots on any TV |

"No band above 0 dB" is a configured-gain statement. "Night sits at or below
protection" is a static-curve statement. Neither says anything about signal
peaks or transients, which need a measurement on a TV.

## The chain, in the order Core EQ builds it

| Stage | What it does | Configured value | Pinned by |
|---|---|---|---|
| Requested response | Room correction + manual EQ + listening layers, summed per band | room boost ≤ `+6 dB`, cut ≤ `−12 dB`; manual bands ≤ `±6 dB` | `DpMappingTest`, `ManualEqTest` |
| Listening layers (inside headroom) | Clarity (presence box, 2.2 to 4.5 kHz) | `0`, `+1`, `+2`, `+3 dB` | `ListeningLayersTest` |
| | Stereo speakers (speech lift, bass guard) | speech lift scaled by content type; music gets none | `ToneLayersTest` |
| | Low-volume bass (shelf below 100 Hz) | `0` to `+3 dB` in half-dB steps | `ToneLayersTest` |
| | Auto loudness (ISO 226, ten manual bands) | intended `±6 dB` per band at most; exclusive with Low-volume bass. **Known issue: no effect** since 1.10.2 (see below) | `LoudnessCompensationTest`, `AutoLoudnessKnownIssueTest` |
| Headroom shift | Every band lowered by the largest positive band | `max(0, peak)` | `DpMappingTest`, `HeadroomAuditTest` |
| DynamicsProcessing input gain | none | `0 dB`, read back after apply | `DynamicsProcessingEngine` read-back |
| DynamicsProcessing pre-EQ | The only equalising stage | `25` bands: `24` correction bands from 40 Hz to 8 kHz, one guard band to 20 kHz | `DpMappingTest` |
| MBC, post-EQ | none | disabled, read back | `DynamicsProcessingEngine` read-back |
| Limiter (protection) | Linked across channels | `−1 dBFS`, `10:1`, `1 ms` / `50 ms`, post-gain `0`; no knee setting (see below) | `HeadroomAuditTest` |
| Limiter (Night: Light) | Same limiter, retuned | `−6 dBFS`, `3:1`, `5 ms` / `100 ms`, post-gain `0` | `NightModeTest` |
| Limiter (Night: Strong) | Same limiter, retuned; the 1.13.2 switch | `−12 dBFS`, `4:1`, `5 ms` / `50 ms`, post-gain `0` | `NightModeTest` |

**The limiter has no knee setting.** Android's DynamicsProcessing limiter
takes attack, release, ratio, threshold, post-gain and a link group; it has
no knee parameter, and Core EQ's `LimiterSettings.kneeWidthDb` is never sent
to it. How sharply a TV's limiter enters gain reduction is the TV maker's
implementation and is not controllable or known. The static curves below
assume a hard knee as a model, not as a configured value. (Corrected after
review on #474; an earlier revision listed "knee 0" as configured. The
sound is unaffected.)

**Inside Core EQ, no band gain is above 0 dB.** After the shift, the largest
band gain is `0 dB` and every other band is below it. This is a statement
about the configured gains, not the signal level (see the next section). `ListeningLayersTest` drives every
layer to its maximum at once on a profile already at `+6 dB`, on both A/B
sides, and finds no band above 0.

**Known issue: Auto loudness has no effect.** Its lookup of the nearest
manual band compares a band *index* with a frequency in hertz
(`ToneLayers.responseDb`), so every frequency receives the gain of the last
manual band, 8 kHz. The result is one uniform positive gain across all bands,
and the headroom shift removes a uniform positive gain exactly, so the
configured bands are identical with Auto loudness on or off
(`AutoLoudnessKnownIssueTest`). The code has been this way since 1.10.2.
This batch changes no sound: Extra effects and Home now say the switch has no
effect. Fixing it changes the sound for everyone who has the switch on, so
the fix is a separate change (roadmap milestone AL) that asks those users
before it takes effect.

**The A/B bypass side** is flat at the mean of what the correction actually
applies, so it is never positive and plays at the same average level
(`BandMappingBypassTest`, 7 tests).

## What the shift costs

Keeping the largest band gain at `0 dB` means every boost is paid for by
lowering everything else. On a flat room, Clarity `+3 dB` is the largest band,
so the rest of the spectrum plays `3 dB` quieter (`HeadroomAuditTest`). A
viewer hears this as "Clarity made it quieter" and turns the TV up, which is
the intended trade.

That keeps the equaliser from adding level; it is not a guarantee about the
output signal. A band gain of `0 dB` passes material that is already near
full scale (0 dBFS) at near full scale. The response between band centres,
filter overshoot and inter-sample peaks are not modelled here. On
DynamicsProcessing the protection limiter at `−1 dBFS` is what catches peaks;
the Equalizer fallback has no limiter. Without the shift, a band above `0 dB`
could push such material into the limiter more often, or past full scale on
the fallback.

Layers that boost different regions cost the larger of their peaks, not the
sum: Clarity `+3` (presence) and Low-volume bass `+3` (below 100 Hz) together
take `3 dB` of headroom, not `6`. A layer that lands on a band the room
correction already boosts adds to that band's peak.

## Outside the headroom: Android's own effects

Two switches on Extra effects are Android platform effects, not Core EQ
stages. Core EQ creates them on the same audio session as the correction, but
their gain is not part of the requested response, so the headroom shift does
not pay for it.

| Effect | Default | Largest configured value | Notes |
|---|---|---|---|
| BassBoost | off (since `1.2.1`) | strength `600` of `1000` (Movie); `0` for News and Podcast | Android does not define strength in dB; the response is the TV maker's |
| LoudnessEnhancer | off (since `1.2.1`) | `+4 dB` target gain (News, Podcast); `0` for Music | |

Android gives an app no way to order effects on a session or to read the
order back. Whether a TV runs these before or after DynamicsProcessing, and
so whether Core EQ's limiter sees their gain, is **unknown on every device**.
Since this audit, the on-screen notes for both switches say their boost sits
outside Core EQ's headroom.

## Equalizer fallback

Where a TV refuses DynamicsProcessing, Core EQ applies through the platform
Equalizer. The headroom shift still applies, and bands are clamped to the
range the TV reports. There is **no limiter**: Night mode is not active, and
Home and the status line say so ("not active on Equalizer"). The Equalizer's
bands are the TV's own (AOSP's reference Equalizer has five), so the fit is coarser; the fit report
counts range-limited bands.

## Night mode

Night mode retunes the one protection limiter; it never adds a second stage.
Each level's static curve sits at or below protection for every input from
`−60` to `+12 dBFS` in 0.25 dB steps (`NightModeTest`). That is a statement
about the configured threshold and ratio, modelled with a hard knee. With a
`5 ms` attack (protection uses `1 ms`), a fast transient can pass the
threshold before gain reduction catches it. Protection's `1 ms` attack is
faster but not instantaneous, and the limiter has no lookahead, so the same
holds for it to a lesser degree; nothing here measures either.

## Not established by this audit

- Audible output on any TV. The configured values are read back after apply,
  which shows the TV accepted them, not that its implementation honours them.
  `docs/CORE_EQ_M6A_TEST_RUNBOOK.md` is the hardware procedure; its record is
  `docs/CORE_EQ_DEVICE_TEST_RECORD.md`.
- Effect order relative to BassBoost and LoudnessEnhancer.
- True-peak or inter-sample behaviour of any limiter.
- Whether a soundbar or receiver after HDMI ARC applies its own processing.
  Over a bitstream (passthrough) output, no app effect runs at all.

## Findings

| # | Priority | Finding | Status |
|---|---|---|---|
| H1 | P1 | BassBoost and LoudnessEnhancer add gain outside the headroom, and the screen did not say so | Notes updated on this branch; behaviour unchanged |
| H2 | P2 | The level cost of a layer (above) is not shown where the layer is switched on | Open. Manual EQ shows its headroom figure; Extra effects does not |
| H3 | P2 | The Equalizer fallback has no limiter | Accepted: the platform Equalizer offers none. Status already says Night is not active |
| H4 | — | Whether Bass boost should give way to a Warmth control inside the headroom | Open product decision; Bass boost is unchanged |
