# Marshal's Anthem opt-in useful multikicker

Activate with `--ai-profile=marshals-anthem`. Card scripts, preset decks, default AI, existing profiles
and the installed application remain unchanged. All generated runtime outputs stay in the arena.

## Rules and implementation

- Admit only Marshal's Anthem past its untouched in-game `AI:RemoveDeck:All` prohibition.
- Choose the largest natively affordable number of useful kicks, capped by useful creatures in our
  graveyard. Use native target legality, exclude known entry prevention/direct diversion and creatures
  that still have zero toughness after the Anthem bonus.
- Avoid returning an existing conflicting legend or multiple conflicting same-name legends together;
  honor native legend-rule exceptions.
- If no useful return can be paid for, allow an unkicked cast when at least two own creatures benefit
  from the permanent +1/+1 bonus. Otherwise hold.
- Preserve native creature-return ranking and special preferences, targets chosen for the ETB trigger,
  actual multikicker payment, returns, continuous buffs and graveyard-change handling.

`MarshalsAnthemAi.configure` evaluates copies of the full spell cost with successive 1W additions,
using native cost prediction for base 2WW, colored mana, taxes and reductions. The actual ability's
cost is untouched; `GameActionUtil` adds and pays the chosen multikicker costs normally. The selected
amount is reset on each evaluation so a changed graveyard cannot leave stale kicks.

`PermanentAi` uses this configuration only inside its existing multikicker loop for the opt-in profile.
`ChangeZoneAi` retains its normal reanimation ranking but filters this profile's targets for usefulness
and includes the new Anthem's projected bonus in its static-toughness screen. Once the Anthem is on
the battlefield, the bonus is already included by native statics and is not added a second time.

Known return restrictions use native targetability and movement-replacement queries; applicable direct
diversions such as exile are excluded. This is not a general ETB/replacement/combo evaluator. Native
static P/T prediction, obvious legendary conflicts and supported entry restrictions bound the policy;
it does not predict future opponent responses or every returned creature's triggered consequences.
No new evaluation RNG or forced gameplay is introduced.

## Native verification

`Check-MarshalsAnthem.ps1 -Label final`: **135 native checks**, including:

- empty-yard hold, one-versus-two-creature buff threshold and zero kicks despite excess mana;
- one/two/three affordable useful returns and native most-expensive reanimation ranking;
- original spell/ETB resolution, exact native mana payments and actual +1/+1 bonuses;
- white-mana shortages, Thalia's tax and Starfield Mystic's reduction;
- opponent graveyards, Ground Seal, Grafdigger's Cage and Containment Priest;
- duplicate legends, an existing legend, Mirror Gallery, and actual surviving returns;
- Night of Souls' Betrayal versus the future Anthem bonus and still-lethal static reductions;
- changing the graveyard on reevaluation or before trigger targeting;
- default generic multikicker behavior, Everflowing Chalice and profile/deck-building isolation.

The previous profile suites pass **428 regression checks** against the same private candidate. The offline parent/core/game/AI
Maven reactor package passes with zero Checkstyle violations. Generic Maven tests are skipped; explicit
native fixtures provide the behavior gate.

The private engine overlays the new helper, modified Permanent/ChangeZone classes and recent committed
profile classes onto an arena engine snapshot. This avoids stale shared-desktop-jar inputs without
modifying the installed application. Unchanged sources are verification inputs, not production edits.

## Custom-deck probes and reproduction

The native event observer records normal AI casts, paid kick counts, current mana sources, existing
creature stats, original trigger targets and actual graveyard-to-battlefield returns. It never calls
the policy or changes hands, draws, phases, actions, choices or payments.

Repeated-card decks create opportunities for kicked returns and pure unkicked buffs. They are not
Commander-precon or deck-construction validation. Each observation has a 20-second budget; complete
actions before a clock stop provide card-use evidence. Winners and completion are not acceptance gates.

Run from the repository root in PowerShell 7:

```powershell
./arena/marshals-anthem/Check-MarshalsAnthem.ps1 -Label final
./arena/marshals-anthem/Check-Regressions.ps1
foreach ($mode in 'kicked','unkicked') {
    foreach ($profile in 'on','off') {
        foreach ($seed in 96231,96232) {
            ./arena/marshals-anthem/Run-CardUseProbe.ps1 -Mode $mode -ProfileMode $profile -Seed $seed
        }
    }
}
./arena/marshals-anthem/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

See [CARD_USE_EVIDENCE.md](CARD_USE_EVIDENCE.md), [card-use-evidence.json](card-use-evidence.json), and
[verification-summary.json](verification-summary.json). Raw logs, classes and private jars are ignored
under `target/`.
