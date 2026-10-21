# Selective Obliteration lab

The native `RepeatEachAi` root accepts Selective Obliteration without evaluating the sweep, and the script's `MostProminentComputerControls` chooser uses the casting AI's board for every player. The baseline fixture reproduces that mismatch: a green caster causes an opponent with a red majority and a green minority to preserve green and lose red.

The opt-in `selective-obliteration` profile keeps native sorcery timing, payment, targeting, and exile resolution. For each AI chooser, it preserves the mono-color group with the greatest total native permanent evaluation on that player's public battlefield; ties follow WUBRG order. Multicolored permanents are forecast to be exiled and colorless permanents to survive. The card is cast only when this forecast removes more evaluated permanent value from opponents than from us. The policy uses public state and no policy randomness.

`SelectiveObliterationAi` is hooked at the `RepeatEachAi` cast decision and `ComputerUtilCard.chooseColor` choice path. No card script or installed engine file was changed.

The native baseline passed 3 checks and the focused fixture passed 16, including the native sorcery-timing hold. The final NeoForge 1.0.28 full gate passed 5,736 checks across 57 suites (16 new plus all 5,720 prior checks at exact counts); the installed-file guard checked 21,729 hashes with zero changes. The final sealed candidate is `target/final-1.0.28-r2`.

Paired two-player exposure games used seeds 129011 and 129012 with the same custom decks and 120-second clocks in both modes. Profile off resolved 0 cards on each seed; profile on resolved 1 and 2 respectively. All four games exited cleanly with zero exceptions and no clock limit. These are card-use and resolution observations only, with no winner or strength claim. Counts, witness lines, and hashes are in `verification-summary.json`.
