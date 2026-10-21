# Handoff: Commander card unlocks (2026-10-21)

## Where things stand
- **Engine is now NeoForge 1.0.28** (installed 2026-10-19). Re-baselined gate: `arena/version-compat-1.0.28/target/final-1.0.28` (5,720 checks, 56 suites, exact 1.0.27 counts; includes choice-wipes at its sealed `final-1.0.27` state, i.e. before the r2 `RepeatEachAi` edit). New gates chain from it using `arena/version-compat-1.0.28/Check-Gate.ps1`; latest sealed card gate is Past in Flames at 5,943 checks / 69 suites. See `arena/version-compat-1.0.28/README.md`.
- Latest lab: `past-in-flames` / Past in Flames (local only, never pushed). Tag `commander-compat-past-in-flames-verified-2026-10-21`.
- Coverage: **92 / 184 decks clear**; 111 cards mechanically unlocked, 101 strategically verified, 262 distinct blockers / 347 occurrences (`arena/coverage/COVERAGE.md`).
- Newest sealed gate: `arena/past-in-flames/target/final-past-in-flames-r3`. It passes 5,943 checks across 69 suites: all 5,925 prior checks at exact counts plus 18 new.
  - The installed-file guard checked 21,729 active 1.0.28 files with zero changes.
  - Before gating, check for a newer head: `Get-ChildItem arena/*/target/final-* | sort LastWriteTime`.
  - A gate counts as sealed only once it has `provenance.json`.
- This checkout is shared: check `git status` and commit only your own paths.

## Drawn from Dreams lab
- Approved opt-in rule: bypass only this card's `AI:RemoveDeck:All` marker. Preserve stock `DigAi` cast timing and two-card ranking, native `{2}{U}{U}` payment, and the native look-seven/keep-two resolution.
- Baseline passes 4 checks showing native DigAi willingness while normal selection is blocked. Focused suite passes 12 checks for profile isolation, normal selection, native rankings, payment and actual hand/library/graveyard movement. Full NeoForge 1.0.28 gate: 5,895 checks / 66 suites (12 new plus all 5,883 prior at exact counts); installed-file guard checked 21,729 hashes with zero changes.
- Paired two-player exposures, seeds 140011 / 140012, 120-second clocks: off 0 casts/resolutions on both; on 5/5 and 9/9. All four completed without exceptions. Native fixtures establish card choice; exposure logs establish card use only, with no winner or strength claim. Clears **NCC - Maestros Massacre**. See `arena/drawn-from-dreams/README.md` and `verification-summary.json`.

## Odric, Master Tactician lab
- The `odric` profile bypasses only Odric's `AI:RemoveDeck:All` filter. Stock creature-casting heuristics and the native attack trigger/blocker-control effect remain unchanged. The native suite passes 10 checks, including profile isolation, the exact threshold (Odric plus three other attackers), and no trigger at two other attackers.
- Full NeoForge 1.0.28 gate: 5,925 checks / 68 suites (10 new + 5,915 prior exact); installed guard checked 21,729 hashes with zero changes.
- Paired 120-second two-player games, seeds 141021/141022: zero casts off; 1/2 casts on, with 3/0 native trigger resolutions; zero exceptions. Seed 141022 had casts without an attack trigger. Card-use evidence only, not strength evidence. Clears **MIC - Coven Counters**. See `arena/odric/README.md` and `verification-summary.json`.

## Past in Flames lab
- The `past-in-flames` profile admits only this card and maps its native PumpAll spell to a narrow graveyard-value rule: our main phase, empty stack, at least two own graveyard instants/sorceries, and exact combined colored mana affordability for one follow-up. Native payment, flashback spell selection, legality, and resolution remain in force.
- Full NeoForge 1.0.28 gate: 5,943 checks / 69 suites (18 new + 5,925 prior exact); installed guard checked 21,729 hashes with zero changes. Native fixtures verify threshold, phase/stack holds, opt-in/profile isolation, normal selection, and a real native Past in Flames then Opt flashback cast/exile.
- Paired 120-second two-player games, seeds 142021/142022: zero Past in Flames casts/resolutions off; 6/6 and 2/2 on; logs show 2 and 1 granted-flashback exile markers; zero exceptions. Card-use evidence only, not strength evidence. Clears **40K - The Ruinous Powers**. See `arena/past-in-flames/README.md` and `verification-summary.json`.

