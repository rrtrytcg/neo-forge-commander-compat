# Commander compatibility coverage

Generated 2026-10-21T12:34:40 from `a6d1bd40` by `arena/Audit-Deck.ps1 -All -Coverage`.

| Metric | Value |
| --- | --- |
| Decks in inventory | 184 |
| **Decks with zero remaining blocked cards** | **90** |
| Distinct blocked cards remaining | 264 |
| Blocked card occurrences remaining | 352 |
| Blocked occurrences already unlocked | 378 |
| Cards mechanically unlocked | 109 |
| ... unlocked only, not yet verified | 10 |
| ... of which strategically verified | 99 |
| Cards with no implementation | 0 |
| Unhandled AILogic hints | 0 |

Last recorded engine compatibility snapshot: **NeoForge 1.0.27**, source `899567ea` — 4060 native checks, 20 deadline checks and 22 paid/resolved representative probe casts. [Evidence and version-update workflow](../version-compat-1.0.27/README.md). This snapshot does not automatically certify later source changes or later engine versions.

## Unlocked but not yet verified

The prohibition is bypassed under an opt-in profile, but the complete verification gate is not yet met. Some entries have native fixtures and still lack recorded autonomous card use; see each entry for the missing evidence. A focused custom-deck probe can provide that witness without a completed game.

- `Blighted Burgeoning` - adaptive landManaAuras structural admission; family gate 349/349 verifies mana mechanic only; full-card strategy and Commander cast not established
- `Creeping Renaissance` - adaptive-enchantment profile; native gate 76/76 includes cast threshold, choice and resolution; no recorded Commander cast, so not promoted
- `Faithless Looting` - ahoy-mateys profile; native gate 77/77 includes discard decisions, resolution and Pirate reanimation; no recorded Commander cast, so not promoted
- `Market Festival` - adaptive landManaAuras structural admission; family gate 349/349 verifies mana mechanic; no recorded Commander cast
- `Shimmerwilds Growth` - adaptive landManaAuras structural admission; family gate 349/349 verifies chosen-color mana; no recorded Commander cast
- `Tower Defense` - profile entry; sim showed 6 casts (handoff round 1), no correctness gate
- `Trace of Abundance` - adaptive landManaAuras structural admission; family gate 349/349 verifies mana/shroud handling; no recorded Commander cast
- `Utopia Sprawl` - adaptive landManaAuras structural admission; family gate 349/349 verifies Forest legality and chosen-color mana; no recorded Commander cast
- `Verdant Haven` - adaptive landManaAuras structural admission; family gate 349/349 verifies mana mechanic only; full-card strategy and Commander cast not established
- `Windfall` - ahoy-mateys profile; native gate 77/77 includes hand/draw gates and resolution; no recorded Commander cast, so not promoted

## Strategically verified

A fixture gate asserts the documented AI decisions, plus simulation card-use evidence where needed. Custom-deck probes need not finish a game; their results are not strength claims.

