# Core EQ — generic manual presets, and what a TV's own sound equaliser can be told

Date: 2026-10-28. Follows `core-eq-closed-loop-refinement-2026-10-28.md` (1.8.0) and
`soundbar-device-capabilities-2026-10-20.md` (the product presets). Ships as Core EQ 1.9.0.

## Two questions

1. Core EQ's manual EQ had eleven presets and most of them were about a *detected product*
   (`HardwarePresets`). Somebody with no soundbar, or with a soundbar Core EQ has never seen,
   had a graphic EQ and no starting points. What shapes are worth shipping that make no claim
   about anybody's hardware?
2. Almost every television has a five- or seven-band equaliser in its sound menu, and Core EQ
   cannot reach it. What do those sliders actually do, what is a careful default, and what can
   Core EQ honestly hand over?

## Part 1 — generic presets

A preset here is a **tone**, not a correction. It says "this is a shape"; it does not say "your
room is wrong at 80 Hz", which only a measurement can say. Every preset must therefore be
modest, and every description says what it does *and* what it does not do.

All fourteen live in `ManualEq.BUILT_IN_PRESETS` (ten bands, 40 Hz–8 kHz, ±6 dB, Q 1.0), Flat
first, and each carries a `description` shown on the second line of the picker:

| id | shape (band: dB) | what it is for |
|---|---|---|
| `flat` | — | the reference; every other preset is a departure from it |
| `bass-lift` | 63 +3, 125 +2, 250 +1 | thin speakers, at low level |
| `speech-clarity` | 1k +1, 2k +2, 4k +1.5, 6.3k +0.5 | dialogue over music, without lifting the bass |
| `less-bass` | 40 −3, 63 −2, 125 −1 | boom in a corner, or neighbours |
| `night-quiet` | 40 −3, 63 −3, 125 −2, 250 −1, 1k +0.5, 2k +1 | loud effects quiet, quiet dialogue audible |
| `warm` | 125 +2, 250 +1.5, 500 +0.5, 4k −0.5, 6.3k −1, 8k −1 | bright, hard recordings |
| `bright` | 125 −1, 2k +1, 4k +1.5, 6.3k +1.5, 8k +1 | dull speakers or a soft room |
| `podcast` | 63 −2, 125 −1.5, 1k +1.5, 2k +2, 4k +1 | mono speech, no music bed |
| `sibilance-tame` | 4k −1, 6.3k −2, 8k −2.5 | "s" and "sh" that whistle |
| `small-speaker-body` | 40 −3, 63 −2.5, 125 +2, 250 +1.5, 500 +0.5 | a driver that cannot do 40 Hz: cut what it fakes, lift what it can do |
| `remove-boom` | 125 −3, 250 −2.5, 500 −1, 1k +0.5 | one-note bass around 150 Hz |
| `low-volume-loudness` | 40 +1, 63 +2, 125 +1, 4k +1, 8k +1.5 | the loudness curve, roughly, at low volume |
| `smooth-mids` | 2k −1, 4k −2 | harsh or nasal vocals, without cutting the air at 6–8 kHz |
| `air` | 6.3k +1, 8k +1.5 | breath and sparkle on dull recordings, without touching bass or presence |

Measured with the same RBJ peaking arithmetic the app uses (`Peaking.peakingMagnitudeDb`,
48 kHz, 4001-point log sweep 40 Hz–8 kHz), highest and lowest point of each combined response:

| preset | peak | trough |
|---|---|---|
| `bass-lift` | +3.94 dB @ 75 Hz | 0.00 @ 8 kHz |
| `speech-clarity` | +3.00 dB @ 2.1 kHz | 0.00 @ 40 Hz |
| `less-bass` | — | −4.57 dB @ 50 Hz |
| `night-quiet` | +1.15 dB @ 1.9 kHz | −5.79 dB @ 57 Hz |
| `warm` | +2.65 dB @ 143 Hz | −2.09 dB @ 6.8 kHz |
| `bright` | +3.26 dB @ 5.8 kHz | −0.99 dB @ 125 Hz |
| `podcast` | +2.87 dB @ 1.9 kHz | −2.59 dB @ 71 Hz |
| `sibilance-tame` | — | −4.64 dB @ 7 kHz |
| `small-speaker-body` | +2.07 dB @ 234 Hz | −4.29 dB @ 46 Hz |
| `remove-boom` | +0.06 dB @ 1.4 kHz | −4.11 dB @ 147 Hz |
| `low-volume-loudness` | +2.95 dB @ 61 Hz | +0.11 @ 752 Hz |

`ManualPresetsTest` pins the peaks and troughs above, so a filter edited in `ManualEq` without
re-measuring fails a test rather than quietly changing what the description promises.

Two of them cut harder than the rest (`less-bass`, `sibilance-tame`, and the low end of
`night-quiet` and `small-speaker-body`). That is deliberate: a cut removes energy a small driver
cannot reproduce anyway, so it is the one direction where a preset can be gently aggressive
without adding distortion or eating headroom.