## Done this session (all committed)
| Lab | Cards | Commit |
| --- | --- | --- |
| adaptive-enchantment | Estrid, the Masked | 53b2441a |
| utility-lands | Labyrinth of Skophos, HELIOS One, Thespian's Stage, Dakmor Salvage | 2d7e988f |
| easy-cards | Syr Elenora, Marsh Flitter, Recurring Insight, Pact of the Serpent, Suffer the Past | da113a3b |
| easy-cards-2 | Autumn Willow, Edge of Autumn, Knollspine Dragon, Bosh Iron Golem, Ashling the Pilgrim, Benevolent Offering, Peel from Reality | 263c7293 |
| easy-cards-3 | The Prismatic Piper, Cave of Temptation, Throes of Chaos, Masterful Replication, Call for Aid, Marauding Raptor, God-Eternal Bontu, Esix Fractal Bloom | cb8e97ce |
| jolrael | Jolrael, Mwonvuli Recluse | c4aa6f63 |
| moonlight-bargain | Moonlight Bargain | a56d2d1e |
| doomsday-confluence | Doomsday Confluence | see latest tag |
| immoral-bargain | Immoral Bargain | see latest tag |
| carpet-of-flowers | Carpet of Flowers | see latest tag |
| ghoulcaller-gisa | Ghoulcaller Gisa | see latest tag |
| choice-wipes | Harsh Mercy | see latest tag |
| confiscation-coup | Confiscation Coup | see latest tag |
| daretti | Daretti, Scrap Savant | see latest tag |
| mystic-confluence | Mystic Confluence | see latest tag |
| drawn-from-dreams | Drawn from Dreams | see latest tag |
| gideon-jura | Gideon Jura | see latest tag |
| selective-obliteration | Selective Obliteration | see latest tag |
| ride-the-avalanche | Ride the Avalanche | see latest tag |
| molten-slagheap | Molten Slagheap | see latest tag |
| sylvan-offering | Sylvan Offering | see latest tag |
| saltcrusted-steppe | Saltcrusted Steppe | see latest tag |
| phyrexias-core | Phyrexia's Core | see latest tag |
| magus-of-the-wheel | Magus of the Wheel | see latest tag |
| divine-reckoning | Divine Reckoning | see latest tag |
| odric | Odric, Master Tactician | see latest tag |
| past-in-flames | Past in Flames | see latest tag |

## Divine Reckoning lab
- Approved opt-in rule: in our second main phase with an empty stack, forecast each AI-controlled player's native best-creature choice from the public battlefield and cast only when opposing creature loss value exceeds friendly loss value. Hold for human-controlled seats or when the losses are equal/favor our team. Preserve native payment, choices, destruction and flashback.
- Baseline: 2 checks show the native `ChooseCardAi` API decision accepts an equal-loss board without evaluating the consequences, while `AI:RemoveDeck` blocks normal selection. Focused suite: 15 checks covering normal selection, actual native payment/choice/destroy resolution, equal and friendly-loss holds, timing, affordability and profile isolation. Full 1.0.28 gate: 5,835 checks / 63 suites (15 new + 5,820 prior exact); installed-file guard 21,729 hashes, zero changed.
- Paired custom-deck exposures, seeds 137011 / 137012, 120-second clocks: off 0 casts/resolutions on both; on 4/4 and 6/6; all games exited successfully with zero exceptions. Fixture evidence verifies actual per-player creature choices and destruction; exposures are card-use evidence only. Clears **C17 - Feline Ferocity**. See `arena/divine-reckoning/README.md` and `verification-summary.json`.

