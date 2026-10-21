# Easy cards 3 (profile `easy-cards-3`, NeoForge 1.0.27)

Eight cards that were each the **only** blocked card in a Commander precon. Native defect shown by
`-Baseline`: the `AI:RemoveDeck` block stops every one of them from ever being cast or activated.

| Card | Deck it clears |
| --- | --- |
| The Prismatic Piper | SLD Literally 99 Wastes |
| Cave of Temptation | 40K Tyranid Swarm |
| Throes of Chaos | SOC Prismari Artistry |
| Masterful Replication | MOC Tinker Time |
| Call for Aid | LTC Riders of Rohan |
| Marauding Raptor | LCC Veloci-Ramp-Tor |
| God-Eternal Bontu | EOC World Shaper |
| Esix, Fractal Bloom | C21 Quantum Quandrix |

## Rules

- **The Prismatic Piper**: native cast only (its other tag only affects deck generation).
- **Cave of Temptation**: sorcery speed, with 8 or more lands in play and no land in hand; the two
  counters go on our best creature by native evaluation.
- **Throes of Chaos**: cast from hand in our first main (cascade is native). Retrace from the graveyard
  only in a main phase with 6 or more lands and a land in hand to discard.
- **Masterful Replication**: at the end step of an opponent's turn, always the two 3/3 Golems; the
  clone mode is never chosen.
- **Call for Aid**: first main only. Pick the opponent with the most borrowed power, and cast only if
  the borrowed creatures are what makes the attack lethal on a *different* opponent (their untapped
  creatures absorb our biggest attackers; if our own creatures are already lethal it holds).
- **Marauding Raptor**: native cast; while one is in play, creature spells with toughness 2 or less that are
  not Dinosaurs are held, because the Raptor deals 2 damage to each creature we play.
- **God-Eternal Bontu**: the enter trigger sacrifices our tokens first, then lands beyond the seventh, at
  most three permanents, and draws that many; it sacrifices nothing when nothing qualifies. The
  "third from the top" death trigger is always accepted.
- **Esix, Fractal Bloom**: accept the first-token replacement only when we control a nonlegendary
  creature other than Esix, and copy the one with the best native evaluation.

Costs, targeting legality, payment and resolution stay native. Code: `forge-ai/.../EasyCards3Ai.java`,
hooked by one line each in `SpellAbilityAi.canPlayWithSubs` and `doTriggerNoCostWithSubs`, and in
`PlayerControllerAi` at `confirmReplacementEffect`, `chooseSingleEntityForEffect`,
`choosePermanentsToSacrifice` and `confirmAction`. Default AI and other profiles are unchanged.

## Verification

The final private candidate (Easy Cards 2 1.0.27 head + this overlay) passes **5,317 native checks across 49
suites**: all 5,250 prior checks at their exact counts plus 67 new. The new checks cover each rule's use
and every hold condition, normal AI selection, real payment and resolution (Piper enters as a 3/3; the
Cave sacrifices itself for two counters; Throes cascades and, with the land drop used, the AI retraces it
by discarding a Forest; Replication makes two 3/3 Golems; Call for Aid borrows both Craw Wurms with haste;
the Raptor damages a Hill Giant without killing it; Bontu sacrifices two tokens and a spare land for three
cards and goes third from the top when it dies; Esix turns Soldiers into Craw Wurm or Bears copies and
declines for a legendary-only or empty board), and unchanged behaviour with no profile and with
`abzan-armor`. The baseline (8 checks) shows all eight cards blocked. Installed app and resources are
unchanged.

Exposure games (`Run-Exposure.ps1`, decks in `decks/`), same seed with the profile off and on. The off
games used none of the eight cards in any seed. Call for Aid runs three-player (an extra opponent that
loses life each turn), the others two-player.

| Card | Counted | On, seeds 120011 / 120012 / 120013 |
| --- | --- | --- |
| The Prismatic Piper | cast | 4 / 6 / 1 |
| Cave of Temptation | activated | 1 / 4 / 3 |
| Throes of Chaos | cast; resolved | 4 / 6 / 7 |
| Masterful Replication | cast | 4 / 4 / 3 |
| Call for Aid | cast | 1 / 1 / 1 |
| Marauding Raptor | cast; hits on Hill Giants; hits on Goblin Pikers | 2 / 5 / 5; 0 / 8 / 6; 0 / 0 / 0 |
| God-Eternal Bontu | cast; lands sacrificed | 0 / 1 / 1; 0 / 1 / 1 |
| Esix, Fractal Bloom | cast; replacement offered | 1 / 1 / 1; 1 / 2 / 0 |

Details: the Cave targeted the same Grizzly Bears each time. Call for Aid's stack text reads "(nothing)"
because it is written before resolution; in seed 120011 the borrowed Craw Wurms attacked the life-losing
opponent the same turn (checked in the log; other seeds not individually traced). After any Raptor entered, no Goblin Piker was cast, while Hill Giants kept being cast and took 2.
Esix produced Bears copies on two offers; on one offer it copied a Goblin token because the only
better creature was frozen by a Wall of Frost (native evaluation marks it useless). Seed 120011 never cast
Bontu and seed 120013 never offered Esix a replacement. No exceptions in any game.
Card-use evidence only; repeated-card decks are not valid Commander decks; no winner or strength claim.

```powershell
$g=@{Lab='arena/easy-cards-3';Base='arena/easy-cards-2/target/final-1.0.27';Production='forge-ai/src/main/java/forge/ai/EasyCards3Ai.java','forge-ai/src/main/java/forge/ai/SpellAbilityAi.java','forge-ai/src/main/java/forge/ai/PlayerControllerAi.java';Tests='EasyCards3Tests.java';Profiles='forge-gui/res/ai/overrides/easy-cards-3.properties';OutName='final-1.0.27'}
./arena/lib/Check-Gate.ps1 @g -Baseline; ./arena/lib/Check-Gate.ps1 @g
./arena/easy-cards-3/Run-Exposure.ps1
```
