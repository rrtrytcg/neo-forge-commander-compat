# Immoral Bargain (`immoral-bargain`, NeoForge 1.0.27)

Native `AI:RemoveDeck:All` blocks normal selection. The card's `Destroy` AI computes a zero X
for this non-mana sacrifice-X script, so the generic path refuses the spell before applying its
otherwise native target ranking.

## Approved rule

- In our main phase with an empty stack, cast only when we can pay `{1}{B}{G}`, have expendable
  creatures and have opposing nonland permanents the native Destroy AI can legally remove.
- Set X to the minimum of expendable creatures and worthwhile target count. Sacrifice tokens first,
  then weakest replaceable non-token creatures, preserving the strongest non-token creature.
- Let native destruction filters and target ranking choose the opposing targets. Hold when we would
  have to sacrifice our best or only non-token creature, when no useful legal target remains, or
  when timing/payment conditions are stale.

The opt-in profile is `immoral-bargain`. The helper recovers X when native non-mana X calculation
returns zero, then caps it again after native protection and legality filters. Native Destroy AI
continues to choose targets; native costs, color payment and resolution are unchanged. The helper is
also checked before both normal spell execution and stack payment. No policy RNG, hidden information
or life payment is used.

## Verification

The native suite passes **36 checks** for X/fodder caps, native opponent target ranking and
indestructible filtering, token-first sacrifice, preservation of the best creature, real payment and
destruction, timing/target holds, stale-state cancellation and profile isolation. The baseline
reproduces the default selection block. The full gate passes **5,669 checks across 53 suites**:
5,633 prior checks at exact counts plus 36 new; the installed-file guard checked 21,729 hashes with
zero changes.

| Seed | Profile off: casts / resolved | Profile on: casts / resolved | On: targets |
| --- | ---: | ---: | --- |
| 124011 | 0 / 0 | 1 / 1 | Craw Wurm |
| 124012 | 0 / 0 | 2 / 2 | Isamaru, Hound of Konda; Craw Wurm |

All four observations finished within 120 seconds (1.6–1.9 seconds each), with zero exceptions.
Before the one cast in seed 124011 the log shows Grizzly Bears sacrificed, followed by removal of
Craw Wurm. Seed 124012 shows two cast/resolution pairs. The actors' losses later in the games are
incidental to card-use evidence and do not establish policy strength. Gate hashes and line-numbered
log witnesses are in `verification-summary.json`.

This clears **WHO - Witherbloom Pestilence**, bringing coverage to **77 / 184 decks** (91 cards
unlocked, 81 verified).

Reproduce from the repository root:

```powershell
./arena/immoral-bargain/Run-Gate.ps1 -Baseline -OutName verified-1.0.27
./arena/immoral-bargain/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/immoral-bargain/Run-Gate.ps1
./arena/immoral-bargain/Run-Exposure.ps1
python -I arena/immoral-bargain/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```

The full gate chains on `arena/doomsday-confluence/target/final-1.0.27`. Exposure uses two
purpose-built decks, seeds 124011/124012, profile off/on, 120 seconds per observation. These are
bounded native correctness and autonomous card-use checks, with no strength claim. The policy does
not estimate creature or permanent value beyond native heuristics, preserve mana for other spells,
forecast responses or protect future fodder from combat.
