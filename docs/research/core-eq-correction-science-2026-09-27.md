# Core EQ — the correction science: what to fit, where it pays, and how the industry does it

Research date: 2026-09-27. Third pass, following
`core-eq-measurement-and-capability-2026-09.md` and
`core-eq-precedents-poweramp-and-audio-path-2026-09-27.md`. Those two settled
*whether the idea is precedented* and *whether an app EQ can reach the sound*.
This one settles the question the DSP reference has been answering by
assumption: **once you have a measurement, what are you allowed to correct, at
what frequencies, toward what target, and with what smoothing?**

Four things came out of it, and one of them is a correction to Core EQ's own
reference code:

1. **Correction has a ceiling, and it is computable.** The room stops behaving
   like a set of discrete resonances above its Schroeder frequency; the
   industry's working rule is to invert below roughly 300–500 Hz and leave the
   rest to gentle target shaping.
2. **A null cannot be filled, and trying is actively harmful.** This is
   physics, not preference, and it changes the default from "correct the
   response" to "cut the peaks and leave the cancellations alone".
3. **Core EQ's `harman` target is in the right family but is named and
   parameterised wrongly.** It is a speaker in-room curve, not the Harman
   headphone target — which is fortunate, because the headphone curve's 3 kHz
   ear-gain peak would be wrong to bake into a speaker correction. The numbers
   are also about half the published preference.
4. **The measurement signal should be a swept sine, not pink noise.** The
   reference already ships an inverse-filtered sweep generator and does not use
   it for measurement.

---

## 1. The correction ceiling: Schroeder frequency and Toole's transition

