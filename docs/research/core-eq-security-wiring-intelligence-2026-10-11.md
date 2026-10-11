# Core EQ security hardening, wiring, and intelligent-EQ roadmap

**Research date:** 2026-10-11
**Scope:** Static review of the Core EQ Android TV app and Android phone companion, current platform guidance, and practical AV signal-chain recommendations. This is a research brief and hardening plan—not a penetration test, Android build result, device qualification, or claim of calibrated phone-microphone performance.

## Executive recommendations

1. **Close the companion listener when the TV screen is no longer visible.** `CompanionActivity` starts the listener while the activity is created and closes it in `onDestroy`, but does not close it in `onStop`. A Home/app switch can leave a LAN control listener alive while the user believes the companion screen is gone.
2. **Bound unauthenticated network work.** The listener uses a four-thread fixed pool whose work queue is unbounded. Each socket gets a 20-second read timeout, but there is no total request deadline; a slow peer can retain a worker and repeated connections can accumulate queued sockets. This is a local-network availability risk, not evidence of an internet-exposed service.
3. **Replace the global five-failure pairing shutdown with per-peer throttling.** The one-time code is a strong random 128-bit key, so brute-force guessing is not a realistic reason to let any LAN peer disable pairing. Five unique malformed/authentication-failing envelopes can close the current pairing window for everybody.
4. **Keep two distinct wiring answers visible:** for dependable correction across sources, use a receiver/processor with correction in the final speaker chain; Core EQ's Android TV effect is device-, route-, and format-dependent. For Core EQ's own software EQ, PCM can be a useful diagnostic, but forcing PCM may change channel count or format and does not guarantee the effect is applied.
5. **Build intelligence around confidence and evidence, not automatic claims:** route/configuration fingerprinting, a guided signal-path preflight, same-position repeatability, staged seat-area measurements, room-placement advice, and a before/after verification view. Keep phone-mic calibration and cross-device accuracy explicitly unqualified until measured against a calibrated reference.
6. **Profile before optimizing DSP.** The current path has a 10-second sweep, a full FFT convolution, and a stochastic six-filter fit. It is measurement-time work, not per-audio-frame work. Benchmark time and peak memory on low-end TV hardware first; then consider caching sweep/inverse data, reducing temporary-array copies, and using a compact binary upload rather than JSON/Base64.

## 1. What the repository currently does

The following are source-level observations, not device-tested findings.