## Magus of the Wheel lab
- Approved opt-in rule: activate only in our second main phase with an empty stack, our hand at most two, library at least seven and every opponent's hand at least four. Preserve the printed `{1}{R}`, tap and sacrifice costs and native wheel effect.
- Native `DiscardAi` broadly approves the wheel but inherited cost evaluation rejects the favorable self-sacrifice. Baseline: 2 checks confirm that refusal and normal-selection block. Focused gate: 22 checks including real payment and native draw-seven resolution. Cumulative NeoForge 1.0.28 gate: 5,820 / 62 suites (22 new + 5,798 prior exact); installed-file guard 21,729 hashes, zero changed.
- Paired custom-deck exposures, seeds 136051 / 136052 / 136053, 120-second clocks: off zero casts/activations on all seeds; on activations 6 / 0 / 11 (with 13 / 6 / 28 casts). All six games exited successfully without exceptions or clock caps. Seed 136052 ended without an eligible activation. Card-use evidence only; no winner or strength claim. Clears **NCC - Cabaretti Cacophony**. See `arena/magus-of-the-wheel/README.md` and `verification-summary.json`.

## Phyrexia's Core lab
- Approved opt-in rule: activate only at five or less life or when native combat evaluation says life is in danger. Preserve native timing, `{1}` payment, artifact sacrifice selection and effect resolution.
- Baseline: 2 checks reproduce native refusal at five life and normal deck-selection blocking. Focused fixture: 17 checks covering low life, native combat danger, paid activation and resolution, missing mana/artifact, tapped Core and profile isolation. Cumulative NeoForge 1.0.28 gate: 5,798 / 61 suites (17 new + 5,781 prior exact); installed-file guard 21,729 hashes, zero changed.
- Paired two-player exposures, seeds 135011 / 135012, 120-second clocks: off zero activations on both seeds; on 5 and 9. All four games exited successfully without exceptions or clock caps. Card-use evidence only; no winner or strength claim. Clears no deck by itself and removes two blocked occurrences. See `arena/phyrexias-core/README.md` and `verification-summary.json`.

## Saltcrusted Steppe lab
- Approved opt-in rule: activate storage mana only when it enables a currently castable noninstant, spending only the counters needed after other lands and the `{1}` activation cost; keep support confined to the `molten-slagheap` profile entries for Molten Slagheap and Saltcrusted Steppe.
- Native Mana AI declines the storage ability and the mana-source filter omits it because activation costs `{1}`. `ManaAi` now parses the profile-mapped `{G}/{W}` combo as X mana in the listed colors; source selection, color assignment, payment, and resolution remain native. Baseline 2 checks; focused fixture 14 checks including exact four-counter Serra Angel payment and holds for insufficient funding / unnecessary storage / instant-only. Cumulative NeoForge 1.0.28 gate: 5,781 / 60 suites (14 new + 5,767 prior exact); installed-file guard 21,729 hashes, zero changes.
- Paired two-player exposures, seeds 134011 / 134012, 120-second clocks: off zero Saltcrusted storage activations; on 5 and 2, with all four games exiting cleanly and zero exceptions or clock caps. The off games still cast the artifact through ordinary mana. Card-use evidence only, no strength claim; reduces two blocked occurrences and clears no deck by itself. See `arena/saltcrusted-steppe/README.md` and `verification-summary.json`.

## Sylvan Offering lab
- Approved opt-in rule: cast only in our second main phase with an empty stack, positive X capped at two, and an asymmetric anthem or token-multiplier advantage over the selected opponent; choose the opponent with the fewest creatures at each native choice. Preserve native payment, legality and token resolution.
- Baseline reproduces the permissive native choice and first-opponent selection. Focused fixture passes 22 checks, including both asymmetric advantage sources, equal-opponent hold, least-creature choice, normal selection, X cap, payment, and token-multiplier resolution. Cumulative NeoForge 1.0.28 gate: 5,767 / 59 suites (22 new + 5,745 prior exact); installed-file guard 21,729 hashes, zero changes.
- Paired three-player custom-deck exposures, seeds 133011 / 133012, 120-second clocks: off zero casts on both; on 3 casts on each. All four exited cleanly without exceptions or clock caps. Card-use evidence only, no winner or strength claim. Clears no deck by itself; reduces three remaining blocked occurrences. See `arena/sylvan-offering/README.md` and `verification-summary.json`.