Two bass presets needed a second look. `less-bass` and `remove-boom` are close in name and
overlap in effect, so the test asserts the difference rather than the intent: measured,
`less-bass` is −4.57 dB at 50 Hz, −4.27 at 63 Hz and −0.64 at 250 Hz; `remove-boom` is
−0.80 dB at 50 Hz, −1.31 at 63 Hz, −4.11 at 147 Hz and −3.88 at 250 Hz. One digs in below
80 Hz and has let go by 250 Hz; the other leaves the bottom alone and removes the one-note
boom above it. The first version of that test compared them at the wrong frequencies and
failed for the right reason — the two curves really do cross.

## Part 2 — the TV's own equaliser

### What the hardware is

Flat panels ship two or three small drivers, often rear- or down-firing, in a shallow cabinet.
Measured reviews put useful output at roughly 100–150 Hz at best, with 2–3 dB of headroom
before the amplifier's limiter starts to work. There is no subwoofer, no baffle step correction
worth the name, and no way for firmware to make 40 Hz.

The manufacturers' own advice converges:

- **Movie / Standard picture-sound mode**, not "Vivid" or "Auto" — those apply dynamic
  compression and bass boost that fight the equaliser.
- **Slightly less bass, slightly more mid.** In practice the 100–300 Hz sliders down a step or
  two and 1–3 kHz up a step.
- **Leave dialogue enhancement, virtual surround, "bass boost" and Atmos-on-stereo off.** They
  are dynamic processes (upward compression, all-pass widening) that no static EQ can imitate.
- **Keep changes small — ±3 dB.** A five-band TV equaliser is a broad, low-resolution control;
  ±6 dB moves whole octaves.
- **Exception:** the biggest dialogue problem is the *downmix*, not the EQ. A 5.1 stream folded
  to stereo loses or attenuates the centre channel. Switching the player to stereo output, if it
  has the option, does more than any slider.
- **Turn the TV's sound processing off when a soundbar is attached.** Two processors in series
  is two curves nobody designed together, and the TV's is usually the badly documented one.

### The band layouts

| set | centres | range | source |
|---|---|---|---|
| Samsung / LG (5-band) | 100 / 300 / 1k / 3k / 10k Hz | ±6 dB | Samsung sound-settings support pages; LG webOS help library |
| Sony Google TV (7-band) | 125 / 250 / 500 / 1k / 2k / 4k / 8k Hz | ±12 dB | Sony support article 00302857 |
| soundbars (typical) | 150 Hz–10 kHz, 7 bands | ±6 dB | `soundbar-device-capabilities-2026-10-20.md` |

The Q of these sliders is not published. Assuming anything narrower than an octave would be a
guess printed as a fact, which is why the mapping samples the curve at the slider's own centre
and says so out loud.

### What Core EQ hands over, and what it refuses to

`TvSoundEq` maps any curve — a preset, a saved profile, or a raw set of points — onto those
grids. Three deliberate refusals:

1. **A band outside 40 Hz–8 kHz gets no number.** `DspConstants.F_MIN/F_MAX` is where Core EQ
   trusts itself to correct. A Samsung set's 10 kHz slider is therefore always reported as
   "—" with the reason, never as 0 dB: 0 dB reads as "leave the treble alone", which is a
   recommendation Core EQ has not earned.
2. **The TV's range wins, and the clamp is reported.** A curve wanting +9 dB is written as
   +6 with "(wanted +9.0 dB; this menu stops at +6)". The number on screen is always one the
   menu will accept.
3. **Not an equivalence.** The report's footer says the sliders are wider than Core EQ's
   filters, that their shape is unpublished, and that the values are a starting point to adjust
   by ear.

What it will not do is pretend to fix the room. A TV equaliser is a five-slider tone control;
room correction needs a measurement, filters and the app's own audio path, which is what
`Measure` is for. The export also carries no preamp: a television's equaliser has no headroom
control, so `CapabilityActivity` hands over the **requested** curve
(`BandMapping.requestedGainsDb`) rather than the headroom-shifted one the service applies —
shifting it would slide every band down and leave a shape the menu cannot even express.

### What the screens do now

- **Manual EQ → "Show these as TV settings…"** maps the preset or hand-tuned curve onto both
  grids, shows the values with clamps and refusals, and can export the text file.
- **Manual EQ → preset picker** shows two lines per preset: name, then description.
- **Capability → TV settings export** now writes the `TvSoundEq` report (both grids) instead of
  a five-band file built from a guessed 60/230/910/3600/14000 layout, which was neither this
  TV's layout nor any TV's.

## Not done, and not claimed

- No television was used to check that a mapped slider produces the intended response. The
  grids come from the manufacturers' published band sets; the *shape* of each slider is unknown,
  and the app says so rather than modelling it.
- Nothing here validates the TV's own EQ accuracy, its limiter, or the SPL any of this produces.
- The preset peaks above are computed responses of the filters, not measurements.
