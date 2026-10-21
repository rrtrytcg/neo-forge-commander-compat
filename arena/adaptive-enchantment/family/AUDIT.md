# Land-mana Aura family audit

2026-10-16. Checkout: `forge-ai-arena`. Everything added here is opt-in through the existing `adaptive-enchantment` profile. No installed application files, original card scripts, or original preset decks were changed.

## Findings and implementation

The reproducible script scan found **42 land-capable mana Auras**: 40 with explicit land/basic-land-type enchant restrictions and two permanent-target boundary cases (Song of the Dryads and Elemental Resonance). The scanner reads mechanical lines, not card names or Oracle wording. Complete scripts, classifications and SHA-256 hashes are in `mana-family.json`.

The principal issue is the same one found for Wild Growth: the native predictor sees a mana trigger, but source-to-color mapping omits its bonus colors. Flexible triggered mana also needs to choose colors for the current payment. Upstream beneficial-Aura targeting already worked in the board probes; **no additional targeting fix was justified**. `RemoveDeck:All` is a separate in-game admission blocker, not a targeting failure. Existing basic-first/any-land preferences for the original three cards remain unchanged.

One profile flag, `landManaAuras=true`, enables a structural matcher in `ManaAuraAi`. It recognizes land/Forest-only, beneficial Auras with an unconditional static TapsForMana trigger, an attached-land validity filter, a direct Mana effect for TriggeredCardController, and a fixed amount of one or two. Output may be fixed, chosen, any one color, or any combination of colors. No new card-name overrides were added. Dynamic amounts, conditions, restrictions, subabilities, targeted recipients, and unrecognized fields are rejected. Other spells and abilities on admitted cards retain their native AI.

The shared source/color mapping now includes recognized bonus colors. Chosen mana uses the actual persisted color; flexible prediction and native activation use the existing payment context. Native legality, payment, actual triggers, restricted grants, and untapping remain native. Unknown mana triggers or applicable ProduceMana replacement effects on the same land disable this bridge for that payment rather than being reinterpreted. There is no production cache to invalidate.

## Card groups

| Mechanical group | Cards | Result |
|---|---|---|
| Fixed triggered G / GG | Wild Growth; Wolfwillow Haven; Overgrowth | Wild Growth previously supported; the other two now fixed by the same matcher. |
| Flexible triggered one mana | Fertile Ground; Blighted Burgeoning; Buried in the Garden; Glittering Frost; Trace of Abundance; Verdant Haven | Fertile Ground previously supported; five newly fixed. |
| Triggered two mana in any combination | Dawn's Reflection; Market Festival | Dawn's Reflection previously supported; Market Festival newly fixed. |
| Chosen-color trigger | Utopia Sprawl; Shimmerwilds Growth | Both newly fixed. Native as-enters choice and persisted-color payment verified. Forest-only targeting remains enforced. |
| Granted tap ability, one mana of any color | Abundant Growth; Urban Utopia; Unbridled Growth | Already native: targeting, prediction and real payment verified without the family bridge. |
| Granted tap ability, fixed GG | Find the Path | Already native. |
| Granted tap ability, two mana of any ONE color | Gift of Paradise; Grafted Growth; New Horizons; Sheltered Aerie; Weirding Wood; A-Find the Path; Nature's Embrace | Already native. Cannot split a single activation into different colors. Nature's Embrace's broader creature-versus-land decision is unchanged. |
| Restricted granted tap ability | Discreet Retreat | Already native for legal Outlaw spells; non-Outlaw payment correctly fails. No override. |
| Variable triggered output | Elvish Guidance; Runaway Growth | Deferred: changing Elf counts, intensity growth, and non-static/subability timing require separate evidence. |
| Targeted granted ability plus cascade | Bigger on the Inside | Deferred: targeted recipient and cascade are outside ordinary mana-ability payment. |
| Transformation/replacement of land abilities | Imprisoned in the Moon; Lithoform Blight | Unchanged. Broader removal/targeting decisions and ability replacement are outside the bonus-trigger bridge. Lithoform's existing native any-color ability appears in the diagnostic but needs no new family override. |
| Land-type changes | Nylea's Presence; Lush Growth; Contaminated Ground; Convincing Mirage; Evil Presence; Lingering Mirage; Phantasmal Terrain; Sea's Claim; Song of the Dryads; Spreading Seas; Tainted Well | Unchanged. These use native basic-land abilities, including defensive/disruptive targeting and chosen land types. They are not bonus mana. |
| Phase mana based on enchanted cost | Elemental Resonance | Deferred: first-main-phase mana, not land activation. On an ordinary land its enchanted mana cost is zero. |

