# Dualcaster Mage

`AI:RemoveDeck:All` kept Dualcaster Mage out of normal AI play, and its optional copy trigger declined even when an opponent's spell was on the stack. The `dualcaster-mage` profile admits the creature and handles its copy trigger only when a legal, copyable opponent instant or sorcery is available. It binds that opponent spell as the target using the native target list; native payment, target legality and copy/original resolution remain in the engine. Own spells, abilities, uncopyable spells and empty-stack cases are held. Creature casting otherwise remains native.

The baseline fixture reproduces the declined optional trigger. The focused native suite passes **20 checks**, including profile isolation, own-spell rejection, normal card selection, a real Dualcaster cast and ETB target, and native copied/original Opt resolution.

The full NeoForge 1.0.28 gate passes **5,915 checks across 67 suites** (20 new plus all 5,895 prior checks at exact counts). The installed-file guard checked 21,729 hashes and found zero changes.

| Seed | Profile off: Dualcaster casts / Opt resolutions | Profile on: Dualcaster casts / Opt resolutions | Copied Opts / target failures |
| --- | ---: | ---: | ---: |
| 141011 | 0 / 30 | 9 / 37 | 7 / 0 |
| 141012 | 0 / 30 | 8 / 37 | 7 / 0 |

All four 120-second custom-deck observations completed with zero exceptions. On-profile copied Opt resolutions are identified by the AI controller in the log. These are card-use observations only; game winners are not strength evidence. Dualcaster Mage occurs in four decks and does not clear a deck by itself.

Reproduce from the repository root:

```powershell
$g=@{Lab='arena/dualcaster-mage';Base='arena/drawn-from-dreams/target/final-drawn-from-dreams-r1';Production=@('forge-ai/src/main/java/forge/ai/DualcasterMageAi.java','forge-ai/src/main/java/forge/ai/ability/CopySpellAbilityAi.java');Tests=@('DualcasterMageTests.java');Profiles=@('forge-gui/res/ai/overrides/dualcaster-mage.properties')}
./arena/version-compat-1.0.28/Check-Gate.ps1 @g -Baseline -OutName baseline-dualcaster-mage
./arena/version-compat-1.0.28/Check-Gate.ps1 @g -Focused -OutName focused-dualcaster-mage
./arena/version-compat-1.0.28/Check-Gate.ps1 @g -OutName final-dualcaster-mage
```

Exposure logs and summaries are under `arena/dualcaster-mage/target/exposure-final/`; gate provenance is under `target/final-dualcaster-mage-r4/`.
