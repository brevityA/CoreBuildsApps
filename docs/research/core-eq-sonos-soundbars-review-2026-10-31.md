# Core EQ review: Sonos soundbars (2026-10-31)

## Scope and evidence

Reviewed Sonos product pages and owner's guides (Arc, Beam Gen 2, Ray) and
listening reviews from What Hi-Fi?, Sound & Vision, and MusicTech, plus the
checked-out Core EQ source. This is an engineering review, not a claim that any
Sonos product has been physically tested by Core EQ. No Core EQ microphone
measurement exists for any of these bars; every preset below is a starting point
from the specification and review characterisations, not a correction.

## The products

### Sonos Arc (5.0.2 Dolby Atmos)

- Eleven drivers: eight elliptical woofers, three silk-dome tweeters, eleven
  Class-D amplifiers; HDMI eARC; Trueplay room tuning (iOS).
- Reviews: balanced midrange, clear dialogue that can read slightly bright,
  powerful rich bass, some lack of very low bass without a sub.

### Sonos Beam (Gen 2) (Dolby Atmos via virtual height)

- Four elliptical mid-woofers, one centre tweeter, three passive radiators;
  HDMI eARC; Trueplay. No physical up-firing drivers — height is psychoacoustic.
- Reviews: focused dialogue clarity from the centre channel and retuned centre;
  modest bass output for a compact bar; Trueplay makes an audible difference.

### Sonos Ray (2.0 stereo, optical only)

- Two woofers, two tweeters, four Class-D amplifiers; optical TV input;
  bass + treble EQ plus Speech Enhancement and Night Sound in the Sonos app.
- Reviews: best-in-class dialogue clarity, tight rather than booming bass, output
  rolling off near 65 Hz, thin movie impact without a sub.

## Presets added

One model each in `tv.corebuilds.eq.dsp.HardwarePresets`, with four presets
per model (Movie, Dialogue, Music, Late night) in the app's ten fixed manual
bands. The names and gains are subjective tone overlays: they sit on top of a
measured correction and never replace it, and no model here has a measured
Core EQ response.

- `sonos-arc`: cuts the rich low bass a touch in Movie, lifts 1–4 kHz for
  Dialogue, trims low bass for Late night.
- `sonos-beam-gen-2`: gentler low-bass cut than the Arc (a compact bar's bass
  is already modest), dialogue lift, small treble lift for Music.
- `sonos-ray`: no sub-bass bands (the Ray cannot make them); Dialogue mirrors
  the Ray's own Speech Enhancement 1–4 kHz lift; Music lifts only where the
  small woofers can still work.

## Detection

Android reports "Sonos Arc", "Sonos Beam (Gen 2)" and "Sonos Ray" over HDMI
ARC/eARC, Bluetooth and wired routes, and `DeviceIdentity` already recognises
the Sonos brand by whole-word name match. The Arc Ultra is explicitly excluded
from the Arc match, and the original Beam (no generation marker) from the Beam
Gen 2 match, since both are different products.

None of this claims Sonos tuning, endorsement, or affiliation.