## Molten Slagheap lab
- Approved opt-in rule: use stored mana only for a currently castable noninstant that is not already covered by ordinary mana sources; pay the shortfall after tapping Molten Slagheap and paying its `{1}` activation cost. Preserve native spell choice and payment.
- The native mana-source list excludes mana abilities with a mana cost. A profile-only exception admits cards mapped to `ManaRitualBattery`; the existing ritual evaluator sets X after accounting for floating mana. Focused fixture: 12 checks including source admission isolation, ordinary-mana hold, exact counter shortfall and native payment. Cumulative NeoForge 1.0.28 gate: 5,745 / 58 suites (12 new + 5,733 prior exact); 21,729 installed hashes, zero changes.
- Paired two-player exposures, seeds 132011 / 132012, 120 seconds: off 0 Solemn Simulacrum casts / 0 storage mana activations; on 4 / 4 and 12 / 1. All four finished without exceptions or clock caps. Clears no deck by itself; reduces one remaining blocked occurrence in 40K - The Ruinous Powers. Card-use evidence only, no strength claim. See `arena/molten-slagheap/README.md` and `verification-summary.json`.

## Winter, Cynical Opportunist lab
- Approved opt-in rule: at the delirium end-step trigger, return only the highest-valued legal permanent, choosing the minimal nonpermanent graveyard support needed to reach the script's four-card-type threshold; preserve native attacks and native exile/return/finality effect.
- Baseline reproduced the generic wrong exile selection. The focused fixture passes 13 checks, including best target, minimal support, threshold stop, duplicate-legend protection, native chain/finality, and profile parity. Cumulative NeoForge 1.0.28 gate: 5,733 checks / 57 suites (13 new plus 5,720 prior at exact counts); installed-file guard 21,729 hashes, zero changes.
- Paired two-player exposures, seeds 131011 / 131012, 120-second clocks: off zero casts/triggers; on one cast per seed, with 12 attacks / 5 delirium triggers and 13 attacks / 4 delirium triggers. All games exited cleanly. Native fixture verifies the hidden-zone result; logs establish card use, not choice accuracy or strength. Clears **DSC - Death Toll**. See `arena/winter-cynical-opportunist/README.md` and `verification-summary.json`.
## Ride the Avalanche lab
- Approved opt-in rule: cast only on our own main phase with an empty stack, a counter-receiving creature we control, and enough shared mana for Ride plus another nonland, non-X, mana-only spell in hand. Native flash permission, trigger targeting, payment, and resolution remain unchanged.
- Baseline: two checks reproduce generic EffectAi refusal and normal-selection blocking. Focused fixtures also verify a shroud hold by building Ride's scripted trigger and checking actual target legality. Cumulative NeoForge 1.0.28 gate: 5,753 checks / 58 suites (17 new plus 5,736 prior at exact counts); installed-file guard 21,729 hashes / zero changes.
- Exposure seeds 130011 / 130012, two-player custom decks, 120-second clocks, off/on: off zero Ride casts/resolutions/triggers; on 4 casts/resolutions and 4 counter-trigger resolutions / 3 casts/resolutions and 2 counter-trigger resolutions. One Ride on seed 130012 was not followed by another spell that turn. All four games exited cleanly with zero exceptions or clock limits. Card-use evidence only; no winner or strength claim. Clears **AFC - Aura of Courage**. See `arena/ride-the-avalanche/README.md` and `verification-summary.json`.

## Gideon Jura lab
- Approved opt-in rule: cast normally; activate only native `-2`, on our main phase with empty stack, at least two loyalty, and a legal tapped opposing creature. Keep native Destroy targeting, loyalty payment, and resolution. Decline `+2` (attacker restriction) and `0` (animation) under this profile.
- Baseline proves native Destroy AI can target a tapped creature but RemoveDeck blocks normal selection. Full NeoForge 1.0.28 gate: 5,735 checks / 57 suites (15 new plus 5,720 prior at exact counts); installed-file guard 21,729 hashes / zero changes.
- Exposure seeds 129011 / 129012, two-player decks, 120-second clocks, off/on: off zero casts/activations; on 9/9 and 15/15, each with matching spell and destroy resolutions. Logs show tapped Hill Giants and Craw Wurms destroyed; zero exceptions or clock limits. Card-use evidence only; no winner or strength claim.
- Clears **SCD - First Flight**. See `arena/gideon-jura/README.md` and `verification-summary.json`.
- Related `MustAttack` definitions reviewed: Rowan Kenrith, Instigator, Incite War, Imaginary Threats, Taunt, and Urza, Academy Headmaster share remembered-player restrictions. This is a possible related AI limitation, not evidence that each card is blocked in a precon.

