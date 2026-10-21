# Curse of the Swine: simplest Beast Within reuse

Opt in with `--ai-profile=curse-of-the-swine`. Only Curse of the Swine is admitted and given new AI
decisions. Default AI, existing profile boundaries, original scripts, preset decks and installed app
are unchanged. The profile supplies the original Jeskai Striker precon for both Commander seats.

## Rule

Choose only legal opposing creature targets, in the exact order already implemented for Beast Within:

1. Enchanted/equipped creatures with both power and toughness at least four.
2. Other enchanted/equipped creatures.
3. Plain creatures with both power and toughness at least four.

Within a tier, prefer higher mana value; equal candidates retain engine order. The implementation
calls `SpecialCardAi.BeastWithin.chooseTarget` rather than duplicating its ranking. Noncreature tiers
from Beast Within cannot enter the candidate list.

Use native X-cost prediction to select as many qualifying targets as affordable, then set X to the
number selected. Fewer qualifying creatures means a smaller X; no qualifying targets or no affordable
positive X means hold. Never target our own creatures, and never pad X with small unenchanted creatures.
Indestructible alone does not prevent exile. Native target protection and timing still apply.

The existing script exiles targets and gives their controllers 2/2 green Boars. Native payment,
taxes, partial resolution, Boar creation and cleanup remain unchanged. The ranking adds no RNG;
native mana-reservation checks retain their existing RNG behavior. This is not a broader removal
planner, and does not add commander, combo, death-trigger or token-replacement valuations.

## Verification

Final package: **PASS**, zero Checkstyle violations. **36 Curse native checks** and **13 existing
Beast Within regression checks** passed on the same candidate jar used for the Commander smoke.
The original Jeskai Striker mirror completed normally on turn **12** in **7.552 seconds** (seed 95661,
180-second limit), without a Curse cast. The registry therefore records **unlocked**, pending a
natural Commander-use witness. Refreshed coverage has **22 decks clear of remaining blockers**;
Jeskai Striker is newly clear. Changes are uncommitted.

`verification-summary.json` records final native, regression and Commander smoke results, candidate
hashes, original script/deck hashes and coverage status. The native suite covers all three creature
tiers, mana-value tie-breaking, X shrinking/reset, multi-opponent targets, blue pips, tapped sources,
spell taxes, hexproof/shroud/protection, indestructible, tokens, native sorcery timing, normal AI
selection, native XUU payment, actual exile and 2/2 Boars, and partial resolution without a Boar for an
invalid target. The same runner executes the existing Beast Within fixture as a regression.

Runners check isolated arena runtime paths and copy the candidate jar before running, so another
chat's build cannot replace a running JVM's jar. Full logs/private jars are ignored under `target/`.
Maven packaging skips its generic test phase; the explicit native fixture runner is the gate.
Correctness and runtime evidence are separate from matchup-strength claims. Registration remains
unlocked until a natural Commander-use witness is recorded; fixtures alone do not promote it.

From the source checkout, after building the desktop arena jar:

```powershell
./arena/curse-of-the-swine/Check-CurseOfTheSwine.ps1 -Label final
./arena/curse-of-the-swine/Run-CommanderSmoke.ps1 -Label commander-final
```

## Follow-up (closing the missing witness)

A natural cast witness was obtained after the session above: a constructed probe (4x Curse of the Swine
+ 56 Island against three Leatherback Baloth decks carrying 4/4+ creatures) cast Curse **4 / 2 / 1**
times across seeds 95661-95663 with the profile on, and **0 / 0 / 0** with it off (and the Curse seat
lost those games, so the card was doing work). Logs: `arena/curse-of-the-swine/target/probe-*.log`,
decks in `arena/curse-of-the-swine/decks/`.

So the registry entry is promoted to **verified** (a gate asserts the decisions, and a sim shows it in
a game). Still not observed: a Curse cast in the Jeskai Striker Commander smoke, which remains the
only unwitnessed scenario. The changes are committed now.
