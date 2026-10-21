# Adaptive Enchantment [C18] [2018] - opt-in AI rules

2026-10-16. Profile: `adaptive-enchantment`.

## Owner rules implemented

- Creeping Renaissance: count each legal permanent type in our graveyard, choose the type with the most cards, and cast only if that count is at least four. Multitype cards count once toward each matching type. Ties use a fixed type order: Artifact, Battle, Creature, Enchantment, Land, Planeswalker. Flashback uses the same rule; choice is recomputed at resolution.
- Dawn's Reflection and Fertile Ground: choose a legal basic land we control, otherwise any legal land we control.
- Wild Growth: choose any legal land we control. Equal land choices use the engine's candidate order without adding RNG.

Upstream card scripts and preset decks retain their warning tags. Default AI remains opted out. This work does not deploy to the installed NeoForge app.

## Mana support

The native predictor already sees mana triggers, but its source-to-color map omits the bonus colors. Flexible trigger colors also normally follow hand composition rather than the cost being paid.

`ManaAuraAi` adds the opted-in Auras' colors to source selection, resolves flexible predicted mana against the remaining cost, and supplies payment-specific colors while the engine activates the real land ability and resolves the real triggers. Fixed bonus output is reserved before flexible choices, including stacked Auras in either trigger order. Native targeting, restrictions, costs, activation and mana production still apply. Color context is scoped to one activation and restored afterward.

Hooks are in `ChooseTypeAi` (cast threshold), `ComputerUtil.chooseSomeType` (type choice), `AttachAi` (land choice), `ComputerUtilMana` (source mapping/prediction/payment), and `PlayerControllerAi.chooseColor` (payment-specific trigger colors). The original Abzan profile is separate.

## Verification

Run from the repository root:

```powershell
& ./arena/adaptive-enchantment/Check-AdaptiveEnchantment.ps1
```

76 native checks passed against the rebuilt arena jar. These cover the 3/4-card boundary, mixed types, opponent graveyards, multitype ties, actual return-to-hand resolution, recomputation, native flashback ability, own basic/nonbasic land selection, native payment of W G / W U / W U B using a single enchanted Plains and any-color nonbasic lands, stacked fixed/flexible triggers, insufficient mana, true colorless, tapped-source reuse, context cleanup, and no-profile behavior. Evidence and file hashes: `target/adaptive-enchantment/`.

Maven reactor package succeeded (`target/build-final.log`). The first sandboxed attempt compiled Java but failed when Launch4j could not start its bundled windres.exe (error 623); the approved retry completed. Tests are run by the native fixture above; Maven packaging uses `-DskipTests`.

Commander smoke command (from `forge-gui`, with the bundled JDK and isolated arena home):

```powershell
java '-Xmx4g' '-Djava.awt.headless=true' '-Duser.home=../arena/runtime/home' '-Dsentry.dsn=' '-Dsentry.enabled=false' `
  -cp '../arena/adaptive-enchantment/target/adaptive-enchantment-final.jar' `
  forge.view.Main sim --ai-profile=adaptive-enchantment -f Commander -n 1 -c 180 -s 95318 `
  -a Default Default -g classic classic -p classic classic -y classic classic