- `Abundance` - abundance opt-in native optional draw replacement; NeoForge 1.0.27 gate 4613/4613 across 43 suites (129 new plus 4484 prior including 39 Estrid), 20 deadline checks; fresh Land below six battlefield lands with no hand land, otherwise Nonland; native reveals/hand/bottoming and exact Commander return retained, uncertain draw engines deferred; six paired custom Commander observations seeds 114011/114012/114021: three paid/resolved casts, two Land and 46 Nonland choices, 48 found cards and 27 bottomed revealed cards, three Harmonize choices; seed 114011 exposes Nonland only, all three default controls zero use; 779 card-use checks, four clock-limited observations, no completion/winner/strength claim; see arena/abundance/verification-summary.json
- `Arcane Lighthouse` - arcane-lighthouse opt-in funded protection removal; NeoForge 1.0.25 gate 3829/3829 (158 new plus 3671 prior checks), native joint activation/removal funding, colored costs and taxes, exact stale-plan cancellation and unchanged default mana; custom Commander seed 110012: three paid/resolved original activations followed by four native removals (one destruction, three exiles; one hexproof and three shroud threats), two activation reuses after native untapping and original colorless mana in both on/off runs; both off controls zero special activation/removal; seed 110011 has no Lighthouse land-play exposure in either mode and remains a non-witness; 201 card-use checks, all four observations clock-limited at 30s, no completion or strength claim; see arena/arcane-lighthouse/verification-summary.json
- `Ashling the Pilgrim` - easy-cards-2 opt-in grow twice a turn, sweep only when lethal to an opponent and survivable; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Assault Suit` - assault-suit profile; NeoForge 1.0.25 native gate 21/21 plus prior Commander suites 1726/1726 (1747 total); custom seeds 96801/96802: 7 paid equip activations, each target the largest trample/flying/first-strike/regular by policy; 26 accepted handoffs only with 3+ mana including all three opponents, 56 declines without mana or an equipped creature; both off controls zero
- `Autumn Willow` - easy-cards-2 opt-in native cast; shroud-dropping ability vetoed; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Beast Within` - arena/beast-within gate 13/13 + sim 0/0/0 casts off, 2/2/4 on
- `Benevolent Offering` - easy-cards-2 opt-in second main when 2+ creatures ahead, tokens to the fewest-creature opponent; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Biomass Mutation` - biomass-mutation profile; NeoForge 1.0.25 native gate 116/116, prior Commander regressions 1610/1610 including unchanged shared Mirror evaluation, and deadlines 20/20; custom seeds 96781/96782: 16 paid/resolved/settled casts, 8 offensive and 8 defensive at X=3..17; native X+2 green/blue hybrid payments, actual X/X stats and creature types verified; four off controls zero; bounded combat policy
- `Bosh, Iron Golem` - easy-cards-2 opt-in cheapest sufficient artifact sacrificed to kill the best creature or finish an opponent; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Call for Aid` - easy-cards-3 opt-in only when the borrowed creatures make the attack lethal on another opponent; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Call the Coppercoats` - call-the-coppercoats opt-in bounded army building; NeoForge 1.0.25 gate 2725/2725 (212 new plus 2513 prior checks), including unchanged Command Beacon policy with shared private forecast wrapper; custom Commander scarce-white/plains seeds 104011/104012: 23 native paid/resolved casts create 214 original Soldiers, 17 single-target and six multi-target including one triple; native colored Strive payments verified, four off controls zero; 317 card-use checks; fresh target/count/affordability recheck and private pruned forecasts without live reservation/RNG changes; see arena/call-the-coppercoats/verification-summary.json
- `Carpet of Flowers` - carpet-of-flowers opt-in native permanent cast; trigger targets legal opponent with most Islands, stable seat-order ties, declines when none; NeoForge 1.0.27 gate 5687/5687 across 54 suites (18 new plus 5669 prior at exact counts); paired seeds 125011/125012 off/on: off zero casts/triggers/resolves, on 2 casts/12 triggers/2 resolves and 6/13/6; zero exceptions or clock limits; card-use evidence only; see arena/carpet-of-flowers/README.md and verification-summary.json
- `Cascade Bluffs` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Cave of Temptation` - easy-cards-3 opt-in sorcery-speed counters on the best creature with 8+ lands and no land in hand; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Command Beacon` - command-beacon opt-in commander tax relief; NeoForge 1.0.25 gate 2090/2090 (83 new plus 2007 prior checks); custom Commander seeds 100011/100012: 20 paid activations, 20 native returns and 20 matched one-white hand casts with unchanged command cast history; off control zero; 252 card-use checks; all observations clock-limited at 30s, card use only; four other lands, minimum four tax, pruned private native payment forecast and stale-plan cancellation; see arena/command-beacon/verification-summary.json
- `Confiscation Coup` - confiscation-coup opt-in energy payoff gate checks affordable opposing artifact/creature targets through native ranking, then pays only exact legal target mana-value energy up to four; native spell/payment/resolution; NeoForge 1.0.27 gate 5734/5734 across 57 suites (13 new plus 5721 prior exact); 21,729 installed hashes snapshotted before/after while preserving active 1.0.28 files, zero changed during gate; paired seeds 128011/128012 off/on: off 0/0 casts/resolutions, on 6/6 each, zero exceptions; seed 128011 resolves two Craw Wurms but captures only Hill Giants; card-use/target evidence only; see arena/confiscation-coup/README.md and verification-summary.json
- `Corpse Augur` - corpse-augur profile; NeoForge 1.0.25 native gate 139/139, prior Commander regressions 1229/1229 and deadline checks 20/20; custom seeds 96451/96452: 14 paid/resolved casts, 9 death triggers, 21 draws with matching life loss including 3 zero-card safety choices; both off controls zero; bounded ordinary draw/life-loss policy
- `Curse of the Swine` - curse-of-the-swine profile; native gate 36/36 plus Beast Within regression 13/13; natural cast probe 0/0/0 casts off, 4/2/1 on
- `Dakmor Salvage` - utility-lands opt-in dredge only when a land is needed and the library is safe; NeoForge 1.0.27 gate 4898/4898 (51 new plus 4847 prior); paired two-player exposure games seeds 117011/117012; card-use evidence only; see arena/utility-lands/README.md
- `Daretti, Scrap Savant` - daretti profile uses +2 only in our second main with discardable excess lands; -2 only with legal artifact graveyard target and sacrifice material; native choices, costs and resolution, native ultimate. NeoForge 1.0.28 gate 5861/5861 across 64 suites (26 new + 5835 prior exact); installed guard 21,729 hashes, zero changed; paired seeds 138011/138012 off 0/0 activations/resolutions, on 1/1 and 5/5; zero exceptions or clock caps; see arena/daretti/README.md and verification-summary.json
- `Darksteel Mutation` - darksteel-mutation profile; native gate 87/87 and prior-profile regressions 563/563; custom seeds 96341/96342: 23 paid/resolved Aura casts, 10 indestructible threats and 13 small mana creatures; actual native attachment, base 0/1, type/keyword changes and ability removal; four off controls zero
- `Dawn's Reflection` - arena/adaptive-enchantment gate 76/76 and family gate 349/349; Commander seed 95331 cast, normal completion; bounded mana/target policy
- `Deadly Tempest` - deadly-tempest opt-in bounded mass destruction and per-controller life loss; NeoForge 1.0.27 gate 4445/4445 (147 new plus 4298 prior), 20 deadline checks; two-extra-opposing-death and native value advantage, five-life allied reserve, native tokens/LKI/shields/indestructibility, regeneration responses and fresh pre-payment cancellation; six custom Commander observations at seeds 113011/113012/113021: 25 paid/resolved original casts destroy 115 opposing and 14 friendly creatures with exactly 129 total life lost, eight native shield saves and 30 indestructible survival observations; all three default controls zero Tempest casts; 874 card-use checks, five clock-limited observations, no completion/winner/strength claim; see arena/deadly-tempest/verification-summary.json
- `Decree of Pain` - native gate 69/69; custom-deck autonomous use seeds 95771/95772: casts 3/3 on vs 0/0 off, cycling 3/1 on vs 0/0 off; native payment and cycling trigger/draw traces; card-use evidence only, no completion or strength claim
- `Dispatch` - dispatch profile; native gate 61/61 plus Mirror Entity 48/48, Decree 69/69 and Oreskos 100/100 regressions; custom seeds 96011/96012: 9/7 paid/resolved casts and actual exiles, 15 indestructible targets, both off controls zero; native metalcraft, legality and payment retained
- `Divine Reckoning` - divine-reckoning profile forecasts the native best-creature choice per AI player and casts in our second main only when opposing creature loss value exceeds friendly loss; NeoForge 1.0.28 gate 5835/5835 across 63 suites (15 new + 5820 prior exact); installed guard 21,729 hashes, zero changed; seeds 137011/137012 off zero casts/resolutions, on 4/4 and 6/6; zero exceptions; card-use evidence only; see arena/divine-reckoning/README.md and verification-summary.json
- `Doomsday Confluence` - doomsday-confluence opt-in own second main, empty stack, largest affordable XXB value above zero; at most one friendly-safe legal sacrifice then one discard for two nonempty opposing hands, remaining modes native Daleks; fresh X/mode rechecks with native order/payment/opponent choices; NeoForge 1.0.27 gate 5633/5633 across 52 suites (140 new plus 5493 prior at exact counts); final paired three-player seeds 123011/123012 off/on: off zero use, on 3/3 paid-resolved casts at X 1,2,7 / 1,2,5, all modes exercised, zero exceptions or clock limits; preliminary timed-out controls excluded; card-use evidence only; see arena/doomsday-confluence/README.md and verification-summary.json
- `Drawn from Dreams` - drawn-from-dreams profile bypasses only RemoveDeck; native DigAi timing, choices, payment and resolution; NeoForge 1.0.28 gate 5895/5895 across 66 suites (12 new + 5883 prior exact); installed guard 21,729 hashes, zero changed; paired seeds 140011/140012 off 0/0 casts/resolutions, on 5/5 and 9/9; zero exceptions; clears NCC - Maestros Massacre; see arena/drawn-from-dreams/README.md and verification-summary.json
- `Dualcaster Mage` - dualcaster-mage opt-in copies only a legal, copyable opponent instant or sorcery; native target legality and resolution; NeoForge 1.0.28 gate 5915/5915 across 67 suites (20 new + 5895 prior exact); installed guard 21,729 hashes, zero changed; paired seeds 141011/141012 off 0 Dualcaster casts, on 9/8 casts and 7 copied Opt resolutions each; zero target failures or exceptions; see arena/dualcaster-mage/README.md and verification-summary.json
- `Edge of Autumn` - easy-cards-2 opt-in cast with 4 or fewer lands and a basic in library; land-sacrifice cycling vetoed; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Esix, Fractal Bloom` - easy-cards-3 opt-in copy our best nonlegendary creature for the first tokens each turn; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Fertile Ground` - arena/adaptive-enchantment gate 76/76; Commander seed 95318 cast, normal completion; bounded mana/target policy
- `Fetid Heath` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Fire-Lit Thicket` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Flooded Grove` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Gaze of Granite` - gaze-of-granite opt-in repair; NeoForge 1.0.25 gate 1860/1860 (40 Gaze checks); holds dying continuous sources and Monument/Force collateral deaths; fresh custom seeds 97031/97032 and profile-off control, native X payment/destruction and land survival; see arena/trading-post-gaze/verification-summary.json
- `Ghoulcaller Gisa` - ghoulcaller-gisa opt-in native Gisa casting; deterministic own-second-main activation with empty stack after native X/cost check; native {B}/{T}/other-creature payment and power-based Zombie count; NeoForge 1.0.27 gate 5706/5706 across 55 suites (19 new plus 5687 prior at exact counts); paired seeds 126011/126012 off/on: off 0/0 casts/activations, on 3/5 and 1/2; 5/2 native Zombie resolutions, zero exceptions; card-use evidence only; see arena/ghoulcaller-gisa/README.md and verification-summary.json
- `Gideon Jura` - gideon-jura opt-in admits the planeswalker but uses only native -2 in our main phase with empty stack, 2+ loyalty and a legal tapped opposing creature; native Destroy ranking/payment/resolution, +2/0 held; NeoForge 1.0.28 gate 5735/5735 across 57 suites (15 new plus 5720 prior exact); installed guard: 21,729 hashes, zero changed; paired seeds 129011/129012 off/on: off zero casts/activations, on 9/9 and 15/15 casts/activations, matching spell and destroy resolutions, zero exceptions; card-use evidence only; see arena/gideon-jura/README.md and verification-summary.json
- `God-Eternal Bontu` - easy-cards-3 opt-in sacrifice tokens then spare lands (max three) to draw; death trigger accepted; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Graven Cairns` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Greater Good` - greater-good opt-in rescue; NeoForge 1.0.25 gate 1935/1935 (75 Greater Good plus 1860 prior checks); custom removal seeds 98011/98012 and combat seed 98021: 3 casts, 16 paid/resolved activations; two off controls zero; 181 card-use checks; initial vanilla-combat non-witness preserved; native sacrifice LKI/payment/discard, four-card library reserve and stale-plan cancellation; see arena/greater-good/verification-summary.json
- `Harsh Mercy` - choice-wipes opt-in cast gate forecasts native prominent-type choices; preserves friendly creatures and casts only for at least one opposing creature loss; native choices and destroy resolution; NeoForge 1.0.27 gate 5721/5721 across 56 suites (15 new plus 5706 prior at exact counts); paired two-player seeds 127011/127012 off/on: off 0/0 casts/resolves, on 1/1 both seeds; zero exceptions or clock limits; card-use evidence only; see arena/choice-wipes/README.md and verification-summary.json
- `HELIOS One` - utility-lands opt-in end-step energy and MV3+ opposing permanent destruction keeping five lands; NeoForge 1.0.27 gate 4898/4898 (51 new plus 4847 prior); paired two-player exposure games seeds 117011/117012; card-use evidence only; see arena/utility-lands/README.md
- `Hull Breach` - hull-breach opt-in opposing modal destruction; NeoForge 1.0.27 gate 4298/4298 (238 new plus 4060 prior checks), 20 deadline checks; native distinct-pair preference, native removal value and stable ties, mandatory targets, fresh incarnation/mode/payment checks, shield/ward/regeneration and uncertain-engine holds, native RG/tax payment and partial/all-invalid response resolution; six custom Commander observations at seeds 112011/112021/112031: 17 paid/resolved casts destroy 20 opposing permanents (four artifact-only, ten enchantment-only, three combined), all three default controls zero; 406 card-use checks, three clock-limited observations, no completion/winner/strength claim; see arena/hull-breach/verification-summary.json
- `Hunter's Insight` - hunters-insight opt-in single-step combat draw; NeoForge 1.0.25 gate 2007/2007 (72 new plus 1935 prior checks); custom seeds 99011/99012: 16 casts, 14 matched delayed draws, 48 cards drawn; off control zero; 175 card-use checks; native targeting, payment, damage and draw trigger; minimum three damage, four-card reserve and one cast per combat; see arena/hunters-insight/verification-summary.json
- `Immoral Bargain` - immoral-bargain opt-in main-phase native destroy for expendable-creature X; token-first, weakest replaceable creature with best non-token preserved; native targets, fresh cast/payment checks; NeoForge 1.0.27 gate 5669/5669 across 53 suites (36 new plus 5633 prior at exact counts); paired seeds 124011/124012 off/on: off 0/0 casts/resolved, on 1/1 and 2/2; zero exceptions or clock limits; card-use evidence only; see arena/immoral-bargain/README.md and verification-summary.json
- `Jolrael, Mwonvuli Recluse` - jolrael opt-in native cast and second-draw Cat trigger; own beginning-of-combat animation requires two attack-ready creatures gaining at least four combined power with no friendly stat reduction, fresh pre-payment rechecks and no repeat this turn; NeoForge 1.0.27 gate 5388/5388 across 50 suites (71 new plus 5317 prior at exact counts); paired two-player seeds 121011/121012 off/on: off zero use, on 2 casts, 17 paid/resolved activations followed by boosted attacks and 3 Cat triggers, zero exceptions or clock limits; card-use evidence only; see arena/jolrael/README.md and verification-summary.json
- `Kenrith's Transformation` - kenriths-transformation opt-in bounded Elk neutralization; NeoForge 1.0.25 gate 2366/2366 (157 new plus 2209 prior checks), including unchanged Darksteel Mutation and Viscera Seer suites; custom threats/utility seeds 102011/102012: 26 native paid casts, 26 original 3/3 green Elk attachments and 26 matched entry draws, four off controls zero; 476 card-use checks; net downgrade without combat-stat upgrades, four-card library reserve and fresh pre-payment safety; all eight observations clock-limited at 30s, card use only; see arena/kenriths-transformation/verification-summary.json
- `Knollspine Dragon` - easy-cards-2 opt-in second-main cast; enter trigger draws only when damage dealt beats the discarded hand; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Labyrinth of Skophos` - utility-lands opt-in removal of big or lethal unblocked attackers after blocks; NeoForge 1.0.27 gate 4898/4898 (51 new plus 4847 prior); paired two-player exposure games seeds 117011/117012; card-use evidence only; see arena/utility-lands/README.md
- `Magus of the Wheel` - magus-of-the-wheel profile activates only in our second main with empty stack, our hand <=2, library >=7 and every opponent hand >=4; native mana/tap/sacrifice costs and wheel resolution; NeoForge 1.0.28 gate 5820/5820 across 62 suites (22 new + 5798 prior exact); installed guard 21,729 hashes, zero changed; paired seeds 136051/136052/136053 off zero activations, on 6/0/11; all games clean; card-use evidence only; see arena/magus-of-the-wheel/README.md and verification-summary.json
- `Marauding Raptor` - easy-cards-3 opt-in native cast; small non-Dinosaur creature spells held while it is in play; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Marsh Flitter` - easy-cards opt-in Goblin-token sacrifice only after blocks when a 3/3 changes the combat; NeoForge 1.0.27 gate 5176/5176 across 47 suites (54 new plus 5122 prior); paired two-player exposure games seeds 118011/118012 off/on; arena/easy-cards
- `Marshal's Anthem` - marshals-anthem profile; native gate 135/135 and prior-profile regressions 428/428; custom seeds 96231/96232: 5 paid kicked casts return 7 creatures, 6 useful unkicked casts, four off controls zero; native payment, actual +1/+1 and graveyard-to-battlefield targets verified
- `Masterful Replication` - easy-cards-3 opt-in end step of an opponent's turn, always the two Golems; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Mirage Mirror` - mirage-mirror opt-in bounded creature-combat copies; NeoForge 1.0.25 gate 4053/4053 (224 new plus 3829 prior checks), native copy characteristics, sickness/haste, target legality, payment, stale casting bans, game-local activation history and exact native Commander permission exception; eight final custom Commander observations: 17 native artifact casts and 79 original activations (78 offensive, one defensive), copied Mirrors make 13 native attacks and one native block, 73 later artifact-restoration witnesses; all four off controls zero Mirror use; 1021 card-use checks; many offensive activations are followed by native attack holds, retained as a limitation of the approved legal-attacker rule; six initial Commander-permission no-use diagnostics retained separately; no completion or strength claim; see arena/mirage-mirror/verification-summary.json
- `Mirror Entity` - mirror-entity profile; native gate 48/48 and Decree regression 69/69; custom seeds 95881/95882: 17 offensive and 19 defensive paid/resolved activations, all four off controls zero; actual combat participants and before/after stats verified
- `Misleading Signpost` - misleading-signpost opt-in defensive flash and native blue-mana fallback; NeoForge 1.0.25 gate 3671/3671 (169 new plus 3502 prior checks), native largest power-three attacker/lowest-life legal other opponent, planeswalker rescue, allies/hexproof/taxes/bands, stale-plan cancellation and independent original trigger; custom Commander seeds 109011/109012: eleven paid/resolved Signposts (three defensive flashes, eight last-opponent end-step fallbacks), three original combat redirections, ten native blue-mana payments and seven reuses after observed native untapping; both off controls zero Signpost use; 253 card-use checks, no completion or strength claim; see arena/misleading-signpost/verification-summary.json
- `Molten Slagheap` - molten-slagheap opt-in routes stored mana through native ritual evaluation and native mana-source selection; spends only the shortfall after other sources/activation cost and holds when ordinary mana already covers a noninstant; NeoForge 1.0.28 gate 5745/5745 across 58 suites (12 new plus 5733 exact); installed guard 21,729 hashes, zero changed; paired seeds 132011/132012 off zero Solemn casts/storage-mana activations, on 4 casts/4 activations and 12/1; zero exceptions or clock limits; card-use evidence only; see arena/molten-slagheap/README.md and verification-summary.json
- `Moonlight Bargain` - moonlight-bargain opt-in last-opponent end-step refuel with hand <=3 including Bargain, library >=7 and life >=14; needed land then deterministic mana-value-ranked nonlands, at most 3 keeps/6 life with 10-life reserve and fresh cast/payment rechecks; native no-draw resolution and game-local keep budgets; NeoForge 1.0.27 gate 5493/5493 across 51 suites (105 new plus 5388 prior at exact counts); paired two-player seeds 122011/122012 off/on: off zero use, on 4/1 paid-resolved casts with 10/2 paid keeps costing 20/4 life, zero exceptions or clock limits; exact keep choices/zones/no-draw semantics proven by native fixtures; card-use evidence only; see arena/moonlight-bargain/README.md and verification-summary.json
- `Mystic Confluence` - mystic-confluence profile selects a useful native counter, highest-valued legal opposing creature bounce, then repeated draw; NeoForge 1.0.28 gate 5883/5883 across 65 suites (22 new + 5861 prior exact); installed guard 21,729 hashes, zero changed; paired seeds 139011/139012 off 0/0 casts/resolutions, on 6/6 and 12/12, all bounce and 6/11 repeated-draw resolutions; one additional counter+bounce+draw resolution; zero exceptions; card-use evidence only; see arena/mystic-confluence/README.md
- `Mystic Gate` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Nesting Grounds` - nesting-grounds profile; NeoForge 1.0.25 native gate 115/115, prior Commander regressions 1495/1495 and deadlines 20/20; custom seeds 96671/96672: 19 paid/resolved/settled activations, 6 positive transfers add 17 counters with native Hardened Scales, 13 negative transfers kill 13 opposing creatures; four off controls zero; bounded +1/+1 and -1/-1 policy, native payment and legality retained
- `Nyx Weaver` - nyx-weaver opt-in native creature casting and bounded exile/recovery; NeoForge 1.0.27 gate 5122/5122 across 46 suites (224 new plus 4898 prior including unchanged 51 utility-land checks), three native baseline checks and 20 deadline checks; native joint colored/taxed payment, ordinary four-plus recovery, doomed-source and low-library cash-outs, mandatory mill after source exile, target-response fizzle and fresh pre-cost binding; eight paired custom Commander observations seeds 118011/118012/118021/118031: 16 casts, 42 original mills moving 84 cards, 12 recoveries (three ordinary, eight doomed, one low-library), eight later native casts including three same-turn; first value seed correctly holds with only two green sources, all four default controls zero Nyx use; 1148 card-use checks, four clock-limited runs; Progenitus no-use diagnostics kept separate, no completion/winner/strength claim; see arena/nyx-weaver/verification-summary.json
- `Oreskos Explorer` - oreskos-explorer profile; native gate 100/100 plus Mirror Entity 48/48 and Decree 69/69 regressions; custom seeds 95901/95902: 13 paid/resolved casts, 12 complete searches, 24 Plains fetched including 19 nonbasic; both off controls zero; native casting/search retained with empty-ETB veto exception
- `Pact of the Serpent` - easy-cards opt-in self-target naming our most common creature type with X/life/library guards; NeoForge 1.0.27 gate 5176/5176 across 47 suites (54 new plus 5122 prior); paired two-player exposure games seeds 118011/118012 off/on; arena/easy-cards
- `Peel from Reality` - easy-cards-2 opt-in resets an enter-trigger or afflicted creature and bounces the opponent's best; NeoForge 1.0.27 gate 5250/5250 across 48 suites (74 new plus 5176 prior); paired two-player exposure games seeds 119011-119013 off/on; arena/easy-cards-2
- `Phyrexia's Core` - phyrexias-core opt-in preserves native timing, {1} mana and artifact sacrifice; activate only at 5 or less life or when native combat evaluation says life is in danger. NeoForge 1.0.28 gate 5798/5798 across 61 suites (17 new + 5781 prior exact); installed guard 21,729 hashes, zero changed; paired seeds 135011/135012 off zero activations, on 5/9; zero exceptions or clock caps; native sacrifice choice/resolution; card-use evidence only; see arena/phyrexias-core/README.md and verification-summary.json
- `Prismatic Lens` - prismatic-lens opt-in one-for-one Any-color filter admission; NeoForge 1.0.25 gate 2886/2886 (161 new plus 2725 prior checks); native prediction/payment, two separately funded filters, genuine colorless costs, floating mana, untapping, taxes and activation prohibitions; custom Commander seeds 105011/105012: 10 paid/resolved Lens casts, 14 actual colored conversions covering W/U/B/R/G and two free colorless payments, same Lens reused after turn untapping; both off controls zero Lens use; 212 card-use checks, all four observations clock-limited at 30s, card-use evidence only; shared mechanic also tested on explicitly opted-in Prism without production unlock; see arena/prismatic-lens/verification-summary.json
- `Prophetic Prism` - prophetic-prism opt-in native permanent cantrip and existing one-for-one mana filtering; NeoForge 1.0.25 gate 3080/3080 (194 new plus 2886 prior checks), unchanged Lens suite; native entry evaluation and four-card reserve, public uncertainty holds and fresh pre-payment safety; native costs, original mandatory draw and Lens filtering retained; custom Commander seeds 106011/106012: 13 paid/resolved Prism casts, 13 exact original entry draws and 25 actual colored payments, reused Prisms after native untapping, both off controls zero use; 366 card-use checks, all four observations clock-limited at 30s, no completion or strength claim; see arena/prophetic-prism/verification-summary.json
- `Psychosis Crawler` - rule is scripted (NeedsToPlayVar X GE3); arena/psychosis-crawler gate 6/6 + sim 0/0/0 casts off, 3/9/3 on
- `Rakdos Charm` - arena/ahoy-mateys gate 77/77; Commander seed 95441 cast, normal completion; bounded mode priority and self-lethal protection
- `Recurring Insight` - easy-cards opt-in most-cards opponent with 3+ hand cast and 1+ rebound, library guard; NeoForge 1.0.27 gate 5176/5176 across 47 suites (54 new plus 5122 prior); paired two-player exposure games seeds 118011/118012 off/on; arena/easy-cards
- `Ride the Avalanche` - ride-the-avalanche opt-in requires a legal own counter target and jointly affordable follow-up spell; native CastWithFlash and counter trigger; NeoForge 1.0.28 cumulative gate 5753/5753 across 58 suites (17 new plus 5736 prior exact); paired seeds 130011/130012 off/on: off zero casts/triggers, on 4 casts with 4 triggers and 3 casts with 2 triggers, zero exceptions; card-use evidence only; see arena/ride-the-avalanche/README.md and verification-summary.json
- `Rugged Prairie` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Saltcrusted Steppe` - saltcrusted-steppe under the molten-slagheap profile reuses native battery evaluation, with opt-in Combo G W parsing and exact storage-counter shortfall; NeoForge 1.0.28 gate 5781/5781 across 60 suites (14 new plus 5767 exact); installed guard 21,729 hashes, zero changed; paired seeds 134011/134012 off zero storage-mana activations, on 5/2, no exceptions or clock caps; card-use evidence only; see arena/saltcrusted-steppe/README.md and verification-summary.json
- `Selective Obliteration` - selective-obliteration opt-in preserves each player's highest-value mono-color group by public permanent evaluation and casts only when forecast opposing exile value exceeds friendly loss; native timing/payment/resolution; NeoForge 1.0.28 gate 5736/5736 across 57 suites (16 new plus 5720 prior exact); installed guard: 21,729 hashes, zero changed; paired seeds 129011/129012 off/on: off 0/0 resolutions, on 1/2, zero exceptions; card-use evidence only; see arena/selective-obliteration/README.md and verification-summary.json
- `Selvala, Explorer Returned` - selvala-explorer-returned profile; NeoForge 1.0.25 native gate 13/13 plus prior Commander suites 1747/1747 (1760 total); custom seeds 96901/96902: 2 natural casts and 23 resolved parleys, life gain and every remaining player's draw matching the revealed nonlands, tap cost paid and native non-mana classification retained; both off controls zero; quoted profile-list support for the comma in the card name
- `Skeletal Scrying` - skeletal-scrying opt-in bounded draw and protected graveyard payment; NeoForge 1.0.25 gate 3314/3314 (234 new plus 3080 prior checks); native X2/X3, taxes/reductions, colored payment, reserves, reuse permissions, exact exile binding and stale-plan cancellation; final custom Commander seeds 107011/107012: three paid/resolved X3 casts, nine original draws/life lost/basic exiles, protected creatures retained, 17 original Supplier mills in on runs; both off controls zero use; 166 card-use checks, all four observations clock-limited at 30s, no completion or strength claim; see arena/skeletal-scrying/verification-summary.json
- `Slaughter the Strong` - arena/slaughter gate 18/18 + sim 0/2 casts off, 2/3/1 on
- `Suffer the Past` - easy-cards opt-in opponent graveyard drain at end step before our turn (X>=3) or when lethal; NeoForge 1.0.27 gate 5176/5176 across 47 suites (54 new plus 5122 prior); paired two-player exposure games seeds 118011/118012 off/on; arena/easy-cards
- `Sunken Ruins` - arena/filter-lands gate 10/10 (whole filter land cycle)
- `Swan Song` - swan-song opt-in bounded top-opponent permission and Bird safety; NeoForge 1.0.25 gate 3502/3502 (188 new plus 3314 prior checks), native exact-X mana value, lethal/rescue exceptions, Bird uncertainty, taxes, stale-plan cancellation and unchanged native counter-response admission bypass; custom Commander seeds 108011/108012: six opt-in paid/resolved value counters with six original Birds, zero cheap opt-in counters while 52 opposing cheap draws resolve; default controls retain six value and twelve cheap counters with original Birds; value seed 108011 has no draw-spell exposure in either mode; 637 card-use checks, no completion or strength claim; see arena/swan-song/verification-summary.json
- `Sylvan Offering` - sylvan-offering opt-in casts only in our second main with empty stack and an asymmetric anthem/token-multiplier advantage over the fewest-creature opponent, cap X at two; native payment, legality and token replacement/resolution; NeoForge 1.0.28 gate 5767/5767 across 59 suites (22 new plus 5745 exact); installed guard 21,729 hashes, zero changed; paired seeds 133011/133012 off zero casts, on 3/3, zero exceptions or clock limits; card-use evidence only; see arena/sylvan-offering/README.md and verification-summary.json
- `Syr Elenora, the Discerning` - easy-cards opt-in native play (no rule), cast with draw and hand-size power; NeoForge 1.0.27 gate 5176/5176 across 47 suites (54 new plus 5122 prior); paired two-player exposure games seeds 118011/118012 off/on; arena/easy-cards
- `The Prismatic Piper` - easy-cards-3 opt-in native cast (no rule); NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Thespian's Stage` - utility-lands opt-in one-time copy of a basic land of the scarcest commander colour; NeoForge 1.0.27 gate 4898/4898 (51 new plus 4847 prior); paired two-player exposure games seeds 117011/117012; card-use evidence only; see arena/utility-lands/README.md
- `Throes of Chaos` - easy-cards-3 opt-in first-main cast and spare-land retrace; NeoForge 1.0.27 gate 5317/5317 across 49 suites (67 new plus 5250 prior); paired exposure games seeds 120011-120013 off/on; arena/easy-cards-3
- `Timely Ward` - timely-ward opt-in permanent protection and commander rescue; NeoForge 1.0.25 gate 2513/2513 (147 new plus 2366 prior checks), including unchanged Darksteel Mutation/Kenrith/Viscera suites; custom Commander proactive/rescue seeds 103011/103012: 19 paid casts, 11 native attachments (10 proactive, one flash rescue with settled same-commander survivor), eight native counterplay fizzles recorded separately; four off controls zero; 216 card-use checks; native target-dependent flash timing preserved; see arena/timely-ward/verification-summary.json
- `Toxic Deluge` - toxic-deluge profile; NeoForge 1.0.25 native gate 127/127, prior Commander regressions 1368/1368 including shared Decree preview, and deadlines 20/20; custom seeds 96561/96562: 26 paid/resolved/settled casts at X=1/2/3, 92 opposing vs 4 friendly native deaths including 46 indestructible; four off controls zero; minimum-X five-life reserve and conservative uncertainty holds
- `Trading Post` - trading-post opt-in repair; NeoForge 1.0.25 gate 1860/1860 (60 Post checks); native target selection and library safety restored, no global authorization cache; fresh custom seeds 97021/97022 plus Wayfinder return scenario 97041 and profile-off control; see arena/trading-post-gaze/verification-summary.json
- `Tragic Arrogance` - tragic-arrogance profile; native gate 132/132 with independent survivor oracle plus prior-profile regressions 296/296; custom seeds 96121/96122: 4 paid/resolved casts, 37 opposing vs 6 friendly native sacrifices including 12 indestructible; actual survivors independently optimal and both off controls zero
- `Treacherous Terrain` - treacherous-terrain opt-in native opponent-land damage and basic landcycling; NeoForge 1.0.27 gate 4847/4847 across 44 suites (234 new plus 4613 prior), unchanged three-check baseline and 20 deadline checks; native eight-total or lethal value, five-life allied reserve, first/second-main scarce-land cycling, native basic color fixing/search restrictions and colored/taxed payment, fresh pre-cost rechecks, uncertain damage/search/discard engines deferred; six paired custom Commander observations seeds 115011/115012/115021: 15 paid/resolved original casts deal exactly 350 opposing damage, four cycles discard exact sources and find four basics with four native shuffles and no draws, all three default controls zero Terrain use; 555 card-use checks, three clock-limited controls, no completion/winner/strength claim; see arena/treacherous-terrain/verification-summary.json
- `Twilight Mire` - arena/filter-lands gate 10/10 + sim 0/0/0 casts off, 71/15/6 on
- `Viscera Seer` - viscera-seer opt-in doomed-creature scry; NeoForge 1.0.25 gate 2209/2209 (119 new plus 2090 prior checks), including unchanged Greater Good policy against extracted shared guards; custom seeds 101011/101012 removal and 101021 combat: 7 creature casts, 11 paid/resolved scries (7 removal responses, 3 blockers, 1 attacker; 2 self-sacrifices), 5 top and 6 bottom native choices; both off controls zero; 164 card-use checks; exact sacrifice binding and fresh preference recheck, no policy RNG or pre-activation library-order inspection; see arena/viscera-seer/verification-summary.json
- `Wall of Limbs` - arena/wall-of-limbs gate 12/12 + sim 0 casts off, 15 on
- `Wild Growth` - arena/adaptive-enchantment gate 76/76; Commander seed 95318 cast, normal completion; bounded mana/target policy
- `Winter, Cynical Opportunist` - winter-cynical-opportunist opt-in returns the highest-valued legal permanent at delirium end step, selects minimal nonpermanent type support to meet four types, preserves native effect/finality; NeoForge 1.0.28 gate 5733/5733 across 57 suites (13 new plus 5720 exact); installed guard 21,729 hashes, zero changed; paired seeds 131011/131012 off zero casts/triggers, on one cast each with attack and delirium trigger use, zero exceptions or clock limits; card-use evidence only; see arena/winter-cynical-opportunist/README.md and verification-summary.json
- `Wooded Bastion` - arena/filter-lands gate 10/10 (whole filter land cycle)

