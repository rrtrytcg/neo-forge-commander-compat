# Daretti, Scrap Savant

`AI:RemoveDeck:All` kept Daretti out of normal AI play. The baseline fixture confirmed the native `DiscardAi` declines a useful +2 even with excess lands; native −2 accepts legal sacrifice and return material.

## Rule

- The opt-in `daretti` profile admits Daretti. Use +2 only in our second main when the shared discard chooser can discard at least one excess land, keeping the existing two-land / one-land thresholds. Preserve native discard choice and draw resolution.
- Use −2 only when our graveyard has a legal artifact target and we have legal artifact sacrifice material. Native target ranking, sacrifice choice, loyalty payment and resolution remain in place.
- Keep the ultimate's native `Always` decision, loyalty payment and emblem resolution.

The focused native suite passes **26 checks** for normal selection, exact costs and resolutions, the +2 timing and no-excess-land holds, the −2 missing-material holds, ultimate emblem creation, and profile isolation. The baseline suite passes **1 check** for normal-selection blocking; its log records native +2 declined and native −2 accepted with the same useful materials.

The full NeoForge 1.0.28 gate passes **5,861 checks across 64 suites** (26 new plus all 5,835 prior checks at exact counts). The installed-file guard checked 21,729 hashes and found zero changes.

| Seed | Profile off: casts / activations / ability resolutions | Profile on: casts / activations / ability resolutions |
| --- | ---: | ---: |
| 138011 | 0 / 0 / 0 | 1 / 1 / 1 |
| 138012 | 0 / 0 / 0 | 1 / 5 / 5 |

All four two-player games completed within the 120-second clocks with zero exceptions. The second on-profile game shows four +2 activations and one ultimate activation. The shared chooser traces two discarded lands in its representative turns; the stack description says “draws zero” before resolution, so use the native fixture as the evidence that two cards are drawn. These are card-use observations only, not winner or strength evidence. Daretti clears **C21 - Lorehold Legacies**; coverage is **88 / 184 clear decks**.

Reproduce from the repository root:

```powershell
./arena/daretti/Run-Gate.ps1 -Baseline -OutName baseline-daretti-r1
./arena/daretti/Run-Gate.ps1 -Focused -OutName focused-daretti-r1g
./arena/daretti/Run-Gate.ps1 -OutName final-daretti-r1
./arena/daretti/Run-Exposure.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

See `verification-summary.json` for provenance, hashes, and smoke summaries.
