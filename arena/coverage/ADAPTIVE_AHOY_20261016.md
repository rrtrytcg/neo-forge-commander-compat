# Adaptive Enchantment and Ahoy Mateys coverage registration

This update registers thirteen previously blocked cards under the existing opt-in profiles.
The status is a conservative assessment of the evidence already collected; no additional games
were run to improve the labels.

| Added status | Cards |
| --- | --- |
| verified | Dawn's Reflection; Fertile Ground; Wild Growth; Rakdos Charm |
| unlocked | Creeping Renaissance; Blighted Burgeoning; Market Festival; Shimmerwilds Growth; Trace of Abundance; Utopia Sprawl; Verdant Haven; Faithless Looting; Windfall |

The three original mana Auras have native decision/payment tests and recorded casts in normally
completed Commander games. Rakdos Charm has native mode/target/consequence tests and a recorded
Commander cast. These verified labels apply to the documented bounded opt-in policies, not to
general strategic strength or every possible board state.

Creeping Renaissance, Faithless Looting and Windfall pass native decision and resolution fixtures,
but the recorded Commander smokes did not show them cast. They remain unlocked until that runtime
evidence is available. The six additional blocked Auras have verified mana mechanics, but lack a
recorded Commander cast and, where relevant, full-card strategy coverage. They also remain unlocked.

Other audited Auras without `RemoveDeck:All` are not counted as newly unlocked cards. In particular,
Wolfwillow Haven and Urban Utopia appeared in the family smoke, but already had no in-game admission
blocker. The full 42-card inventory, native support and deliberate deferrals remain in
`arena/adaptive-enchantment/family/AUDIT.md`.

Evidence:

- `arena/adaptive-enchantment/README.md`: 76 native checks; original precon Commander seed 95318,
  normal completion, turn 14, 50.535 seconds. Local log: `target/commander-95318-final.log`, showing
  Wild Growth and Fertile Ground casts.
- `arena/adaptive-enchantment/family/verification-summary.json`: 349 native family checks; Commander
  seed 95331, normal completion, turn 10, 3.869 seconds; Dawn's Reflection, Wolfwillow Haven and
  Urban Utopia cast. Includes original script and candidate provenance.
- `arena/ahoy-mateys/verification-summary.json`: 77 native checks and 76 Adaptive regression checks;
  Commander seed 95441, normal completion, turn 12, 7.738 seconds; Rakdos Charm cast.

The audit now recognizes `landManaAuras=true` using the committed native-tested family inventory,
checking each original script hash before accepting structural profile coverage. It does not add
named overrides, admit deferred cards or count native grants as new unblockings.

Coverage is the union of available opt-in card fixes. It does not mean a single combined profile is
installed or enabled: Adaptive, Ahoy and Abzan retain separate boundaries, and default AI is unchanged.
