# Darksteel Mutation opt-in

Enable with `--ai-profile=darksteel-mutation`. The profile admits the card during play and enables
the narrow `AttachAi` target-choice branch. The original card script, ordinary Aura timing,
targetability, attachment legality, payment and resolution remain native. Other profiles and the
default AI retain their original behavior. All builds and probes use private arena outputs; the
installed application and original preset decks are untouched.

## Rules

1. Consider the native legal/attachable creature list across **all opponents**, excluding self/allies.
2. Skip a creature already enchanted by Darksteel Mutation.
3. Preview the original Aura's continuous effect using LKI copies and native static-ability layers.
   A target qualifies if its combat damage drops by at least two, or its native non-PT/non-mana-value
   utility evaluation drops. The latter credits back the native evaluator's 70-point price for newly
   granted indestructibility so it measures lost utility, separately from the combat-reduction gate.
   This permits small mana creatures and activated utility creatures while rejecting plain 1/1s,
   harmless 0-power flyers and vanilla defenders.
4. Choose the highest native creature evaluation among qualifying opponents' creatures. Being a
   commander supplies no separate override. Already-indestructible creatures remain eligible.
5. Hold without a useful legal target. Preview links never call the eventful attachment API, consume
   timestamps or change zones. Native layers are restored in `finally`.

Counters and external effects remain in the preview and actual resolution: base 0/1 does not mean
final 0/1. The utility rule deliberately follows the existing bounded native evaluator; it does not
recognize every valuable triggered/static ability or forecast combos, responses, Aura removal,
sacrifices or reanimation. Unrecognized noncombat utility can therefore be conservatively passed over.
The preview pattern reuses Forge's existing LKI/static-layer approach rather than changing the rules
engine or recreating the card's transformation by hand.

## Verification

`Check-DarksteelMutation.ps1 -Label final` passed **87 native checks** in four-player constructed
fixtures, using original scripts and normal controller selection, native mana payment and stack
resolution. Coverage includes:

- empty/own/allied boards and poor targets; small utility/mana creatures;
- strongest-target choice across opponents and no automatic small-commander preference;
- already-indestructible targets, hexproof and protection from white;
- actual ability removal, artifact/Insect types, indestructibility and base 0/1;
- retained +1/+1 counters and external Glorious Anthem bonuses;
- already-suppressed abilities, already-mutated creatures, repeat-preview stability and live-state restoration;
- disappearing targets, native fizzle, Thalia tax, unavailable mana and missing white mana;
- default admission, unrelated-profile admission and unchanged original deck-building warning.

`Check-Regressions.ps1` passed **563 checks** on the identical private candidate: Marshal's Anthem 135,
Mirror Entity 48, Decree of Pain 69, Oreskos Explorer 100, Dispatch 61, Slaughter the Strong 18 and
Tragic Arrogance 132. The offline `forge-ai` reactor package build passed with zero Checkstyle violations;
generic Maven tests were skipped because these native fixture gates are run explicitly.

The gate snapshots the desktop engine and overlays the current policy classes, including recent
committed fixes that the shared jar can lag. Provenance records source/profile/test/runner and candidate
hashes. Probes freeze that tested candidate and add only a separate read-only event observer.

## Reproduce

```powershell
./arena/darksteel-mutation/Check-DarksteelMutation.ps1 -Label final
./arena/darksteel-mutation/Check-Regressions.ps1
foreach ($mode in 'threats','utility') {
    foreach ($profile in 'on','off') {
        foreach ($seed in 96341,96342) {
            ./arena/darksteel-mutation/Run-CardUseProbe.ps1 -Mode $mode -ProfileMode $profile -Seed $seed
        }
    }
}
./arena/darksteel-mutation/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

See [CARD_USE_EVIDENCE.md](CARD_USE_EVIDENCE.md), [card-use-evidence.json](card-use-evidence.json) and
[verification-summary.json](verification-summary.json). Raw logs, private jars and compiled classes
remain ignored under `target/`. Custom decks demonstrate autonomous card use; they do not establish
Commander-precon completion, winners or strength.
