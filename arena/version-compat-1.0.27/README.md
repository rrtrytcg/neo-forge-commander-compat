# NeoForge 1.0.27 compatibility audit — 19 October 2026

This is an additive compatibility lane for the Commander opt-in work through
Mirage Mirror (`899567ea`). The installed application was already 1.0.27, built
2026-10-18. Nothing here installs or launches a modified NeoForge UI.
Hull Breach remains explicitly on hold; this lane does not implement it.

## Release and installed audit

The [release](https://github.com/AdrianLopez98/NeoForge/releases/tag/v1.0.27)
advances Forge to `77b6f5ae95f7f11d5a718c52a977470131db0fe1`, from the old
`64569999b8b99dd7176ab9d3e1e6008f3ab68590` pin. The unchanged Forge version string
`2.0.16-SNAPSHOT` does not identify this engine update. The exact pin is recorded
in the [release workflow](https://github.com/AdrianLopez98/NeoForge/blob/v1.0.27/.github/workflows/macos.yml).
The public release metadata and workflow are archived alongside this report.

Against the preserved native 1.0.25 assets:

- Core has 19 changed classes; game has nine; AI has four; GUI has 52 changed
  and two added classes. Class inventories are not behavioral certification.
- 34,087 packed card scripts are identical. Only Agonizing Remorse changed: its
  graveyard choice now admits lands while its hand choice still excludes them.
  No scripts were added or removed. All **58 registered cards** are unchanged.
- All **184 Commander preset decks** are byte-identical.

Machine details: [installed-audit.json](installed-audit.json),
[card-script-changes.patch](card-script-changes.patch).

## Adaptation

No card-policy rewrite was required by the source audit. A refreshed native build
is required: the old 1.0.25 runners intentionally reject the changed installed
assets and remain historical reproduction lanes.

`prepare.py` copies the 95 committed arena overlay sources into ignored scratch
storage. It retains the earlier reconciled native deadline hooks, layers the
subsequent Commander hooks through Mirage Mirror, then applies the new upstream
changes to all five overlapping source files:

- `AiBlockController`: preserve the new empty-attacker guard and ordering fix.
- `ComputerUtilCombat`: account for actual blockers and trample when assessing
  lethal commander damage.
- `ComputerUtilMana`: retain the native last-known casting-zone prediction fix.
- `ChangeZoneAi`: retain the native commander-return decision changes.
- `TrackableProperty`: preserve the new native property ordering while retaining
  the arena's added tracked fields.

The two generated reconciliation patches are tracked. No source card, original
AI implementation, installed jar, frozen baseline or previous evidence manifest
is rewritten. The candidate starts from the installed core/game/AI/GUI classes
with the desktop harness/dependencies; only the explicitly compiled opt-in
overlays replace native classes. Assembly verification checks every native class
against either its installed bytes or its exact compiled overlay bytes.

All resources and AI profiles are copied into a private runtime. User, deck,
cache and home paths point inside that runtime. The baseline hashes cover all
21,729 installed app/resource files and the 18 unrelated untracked files present
at the start of the audit, including the concurrent Estrid untap fixtures.

## Verification and limits

The sealed [verification summary](verification-summary.json) contains the final
native, deadline, upstream-combat and representative custom-deck results.
All **4,053 Commander checks, seven additional combat checks and 20 deadline
checks passed** on the same candidate. Ten observations passed **1,294 card-use
checks**: 22 original artifact casts, 74 activations (63 offensive and 11 defensive),
32 native attacks, two native blocks and 64 later restoration witnesses. All five
profile-off controls show zero Mirror use. Six observations reach their 30-second
clock and four return before it; no pending observed actions, fizzles, late events
or unexpected native diagnostics were found.
The native card gate runs all 38 existing suites, expecting 4,053 checks; seven
additional tests distinguish the new upstream commander-blocking behavior from
the old logic. Deadline tests reuse the unchanged native 11-test suite and nine
integration checks.

Eight custom Commander observations reuse the unchanged Mirage Mirror observer
and decks, with on/off controls and the original offensive/defensive seeds.
Neither original defensive seed supplies a defensive activation on this engine.
Their initial checker failure and all eight observations are retained. A separate
on/off pair at seed 111031 uses the same actor and observer, with opposing Hill
Giants and Walls of Razors. The four-power defender admits original artifact
casting without providing an eligible offensive copy; three-power attackers
create ordinary defensive opportunities. This additional exposure uses the same
30-second clock and unchanged policy, and supplies actual defensive copies and
two native blocks. The final checker includes all ten observations.
The decks intentionally repeat cards to expose decisions. Clock-limited runs
remain card-use observations: there is no completed-game, winner or strength
claim. The existing limitation remains that a legal offensive copy does not
guarantee native AI will attack with it. Verification of this bounded profile
does not admit noncreature or complicated copying.

Historical autonomous witnesses for other cards remain historical. The current
engine has native regression coverage for their documented rules, not a new
autonomous witness for every card or a general research-policy certification.
Registry statuses stay separate from engine compatibility; no card is promoted
by this audit. Existing 1.0.25 artifacts remain unchanged.

The first native launch had a PowerShell argument-parsing error before any test
main executed. The runner now passes an explicit Java argument array; its failed
launch log is retained separately. It is not counted as a native test result.

## Reproduce

```powershell
git fetch --no-tags --depth=48 origin 77b6f5ae95f7f11d5a718c52a977470131db0fe1
python ./arena/version-compat-1.0.27/audit.py
./arena/version-compat-1.0.27/Check-NeoForge.ps1
./arena/version-compat-1.0.27/Check-Timeouts.ps1
./arena/version-compat-1.0.27/Check-Upstream.ps1
python ./arena/version-compat-1.0.27/make-adapters.py
foreach ($scenario in @('offense','defense')) {
    $seeds = if ($scenario -eq 'offense') { @(111011,111012) } else { @(111021,111022) }
    foreach ($mode in @('on','off')) {
        foreach ($seed in $seeds) {
            ./arena/version-compat-1.0.27/Run-CardUseProbe.ps1 -Scenario $scenario -ProfileMode $mode -Seed $seed
        }
    }
}
./arena/version-compat-1.0.27/Run-DefenseExposure.ps1 -ProfileMode on -Seed 111031
./arena/version-compat-1.0.27/Run-DefenseExposure.ps1 -ProfileMode off -Seed 111031
./arena/version-compat-1.0.27/Check-CardUseEvidence.ps1
python ./arena/version-compat-1.0.27/refresh.py
```

Run the build in a fresh checkout/lane if a final provenance file already exists;
the runner protects recorded evidence. `-BuildOnly` and `-RunOnly` split an initial
build and its native tests. To add future cards, explicitly extend the suite list
and expectations before claiming they were tested. Do not relabel old manifests
or overlay an old candidate onto the installed app.