| Area | Current behavior observed in code | Implication |
|---|---|---|
| Listener lifecycle | `CompanionActivity` starts `CompanionServer` on screen creation and closes it from `onDestroy`; there is no `onStop` teardown. | Treat “on-demand” as **activity-created**, not yet guaranteed “visible-screen-only.” Add a lifecycle test/fix before broad rollout. |
| Bind and peer filter | `CompanionServer` binds IPv4 `0.0.0.0` on an ephemeral port and accepts peers whose address is in RFC1918 ranges. | It is reachable from any routable private-LAN interface while open—not just the paired phone or one Wi-Fi SSID. Pairing/authentication still gates control. |
| Pairing and authorization | Pairing is off until the TV user starts it; code is 16 random bytes, valid for 120 seconds, and authorizes one phone. RPC operations are an allow-list (status/profiles, profile/mode/enable controls, finite measurement, revoke). | Good least-functionality boundary. Do not expose general ADB, arbitrary intents, volume, or shell control through this endpoint. |
| Pairing lockout | Five failed decrypt/authentication attempts close the **whole** current pairing window. The counter is global, not per peer. | A private-LAN peer that can reach the port can cause a pairing denial without guessing the 128-bit key. |
| Work admission and parsing | Four workers are created by `Executors.newFixedThreadPool`; `Socket.SO_TIMEOUT` is 20 seconds per blocking read; request headers are byte-read up to 8 KiB; bodies are capped at 3 MiB. | The fixed pool has an unbounded queue, and a per-read timeout is not an absolute request deadline. Bound both concurrent sockets and queued work; close rejected sockets. |
| Message protection | Request/response JSON bodies use AES-GCM, 128-bit tags and 12-byte random nonces. The HTTP path is AAD. RPCs are time-limited to 120 seconds and checked against a 2,048-entry nonce cache. | Payload confidentiality/integrity is present, but this is not TLS. The cache can evict a still-fresh nonce under high request volume; the generated `request_id` is not enforced server-side. |
| Secret storage | Phone pairing token is AES-GCM-encrypted using an Android Keystore AES key. The TV stores the 256-bit token as Base64 in private `SharedPreferences`. | Phone-side key handling is stronger. The TV preference is app-sandbox protected, not cryptographically wrapped at rest. |
| Backup | `backup_rules.xml` and `data_extraction_rules.xml` explicitly exclude both TV and phone pairing preference files from cloud backup and device transfer. | This is a good control; preserve it with tests whenever preference files or backup rules change. `allowBackup=true` remains appropriate for non-credential user data if exclusions stay correct. |
| Android components | Main launcher and protected system-broadcast receiver are exported; app screens/services are non-exported; FileProvider is non-exported and scoped to the update cache path. | Good default component boundary. Keep explicit `android:exported` values and lint this on every build. |
| Local-network permission | The manifest declares `ACCESS_LOCAL_NETWORK`; the TV and phone check/request it on API 37+. Denial is surfaced. | Correct platform direction for target SDK 37. Add permission-rationale/permanent-denial recovery and test grant/revoke on actual API 37 devices. |
| Capture/privacy | Phone capture requests `RECORD_AUDIO` when a measurement starts, records a bounded session, reports input/effect metadata, buffers PCM locally and uploads it to the paired TV. `onStop` cancels the capture. No cloud upload is implemented. | Preserve explicit start/stop, on-device indication, cancellation, and “metadata is not calibration” language. |
| Update channel | Update checks/downloads use HTTPS URLs, constrain the feed/APK URL families, validate APK package/version, and compare APK signing-certificate digests. SHA-256 in the feed is optional. | Strong baseline for a sideload updater. Require the hash in production feeds and validate the final manifest URL after redirects as defense in depth. |
| Audio application | `OutputRoute.current()` is an estimate for another app's route; the sweep can report its own `AudioTrack` route. `mayPassThrough()` is a warning clue, not live stream proof. Android effects attach to sessions; session-0/global behavior is device-dependent. | The app should present route/effect read-back as evidence levels, not a universal guarantee that every app or codec path was equalized. |

Relevant code: [`CompanionActivity`](../../coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/CompanionActivity.kt), [`CompanionServer`](../../coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/CompanionServer.kt), [`CompanionCrypto`](../../coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/CompanionCrypto.kt), [`PhoneCredentialStore` / client](../../coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/CompanionClient.kt), [`PhoneCaptureEngine`](../../coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/PhoneCaptureEngine.kt), [`AndroidManifest.xml`](../../coreeq/app/src/main/AndroidManifest.xml), and [`OutputRoute`](../../coreeq/app/src/main/kotlin/tv/corebuilds/eq/apply/OutputRoute.kt).

## 2. Security hardening plan

### Threat model

Protect: the TV-control token; local control of correction/profile/mode; measurement-session integrity; microphone PCM and device/route metadata; availability of the TV-side listener and audio service.

Assume that another device on the same private network may be untrusted (guest/IoT device, compromised phone, shared household network), the TV may be left unattended, IP addresses can change, and users may lose a phone. The companion is not designed to resist a rooted/compromised TV or malicious firmware. Private RFC1918 addressing is a **scope restriction**, not an authentication mechanism: a home LAN is not automatically a trusted zone.

### Prioritized findings and actions

