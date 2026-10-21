# Oreskos Explorer opt-in native casting and search

Activate with `--ai-profile=oreskos-explorer`. Original scripts, preset decks, default AI, existing
profiles and the installed application remain unchanged. Work and runtime outputs stay in the arena.

## Rules and implementation

- Admit Oreskos Explorer past its untouched `AI:RemoveDeck:All` in-game prohibition. The original
  deck-building warning remains active.
- Keep native creature evaluation, timing, legality and payment. Casting does not require search value.
- Keep the original ETB trigger and native resolution-time count of players controlling more lands.
- Take the maximum available legal Plains cards, up to that count. Nonbasic Plains qualify.
- Reuse native land selection and mana fixing. Native search restrictions, revealing and shuffling
  remain in charge. Searched cards go to hand.

Admission alone passed the ordinary searches, but a native test found `BadEtbEffects` when no Plains
remain: `ChangeZoneAi.hiddenTriggerAI` rejects an empty eligible library list, which feeds the creature's
ETB assessment. The profile's `OptionalPlainsSearch` hint adds one narrow exception: a triggered,
non-targeting Library-to-Hand Plains search with no subability or mandatory-selection flag is harmless
even when empty. It changes the willingness assessment only. It does not implement a new search,
change X, select cards, force a cast, override creature timing, or weaken payment/legality checks.

The native creature policy may wait until the second main phase. Fixtures respect that existing timing.

## Verification

- `Check-OreskosExplorer.ps1 -Label final`: **100 native checks**. Real four-player fixtures exercise
  normal creature selection, native 1W payment, original spell and ETB resolution, X=0–3, strict land
  comparisons, changing land counts before resolution, insufficient/no Plains, nonbasic Plains,
  restricted Aven Mindcensor searches, native shuffling, tapped mana, admission isolation, preserved
  deck-building warnings and unchanged default empty-search assessment.
- `Check-Regressions.ps1`: **48 Mirror Entity + 69 Decree of Pain** checks against the same candidate.
- Offline parent/core/game/AI Maven reactor package: success, zero Checkstyle violations. Generic
  Maven tests were skipped; the explicit native suites above supply the behavior gate.
- The broader desktop package attempt compiled its Java code but failed in the optional Launch4j
  Windows resource compiler (`windres.exe`, CreateProcess error 623). No installed executable was
  changed. The tested private candidate is an existing arena desktop engine snapshot overlaid with
  the freshly compiled `ChangeZoneAi.class`; native fixtures, regressions and probes use that same jar.
- See [CARD_USE_EVIDENCE.md](CARD_USE_EVIDENCE.md) for autonomous card-use witnesses and controls.

## Reproduce

Run from the repository root in PowerShell 7:

```powershell
./arena/oreskos-explorer/Check-OreskosExplorer.ps1 -Label final
./arena/oreskos-explorer/Check-Regressions.ps1
./arena/oreskos-explorer/Run-CardUseProbe.ps1 -ProfileMode on -Seed 95901
./arena/oreskos-explorer/Run-CardUseProbe.ps1 -ProfileMode off -Seed 95901
./arena/oreskos-explorer/Run-CardUseProbe.ps1 -ProfileMode on -Seed 95902
./arena/oreskos-explorer/Run-CardUseProbe.ps1 -ProfileMode off -Seed 95902
./arena/oreskos-explorer/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

The native runner snapshots the arena engine and compiles the changed production class plus fixtures.
The probe runner requires the recorded native input hashes to match before copying that private jar.
The probes launch ordinary native matches with a read-only event subscriber. Custom decks deliberately
increase relevant opportunities; there are no forced hands, draws, actions, payments or phase changes.
The 20-second observation clock can stop a run. Only complete actions observed before the stop count.
Game completion, winners, Commander-precon use and matchup strength are not acceptance criteria.

`card-use-evidence.json` contains snapshots and hashes; `verification-summary.json` links the native,
regression and coverage gates. Raw logs, classes and private jars are ignored under `target/`.