```

Smoke log: `target/commander-95318-final.log`. The jar is copied from the desktop target into this lab before running to avoid changes by concurrent builds; hash: `target/final-jar-hash.json`. Its result is recorded below after completion.

An earlier run directly against the shared desktop jar ended with a class-loading failure while other work was rebuilding that jar; it is not used as gameplay evidence. A subsequent private-jar run completed on turn 14 in 57.352 seconds. The final run includes the additional nonbasic-land refinement. Native rule correctness and smoke completion are not deck-strength or human gameplay acceptance evidence.

Final packaged-jar smoke: completed normally on turn 14 in 50.535 seconds (seed 95318, 180-second limit). No timeout.

## Family audit extension (2026-10-16)

See [family/AUDIT.md](family/AUDIT.md) for the complete 42-card script inventory, ten additional fixes through one structural matcher, native grant findings, deliberate deferrals, 349 family checks, 76 regression checks, and a Commander smoke with three distinct mana Auras cast. The existing profile opts in with `landManaAuras=true`; original scripts, default AI, and the Abzan profile boundaries are preserved.

## Estrid, the Masked extension (2026-10-19, NeoForge 1.0.27)

Estrid is the deck's commander and carries no `AI:RemoveDeck` tag, so this is a play-quality fix,
not a coverage unlock. The native AI had two defects, both reproduced on the unchanged 1.0.27
candidate by `Check-EstridUntap.ps1 -Baseline`:

- **+2 was never used.** `UntapAllAi.canPlay` only evaluates abilities that have a sub-ability;
  Estrid's +2 has none, so it always returned `CantPlayAi`.
- **The −1 Mask protected opponents.** `TokenAi` falls into its Flash Foliage branch and targets
  the best opposing creature, giving it umbra armor.

Opt-in rule (`"Estrid, the Masked=Estrid"` in the profile; quoted because of the comma), one loyalty
decision per turn, only in our second main with an empty stack:

- Mask our best unmasked creature (native creature evaluation) when Estrid has at least three
  loyalty and no enchanted permanent we control is tapped. Opposing permanents are never masked;
  creatures already wearing an umbra armor Aura are skipped.
- Otherwise +2: it untaps tapped enchanted permanents (mana Auras on lands, enchanted creatures
  after combat) and otherwise still gains loyalty.
- The ultimate stays native. Loyalty costs, the once-per-turn restriction, targeting legality,
  token creation, attachment and umbra armor remain native.

Hooks: one line each in `UntapAllAi.canPlay` and `TokenAi.checkApiLogic`; the rule lives in
`AdaptiveEnchantmentAi`. `UntapAllAi.java` is byte-identical to upstream 1.0.27 and `TokenAi.java`
to the reconciled 1.0.27 overlay source, so the overlay only adds these hooks. Default AI and other
profiles (checked: none, `abzan-armor`) keep the native behaviour.

```powershell
./arena/adaptive-enchantment/Check-EstridUntap.ps1 -Baseline   # native defects, unchanged candidate
./arena/adaptive-enchantment/Check-EstridUntap.ps1 -Focused    # new fixtures only
./arena/adaptive-enchantment/Check-EstridUntap.ps1             # full gate on the Deadly Tempest 1.0.27 base
```

### Results

`-Baseline` on the unchanged Deadly Tempest 1.0.27 candidate reproduces both defects (2 checks).
The full gate passes **4,484 native checks across 42 suites**: all 4,445 prior checks at their
exact counts plus 39 new Estrid checks, and all 21,729 installed app/resource hashes unchanged.
The audit's 18 "unrelated" preserved files are not rechecked: they were this fixture (rewritten)
and the stompy/aristocrats labs removed in `904ff985`.

New checks cover +2 vs Mask choice, normal native ability selection, real loyalty payment and
resolution (land untapped with Aura still attached; Mask token with umbra armor on our creature),
a native Murder against the masked creature (umbra armor saves it), best-creature choice,
already-armored creatures, two-loyalty fallback, opposing-only boards, opposing tapped enchanted
lands, once-per-turn restriction, timing/stack holds and unchanged default/`abzan-armor` behaviour.

Real-deck smoke (`Run-EstridSmoke.ps1`): unchanged C18 Adaptive Enchantment vs the other three
C18 precons, 180-second clock, same seed with the profile off and on.

| Seed | Profile | Estrid casts | +2 | Masks | Mask targets |
| --- | --- | ---: | ---: | ---: | --- |
| 114011 | off | 2 | 0 | 2 | High Priest of Penance ×2 (opponent's) |
| 114011 | on | 3 | 7 | 2 | Heavenly Blademaster, Beast token (own) |
| 114012 | off | 1 | 0 | 1 | Myr token (opponent's) |
| 114012 | on | 0 | 0 | 0 | Estrid not cast: no exposure |

No exceptions; all four games finished before the clock. Card-use evidence only, with no winner
or strength claim.
