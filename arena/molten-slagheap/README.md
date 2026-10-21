# Molten Slagheap lab

The native mana-payment helper excludes mana abilities whose costs include mana, so Molten Slagheap's `{1}, Remove X storage counters` mode was never selected to pay for a spell. Its profile routes the ability through the existing `ManaRitualBattery.0` evaluator and admits it to native mana-source selection only while the `molten-slagheap` profile is active.

The profile uses existing castable-spell evaluation, skips spells already affordable through ordinary sources, spends only the counter shortfall after the land tap and `{1}` activation cost, and declines when no noninstant candidate or no positive shortfall exists. Spell selection, actual payment, and mana production remain native. Other cards and profiles keep the existing filter behavior.

Baseline fixtures pass 2 checks. Focused fixtures pass 12 checks for native exclusion, profile-only source admission, a castable noninstant, exact counter shortfall, native spell/payment behavior, no candidate, instant-only, ordinary-mana affordability, insufficient mana, and profile isolation. The cumulative NeoForge 1.0.28 gate passes 5,745 checks across 58 suites (12 new plus all 5,733 prior at exact counts); the installed-file guard checked 21,729 hashes with zero changes.

Paired custom-deck exposures used seeds 132011 and 132012 with 120-second clocks. Off: zero Solemn Simulacrum casts and zero counter-funded Molten mana activations on both seeds. On: seed 132011 had 4 Solemn casts and 4 counter-funded Molten activations; seed 132012 had 12 casts and 1 counter-funded activation. All four games exited without exceptions or clock caps. Logs establish card use and mana mode; no winner or strength claim. Commands, witnesses, hashes, and logs are in `verification-summary.json`.
