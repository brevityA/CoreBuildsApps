# Core EQ — what Android's 5.1-to-stereo fold actually does, and what the lift answers

Date: 2026-10-30. Ships as Core EQ 1.10.0 (Stereo speakers in Extra effects). Follows
`core-eq-manual-presets-and-tv-sound-eq-2026-10-28.md`, which established what a small television
driver can and cannot do.

## Why this exists

The 1.10.0 screen ships a +3 dB lift across 2.2–4.5 kHz for 5.1 content on two speakers, and its
first draft justified that number as "the fold buries the centre by 3 dB, so put the 3 dB back".
The number is defensible. That sentence is not, and it was about to be repeated in the changelog,
the switch's note and the KDoc. This note records what the source actually says, and the
arithmetic that corrects the story.

## The matrix, from AOSP

`audio_utils/include/audio_utils/ChannelMix.h` in `platform/system/media` builds the stereo
downmix matrix for `AUDIO_CHANNEL_OUT_STEREO`. For the 5.1 and 7.1 inputs that matter here:

| input channel | into left | into right |
|---|---|---|
| front left / right | 1.0 (own side) | — |
| **centre** | **0.707** | **0.707** |
| surround left / right | 0.707 (own side) | — |
| **LFE** | **0.5** | **0.5** |

Two comments in that file matter as much as the numbers:

- On the LFE weight: "We add contributions from the LFE into the L and R channels at a weight of
  0.5 (rather than the power preserving 0.707) which is to ensure that headphones can still
  experience LFE with lesser risk of speaker overload." The fold is explicitly an
  overload-avoidance choice, not a fidelity one — which is precisely the headroom argument the
  bass guard uses.
- "Prior to API 32, use of downmix resulted in channels being scaled in half amplitude." The
  absolute level a fold produces is version-dependent; the *relative* offset between a centre and
  a front is not.

## The correction

The centre arrives in both channels at 0.707, which is 3.01 dB below a front at 1.0 — and that is
the only statement for which "3 dB down" is true. What a listener hears is not a channel:

| quantity | value |
|---|---|
| centre, per channel, versus a front at unity | −3.01 dB |
| centre summed coherently across L+R, versus a front at unity | **+3.01 dB** |
| centre summed incoherently (power) | 0.00 dB |

Two speakers, one signal, one listener between them: the fold leaves dialogue at the same level as
a front-panned effect, or marginally above it. It does not leave it 3 dB under.

What the fold costs is the **image** and the **headroom**. A phantom centre built from two spaced
sources is comb-filtered and narrow, and it collapses towards the nearer speaker as soon as the
listener leaves the axis; a real centre channel does not. And the small drivers of a 2.0 bar or a
bookshelf pair are weakest exactly where speech intelligibility lives, 1–4 kHz — a point already
established for television speakers. Add the folded LFE spending excursion on material the driver
cannot reproduce, and the protection limiter pulling speech down with every explosion, and there is
a real problem to answer.

## What the switch does, and why 3 dB

- **Speech lift, +3 dB across 2.2–4.5 kHz** on Dialogue boost's curve: a presence lift for a
  phantom centre, for image and driver reasons rather than a measured level deficit. Slightly more
  than Dialogue boost's +2 dB because the fold costs image as well as headroom; it never stacks
  with it, and a Dialogue or House target's own +2 dB is counted first, so those get +1 dB.
  The constant is named `STEREO_PRESENCE_DB`, not `CENTRE_RESTORE_DB`: it restores nothing.
- **Bass guard, −6 dB below half an octave under the speaker's measured roll-off**, nothing above
  it. The AOSP comment above is the justification for treating the folded LFE as headroom spent
  rather than content delivered. A manual profile has no measured roll-off, so 80 Hz is assumed.
- **Surround sound set to Never** on bitstream-capable outputs (Android 12+, with WRITE_SETTINGS),
  because otherwise the soundbar does the fold and none of the above reaches the audio at all.

## Limits, stated plainly

- No 5.1 film was played through any of this on any hardware.
- The +3 dB is a judgement about image and driver headroom, not a measurement of a room, a
  speaker or a listener position. Nothing here was verified with a microphone.
- The coherent-sum argument assumes a listener roughly equidistant from two speakers. Off-axis
  listening is the case the lift is *for*, and its magnitude there is not modelled.
- Whether a given television's "Surround sound" menu maps onto `encoded_surround_output` is a
  platform assumption taken from the Android API, not checked on a set.
