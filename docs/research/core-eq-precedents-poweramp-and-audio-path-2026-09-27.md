# Core EQ — precedents, the apply path, and the audio path

Research date: 2026-09-27. Second pass, following
`core-eq-measurement-and-capability-2026-09.md`. It answers the question that
first pass left open — *"does anything like this already work, and what does
that teach us?"* — and it turns up one architectural finding that the first
pass missed entirely.

Three things came out of it:

1. **A mainstream product already does phone-mic room correction**, and its
   reason for refusing Android is the exact objection Core EQ has to answer.
2. **Poweramp Equalizer is the apply target, not a competitor**, and its
   import format is already what Core EQ exports.
3. **The audio output path decides whether an app-level EQ can do anything at
   all** — and that is detectable before anyone measures a room.

---

## 1. Sonos Trueplay: the precedent, and the objection

Trueplay is the closest living relative of this product. The speaker emits
tones and sweeps; a phone microphone records the result; a correction is
applied [1](https://www.whathifi.com/advice/sonos-trueplay-what-it-how-can-you-use-it).
It takes 60–180 s and it is widely considered to work.

### 1.1 The protocol is doing real work

Three details of the procedure are worth stealing outright:

- **The phone is held upside down**, so the hand does not cover the microphone
  [7](https://www.digitaltrends.com/home-theater/sonos-trueplay-first-impressions-review/).
  This is a known measurement artefact, not a ritual.
- **It samples many positions.** First the main listening position, then a walk
  of the room with a slow vertical wave to sample different heights
  [1](https://www.whathifi.com/advice/sonos-trueplay-what-it-how-can-you-use-it),
  [7](https://www.digitaltrends.com/home-theater/sonos-trueplay-first-impressions-review/).
  A single stationary measurement is not what a room average means.
- **It is airtight about background noise**, because a measurement is only as
  good as its noise floor
  [3](https://www.express-installers.co.uk/blog/trueplay-technology/).

The obvious reading is that Core EQ should copy the walk. The less obvious
reading is that **a TV is a single-seat use case**. Averaging over the room is
right for a shared speaker; measuring at the seat you actually watch from is
right for a television. That is a defensible position — but it has to be
*said*, and it means the remote should be held at ear height at the seat, not
waved around.

### 1.2 The objection Sonos raises is the one Core EQ has to answer

Sonos will not ship full Trueplay on Android, and their stated reason is
microphone variation:

> "there is currently too much variation when it comes to the microphones.
> Sonos says that even the same device on a different carrier will deliver
> varied results"
> [4](https://www.pocket-lint.com/what-is-sonos-trueplay-tuning-and-how-does-it-work/)

> "Full Trueplay is not available on Android because it requires calibration
> for specific devices and the Android market is much more fragmented than
> iOS"
> [6](https://www.reddit.com/r/sonos/comments/1jenl7o/trueplay_now_on_android/)

Their answer to that fragmentation is calibration per device model. Their
compromise for Android is "Quick Tune", which uses the *speaker's own*
microphone instead of a phone's
[4](https://www.pocket-lint.com/what-is-sonos-trueplay-tuning-and-how-does-it-work/),
[9](https://www.trunetto.com/troubleshooting/smart-speakers/sonos/sonos-trueplay-not-available).

A remote microphone is cheaper and less characterised than a phone
microphone. The objection lands harder on Core EQ than it does on Sonos.

The audiophile version of the same objection is blunter:

> "It's not a full range mic and it's not omnidirectional so it is impossible
> to use for proper room tuning. Plus it's encased… causes comb filtering."
> [5](https://www.reddit.com/r/sonos/comments/w45yww/trueplay_or_not_trueplay_thats_the_question/)

### 1.3 …and yet it measures out

The same thread records someone checking Trueplay's result with REW and
confirming it tamed a 100 Hz room mode
[5](https://www.reddit.com/r/sonos/comments/w45yww/trueplay_or_not_trueplay_thats_the_question/).
And the specific fear about subwoofer bass — *"how well can the microphone on
your iPhone measure the frequencies coming from a subwoofer?"* — was answered
by testing: Trueplay "continues to be surprisingly effective"
[7](https://www.digitaltrends.com/home-theater/sonos-trueplay-first-impressions-review/).

**Conclusion.** The premise is validated by a shipping mainstream product.
The mic-variance objection is real, and the correct answer is not to ignore it
but to scope around it — which is exactly what the 40 Hz–8 kHz correction
window and the honest-limits policy already do. It also argues for a
**per-remote profile** rather than a single global assumption.

---

## 2. The audio path: where the correction can actually land

This is the finding the first pass missed, and it is architectural.

### 2.1 ARC/eARC hands processing to the far end

On a modern TV, audio usually leaves over HDMI ARC/eARC to a soundbar or
receiver. What happens then is not what most people assume:

> "The audio is digital going to the receiver of the ARC channel. Whatever
> device receives that data is who processes it."
> [3](https://forums.tomsguide.com/threads/who-processes-the-sound-when-smarttv-app-is-connected-to-a-soundbar-via-hdmi-arc.437672/)

> "Usually if the tv and soundbar are two different brands the TV's audio
> processing is shut off and the soundbar processes the signal."
> [3](https://www.reddit.com/r/Soundbars/comments/165043f/do_eq_settings_not_work_through_arc/)

So in a soundbar setup the TV's own sound modes and EQ frequently do nothing
at all. If the output is **bitstream passthrough**, the Android audio effect
chain is bypassed entirely: there is no PCM for an `Equalizer` to sit in front
of. ARC carries compressed 5.1 and stereo PCM only; lossless needs eARC
[5](https://www.xda-developers.com/old-av-receiver-still-outperforms-every-new-soundbar-but-modern-tvs-broke-something/).

### 2.2 And the TV's own sound modes post-process anyway

Even on TV speakers, the pipeline is rarely "app → speaker". "Standard" is
commonly the no-processing mode; "Adaptive", "DTS Virtual:X" and "Dolby Atmos
for TV speakers" all post-process
[1](https://www.reddit.com/r/Soundbars/comments/1puoh1g/surround_sound_vs_dts_virtualx/),
[2](https://www.reddit.com/r/Soundbars/comments/13l1p8b/hi_can_someone_explain_me_this_sound_modes/).
A measurement taken in one mode and a correction applied in another are not
the same system.

### 2.3 What this forces

**Core EQ must determine the output path before it measures anything**, and
say what it finds. The honest states are at least:

| Path | Can an app EQ reach it? | What Core EQ should do |
|---|---|---|
| TV speakers, "Standard"/direct | Yes | Measure and apply in-app |
| TV speakers, sound mode on | Yes, but the mode colour it afterwards | Tell the user which mode the profile was measured in, and pin it |
| Soundbar, **PCM** over ARC | Often yes | Measure and apply, with a note that the bar's own DSP follows |
| Soundbar, **bitstream/passthrough** | **No** | Say so. Offer the export for the bar's own EQ, or suggest PCM |
| External streamer → soundbar directly | Not through the TV at all | Out of scope; the profile is still exportable |

This is a **capability screen line item**, and it belongs next to the session
ladder from the first pass. A product that measures a room and then applies
nothing silently is worse than one that says "this setup is out of reach".

---

## 3. Poweramp Equalizer: the apply target, precisely specified

You already run Poweramp Equalizer. The research says that is the right
engine, and that Core EQ should hand it curves rather than replace it.

### 3.1 It runs on Android TV, and the pain is the UI

Sideloaded PA EQ is reported stable and system-wide on TV boxes and Google TV
dongles for over a year, with ADB grants and notification access
[2](https://www.reddit.com/r/PowerAmp/comments/1cecy9u/poweramp_equalizer_for_android_tv/).
The developer's own position is that a certified TV UI is "no going to happen"
under Play's TV UI rules
[9](https://forum.powerampapp.com/topic/18474-poweramp-for-nvidia-shield-tv/),
and users work around navigation with a pointer app
[2](https://www.reddit.com/r/PowerAmp/comments/1cecy9u/poweramp_equalizer_for_android_tv/).

That is precisely the hole a 10-foot D-pad UI fills — and it is a *companion*
role, not a rival one.

### 3.2 The import format is already what Core EQ exports

PA EQ imports AutoEQ `.txt` files in both graphic and parametric forms, via
long-press → Import on a preset, or by opening the file in a file manager
[1](https://forum.powerampapp.com/topic/21506-poweramp-equalizer-builds-899-908/),
[3](https://forum.powerampapp.com/topic/20397-parametric-equalizer-and-autoeq-integration/).
A real parametric file looks like:

```
Preamp: -7.1 dB
Filter 1: ON PK Fc 21 Hz Gain -12.1 dB Q 0.68
Filter 2: ON PK Fc 64 Hz Gain -2.2 dB Q 1.13
...
```

`tools/core_eq_dsp.py`'s `export_parametric_txt()` emits exactly this. It also
emits the native `.pa-eq-preset` JSON shape, documented from a real export
[9](https://forum.powerampapp.com/topic/26543-trouble-importing-json-generated-by-script/):

```json
[{
  "name": "Living room",
  "preamp": -3.72,
  "parametric": true,
  "bands": [
    {"type": 0, "channels": 0, "frequency": 40, "q": 0.7, "gain": 0, "color": 0},
    {"type": 1, "channels": 0, "frequency": 20000, "q": 0.7, "gain": 0, "color": 0},
    {"type": 2, "channels": 0, "frequency": 72, "q": 2.0, "gain": -2.6, "color": 0}
  ]
}]
```

`type` 0 = high-pass, 1 = low-pass, 2 = peaking band; `channels` 0 = both,
1 = L, 2 = R
[8](https://shapeshifter32.substack.com/p/fine-tuning-wired-headphones-for).
The first two bands in the sample are the passband edges, not corrections.

### 3.3 Three constraints that must be honoured on export

These are not documented specs — they are what users hit:

1. **PA EQ's gain range is ±15 dB**, narrower than REW's ±20. The published
   workaround is to split one filter into two with identical Fc and Q whose
   gains sum to the wanted value
   [7](https://forum.powerampapp.com/topic/23937-eq-parameter-ranges-parametric-equalizer/).
   Core EQ's own +6/−12 dB budget already fits inside ±15, so this should
   never be hit — but the exporter must *clamp and say*, not emit something
   PA will silently refuse.
2. **Q has an upper bound too**, tighter than REW's 0–20
   [7](https://forum.powerampapp.com/topic/23937-eq-parameter-ranges-parametric-equalizer/).
   Core EQ's PEAKING_MAX_Q of 4.0 is conservative against it.
3. **"Bands Overlap" must be set to Cascade** in PA's settings, "otherwise the
   end result might sound different (worse) than intended"
   [2](https://www.reddit.com/r/oratory1990/comments/1btfpu2/poweramp_eq_values/).
   This is a one-line setup instruction and it belongs on the export screen.

Also known: PA EQ adds +6 dB to imported **graphic** gains
[1](https://forum.powerampapp.com/topic/21506-poweramp-equalizer-builds-899-908/),
so the graphic export and the parametric export are not interchangeable.
Parametric is the right default for a measured curve.

---

## 4. The remote microphone: the risk is narrower than first thought

The first pass flagged "the mic stops after a few seconds" as kill-or-cure.
That was too pessimistic, and it is worth correcting.

### 4.1 `AudioRecord` is not `SpeechRecognizer`

The stop-after-silence behaviour belongs to `SpeechRecognizer` and to
call-style capture — *"you can't force built-in speech module to listening all
the time"*
[2](https://stackoverflow.com/questions/71391119/voice-recognition-stops-listening-after-a-few-seconds-i-want-it-alive-until-pres/71391182),
[7](https://stackoverflow.com/questions/79069875/can-i-run-voice-recognition-speaker-output-and-continuous-audio-recording-sim).
`AudioRecord` is a continuous PCM stream with no such timeout. A 20-second
capture is ordinary use of the API.

The remaining risks are *routing* and *buffers*, not timeouts.

### 4.2 The real question is whether the remote's mic appears as an input

Detection is well-defined and is exactly the probe already designed: construct
`AudioRecord`, `startRecording()`, and check
`getRecordingState() == RECORDSTATE_RECORDING`
[2](https://stackoverflow.com/questions/65990310/is-there-a-way-to-detect-whether-my-android-tv-remote-has-a-microphone-or-not).
`AudioManager.getDevices(GET_DEVICES_INPUTS)` then tells you what you are
actually talking to: `TYPE_BLUETOOTH_SCO` for classic HFP
[5](https://github.com/google/oboe/wiki/TechNote_BluetoothAudio), `TYPE_BLE_HEADSET`
for BLE Audio
[1](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-recording).

### 4.3 If the remote presents as HFP/SCO, there is a known handshake

Bluetooth mic capture needs the SCO link brought up *and awaited* before
recording begins:

```
setMode(MODE_IN_COMMUNICATION) → startBluetoothSco()
→ wait for SCO_AUDIO_STATE_CONNECTED
→ setBluetoothScoOn(true) → AudioRecord(VOICE_COMMUNICATION)
```

[4](https://stackoverflow.com/questions/14455797/recording-audio-from-a-bluetooth-audio-device-in-android),
[8](https://stackoverflow.com/questions/24783146/capture-audio-through-bluetooth-headset-paired-with-android-device),
[9](https://stackoverflow.com/questions/54492486/not-able-to-get-audio-from-bluetooth-mic).
Source choice is device-dependent — `MIC`, `DEFAULT`, `VOICE_COMMUNICATION`
and `VOICE_RECOGNITION` all have reports of being the one that works
[4](https://stackoverflow.com/questions/72656924/how-to-capture-tv-remote-microphone-on-android-tv-os),
[7](https://stackoverflow.com/questions/72711811/android-bluetooth-sco-not-picking-headset-microphone-after-pairing) —
so the source must be probed, not hard-coded.

There is also a documented trap where `getRoutedDevice()` reports the headset
while audio actually arrives from the device mic
[7](https://stackoverflow.com/questions/72711811/android-bluetooth-sco-not-picking-headset-microphone-after-pairing),
so a capture sanity check — play a tone, see whether the response moves — is
worth more than trusting the routing API.

### 4.4 Two implementation details that will otherwise waste a day

- **`AudioRecord` silently drops the tail** unless you keep reading for a few
  seconds after the user presses stop
  [9](https://stackoverflow.com/questions/31658736/android-audiorecording-losing-last-few-seconds-of-audio).
  For a 20 s capture that is 15 % of the measurement. Read past the stop.
- **Android 14+ requires a foreground service with
  `FOREGROUND_SERVICE_TYPE_MICROPHONE`**, plus audio-focus and interruption
  handling, to keep a capture alive
  [6](https://dev.to/agnihotripushkar/everything-that-can-interrupt-your-microphone-on-android-and-how-to-handle-it-68b).
  Only one app may hold the mic at a time.

**Revised verdict.** The premise survives. The risk moved from *"can we record
at all"* to *"which input are we on, and is it really the remote"* — which is a
probe-and-state problem, and this suite's whole design language.

---

## 5. A TV is not a pair of headphones: the dialogue target

The fourth finding is a gap in the first pass's design. Every target I wrote
down — flat, B&K, Harman — is a **music** target. The dominant TV complaint is
not tonal balance, it is dialogue intelligibility.

The literature is consistent about where speech lives:

- **1–4 kHz** carries the harmonics that make words distinct; **2–5 kHz** is
  the presence region
  [1](https://www.the-home-cinema-guide.com/how-to-improve-tv-sound.html),
  [4](https://www.makeuseof.com/fix-muffled-tv-dialogue-with-3-simple-audio-tweaks/).
- **120–250 Hz** is where voices go "chesty" or "boxed"
  [2](https://techyorker.com/fine-tuning-your-movie-experience-unveiling-the-best-equalizer-setting-for-maximum-impact/).
- **300–800 Hz** is where muddiness comes from
  [1](https://www.the-home-cinema-guide.com/how-to-improve-tv-sound.html).
- **Below 80–120 Hz** bass masks speech outright
  [3](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/),
  [5](https://ththeater.com/improve-home-theater-dialogue-clarity-without-new-speakers/).

And two cautions that argue for restraint:

> "Dolby Laboratories mixes are often built to reference targets, so less
> equalization is usually the goal, not more 'correction.'"
> [2](https://techyorker.com/fine-tuning-your-movie-experience-unveiling-the-best-equalizer-setting-for-maximum-impact/)

> "once low-end is boosted aggressively, intelligibility drops even if the
> overall volume sounds bigger"
> [2](https://techyorker.com/fine-tuning-your-movie-experience-unveiling-the-best-equalizer-setting-for-maximum-impact/)

Both fit the gain budget already in place: cut first, boost little, and never
chase the room's bass with more bass.

**Design consequence.** Ship a **Dialogue** target as a first-class option,
and consider making it the TV default rather than Harman. Shape it as: the
room correction as measured, plus a modest +1 to +3 dB presence shelf around
2–3 kHz, a trim of 300–800 Hz when the measurement shows the box, and no
brute treble above 6 kHz (sibilance)
[2](https://techyorker.com/fine-tuning-your-movie-experience-unveiling-the-best-equalizer-setting-for-maximum-impact/),
[3](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/).
Call it what it is: **TV**, not "Reference".

---

## 6. What this pass changes

| # | Change | From |
|---|---|---|
| 1 | **Output-path detection is a first-class probe**, before any measurement, with an explicit "an app EQ cannot reach this" state | §2 |
| 2 | **Poweramp EQ export is rung 1 of the ladder**, not a fallback; ship `.pa-eq-preset` JSON and AutoEQ `.txt`, and print the "set Bands Overlap = Cascade" instruction with the export | §3 |
| 3 | **Export must clamp to PA EQ's ±15 dB and Q bounds** and say when it clamps | §3.3 |
| 4 | **The remote-mic risk is downgraded** from kill-or-cure to a probe: `AudioRecord` has no timeout; the work is SCO/HFP handshake, source selection, tail-read, and a played-tone sanity check | §4 |
| 5 | **Add a Dialogue/TV target**, and reconsider it as the default over Harman | §5 |
| 6 | **Single-seat measurement is a stated position**, not an oversight; the UI says "measure at the seat you watch from" and does not claim a room average | §1.1 |
| 7 | **Per-remote microphone profiles**, because Sonos's objection is variation, and variation is answered with per-device characterisation | §1.2 |

## 7. Still unverified

- No TV hardware here. Nothing in §2 or §4 has been run on a device.
- PA EQ's exact numeric gain and Q bounds are user-reported, not published.
  The clamp must be tested against a real import.
- Whether PA EQ's AutoEQ `.txt` import honours the `Preamp:` line is implied
  by the format but not confirmed by a test.
- No Dialogue/TV target has been *listened to* yet. The frequency regions are
  well sourced; the actual curve is a starting point and needs an A/B.
- Whether a specific TV remote exposes its mic as SCO, BLE, or a vendor-private
  path is device-by-device. That is now a probe, not an assumption.
