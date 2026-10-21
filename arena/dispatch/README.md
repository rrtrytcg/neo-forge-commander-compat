# Dispatch opt-in metalcraft removal

Activate with `--ai-profile=dispatch`. Original scripts, preset decks, default AI, existing profiles
and the installed application remain unchanged. Runtime outputs stay in the isolated arena.

## Rules and implementation

- Admit Dispatch past its untouched `AI:RemoveDeck:All` in-game prohibition; retain deck-building hints.
- Require native `Player.hasMetalcraft()` when deciding. Artifact creatures and lands count;
  opponents' and phased-out artifacts do not.
- Consider legal opposing creatures across every opponent, including tapped and indestructible ones.
- Prefer actual attackers against us or our permanents, then creatures actually blocking our attackers.
  Within those groups, or otherwise, reuse native creature evaluation.
- Allow removal at ordinary legal instant-speed opportunities, including after attackers/blockers.
  Do not inherit the generic tap-only filters or combat timing gates.
- Keep native target legality and cost prediction, including protection, hexproof, shroud, ward and
  spell taxes. Hold if the chosen target cannot be afforded; no alternative-target cost search is added.
- Resolve the original Tap ability and conditional exile subability. If metalcraft is lost in response,
  the spell only taps. If its target leaves the battlefield, normal legality/fizzle handling applies.

`DispatchAi` contains the bounded decision helper. A three-line early branch in `TapAi.checkApiLogic`
uses it only when the profile supplies `DispatchRemoval`. No default helper or script is weakened.
The policy does not predict opponent responses, perform a general removal search, or add policy RNG.

## Native verification

`Check-Dispatch.ps1 -Label final` passes **61 native checks** in four-player fixtures:

- 0–4 artifacts, artifact creatures/lands, opponent artifacts, phasing and stale-target clearing;
- all opponents, own-creature exclusion, native evaluation and tapped targets;
- actual attackers against us and actual blockers of our attackers, plus normal controller selection
  after both declaration steps;
- white mana, tapped sources, Thalia's tax, hexproof, shroud and protection from white;
- native W payment and original resolution against tapped Serra Angel and Darksteel Colossus;
- losing metalcraft before resolution and a target leaving before resolution;
- native ward cost prediction and successful payment; original deck-building hints and profile isolation.

`Check-Regressions.ps1` reruns the existing **48 Mirror Entity, 69 Decree of Pain and 100 Oreskos Explorer**
checks against the same private candidate. The candidate overlays freshly compiled `DispatchAi.class`,
`TapAi.class` and the already committed `ChangeZoneAi.class` onto an arena desktop engine snapshot,
so the last committed search fix is present even when the shared desktop jar lags behind source.
The unchanged search source is an input, not a new production edit.

The offline parent/core/game/AI Maven reactor package passes with zero Checkstyle violations. Generic
Maven tests are skipped; the explicit native suites supply the behavior gate. No Windows executable
packaging or installed-app update is required.

## Autonomous card-use probes

See [CARD_USE_EVIDENCE.md](CARD_USE_EVIDENCE.md), [card-use-evidence.json](card-use-evidence.json) and
[verification-summary.json](verification-summary.json). The event subscriber is read-only. The game
engine and normal AI choose cards, actions, targets, mana, phases and combat assignments.

Custom decks deliberately increase metalcraft and removal opportunities. They are not deck-construction
or Commander-precon validation. A 20-second observation can stop on its clock; only complete native
actions observed before that stop count. Winners and completion are not acceptance criteria.

Run from the repository root in PowerShell 7:

```powershell
./arena/dispatch/Check-Dispatch.ps1 -Label final
./arena/dispatch/Check-Regressions.ps1
./arena/dispatch/Run-CardUseProbe.ps1 -ProfileMode on -Seed 96011
./arena/dispatch/Run-CardUseProbe.ps1 -ProfileMode off -Seed 96011
./arena/dispatch/Run-CardUseProbe.ps1 -ProfileMode on -Seed 96012
./arena/dispatch/Run-CardUseProbe.ps1 -ProfileMode off -Seed 96012
./arena/dispatch/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

The probe runner checks native-tested input hashes before copying the private jar. The evidence checker
revalidates input/log hashes and pairs native casts, payments and actual target exiles. Raw logs,
classes and private jars are ignored under `target/`.
