# NeoForge installed-version compatibility

## 1.0.25 result — 17 October 2026

The installed application reports **NeoForge 1.0.25**, built 2026-10-16, Forge 2.0.16-SNAPSHOT.
The unchanged Forge version string is insufficient to identify the engine. Its release workflow pins
official Forge commit `64569999b8b99dd7176ab9d3e1e6008f3ab68590`:
[release workflow](https://github.com/AdrianLopez98/NeoForge/blob/v1.0.25/.github/workflows/macos.yml).

The installed files were read only. No application, user data, original source card script, preset
deck, frozen jar or unrelated research file was updated by these checks. In-place app updates are
supported by the [author's instructions](https://github.com/AdrianLopez98/NeoForge/blob/v1.0.25/README.md):
the Windows archive does not contain `datos`.

### What changed

- Against frozen Classic bytecode: core has four added deadline-tracker classes; game has seven
  changed classes; AI has six changed classes; GUI has 46 changed and 13 added classes. Class
  differences are inventory evidence, not behavioral claims.
- Packed card definitions versus our source: **34,070 identical**, 16 changed, two additions.
  **None of the 36 coverage-registry cards changed**, and no AI-removal flags or AILogic hints changed
  among the 16 changed scripts. The installed Commander inventory is still **184 decks**.
- New cooperative deadlines affect spell evaluation, attacks and blocks. Four source files overlap
  existing arena hooks: `AiAttackController`, `AiBlockController`, `AiController`, `ComputerUtil`.
  The first three need reconciliation; ComputerUtil's upstream change is a comment.

### Adaptation

`Check-NeoForge.ps1` creates a private candidate from the installed core/game/AI/GUI classes, the
desktop harness/dependencies, and freshly compiled arena policy sources. Four overlapping classes
start from the pinned new upstream source and receive `neoforge-1.0.25-hooks.patch`. It preserves the
existing opt-in hooks while retaining new native deadline handling. A deadline-expired spell search
sets the existing interruption flag so after-pass policies cannot treat cancellation as a true pass.
`hook-sources.json` rejects a stale reconciliation when any of those four local hook sources changes.

Installed assets are copied into an isolated test runtime and our opt-in profiles copied over its AI
directory. The copied `forge.profile.properties` points only inside that runtime. Native module
classes, source inputs, candidate, test files and logs are hashed. No legacy runner or its recorded
inputs has been rewritten. This lane validates the committed source snapshot; for a new uncommitted
helper, explicitly extend its source/test list or run after committing that helper. A newly referenced
missing helper fails compilation; it is never silently substituted.

There is **no card-policy rewrite needed** for the current 36 registered cards. The normal checkout
and frozen research baselines stay pinned; this is an additive 1.0.25 compatibility build, not a broad
research migration or a packaged NeoForge UI build. Existing non-Commander hooks are preserved in the
reconciliation, but their tactics/strength have not been revalidated on the new version.

### Verification

All **16 native Commander suites passed, 1,229 checks total**, on the same candidate:

| Suite | Checks | Suite | Checks |
| --- | --- | --- | --- |
| Adaptive Enchantment | 76 | Land-mana family | 349 |
| Ahoy Mateys | 77 | Beast Within | 13 |
| Curse of the Swine | 36 | Filter lands | 10 |
| Psychosis Crawler | 6 | Wall of Limbs | 12 |
| Slaughter the Strong | 18 | Mirror Entity | 48 |
| Decree of Pain | 69 | Oreskos Explorer | 100 |
| Dispatch | 61 | Tragic Arrogance | 132 |
| Marshal's Anthem | 135 | Darksteel Mutation | 87 |

The unchanged upstream `AITimeoutTrackerTest` passes **11 tests**. Nine additional native integration
checks verify normal spell-search control, deterministic deadline expiry, interruption flag, session
cleanup, next-search recovery, different keys after game rollover, no prior-game contamination, no
thread-local deadline leak, and unchanged default card admission. The deliberate expired search can
print `TimeoutException`; it is expected only in that fixture. Probe diagnostics are checked separately.

Eight autonomous custom-deck probes reuse the committed Darksteel Mutation observer, decks and
evidence checker. Generated adapters change only candidate/runtime locations. Seeds 96341/96342,
20-second observation clocks, four players, original scripts, no forced gameplay:

| Mode | Profile | Seed 96341 casts | Seed 96342 casts |
| --- | --- | --- | --- |
| Indestructible threats | on | 4 | 5 |
| Indestructible threats | off | 0 | 0 |
| Small utility creatures | on | 4 | 7 |
| Small utility creatures | off | 0 | 0 |

**20 paid/resolved casts**: nine Darksteel Juggernauts and eleven Llanowar Elves. Every matched cast
has native payment, actual attachment, base/final 0/1 in these decks, artifact/Insect types,
indestructibility and removal of original abilities. Four off controls cast none. Clock stops,
pending actions, late observations and diagnostics are retained in the JSON. These are representative
card-use witnesses, not completed Commander games or strength evidence. Historical witnesses for the
other cards remain historical; the new engine has current native correctness coverage for all suites.

Coverage remains **27 clear decks, 26 verified, 10 unlocked-only**, with 337 distinct blocked cards
and 541 blocked occurrences remaining. No registry status was promoted by this version audit.

## Reproduce and extend

```powershell
git fetch --no-tags --depth=32 origin 64569999b8b99dd7176ab9d3e1e6008f3ab68590
./arena/version-compat/Audit-Installed.ps1
./arena/version-compat/Check-NeoForge.ps1
./arena/version-compat/Check-Timeouts.ps1
./arena/version-compat/Run-CommanderProbes.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

Use a fresh `-Label` for a changed installed asset snapshot; the runner rejects a changed card ZIP
in an existing label. The current lane rejects an installed version other than 1.0.25. For a future
release: determine its official Forge pin, compare scripts/decks/registered cards, reconcile only the
overlapping hooks against that pin, update the guarded hook hashes, extend the native suite list for
new card work, and record new native and autonomous-use evidence before declaring compatibility.
Do not replace frozen baselines or rewrite old manifests to make a new version look already verified.

Machine evidence: [installed-audit.json](installed-audit.json),
[verification-summary.json](verification-summary.json), [card-use-evidence.json](card-use-evidence.json).
Private sources, candidate jars, copied assets, native logs and probe adapters are ignored under
`target/neoforge-1.0.25/`. The progress report links the last recorded compatibility snapshot;
that link does not automatically certify later source changes or later NeoForge versions.