| Priority | Finding / evidence | Recommendation |
|---|---|---|
| **P1 — listener lifetime** | The listener closes on activity destruction, not activity stop. A stopped activity can remain in the back stack while its server thread stays alive. | Tie the listener to a genuinely visible, user-authorized session. On background/stop, stop accepting, invalidate an unconsumed pairing code, cancel any active measurement safely, resume EQ if it was suspended, and close active sockets. Recreate on return rather than keeping a hidden listener. Test Home, Back, activity switch, screen-off, process death, and permission dialogs. |
| **P1 — resource exhaustion** | Four workers plus an unbounded executor queue; a 20-second timeout resets for each read; no connection cap or whole-request deadline. | Use a bounded `ThreadPoolExecutor`/queue (or no queue), a small maximum active-connection count, short header/body deadlines, strict framing (`Content-Length`, reject `Transfer-Encoding`/duplicate lengths), per-peer request-rate limits, and immediate socket closure on rejection. Keep the 3 MiB body cap and enforce operation-specific limits before allocation, including the existing PCM/profile ceilings. Track accepted sockets so queue rejection and server shutdown close sockets whose handler has not started. Return a generic error without logging secrets or request bodies. |
| **P1 — pairing denial of service** | Any private-LAN peer can submit five distinct JSON envelopes with a parseable nonce and invalid GCM data to exhaust the global failure counter. The 128-bit pairing key already makes online guessing infeasible. | Rate-limit bad attempts per source address, use a short backoff per peer, and avoid globally closing pairing from unauthenticated failures. If a global circuit breaker is retained for resource protection, make it temporary and recoverable without restarting the user's pairing flow. Add a test that an unrelated peer cannot disable pairing for the intended phone. |
| **P1 — secret lifecycle** | TV pairing token is Base64 in private preferences; phone-side credential encryption is already backed by Keystore. Backup exclusions are present. | Wrap the TV token with a TV-side Android Keystore AES-GCM key (fallback only if a device lacks the required provider; do not claim hardware-backed unless queried). Delete/rotate the alias when TV pairing is revoked. Delete the phone Keystore alias on local unpair/forget so old ciphertext cannot be reused. Test migration, damaged ciphertext, key invalidation, reinstall, restore, and revocation. Keystore keys are non-exportable by design, but hardware backing is device-dependent ([Android Keystore](https://developer.android.com/privacy-and-security/keystore)). |
| **P2 — protocol freshness / idempotency** | The replay cache is capped at 2,048 and old entries are evicted while timestamps remain valid for up to 120 seconds. `request_id` is generated on the phone but not deduplicated by the TV. | Add a versioned session protocol with authenticated sequence numbers and a bounded out-of-order window, plus server-side request-ID dedupe for stateful operations (especially starting a measurement). Derive separate phone→TV and TV→phone keys with context labels; bind version, method, path, direction, pairing/session ID and payload type in AAD. Define key rotation/expiry and re-pair behavior. |
| **P2 — transport identity / metadata** | AES-GCM protects message bodies, but the socket is HTTP over TCP without TLS. The path, headers, lengths, timing, connection attempts, and current plaintext error responses remain observable; endpoint identity rests on possession of the one-time code/token. | Prefer a standard TLS 1.3 transport for the LAN channel, with the TV certificate fingerprint bound to the TV-displayed/QR pairing step and a documented rotation path. Do not replace the existing AEAD with another home-grown protocol. If retaining body AEAD during migration, explain the distinct end-to-end purpose; avoid needless double encryption. TLS does not remove the need for explicit pairing/authorization. Android recommends secure network communication; RFC 9325 is the current IETF TLS best-practice document ([Android networking](https://developer.android.com/develop/connectivity/network-ops/connecting), [RFC 9325](https://datatracker.ietf.org/doc/html/rfc9325)). |
| **P2 — pairing confirmation** | The code is displayed on the TV for two minutes and the first valid holder pairs immediately. | Keep the random high-entropy code; do not replace it with a short numeric PIN. Consider a final TV confirmation showing the requesting phone label and a short authentication string, especially if pairing is moved to QR or service discovery. Sanitize control/bidi characters in the displayed phone name. |
| **P2 — Android 17 permissions and discovery** | API 37 runtime permission is requested; the current flow is manual IP/port and does not use NSD. | Keep the TV's broad `ACCESS_LOCAL_NETWORK` permission because it accepts a TCP connection. On the phone, consider Android's `NsdManager` system service picker (`DiscoveryRequest.FLAG_SHOW_PICKER`) to let the user select one TV and receive service-scoped access without broad LAN scanning. Resolve current addresses before each connection—do not persist a stale IP. Keep manual entry as a fallback. Explain the Nearby devices prompt and provide a Settings recovery path if the user has denied it. See [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission) and [`NsdManager`](https://developer.android.com/reference/android/net/nsd/NsdManager). |
| **P2 — cleartext and update channel** | No Network Security Config is present. The updater uses HTTPS/host rules and signature checks, while the optional SHA-256 can be absent and manifest redirects are not explicitly checked. The companion is a raw `Socket`, not `HttpURLConnection`. | Add explicit cleartext opt-out/network-security configuration for normal HTTP clients, but **do not assume it secures or blocks the custom raw-socket protocol**—TLS must be implemented there. Validate that the update manifest's final redirected URL is HTTPS on the approved host, require `apkSha256` in release feeds, and retain APK signing-certificate, package and version checks. A hash hosted beside the manifest detects transfer corruption but is not a signature over a compromised feed. See [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config). |
| **P3 — controlled auditability** | Pairing/session failures have logs/status, but there is no formal security event model or external audit. | Keep only privacy-minimal counters: rejected connection, rate-limit hit, pairing success/failure, permission denial, revoke, measurement start/stop. Never log tokens, pairing codes, PCM, complete encrypted envelopes, or microphone metadata not needed for diagnostics. Add tests around component export, backup exclusions, TLS policy, size/time bounds and session cleanup. Use [OWASP MASVS](https://mas.owasp.org/MASVS/) as a verification checklist, not a compliance claim. |

AES-GCM's IV/nonce uniqueness is a per-key requirement; NIST calls out its critical importance ([NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf)). A high-entropy token plus random 96-bit nonces is a strong starting point, but a persistent key shared by both directions benefits from direction-specific derived keys and explicit per-key message limits. HKDF's context input is designed to bind derived key material to protocol/application context ([RFC 5869](https://datatracker.ietf.org/doc/html/rfc5869)). Any protocol change should receive a focused crypto review and interop tests before release.

### Security verification matrix

- **Lifecycle:** start pairing, press Home, switch apps, screen-off, revoke while connected, and return. Verify the TV no longer accepts new sockets while hidden, the pairing code cannot be reused, and any suspended correction resumes after measurement teardown.
- **Admission:** open more than the configured socket cap; send partial headers/body slowly; send duplicate/negative/oversized lengths, unsupported transfer encoding, malformed JSON, repeated nonce, unknown path/method, and a full PCM upload. Verify bounded memory/thread use and prompt rejection.
- **Pairing:** submit invalid encrypted envelopes from a second LAN peer while the user pairs the intended phone. Verify per-peer throttling and that the legitimate phone can still pair.
- **Protocol:** tamper with every authenticated field; replay within and after the freshness window; saturate the replay window; resend a state-changing request ID; simulate a wall-clock change; revoke and verify old tokens fail.
- **Storage/backup:** inspect backup and device-transfer outputs on API 30 and API 31+; verify token preferences are absent, non-credential profiles behave as intended, Keystore invalidation is handled, and a restored install must pair again.
- **Android 17:** test API 37 grant, denial, permanent denial/recovery, revocation while open, TV inbound sockets, phone outbound sockets, and the NSD picker fallback if implemented.
- **Updates/components:** redirect manifest and APK URLs to disallowed hosts; test missing/bad hash, wrong signer/package/version; run Android Lint and inspect merged release manifest.

The code review was static. This checkout's earlier Android Gradle verification was blocked by the absence of a Java runtime; CI/device testing is still required.

## 3. Recommended audio wiring and configuration

### First choose where correction must be guaranteed

For a robust system-level correction path, put a receiver or dedicated room-correction processor **after the source selection and before the power amplifiers/speakers**. Let that hardware own the final correction for every input it actually processes. Core EQ is an Android TV app; it cannot promise that arbitrary apps, external HDMI sources, encoded passthrough, or downstream DSP traverse its Android audio effect.

#### Topology A — receiver/processor owns correction (recommended when coverage matters)

```text
External streamers / consoles / disc players
                    │ HDMI IN
                    ▼
              AVR / DSP processor ───── HDMI OUT ─────► TV
                    │
        speaker outputs / subwoofer pre-out
                    ▼
          passive speakers + powered sub

TV built-in apps ── HDMI eARC/ARC ──► AVR TV/eARC input
```

Use the receiver's room-correction system or a dedicated processor, and verify it is not in a “Direct/Pure” mode that bypasses its DSP. Route TV built-in apps back to the AVR over the labelled ARC/eARC ports. Dolby documents that TV output varies with PCM/bitstream/passthrough setting, the capability handshake, codec support and ARC/eARC; direct-to-AVR source wiring can also avoid some TV-path A/V-sync issues ([Dolby endpoint setup guidance](https://professionalsupport.dolby.com/s/article/Endpoint-Device-Testing-for-Streaming-Services?language=en_US)). The exact supported formats are model-specific.

#### Topology B — Core EQ runs on the TV

```text
Apps running on Android TV ──► Android TV audio pipeline / Core EQ attempt
                                         ├──► TV speakers
                                         └──► HDMI eARC/ARC ──► AVR/soundbar ──► speakers
```

This is the right topology to test Core EQ, but not proof of coverage. Start with the normal output configuration, then use a **temporary PCM A/B** only as a diagnostic if the TV exposes it. PCM may allow a decoded/mixed path but can change channel count, downmix/upmix behavior, dynamic processing or available formats; forcing it permanently can sacrifice a bitstream format or object metadata. Conversely, passthrough/bitstream can bypass effects that only see the mixed PCM path. Record the exact TV audio setting and receiver mode with each measurement. Android's public route APIs cannot reveal another app's actual live route or prove an effect processed its stream; the repository's existing [audio-routing research](google-tv-audio-routing-2026-10-05.md) correctly describes this limit.

#### Topology C — optical, analog and legacy systems

```text
TV Optical OUT ── TOSLINK ──► AVR / soundbar Optical IN
TV fixed LINE OUT ── RCA ──► amplifier / powered-speaker LINE IN
```

Optical is a useful legacy fallback, but do not promise a particular surround codec or high-bitrate format without checking both manuals. TV settings may output PCM, downmix, or pass a supported encoded format. Analog line-out is appropriate only when the TV provides a true line-level output; use a fixed output when available and control listening level at the amplifier. Do not connect a power-amplifier speaker output to an RCA/line input. Avoid headphone-out as a reference measurement feed unless its level is fixed and checked for clipping/noise.

### Physical speaker/sub wiring and crossover setup

- For passive speakers, connect each receiver's `+`/red terminal to that speaker's `+`/red terminal and `−`/black to `−`/black. Keep channel identity consistent; an accidental polarity reversal can undermine bass and imaging. Use the receiver and speaker manuals for impedance and wire gauge; never join amplifier outputs or parallel speaker loads unless the manufacturer explicitly supports the wiring.
- For a powered subwoofer, the usual AVR path is `SUB/LFE PRE OUT` to the sub's `LFE/LINE IN`. Keep the AVR's bass-management configuration and the sub's own low-pass setting coordinated; a second unintended low-pass stage can create a crossover dip. Avoid connecting speaker-level outputs to line-level inputs without a specifically designed speaker-level input.
- Set AVR speaker sizes/crossovers from the speaker's low-frequency capability, not cabinet size. Denon describes 80 Hz as a broad starting/default point on many models and recommends higher crossover settings for speakers that cannot reproduce low bass; that is a starting point, not a universal answer ([Denon crossover guidance](https://support.denon.com/app/answers/detail/a_id/4024/~/setting-the-crossover-frequency)). Re-measure the speaker/sub integration and confirm the AVR's distance/phase settings.
- Prefer one deliberate correction owner. If Core EQ and an AVR's room correction, a soundbar's “AI”/night/dialogue mode, and dynamic loudness are all active, the result is a cascade. Either bypass the other correction while measuring or treat the existing downstream DSP as part of the final, fixed chain and never change it without creating/revalidating the corresponding output profile.

### Measurement wiring and repeatability checklist

1. Label the chain precisely: source/app; TV output; HDMI ARC/eARC/optical/analog; AVR or soundbar model/input; speaker/sub layout; TV digital output mode; AVR listening mode/correction; volume; crossover; and any night/dialogue/loudness settings.
2. Keep that chain and level unchanged for baseline, correction and verification. If any downstream setting or output changes, select/measure the matching route profile rather than applying a curve measured on a different chain. Core EQ already records an output route/profile association, but its route estimate must remain qualified.
3. Put a measurement microphone on a stand at ear height at the principal listening position; select the calibration file and orientation that match the *specific microphone and pointing direction*. miniDSP documents serial-specific mic files and different 0°/90° orientations for its UMIK microphones ([UMIK-1 calibration guidance](https://www.minidsp.com/products/acoustic-measurement/umik-1)). A phone's reported microphone response is not equivalent to that traceable calibration.
4. Check level before a sweep: loud enough for signal-over-noise, below clipping, room quiet, people away from the mic. REW's official guide calls out calibration files, level checks, clipping, and the danger of synchronously averaging repeated sweeps when input/output are on independent devices ([REW measurement guide](https://www.roomeqwizard.com/help/help_en-GB/html/makingmeasurements.html)). Core EQ's timing markers mitigate clock mismatch but do not calibrate the phone mic or the TV's route.
5. A deep/narrow bass null is often a placement/cancellation issue, not a request for a large boost. Change seat/sub placement or use a qualified multi-sub optimization before raising EQ. miniDSP explicitly notes that a null cannot be corrected by simply applying EQ and that multiple subs can improve consistency over a listening area ([subwoofer tuning](https://www.minidsp.com/applications/subwoofer-tuning/tuning-multiple-subs)).
6. Begin with the existing single-position capture. Multi-position correction should be an optional, preserved set of per-position measurements—not a hidden average. Choose and validate the averaging domain and target against reference measurements before changing the correction algorithm.

### Phone–TV local-network wiring/configuration

```text
Android phone ── same trusted Wi-Fi/LAN ──► Core EQ TV
                                           ephemeral TCP port shown by TV
```

- Use a trusted home LAN with the phone and TV on a network that permits peer-to-peer client traffic. Guest Wi-Fi/client isolation commonly prevents that; do **not** disable isolation for untrusted guests/IoT devices just to make pairing work. If using VLANs, explicitly allow the trusted phone to reach the TV's displayed service port; mDNS discovery may not cross VLANs without a reflector.
- A DHCP reservation can keep manual-IP pairing stable. Better long-term UX is on-demand NSD discovery with the Android system picker and fresh address resolution; keep manual IP/port as a fallback.
- Never port-forward the companion port from the internet, enable router “remote management” for it, or treat an RFC1918 source address as trusted identity. Pair only while physically present at the TV, close pairing after use, and revoke a lost phone on the TV.
- This is a direct phone-to-TV connection; no cloud account/backend is required. The TV's separate updater uses HTTPS to GitHub. Do not use the phone as a proxy, and do not let a guest-network/VPN/firewall workaround silently widen the listener's reach.

## 4. Features to make Core EQ more efficient and intelligent

The most useful next features improve the measurement-to-action loop and explain **why** a correction is or is not safe. Several items below extend capabilities already present—per-band SNR, clipping/drift/bandwidth checks, output-tagged profiles, null/phase/roll-off gating, filter headroom and visible import caveats—rather than replacing those safeguards.

### A. Better decisions from the current measurement

1. **Evidence-based path confidence.** Add a compact “what we know” panel: Android-anticipated output vs sweep's actual reported route; device/output profile match; PCM/direct/passthrough clues (label clues as clues); effect object/read-back; and whether acoustic before/after evidence exists. Use states such as `reported`, `estimated`, `not reported`, and `acoustically verified`; never collapse them into “system-wide EQ is working.”
2. **Guided preflight with actionable fixes.** Before a full sweep, check selected output, current profile match, volume/clipping headroom, sample rate, microphone permission/route, capture effects, and room noise. Then present specific next actions: lower playback level for clipping; quiet the room/raise level safely for low SNR; reconnect/select the intended output for route mismatch; remeasure after moving the mic for dropout/drift; change placement rather than boost a deep null. Run extra diagnostic sweeps only when the first capture's confidence is low.
3. **Same-seat repeatability gate.** Offer a second capture at the same marked position after a borderline/uncertain result. Compare smoothed band curves, route/device metadata, drift and per-band SNR; save both traces. If they disagree beyond a threshold validated on physical reference chains, ask for a better capture rather than averaging incompatible recordings. This will quantify whether a result is repeatable without pretending a generic phone is calibrated.
4. **Correction verification / application evidence.** Add an explicit “verify after correction” action using the same source, output, microphone, position, level and DSP state. Compare before/after only in bands that pass quality gates; separate “profile/effect configured” from “measured acoustic response changed.” Preserve the baseline and do not automatically feed the corrected measurement back into a new correction loop.
5. **Placement and crossover assistant.** Combine the existing null, low-rolloff, RT60 and transition reports with a short, prioritized suggestion: likely placement null; speaker capability/low-frequency roll-off; weak signal; or long decay that EQ cannot fix. For subwoofer systems, optionally guide one-sub/individual-sub measurements and crossover verification. Avoid asserting a room mode or damaged speaker from one phone sweep.

### B. Safe spatial and nonlinear intelligence (stage after qualification)

6. **Small listening-area mode.** After same-position repeatability and a calibrated reference path exist, offer center + a few nearby seat positions. Store every capture separately, use a validated energy/magnitude-domain combination, and show the spread as well as the aggregate. The aggregate targets a chosen listening zone; it must not promise each seat is flat. REW supports RMS/vector/dB averaging as distinct operations; choose deliberately, test against measured rooms, and preserve the individual responses ([REW features](https://www.roomeqwizard.com/features.html)).
7. **Seat/sub-placement comparison.** Let users compare candidate sub/seat positions and identify which change improves several positions before EQ. A deep cancellation cannot be fixed by a boost, and multiple subs can reduce spatial variation that a single global curve cannot remove ([miniDSP multi-sub basics](https://www.minidsp.com/applications/subwoofer-tuning/tuning-multiple-subs)). Keep an explicit “not enough positions to recommend” state.
8. **Two-level dynamics/distortion check.** A future calibrated path could repeat a sweep at two safe SPLs and compare level-normalized response/decay to detect dynamic compression or processing. Harmonic response analysis may help identify nonlinear behavior. This is a research candidate only: without a known acoustic level and validated mic/output chain, report an anomaly/level dependence—not THD, speaker damage, or a specific fault.

### C. More efficient correction and compute

9. **Fit to the real application backend.** Core EQ's correction fitter currently defaults to six parametric filters, while application may use the configured `DynamicsProcessing` PreEQ layout (24 correction bands plus a high guard band) or a device-reported platform `Equalizer` fallback (often five bands, but queried where available). Fit/quantize against the actual selected backend, its band centers/range and read-back values; show residual error and headroom for that backend. If a fallback cannot represent the curve, use the safer lower-resolution result and explain the residual rather than implying equivalence.
10. **Headroom-aware filter allocation.** Allocate filter degrees of freedom to high-confidence, correctable bands, favor cuts over large boosts, and include imported manual filters plus room correction when calculating worst-case headroom. Keep current null/SNR/phase gates as hard constraints. Treat backend read-back and a post-correction measurement as separate validation layers.
11. **Benchmark and then reduce repeated work.** Measure analysis wall-time, peak heap, GC pauses and upload size on representative low-end TV and phone hardware. If warranted, cache immutable generated sweeps/inverse kernels per sample rate; reuse FFT/window tables; reduce full-size intermediate copies; and avoid recomputing fits where the input fingerprint and configuration are identical. Keep caches bounded and invalidate on sample-rate/algorithm-version changes.
12. **Compact, bounded audio transfer.** The phone currently Base64-encodes PCM inside JSON, which expands the payload and creates transient copies. If profiling shows memory/latency pressure, add a versioned binary/chunked upload with a strict sample-count ceiling, session binding, per-chunk sequence/AEAD, integrity check, cancellation and back-pressure. Do not trade away authentication, max duration or in-memory privacy for bandwidth.
13. **Keep intelligence local and inspectable.** Start with deterministic signal features and explainable rules, not a cloud model or continuous microphone analysis. A local anomaly classifier may rank “room/placement,” “route/config,” “capture quality,” and “likely output limitation,” but it should display the measurements and confidence that led to its suggestion. The microphone remains off except during an explicit measurement.

## 5. Suggested delivery order and acceptance gates

| Stage | Deliverable | Acceptance gate |
|---|---|---|
| **1 — Contain the LAN surface** | Visible-session listener teardown; bounded connection admission/deadlines; per-peer pairing throttles; robust cleanup and safe errors. | Automated protocol tests plus API 37 permission and Home/Back/screen-off device tests; no unbounded queue/socket growth; unauthorized peer cannot control or globally disable pairing. |
| **2 — Harden credentials/protocol** | TV Keystore wrapping; key deletion/rotation; versioned directional keys/nonces/idempotency; transport TLS decision and authenticated pairing binding; backup/update regression tests. | Crypto review; tamper/replay/restore/revoke tests pass; no token/audio in logs or backups; update requires an approved HTTPS destination, APK signer/package/version and release hash. |
| **3 — Make wiring observable** | Topology wizard/checklist; exact output/DSP settings attached to measurement; route-confidence wording; NSD-picker experiment with manual fallback. | TV/AVR/soundbar matrix demonstrates which combinations work; the UI never calls estimated or codec-capability data proof of active-stream coverage. |
| **4 — Improve useful results** | Guided preflight; repeatability; before/after verification; placement/crossover recommendations. | Predeclared reference-mic error/repeatability budget, fixed chain, and a recorded physical-device test matrix. Phone mic remains “uncalibrated” until its exact model/route has traceable qualification. |
| **5 — Add advanced optimization** | Multi-position/seat-area EQ, nonlinear diagnostics, backend-aware fitting and measured performance improvements. | Individual traces retained; validated averaging and safety/headroom constraints; field measurements outperform the single-position baseline without unsafe null boost or false diagnosis. |

## Sources

### Android, network and mobile security

- [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission) — API 37 runtime permission for incoming/outgoing LAN socket operations and privacy-preserving picker option.
- [Android `NsdManager`](https://developer.android.com/reference/android/net/nsd/NsdManager) — system service picker, service-scoped permission, current service address resolution, and no-stale-IP guidance.
- [Android network communication best practices](https://developer.android.com/develop/connectivity/network-ops/connecting) and [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config) — secure transport and cleartext controls.
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore) — non-exportable keys and device-dependent hardware backing.
- [Android Auto Backup rules](https://developer.android.com/identity/data/autobackup) — default inclusion of shared preferences and explicit include/exclude rules for Android 12+ cloud and device transfer.
- [Android component access-control guidance](https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components) — minimize exported components and enforce permissions for sensitive entry points.
- [OWASP MASVS](https://mas.owasp.org/MASVS/) — mobile storage, crypto, auth, network, platform, privacy and code verification groups.
- [OWASP API4:2023 — Unrestricted Resource Consumption](https://owasp.org/API-Security/editions/2023/en/0xa4-unrestricted-resource-consumption/) — bound payloads, execution resources and per-client rates.
- [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf) — AES-GCM IV uniqueness requirement.
- [RFC 5869 (HKDF)](https://datatracker.ietf.org/doc/html/rfc5869) — purpose/context-bound derived keys.
- [RFC 9325](https://datatracker.ietf.org/doc/html/rfc9325) — IETF current TLS/DTLS deployment recommendations.

### Audio routing, wiring and measurement

- [HDMI eARC overview](https://www.hdmi.org/spec2sub/enhancedaudioreturnchannel) — supported high-bandwidth audio formats and the TV-to-AVR/soundbar return path.
- [Dolby endpoint-device setup guidance](https://professionalsupport.dolby.com/s/article/Endpoint-Device-Testing-for-Streaming-Services?language=en_US) — TV output/handshake variability, ARC/eARC format constraints and direct-to-AVR topology considerations.
- [Dolby home-theater setup](https://www.dolby.com/about/support/guide/home-theater-setup/) — receiver/speaker system roles and connection planning.
- [Denon crossover-frequency guidance](https://support.denon.com/app/answers/detail/a_id/4024/~/setting-the-crossover-frequency) — crossover depends on speaker low-frequency capability; 80 Hz is a common receiver default, not a universal mandate.
- [Room EQ Wizard measurement guide](https://www.roomeqwizard.com/help/help_en-GB/html/makingmeasurements.html), [calibration-file help](https://www.roomeqwizard.com/help/help_en-GB/html/meter.html), and [features](https://www.roomeqwizard.com/features.html) — calibrated input, level/clipping checks, asynchronous-device caveats, and distinct averaging modes.
- [miniDSP UMIK-1 calibration guidance](https://www.minidsp.com/products/acoustic-measurement/umik-1) — per-microphone calibration files and orientation-specific use.
- [miniDSP multi-sub tuning basics](https://www.minidsp.com/applications/subwoofer-tuning/tuning-multiple-subs) — spatial variation and why EQ alone cannot fix a cancellation between seats.

### Repository research already informing this brief

- [`google-tv-audio-routing-2026-10-05.md`](google-tv-audio-routing-2026-10-05.md) — ordinary-app route visibility, session-0 effect and passthrough limits.
- [`core-eq-phone-companion-readiness-2026-10-11.md`](core-eq-phone-companion-readiness-2026-10-11.md) — phone capture, permissions, privacy and physical qualification boundary.
- [`core-eq-poweramp-import-and-accuracy-roadmap-2026-10-11.md`](core-eq-poweramp-import-and-accuracy-roadmap-2026-10-11.md) — existing DSP safeguards and staged accuracy roadmap.
