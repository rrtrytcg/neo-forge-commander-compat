# Ahoy Mateys opt-in AI

Activate with `--ai-profile=ahoy-mateys` in the arena simulator. The profile supplies two copies of the original `Ahoy Mateys [LCC] [2023]` Commander precon. Only Faithless Looting, Rakdos Charm and Windfall receive new decisions and admission past their existing `RemoveDeck:All` warning. Original card scripts and decks are unchanged. The default AI and the Abzan/Adaptive profiles retain their existing paths. No installed application files were changed.

## Rules

- **Faithless Looting, including flashback:** require two preferred discards before drawing, excluding Looting itself. Keep enough hand lands to reach five lands in play, plus one spare; excess lands come first. With Admiral Brass, Unsinkable on the battlefield or in the command zone, permit at most one Pirate creature that native mana prediction says cannot currently be afforded, highest mana value first. Reassess after the actual draw; use native discard selection to finish any mandatory choices. Require enough library for two draws.
- **Rakdos Charm:** first choose damage that kills an opponent while keeping us alive; otherwise respond to an opponent's pending graveyard use; otherwise destroy the best legal opposing non-token artifact using native artifact evaluation; otherwise exile the largest opposing graveyard with at least four nonland cards. Hold if none qualify. Never target our own graveyard.
- **Windfall:** require at most two cards left after casting and an expected largest discard of at least four. Public hand counts determine that maximum, excluding players protected from discarding. Require enough library for the expected draw. Pirate setup never relaxes these gates.

Native targeting, payment, spell timing legality, resolution and commander reanimation remain responsible for execution. Looting's AI phase preference permits setup in Main 1, subject to native sorcery legality. There is no new policy RNG or access to opposing hidden card identities.

## Conservative limits

Charm treats each creature as its own damage source, matching the original script. Its bounded forecast supports native full prevention and exact doubling templates (including the deck's Angrath's Marauders), native source/controller matching, prevention bans, life-loss protection and cannot-lose protection. Unknown damage replacements, partial prevention, infect or lifelink make the damage mode unavailable; the other modes can still qualify. This is deliberately not a general damage simulator. Public draw replacements also cause Looting/Windfall to hold rather than guess a safe draw count.

Reactive graveyard handling covers targeted cards currently in opposing graveyards and public mass graveyard-to-battlefield recursion, including an exile-then-return chain such as Living Death. It does not attempt to predict future graveyard uses or reconstruct hidden plans.

## Verification, 2026-10-16

- Offline Maven arena package: **PASS**, zero Checkstyle violations. Maven's generic test phase was skipped; the explicit native fixture runners below were run separately.
- `Check-AhoyMateys.ps1 -Label ahoy-built`: **77 native checks PASS**. Includes actual Looting draw/discard, changed discard candidates after drawing, native Admiral reanimation with finality, flashback eligibility, native Windfall resolution and cleanup, protected hand counts, native Charm artifact/exile/damage resolution, self-lethal boundaries, both controllers' Marauders, full prevention, prevention disabled, uncertain damage fallback, and profile isolation.
- `../adaptive-enchantment/Check-AdaptiveEnchantment.ps1 -Label ahoy-regression`: **76 checks PASS**.
- `Run-CommanderSmoke.ps1`: **PASS**, seed **95441**, original precon mirror, Commander, Classic policies, 180-second clock. Normal completion in **7,738 ms**, turn **12**. Rakdos Charm was cast. Looting/Windfall decisions are covered by native fixtures; this one natural smoke does not demonstrate every policy card being cast.

These are correctness, regression and runtime checks, not evidence of improved win rate. Machine-readable hashes and results are in `verification-summary.json`. Full logs and private candidate jars are under ignored `target/`; each runner snapshots the engine jar so concurrent builds cannot replace it during a run. All runtime directories are checked to remain inside `arena/runtime`.

## Reproduce

From the source checkout, after building the desktop arena jar:

```powershell
./arena/ahoy-mateys/Check-AhoyMateys.ps1 -Label ahoy-built
./arena/adaptive-enchantment/Check-AdaptiveEnchantment.ps1 -Label ahoy-regression
./arena/ahoy-mateys/Run-CommanderSmoke.ps1
```

Implementation: `forge-ai/src/main/java/forge/ai/AhoyMateysAi.java`, with small profile-gated entry hooks in `AiController`, `DrawAi`, `DiscardAi` and `CharmAi`. Profile configuration: `forge-gui/res/ai/overrides/ahoy-mateys.properties`.
