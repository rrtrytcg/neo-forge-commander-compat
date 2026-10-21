# Abundance opt-in policy on NeoForge 1.0.27

Profile: `abundance`. This extends the sealed private Estrid 1.0.27
candidate, with scoped source inputs recorded at `53b2441a`. It does not update
the installed app. Original scripts, default AI and other profile behavior stay
unchanged.

## Rules

- Normal hand cast in our second main, with an empty stack and no existing own
  Abundance. Retain native permanent evaluation, legality, costs and payment.
- Accept the original optional replacement only for our native offered draw event
  with the original active Abundance and supported public circumstances.
- Choose **Land** when we have fewer than six lands in play and no land in hand;
  otherwise choose **Nonland**. Recompute for every card in a multi-card draw.
- Use current battlefield and our hand only. Never evaluate candidate DigUntil
  branches using their generic AI: that code inspects library contents. The new
  acceptance/type decision does not read library contents or order or use RNG.
- Original native code reveals until the chosen type, puts the found card into
  hand and puts skipped revealed cards on the bottom. Existing native ordering
  of revealed cards is unchanged. This is not itself a draw; no invented
  four-card draw reserve is applied. Native empty-library and absent-kind behavior
  remains intact, as verified by original-script fixtures.
- Defer competing public draw replacements, relevant library/hand movement
  replacements and active draw/reveal/mill engines, including Sylvan Library and
  draw-trigger engines. Guards inspect active public traits only, not opposing
  hands or libraries. An opposing unrelated draw replacement is conservatively
  treated as uncertain in this first version. Ordinary multi-card draw spells
  remain supported.
- Recognize the exact immutable native Commander return template, without exempting
  modified templates or additional effects. Native commander redirection remains
  authoritative, including a commander found in the library.
- Native cannot-draw restrictions, replacement eligibility/order, offered legal
  choices and mandatory completion after an accepted replacement remain native.
  Do not override other replacement effects. Fresh source, profile, original
  templates, timing, restrictions and native affordability are rechecked before
  casting costs; replacement confirmation rechecks live public circumstances.

The first pass is intentionally bounded. It can hold in complicated enchantress,
draw-engine and mixed-replacement positions. It is not a general draw-replacement
planner or a claim that all Abundance combinations are supported.

## Evidence

The initial constructed fixtures passed, but the first Commander observations had
zero Abundance use: the conservative movement guard also held the standard
Commander return rule. The initial candidate and three observations remain
separate in `initial-observation.json` and ignored raw archives. The final policy
admits only that exact native rule template, with Commander cast/replacement,
found-commander and altered-template regression fixtures. No game action or
choice was forced to obtain the subsequent witness.

The final integrated gate passes **4,613 native checks across 43 suites**, plus
20 deadline checks. Six paired observations pass **779 card-use checks**: three
paid/resolved casts, two Land and 46 Nonland choices, 48 found cards, 27 bottomed
revealed cards and three Harmonize choices. All three default controls have zero
Abundance casts/choices. Four runs hit the 30-second clock; two return earlier.
No pending observed actions, late events, fizzles or unexpected diagnostics.

Measured final counts and candidate fingerprint are in `verification-summary.json`
and `card-use-evidence.json`. Native fixtures use the original card script,
normal controller selection/payment/resolution and actual `Player.drawCard(s)`
replacement handling. They cover both type choices, fresh hands, the six-land
boundary, native reveal/bottom/hand movement, multi-card draws, thin/empty
libraries, absent types, draw restrictions, opaque engines, stale plans, native
colored/taxed payment, policy RNG/resource integrity and unchanged defaults.

Paired custom Commander probes intentionally use repeated cards and an off-color
Yargle and Multani commander. They are exposure decks, not valid Commander decks.
The demand actor has uncastable black creatures so native main-phase spending
can expose Abundance at four or five lands; the flood actor has ordinary Centaur
Courser and Harmonize casting. Opponents use Wall of Wood and Forest.
Seed 114011 naturally discards its early Abundance copies before reaching four
mana, then casts at seven lands and correctly chooses only Nonland. Seed 114012
exposes both Land and Nonland. Both observations are retained; type coverage is
required across the paired seeds, not fabricated in every run.
This changes opportunities through deck construction, never forced actions,
phases, choices or policy exceptions. Native land plays provide ordinary-action
controls in every run; the flood pair also provides native creature casting.

The observer reads native cast/mode/zone/phase events, the actor's own hand,
public battlefield/library counts and already revealed movement information.
For bottom movements it checks only whether the known revealed card is actually
at the bottom, without snapshotting unrevealed library contents or order.
It supplies no gameplay decisions. Each choice is paired with settled native
hand/library movement, and put-into-hand events must not increment draw counts.

Observations establish autonomous card use only. Clock stops and the intentionally
invalid decks establish no normal-completion, winner, tactical-strength or
win-rate claim.

## Preservation and concurrent work

`904ff985` deliberately retired the abandoned research labs. Only the **installed**
group is checked: all 21,729 app/resource hashes must remain unchanged. The old
unrelated group is intentionally ignored; deleted folders are not restored.

The final candidate builds on the sealed Estrid gate from `53b2441a`, tagged
`commander-compat-estrid-verified-2026-10-19`. Its 42 suites / 4,484 checks include
39 Estrid checks. The three Estrid production sources, adaptive-enchantment
profile and Estrid test source are recorded as build inputs; EstridUntapTests
runs alongside the 129 new Abundance checks: **43 suites / 4,613 checks**.
Non-overlay candidate entries must match that sealed base byte for byte.
No Estrid source, test or resource is edited by this task. Only Abundance's
helper/hooks/resources/tests/evidence and shared coverage/tracker refresh belong
to this change.

## Reproduce

```powershell
./arena/abundance/Check-NeoForge-1.0.27.ps1
./arena/abundance/Check-Timeouts.ps1
./arena/abundance/Run-CardUseProbe.ps1 -Scenario demand -Seed 114011 -ProfileMode on
./arena/abundance/Run-CardUseProbe.ps1 -Scenario demand -Seed 114011 -ProfileMode off
./arena/abundance/Run-CardUseProbe.ps1 -Scenario demand -Seed 114012 -ProfileMode on
./arena/abundance/Run-CardUseProbe.ps1 -Scenario demand -Seed 114012 -ProfileMode off
./arena/abundance/Run-CardUseProbe.ps1 -Scenario flood -Seed 114021 -ProfileMode on
./arena/abundance/Run-CardUseProbe.ps1 -Scenario flood -Seed 114021 -ProfileMode off
python arena/abundance/check-card-use.py
./arena/Audit-Deck.ps1 -All -Coverage
python arena/abundance/refresh.py
```

The build runner protects an existing final gate. `-Focused` is the development
fixture lane; `-RunOnly` uses a sealed build without recompilation. ComputerUtil
and AiController patches are anchored to frozen source `899567ea` and applied to
the reconciled private 1.0.27 sources. Raw logs, resources and binaries remain
under ignored `target/`.
