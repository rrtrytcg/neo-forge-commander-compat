# Decree of Pain autonomous card-use witnesses

2026-10-17. **PASS: both casting and cycling observed through normal AI selection and native payment.**
Combined with the existing 69 native correctness checks, this promotes Decree from unlocked to
verified for its documented opt-in rules. No production AI or profile change was required.

## Setup

- Decree seat: `decks/DecreeProbe.dck`, four Decrees and 56 Swamps.
- Casting case: three copies of `decks/CastTargets.dck`, each 36 Leatherback Baloths and 24 Forests.
  The 4/5 creatures survive -2/-2, so they make the full spell useful without qualifying cycling.
- Cycling case: three copies of `decks/CycleTargets.dck`, each 36 Grizzly Bears and 24 Forests.
  The 2/2 creatures qualify for the cheaper cycling effect.
- Four-player Constructed simulator, Default AI / Classic policies, seeds 95771 and 95772, profile on
  and off, fixed 20-second observations. These deliberately nonstandard decks isolate card use;
  deck-construction legality, game completion and winners are not acceptance criteria.
- Private jar SHA-256 `677541B96DC3E78D8FDBD18A59CE27AF8351D3DF16D0B716CD706A88E4D01EC1`:
  the same candidate recorded by the 69-check native gate and 77-check Ahoy regression. The runner
  checks all recorded native source/test/profile hashes before reusing it.

## Observed actions

| Target deck | Seed | Profile on: full casts | Profile on: cycling activations / triggers | Profile off: casts / cycles |
| --- | --- | --- | --- | --- |
| Baloths, 4/5 | 95771 | 3 | 0 / 0 | 0 / 0 |
| Baloths, 4/5 | 95772 | 3 | 0 / 0 | 0 / 0 |
| Bears, 2/2 | 95771 | 0 | 3 / 3 | 0 / 0 |
| Bears, 2/2 | 95772 | 0 | 1 / 1 | 0 / 0 |

All **six full casts** have eight immediately preceding Swamp mana events. All **four cycling
activations** have five preceding Swamp mana events plus the native self-discard. Each cycling
activation also produces the cycling trigger. None of the four profile-off controls uses either mode.
No hands, actions, phases, draws or payment were forced, and no fallback policy was substituted.

## Resolution witnesses

For example, the casting log `target/card-use/cast-on-95772/probe.log` shows eight Swamps producing
mana, the AI casting Decree, then Baloths moving from battlefield to graveyard. The full draw count
is checked by the native fixture; the stack-description text is not used as a draw-count measurement.

The cycling log `target/card-use/cycle-on-95772/probe.log:333` shows five Swamp mana events followed by:

```text
Discard: Ai(1)-Decree Card Use AITEST discards Decree of Pain (60).
Add To Stack: Ai(1)-Decree Card Use AITEST activated Decree of Pain
Add To Stack: Ai(1)-Decree Card Use AITEST triggered Decree of Pain
Resolve Stack: When you cycle Decree of Pain, all creatures get -2/-2 until end of turn. [Cycled: Decree of Pain (60)]
```

Twelve Bears then move from battlefield to graveyard, followed by:

```text
Resolve Stack: Decree of Pain (60) - Ai(1)-Decree Card Use AITEST draws a card.
```

This establishes the native order: self-discard and activation, cycling trigger, creature deaths,
then the cycling draw. These are card-use observations alongside the native correctness gate, not
evidence about matchup strength or original-precon Commander play.

## Observation cutoffs and provenance

All four profile-off runs and the cycling-on 95771 run reach their observation clock. The latter
also prints a timer-stop `java.lang.InterruptedException` while the evaluator is waiting. The first
runner version flagged that shutdown diagnostic as an execution error; review confirmed the expected
cutoff, with three completed cycling sequences already in the log. The original log and initial
summary are retained. The runner now records that specific clock-stop interruption separately and
still rejects unexpected exceptions. No winner or result printed after a cutoff is used here.

`card-use-evidence.json` records all eight runs, exact Java commands, input/log hashes, diagnostics and
line-numbered payment/action witnesses. `Run-CardUseProbe.ps1` reproduces individual observations;
full logs and private jars remain under ignored `target/card-use/`. The original Commander smoke
and its absence of a Decree cast remain separately recorded in `verification-summary.json`.
