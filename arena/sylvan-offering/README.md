# Sylvan Offering lab

The native `ChoosePlayerAi` always approved Sylvan Offering and picked the first legal opponent, even when the symmetric gift had no upside and another opponent had fewer creatures. The opt-in `sylvan-offering` profile only casts in our second main phase with an empty stack, a positive X no greater than two, and an asymmetric advantage over the chosen opponent from a creature anthem or token multiplier. At each native player choice it selects the opponent with the fewest creatures, with player ID breaking ties. Native payment, timing legality and token resolution remain authoritative.

The policy is in `SylvanOfferingAi`; `SpellAbilityAi` gates the spell decision, and `ChoosePlayerAi` applies the least-creature choice only under the matching profile. Baseline fixtures reproduce the permissive decision and first-opponent choice. The focused suite passes 22 checks covering profile isolation, timing, stack and mana holds, anthem and token-multiplier comparisons, normal AI selection, X capping, native payment, and native token replacement/resolution.

The cumulative NeoForge 1.0.28 gate passes **5,767 checks across 59 suites** (22 new plus all 5,745 prior checks at their exact counts). The installed-file guard checked 21,729 hashes with zero changes.

Paired three-player custom-deck exposures used seeds 133011 and 133012 with 120-second clocks. Off: zero Sylvan Offering casts on both seeds. On: three casts on each seed. All four games exited cleanly without exceptions or clock caps. These games show card use only; the native fixture verifies the least-creature choice and native token outcomes. No winner or strength claim is made. Exact logs, hashes, and commands are recorded in `verification-summary.json`.
