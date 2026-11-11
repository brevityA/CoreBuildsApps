# Core EQ Home status: states, evidence, precedence

Home's first summary line says what Core EQ has **applied**, which is not the
same as what is switched on. This page lists every state it can show, the
evidence behind each one, and which state wins when several conditions hold
at once. The logic is `HomeSummary.stateOf` (pure Kotlin). Its facts are read
in one place, `StatusFacts`, which Home and the diagnostics summary share.

## The facts and where they come from

| Fact | Source | Trusted when |
|---|---|---|
| Correction on | the Correction switch (`ProfileStore.correctionEnabled`) | always: it is the user's setting |
| Service running | `EqService.running`, set while the service instance is alive in this process | always |
| Measuring | `ProfileStore.measurementPaused`, written by the service when Measure suspends it and cleared on resume, start and stop | only while the service runs |
| Any profile | the saved profile list is not empty | always |
| Profile for this output | `OutputRoute.pick` returned a profile for the output in use | as good as the route evidence below |
| Output identified | `OutputRoute.current` returned a kind other than `ambiguous` or `unconfirmed` (`OutputRoute.isIdentified`) | the route evidence: Android's anticipated route (API 33+), a connected-device estimate, or a probe track's reported route |
| Applied engine | `ProfileStore.appliedEngine`, written when the service holds a configured effect (DynamicsProcessing or Equalizer) and cleared when it holds none, starts or stops | only while the service runs; ignored otherwise |
| Media playing | the service's last status: the output-mix effect is configured **and** Android reports active media playback | only while the service runs |
| Fault | the service's last status, marked as an error | only while the service runs |

**"Applied" means configured, not heard.** An applied engine shows that
Core EQ holds an effect that Android accepted and read back. Android does not
report which app's stream passes through that effect, or what reaches the
speakers. The applied line says so every time it appears: "Android does not
confirm which stream passes through it."

## Precedence

The first row whose condition holds is the state shown. Later rows assume
every earlier condition is false.

| # | State | Condition | Home says | Why it outranks the rows below |
|---|---|---|---|---|
| 1 | Off | Correction is off | "Correction is off. Nothing Core EQ does is applied." | The user's switch is certain; nothing else matters while it is off |
| 2 | Measuring | Service running and suspended for a measurement | "Paused while a room is measured…" | A measurement deliberately pauses everything, including a first measurement with no profile yet |
| 3 | No profile | No saved profile | "Nothing to apply yet. Next: measure this room, or import a profile" | Without a profile there is nothing the service could apply, running or not |
| 4 | Not running | The service is not running | "Unknown: the correction service is not running…" | Every later fact comes from the service; without it, a stored fault, engine or measuring flag is stale |
| 5 | Fault | The running service's last status is an error | "Not applied: <the TV's reason>" | A live refusal outranks route and engine facts that may predate it |
| 6 | Output unknown | No profile for the output, and Android has not identified the output (including ambiguous or unconfirmed routes) | "Unknown: Android has not identified the output in use (…), so no profile is applied." | Saying "nothing measured for this output" would be a guess about which output |
| 7 | No profile for this output | No profile for the identified output | "Paused: nothing measured for <output>. Next: measure it, or switch back…" | A configured engine left over from the previous output is not what plays here |
| 8 | Applied | An engine is held and configured | "Applied by <engine> · media is playing / nothing is playing [· output not identified by Android] [· A/B bypass] . Android does not confirm which stream passes through it." | |
| 9 | Pending | Everything else: correction on, running, a profile for the output, no engine yet | "Pending: correction is on and waiting for a player or a route check." | |

A profile saved before outputs were recorded (or for an output Android did not
name) can apply while Android names no output. Home then shows **Applied**
with "output not identified by Android" instead of implying a known output.

## Tests

- `HomeSummaryTest`: each state reached by the fact that defines it, and the wording.
- `HomeSummaryPrecedenceTest`:
  - Every combination of the facts, checked against rules stated independently of the code's order: Off whenever the switch is off; no live state without the service; Applied only with every precondition; no line claiming a stream is processed; unknown said where the output is not identified.
  - Each adjacent pair of rows in the table above with both conditions true.
  - Two transition sequences (a viewing session; a fault and a service loss).
- `StatusFactsTest` (Robolectric): a stopped service's stored engine, playing flag and fault are not trusted, and ambiguous or unconfirmed routes are not counted as identified outputs.

None of these run on a TV. Hardware check 4 on the pull request covers the states on real outputs.
