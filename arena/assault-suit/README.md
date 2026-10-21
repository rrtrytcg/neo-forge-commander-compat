# Assault Suit opt-in equipment policy

`--ai-profile=assault-suit` admits the original artifact during play. The original
`AI:RemoveDeck:All` deck-building hint, `Equip {3}` ability, +2/+2/haste/cannot-be-sacrificed
static, attack restriction and until-end-of-turn control effect remain untouched.

## Rules

- Equip the largest legal creature in the first non-empty tier: **trample**, then **flying**,
  then **first strike or double strike**, then any other creature.
- Size is net power, then net toughness, then the existing creature evaluation. Zero-power,
  non-attacking and otherwise useless creatures are skipped.
- Accept the opponent-upkeep handoff only when the Suit is attached and at least **three mana**
  remains available to re-equip. The equipped creature's own mana abilities are excluded, because
  control of that creature is what the trigger gives away. Otherwise decline the optional trigger.

Mana availability is the native estimate at the opponent's upkeep, so the AI's own tapped lands do
not count. Native equip cost, timing, target legality, taxes, the +2/+2 bonus and the actual
control-change effect remain authoritative. The default AI and every other card keep their previous
behavior; the hook is reachable only through the profile's `Assault Suit=AssaultSuit` mapping.

## Reproduce on NeoForge 1.0.25

```powershell
./arena/assault-suit/Check-AssaultSuit.ps1
./arena/assault-suit/Check-NeoForge-1.0.25.ps1
./arena/assault-suit/Check-Regressions.ps1
foreach ($profile in @('on','off')) {
    foreach ($seed in @(96801,96802)) {
        ./arena/assault-suit/Run-CardUseProbe.ps1 -ProfileMode $profile -Seed $seed
    }
}
./arena/assault-suit/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage *> arena/assault-suit/target/coverage-audit.log
./arena/assault-suit/Refresh-Verification.ps1
```

`Check-AssaultSuit.ps1` is the fast development gate against the desktop engine snapshot.
`Check-NeoForge-1.0.25.ps1` requires the prior Biomass Mutation 1.0.25 candidate and runs the new
21-check suite plus all 20 previous suites on the installed-engine candidate. `Check-Regressions.ps1`
reruns the older shared-hook suites against the private candidate: 671 checks including this lane.

The 21 native checks cover the complete tier order, largest-within-tier selection, exclusion of
walls/zero-power/non-attackers and opponents' creatures, the normal controller selection path, three
versus two available mana, tapped sources, the equipped mana source exclusion, profile admission,
the original deck-building warning, and default/unrelated-profile isolation. The optional handoff
was already accepted by the default AI; the profile adds the explicit three-mana gate.

## Autonomous custom-deck probes

Four-player native Constructed Matches use a repeated-card deck: 12 Assault Suits, two each of War
Mammoth, Serra Angel, White Knight and Craw Wurm, and 32 basic lands, against Walls of Wood and
Forests. The decks need not be construction-legal. Nothing forces hands, draws, mana, phases or
casts; the read-only subscriber records only events and card state.

Seeds 96801 and 96802 produced 7 paid casts, 7 paid equip activations and 7 attachments. Every equip
chose the trample War Mammoth even when larger flyers or first strikers were available. The handoff
was accepted 26 times, only with 3+ available mana, and moved control to each of the three opponents;
56 handoffs without three mana or without an equipped creature were declined. Both profile-off
controls produced zero casts, equips, attachments or handoffs. `Check-CardUseEvidence.ps1` validates
all 234 observations, including values read from the event stream rather than only the policy hook.

This is bounded correctness and card-use evidence, not a game-completion, winner or strength claim.
