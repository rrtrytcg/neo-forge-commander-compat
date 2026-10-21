# Divine Reckoning

`AI:RemoveDeck:All` keeps Divine Reckoning out of normal play. The native `ChooseCardAi` admission accepts it without evaluating the table-wide creature losses, while each AI player already chooses its best creature to keep.

## Rule

- Consider it only in our second main phase with an empty stack, preserving native timing and mana payment.
- Forecast each AI player's native choice using `ComputerUtilCard.getBestAI` over that player's creatures. Hold when a seat is human-controlled because its choice is unknown.
- Count the evaluated value of creatures that would be destroyed, excluding each chosen creature, creatures that cannot be destroyed, shielded creatures and creatures with shield counters. Cast only when opposing creature loss value is strictly greater than friendly loss value.
- Keep the native per-player choices, destroy effect and flashback behavior.

## Verification

The baseline suite passes 2 checks: the native `ChooseCardAi` decision accepts the equal-loss board without evaluating consequences, and `AI:RemoveDeck` keeps the card out of ordinary selection. The focused native suite passes 15 checks for a favorable forecast, normal spell selection, real four-mana payment, each player's native kept creature and destruction, equal/friendly-loss holds, second-main timing, affordability and profile isolation.

The full NeoForge 1.0.28 gate passes **5,835 checks across 63 suites** (15 new plus all 5,820 prior checks at exact counts). The installed-file guard checked 21,729 hashes and found zero changes.

| Seed | Profile off: casts / resolutions | Profile on: casts / resolutions |
| --- | ---: | ---: |
| 137011 | 0 / 0 | 4 / 4 |
| 137012 | 0 / 0 | 6 / 6 |

All four two-player custom-deck games exited successfully within the 120-second clocks with zero exceptions. The on-profile logs confirm repeated casts and resolutions; native fixture checks establish the exact creature choices and destruction. These are card-use observations only, not winner or strength evidence. The lab clears **C17 - Feline Ferocity**, bringing coverage to **87 / 184 clear decks**. See `verification-summary.json` for hashes and commands.

Reproduce from the repository root:

```powershell
./arena/divine-reckoning/Run-Gate.ps1 -Baseline -OutName baseline-divine-r2
./arena/divine-reckoning/Run-Gate.ps1 -Focused -OutName focused-divine-r1
./arena/divine-reckoning/Run-Gate.ps1 -OutName final-divine-r1
./arena/divine-reckoning/Run-Exposure.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```