## Selective Obliteration lab
- Approved opt-in rule: each AI player preserves the mono-color group with the greatest evaluated public permanent value (WUBRG tie order); multicolored permanents are forecast exiled and colorless permanents survive. Cast only when forecast opposing permanent value lost exceeds friendly loss. Native sorcery payment and exile resolution remain intact.
- Baseline reproduces both issues: `RepeatEachAi` accepts without weighing the sweep, and `MostProminentComputerControls` applies the caster's green color choice to the opponent's red-majority board. Full NeoForge 1.0.28 gate: 5,736 checks / 57 suites (16 new plus 5,720 prior at exact counts); installed-file guard 21,729 hashes / zero changes.
- Paired two-player exposures, seeds 129011 / 129012, 120-second clocks, off/on: off zero resolutions on both; on 1 and 2 resolutions respectively; all four games had zero exceptions and no clock limits. Card-use evidence only; no winner or strength claim. Clears **M3C - Eldrazi Incursion**. See `arena/selective-obliteration/README.md` and `verification-summary.json`.

## Confiscation Coup lab
- Approved opt-in rule: retain native sorcery timing and payment; restrict native ControlGain ranking to opposing artifact/creature targets with mana value at most the four energy Coup provides; pay only the exact target mana-value energy when it remains legal and opponent-controlled. Hold when no affordable target exists.
- Baseline: four checks reproduce generic energy-counter rejection, the native optional-payment refusal for a sorcery host outside the battlefield, and the normal-selection block. Focused gate: 13 checks. Full gate: 5,734 checks across 57 suites (13 new plus all 5,721 prior checks exact). Fixtures verify native ranking, affordable-target selection, real control change and energy payment, high-mana-value holds, timing, stale-target refusal and profile isolation.
- Exposure seeds 128011 / 128012, two-player custom decks, 120 seconds off/on: off 0/0 casts/resolutions on both seeds; on 6/6 each, zero exceptions. Seed 128011 resolves Craw Wurms while Coup captures Hill Giants only. Card-use/target evidence only; no winner or strength claim. Clears **M3C - Creative Energy**. See `arena/confiscation-coup/README.md` and `verification-summary.json`.
- Same helper ruling checked: Emberwilde Djinn is the only other card found with `GainControl` and `UnlessSwitched`; its source remains on the battlefield, so it does not share Coup's source-zone failure.

## Harsh Mercy lab
- Approved opt-in rule: preserve native sorcery timing/payment; forecast each player's native prominent creature-type choice from the public battlefield; cast only when no friendly creatures are predicted to die and at least one opposing creature is, holding whenever a choice is uncertain. Native choices and destroy resolution stay unchanged.
- Baseline: four checks across Harsh Mercy and Selective Obliteration confirm the generic `RepeatEachAi` root accepts table-wide wipes without evaluating consequences, while both cards remain blocked by `RemoveDeck`. Focused gate: 15 checks. Full gate: 5,721 checks across 56 suites (15 new plus all 5,706 prior checks at exact counts). Fixtures verify safe/unsafe forecasts, native choices, native payment/resolution and profile isolation; installed-file guard: 21,729 hashes, zero changes.
- Exposure seeds 127011 / 127012, two-player custom decks, 120 seconds off/on: off 0 casts/resolutions on both seeds; on 1/1 on both seeds; zero exceptions. Card-use evidence only; no winner or strength claim. Clears **CMM - Sliver Swarm**. See `arena/choice-wipes/README.md` and `verification-summary.json`.
- Selective Obliteration shares the generic root defect but stays blocked: the native color chooser uses the casting AI's prominent color for each player. Its separate chooser correction needs its own baseline and approved rule.

