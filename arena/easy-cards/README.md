# Easy cards (profile `easy-cards`, NeoForge 1.0.27)

Five cards that were each the **only** blocked card in a Commander precon. Native defects shown by
`-Baseline`: the `AI:RemoveDeck` block stops every one of them from ever being cast.

## Rules

- **Syr Elenora, the Discerning**: no rule; native play only (unblocked).
- **Marsh Flitter**: after blockers only, sacrifice one Goblin token (never a nontoken Goblin) when a
  3/3 changes this combat: unblocked attacker, or a single block where a 3/3 kills where a 1/1 would
  not, or survives where a 1/1 would die. Holds when already 3/3 or while its own pump is on the stack.
- **Recurring Insight**: target the opponent with the most cards in hand. Hand cast in our main phase
  needs 3+ cards; the free rebound cast needs 1+. Library must stay above draw + 5.
- **Pact of the Serpent**: always targets ourselves; names our most common creature type (alphabetical
  tiebreak, no RNG). Needs X >= 2, life after loss >= 10, library > X + 3, our main phase.
- **Suffer the Past**: opponents only. X = available mana capped by that graveyard. Cast at the end step
  of the player right before us with X >= 3, or any time it is lethal (lethal opponent first, else
  the biggest graveyard). Exiles creatures first (best first), then highest mana value.

Costs, targeting legality, payment and resolution stay native. Code: `forge-ai/.../EasyCardsAi.java`,
hooked by one line in `SpellAbilityAi.canPlayWithSubs` and the Pact type choice in
`PlayerControllerAi.chooseSomeType`. Default AI and other profiles are unchanged.

## Verification

The final private candidate (Nyx Weaver 1.0.27 head + this overlay) passes **5,176 native checks across
47 suites**: all 5,122 prior checks at their exact counts plus 54 new. The new checks cover each rule's
use and every hold condition, normal AI selection, real payment and resolution (Elenora draws and tracks
hand size; Flitter sacrifices a token and becomes 3/3; Insight draws 4 and rebounds; Pact with our chosen
type draws 3 and loses 3; Suffer pays X = 4, exiles 4 and drains 4), and unchanged behaviour with no
profile and with `abzan-armor`. The baseline (5 checks) shows all five cards blocked.

Two-player exposure games (`Run-Exposure.ps1`, decks in `decks/`): the actor deck holds many copies of the
card; the opponent creates the situation (small and large creatures; a land-light deck that keeps a full
hand; a self-milling deck). Same seed with the profile off and on.

| Card | Seed | Counted | Off | On |
| --- | --- | --- | ---: | ---: |
| Syr Elenora | 118011 | cast / draw | 0 / 0 | 1 / 1 |
| Syr Elenora | 118012 | cast / draw | 0 / 0 | 1 / 1 |
| Marsh Flitter | 118011 | cast / pump | 0 / 0 | 5 / 10 |
| Marsh Flitter | 118012 | cast / pump | 0 / 0 | 5 / 10 |
| Recurring Insight | 118011 | cast / resolved | 0 / 0 | 6 / 6 |
| Recurring Insight | 118012 | cast / resolved | 0 / 0 | 4 / 4 |
| Pact of the Serpent | 118011 | cast / resolved | 0 / 0 | 6 / 6 |
| Pact of the Serpent | 118012 | cast / resolved | 0 / 0 | 6 / 6 |
| Suffer the Past | 118011 | cast / resolved | 0 / 0 | 4 / 4 |
| Suffer the Past | 118012 | cast / resolved | 0 / 0 | 3 / 3 |

Details: Elenora is legendary, so one copy per game is expected. Every Flitter pump came after blockers,
one token per Flitter, each resolving before any further pump (an earlier build double-pumped one Flitter
while its pump was on the stack; fixed and covered by a fixture). Insight drew 4-6 per cast and rebound
casts resolved. Pact life loss matched the Zombie count (2-3). Suffer used X = 3, 5, 7, 8 against the
milling opponent with real drains (the stack text says "loses 0 life" because it is written before
resolution; the Life lines show the loss). No exceptions in any game.
Card-use evidence only; repeated-card decks are not valid Commander decks; no winner or strength claim.

```powershell
$g=@{Lab='arena/easy-cards';Base='arena/nyx-weaver/target/final-1.0.27';Production='forge-ai/src/main/java/forge/ai/EasyCardsAi.java','forge-ai/src/main/java/forge/ai/SpellAbilityAi.java','forge-ai/src/main/java/forge/ai/PlayerControllerAi.java';Tests='EasyCardsTests.java';Profiles='forge-gui/res/ai/overrides/easy-cards.properties';OutName='final-1.0.27'}
./arena/lib/Check-Gate.ps1 @g -Baseline -OutName baseline2-1.0.27; ./arena/lib/Check-Gate.ps1 @g
./arena/easy-cards/Run-Exposure.ps1
```
