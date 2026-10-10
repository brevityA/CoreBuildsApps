# Device research for model-level presets (2026-10-10)

Purpose: decide what the model-level presets can honestly be based on, and
which models are worth a device record first. This is research only. No
model-level presets were added, because no source here publishes a measured
frequency response for any of these models.

## 1. TV manufacturer strings (for `Build.MANUFACTURER`)

Brand detection depends on the manufacturer string, and the exact strings were
not confirmed by any source found here.

| Brand | What sources confirm | Status in the app |
| --- | --- | --- |
| Philips | Philips Android TVs are made by TP Vision (TPV), which is a separate company from Philips. ([TV Brands](https://www.tvbrands.org/what-company-makes-tvs/), [Broadband TV News](https://www.broadbandtvnews.com/2017/11/24/angelo-lamme-overview-of-android-tv-tv-manufacturers/)) | Aliases `tp vision` and `tpv` added to the Philips brand this round. Unverified on a device. |
| LG | LG TVs run webOS, not Android (same repo research note). The build string `LGE` is from memory; not confirmed by a source here. | Already mapped (`lg`, `lge`, `lg electronics`). Verify on device. |
| TCL | User-agent strings show `TCL TV` in the Android build token, which is the model field, not the manufacturer. ([DeviceAtlas](https://deviceatlas.com/blog/list-smart-tv-user-agent-strings)) | Mapped as `tcl`. Verify on device. |
| Hisense | Hisense's VIDAA TVs are a non-Android platform, with their own user-agent format. ([DeviceAtlas](https://deviceatlas.com/blog/list-smart-tv-user-agent-strings)) Hisense Google TV models are Android. | Mapped as `hisense`. A VIDAA TV will not report Android build fields, so it will be detected as unknown. This is expected. |
| Sony | Sony BRAVIA TVs run Android TV, with the BRAVIA name in the build token. ([DeviceAtlas](https://deviceatlas.com/blog/list-smart-tv-user-agent-strings)) | Mapped as `sony`. Verify on device. |
| Samsung | Samsung TVs use Tizen, not Android (`core-eq-android-tv-market-and-competitors-2026-09-28.md`, section on Samsung Tizen). Core EQ cannot run on them. | No TV manufacturer string. Samsung stays as a soundbar name only. |

Decision (maintainer, 2026-10-10): Core EQ is Android TV only. Samsung and LG
are removed from the TV-brand list (no manufacturer string maps to them). They
remain soundbar brands, detected from the output name over HDMI ARC or eARC.
The TV brands are Sony, TCL, Hisense and Philips.

## 2. Soundbar and speaker candidates

The candidates come from 2025–2026 reviews. Specs are not measured
responses, and the sources do not publish frequency response for these models.
Each candidate needs a device record before any preset is based on it.

| Brand and model | What reviewers say (relevant to tone and processing) | Source |
| --- | --- | --- |
| Samsung HW-Q990F | 11.1.4-channel Atmos with a wireless sub and satellites; a "flagship". | [Business Insider](https://www.businessinsider.com/guides/tech/best-soundbars), [PCMag](https://www.pcmag.com/picks/the-best-soundbars) |
| Sonos Arc Ultra | One-piece Atmos with Bluetooth; a new Advanced Speech Enhancement mode that changes dialogue. Dialogue-processing modes are a reason to avoid stacking a second correction. | [Business Insider](https://www.businessinsider.com/guides/tech/best-soundbars) |
| Bose Smart Soundbar 600 | Compact; "bass is limited", and its tone has a "slight synthetic" edge. | [Stuff](https://www.stuff.tv/features/best-soundbar/) |
| Yamaha YAS-209 | Good quality for the price, with a wireless sub. | [Business Insider](https://www.businessinsider.com/guides/tech/best-soundbars) |
| Samsung HW-Q990C | Earlier generation of the Q990 line; 22 speakers. | [Reviewed](https://www.reviewed.com/televisions/best-right-now/the-best-soundbars) |
| Sony HT-S100F | Budget 2.0 bar with a built-in tweeter. | [Reviewed](https://www.reviewed.com/televisions/best-right-now/the-best-soundbars) |
| JBL (Bar 800, Bar 2.0) | Already covered by the existing hardware presets. | `HardwarePresets.kt`, CHANGELOG |

LG and Sony soundbars were not covered by these sources. Add them only with a
source.

## 3. What is missing for model-level presets

- **No measured frequency response** for any candidate. Reviewer descriptions
  are not curves, so they can only support a labelled starting point, the same
  as the Bar 800 and Bar 2.0 presets.
- **Speech and bass processing built into the soundbar.** Some models process
  dialogue or bass themselves (Sonos Advanced Speech Enhancement). A preset
  stacked on that processing has nothing reliable to correct against. The
  measure screen already says not to stack corrections; a model preset must
  say the same.
- **The device list from the user** is still missing. Without it, research can
  only cover the most-sold models, not the user's own hardware.

## 4. Recommendation

1. Ask the user for the device list (TV brand and model, output model), then
   record each device with the device test record template.
2. Add a model preset only when a device record exists for that model, and
   label it "reviewer-informed, not measured" until a measurement is attached.
3. Do not claim LG, Samsung or Sony TV identity from Android build fields
   until a device record confirms it.