## Ghoulcaller Gisa lab
- Approved opt-in rule: cast with native creature AI; activate only in our second main with an empty stack after native cost payability succeeds. Keep native `{B}`, tap and another-creature payment, fodder choice and power-based Zombie resolution.
- Baseline: one check reproduces the normal-selection block. Focused gate: 19 checks. Full gate: 5,706 checks across 55 suites (19 new plus all 5,687 prior checks at exact counts). Fixtures prove deterministic AI selection, native cast, payment, sacrifice and Zombie count, no-fodder/no-mana holds, timing, stack and tapped-source holds, and profile isolation. Installed-file guard: 21,729 hashes, zero changes.
- Exposure seeds 126011 / 126012, two-player custom decks, 120 seconds each off/on: off zero casts/activations/tokens; on 3 casts, 5 activations and 5 Zombie resolutions / 1 cast, 2 activations and 2 resolutions. All four observations finished within clock with zero exceptions. Card-use evidence only; no winner or strength claim. Clears **FDC - Wretched Ranks**. See `arena/ghoulcaller-gisa/README.md` and `verification-summary.json`.

## Carpet of Flowers lab
- Approved opt-in rule: retain native permanent casting timing and affordability, but cast only with an opponent controlling an Island. Each trigger selects the legal opponent with the most Islands, stable seat-order tie break, and declines if none have Islands. Native once-per-turn limits and mana-color choice remain in force.
- Baseline: one check reproduces blocked normal selection. Focused gate: 18 checks. Full gate: 5,687 checks across 54 suites (18 new plus all 5,669 prior checks at exact counts). Native fixtures verify cast/trigger admission, greatest-Island selection and ties, mana production, timing/board-state holds and profile isolation. Installed-file guard: 21,729 hashes, zero changes.
- Exposure seeds 125011 / 125012, two-player custom decks, 120 seconds each off/on: off zero casts/triggers/resolutions; on 2 casts, 12 triggers, 2 resolutions / 6 casts, 13 triggers, 6 resolutions. All four observations finished in 1.3–1.8 seconds with zero exceptions. Trigger witnesses show the selected opponent; native fixtures verify Island ranking and mana. Card-use evidence only; no winner or strength claim. Clears **WHO - Paradox Power**. See `arena/carpet-of-flowers/README.md` and `verification-summary.json`.

## Immoral Bargain lab
- Approved opt-in rule: cast in our main phase with an empty stack only when paying `{1}{B}{G}` and sacrificing expendable creatures can remove worthwhile opposing nonland permanents. X is the smaller of expendable creatures and native-useful targets. Sacrifice tokens first, then weakest replaceable creatures, preserving the best non-token creature; native Destroy AI retains target ranking and legality.
- Baseline reproduces the blocked normal selection. Full gate: 5,669 checks across 53 suites (36 new plus all 5,633 prior checks at exact counts). Native fixtures prove X/fodder caps, target ranking and protection filtering, real payment/destruction, timing, stale-state cancellation and profile isolation. Installed-file guard: 21,729 hashes, zero changes.
- Exposure seeds 124011 / 124012, two-player custom decks, 120 seconds per observation, off/on: off zero casts/resolutions; on 1/1 and 2/2. All four observations completed in 1.6–1.9 seconds with zero exceptions. Seed 124011 logs the Grizzly Bears sacrifice before destroying Craw Wurm; seed 124012 destroys Isamaru then Craw Wurm. Card-use evidence only; no strength claim.
- Clears **WHO - Witherbloom Pestilence**. See `arena/immoral-bargain/README.md` and `verification-summary.json` for reproducible commands, hashes and per-cast log witnesses.

