# Oreskos Explorer autonomous card-use evidence

2026-10-17. Four-player native `Match.startGame` with profile on/off controls, seeds 95901/95902,
20-second observation budget per run. The observer only reads native events and card state.

| Profile | Seed | Paid, resolved creatures | Completed ETB searches | Plains fetched | Search X values |
| --- | --- | --- | --- | --- | --- |
| on | 95901 | 6 | 6 | 13 | 0, 2, 2, 3, 3, 3 |
| on | 95902 | 7 | 6 | 11 | 0, 0, 3, 3, 3, 2 |
| off | 95901 | 0 | 0 | 0 | none |
| off | 95902 | 0 | 0 | 0 | none |

Total accepted: **13 natively paid and resolved creature casts, 12 completed search triggers,
24 fetched Plains**. Those lands include **19 Hallowed Fountains and 5 basic Plains**. Three completed
triggers had X=0, establishing autonomous body casting without search value. Native fixtures cover X=1.

Seed 95901 on finished within the observation budget. Seed 95902 on and both off controls reached
their clocks. One final search was pending at the seed 95902 on cutoff; its late resolution event is
excluded. The preceding creature's completed cast remains valid evidence. Shutdown interruptions are
recorded separately in each run summary and are not interpreted as card failures or game results.

## Deck design

Actor: 16 Oreskos Explorer, 24 Plains, 12 Hallowed Fountain, 8 Wall of Hope.
Each of three opponents: 20 Rampant Growth, 28 Forest, 12 Wall of Wood.

Repeated Explorers expose casting decisions, plentiful Plains expose both basic and nonbasic search
choices, and the opponents' ordinary Rampant Growth casts provide land-count differences. These are
deliberately nonstandard custom decks, not a Commander-precon or deck-construction validation.

## What the gate checks

- Every accepted body cast has a paired native resolution into the battlefield and native log evidence
  of two mana payments, including white, from the actor's Plains or Hallowed Fountains.
- Every accepted search belongs to an observed resolved Explorer, has a paired non-fizzled resolution,
  and takes exactly `min(X, available Plains)` from the actor's library into its hand.
- Actual card IDs, types, hand/library membership and unchanged land count are compared before/after.
- Off controls contain no Explorer casts or trigger events.
- Events after the clock-stop marker are excluded. An incomplete last search is reported separately.
- Native-test inputs, decks, observer, runner, candidate and logs are hashed; summaries link to the same
  private candidate used for the 100 native checks and both regression suites.

The native fixtures separately verify restricted searches, original shuffling, changing land counts
and unchanged default behavior. These bounded rules and observable use justify `verified` coverage;
they establish neither matchup strength nor normal Commander-precon performance.

Full snapshots and provenance: [card-use-evidence.json](card-use-evidence.json).
