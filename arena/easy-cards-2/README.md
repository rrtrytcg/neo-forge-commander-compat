# Easy cards 2 (profile `easy-cards-2`, NeoForge 1.0.27)

Seven cards that were each the **only** blocked card in a Commander precon. Native defect shown by
`-Baseline`: the `AI:RemoveDeck` block stops every one of them from ever being cast or activated.

| Card | Deck it clears |
| --- | --- |
| Autumn Willow | TMP Old Border Green |
| Edge of Autumn | TD0 Deathdancer Xira |
| Knollspine Dragon | LTC The Host of Mordor |
| Bosh, Iron Golem | C18 Exquisite Invention |
| Ashling the Pilgrim | DMC Legends' Legacy |
| Benevolent Offering | VOC Spirit Squadron |
| Peel from Reality | CMR Reap the Tides |

## Rules

- **Autumn Willow**: native cast; the `{G}` shroud-dropping ability is vetoed (never worth mana).
- **Edge of Autumn**: cast in our main phase with 4 or fewer lands and a basic land in the library;
  the sacrifice-a-land cycling is vetoed.
- **Knollspine Dragon**: cast only in our second main so combat damage counts. The enter trigger
  targets the opponent who took the most damage this turn and accepts the discard only when that damage
  is larger than the hand it throws away and the library is deeper than the draw.
- **Bosh, Iron Golem**: any time the stack is empty. Lethal on an opponent whose life is at most the
  best artifact's mana value comes first; otherwise the best opposing creature the artifacts can kill.
  The sacrificed artifact is the cheapest nonland, nontoken artifact with mana value 3 or more that
  covers the target; never Bosh itself. Payment is chosen in `AiCostDecision.visit(CostSacrifice)`.
- **Ashling the Pilgrim**: grow in our second main or at any end step, up to twice a turn. The third
  resolution (the sweep) only when it kills an opponent and we survive the damage.
- **Benevolent Offering**: our second main, when we control at least 2 more creatures than the
  opponent with the fewest. Both player choices pick the opponent with the fewest creatures.
- **Peel from Reality**: our main phase. Returns a nontoken creature of ours that has an enter trigger
  (mana value 3 or less) or carries -1/-1 counters or an opposing aura, plus the opponent's best creature,
  only if theirs evaluates higher.

Costs, targeting legality, payment and resolution stay native. Code: `forge-ai/.../EasyCards2Ai.java`,
hooked by one line each in `SpellAbilityAi.canPlayWithSubs` (spells and abilities),
`SpellAbilityAi.doTriggerNoCostWithSubs` (Dragon trigger), `AiCostDecision.visit(CostSacrifice)` (Bosh) and
`ChoosePlayerAi.chooseSinglePlayer` (Offering). Default AI and other profiles are unchanged.

## Verification

The final private candidate (Easy Cards 1.0.27 head + this overlay) passes **5,250 native checks across 48
suites**: all 5,176 prior checks at their exact counts plus 74 new. The new checks cover each rule's use
and every hold condition, normal AI selection, real payment and resolution (Willow enters with shroud;
Edge fetches a basic; Dragon draws 5 against a 2-card hand and keeps a 3-card hand against 2 damage;
Bosh sacrifices the cheapest sufficient artifact and kills a Giant or an opponent at 5 life; Ashling's
third activation sweeps 3 to everyone; Offering gives tokens to the fewest-creature opponent; Peel returns
both creatures), and unchanged behaviour with no profile and with `abzan-armor`. The baseline (7 checks)
shows all seven cards blocked. Installed app and resources are unchanged.

Two-player exposure games (`Run-Exposure.ps1`, decks in `decks/`), same seed with the profile off and on.
The off games used none of the seven cards in any seed.

| Card | Counted | On, seeds 119011 / 119012 / 119013 |
| --- | --- | --- |
| Autumn Willow | cast; activated | 1 / 1 / 1; 0 / 0 / 0 |
| Edge of Autumn | cast; cycled | 2 / 1 / 2; 0 / 0 / 0 |
| Knollspine Dragon | cast; trigger resolved | 3 / 0 / 3; 3 / 0 / 3 |
| Bosh, Iron Golem | activated | 1 / 0 / 2 |
| Ashling the Pilgrim | activated | 9 / 6 / 12 (no sweep: no lethal) |
| Benevolent Offering | cast | 2 / 2 / 3 |
| Peel from Reality | cast | 7 / 2 / 9 |

Bosh killed Grizzly Bears and Craw Wurm, sacrificing Thran Dynamo, Gilded Lotus and Coalition Relic and
never Bosh. The Dragon trigger's discards were seen in the logs. Dragon and Bosh did not come up in seed
119012 (the game ended before seven or eight mana). No exceptions in any game.
Card-use evidence only; repeated-card decks are not valid Commander decks; no winner or strength claim.

```powershell
$g=@{Lab='arena/easy-cards-2';Base='arena/easy-cards/target/final-1.0.27';Production='forge-ai/src/main/java/forge/ai/EasyCards2Ai.java','forge-ai/src/main/java/forge/ai/SpellAbilityAi.java','forge-ai/src/main/java/forge/ai/AiCostDecision.java','forge-ai/src/main/java/forge/ai/ability/ChoosePlayerAi.java';Tests='EasyCards2Tests.java';Profiles='forge-gui/res/ai/overrides/easy-cards-2.properties';OutName='final-1.0.27'}
./arena/lib/Check-Gate.ps1 @g -Baseline; ./arena/lib/Check-Gate.ps1 @g
./arena/easy-cards-2/Run-Exposure.ps1
```
