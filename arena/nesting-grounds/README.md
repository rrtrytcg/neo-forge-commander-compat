# Nesting Grounds opt-in policy

`--ai-profile=nesting-grounds` admits the original land's counter-moving activation during play.
The script, including its deck-building prohibition, stays unchanged. Its ordinary colorless mana
ability and all default/unrelated AI profiles retain their previous behavior.

## Bounded rules

- First consider moving a -1/-1 counter from our creature to an opposing creature. Require a
  positive combined native creature-evaluation gain. Prefer a lethal recipient over a nonlethal one.
  Hold a lethal transfer when public death/leave triggers, recipient static abilities or recipient
  replacements make the consequences uncertain.
- Otherwise move a +1/+1 counter between our creatures for a strict combined native evaluation
  gain, or to spread from a donor with at least two counters to a recipient with zero.
- Preserve the donor's positive toughness and survival through marked damage. Do not give a first
  +1/+1 counter to a creature with undying. Do not shuffle one counter neutrally between equal bodies.
- Other types, including shield, loyalty, lore and keyword counters, are deferred. Allied creatures
  are neither our positive-counter recipients nor opposing negative-counter recipients.

The helper coordinates the original root `Pump` donor target and its `MoveCounter` recipient.
`PumpAi` delegates only for the explicit profile and matching original ability shape;
`CountersMoveAi` validates the pair without replacing it with an independent generic target choice.
Resolution recomputes the useful supported type from the native source, target and legal options.
There is no policy RNG, global plan cache, hidden-card inspection or script-parameter mutation.

Native sorcery timing, distinct targets, targeting restrictions, counter prohibitions, one mana
plus tapping the land, replacement effects and state-based actions remain authoritative. A native
Hardened Scales replacement removes one counter from the donor and adds two to the recipient.
This is a conservative local policy, not a counter-combo or general replacement/death-chain solver.

If the board changes after payment, resolution is mandatory: when the useful type has disappeared
but another legal type remains, the native default choice is retained. Returning null or cancelling
that compulsory choice would break the original ability. This fallback does not initiate transfers
of deferred counter types. If there are no movable counters, the engine moves nothing. Native
fixtures cover counter disappearance, this mixed-type fallback, and departed source/recipient.

## Reproduce on NeoForge 1.0.25

```powershell
./arena/nesting-grounds/Check-NestingGrounds.ps1
./arena/nesting-grounds/Check-Timeouts.ps1
foreach ($mode in @('positive','negative')) {
    foreach ($profile in @('on','off')) {
        foreach ($seed in @(96671,96672)) {
            ./arena/nesting-grounds/Run-CardUseProbe.ps1 -Mode $mode -ProfileMode $profile -Seed $seed
        }
    }
}
./arena/nesting-grounds/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage *> arena/nesting-grounds/target/coverage-audit.log
./arena/nesting-grounds/Refresh-Verification.ps1
```

The runner requires the prior Toxic Deluge 1.0.25 candidate and its 18-suite, 1,495-check provenance.
It checks installed engine/asset hashes against the recorded compatibility audit, then copies the
candidate and resources into this lane's ignored `target/`. It explicitly compiles the new helper
and both shared hooks, includes the existing casualty guard, and runs the new suite plus all 18 prior
Commander suites. `-CardOnly` is a development option and cannot satisfy the probe runner's
complete-regression gate. Deadline checks
run the unchanged upstream tracker tests and compatibility integration fixtures on this candidate.
The final verifier also removes only the three opt-in hook blocks in memory and compares both
shared classes with the release's pinned official Forge source. Their remaining text must match.

The 115 native fixtures cover both coordinated transfer types; lethal preference; strict evaluation
gain and spreading; stable previews and stale-target reset; source survival including marked damage and lethal damage determined by power;
undying; multiple opponents and allies; mixed and deferred types; native hexproof/shroud and counter
prohibitions; real controller selection, separate mana/tap costs, actual stats and casualties;
Hardened Scales; sorcery timing; tapped/missing mana; profile boundaries; and resolution responses.

## Custom-deck evidence

The four-player Constructed probes deliberately use repeated cards. Positive decks naturally cast
Phantom Tiger and Grizzly Bears, with Hardened Scales available; negative decks naturally cast
Bloodied Ghost. Opponents supply Eager Cadets. The engine shuffles, draws, plays lands, casts creatures,
chooses abilities, pays costs and moves counters autonomously. No state, hands, actions, targets,
counter choices, phases or mana are forced by the observer.

The read-only subscriber records paired targets on the stack, actual counters and creature stats
after resolution, then donor/recipient survival after native state actions. The independent checker
matches one native mana payment from a different land with the tapped Nesting Grounds, checks
counter/stat arithmetic and Hardened Scales, verifies ordinary spreading and negative transfers,
and requires zero counter-moving activations in every off control. Commands, seeds, candidate,
source, decks and logs are hashed. Pending observations, clock stops, late events and shutdown
diagnostics are retained. These are card-use witnesses, with no game-completion, winner or strength
claim and no claim that the deliberately repeated-card decks satisfy deck-construction rules.

The durable progress tracker is `arena/coverage/card-status.tsv`, with generated totals in
`arena/coverage/COVERAGE.md` and `coverage.json`. Unlocked-only and verified remain separate states.
Installed files, original scripts/presets, historical evidence and unrelated work are preserved.

## Verified result - 17 October 2026

- **115 Nesting Grounds checks**, **1,495 prior Commander checks** across 19 suites, and
  **20 deadline checks** pass on the same private NeoForge 1.0.25 candidate.
- Seeds **96671/96672** produce **19 paid, resolved and settled activations**: six positive
  transfers and thirteen negative transfers. Every activation pays one native mana from a different
  land and taps its own Nesting Grounds.
- The positive transfers remove six counters from donors and add **17** to recipients, including
  native replacement by one, two and three Hardened Scales. The negative transfers remove thirteen
  donor counters and put thirteen onto opposing Eager Cadets, which die through native state actions.
  All nineteen donors survive.
- Positive probes show **2/4** activations; negative probes show **6/7**. All four matching off
  controls show zero. All eight runs have zero clock stops, pending observations, late events and
  unexpected diagnostics. Printed game results are not used as evidence.
- Coverage reaches **30 decks with zero remaining blocked cards**, newly including
  **FIC - Counter Blitz**. Of **39** mechanically unlocked cards, **29** are verified and **ten**
  remain unlocked-only. The fix covers all five inventory occurrences of Nesting Grounds.

`card-use-evidence.json` records the checked observations and payment logs;
`verification-summary.json` ties them to native/deadline results, source and asset hashes, refreshed
coverage, original-script comparison, shared-hook upstream parity and protected unrelated files.
Full logs, candidate jars and copied resources remain under ignored `target/`.