A small room has two acoustic personalities. Below a crossover frequency the
response is a handful of discrete standing waves — axial, tangential and
oblique modes — whose peaks and nulls move dramatically with position. Above
it, modes overlap so densely (roughly three or more per bandwidth) that the
field becomes statistical and the response varies only a few dB across the
room
[1](https://sonavyx.com/en/insights/schroeder-frequency-explained),
[2](https://rftools.io/calculators/audio/room-modes).

The crossover is the **Schroeder frequency**:

```
f_s = 2000 · sqrt(RT60 / V)        V in m³, RT60 in seconds
```

Worked values from the same sources: a 5 × 4 × 2.7 m living room (54 m³) at
RT60 = 0.5 s gives **f_s ≈ 192 Hz**; a 30 m³ bedroom studio at RT60 = 0.8 s
gives **≈ 327 Hz**; a typical small room lands at **200–400 Hz**
[1](https://sonavyx.com/en/insights/schroeder-frequency-explained),
[2](https://rftools.io/calculators/audio/room-modes). Below f_s the response
varies 10–25 dB with position and nulls can be complete; above it, variation
is on the order of ±5 dB
[2](https://rftools.io/calculators/audio/room-modes). The *transition*
frequency practitioners actually use is often taken as about one octave above
f_s
[3](https://www.gearspace.com/board/so-much-gear-so-little-time/1279201-loudspeakers-rooms-scientific-approach.html).

Two consequences, both operational:

- **f_s is computable at runtime from a room description**, or better, from a
  measured decay. Core EQ can ask for room dimensions (three numbers, one
  screen) and set its own correction ceiling instead of guessing.
- **Small rooms are the hard case.** In a 10 m³ room f_s approaches 350 Hz —
  modal behaviour reaches well into the vocal range
  [1](https://sonavyx.com/en/insights/schroeder-frequency-explained). That is
  exactly the living-room-with-a-TV case.

### 1.1 The industry's working rule: stop inverting around 300–500 Hz

This is the most consistently repeated boundary in the whole literature.
Toole's position is that room curves *"do not correlate as well with listener
preferences, except at bass frequencies, below the about 300–400 Hz transition
frequency… especially true if narrow-band equalization is used above the
transition frequency"*, and that it is useful when an EQ can be **disabled
above roughly 400–500 Hz**
[4](https://www.headphonesty.com/2026/09/audiophiles-distrust-dsp-judging-technology/).
His dubbing-stage summary is blunter: *"The LF below approximately 300 Hz
needs to be EQed to smooth the SPL irregularities due to room modes. No other
EQ is necessary"*
[5](https://www.hometheatershack.com/threads/a-response-to-floyd-e-tooles-aes-article-the-measurement-and-calibration-of-sound-reproducing-systems.130122/).

The products agree. Dirac's ART acts only from **20–150 Hz**; Ultrasonic caps
correction around **200 Hz**
[4](https://www.headphonesty.com/2026/09/audiophiles-distrust-dsp-judging-technology/).
`audioxpress` recommends limiting auto correction to **20–400 Hz** and doing
the rest by hand
[6](https://audioxpress.com/article/trouble-with-the-curve). Audyssey users
who tune seriously converge on limiting the filter range to **250–500 Hz**,
with the explicit rationale *"limiting the correction to close to the room
Schroeder frequency, you get the best of both worlds"*
[7](https://forums.audioholics.com/forums/threads/the-audyssey-multeq-editor-app-users-thread-with-facts-and-tips.118005/),
[8](https://www.reddit.com/r/hometheater/comments/xc5sdy/disabling_audyssey_has_made_an_dramatic/).
Even a good-room caveat from DIY practice caps narrow-band correction at about
500 Hz because wavelengths get short enough that moving the mic a little
changes the answer
[9](https://www.reddit.com/r/audiophile/comments/1i9kcn1/room_eq_question_for_2_channel_stereo/).

Why above the line the microphone stops being a reliable guide: it sees the
sum of direct and reflected field at one point, while a listener separates
some of those arrivals perceptually. Forcing a smooth in-room curve above the
transition is often equalising **reflections**, which is not something an
upstream filter can do (§2.3)
[4](https://www.headphonesty.com/2026/09/audiophiles-distrust-dsp-judging-technology/),
[10](https://www.acousticsinsider.com/blog/frequency-response-uneven).

**Consequence for Core EQ:** the current reference corrects uniformly from
`F_MIN = 40` to `F_MAX = 8000` Hz
[11](https://github.com/brevityA/CoreBuildsApps/blob/ce0f17a/tools/core_eq_dsp.py).
That is the wrong default. The corrector should have two regimes: full
inversion below the transition, and only broad target shaping above it.

---

## 2. What EQ can fix and what it categorically cannot

### 2.1 Cut peaks; do not fill nulls

The single most repeated rule in the field, and it is physics rather than
taste. A null is **destructive interference** — direct and reflected arrival
out of phase at the microphone or eardrum — and *"you cannot add energy where
none exists due to phase cancellation"*. Boosting a 20 dB null by 20 dB does
not fill it; it wastes amplifier power, raises distortion, increases driver
excursion, and makes the response worse at other seats
[12](https://demaudio.com/doc/linfir/acoustic-advisory.html),
[13](https://www.soundtechnology.com.au/articles/room-modes-explained-bass-problems-fixes).

The cleanest formulation is the tug-of-war one: the reflection is an
equally-matched opponent, so turning up the speaker raises both sides and the
cancellation remains
[14](https://www.reddit.com/r/mixingmastering/comments/pxjdrk/is_treating_room_modes_with_systemwide_eq_a_good/).
And comb filtering from reflections cannot be corrected at all: EQ changes the
source material, which affects the direct and reflected sound *in equal
amounts*, so their interference at the ear is untouched
[10](https://www.acousticsinsider.com/blog/frequency-response-uneven).

Practical numbers that recur across sources:

| Rule | Value | Source |
|---|---|---|
| Maximum boost | **6–8 dB** | [12](https://demaudio.com/doc/linfir/acoustic-advisory.html), [15](https://www.avsforum.com/threads/boosting-nulls.2319393/) |
| Maximum cut | **10–15 dB** (beyond that, investigate instead) | [16](https://www.audioholics.com/subwoofer-setup/multiple-subwoofer-setup-calibration-1) |
| Dips to leave alone | deeper than **6–8 dB** below the local trend | [12](https://demaudio.com/doc/linfir/acoustic-advisory.html) |
| Asymmetric practice | *"cut up to 15 dB, but only boost 1 dB"* | [17](https://www.reddit.com/r/audiophile/comments/mnuo5u/best_practices_for_creating_adjusting_room/) |
| Limit the **final response**, not individual filters | — | [17](https://www.reddit.com/r/audiophile/comments/mnuo5u/best_practices_for_creating_adjusting_room/) |

`MAX_BOOST_DB = 6` and `MAX_CUT_DB = 12` in the reference are already inside
these limits
[11](https://github.com/brevityA/CoreBuildsApps/blob/ce0f17a/tools/core_eq_dsp.py).
What is missing is *detection*: the fitter will happily park a +6 dB filter in
a dip if the dip is shallower than the clamp.

### 2.2 Cutting a modal peak also fixes its ringing — this is the one thing EQ genuinely does

Toole's finding, and it is the reason bass EQ is worth doing at all: *"Room
resonances at low frequencies behave as 'minimum phase' phenomena, and so, if
the amplitude vs. frequency characteristic is corrected, so also will the
phase vs. frequency characteristic. If both amplitude and phase responses are
fixed, then it must be true that the transient response must be fixed — i.e.
the ringing, or overhang, must be eliminated"*
[18](https://www.acousticfrontiers.com/201163hard-proof-that-equalization-kills-room-modes-html/),
[19](https://www.gearspace.com/board/so-much-gear-so-little-time/1279201-loudspeakers-rooms-scientific-approach.html).
Acoustic Frontiers published measured proof: waterfall and impulse-response
plots show the decay time collapsing when the peak is notched
[18](https://www.acousticfrontiers.com/201163hard-proof-that-equalization-kills-room-modes-html/).

The boundary condition matters. This only holds **where the response is
minimum phase**. REW exposes exactly this test — *excess group delay* — and
the practitioner rule is to correct only where the curve sits near zero
deviation; a region showing tens of milliseconds of excess group delay is
non-minimum-phase and inverting it distorts the waveform without fixing the
sound
[18](https://www.acousticfrontiers.com/201163hard-proof-that-equalization-kills-room-modes-html/),
[20](https://www.gearspace.com/board/bass-traps-acoustic-panels-foam-etc/643333-room-treatment-coupled-digital-room-correction.html).
Dirac's own analysis finds non-minimum-phase zeros just outside the unit
circle that *move with listening position*, and concludes a robust corrector
should not try to alter them
[21](https://www.dirac.com/wp-content/uploads/2021/09/On-equalization-filters.pdf).

Since a swept-sine measurement yields an impulse response (§4), this test is
available to Core EQ for free once the stimulus changes.

### 2.3 And a null *is* the non-minimum-phase case

The same practitioner reports the paradox plainly: after heavy treatment he
could notch the remaining bass peaks, but the worst null was *"the only part
of the plot which shows large deviation from minimum phase. So I can fix the
frequencies that ain't broke, but can't EQ-fix the frequency that IS broke"*
[20](https://www.gearspace.com/board/bass-traps-acoustic-panels-foam-etc/643333-room-treatment-coupled-digital-room-correction.html).
The two rules — leave nulls alone, only correct minimum-phase regions — are
the same rule seen from two sides.

---

## 3. Target curves: the speaker in-room family, and a check on our own

### 3.1 Flat in-room is not the target

Every serious study of loudspeaker-in-room preference converges on the same
finding: listeners prefer a response that **slopes gently downward from bass
to treble**, and a flat in-room target *"sounds too thin and bright"*
[22](https://seanolive.blogspot.com/2009/11/subjective-and-objective-evaluation-of-room-correction-products),
[23](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html).
This has been stable from the B&K work of the 1970s through Toole, Olive, the
cinema X-curve and the Dolby Atmos Music curve — they differ in slope and
breakpoints, not in direction
[23](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html).

The quantitative reference is Olive, Welti & McMullin, AES 2013 (paper 8994):
eleven listeners adjusting bass and treble landed on average on **+6.6 dB of
bass below 105 Hz** and **−2.4 dB of treble above 2.5 kHz** — with trained
listeners preferring a more gradual slope and untrained listeners a steeper
bass shelf
[23](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html).
Toole's own room curve slopes on the order of **2–3 dB per decade** from 100 Hz
to 10 kHz; Anthem's ARC Genesis and Dirac Live both add a further 2–3 dB of
bass below 200 Hz on top of that
[6](https://audioxpress.com/article/trouble-with-the-curve). The preferred home
response falls roughly **9 dB from 50 Hz to 13 kHz**
[5](https://www.hometheatershack.com/threads/a-response-to-floyd-e-tooles-aes-article-the-measurement-and-calibration-of-sound-reproducing-systems.130122/).
Spread between individuals is wide — about 17 dB in bass and 11 dB in treble
across the eleven subjects — so the target is a *starting point with a
preference knob*, not a truth
[23](https://archimago.blogspot.com/2026/07/room-target-curves-then-now-and-options.html).

### 3.2 The headphone curve is a different object, and we must not mix them up

The Harman **headphone** target carries a 3 kHz ear-gain peak and +5–6 dB of
bass relative to diffuse-field
[24](https://acousticstoday.org/wp-content/uploads/2022/03/The-Perception-and-Measurement-of-Headphone-Sound-Quality-What-Do-Listeners-Prefer-Sean-E.-Olive.pdf).
That peak exists to simulate what a *loudspeaker in a room* does at the
eardrum — for an external source, the listener's own head supplies it
[25](https://www.reddit.com/r/headphones/comments/x2j2q0/can_someone_help_me_understand_harman_curves_i/).
Baking it into a speaker correction double-counts pinna gain and makes voices
speak through a telephone.

### 3.3 So: what is `target_curve("harman")` actually doing?

Reading the reference
[11](https://github.com/brevityA/CoreBuildsApps/blob/ce0f17a/tools/core_eq_dsp.py):

```python
bass = 3.5 * 0.5 * (1.0 - np.tanh((f - 105.0) / 55.0))
out  = bass + tilt_db_per_oct * octaves_from_pivot     # default -1.0 dB/oct at 630 Hz
```

Good news first: **it is speaker-family, not headphone-family.** There is no
3 kHz ear-gain peak. The 105 Hz bass-shelf breakpoint is exactly the one used
in the 2013 study, and a −1 dB/octave tilt is in the right region.

But three things are wrong with it:

1. **The name overclaims.** It is not the Harman headphone target, and it is
   not the published Harman in-room curve either. A name that cites a
   standard invites the reader to assume the numbers match the paper. They
   do not.
2. **The bass is about half the published preference** — 3.5 dB against the
   study's +6.6 dB.
3. **There is no treble term.** The study's −2.4 dB above 2.5 kHz is missing;
   the −1 dB/oct tilt supplies roughly −1.5 dB at 8 kHz relative to the pivot,
   which is in the right direction but is doing the job by accident.

Recommended fix: rename to `room`, state the family and the citation in the
docstring, ship the published Olive 2013 shape as an explicit preset, and make
the *default* the gentler curve — because a TV's own drivers are already
rolled off at the top and pushing the target further down is not what a small
speaker needs.

### 3.4 Two more target lessons from the commercial correctors

- **Audyssey's Reference and Flat targets differ only in the high frequencies**,
  and the difference exists because the direct-to-reverberant ratio at home
  differs from the mixing stage
  [26](https://www.audioholics.com/room-acoustics/audyssey-room-eq-interview).
  That is a second, gentler argument for not inverting the treble at home.
- **Audyssey's "midrange compensation" is a ~2 dB dip at ~2 kHz**, originally
  added because many speakers have a natural dip at the tweeter/woofer
  crossover and filling it sounded worse. Users routinely disable it, and at
  least one reports it *"killed the listenability to voices"*
  [8](https://www.reddit.com/r/hometheater/comments/xc5sdy/disabling_audyssey_has_made_an_dramatic/),
  [27](https://www.reddit.com/r/hometheater/comments/18t2ch4/is_this_target_curve_optimal/).
  A default that subtracts 2 kHz is a bad default for a dialogue-first
  product. **Do not copy MRC.**

---

## 4. Smoothing should follow the ear, not a fixed fraction

The reference smooths with a fixed 1/3-octave moving average everywhere
[11](https://github.com/brevityA/CoreBuildsApps/blob/ce0f17a/tools/core_eq_dsp.py).
That is the right resolution in the midrange and the wrong resolution at both
ends:

- In the **bass**, 1/3-octave at 50 Hz is ~23 Hz wide — narrower than a single
  modal peak can be, or wider, depending on the mode's Q, and it certainly
  does not resolve a peak at 42 Hz next to a null at 55 Hz. Correcting modal
  peaks needs detail down there
  [28](https://forum.wiimhome.com/threads/how-to-think-about-room-correction-settings-like-frequency-range-and-gain.6560/page-14).
- In the **treble**, 1/3-octave is finer than perception and invites
  overcorrection of comb structure that changes if you move your head
  30 cm. The measured standard is 1/3 to 1/6 octave in mids and highs
  [29](https://acousticfrontiers.com/blogs/articles/room-acoustic-measurements-101),
  and a careful analysis of multi-angle measurements concludes **1/6 octave is
  about as good as we need** — beyond that, angle-to-angle variation swamps any
  single filter
  [30](https://forums.prosoundweb.com/index.php?topic=173902.0).

REW ships three frequency-dependent schemes, and the descriptions are
directly usable
[30](https://forums.prosoundweb.com/index.php?topic=173902.0):

| Scheme | Bandwidth | Notes |
|---|---|---|
| Variable | detailed in bass, heavily smoothed in treble | *"ideal for room EQ… precise resonance correction… minimising overcorrection"* [28](https://forum.wiimhome.com/threads/how-to-think-about-room-correction-settings-like-frequency-range-and-gain.6560/page-14) |
| Psychoacoustic | 1/3 oct below 100 Hz → 1/6 oct above 1 kHz, cubic mean | peak-weighted, approximates perceived response |
| ERB | `ERB(f) = 107.77·f + 24.67` Hz (f in kHz) | ≈ 1 oct at 50 Hz, 1/3 oct at 200 Hz, 1/6 oct above 1 kHz |

**Consequence:** adopt Variable smoothing as the corrector's input, keep 1/3
octave as the *display* resolution on the TV graph (what a 10-foot UI can show
honestly), and note the two are deliberately different.

---

## 5. The stimulus: pink noise is a choice, not a given

The reference measures with 20 s of pink noise and Welch PSD
[11](https://github.com/brevityA/CoreBuildsApps/blob/ce0f17a/tools/core_eq_dsp.py).
That is an RTA-grade transfer-function estimate: simple, robust, and **time
blind** — it yields no impulse response, no decay, no distortion separation,
and therefore no group-delay gate and no RT60 for the Schroeder formula.

The measurement literature's preferred stimulus for exactly this job is the
**exponential sine sweep** (Farina 2000)
[31](https://www.researchgate.net/publication/2456363_Simultaneous_Measurement_of_Impulse_Response_and_Distortion_With_a_Swept-Sine_Technique):

- Separates the **linear** impulse response from harmonic distortion orders,
  which land at separate positions in the deconvolved result.
- Dynamic range roughly **15–20 dB better than MLS** under comparable
  conditions; measured optimal SNR 80.1 dB for a sweep against 60.5 dB for
  MLS in a direct comparison
  [32](https://people.montefiore.uliege.be/stan/ArticleJAES.pdf).
- A single long sweep beats repeated noise averaging; doubling measurement
  time buys only 3 dB either way
  [33](https://www.researchgate.net/publication/280555212_Impulse_Responses_Measured_with_MLS_or_Swept-Sine_Signals_Applied_to_Architectural_Acoustics_An_In-depth_Analysis_of_the_Two_Methods_and_Some_Case_Studies_of_Measurements_Inside_Theaters).
- Requires **silence after the sweep** to recover the tail — the same
  read-past-stop requirement already noted for `AudioRecord`.

The honest counterpoint, and it matters for a living room: the sweep's
weakness is **impulsive noise**, which is correlated with the excitation and
does not average away, whereas MLS randomises uncorrelated disturbance along
the impulse response so averaging works. The same comparison concludes that
in a non-random noisy environment MLS can win, while the sweep is best in a
quiet room
[32](https://people.montefiore.uliege.be/stan/ArticleJAES.pdf),
[33](https://www.researchgate.net/publication/280555212_Impulse_Responses_Measured_with_MLS_or_Swept-Sine_Signals_Applied_to_Architectural_Acoustics_An_In-depth_Analysis_of_the_Two_Methods_and_Some_Case_Studies_of_Measurements_Inside_Theaters).
Sweeps also concentrate energy one frequency at a time, so they beat noise for
finding resonances and for surviving ambient noise in the first place
[34](https://www.audiocheck.net/testtones_sinesweep20-20k.php).

**Consequence:** make the sweep the primary stimulus (the codebase already
generates an inverse-filtered one for `--stimulus`), take 2–3 sweeps and
reject a sweep whose tail disagrees with the others, and keep pink noise as
the cross-check that a non-technical user can see on a live RTA screen. The
sweep also unlocks the two measurements the noise path cannot produce: RT60
(for f_s) and excess group delay (for the minimum-phase gate).

---

## 6. What the commercial correctors actually do

**Audyssey MultEQ**
[26](https://www.audioholics.com/room-acoustics/audyssey-room-eq-interview),
[35](https://ask.audyssey.com/hc/en-us/articles/212347483-How-does-MultEQ-apply-room-correction):

- Measures **in the time domain**, generating impulse responses — explicitly
  not RTA/frequency-domain, because *"measuring in one microphone location and
  creating a filter that 'corrects' for that tiny spot will lead to poor
  equalization results"*.
- Takes **many positions**, clusters them by similarity (fuzzy logic), builds
  a representative response per cluster, recombines, and inverts that.
- **Limits correction below each speaker's measured roll-off point** — it does
  not try to make a small driver produce what it cannot
  [36](https://forums.audioholics.com/forums/threads/the-audyssey-multeq-editor-app-users-thread-with-facts-and-tips.118005/).
- FIR filters, practical limits of **+9 dB boost / −20 dB cut** in AVRs.
- Ships **Reference and Flat** targets differing only in the treble; optional
  midrange compensation (§3.4); and a separate **Dynamic EQ** that applies
  loudness compensation — boosting bass as volume drops, per Fletcher-Munson.

**Dirac Live** works in the mixed-phase domain but its published analysis
concedes that in a well-behaved listening room plain minimum-phase filters
capture most of the benefit, and its newest product (ART) confines itself to
**20–150 Hz**
[21](https://www.dirac.com/wp-content/uploads/2021/09/On-equalization-filters.pdf),
[4](https://www.headphonesty.com/2026/09/audiophiles-distrust-dsp-judging-technology/).

**The hand-tuned REW workflow** is the closest thing to what Core EQ is
building, and it is worth quoting because it is what our users will compare
against: smooth the trace, design a target that is **not flat** (they use a
bass-boosted tilt approximating loudness compensation), divide target by
measurement to get the correction, **limit it to 20–500 Hz**, cap boost at
about 7 dB, and accept that some nulls will remain
[9](https://www.reddit.com/r/audiophile/comments/1i9kcn1/room_eq_question_for_2_channel_stereo/).
Modal peaks get filters matched to their width — *Q = f₀ / BW*, measured
between the −3 dB points of the peak
[16](https://www.audioholics.com/subwoofer-setup/multiple-subwoofer-setup-calibration-1).

One thing to file for later rather than now: **loudness compensation**.
Audyssey Dynamic EQ exists because hearing sensitivity falls with level, and
the TV use case — quiet night-time dialogue — is precisely the case it
addresses. It is not room correction and should not be conflated with it, but
a "Night" preset is a natural future extension, and it is why the target
should record the level it was tuned at.

---

## 7. Dialogue, with numbers this time

Pass two established that the dominant TV problem is dialogue intelligibility
and proposed a Dialogue/TV target. This pass grounds the shape in the Speech
Intelligibility Index (ANSI S3.5-1997) and its frequency importance function
[37](https://publish.kne-publishing.com/index.php/AVR/article/download/13590/12769/):

| Octave band | Contribution to intelligibility |
|---|---|
| centred **2 kHz** | ~**30 %** |
| centred **4 kHz** | ~**25 %** |
| centred **1 kHz** | ~**20 %** |
| 500 Hz – 1 kHz | ~35 % in one practitioner model |
| 1–8 kHz overall | **5 % of the power, 60 % of the intelligibility** |

Sources: JBL professional technical note
[38](http://proaudioencyclopedia.com/speech-intelligibility-a-jbl-professional-technical-note-technical-notes-volume-1-no-26/),
[39](https://helpdesk.flexradio.com/hc/en-us/articles/203853305-Rules-for-EQing-Voice-for-Optimal-Phone-Operation).
The asymmetry between power and importance is the whole story: vowels carry
the energy at 350 Hz–2 kHz, consonants carry the meaning at 1.5–4 kHz and
have almost no energy of their own
[40](https://www.behindthemixer.com/how-eq-speech-maximum-intelligibility/).
Filtering evidence: cutting everything below 500 Hz costs only ~5 %
intelligibility, while low-passing at 1 kHz drops it below 40 %
[41](https://medium.com/@gurugubellik/what-affects-intelligibility-bceb62c94e11).

Practical TV numbers that recur: boost 1–4 kHz by 2–4 dB, or 2–6 kHz
depending on the source; trim 300–800 Hz when the mix is boxy; reduce below
200 Hz because bass masks the vocal range; and **do not** chase presence above
6 kHz, because sibilance lives at 5–7 kHz and overboosting 4.5–6 kHz trades
clarity for hiss
[40](https://www.behindthemixer.com/how-eq-speech-maximum-intelligibility/),
[42](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/),
[43](https://www.the-home-cinema-guide.com/how-to-improve-tv-sound.html),
[44](https://techyorker.com/fine-tuning-your-movie-experience-unveiling-the-best-equalizer-setting-for-maximum-impact/).

One useful trick from live sound, which translates to TV mixes with loud
scores: **cutting** the music 5–10 dB in 1–4 kHz improves narration
intelligibility more than boosting the voice does
[41](https://medium.com/@gurugubellik/what-affects-intelligibility-bceb62c94e11).
Core EQ cannot separate the stems, but it can prefer *cuts* in the dialogue
band over boosts — which is what the cut-first budget already wants to do.

**Recommended Dialogue shape**, to sit beside `room` rather than replace it:
room correction below the transition, then a wide low-Q shelf of **+1 to +3 dB
at 2–3 kHz**, an optional **−1 to −3 dB trim at 300–800 Hz**, hard stop at
**6 kHz**, and no bass boost.

---

## 8. The boost budget is a physics budget

Every 6 dB of boost doubles cone excursion and quadruples power draw
[45](https://techtalk.parts-express.com/forum/tech-talk-forum/1473475-ot-can-an-eq-help-compensate-for-an-undersized-box).
Small drivers roll off below about 60–80 Hz and boosting under that produces
distortion haze rather than bass
[44](https://techyorker.com/fine-tuning-your-movie-experience-unveiling-the-best-equalizer-setting-for-maximum-impact/);
boosting below a ported box's tuning frequency exceeds excursion limits
easily
[45](https://techtalk.parts-express.com/forum/tech-talk-forum/1473475-ot-can-an-eq-help-compensate-for-an-undersized-box).
Consumer Bluetooth speakers typically have a few dB of clean EQ headroom, and
beyond about +6 dB the amplifier's protection compresses the result flat.

This gives a second, independent argument for `MAX_BOOST_DB = 6` — and a third
for stopping the correction at the speaker's roll-off rather than at a fixed
40 Hz. A TV's thin drivers are the limiting factor, not the room, below ~80 Hz.

---

## 9. What this pass changes

| # | Change | From |
|---|---|---|
| 1 | **Two-regime corrector.** Full inversion only below the transition frequency; above it, broad target shaping only (tilt + optional dialogue shelf). Default ceiling `min(2·f_s, 400 Hz)`, user-overridable | §1.1 |
| 2 | **Compute the Schroeder frequency** from room dimensions + estimated or measured RT60; ask for dimensions in onboarding, fall back to 300 Hz when unknown | §1 |
| 3 | **Null detection is mandatory, not implied.** Dips deeper than 6–8 dB below the local trend are skipped and *reported to the user* as uncorrectable cancellations; `cut_only` becomes the default above the transition | §2.1 |
| 4 | **Minimum-phase gate.** From the sweep's impulse response, compute excess group delay and refuse to correct regions far from minimum phase | §2.2 |
| 5 | **Swept sine becomes the primary stimulus**, with repeat-and-reject; pink noise demoted to cross-check and live RTA. This also yields RT60 and group delay | §5 |
| 6 | **Variable smoothing for the corrector**, 1/3-octave retained only for the on-screen graph. Display resolution and correction resolution are deliberately different | §4 |
| 7 | **Rename `harman` → `room`**, document the speaker-in-room family, ship the published Olive 2013 shape as a named preset (+6.6 dB at 105 Hz, −2.4 dB above 2.5 kHz), keep the gentler curve as default. Do not implement Audyssey's midrange dip | §3.3, §3.4 |
| 8 | **Dialogue target gets numbers**: wide +1–3 dB shelf at 2–3 kHz, optional 300–800 Hz trim, nothing above 6 kHz, cuts preferred over boosts in the band | §7 |
| 9 | **Correction floor follows the speaker's roll-off**, detected from the measurement, instead of the fixed `F_MIN = 40` | §6, §8 |
| 10 | **Three measurements within the seat envelope** (head height, ±25 cm) averaged — Audyssey's spatial clustering reduced to the seat, to average position-specific comb structure without claiming a room average | §6 |
| 11 | **The export carries the ceiling.** The Poweramp preset states its own band limit and the "Bands Overlap = Cascade" instruction, so the corrected range survives the hand-off | pass 2 §3 |

Items 1–4 and 9 change what the DSP reference computes and will need new
tests; items 5–6 change the measurement contract; items 7–8 change target
naming and the profile schema.

---

## 10. Still unverified

- **No listening.** Every curve here is sourced from published work and
  practitioner consensus; none has been heard through a TV speaker. The
  Dialogue shape in particular is a starting point.
- **RT60 is not measured today.** The Schroeder formula needs it. Either the
  sweep supplies it (preferred) or the user is asked; an assumed RT60
  underestimates f_s by 30–50 % in reverberant rooms
  [2](https://rftools.io/calculators/audio/room-modes).
- **Room dimensions as a user input** is untested as a 10-foot flow, and
  non-rectangular rooms make f_s an estimate. The fallback (300 Hz) needs to
  be stated in the UI, not silently applied.
- **Excess group delay on a TV remote mic** has never been computed. It may be
  dominated by the mic's own response and the BT/SCO path rather than the
  room. This is a hardware spike item.
- **Sweep repeatability through a TV's own processing** — dynamic range
  compression, "auto volume", sound modes — is unknown. If the TV compresses
  the stimulus, the measurement is of the compressor.
- The **A/B against a hand-tuned REW filter set** on the same room is the real
  acceptance test for the fitter, and has not been run.
