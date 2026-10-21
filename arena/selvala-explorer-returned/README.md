# Selvala, Explorer Returned opt-in admission

`--ai-profile=selvala-explorer-returned` admits the original card during play. The card
script, `AI:RemoveDeck:All` deck-building hint, parley chain and default AI remain untouched.
The only AI change is profile-list parsing: double-quoted entries may contain commas, because
the card name itself does. Existing unquoted comma lists keep their exact previous behaviour,
and `Audit-Deck.ps1` now uses the same quoted-list rule when checking that the registry is wired.

## Rules

- Admission only; no new targeting, mode or activation policy.
- Selvala's `{T}` ability is a normal non-mana activated ability (the draw disqualifies it under
  CR 605.1a). The AI selects it natively when the reveal, life and mana are useful.
- The profile file uses `ignoreRemoveDeckDuringPlay="Selvala, Explorer Returned"`.

## Evidence

- NeoForge 1.0.25: **1760/1760** checks across 22 suites (13 Selvala checks plus 1747 prior
  Commander checks, including Assault Suit).
- Native fixtures cover quoted admission, default blocking, normal cast selection, native parley
  resolution (reveal, life, each-player draw, tap cost), AI selection when a green spell needs mana,
  and backward-compatible unquoted comma lists.
- Custom-deck probes, seeds 96901/96902: 2 natural casts, 23 resolved parleys, each with life gain
  equal to the revealed nonlands and exactly one draw per remaining player. Both profile-off
  controls produced zero casts and zero parleys. `Check-CardUseEvidence.ps1` validates all 155
  observations from the event stream.
- Coverage: 42 unlocked, **32 verified**, 10 unlocked-only, 0 unimplemented and 31 decks clear.

This is bounded correctness and card-use evidence, not a game-completion, winner or strength claim.

## Reproduce

```powershell
./arena/selvala-explorer-returned/Check-Selvala.ps1
./arena/selvala-explorer-returned/Check-NeoForge-1.0.25.ps1
foreach ($profile in @('on','off')) {
    foreach ($seed in @(96901,96902)) {
        ./arena/selvala-explorer-returned/Run-CardUseProbe.ps1 -ProfileMode $profile -Seed $seed
    }
}
./arena/selvala-explorer-returned/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage *> arena/selvala-explorer-returned/target/coverage-audit.log
./arena/selvala-explorer-returned/Refresh-Verification.ps1
```

`Check-Selvala.ps1` is the fast desktop-engine gate. `Check-NeoForge-1.0.25.ps1` requires the prior
Assault Suit 1.0.25 candidate and reruns every suite on that installed-engine candidate with the
new profile parser. The probes use the same private candidate and the installed 1.0.25 runtime.