## Doomsday Confluence lab
- Approved opt-in rule: own second main, empty stack, largest natively affordable X for `{X}{X}{B}`, never X=0. At most one sacrifice when we/allies have no nonartifact creatures and an opponent has a legally sacrificable one; then at most one discard when two opponents have nonempty hands; fill remaining slots with native Daleks. Native printed order and opponent choices remain intact.
- Baseline: three checks show blocked normal selection and the native repeated-mode defect at affordable X=4. Full gate: 5,633 checks across 52 suites (140 new plus all 5,493 prior at exact counts). Native fixtures cover payment including rocks/taxes, team/protection safety, exact mode counts/order and real consequences, fresh mana/board/hand/timing cancellation in both execution paths, game isolation and default/unrelated-profile parity.
- The helper has no cached state or policy RNG and reads only public opposing hand counts. It rebuilds X and the mode chain on evaluation and immediately before payment. Original script and installed resources remain unchanged; installed-file guard checks 21,729 hashes with zero changes.
- Final exposure seeds 123011 / 123012, three-player custom decks, 120 seconds each off/on: off zero casts/resolutions; on three paid/resolved casts per seed with X=1,2,7 / X=1,2,5. Sacrifice/discard/token mode totals 2/3/5 and 2/1/5; three actual sacrifices each and six/two discards. Every cast used own second main and exactly 2X+1 mana. All four final observations finished in 4–6 seconds with zero exceptions; card-use evidence only, no strength claim.
- Three seats exercise the two-opponent discard threshold. Wall-only and Craw-Wurm-only preliminary controls timed out at 120 seconds and are excluded; raw logs/hashes remain recorded. Final opponents use low creature density and Underworld Dreams to end naturally, without increasing the clock. See `arena/doomsday-confluence/README.md` and `verification-summary.json`. Clears **WHO - Masters of Evil**.

## Moonlight Bargain lab
- Approved opt-in rule: cast only in the opponent end step immediately before our turn, empty stack, hand at most three including Bargain, library at least seven and life at least fourteen, with native mana affordability. After the native look, select a needed land below six lands with none in hand, then nonlands ranked by the approved near-term mana-value/name rule. At most three keeps/six life, retaining ten life; recheck cast state and every payment.
- Baseline: five checks show blocked selection, refusal of lands/noncreatures/low-power creatures and unbounded ten-life spending on five creatures. Full gate: 5,493 checks across 51 suites (105 new plus all 5,388 prior at exact counts). New fixtures prove native mana/life payment, exact keeps and hand/graveyard destinations, cleanup, no-draw semantics, stale-state cancellation, recasting, default/unrelated-profile parity and native game-copy/LKI ability-ID budget isolation.
- Exposure seeds 122011 / 122012, two-player custom decks, 120 seconds per observation, off/on: off zero casts/resolutions; on casts/resolutions 4 / 1, paid keep offers 10 / 2, life spent 20 / 4. Per-resolution life spending: 6, 6, 6, 2 / 4. Every cast paid five Swamp mana at the approved end step. All four observations finished within the clocks; zero exceptions. Card-use/payment evidence only, no strength claim.
- Logs write `Resolve Stack` after the effect's life payments; inspect payments between cast and resolution markers. Library-to-hand/graveyard moves are omitted by native match logs, so exact keep choices and no-draw behavior are fixture evidence. Raw logs and witness line numbers are recorded in `arena/moonlight-bargain/verification-summary.json`; commands and limits are in its README.
- Clears **C18 - Nature's Vengeance**. Keep sets are weakly scoped by game and indexed by ability ID, with primitive identities only, so copied-game evaluations cannot overwrite a live resolution's budget. Installed-file guard: 21,729 hashes, zero changes. Installed resources and original card script remain unchanged.

## Jolrael lab
- Approved opt-in rule: native creature casting and second-draw Cat trigger; activate at our beginning of combat with an empty stack only if at least two attack-ready creatures gain at least four combined power and no friendly creature loses power or toughness. Recheck current hand, board, source and affordability before payment; do not repeat this source's activation in the same turn.
- Native baseline: two checks expose rejection of the useful activation and blocked normal selection. New fixtures cover native selection, six-mana colored payment, 4/4 resolution, end-of-turn restoration, Cat creation, safety/timing holds, both execution paths' stale-state cancellation, game isolation and default/unrelated-profile behavior.
- Exposure seeds 121011 / 121012, two-player custom decks, 120 seconds each, off/on: off zero use in both; on casts 1 / 1, activations 4 / 13, Cat triggers 1 / 2. All 17 activations paid six Forest mana, resolved before attackers and were followed by boosted attacks. No exceptions or clock-limited observations. Card-use evidence only, no strength claim.
- Jolrael clears **MKC - Deep Clue Sea** and **SLD - From Cute to Brute**; Peace Offering retains three blockers. See `arena/jolrael/README.md` and `verification-summary.json` for reproducible commands, hashes and per-activation log witnesses.
- Reused the native layered base-stat preview, with an opt-in exception for the exact native Commander casting permission. Existing preview callers retain their previous behavior. Installed-file guard: 21,729 hashes, zero changes.

