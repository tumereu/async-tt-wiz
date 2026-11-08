# Castability, costs and payment

Agreed with the owner 2026-11-08 after problem report KX8GYS. Deleted once built; the catalog then holds the behaviour.

Owner's rule: **this is not a "works sometimes" engine and there is no manual fallback. If a single card needs a case, it
must work.** How rare a case is decides what is tested first, never whether it is handled. A check that lets a cast through
is only half the job: the player must also be able to finish it in the app.

Hotfix 0.2.0-beta8 fixed part of this (`Plan.costMayFall`, `Engine.costMayFall`, `CastFlow.payableChoices`,
`CostFallsAfterChoiceOptionsTest`, `CostFallsByTargetTest`). Keep what still fits and replace what doesn't.

## 1. Whether a card counts as castable

A card or ability counts as castable (glow, raise, listed by `Engine.options`) when there is, **or seems to be**, a valid way
to do it from the current state: mana and other costs, targets and the card's other conditions.

1. **Deny only with proof.** A cast is left out only when the search has shown there is no way. Whenever a search stops early
   (any bound, sample or step limit), the answer is "castable". False approvals are a UX cost; false denials lose game
   actions, also because Quick mode's auto-pass reads the same check.
2. **The current state, as it stands.** Mana already in the pool counts and is spent first. The check doesn't look for "this
   becomes castable if you first activate that mana ability" (owner: if you want to cast it on your Elf as a tapped creature,
   tap the Elf first). Tapping it changes the state, and the card then glows.
3. **Autotap is a convenience and never decides.** Whether something is castable doesn't depend on what Autotap's suggestion
   can do. Today it does: `CastController` drops a cast with "Not enough mana to pay" when the suggestion fails, and
   `HandCasts` calls a way unaffordable from it. When a way seems to exist but Autotap finds none, the cast panel opens for
   the player to pay by hand.
4. **One verdict.** What the engine lists, the app doesn't deny again: chips show prices and never shake a card the engine
   listed (`HandCasts.Probe.affordable` at `CastController` and `AutomatumHand`).

Mana that the check must count, all of it:

- Every mana ability, not only `Mana.isSimple` ones. Known not counted on 0.2.0-beta8 (probed): a pain land's coloured ability,
  an Ancient Tomb style land, a land removing a counter, "Sacrifice a creature: Add {R}", a Signet paid by another land. Also
  filter lands, abilities with a colour or amount choice, and triggered mana abilities ("Whenever you tap a land for mana").
- Damage from a mana ability is an effect, not a cost: it counts even when lethal (CR 118.3 and 119.4 limit only paid life;
  CR 605.3a, 605.3b, 704.3, 704.5a: the cast finishes, then you lose). Paid life you don't have doesn't count.
- A chain counts by what it nets (a Plains into a Signet). An upper bound is fine for the check: allow when it may be enough.
- Rituals and other non-mana abilities don't count: they can't be used during a cast, and the card glows once they resolve.

Mana choices do interact with other costs of the same cast (the targets are already chosen by then, so they can't create
targets; the cost is locked by then, CR 601.2f). The check and manual payment must allow all of these:

- A land tapped for mana, then sacrificed as the cost (CR 601.2g before 601.2h): one Forest pays "{G}, sacrifice a land".
- A creature needed for "tap an untapped creature" can't also tap for mana. Autotap avoids it; today only a {T} cost of the
  card itself is avoided (`Plan.tappedByCost`).
- A pain land's damage comes before a paid-life cost: at 2 life, it can't be combined with "pay 2 life", at 3 it can.
- A creature sacrificed for mana can't also be sacrificed for the cost.

The search has known early stops to turn into "castable" or make exhaustive: `Options.some` tries only the first set of a
required choice of two or more (targets, objects, modes); `Mana.search` gives up after 3,000 steps; X is tried 20 past its
least; `Plan.costMayFall` skips a reducer that already applies though its amount may read the target; assist amounts.

## 2. What the card shows before a choice

For the selected way (normally plain Cast, or kicker and so on, ZON-080):

