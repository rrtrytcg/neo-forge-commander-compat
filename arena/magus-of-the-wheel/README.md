# Magus of the Wheel

Opt-in policy for the self-sacrificing wheel. Activate in our second main phase with an empty stack only when our hand has at most two cards, our library has at least seven, and every opponent has at least four cards in hand. Preserve the printed `{1}{R}`, tap, and sacrifice costs and the native table-wide discard/draw effect.

Native `DiscardAi` broadly accepts the activation, but its inherited cost evaluator rejects the favorable use because it overvalues sacrificing the 3/3 creature. Baseline fixtures reproduce that refusal and normal deck-selection blocking (2 checks). The profile-specific `DiscardAi` hook admits the approved state and overrides only the generic sacrifice-value veto; it still checks that the full printed cost is affordable and lets native payment and resolution choose/pay everything. The focused suite passes 22 checks for native selection/payment and seven-card draws, plus holds for hand/library/opponent thresholds, mana, timing, stack, and profile isolation.

The cumulative NeoForge 1.0.28 gate passes **5,820 checks across 62 suites** (22 new plus all 5,798 prior checks at their exact counts). The installed-file guard checked 21,729 hashes with zero changes.

Paired two-player custom-deck exposures used seeds 136051, 136052 and 136053 with 120-second clocks. Profile off recorded zero casts and activations on every seed. Profile on recorded 13 casts / 6 activations, 6 / 0, and 28 / 11 respectively. All six games exited successfully with no exceptions or clock caps. Seed 136052 did not reach an activation opportunity before the game ended. These observations show card use only, not strength or winner claims. The lab clears **NCC - Cabaretti Cacophony**. Exact logs and hashes are in `verification-summary.json`.