Shared tools (committed `a7a307a4`):
- `arena/lib/Check-Gate.ps1`
- `arena/lib/Run-Smoke.ps1`
- `arena/lib/next-cards.py`
- `arena/lib/PLAYBOOK.md`

Jolrael also fixes `Check-Gate.ps1` patch line endings to match LF or CRLF reconciled sources; otherwise an LF shared source cannot be overlaid by PowerShell's CRLF patch output.

Skill: `.claude/skills/commander-unlock/SKILL.md`.

## How to continue
1. `python -I arena/lib/next-cards.py` lists remaining single-blocker decks (3 distinct cards below; it can take over two minutes).
2. Follow `arena/lib/PLAYBOOK.md`:
   1. Propose rules **in text** and wait for approval.
   2. Write the helper class with one-line native hooks, plus a profile in `forge-gui/res/ai/overrides/`.
   3. Write native fixtures, then run the gate: `-Baseline`, then `-Focused`, then full with `OutName final-1.0.27`.
   4. **Run exposure games with purpose-made decks** (prefer two players; use three when a rule requires two opponents; off/on, two seeds, 120 s). No long random precon games.
   5. Inspect the logs, not just the counts.
   6. Update the README, add the registry rows in `arena/coverage/card-status.tsv`, run `./arena/Audit-Deck.ps1 -All -Coverage`, then commit and tag.

## Remaining single-blocker cards
Latest inventory (`python -I arena/lib/next-cards.py`): Crucible of the Spirit Dragon (AFC - Draconic Rage), Disciple of Bolas (C20 - Ruthless Regiment), Epic Experiment (C21 - Prismari Performance), and Goblin Bombardment (SLD - Goblin Storm). Crucible remains deferred; read the other card scripts before proposing rules.

**Crucible of the Spirit Dragon is deferred.** Native AI payment cannot spend storage-counter mana, so it needs payment support as its own job (see `arena/utility-lands/README.md`).

## Lessons and pitfalls
- **Stale card references:** after a zone change, re-fetch the card with `game.getCardState(card)`.
- **ETB triggers in fixtures:** call `runWaitingTriggers()` and then `addAllTriggeredAbilitiesToStack()` before `resolveStack()`.
- **Your own ability on the stack:** guard activated pumps against "already on the stack" (Flitter double-pumped without this guard).
- **Misleading stack descriptions:** "loses 0 life" in a Resolve line can be pre-resolution text; check the `Life:` lines.
- **Deck names can contain card names:** anchor count regexes with `^Add To Stack: .*cast <card>`.
- **Gate hash:** don't reformat a source file after its gate; the gate records its sha256.
- **Locked directories:** if `rm` says "Device or resource busy", the shell working directory is inside it; `cd` to the repo root in a separate command first.
- **Players leave `game.getPlayers()` when they lose:** hold a `Player` reference before a lethal resolution instead of calling `p(i)` after it.
- **Fixture colours:** exposure decks must be able to cast the card they test (Elvish Visionary needs green).
- **Commit rules:** commit messages end with the `Co-Authored-By` line from the session attribution reminder. Never make claims about winners or strength.

## Copy and retarget family

Dualcaster Mage is verified as the first narrow copy-family unlock. Its `dualcaster-mage` opt-in targets only a legal, copyable opponent instant or sorcery and binds that spell before native copy resolution. NeoForge 1.0.28 gate: 5,915/5,915 checks across 67 suites; paired seeds 141011/141012: zero Dualcaster casts off, 9/8 on, with seven copied Opt resolutions each and no target failures or exceptions. It occurs in four decks but does not clear one by itself. See `arena/dualcaster-mage/README.md` and `verification-summary.json`.

Other blocked cards sharing copy or retarget APIs still need their own rule review: Wild Ricochet (4 decks), Strionic Resonator (4), Willbender (3), Aethersnatch, Boltbender, Refuse, and Sudden Substitution (one each). Do not generalize Dualcaster's opponent-spell filter to activated or triggered ability copying, spell retargeting, or control exchange.
