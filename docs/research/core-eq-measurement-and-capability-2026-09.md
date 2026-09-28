# Core EQ — measurement, capability and the honest limits

Research date: 2026-09-27. Companion to `docs/CORE_EQ_PLAN.md`, and the source
of every constant in `tools/core_eq_dsp.py` and `tests/test_core_eq_dsp.py`.

The brief was "an equaliser for Android TV that builds a custom equaliser
setting, using pink noise and your TV remote mic". Two questions decide whether
that is a product or a disappointment, and both have surprisingly specific
answers:

1. **Can a third-party app put an equaliser in front of all the audio on an
   Android TV?**
2. **Can an app actually listen through the remote's microphone?**

Then, if those are survivable: what is the right way to turn a pink-noise
measurement into a correction curve without wrecking someone's TV?

---

## 1. The capability problem: Android has no global equaliser

### 1.1 Session 0 is deprecated and never removed

`android.media.audiofx.Equalizer` attaches to an audio session. The documented
way to affect everything is session 0, the global output mix, and the API
reference says outright that this is deprecated: *"NOTE: attaching an
Equalizer to the global audio output mix by use of session 0 is
deprecated."* [2](https://developer.android.com/reference/android/media/audiofx/Equalizer)

It was never removed. `Equalizer(0, 0)` still works on a meaningful fraction of
devices, and it is the only path that reaches audio from apps that do not
cooperate. It also logs a warning, may require `MODIFY_AUDIO_SETTINGS` for the
output mix [9](https://stuff.mit.edu/afs/sipb/project/android/docs/reference/android/media/audiofx/AudioEffect.html),
can be fought over by other equaliser apps
[2](https://stackoverflow.com/questions/26694519/equalizer-how-to-regain-control-state),
and has no replacement. A Googler's summary of why it was deprecated is blunt:
"too many problems with supporting global effects"
[1](https://www.esper.io/blog/android-equalizer-apps-inconsistent).

**Consequence:** session 0 must be tried, must be tested at runtime, and must
never be promised in the UI before it has been probed.

### 1.2 The session broadcast is the supported path, and it is opt-in

The supported mechanism is that a player announces its audio session:

```
AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
```

A manifest-registered receiver catches these (the platform exempts this action
from implicit-broadcast limits) and attaches the effect to whatever session
just opened
[1](https://stackoverflow.com/questions/9404776/preferred-way-to-attach-audioeffect-to-global-mix),
[5](https://github.com/nepg82/MotoEQ). This is what Wavelet, Poweramp EQ and
MotoEQ all do.

The catch: **players are not required to send the broadcast.** Many do not;
some need per-app configuration. This is the single biggest reason system-wide
equaliser apps "don't work with all media players"
[1](https://www.esper.io/blog/android-equalizer-apps-inconsistent),
[6](https://www.androidpolice.com/stopped-blaming-my-headphones-after-trying-this-android-equalizer-app/).

### 1.3 The escape hatch: enhanced session detection

Wavelet's answer to non-broadcasting players is to read the audio system
service's own dump, using the `DUMP` permission granted by a one-line ADB
command the user runs once:

```
adb shell pm grant com.pittvandewitt.wavelet android.permission.DUMP
```

That is what its "enhanced session detection" mode is
[1](https://www.esper.io/blog/android-equalizer-apps-inconsistent),
[7](https://www.xda-developers.com/android-auto-equalizer-free-app-poweramp-wavelet/).
It reaches most players that do not broadcast. It does not reach YouTube, which
only logs its session ID for debugging.

**Consequence:** ship a session-detection ladder — broadcast → DUMP-assisted →
session 0 → nothing — and show which rung the device is actually on. This suite
already ships sideloaded APKs with Downloader codes rather than through the
Play Store, so an ADB-assisted optional mode is consistent with how the
audience installs these apps.

### 1.4 DynamicsProcessing is the better engine, on API 28+

`android.media.audiofx.DynamicsProcessing` (API 28+) is a full chain:
`inputGain → PreEQ → MBC → PostEQ → Limiter`, with configurable band counts
and **your own** band layout rather than whatever the ROM baked in
[1](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing).
Wavelet builds its equalisation on it
[3](https://grokipedia.com/page/Wavelet_app); it is what a community app
recommends when `Equalizer`'s automatic headroom attenuation gets in the
way
[6](https://github.com/GrakovNe/lissen-android/issues/486).

Two things follow. First, the band layout becomes the app's decision, not the
device's, so a 10- or 31-band UI is possible
[8](https://github.com/erinlkolp/android-eq). Second, **the limiter stage is
not optional**: once the platform `Equalizer` stops doing its own headroom
management, nothing protects against clipping except you.

### 1.5 The platform `Equalizer` is device-dependent in every dimension

- Band count is whatever the ROM reports: 5 is common, 8 and 13 occur, and it
  varies by device
  [1](https://stackoverflow.com/questions/12545571/android-audio-effect-limits-to-5-bands).
- Centre frequencies are device-defined and queried with `getCenterFreq()`,
  which returns **millihertz**, a detail at least one shipped app got wrong
  [6](https://github.com/ErfanBagheri404/Streamify/pull/83).
- The gain range is device-defined too, via `getBandLevelRange()`, in
  millibels
  [2](https://stackoverflow.com/questions/58612374/how-to-change-the-frequency-of-an-equalizer).

**Consequence:** never draw five hard-coded sliders. Query the device, draw
the bands it reports, and clamp to the millibel range it reports. This is
exactly what `collapse_to_bands()` in `tools/core_eq_dsp.py` does and what
`tests/test_core_eq_dsp.py` pins.

### 1.6 Capability must be probed and stated, not assumed

The pattern that earns trust is a capability screen: try each path, record the
verdict, and say it out loud. Every serious app in this space is defined by its
limitations list
[5](https://github.com/nepg82/MotoEQ),
[2](https://xdaforums.com/t/app-9-0-wavelet-headphone-specific-equalization.4097957/):
system sounds may bypass sessions entirely, some apps need configuration,
legacy mode needs a developer-option toggle for Bluetooth offload, and no
non-root path can override hardware DSP.

---

## 2. The microphone problem: the remote mic is supported, but it is not a measurement microphone

### 2.1 Official position: TV hardware has no microphone feature, but the controller mic is supported

Android TV's own hardware guide lists `android.hardware.microphone` among the
features TVs do not support — and then says, explicitly:

> **Note:** Some TV controllers have a microphone, which is not the same as the
> microphone hardware feature described here. **The controller microphone is
> fully supported.**
> [1](https://developer.android.com/training/tv/get-started/hardware)

So the answer to "can we use the TV remote mic" is **yes**, with three
consequences that the docs do not spell out and that practice does:

- `hasSystemFeature(FEATURE_MICROPHONE)` is the wrong probe. It answers for the
  TV, not the remote. Probe by opening `AudioRecord` and seeing whether
  samples arrive.
- Declaring `RECORD_AUDIO` *implies* `android.hardware.microphone`
  [1](https://developer.android.com/training/tv/get-started/hardware).
  For TV, that needs `android:required="false"` or the app is filtered out of
  TV search entirely — while still holding the permission at runtime.
- The documented TV workaround for a missing hardware feature is a runtime
  check plus a graceful path, which is exactly what the capability screen is
  for.

### 2.2 The audio source is `VOICE_RECOGNITION`, not `MIC`

The reproducible field finding: on real Google TV hardware with a remote mic,
`MediaRecorder.AudioSource.MIC` records nothing, and
`MediaRecorder.AudioSource.VOICE_RECOGNITION` works
[1](https://stackoverflow.com/questions/72656924/how-to-capture-tv-remote-microphone-on-android-tv-os).
`VOICE_RECOGNITION` also disables AGC and noise suppression, which is what a
measurement wants anyway.

### 2.3 Other things that will bite

- The mic is on the remote, roughly 3 m from the listening position, and some
  implementations stream only while a button is held. That is a usage
  instruction, not a bug: "point the remote at your seat" is in the UI for
  this reason.
- On some firmware the remote's mic path drops after a few seconds. Google was
  reported to be working on it
  [1](https://www.reddit.com/r/AndroidTV/comments/kz9lsf/google_duo_android_tv_app_microphone_not_working/).
- One app at a time may hold the mic; a foreground assistant can pre-empt it
  [2](https://stackoverflow.com/questions/24170124/unable-to-access-microphone-when-another-app-is-using-it-in-android).
- USB measurement microphones (UMIK-1 and similar) are the upgrade path, and
  they need USB Audio Class support
  [4](https://electronics.alibaba.com/buying-guides/mic-input-on-android-tv-box-what-works-what-doesn%E2%80%99t).
  Support them when present; do not require them.

### 2.4 An uncalibrated remote mic cannot be trusted below 40 Hz or above 8 kHz

This is the finding that most changes the product.

A phone-class electret capsule rolls off hard below about 80 Hz and is
unreliable above about 4 kHz; below 40 Hz its output is essentially noise
[2](http://www.gaussoftware.de/hifi-apps/mic_calibration.en.htm). Independent
A/B tests against a calibrated measurement mic show that correcting from an
uncalibrated mic **boosts the bass by a lot**, because the algorithm reads the
mic's own roll-off as a room deficiency
[1](https://forum.wiimhome.com/threads/earbud-diy-mic-calibration-for-rc-and-test-of-the-new-calibration-file-import-feature.6598/).
Even calibrated measurement mics disagree above 10 kHz
[4](https://www.gearspace.com/forum/studio-building-acoustics/1014060-room-curve-after-room-calibration.html).

A remote's capsule is not better than a phone's.

**Consequence — the single most important number in this design:** Core EQ
corrects **40 Hz – 8 kHz** and nothing outside it. Below and above that, the
measurement is shown for information and the correction is exactly zero. Users
who want a bass lift use the House target, where +6 dB is a deliberate taste
setting rather than a correction the microphone invented.

---

## 3. Stimulus: pink noise, and the sweep it is not

### 3.1 Pink noise is the right default

Pink noise is equal energy per octave (−3 dB/octave), which is the right
weighting for judging tonal balance and for protecting a small TV speaker
during a long capture
[9](https://www.reddit.com/r/audiophile/comments/kptzt3/what-kind-of-sweep-should-i-use-for-taking-room-response-measurements/).
It is also the stimulus the accessible room-tuning tools use
[3](https://www.theabsolutesound.com/articles/magic-beans-room-correction-software/).

The 1/N-octave band reduction in `tools/core_eq_dsp.py` averages **power**, not
decibels. Averaging decibels flattens peaks and is the wrong operation;
`tests/test_core_eq_dsp.py` pins the difference on a synthetic spectrum where
the two answers are 40+ dB apart.

### 3.2 Pink noise cannot give you phase, and the product must say so

> "the downside to pink noise is you can't get an impulse response from it,
> meaning you can't use it to measure audio phase or delay relative to
> frequency."
> [9](https://www.reddit.com/r/audiophile/comments/kptzt3/what-kind-of-sweep-should-i-use-for-taking-room-response-measurements/)

Magnitude-only correction is the honest scope for a 5-band EQ. Phase and
time-domain correction are out of scope, and the UI must not imply otherwise.

### 3.3 If a phase pass is ever wanted, it is an exponential sine sweep

The exponential sine sweep (Farina, 2000) is the standard impulse-response
method. Against MLS it gives better signal-to-noise ratio and — the headline
result — it **separates harmonic distortion from the linear response**, because
distortion products land at negative time in the deconvolved impulse response
where they can be cut away
[1](https://www.researchgate.net/publication/280555212-...),
[5](https://www.conforg.fr/euronoise2015/proceedings/data/articles/000420.pdf).
The sweep's own spectrum is pink (−3 dB/octave) because it dwells longer at low
frequencies, so the inverse filter must carry a compensating +3 dB/octave ramp
[4](https://www.conforg.fr/euronoise2015/proceedings/data/articles/000420.pdf),
[2](https://medium.com/@baranov.mv/how-i-was-listening-to-a-sine-sweep-all-day-long-294c374995f6).

`tools/core_eq_dsp.py` ships `sine_sweep()` returning both the sweep and its
inverse filter, for exactly this reason: the harder half is written and tested
now, and it is the natural v2 feature. It is not in v1 because a TV
loudspeaker's phase at the seat is dominated by reflections no 5-band EQ can
touch.

---

## 4. From measurement to correction: the limits that make it safe

### 4.1 Do not aim for flat

A flat in-room curve is not what listeners prefer and not what measurements
support. The Bruel & Kjaer 1974 curve is flat to roughly 160 Hz and about
−6 dB by 20 kHz
[1](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html).
Harman/Olive preference work points at roughly +3 dB of bass below 200 Hz with
a gentle high-frequency roll-off of about −0.5 dB/octave, and it wins
preference tests across listener populations
[2](https://ombs.io/guides/room-correction-audio/). JBL Synthesis's own room
correction targets about a 1 dB/octave room-gain curve rather than flat
[3](https://techtalk.parts-express.com/forum/tech-talk-forum/1340306-anyone-designing-for-the-harman-target-response-curve).

Core EQ ships four targets — **Flat**, **B&K**, **Harman**, **House** — with
Harman as the default and House carrying user bass-boost and tilt knobs.

### 4.2 Correct more in the modal region, less everywhere else

Below roughly 150–300 Hz (the Schroeder frequency) room modes dominate and
that is where EQ helps most
[1](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html),
[5](https://housecurve.com/docs/tuning/equalization.html). Above it, broad
trends matter and narrow ripple does not: *peaks and dips become much less
audible and should not be adjusted; instead adjust wide regions of the
magnitude response, an octave or more*
[5](https://housecurve.com/docs/tuning/equalization.html).

This is why the correction is smoothed to **1/3 octave** before the decision:
the correction chases tonal balance, not the ripple that a 5-band EQ cannot
represent anyway. Toole's position is that 1/12-octave resolution reveals
"non-minimum-phase ripples that cannot be equalized"
[7](https://www.linkwitzlab.com/Toole-Room%20calibration.pdf).

### 4.3 The four limits, and why each exists

| Limit | Value | Why |
|---|---|---|
| Max boost | **+6 dB** | Room correction should primarily cut; +6 dB is the ceiling the practice settles on [1](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html), [2](https://ombs.io/guides/room-correction-audio/). Boost costs headroom and drives a small TV speaker into distortion. |
| Max cut | **−12 dB** | The asymmetric budget the field uses [2](https://ombs.io/guides/room-correction-audio/). |
| Max slope | **6 dB/octave** | Stops a narrow null becoming a narrow, ringing boost — AutoEq's regularisation [3](https://github.com/jaakkopasanen/AutoEq/wiki/How-Does-AutoEq-Work%3F). |
| Cut-only mode | optional | Sometimes the safest answer is to refuse all boosts and accept lower output [5](https://housecurve.com/docs/tuning/equalization.html). Core EQ exposes it as a toggle and skips re-centring in that mode, because re-centring is precisely what turns cuts into boosts. |

Two more rules come straight from the literature:

- **Never correct a deep null with boost.** Destructive interference does not
  respond to more power at that frequency
  [4](https://forum.wiimhome.com/threads/custom-room-fit-target-curve.8059/).
  The slope limiter plus the +6 dB cap enforce this mechanically.
- **Do not boost below what the speaker can do.** A bookshelf speaker at −6 dB
  at 50 Hz should not be given +10 dB at 30 Hz
  [5](https://housecurve.com/docs/tuning/equalization.html).
  The 40 Hz floor enforces this too.

### 4.4 AutoEq's fitting method is the right one, and it is portable

The parametric export does not need a research project. AutoEq's documented
approach — detect peaks and seed a peaking filter per peak, merge or drop until
the filter count fits, then optimise the sum of filter responses against the
target with a penalty on high-Q high-gain filters — is public and well
described
[3](https://github.com/jaakkopasanen/AutoEq/wiki/How-Does-AutoEq-Work%3F),
[2](https://archive.org/details/github.com-jaakkopasanen-AutoEq_-_2021-11-21_09-13-16).
10 filters is the practical ceiling of improvement; 5 can be good enough;
the last 10 kHz should be treated as an average rather than a precise curve.

`tools/core_eq_dsp.py` implements the same two-phase shape with no scipy
dependency and no random restart, so the same measurement yields the same
filters on every machine — plus a quarter-octave spacing penalty, because
unconstrained descent parks two filters on one frequency and wastes a band.

Every parametric export carries a **Preamp** line, and that preamp rounds
toward more negative, never to nearest: the safety number in a file must not
round short.

---

## 5. The product gap

Room correction on Android today is either hardware-bound (Dirac Live, Audyssey
MultEQ Editor, both tied to a compatible receiver and, for Dirac, a calibrated
external mic
[2](https://www.howtogeek.com/best-apps-for-home-theater-sound-calibration/)),
or a paid PC-centric workflow with a UMIK-1
[3](https://www.theabsolutesound.com/articles/magic-beans-room-correction-software/),
or a phone app with no TV story at all.

The Android equaliser apps are headphone-focused. Wavelet is AutoEq profiles
and headphone switching [4](https://play.google.com/store/apps/details?id=com.pittvandewitt.wavelet),
Poweramp EQ is a player's companion. **Nothing in the mainstream field does
"measure the room you are sitting in, from the device you are watching, with
the remote you already hold".**

That is Core EQ's actual opening, and it is narrow and real: not "a better
equaliser", but *a room measurement you can run with the hardware already in
the room, whose verdicts it states out loud*.

---

## 6. Decisions this research forces

| Decision | Call | Rests on |
|---|---|---|
| Package ID | `tv.corebuilds.eq`, product name **Core EQ** | `AGENTS.md`: new apps use `tv.corebuilds.<name>` |
| Correction band | 40 Hz – 8 kHz, zero outside | §2.4 |
| Default target | Harman (B&K, Flat, House selectable) | §4.1 |
| Gain budget | +6 dB / −12 dB, slope ≤ 6 dB/oct, cut-only toggle | §4.3 |
| Stimulus | 20 s pink noise, deterministic, shipped as an asset | §3.1 |
| Phase | Out of scope in v1; `sine_sweep()` written and tested for v2 | §3.2–3.3 |
| Effect ladder | session broadcast → DUMP-assisted → session 0 → none | §1.2–1.3 |
| Effect engine | `DynamicsProcessing` on API 28+ with its limiter on; `Equalizer` below | §1.4 |
| Band UI | Draw the device's own bands and clamp to its millibel range | §1.5 |
| Mic source | `VOICE_RECOGNITION`, probed at runtime, `FEATURE_MICROPHONE` never used as the gate | §2.1–2.2 |
| Manifest | `RECORD_AUDIO` with `android:required="false"` implied features | §2.1 |
| Capability UI | First-class screen, verdicts recorded per device and per profile | §1.6 |
| Exports | Parametric `.txt` (with preamp), EqualizerAPO `GraphicEQ:`, profile `.json` carrying its own limits | §4.4 |
| Honesty rule | A profile travels with the microphone, band and capability notes it was built under | `docs/BRAND-GUIDE.md`: "Name local, offline, cached, premium-gated, unverified." |

## 7. What is still unverified

Named rather than guessed, per house style:

- No Android TV device is available in this environment. Every API behaviour
  above is cited from documentation and field reports, not reproduced here.
  The runtime probe ladder is the design's answer to that, but it has not been
  run on hardware.
- How often `Equalizer(0, 0)` actually works on current Google TV / Fire OS
  builds is not published. It must be measured per device, which is why the
  capability screen reports it rather than the README claiming it.
- Remote-mic frequency response is not characterised for any specific remote.
  The 40 Hz–8 kHz window is derived from phone-class and earbud-class capsule
  behaviour and is deliberately conservative.
- Whether `DynamicsProcessing` is present and correct on the Android TV SoCs
  in the field is vendor-dependent
  [6](https://github.com/GrakovNe/lissen-android/issues/486).
