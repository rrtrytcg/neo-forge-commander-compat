# Phyrexia's Core

Opt-in policy for the artifact land's life ability. Activate only when life is at or below five, or when the native combat evaluator says life is in danger. Native timing, mana payment, artifact sacrifice selection and resolution remain in control. The `phyrexias-core` profile is isolated from default play.

The native `LifeGainAi` declined the legal activation at five life in the baseline fixture, and the card was blocked from normal deck selection. The focused fixture passes 17 checks: low-life and native combat-danger admission, native payment/sacrifice/effect, plus holds for safe life totals, missing artifact material, missing mana, tapped Core and unrelated profiles. Baseline passes 2 checks. The cumulative NeoForge 1.0.28 gate passes 5,798 checks across 61 suites (17 new plus all 5,781 prior checks at exact counts). The active installed-file guard checked 21,729 hashes with zero changes.

Paired two-player custom-deck games used seeds 135011 and 135012 with 120-second clocks. Profile off produced zero Core activations on both seeds; profile on produced 5 and 9. All four games exited successfully without exceptions or clock caps. These are card-use observations, not a strength or winner claim. This clears no deck by itself and removes two blocked occurrences.

The sealed gate is `arena/phyrexias-core/target/final-core-r1`; its provenance records the candidate and all suite results. Exposure decks, logs, hashes and per-seed results are retained under `arena/phyrexias-core/target/` and summarized in `verification-summary.json`.
