# Harsh Mercy opt-in safety gate

`AI:RemoveDeck:All` keeps Harsh Mercy out of normal play. Native `RepeatEachAi` accepts unhandled table-wide effects without estimating their consequences. The separate baseline suite confirms that both Harsh Mercy and Selective Obliteration reach this same unconditional root decision.

## Rule

- Preserve the native sorcery timing and mana checks.
- For Harsh Mercy, predict each player's native prominent creature-type choice from the public battlefield. Hold if any player's choice cannot be forecast.
- Cast only when no creature on our team is predicted to be destroyed and at least one opposing creature is predicted to be destroyed. Indestructible creatures count as survivors; regeneration shields do not, because Harsh Mercy says those creatures can't be regenerated.
- Leave each player's type choice and the original destroy effect native.

The deterministic gate uses `ComputerUtilCard.getMostProminentType`; a seat with no determinable public creature type causes a hold. It counts only creatures that can be destroyed, and this remains a narrow board forecast rather than a combat or strategic evaluator.

## Selective Obliteration finding

The baseline confirms the same unconditional `RepeatEachAi` root decision for Selective Obliteration, but it is not included in this profile. Native color-choice evaluation reads the casting player's prominent color for every player. In a resolution fixture, this made the opponent's red permanents exile while its green permanent survived, despite the opponent's red majority. Fixing that chooser requires a separate opt-in native color-choice hook and fresh safety rules, so Selective Obliteration remains blocked.

## Verification

The baseline suite passes 4 checks across both cards. Harsh Mercy's focused suite passes 15 checks. The full sealed gate passes **5,721 checks across 56 suites** (15 new plus all 5,706 prior checks at exact counts); the installed-file guard checked 21,729 hashes and found zero changes. Native fixtures cover the baseline decision, deterministic forecast, normal selection, native payment and resolution, preserved and destroyed creatures, unsafe-board holds and profile isolation.

| Seed | Profile off: casts / resolutions | Profile on: casts / resolutions |
| --- | ---: | ---: |
| 127011 | 0 / 0 | 1 / 1 |
| 127012 | 0 / 0 | 1 / 1 |

All four two-player custom-deck games finished within 120 seconds with zero exceptions. Stack-log witnesses and hashes are recorded in `verification-summary.json`. These observations establish card use only; they do not make winner or strength claims.

Harsh Mercy clears **CMM - Sliver Swarm**, bringing coverage to **80 / 184 decks**. Selective Obliteration still blocks **M3C - Eldrazi Incursion**.

Reproduce from the repository root:

```powershell
./arena/choice-wipes/Run-Gate.ps1 -Baseline -OutName baseline-2-1.0.27
./arena/choice-wipes/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/choice-wipes/Run-Gate.ps1 -OutName final-1.0.27-r2
./arena/choice-wipes/Run-Exposure.ps1
python -I arena/choice-wipes/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```