Thus **13 bonus Auras are supported (3 prior + 10 newly fixed)**, **12 grant Auras retain working native mana handling**, and **17 other cases remain unchanged for the reasons above**. This is mana-mechanic coverage, not certification of every ETB, sacrifice, removal, or deck-building strategy on those cards. No land-Aura ProduceMana replacement family was found that justified extending the bridge to global multipliers/replacements.

## Evidence and limits

- `baseline-audit.csv` and `after-audit.csv`: 84 rows each (42 cards, profile off/on). The baseline is the earlier three-card implementation in the working tree, not an upstream commit. The ten new cards change from unpayable to payable for their diagnostic cost under Adaptive; the original three remain payable.
- Target probes use own Plains/Forest/nonbasic land and an opposing Island. All beneficial bonus Auras already selected an own legal land; granted ramp also selected own lands in the audit. Removal-Aura rejections against low-value lands are contextual, not proof of broken targeting.
- CSV payment probes put the Aura directly on a land and prechoose blue where necessary; they isolate production from casting and ETB decisions. A false row is not automatically a bug: e.g. Lush Growth does not produce U, Imprisoned in the Moon produces C, and Discreet Retreat cannot pay for Divination. The dedicated native tests separately cover real Aura resolution and as-enters color choice.
- **349 family checks passed**: every one of the 13 recognized scripts; real bonus payment; over-budget rejection; no tapped-source reuse; native untap then repayment; source removal; Forest-only restrictions; shroud; native chosen-blue entry and refusal to treat it as red; zero RNG in family recognition; default and Abzan isolation; all 11 unrestricted grants; legal/illegal restricted grants; unknown-field rejection; explicit refusal to reinterpret Mana Flare and Mana Reflection.
- Stacking checks cover fixed + chosen, two independently flexible bonuses in both orders, fixed GG + any-combination-two in both orders, and a granted two-of-one-color ability + Wild Growth. Two granted tap abilities cannot be combined as if the land tapped twice. Untapping uses the real Early Harvest effect targeting the player, so it also respects Trace of Abundance's land shroud.
- **76 original Adaptive checks passed**, including Creeping Renaissance and the original three Auras.
- Maven package succeeded with **zero Checkstyle violations**. Maven uses `-DskipTests`; the native checks above are run separately. Build log: `target/build.log`.
- **Commander smoke:** seed 95331, 180-second cap, normal life-total win on turn 10 in 3.869 seconds. Actual casts: Dawn's Reflection, Wolfwillow Haven, Urban Utopia (combination bonus, fixed bonus, native grant). Log/hash/decks: `target/commander-family/`. No timeout. This is one runtime smoke, not strength evidence or proof every audited card naturally appeared.
- Production class hashes match across the smoke jar and the two native-suite jars. Original script hashes were rechecked. `verification-summary.json` records the result.

The first regression attempt ran against a shared jar during packaging and hit a class-loading failure. All three native runners now snapshot their dependency jar before compiling/running. The verified passes and smoke use private copies; the failed attempt is not counted.

## Reproduce

From the repository root, with the bundled JDK/Maven:

```powershell
python arena/adaptive-enchantment/family/scan_land_mana_auras.py
python arena/adaptive-enchantment/family/make_smoke_decks.py
& ./arena/adaptive-enchantment/family/Run-FamilyAudit.ps1 -Label family-audit
& ./arena/adaptive-enchantment/family/Check-LandManaFamily.ps1 -Label family-native
& ./arena/adaptive-enchantment/Check-AdaptiveEnchantment.ps1 -Label family-regression
# After building the desktop jar:
& ./arena/adaptive-enchantment/family/Run-CommanderSmoke.ps1
```

The smoke decks are local 100-card Commander decks derived from the original Adaptive precon, with Tuvasa as commander and eleven explicit card substitutions to increase Aura exposure. Their source deck, substitutions and hashes are recorded in `smoke-deck-provenance.json`. The profile's default decks remain the original precon.

## Per-card production diagnostic

These are fixed board/cost probes, not full-card verdicts. `targetOwnLand` is only meaningful when the AI is willing to cast in this specific board state.

| Card | Group | Diagnostic cost | Adaptive before | Adaptive after | Upstream own-land target in probe |
|---|---|---|---|---|---|
| Abundant Growth | native-grant | U | true | true | true |
| Bigger on the Inside | targeted-grant | U U | false | false | true |
| Blighted Burgeoning | flexible-trigger | W G | false | true | true |
| Buried in the Garden | flexible-trigger | W G | false | true | true |
| Contaminated Ground | land-type-change | U | false | false | false |
| Convincing Mirage | land-type-change | U | false | false | false |
| Dawn's Reflection | combination-trigger | W U B | true | true | true |
| Discreet Retreat | restricted-grant | U U | false | false | true |
| Elemental Resonance | phase-mana | U | false | false | false |
| Elvish Guidance | variable-trigger | W G G | false | false | true |
| Evil Presence | land-type-change | U | false | false | false |
| Fertile Ground | flexible-trigger | W G | true | true | true |
| Find the Path | native-grant | G G | true | true | true |
| Gift of Paradise | native-grant | U U | true | true | true |
| Glittering Frost | flexible-trigger | W G | false | true | true |
| Grafted Growth | native-grant | U U | true | true | true |
| Imprisoned in the Moon | transform-grant | U | false | false | false |
| Lingering Mirage | land-type-change | U | true | true | false |
| Lithoform Blight | transform-grant | U | true | true | false |
| Lush Growth | land-type-change | U | false | false | true |
| Market Festival | combination-trigger | W U B | false | true | true |
| Nature's Embrace | native-grant | U U | true | true | true |
| New Horizons | native-grant | U U | true | true | true |
| Nylea's Presence | land-type-change | U | true | true | true |
| Overgrowth | fixed-trigger | W G G | false | true | true |
| Phantasmal Terrain | land-type-change | U | false | false | false |
| Runaway Growth | variable-trigger | W G | false | false | true |
| A-Find the Path | native-grant | U U | true | true | true |
| Sea's Claim | land-type-change | U | true | true | false |
| Sheltered Aerie | native-grant | U U | true | true | true |
| Shimmerwilds Growth | chosen-color-trigger | W U | false | true | true |
| Song of the Dryads | land-type-change | U | false | false | false |
| Spreading Seas | land-type-change | U | true | true | false |
| Tainted Well | land-type-change | U | false | false | false |
| Trace of Abundance | flexible-trigger | W G | false | true | true |
| Unbridled Growth | native-grant | U | true | true | true |
| Urban Utopia | native-grant | U | true | true | true |
| Utopia Sprawl | chosen-color-trigger | G U | false | true | true |
| Verdant Haven | flexible-trigger | W G | false | true | true |
| Weirding Wood | native-grant | U U | true | true | true |
| Wild Growth | fixed-trigger | W G | true | true | true |
| Wolfwillow Haven | fixed-trigger | W G | false | true | true |
