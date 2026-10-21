# Utility lands (profile `utility-lands`, NeoForge 1.0.27)

Four lands that were each the **only** blocked card in a Commander precon:

| Land | Deck it clears |
| --- | --- |
| Labyrinth of Skophos | MKC Blame Game |
| HELIOS One | PIP Science! |
| Thespian's Stage | WHO Timey-Wimey |
| Dakmor Salvage | M3C Graveyard Overdrive |

The `AI:RemoveDeck` block never stopped these lands being played or tapped for mana; it hid their
utility abilities. Native defects (shown by `-Baseline`): `RemoveFromCombatAi` refuses every
activation, HELIOS never uses its banked energy to destroy anything, and dredge is accepted at
every opportunity, even with seven or more lands in play (it then mills the land it would have drawn).

## Rules

- **Labyrinth of Skophos**: opponent's turn, after blockers. Remove the biggest unblocked attacker
  hitting us or our planeswalker if it has power 4+ or the unblocked damage to us is lethal; needs
  four other untapped lands. Never our own combat, never another player's defence.
- **HELIOS One**: take energy at the end step of the player right before us, with one other untapped
  land. In our main phase, destroy the best opposing nonland permanent (native evaluation) with
  3 <= mana value <= energy, keeping five other lands. Never our own permanents.
- **Thespian's Stage**: once (while it is still a plain Stage), in our second main with two other
  untapped lands, copy a basic land of our scarcest commander colour; our own basic first, otherwise
  any legal one. Nonbasic and legendary lands are never copied.
- **Dakmor Salvage**: dredge only with no land in hand, fewer than seven lands in play and 20+ cards
  in library; otherwise draw normally.

Costs, targeting legality, payment and resolution stay native. Code: `forge-ai/.../UtilityLandsAi.java`,
hooked by one line in `SpellAbilityAi.canPlayWithSubs` (activated abilities) and one in
`PlayerControllerAi.confirmReplacementEffect` (dredge). Default AI and other profiles are unchanged.

**Deferred: Crucible of the Spirit Dragon.** Native AI payment cannot spend storage-counter mana at
all (fixture `deferral reason`), so storing counters would be pointless. It stays blocked and
unmapped; it needs payment support for X-counter mana abilities as its own job.

## Verification

The final private candidate (Treacherous Terrain 1.0.27 head + this overlay) passes **4,898 native
checks across 45 suites**: all 4,847 prior checks at their exact counts plus 51 new. The new checks
cover each rule's use and every hold condition, normal AI selection, real activation/payment/resolution
(attacker removed from combat, energy gained and paid as X with HELIOS sacrificed, Stage becomes a
Plains copy, dredge returns Salvage and mills two), the Crucible deferral reason, and unchanged
behaviour with no profile and with `abzan-armor`. Installed app/resources are unchanged.

Two-player exposure games (`Run-Exposure.ps1`, decks in `decks/`): the actor deck holds many copies of
the land; the opponent creates the situation (big and small attackers; MV2-6 permanents; Plains to
copy; an ordinary opponent while the land-light actor self-mills). Same seed with the profile off and on.

| Land | Seed | Counted | Off | On |
| --- | --- | --- | ---: | ---: |
| Labyrinth | 117011 | remove | 0 | 1 |
| Labyrinth | 117012 | remove | 0 | 3 |
| Helios | 117011 | energy, destroy | 0 / 0 | 8 / 2 |
| Helios | 117012 | energy, destroy | 0 / 0 | 10 / 3 |
| Stage | 117011 | copy, commanderCast | 0 / 0 | 7 / 1 |
| Stage | 117012 | copy, commanderCast | 0 / 0 | 1 / 0 |
| Dakmor | 117011 | dredge | 7 | 1 |
| Dakmor | 117012 | dredge | 5 | 4 |

Targets: Labyrinth only ever removed 6-power Craw Wurms; HELIOS destroyed Gray Ogre (MV3) x3 and
Hill Giant (MV4) x2, never an MV2 Bear; Stage copied Plains x6 (enabling its white commander) and
Forest x2 after earlier Plains copies had made green the scarcer colour. Dakmor: default AI dredges
at every chance; the rule dredges only when a land is needed. No exceptions in any game.
Card-use evidence only; repeated-card decks are not valid Commander decks; no winner or strength claim.

```powershell
$g=@{Lab='arena/utility-lands';Base='arena/treacherous-terrain/target/final-1.0.27';Production='forge-ai/src/main/java/forge/ai/UtilityLandsAi.java','forge-ai/src/main/java/forge/ai/SpellAbilityAi.java','forge-ai/src/main/java/forge/ai/PlayerControllerAi.java';Tests='UtilityLandsTests.java';Profiles='forge-gui/res/ai/overrides/utility-lands.properties';OutName='final-1.0.27'}
./arena/lib/Check-Gate.ps1 @g -Baseline; ./arena/lib/Check-Gate.ps1 @g
./arena/utility-lands/Run-Exposure.ps1
```
