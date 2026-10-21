# Nyx Weaver opt-in on NeoForge 1.0.27

Profile: `nyx-weaver`. New decisions only; original scripts, default AI and the installed app
stay unchanged. No utility-land or other concurrent policy source is edited.

## Native defect and rule

The original `AI:RemoveDeck:All` excludes the spell/activation from native selection.
The three-check unchanged-engine baseline confirms that filter and that forced generic
ChangeZone AI trades a healthy Weaver for a cheap Grizzly Bears. Generic recovery
already chooses graveyard cards and handles exile/payment/movement; it has no Weaver
body-versus-recovery exchange rule. Its mandatory upkeep mill already works natively.

- Cast normally from hand in our second main with an empty stack, at least eight cards
  in library, and no own Weaver already in play. Retain native creature evaluation.
- Ordinary recovery in our second main, empty stack: an eligible own nonland with mana
  value four or more, legally castable and jointly affordable after 1BG and source exile.
  This does not force the later spell play. Active-profile play exclusions remain in force.
- Cash in a publicly doomed Weaver using the existing no-rescue threat predictor. Preserve
  favorable trades and avoid removing a blocker into lethal combat. Exile is not sacrifice
  or death; native source exile is retained.
- With four or fewer cards in library, cash in during own second main or the last
  opponent's end step before our next upkeep, with a legal usable target and native payment.
  Emergency targets can include lands. This only stops future Weaver mills: an already
  triggered mill still resolves. Missing mana/targets cannot be invented into a rescue.
- Retain native creature-first, then nonland recovery valuation. Use stable card-ID ties
  and native land evaluation instead of the generic random land-only fallback.
- A hidden-information-pruned private native game pays mana, checks the native exile cost,
  exiles the source and resolves the original return effect. For ordinary recovery it then
  checks native casting restrictions/timing and affordability on the recovered card.
- Preserve a conservative five-life friendly reserve.
- Hold uncertain exile/graveyard/library/trigger consequences and collateral losses;
  attached sources, nonstandard templates and relevant public engines defer. Fresh source
  and target incarnations, exact target, branch, timing, profile, affordability and safety
  are rechecked before live costs. Native response resolution and target fizzles stay native.
- Do not change MillAi or the mandatory trigger. No library-order or opposing hidden-card
  inspection and no policy RNG. Existing native shuffles/randomness stay authoritative.

Hooks: scoped helper NyxWeaverAi, casting veto in PermanentCreatureAi, opt-in activation
entry/phase handling in ChangeZoneAi, and one preflight in ComputerUtil. All utility-land
sources and shared native gate tooling remain untouched.

## Candidate and evidence

The other coder committed utility-land work as `2d7e988f` during implementation. The
final candidate builds on its sealed 45-suite / 4,898-check engine, already containing
Terrain, Abundance and Estrid. Its 51 utility-land regressions run unchanged; no utility
source is in Nyx's compiled production list. Earlier development output based on Terrain
is archived separately under ignored `target/pre-utility-*` and is not final evidence.

The 224 new native checks cover casting reserve/duplicates, ordinary recovery and exact
joint colored/taxed funding, later native casting, legal emergency lands, low-library
windows, doomed-source removal/combat, unfavorable blocker removal, native source exile,
opposing target-exile fizzles without refunds, original mandatory upkeep mill and its
resolution after source exile, stale plans, profile boundaries and RNG/resource integrity.
The fixture refreshes active triggers after setting the upkeep phase; no production
trigger behavior was changed to obtain that test.

The full gate passes **5,122 native checks across 46 suites** (224 new plus 4,898 prior),
including all 51 utility-land regressions. The unchanged-engine baseline passes three checks.

The first five Progenitus-commander observations had zero Nyx use because its native
zone-change replacement correctly caused a conservative hold. They remain separate in
`initial-observation.json` and ignored `target/progenitus-no-use` with deck snapshots.
Final exposure changes only the commander to vanilla off-color Isamaru; policy and
clock are unchanged. A task-owned stop flag allows a batch to end between observations.

