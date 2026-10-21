# Gideon Jura lab

Gideon Jura is marked `AI:RemoveDeck:All`; the native Destroy AI can choose a legal tapped creature for its `-2`, but the card is excluded from ordinary AI selection. The script also calls out missing attacker restriction logic for `+2`.

The opt-in `gideon-jura` profile admits the card and enables only `-2`: on our turn in either main phase, with an empty stack, at least two loyalty, and a legal tapped opposing creature. Native `DestroyAi` still ranks and targets the creature and pays loyalty. The profile declines `+2` and `0` because the former depends on opponent attack restrictions and the latter turns Gideon into a creature.

Hooks: `GideonJuraAi`, `DestroyAi`, `DelayedTriggerAi`, and `AnimateAi`. Native card text, loyalty costs, target legality, payment, and resolution stay intact. The native baseline passed 2 checks; focused fixtures passed 15. The NeoForge 1.0.28 full gate passed 5,735 checks across 57 suites (15 new plus all 5,720 prior checks at exact counts). The installed-file guard checked 21,729 hashes with zero changes.

Paired two-player exposures, seeds 129011 / 129012, 120-second clocks, off/on: off had zero casts and activations both seeds; on had 9 casts / 9 activations and 15 / 15, each with matching spell and destroy resolutions. Logs show the −2 destroying tapped Hill Giants and Craw Wurms. All observations finished without exceptions or clock limits. Card-use evidence only, no winner or strength claim. Full counts, log witnesses, and hashes are in `verification-summary.json`.
