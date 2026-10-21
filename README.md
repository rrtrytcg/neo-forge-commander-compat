# NeoForge Commander Compatibility

Opt-in AI support for Commander cards and abilities that NeoForge's stock AI filters or evaluates poorly. This repository is a focused source-and-test bundle; it does not vendor the Forge game engine.

The compatibility snapshot is pinned to NeoForge 1.0.28. The sealed cumulative gate is 5,943 checks across 69 suites. The standalone runner recompiles checked-in fixture sources and opt-in source overlays and runs 5,944 checks; Choice Wipes includes one extra regeneration-shield assertion beyond its sealed precompiled suite. It asserts the expected count for every suite. These are native decision and rules-correctness checks; paired seed games recorded in the card labs demonstrate card use and resolution, not AI strength.

## One-command native test

Use PowerShell 7 on Windows with NeoForge 1.0.28 installed. From a clone of this repository:

```powershell
./scripts/Test-CommanderCompat.ps1 -NeoForgePath 'C:\Games\NeoForge'
```

The runner downloads the matching sealed test engine from the latest GitHub release when needed, obtains pinned Temurin JDK 17, and runs all 69 native suites. Use `-Suite PastInFlamesTests` for a focused run. The first run copies NeoForge resources (about 280 MB) into an ignored, disposable `target/` workspace. It validates the installed AI jar hash and never writes to the supplied install.

The release test engine is the sealed NeoForge 1.0.28 classpath candidate. Checked-in AI overlays and Java fixtures are compiled against it at test time. `project-manifest.json` records the engine hash, supported install hash, overlay sources, per-suite expected counts, profiles, and verification tag.

## Gameplay evidence

Each card lab includes its approved decision rule, native fixtures, and purpose-made exposure decks/scripts where available. Paired profile-off/profile-on runs are preserved as evidence. Some historical exposure and refresh commands require the full NeoForge development checkout and sealed build artifacts; `scripts/Test-CommanderCompat.ps1` is the supported portable test path for this standalone repository.

## Project structure

- `source/` contains the Java overlays used by the opt-in profiles.
- `profiles/` contains the profile property files.
- `arena/` contains card-level fixtures, exposure decks/scripts, evidence summaries, shared gates, coverage, and the handoff.
- `scripts/Test-CommanderCompat.ps1` runs the native test gate in an isolated workspace.
- `build/` is ignored; the matching test-engine JAR is distributed as a GitHub release asset.

Each profile is opt-in; default AI behavior remains separate. The test runner writes only under this repository's ignored `target/` directory and leaves the supplied NeoForge installation unchanged.

## Support and licensing

This is an unofficial community project for NeoForge 1.0.28 and is not affiliated with Card Forge or Wizards of the Coast. The source overlays remain under GPL-3.0-or-later, consistent with the upstream Forge project. See `LICENSE` and upstream notices in the source files.