## Top blocked cards remaining

| Card | Decks |
| --- | --- |
| Profane Command | 4 |
| Wild Ricochet | 4 |
| Strionic Resonator | 4 |
| Oblivion Stone | 4 |
| Disciple of Bolas | 4 |
| Reins of Power | 4 |
| Cataclysmic Gearhulk | 4 |
| Nightmare Unmaking | 3 |
| Izzet Chemister | 3 |
| Illusionist's Gambit | 3 |
| Epic Experiment | 3 |
| Black Sun's Zenith | 3 |
| Curse of Chaos | 3 |
| Willbender | 3 |
| Synthetic Destiny | 3 |
| Phyrexian Delver | 3 |
| Phyrexian Reclamation | 3 |
| Past in Flames | 3 |
| Bane of the Living | 2 |
| Disaster Radius | 2 |
| Dreadship Reef | 2 |
| Shielded by Faith | 2 |
| Perpetual Timepiece | 2 |
| Champion of Stray Souls | 2 |
| Dawnglare Invoker | 2 |

## Decks with zero remaining blocked cards

- 40K - Forces of the Imperium
- 40K - Necron Dynasties
- 40K - Tyranid Swarm
- AFC - Aura of Courage
- BLC - Animated Army
- BLC - Family Matters
- BLC - Squirreled Away
- BRC - Urza's Iron Alliance
- C17 - Feline Ferocity
- C17 - Vampiric Bloodlust
- C18 - Adaptive Enchantment
- C18 - Exquisite Invention
- C18 - Nature's Vengeance
- C21 - Lorehold Legacies
- C21 - Quantum Quandrix
- C21 - Witherbloom Witchcraft
- CMM - Enduring Enchantments
- CMM - Sliver Swarm
- CMR - Reap the Tides
- DMC - Legends' Legacy
- DRC - Eternal Might
- DSC - Death Toll
- DSC - Endless Punishment
- DSC - Miracle Worker
- EOC - Counter Intelligence
- EOC - World Shaper
- FDC - Calling All Angels
- FDC - Keen Engineering
- FDC - Reign of Dragons
- FDC - Tramplesaurus Rex
- FDC - Wretched Ranks
- FIC - Counter Blitz
- FIC - Limit Break
- FIC - Revival Trance
- FIC - Scions & Spellcraft
- HOB - There And Back Again
- LCC - Ahoy Mateys
- LCC - Blood Rites
- LCC - Veloci-Ramp-Tor
- LTC - Elven Council
- LTC - Food and Fellowship
- LTC - Riders of Rohan
- LTC - The Host of Mordor
- M3C - Creative Energy
- M3C - Eldrazi Incursion
- M3C - Graveyard Overdrive
- MKC - Blame Game
- MKC - Deep Clue Sea
- MOC - Cavalry Charge
- MOC - Tinker Time
- MSC - Avengers Assemble
- MSC - Doom Prevails
- MSC - The Fantastic Four
- MSC - Wakanda Forever
- NCC - Cabaretti Cacophony
- NCC - Maestros Massacre
- NEC - Upgrades Unleashed
- ONC - Corrupting Influence
- ONC - Rebellion Rising
- OTC - Grand Larceny
- OTC - Most Wanted
- PIP - Hail, Caesar
- PIP - Mutant Menace
- PIP - Science!
- SCD - Draconic Destruction
- SCD - First Flight
- SLD - 20 Ways to Win
- SLD - Angels They're Just Like Us
- SLD - From Cute to Brute
- SLD - Literally 99 Wastes
- SLD - Raining Cats and Dogs
- SLD - War of Whtz
- SOC - Prismari Artistry
- SOC - Witherbloom Pestilence
- TD0 - Deathdancer Xira
- TDC - Abzan Armor
- TDC - Jeskai Striker
- TDC - Mardu Surge
- TDC - Temur Roar
- TMC - Turtle Power!
- TMP - Old Border Green
- VOC - Spirit Squadron
- VOC - Vampiric Bloodline
- WHO - Blast from the Past
- WHO - Masters of Evil
- WHO - Paradox Power
- WHO - Timey-Wimey
- WOC - Virtue and Valor
- ZNC - Land's Wrath
- ZNC - Sneak Attack

