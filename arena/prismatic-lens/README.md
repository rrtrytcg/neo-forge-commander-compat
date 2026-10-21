# Prismatic Lens: opt-in native mana filtering on NeoForge 1.0.25

Use `--ai-profile=prismatic-lens`. The original card script and default AI remain unchanged.

## Rules and implementation

- Cast the original two-mana artifact using native permanent/mana-rock selection.
- Let native payment select free colorless production or a needed colored conversion.
- `{1}, {T}: Add one mana of any color` consumes another funding source or suitable floating mana. It does not add net mana. Native payment excludes the other ability of the tapped Lens.
- Preserve native colored/colorless requirements, cost adjustments, activation prohibitions, source reservations, timing, legality and untapping.

`AiOverrideProfile.oneForOneManaFilters` is an explicit card list. `ComputerUtilMana.isFilterManaAbility` recognizes only a listed, plain Mana ability with no subability, exactly one generic mana and a tap as its only costs, literal one-mana `Any` output, and no special/combo output. The existing filter prediction, source accounting, color choice, activation funding and actual payment code are reused. No card-name conditional or alternative payment engine was added.

The production profile admits only Prismatic Lens. A temporary fixture-only profile inside the isolated runtime demonstrates the same mechanic on Prophetic Prism, without unlocking Prism in production. Its entry draw and casting policy remain deferred. Two-mana activation costs, multi-mana output, sacrifices, chosen/combo outputs and untapped filters remain outside this new capability; existing supported filter behavior is preserved.

## Native evidence

`Check-NeoForge-1.0.25.ps1` overlays only `AiOverrideProfile` and `ComputerUtilMana` onto the immutable Call the Coppercoats 1.0.25 candidate. All outputs and copied assets live under this directory's ignored `target/`; it never writes to the installed app.

- 161 new checks, plus all 2725 previous checks: **2886 checks across 32 suites**.
- Five actual colored payments; native two-Lens GG payment with separate funding; no self-funding, no reuse or invented net mana.
- Genuine colorless requirements, direct matching-color payment, insufficient mana, tapped Lens, untapping and reuse.
- Native Thalia spell tax and Stony Silence activation prohibition.
- Floating-mana funding and exact native permanent/draw resolution.
- Default and unrelated-profile behavior, original warning retained, explicit per-card boundaries and excluded mechanical shapes.

## Custom-deck evidence

Read-only event observer in autonomous four-player Commander matches. Repeated-card decks deliberately make casting and both payment modes observable; they are not legal preconstructed decks. The actor has Wastes, Lens and ordinary creatures requiring different colors or genuine colorless mana. The observer records native spell casts/resolutions and each paid mana's actual source, color and original ability output, plus tapped Lens identities. It does not call policy helpers or force gameplay.

Seeds 105011 and 105012 each run profile on and off with a 30-second observation clock. `Check-CardUseEvidence.ps1` pairs casts with resolutions, checks original Lens casts and both payment modes, excludes observer events after cutoff, and records clocks, interruptions and pending casts. These establish card use, not completed games, winners or strength.

**212 card-use checks passed.** There are 10 native paid/resolved Lens casts, 14 filtered mana payments covering all five colors, and two free colorless payments. Both on runs reuse two Lens identities across later turns after native untapping. Both off controls show zero Lens casts or Lens mana. All four observations reach their clock; there are no fizzles, pending casts at cutoff or unexpected diagnostics.

| Profile | Seed | Lens casts/resolutions | Filtered mana | Free colorless mana |
| --- | --- | --- | --- | --- |
| on | 105011 | 5 | 7 (W/B/R/G) | 0 |
| on | 105012 | 5 | 7 (W/U/B/R/G) | 2 |
| off | 105011 | 0 | 0 | 0 |
| off | 105012 | 0 | 0 | 0 |

The runner's generic `casts`/`resolved` fields count all observed actor spells; the evidence checker's `lensCasts` is the actual paired Lens count. Actual filtered payments also match a native activation with one separately paid colorless mana. Raw mana records identify the source and original `Any` or `C` ability, rather than inferring mode use from the spell name.

Coverage: **43/184 clear decks**, including newly cleared `OTC - Grand Larceny`; 52 mechanically unlocked cards = 42 verified + 10 unlocked-only. Prismatic Lens also benefits `C18 - Exquisite Invention`, which retains other blockers. Remaining blockers: 321 distinct cards / 476 occurrences.

Run from the repository:

```powershell
./arena/prismatic-lens/Check-NeoForge-1.0.25.ps1
foreach ($mode in @('on','off')) {
    foreach ($seed in @(105011,105012)) {
        ./arena/prismatic-lens/Run-CardUseProbe.ps1 -ProfileMode $mode -Seed $seed
    }
}
./arena/prismatic-lens/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage
./arena/prismatic-lens/Refresh-Verification.ps1
```

`card-use-evidence.json` and `verification-summary.json` seal inputs, candidate, raw logs, original-script equality, unchanged installed assets, coverage and unrelated-file baselines. Raw logs and build artifacts remain under `target/`.
