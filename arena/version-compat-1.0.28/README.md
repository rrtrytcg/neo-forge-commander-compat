# NeoForge 1.0.28 re-baseline — 20 October 2026

Additive lane; nothing in `version-compat-1.0.27` or any sealed gate was edited. Installed build: 1.0.28 (built 2026-10-19),
upstream Forge `77b6f5ae` -> `84f4983c1ce872932cf9a26a8bd54ca00056aae9`.

## Audit (`installed-audit.json`, `audit.py`)
- Versus the 1.0.27 preserved baseline, 21,705 of 21,729 installed files are unchanged. Changed: forge-core/game/gui/gui-mobile/gui-mobile-dev/neo jars,
  `cardsfolder.zip`, 10 language files, 6 adventure UI/language files. **forge-ai jar is byte-identical.**
- Cards: 34,087 scripts identical; the only change is `upcoming/magik_illyana_rasputin_darkchild_demon_uncaged.txt` (`card-script-changes.patch`).
  None of the 94 registry cards (incl. the 58 registered/blocked) changed; all 184 Commander precon decks are byte-identical.
- Engine source (GitHub compare): `Player.initVariantsZones` and `RestartGameEffect` (Karn Liberated commander restart), plus
  `FModel` (progress bar) and `ForgePreferences` (four UI prefs) in forge-gui, a new `RestartGameTest`, mobile UI and translations. Nothing in forge-ai.
- No arena overlay compiles any of those four native classes (checked against every build.json source list), so no policy source needed changing.

## Candidate and gate
- `assemble.py`: installed 1.0.28 core/game/ai/gui + desktop harness + the 230 overlay classes from the sealed 1.0.27 gate jar
  (everything that differs from the fresh base except the four upstream-changed classes). Result differs from the sealed 1.0.27 jar only in
  Player, RestartGameEffect, FModel, ForgePreferences (+ their inner classes) and module-info.
- `Rebaseline.ps1` runs all 56 suites with the same compiled fixtures against private 1.0.28 resources + the same AI profiles.
- **Sealed `target/final-1.0.28/provenance.json` (engine "NeoForge 1.0.28"): 5,720 checks, 56 suites, every suite count equals the 1.0.27 gate** (choice-wipes 14, included at its sealed state;
  the uncommitted `RepeatEachAi` r2 edit is not in this candidate). Installed-file guard: 21,729 hashes, 0 changed.
- Karn/Commander: 8 existing suites use the command zone and pass in the gate. None exercises restart, so the upstream `RestartGameTest` (4 tests, Karn Liberated)
  was run on this candidate: 4/4 pass (`target/karn/karn-1.0.28.log`); same test on the 1.0.27 candidate: 0/4 (the upstream fix, as expected).
- `Check-Gate.ps1`, `Run-Smoke.ps1`: 1.0.28 copies (engine string, UPSTREAM sha, `final-1.0.28`, baseline path). Reconciled overlay sources still come from `version-compat-1.0.27/target/source` (no overlap).

Limits: re-baseline proves existing fixtures; it adds no new card evidence.
