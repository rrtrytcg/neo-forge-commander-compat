# Past in Flames

The installed card script uses `PumpAll` to grant flashback to instants and sorceries in the graveyard, but generic `PumpAllAi` estimates value by looking for battlefield creatures. That misses this card's graveyard payoff, while `AI:RemoveDeck:All` also keeps it out of normal play. The `past-in-flames` profile opts into a narrow `PastInFlamesAi` decision at the native PumpAll hook.

The rule considers the card only in our main phase with an empty stack, at least two eligible instants or sorceries in our graveyard, and enough currently available mana to pay Past in Flames plus one eligible spell's exact colored mana cost. X-cost spells and Past in Flames itself are excluded from the follow-up check. Native payment, flashback spell selection, legality, and resolution remain authoritative.

The native suite passes **18 checks** for the graveyard-value threshold, exact combined colored affordability, phase/stack and profile isolation, ordinary AI selection, and a real Past in Flames / Opt cast-resolution-exile sequence. The full NeoForge 1.0.28 gate passes **5,943 checks across 69 suites**: all 5,925 prior checks at exact counts plus 18 new. The installed-file guard checked 21,729 hashes with zero changes.

| Seed | Profile off: casts / resolutions | Profile on: casts / resolutions | Granted-flashback exile markers | Exceptions |
| --- | ---: | ---: | ---: | ---: |
| 142021 | 0 / 0 | 6 / 6 | 2 | 0 |
| 142022 | 0 / 0 | 2 / 2 | 1 | 0 |

The four two-player observations used the same purpose-built decks and 120-second limits. Both on-profile games cast Past in Flames, and the logs show three uses of granted flashback across the pair. These are card-use observations, not strength evidence. It clears **40K - The Ruinous Powers**; two other decks still have separate blockers.
