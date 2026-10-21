# Confiscation Coup opt-in policy

`AI:RemoveDeck:All` blocks Coup from normal spell selection. Its native `CountersPutAi` treats the player-directed energy effect as though it needed a counter recipient, and native `ControlGainAi` declines the optional `UnlessSwitched` payment because the sorcery host is not on the battlefield.

## Rule

- Keep native sorcery timing, mana payment, target legality and gain-control resolution.
- Only consider opposing artifact or creature targets with mana value at most four, matching the four energy Coup provides. ControlGainAi retains its existing target ranking among those affordable choices.
- Pay the target's exact mana-value energy only if it is still legal, remains opponent-controlled, and the generated energy is available. Hold when no affordable target exists or the payment no longer matches the target.

The only other card script found with `GainControl` plus `UnlessSwitched` is Emberwilde Djinn; its source remains on the battlefield, so it does not share Coup's off-battlefield-source failure.

## Verification

The baseline fixture passes 4 checks, reproducing native payment refusal and the normal-selection block. The focused suite passes 13 checks. The full private **NeoForge 1.0.27** gate passes **5,734 checks across 57 suites** (13 new plus all 5,721 prior checks at exact counts). The installed 1.0.28 state differed from the 1.0.27 preservation manifest in 24 files, so the lab runner captured the active 21,729-file state before the full run and verified zero changes afterward. It did not edit the installed files or the 1.0.28 compatibility lab.

| Seed | Profile off: casts / resolutions | Profile on: casts / resolutions | Target witness |
| --- | ---: | ---: | --- |
| 128011 | 0 / 0 | 6 / 6 | Six Hill Giants captured; two Craw Wurms resolved before Coup and were not selected |
| 128012 | 0 / 0 | 6 / 6 | Six Hill Giants captured |

All four two-player custom-deck runs completed within 120 seconds with zero exceptions. Logs and hashes are recorded in `verification-summary.json`. This is card-use and target-selection evidence only; it is not a strength claim.

Confiscation Coup clears **M3C - Creative Energy**, bringing coverage to **81 / 184 decks**. Six single-blocker cards remain.

Reproduce from the repository root:

```powershell
./arena/confiscation-coup/Run-Gate.ps1 -Baseline -OutName baseline-1.0.27-r6
./arena/confiscation-coup/Run-Gate.ps1 -Focused -OutName focused-1.0.27-final
./arena/confiscation-coup/Run-Gate.ps1 -OutName final-1.0.27-current-install
./arena/confiscation-coup/Run-Exposure.ps1
python -I arena/confiscation-coup/Write-InstalledSnapshot.py verify
python -I arena/confiscation-coup/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```
