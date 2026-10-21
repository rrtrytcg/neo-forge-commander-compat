# Odric, Master Tactician

The `odric` profile bypasses only Odric's `AI:RemoveDeck:All` play filter. The stock creature AI still chooses when to cast him. NeoForge 1.0.28 already contains the native trigger and temporary blocker-control effect, so this lab adds no card-specific AI policy.

The native fixture passes 10 checks for default and unrelated-profile isolation, filter-only profile wiring, the exact threshold (Odric plus three other attackers), and native blocker-control assignment. The full 1.0.28 gate passes **5,925 checks across 68 suites**: all 5,915 prior checks at exact counts plus 10 Odric checks. The installed-file guard checked 21,729 hashes with zero changes.

| Seed | Profile off: Odric casts / trigger resolves | Profile on: Odric casts / trigger resolves | Exceptions |
| --- | ---: | ---: | ---: |
| 141021 | 0 / 0 | 1 / 3 | 0 |
| 141022 | 0 / 0 | 2 / 0 | 0 |

Both exposure pairs used the same purpose-built two-player decks and 120-second limits. These are card-use observations, not strength evidence. Seed 141022 showed casts without the attack trigger; the native fixture covers the trigger and threshold directly.
