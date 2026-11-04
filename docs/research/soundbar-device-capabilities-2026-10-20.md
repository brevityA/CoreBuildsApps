# Soundbar capabilities for Core EQ (research, 2026-10-20)

Scope: the six soundbars Core EQ already has tone presets for. Read-only research. Nothing
in the app changed. Sources are manufacturer spec sheets, retailer listings, and reviews.
Where a claim comes only from a retailer listing or a review, it is marked. Where sources
disagree, both values are given.

Why this matters for Core EQ: the app measures the room through the phone's microphone
and applies a correction on the TV's audio output. Three soundbar capabilities can change
that result: built-in room calibration (which may stack with Core EQ's correction),
bitstream passthrough (which bypasses Android effects), and wireless or proprietary
audio routes (which may not pass through the TV's output at all). Those are called out
per model below.

## 1. Summary

| Model | Channels | Power (spec) | Atmos / DTS:X | Input to bar | Built-in calibration | Bluetooth codecs (spec) |
|---|---|---|---|---|---|---|
| JBL Bar 800 | 5.1.2 | 720 W total | Atmos, Dolby Digital, PCM | HDMI eARC, HDMI in, optical | "Easy sound calibration" (spec sheet); mechanism not stated | A2DP 1.2 (codec not stated) |
| JBL Bar 2.0 All-in-One | 2.0 | 80 W total | No (Dolby Digital only) | HDMI ARC, optical | None listed | A2DP 1.3, BT 4.2 |
| Samsung HW-Q800H | 5.1.2 | 400 W (listing) | Atmos; Dolby TrueHD / DD+ | HDMI eARC, HDMI in, optical | SpaceFit Sound Pro (TV-dependent); automatic room correction (review) | Not stated in sources |
| Sony Bravia Theatre Bar 6 | 3.1.2 | 350 W (system) | Atmos; DTS:X | HDMI eARC/ARC, optical | None (manual distances in app) | SBC, AAC (Sony spec); LDAC claimed by some listings, see conflicts |
| LG S80TR | 5.1.3 | 580 W | Atmos; DTS:X | HDMI eARC, HDMI in (4K/120 with VRR/ALLM per LG) | AI Room Calibration (LG) | Bluetooth 5.1, AAC (review) |
| JBL Bar 1000 MK2 | 7.1.4 | 960 W total | Atmos; DTS:X | HDMI eARC out, 3 HDMI in, optical | "Easy sound calibration" with a microphone (owner manual) | A2DP 1.3, BT 5.3 (codec not stated) |

## 2. Per model

### JBL Bar 800 (5.1.2)

- Power: 720 W total; 340 W bar, 2 x 40 W surrounds, 300 W sub. [JBL spec](https://global.jbl.com/soundbars/BAR-800-.html)
- Connections: HDMI eARC, HDMI in with 4K Dolby Vision pass-through, optical, Bluetooth 5.0 (A2DP 1.2), Wi-Fi. [JBL PDF](https://pl.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw8c856787/pdfs/JBL%20BAR_800_Specsheet_EN_102722.pdf)
- Audio formats: Dolby Atmos, Dolby Digital, PCM. [ProAudioVideo](https://proaudiovideo.in/jbl-bar-800-soundbar-review-2026/)
- Frequency response 35 Hz to 20 kHz (-6 dB). [JBL PDF](https://pl.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw8c856787/pdfs/JBL%20BAR_800_Specsheet_EN_102722.pdf)
- Calibration: the spec sheet lists "Easy sound calibration" with no detail on how it works. [JBL PDF](https://pl.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw8c856787/pdfs/JBL%20BAR_800_Specsheet_EN_102722.pdf) Not verified whether it uses a microphone.
- Note: the MK2 (780 W, 7.1, DTS Virtual:X, PureVoice 2.0) is a different product. The Bar 800 presets must not match the MK2 name. [JBL MK2 spec](https://mm.jbl.com/soundbars/BAR-800MK2.html)

### JBL Bar 2.0 All-in-One (2.0)

- Power: 80 W total; 2 x 40 W; two racetrack drivers; 70 Hz to 20 kHz. [JBL spec](https://global.jbl.com/soundbars/BAR+2.0+ALL-IN-ONE.html)
- Connections: HDMI ARC (audio return) and optical. Bluetooth 4.2 (A2DP 1.3). [JBL spec](https://global.jbl.com/soundbars/BAR+2.0+ALL-IN-ONE.html)
- Audio: Dolby Digital only; no Atmos or DTS:X. [HomeCinemaChoice](https://www.homecinemachoice.com/content/jbl-bar-20-all-one-soundbar-review) (older review; consistent with the B&H listing: Dolby Digital only). [B&H](https://www.bhphotovideo.com/c/product/1509313-REG/jbl_jblbar20aioblkam_bar_2_0_all_in_one_2_0.html)
- Note: a 2.0 bar with 70 Hz low end cannot reproduce the bass that the bass-related presets assume.
- An MK2 exists with the same main spec. [JBL MK2 PDF](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwfc6b6831/pdfs/JBL%20Bar%202.0_%20All%20in%20one_MK2_%20Specsheet_EN.pdf)

### Samsung HW-Q800H (5.1.2)

- Channels: 5.1.2 from 11 speakers; 6.5-inch wireless sub with 8-inch passive radiator. [Samsung AE](https://www.samsung.com/ae/audio-devices/soundbar/q800h-black-hw-q800h-zn/), [Crutchfield](https://www.crutchfield.com/S-RWKCloOpPFo/learn/samsung-hwq800h-sound-bar-review.html)
- Total power 400 W. **Retailer listing, not Samsung's spec sheet.** [Amazon listing](https://www.amazon.com/Samsung-Soundbar-HW-Q800H-Subwoofer-Connectivity/dp/B0HKVSY4MZ)
- Connections: HDMI in (1), HDMI out (eARC), optical, Bluetooth 5.3, Wi-Fi, CEC. [Samsung AU spec](https://www.samsung.com/au/audio-devices/soundbar/q800h-black-hw-q800h-xy/)
- HDMI limit: the HDMI input carries 4K at 60 Hz only, with no 120 Hz, VRR or ALLM. [Les Numériques](https://www.lesnumeriques.com/barre-de-son/samsung-hw-q800h-p78098/test.html)
- Audio: Dolby Atmos, Dolby Digital Plus, Dolby TrueHD, 5.1; LPCM multichannel. [Samsung AE](https://www.samsung.com/ae/audio-devices/soundbar/q800h-black-hw-q800h-zn/)
- Sound modes: Surround Sound Expansion, Game Pro, Adaptive Sound, Standard. [Samsung AU spec](https://images.samsung.com/is/content/samsung/assets/au/audio-devices/product-specs/HW-Q800H_2026_RetailerSpecSheet_V2-2.pdf)
- Calibration: SpaceFit Sound Pro is listed as available. Its use is limited to certain Samsung TVs. Check the TV list before relying on it. Automatic room correction is described by Lifehacker. [Lifehacker](https://lifehacker.com/tech/samsung-2026-hw-q800h-dolby-atmos-soundbar-sale-october-2026)
- **Wireless Dolby Atmos** runs over Wi-Fi from a compatible Samsung TV. The audio then does not travel as an ordinary HDMI stream. [Samsung LATAM](https://www.samsung.com/latin_en/audio-devices/soundbar/q800h-black-hw-q800h-zp/) Core EQ's effect on that path is unverified.
- Q-Symphony compatible; wireless rear speakers optional (SWA-9500S, not included). [Samsung AE](https://www.samsung.com/ae/audio-devices/soundbar/q800h-black-hw-q800h-zn/)

### Sony Bravia Theatre Bar 6 (3.1.2)

- Channels: 3.1.2 from 6 speaker units, per Sony's spec. [Sony spec](https://www.sony.com/electronics/support/sound-bars-home-theater-systems-sound-bars-surround-speakers/ht-b600/specifications), [What Hi-Fi](https://www.whathifi.com/tv-home-cinema/soundbars/sony-bravia-theatre-bar-6)
- Power: 350 W system (listing and review figure). Measured draw: bar 35 W, sub 20 W. [PCMag](https://me.pcmag.com/en/speakers/30454/sony-bravia-theater-bar-6)
- Connections: one HDMI eARC/ARC, one optical, Bluetooth 5.3. No Wi-Fi streaming or Chromecast. [TrustedReviews](https://www.trustedreviews.com/reviews/sony-bravia-theatre-bar-6)
- Audio: Dolby Atmos and DTS:X over eARC. Over ARC without eARC, Atmos and DTS:X need Sound Field on, otherwise the bar treats them as 7.1 Dolby Audio. [Mashable](https://mashable.com/article/sony-bravia-theater-bar-6-review)
- Sound fields: Vertical Surround Engine, S-Force PRO, DSEE for music enhancement; night and voice modes. [Sony spec](https://www.sony.com/electronics/support/sound-bars-home-theater-systems-sound-bars-surround-speakers/ht-b600/specifications)
- Calibration: none automatic. The user enters distances in the Bravia Connect app.

### LG S80TR (5.1.3)

- Channels: 5.1.3 from 11 speaker units; up-firing centre; wireless rear surrounds included. [Abt](https://www.abt.com/LG-Home-Theater-Soundbar-5.1.3-Channel-with-Rear-Speakers-and-WOW-Orchestra-S80TR/p/207279.html), [PC Richard spec](https://static.pcrichard.com/docs/S80TR_Specifications-Sheet_01.pdf)
- Power: 580 W. [LG AU](https://www.lg.com/au/speakers/soundbars/s80tr/)
- Subwoofer: 8-inch wireless. [HomeTechnologyReview](https://hometechnologyreview.com/product/lg-s80tr-5-1-3-channel-soundbar-with-dolby-atmos-and-rear-speakers-soundbar/)
- Connections: HDMI eARC out; HDMI in with 4K pass-through, 120 Hz, VRR and ALLM (LG's own spec page). [LG SA](https://www.lg.com/sa_en/tv-soundbars/soundbars/s80tr/)
- Audio: Dolby Atmos, DTS:X, Dolby Vision and HDR10 pass-through. [LG AU](https://www.lg.com/au/speakers/soundbars/s80tr/)
- Wireless: Bluetooth 5.1 (AAC codec), AirPlay 2, Chromecast, Spotify Connect; WOWCAST built-in for wireless Atmos from LG TVs. [HomeTheaterReview](https://hometheaterreview.com/vs/lg-s80tr-5-1-3-channel-soundbar-with-dolby-atmos-and-rear-speakers-soundbar-vs-yamaha-sr-c30a-soundbar-with-subwoofer-comparison/), [Abt](https://www.abt.com/LG-Home-Theater-Soundbar-5.1.3-Channel-with-Rear-Speakers-and-WOW-Orchestra-S80TR/p/207279.html)
- Calibration: LG lists AI Room Calibration. [PC Richard spec](https://static.pcrichard.com/docs/S80TR_Specifications-Sheet_01.pdf) Whether it uses a microphone on the bar or on the phone is not confirmed for the S80TR. The S95TR is described with a built-in microphone. [OFZen](https://www.ofzenandcomputing.com/best-lg-soundbars/)
- Low end, conflicting: HomeTheaterReview says bass rolls off below about 70 Hz; a retailer listing gives 40 Hz. Treat the bass floor as unverified.

### JBL Bar 1000 MK2 (7.1.4)

- Channels: 7.1.4 by JBL's spec. TechRadar's table lists 9.1.4. JBL's figure is used here. [JBL spec](https://www.jbl.com/BAR-1000MK2.html), [TechRadar](https://www.techradar.com/televisions/soundbars/jbl-bar-1000mk2-soundbar-review)
- Power: 960 W total; bar 500 W; two surrounds at 80 W; 300 W sub. [JBL spec](https://www.jbl.com/BAR-1000MK2.html)
- Frequency response 33 Hz to 20 kHz (-6 dB). [JBL PDF](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwbfcc4213/pdfs/JBL_BAR_1000MK2_Specsheet_EN.pdf)
- Connections: HDMI eARC out; three HDMI in; optical; Bluetooth 5.3 (A2DP 1.3); Wi-Fi. [JBL PDF](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwbfcc4213/pdfs/JBL_BAR_1000MK2_Specsheet_EN.pdf)
- HDMI in: 4K Dolby Vision and HDR10+ pass-through. [JBL PDF](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwbfcc4213/pdfs/JBL_BAR_1000MK2_Specsheet_EN.pdf)
- Audio: Dolby Atmos and DTS:X; MultiBeam 3.0; PureVoice 2.0; night listening; SmartDetails. [JBL PDF](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwbfcc4213/pdfs/JBL_BAR_1000MK2_Specsheet_EN.pdf)
- Calibration: the owner's manual lists a **microphone for sound calibration**. [JBL manual](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw3a4f94e5/pdfs/HA_JBL_Bar%201000MK2_OM_Global_EN_V3.pdf)

## 3. Conflicts between sources

| Item | Source A | Source B | Used here |
|---|---|---|---|
| Sony Bar 6 codecs | Sony spec: SBC, AAC | LDLC and a Spanish reseller: LDAC | Sony's spec |
| JBL Bar 1000 MK2 channels | JBL: 7.1.4 | TechRadar table: 9.1.4 | JBL's spec |
| LG S80TR bass depth | HomeTheaterReview: rolls off below ~70 Hz | Listing: 40 Hz | Both, flagged |
| Samsung Q800H power | Listing: 400 W | Samsung spec: not stated | Listing, flagged |

## 4. What this means for Core EQ

1. **Built-in calibration can stack with Core EQ.** The JBL Bar 1000 MK2 (microphone), the LG S80TR (AI Room Calibration), and the Samsung Q800H (automatic room correction, SpaceFit) each correct the room themselves. If the bar corrects, and Core EQ also corrects the same room, the two corrections add. Core EQ should detect or warn about this, and the user should be told to turn the bar's own calibration off before measuring. This is not built.
2. **Bitstream passthrough may bypass Android effects.** Dolby Atmos and DTS:X sent over eARC are typically passed to the bar as an encoded stream. Android's effect chain then cannot process them. This is consistent with the general behaviour of Android audio, and is not verified on these bars. It affects all six models whenever Atmos is playing. Core EQ's correction would then apply only to PCM content.
3. **Proprietary wireless routes may skip the TV's output.** Samsung's wireless Dolby Atmos and LG's WOWCAST send audio over Wi-Fi from a compatible TV. Core EQ may not see that path. This needs a device test before any claim.
4. **HDMI input limits.** The Samsung Q800H's HDMI input is 4K/60 only. Gamers would need to connect through the TV. This does not affect audio correction, but it affects setup advice.
5. **Old and small bars.** The Bar 2.0 has a 70 Hz low end and no Atmos. Bass correction below 70 Hz is not meaningful on it, and the presets should not promise bass.
6. **Look-alike model names.** The Bar 800 and Bar 800 MK2 are different products (the MK2 is 780 W, 7.1, per JBL). Name matching must keep these apart.

## 5. Unknowns

- Whether the JBL Bar 800 calibration uses a microphone (spec says only "easy sound calibration").
- The Bluetooth codecs on the Samsung Q800H, JBL Bar 800, and JBL Bar 1000 MK2 (not stated in the sources reviewed).
- Whether the LG S80TR calibration uses the bar's microphone or the phone.
- How Android reports each bar's product name over HDMI eARC, Bluetooth and optical. The app's name matching depends on this, and it has not been tested on hardware.
- The effect of each bar's own sound modes (Samsung Adaptive Sound, JBL MultiBeam, LG WOW Orchestra) on the measured result.

## 6. Verification status

- Specifications come from manufacturer pages and PDFs where available, and from retailer listings and reviews where not. Each claim above names its source.
- No device was measured in this research. Nothing here has been tested on the bars, the TV, or with Core EQ.
- The app's presets were not changed by this research.