All **20 deadline checks** and **1,148 card-use checks** pass on eight paired observations.

| Scenario / seed | Nyx casts | Mill triggers | Ordinary recovery | Doomed recovery | Low-library recovery |
| --- | ---: | ---: | ---: | ---: | ---: |
| Value / 118011 | 1 | 14 | 0 | 0 | 0 |
| Value / 118012 | 3 | 11 | 3 | 0 | 0 |
| Rescue / 118021 | 11 | 6 | 0 | 8 | 0 |
| Library / 118031 | 1 | 11 | 0 | 0 | 1 |

Totals: 16 casts, 42 native upkeep mills / 84 milled cards, 12 native source-exile
recoveries. Eight returned cards are later cast, three in the same turn. Each Cut Down
response is followed by that native removal's fizzle; matching uses the stack ability ID
because native legality removes an invalid target before the resolution event. All four
default controls show zero Nyx use. Four observations hit the 30-second clock and four
return earlier; no pending observed actions, late events or unexpected diagnostics.

Coverage: **184 decks, 52 clear, 67 mechanically unlocked = 57 verified + 10 unlocked-only**.
Enduring Enchantments is newly clear. This includes the other coder's previously committed
four-land coverage gains without changing their registry rows or evidence.

Final native totals and measured use counts are sealed in `verification-summary.json`
and `card-use-evidence.json` after the full gates and paired observations pass.

Exposure decks deliberately repeat cards and use off-color Isamaru, Hound of Konda as commander.
They are invalid custom Commander decks; deck validity and game strength are not tested.
Value decks expose native milling followed by funded recovery; rescue opponents use
Cut Down, which can kill the original 2/3; a short low-library deck contains only cheap
recovery targets so the low-library branch has its own exposure. Normal AI selects all
actions with the existing 30-second clock: no forced plays, target choices or phase edits.

Value seed 118011 is a colored-payment hold: it develops only two Forests, so the
activation and a double-green recovery spell cannot both be funded. It still casts
and mills normally; seed 118012 supplies the positive ordinary-recovery witness.
Both paired observations are retained, not forced into uniform activation counts.

The read-only observer records native casts, activations, upkeep triggers, source exile,
public graveyard movements and results, plus own hand and public stack/library/life counts.
It never calls policy helpers or reads hidden library order. The independent checker
pairs each action with a resolution and verifies original colored costs, exact source
exile before activation, target-to-hand movement, native mill counts and no spurious draws.
For Cut Down responses it also checks the native removal's later fizzle on the exiled
source. Later native casts of returned cards are counted separately from recovery.
Clock stops and invalid decks establish card-use evidence only; no completion, winner
or strength claim. Complex enchantress/graveyard engines remain conservatively deferred.

Only the installed group of the original compatibility baseline is checked (21,729
hashes). Retired lab folders stay deleted. Verified and unlocked-only coverage stay
separate, with no Nyx promotion before both native and autonomous use evidence pass.

## Reproduce

```powershell
./arena/nyx-weaver/Check-NeoForge-1.0.27.ps1 -Baseline
./arena/nyx-weaver/Check-NeoForge-1.0.27.ps1
./arena/nyx-weaver/Check-Timeouts.ps1
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario value -Seed 118011 -ProfileMode on
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario value -Seed 118011 -ProfileMode off
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario value -Seed 118012 -ProfileMode on
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario value -Seed 118012 -ProfileMode off
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario rescue -Seed 118021 -ProfileMode on
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario rescue -Seed 118021 -ProfileMode off
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario library -Seed 118031 -ProfileMode on
./arena/nyx-weaver/Run-CardUseProbe.ps1 -Scenario library -Seed 118031 -ProfileMode off
python arena/nyx-weaver/check-card-use.py
./arena/Audit-Deck.ps1 -All -Coverage
python arena/nyx-weaver/refresh.py
```

A task-owned exclusive runtime lock prevents overlapping gate/probe calls. `-Focused`
is the development lane, `-RunOnly` reuses a sealed build. Final provenance is protected;
all raw binaries/resources/logs stay under ignored `target/`.
