# Commander card unlock playbook

> **Current engine: NeoForge 1.0.28** (re-baselined; sealed gate `arena/version-compat-1.0.28/target/final-1.0.28`, 5,720 checks / 56 suites). New labs chain from it with `arena/version-compat-1.0.28/Check-Gate.ps1` and `Run-Smoke.ps1` (`-OutName final-1.0.28`). `arena/lib/Check-Gate.ps1` / `Run-Smoke.ps1` and the `1.0.27` paths below are historical; see `arena/version-compat-1.0.28/README.md`.

The working procedure for taking one blocked card (or one family) from `AI:RemoveDeck:All` to
**verified** under an opt-in AI profile on NeoForge 1.0.27. Background and history live in
`arena/COMMANDER_COMPAT_WORKFLOW.md`; you do not need to read it to do a card.

## Goal

Headline metric: Commander precons with **zero remaining blocked cards** (184 audited). Prefer cards
that clear a deck on their own:

```powershell
python -I arena/lib/next-cards.py          # single-blocker decks; --two adds the next tier
```

## What the block actually does (read before designing)

`ComputerUtilCard.isCardRemAIDeck` is enforced in `AiController.chooseSpellAbilityToPlay`: blocked
cards' **nonland spells and activated abilities** are dropped from the AI's choices unless the active
profile lists the card in `ignoreRemoveDeckDuringPlay`. It does **not** stop land drops, mana
abilities used during payment, triggers, or replacement choices (e.g. dredge). So a blocked land is
already played and tapped for mana; unlocking it means deciding what its utility abilities may do.

## Steps

1. **Read the card**: `forge-gui/res/cardsfolder/<letter>/<name>.txt`. Find the native AI class for
   each ability API (`forge-ai/src/main/java/forge/ai/ability/<Api>Ai.java`) and the exact reason it
   misplays or is unsafe. Confirm the problem with a `baseline` fixture before fixing it.
2. **Agree the rule with the user** in a few plain bullets (when to use it, what to target, what to
   hold). Keep it bounded and deterministic: no policy RNG, no peeking at hidden hands or library
   order. A rule that holds when unsure is fine; document what it skips.
3. **Profile**: `forge-gui/res/ai/overrides/<lab>.properties`:
   ```
   ignoreRemoveDeckDuringPlay=Card A,"Name, With Comma"
   aiLogic=Card A=LogicName,"Name, With Comma=LogicName"
   ```
   Only one profile is active at a time (`AiOverrideProfile.setActive`). Quote names containing commas.
4. **Code**: put the rule in `forge-ai/src/main/java/forge/ai/<Lab>Ai.java` with
   `uses(sa)` = `"<LogicName>".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))` and a
   `consider(ai, sa)` returning `AiAbilityDecision`. Hook it with **one line** at the top of the
   native method (`checkApiLogic`/`canPlay`/`chooseSingle...`). Default AI and other profiles must be
   unchanged. Leave costs, targeting legality, payment and resolution native. Re-check the decision at
   activation time if anything could go stale.
5. **Fixture** `arena/<lab>/<Lab>Tests.java`: copy the skeleton of
   `arena/adaptive-enchantment/EstridUntapTests.java` (game setup, `add()`, `refresh()`,
   `check()` printing `PASS <label>`, `willing(sa)` through `SpellApiToAi.Converter`). Cover: the
   intended use, normal AI selection (`chooseSpellAbilityToPlay`), a real `ComputerUtil.playStack` +
   `resolveStack` with native results, each hold condition, timing, and unchanged behaviour with no
   profile and one unrelated profile. A `baseline` argument path asserts the native defect.
6. **Gate** (shared script; chains on the newest sealed gate):
   ```powershell
   $g=@{Lab='arena/<lab>';Base='<newest gate dir>';Production='forge-ai/src/main/java/forge/ai/<Lab>Ai.java','forge-ai/src/main/java/forge/ai/ability/<Api>Ai.java';Tests='<Lab>Tests.java';Profiles='forge-gui/res/ai/overrides/<lab>.properties'}
   ./arena/lib/Check-Gate.ps1 @g -Baseline   # native defect, unchanged candidate
   ./arena/lib/Check-Gate.ps1 @g -Focused    # iterate on new suites
   ./arena/lib/Check-Gate.ps1 @g             # all prior suites at exact counts + new (run in background)
   ```
   Newest gate = the sealed `provenance.json` with the most suites:
   ```powershell
   Get-ChildItem arena -Recurse -Filter provenance.json | ? FullName -match 'target' | % { $p=gc $_ -Raw|ConvertFrom-Json; if($p.engine -eq 'NeoForge 1.0.27' -and $p.results -and !$p.baseline){[pscustomobject]@{suites=$p.results.Count;checks=($p.results|measure checks -Sum).Sum;dir=$_.DirectoryName}} } | sort suites -Desc | select -First 3
   ```
   `Production` lists every source file you changed for this card (helper plus each hooked native
   file). The script reconciles each against 1.0.27 and refuses stale or conflicting sources.
7. **Exposure games** (not random precon games: a single copy in 99 cards rarely comes up). Write
   small purpose-made decks in `arena/<lab>/decks/`: the actor deck holds many copies of the card
   (repeated cards and an off-colour commander are fine; deck validity is not under test), and the
   opponent deck creates the exact situation the rule reacts to (big attackers, targets of the right
   mana value, the basic land to copy...). Prefer two-player games with a short clock (90-120 s).
   Run the same seed with the profile off and on, two seeds, and count the log lines that prove use
   (`activated <Card>`, `Resolve Stack: <Card>`, `Replacement Effect: <Card>`):
   ```powershell
   ./arena/lib/Run-Smoke.ps1 -Gate arena/<lab>/target/final-1.0.27 -Profile <lab> -ProfileMode on -Seed 117011 -ClockSeconds 120 `
     -Decks (Resolve-Path arena/<lab>/decks/Actor.dck),(Resolve-Path arena/<lab>/decks/Opponent.dck) -Count @{use='activated <Card>'}
   ```
   Only fall back to real precons for a final sanity game if the user asks. Card-use evidence only.
8. **Record**: add one row per card to `arena/coverage/card-status.tsv`
   (`card<TAB>verified<TAB>short evidence; see arena/<lab>/README.md<TAB>date`), then
   `./arena/Audit-Deck.ps1 -All -Coverage` to regenerate `COVERAGE.md`/`coverage.json`. Write a
   **short** `arena/<lab>/README.md`: defect, rule, hooks, gate numbers, smoke table, limits.
9. **Commit only your paths** (`git add <explicit paths>`), message `Verify opt-in <Card> ...`,
   attribution line, tag `commander-compat-<lab>-verified-<date>`. Local only; never push.

## Pitfalls seen so far

- Another agent may be editing the same checkout. Check `git status` before starting and before
  committing; never include their files; never put a file they are editing in `Production`.
- PowerShell: variable names ignore case (a `$base` local clobbers a `[string]$Base` param); wrap
  single results in `@()` before splatting; native output piped to files loses bytes/EOLs (use python).
- Working tree is CRLF, git blobs are LF.
- `arena/version-compat-1.0.27/verify-preserved.py`'s `unrelated` group is obsolete (stompy/aristocrats
  removed in `904ff985`, Estrid fixtures rewritten); `Check-Gate.ps1` checks installed files only.
- Smoke results are card-use evidence. Never claim winners, strength or win rate.