- If it has **a single cost**, show it.
- If it **can have several costs**, or the search couldn't finish, show **the smallest legal cost, then "+" and a generic mana
  symbol holding a question mark** (`{R} + {?}`). Not real MTG notation: our UI's own. "Smallest" = lowest mana value among
  those payable now; a tie takes the first listed. X spells keep showing {X}.
- A raised card outlines Autotap's sources **for that minimum cost only**.

A chip's price must not come from "the first listed target" (`HandCasts.defaultAnswer` today, which can show the highest).

## 3. Choosing targets and other selections

While casting you are offered only candidates legal **given your current configuration**:

- A candidate is selectable when **some finished selection including it can be paid**, not when the selection is payable right
  now, because a cost can fall as you add targets ({5}, {1} less per target, 3 lands: one target {4} doesn't pay, two {3} do).
- Selecting or unselecting re-evaluates every candidate. Done (and Cast) enables only on a selection that really can be paid.
- Owner's example: "Destroy any number of target creatures. Costs {B}{B} for each creature type on selected cards", 6 Swamps. A
  4-type creature is unselectable. After a 2-type one is selected, another 2-type one with different types is unselectable and
  1-type ones remain selectable. Two creatures sharing their types depend on how the card counts them: test both readings.
- A search bound keeps the candidate selectable; the final check is exact.
- X comes before targets (CR 601.2b, 601.2c): X's maximum assumes the cheapest possible target, then the targets are narrowed by
  the X chosen. An earlier choice is never a dead end.
- The same for choices of objects on the table and in panels (the creature for emerge, harmonize's tap), and for activations.
  "None" or a smaller "up to" stays only where it can be paid. `CastFlow.payableChoices` tests singles only today, so it must
  not stop a target that only works as part of a pair.
- A legal target you can't pay for doesn't glow, and a tap on it shakes (TBL-118). A reason line is a separate, undecided feature
  (backlog, Needs discussion).

Work this out off the main thread: the tap's own response (picked look) is immediate, glows follow. An engine call for "the
selectable candidates given these picked" is expected; the engine answers one decision with a list today, so partial selections
need new API (report it for the owner's API review).

## 4. Paying

- **Nothing is tapped while you choose.** Mana abilities run when the cast or activation is finally made and goes on the Stack.
  An open cast stays the caster's alone and sends nothing (STK-025).
- **During a cast you can use mana abilities and no other abilities** (CR 605.3a): all of them, with what they need (a Signet's
  own {1}, the creature an altar sacrifices, a colour or an amount), nested as the rules allow. `Payment.activateForMana` refuses
  non-simple ones today ("Activate that mana ability on its own first"), so this is engine work too. Mana left over goes to the
  pool. Other ways to pay (convoke, delve, improvise, waterbend, assist) stay as they are.
- **Every payment works this way** (CR 605.3a), not only casts: ward, "counter unless you pay", echo and other payments during
  resolution, where the player can't float first.
- **Partial override.** A source you pick stays picked and Autotap fills in the rest; a source you take out isn't used again in
  this cast; if the rest can't be filled, Cast is greyed and says what is missing. This changes STK-024, where the first tap stops
  Autotap altogether. You can override it completely by picking everything. When the cost changes (a target chosen), your picks
  stay and Autotap's are worked out again.
- **Autotap's order:** pool; sources with no side effects (keeping today's colour and land-before-creature order); damage or life;
  sacrifices; a lethal source last. It uses a lethal source only when that's the only way to pay, by the owner's decision ("we
  have undo"). Never pays life you don't have.
- **A cast that asks only targets, with Autotap on, still goes out at once after its last target** (owner: "common things easy, rare
  things possible"). There is no source picking in the target bar: to pay another way, tap and float the mana before casting, or turn
  Autotap off (the payment panel then opens after the targets). STK-024's panel and its partial override apply wherever the panel is open.

## 5. Undo after killing yourself

A cast Autotap paid with lethal damage ends the game when priority would next come (CR 704.3), and a one-tap cast can do it. The
owner's decision relies on Undo reaching back from there. Whether it does isn't known: `GameDevice.undoTarget` has nothing against it
but it's untested, and TRN-065 shows a result dialog. Cover it by e2e first. If Undo can't be reached from that ending, stop and
report it to the owner rather than choosing a behaviour.

## 6. Tests

Invented cards with wording from real oracle templates (AGENTS.md). A wording the parser lacks (the owner's example) is either a
parser wording decided as usual or a definition built directly in the test. Each failing before its fix where it's a bug today.

**E2e, on two phones** (the owner asked for this logic to be tested carefully):

1. Enough mana for both, a tapped and an untapped creature ("{2} less on a tapped creature"): both glow; chip shows the smallest +
   {?}; outline for the minimum. Choose the cheap one: the right lands tap, the others stay. Same start, choose the dear one.
2. Exact mana for the cheap one only (KX8GYS): only it is offered; the other shakes.
3. The owner's multi-target example, selecting and unselecting: glows follow; Done only on a payable selection; shared-type variant.
4. A cost falling with more targets: a candidate only works with a second; Done greyed until then.
5. A rising cost per target (strive style) and "spells that target this cost more" on the other side.
6. X plus a target-based reduction: X's maximum, then the narrowed targets.
7. Pool: floated mana counts and is spent first; tapping your Elf first makes it the tapped target and the card then glows.
8. Each mana kind pays, by Autotap and by hand, in a cast and in a payment during resolution: pain land, Ancient Tomb style, counter
   land, sacrifice altar, Signet chain, filter land, colour choice, triggered mana.
9. Partial override: pick one source, Autotap fills in; take one out; cost changes after the target; complete override; Autotap off.
10. Autotap prefers damage-free sources; a pain land only when needed; a lethal source only when it's the only way; then Undo (§5).
11. A way exists but Autotap finds none: the panel opens and you pay by hand.
12. Interactions with other costs (§1 list), and non-mana abilities refused during a cast (they shake).
13. Quick mode doesn't pass your window while a card is castable only through a pain land or Signet. A spell castable only after floating
    first: Quick passes; check what Normal and Control do (§9).
14. A cast asking only targets, Autotap on: floated mana pays it instead of the sources Autotap would take; with Autotap off the panel opens
    after the targets.

**Engine:**

- Extend `CostFallsAfterChoiceOptionsTest`; add the Phyrexian + reduction combination (a bug Arena has had).
- A **no-false-denial fuzz test**: on generated boards, for each card the engine leaves out, a search without bounds must find no way.
  `OptionsFuzzTest` covers the other direction and needs to accept "seems castable".
- A **local check over every supported card**, in `arcanum-tools` beside `setSupport`, reading card data from local files (never
  committed): each mana ability can be activated and pays, each cost reducer lowers a cost as written, each listed in the report.

**Speed:** run `EndTurnBenchmark` before and after, and report both. The gate is that most cards have no reducer; reducers on the board
add look-ahead for unaffordable cards. If it's slow, tell the owner what it costs rather than narrowing the search with false denials.

**Gallery** (gallery.tsv): the raised card with the smallest cost + {?}; a target bar with an unaffordable creature not glowing; both
affordable; the multi-target selection; a Signet chain in the payment; partial override.

## 7. Catalog

Expected to change: TBL-117, TBL-118 (unaffordable targets), TBL-122, ZON-079, ZON-080, STK-023, STK-024, STK-047, STK-052, SYS-053. New
entries as needed. Anything else the work would change: stop and ask (AGENTS.md).

## 8. Not in this work

- The "can't do" explainer (a reason shown from a shaken card): backlog, Needs discussion.
- Searching for "tap this first, then it's castable" (§1.2).
- Whether this invalidates stored games: if events or stored formats must change, ask the owner first. Nothing is known to need it.

## 9. Decided consequences (owner, 2026-11-08)

Quick mode auto-passes when nothing is castable from the current state, so a spell castable only after floating first doesn't stop your
response window there. Accepted: for such situations players use Normal or Control mode, and Undo covers a mistake. If Normal mode doesn't
stop in that situation as the owner expects, report it rather than changing the modes.

## 10. Process

Reviewed by session async-tt-wiz-dc (which wrote this and holds the context) before merge: the implementor's branch goes there for review
of the rules and the search's direction of failure, and its full suites and e2e results with it.
