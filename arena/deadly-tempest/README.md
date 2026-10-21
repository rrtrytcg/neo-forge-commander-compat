# Deadly Tempest opt-in policy on NeoForge 1.0.27

Profile: `deadly-tempest`. This extends the private Hull Breach / NeoForge 1.0.27
candidate from `69816488`. Default AI, original scripts and other profile admissions
remain unchanged. Nothing is written to the installed app.

## Rules and boundaries

- Normal hand casting only, in our second main with an empty stack.
- Require at least two more opposing creatures actually destroyed than friendly
  creatures, and strictly greater native opposing destroyed-creature value. Allies
  count as friendly. No emergency or opponent-lethal exception bypasses these rules.
- Forecast the original `DestroyAll -> RepeatEach -> LoseLife -> Cleanup` effects
  in a hidden-information-pruned native game copy. Native affordability and actual
  copied mana payment include colors, taxes and any life spent making mana.
  Non-mana additional spell costs defer. All real costs and resolution stay native.
- Native destruction, shield counters, indestructibility, token death and
  last-known controller accounting decide casualties. Compare the original native
  life-loss chain with those actual casualties for every player. Us and every ally
  must remain alive with at least five life after costs and the spell.
- Reuse Hull Breach's public destruction/continuous-effect guard. Its existing
  profile behavior is unchanged; only this new caller permits the exact native
  shield-counter replacement templates. No general replacement-effect support
  is added. Do not reuse Decree of Pain's no-regeneration assumption.
- Defer active or available regeneration, death/damage/life-loss engines (including
  nested delayed life loss such as Wound Reflection), movement/life replacements,
  life locks, dying continuous creature sources, attached Auras, opaque or variable
  engines, cloned/face-down positions and uncertain collateral deaths. A fixed
  surviving noncreature anthem and an ordinary native mana tax are supported.
- Regeneration supplied in response after the actual cast remains legal and native;
  regenerated creatures do not contribute to the spell's remembered destruction.
- Weak game-local source plans bind the exact original chain and source incarnation.
  Recheck the complete forecast, timing, restrictions, payment and profile before
  any real costs. Pending stale/profile-removed/malformed plans cancel. No policy
  RNG, hidden hand inspection, live mana reservations or live forecast mutation.

This is a bounded compatibility policy, not a general board-wipe strategist.
It can hold in complicated precon positions. It deliberately skips profitable
lines that cannot satisfy these simple safety rules.

## Validation

Final measured native/probe results are recorded in `verification-summary.json`
and `card-use-evidence.json`. Native fixtures use the original card script and
normal controller selection plus `ComputerUtil.handlePlayingSpellAbility`.
They independently assert counts, life totals, actual graveyard movement, shield
survival, regeneration responses, tokens, last-known control, taxes, allied
reserves, unknown-engine holds, stale cancellation, RNG/resource integrity and
unchanged default/unrelated admission. All previous Commander suites run on the
same compiled candidate, including the unchanged Hull Breach fixtures.

The final private candidate passes **4,445 native checks across 41 suites**
(147 new plus 4,298 prior), **20 deadline checks** and **874 card-use checks**.
Its SHA256 is `3AEEE11944B922C4150344FE80671C9CB333F7F175EB9C92307CAD4F2C3AE2F8`.

| Exposure / seed | Opt-in casts | Default casts | Opposing / friendly destroyed | Total life lost |
| --- | ---: | ---: | ---: | ---: |
| Ordinary / 113011 | 8 | 0 | 35 / 6 | 41 |
| Ordinary / 113012 | 12 | 0 | 57 / 5 | 62 |
| Protected / 113021 | 5 | 0 | 23 / 3 | 26 |

The 25 paid/resolved casts produce exactly 129 life lost from 115 opposing and
14 friendly creatures destroyed. The protected run has eight native shield-counter
saves and 30 indestructible survival observations (repeated observations may involve
the same permanent). No saved creature contributes to destruction life loss.
All three default controls show zero Tempest use. Ordinary Walking Corpse casting
remains active in all six runs. Five runs reach the unchanged 30-second clock;
ordinary-on 113011 returns earlier. No pending observed actions, fizzles, late
observer events, unexpected diagnostics or native payment/stack failures remain.

Four occurrences unlock: Call the Spirits, Enhanced Evolution, Witherbloom
Witchcraft and Chaos Incarnate retain 3/2/1/3 blockers respectively. Fleet coverage
is now 50 verified and 10 unlocked-only cards, with 46 clear decks unchanged.

The custom decks intentionally contain repeated cards and an off-color Yargle and Multani
commander. This is the established exposure technique, not a deck-validity test.
The observer only reads native public cast/resolution events, mana, boards,
graveyards and life totals. It never invokes the policy or supplies gameplay
choices. Paired default controls use the same decks and seeds.
Bounded observations establish autonomous card use only, with no claim of normal
game completion, trustworthy winners, tactical superiority or win-rate strength.

## Reproduce

```powershell
./arena/deadly-tempest/Check-NeoForge-1.0.27.ps1
./arena/deadly-tempest/Check-Timeouts.ps1
./arena/deadly-tempest/Run-CardUseProbe.ps1 -Scenario ordinary -Seed 113011 -ProfileMode on
./arena/deadly-tempest/Run-CardUseProbe.ps1 -Scenario ordinary -Seed 113011 -ProfileMode off
./arena/deadly-tempest/Run-CardUseProbe.ps1 -Scenario ordinary -Seed 113012 -ProfileMode on
./arena/deadly-tempest/Run-CardUseProbe.ps1 -Scenario ordinary -Seed 113012 -ProfileMode off
./arena/deadly-tempest/Run-CardUseProbe.ps1 -Scenario protected -Seed 113021 -ProfileMode on
./arena/deadly-tempest/Run-CardUseProbe.ps1 -Scenario protected -Seed 113021 -ProfileMode off
python arena/deadly-tempest/check-card-use.py
./arena/Audit-Deck.ps1 -All -Coverage
python arena/deadly-tempest/refresh.py
```

The runner preserves an existing final gate; use a new scratch output for a new
candidate. `-Focused` runs only the new fixtures during development; `-RunOnly`
uses the sealed build without recompiling. The ComputerUtil patch is based on
frozen source `899567ea` and applies to the reconciled private 1.0.27 source, so
reproduction does not depend on an uncommitted `git diff HEAD` after release.
Build products, raw logs and private resources stay under ignored `target/`.
