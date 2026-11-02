package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.token
import kotlin.test.Test

/**
 * Reality Fracture chunk 07: blue cards. Counting cards milled into a target player's graveyard this turn; stun counters
 * (CR 122.1d); modal enters triggers and spells (CR 700.2), a counter unless its controller pays (CR 118.12a), base power and
 * toughness (layer 7b); a reflexive trigger whose number of targets is the nonland cards discarded (CR 603.12, 601.2c);
 * "if it isn't a token, create a token that's a copy of it" (CR 707.2, 608.2h); a spell exiled with four cards of its name as
 * a cost paid on resolution, and the deck-building rule (CR 100.2a); attacking despite defender after scrying or surveilling.
 */
class FraChunk07BlueTest {
    @Test
    fun mercilessComputations() = card(
        name = "Merciless Computations",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Draw X cards, where X is the number of cards that were put into target player's graveyard from their library this turn.",
    ) parsesTo abilities(spell(Step.Do(Effect.Draw(Players.You, Value.EventsThisTurn(EventPattern.ZoneChange(ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))),
            from = Zone.Library, to = Zone.Graveyard), Measure.Objects))), targets = listOf(targetPlayer)))

    // CR 122.1d
    @Test
    fun frostlogicAdept() = card(
        name = "Frostlogic Adept",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            {3}{U}, Exile this card from your graveyard: Tap target creature and put a stun counter on it. Activate only as a sorcery. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(Keyword.Prowess, activated(cost(mana("{3}{U}"), exileSelfFromGraveyard),
            Step.Do(Effect.Tap(target("creature"))), counters(target("creature"), kind = CounterKind.Stun),
            targets = listOf(slot("creature", creature)), rules = asSorcery))

    @Test
    fun scryingFencer() = card(
        name = "Scrying Fencer",
        cost = "{2}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "3/2",
        text = """
            Flash
            When this creature enters, choose one —
            • Tap target creature.
            • Untap target creature.
            • Draw a card, then discard a card.
        """,
    ) parsesTo abilities(Keyword.Flash, Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.Tap(target("creature")))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.Untap(target("creature")))))),
            Mode(Instructions(steps = listOf(draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any))))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 118.12a
    @Test
    fun frigidWelcome() = card(
        name = "Frigid Welcome",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target creature or legendary spell unless its controller pays {3}.
            • Target creature gets -5/-0 until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("spell", and(or(creature, ObjectFilter.Super(Supertype.Legendary)), spellFilter))), steps = listOf(
                Step.Pay(cost(mana("{3}")), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell")))))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(target("creature"), -5, 0)))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 613.4b
    @Test
    fun polishedHypothesis() = card(
        name = "Polished Hypothesis",
        cost = "{U}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature has base power and toughness 1/1 until end of turn.
            • Target creature has base power and toughness 4/5 until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(1), n(1)))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(4), n(5)))))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun exactExcision() = card(
        name = "Exact Excision",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target white or black spell.",
    ) parsesTo abilities(spell(Step.Do(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(or(ObjectFilter.OfColor(Color.White), ObjectFilter.OfColor(Color.Black)), spellFilter)))))

    // CR 603.12, 601.2c: up to that many targets
    @Test
    fun veteranFrostweaver() = card(
        name = "Veteran Frostweaver",
        cost = "{1}{U}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            When this creature enters, draw two cards, then discard two cards. When you discard one or more nonland cards this way, tap up to that many target creatures and put a stun counter on each of them.
            {3}{U}{U}, Exile this card from your graveyard: Draw two cards.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, draw(2), Step.Do(Effect.Discard(Players.You, n(2), ObjectFilter.Any, preBind = "discarded")),
                Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("discarded"), nonland)), Comparison.AtLeast, n(1)), then = listOf(
                    Step.Do(Effect.ReflexiveTrigger(Instructions(
                        targets = listOf(TargetSlot("creature", TargetKind.Objects(creature), min = n(0), max = Value.Count(Objects.Where(Objects.Bound("discarded"), nonland)))),
                        steps = listOf(Step.Do(Effect.Tap(target("creature"))), counters(target("creature"), kind = CounterKind.Stun)))))))),
            activated(cost(mana("{3}{U}{U}"), exileSelfFromGraveyard), draw(2)),
        )

    // CR 707.2, 608.2h
    @Test
    fun sphinxOfMistakenProofs() = card(
        name = "Sphinx of Mistaken Proofs",
        cost = "{2}{U}{U}",
        type = "Creature — Sphinx Illusion",
        pt = "4/2",
        text = """
            Flash
            Flying
            Whenever this creature attacks, draw a card, then discard a card.
            When this creature dies, if it isn't a token, create a token that's a copy of it.
        """,
    ) parsesTo abilities(
            Keyword.Flash,
            Keyword.Flying,
            triggered(attacksSelf, draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any))),
            triggered(diesSelf, Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You)),
                interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, token))),
        )

    // CR 118.12, 100.2a
    @Test
    fun sphinxSAdvance() = card(
        name = "Sphinx's Advance",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Draw two cards. Then you may exile this spell and four cards named Sphinx's Advance from your graveyard. If you do, search your library for a Sphinx creature card, put it onto the battlefield, then shuffle.
            A deck can have any number of cards named Sphinx's Advance.
        """,
    ) parsesTo abilities(
            spell(draw(2),
                Step.Pay(cost(CostPart.Exile(CostObjects.Self, from = Zone.Stack),
                    CostPart.Exile(CostObjects.Choose(and(ObjectFilter.Named("Sphinx's Advance"), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), n(4)),
                        from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(
                    Step.Do(Effect.Search(Players.You, and(sub("Sphinx"), creature), n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                    move(Objects.Bound("found"), Zone.Battlefield),
                    Step.Do(Effect.Shuffle(Players.You))))),
            static(Mechanic.AnyNumberInDeck),
        )

    // CR 702.3b
    @Test
    fun watchfulPhantasm() = card(
        name = "Watchful Phantasm",
        cost = "{1}{U}",
        type = "Creature — Bird Illusion",
        pt = "2/3",
        text = """
            Defender, flying, vigilance
            As long as you've scried or surveilled this turn, this creature can attack as though it didn't have defender.
            {3}{U}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
            Keyword.Defender,
            Keyword.Flying,
            Keyword.Vigilance,
            static(Mechanic.CanAttackDespiteDefender, condition = scriedOrSurveilledThisTurn),
            activated(cost(mana("{3}{U}")), surveil(1)),
        )

    @Test
    fun ripplingObserver() = card(
        name = "Rippling Observer",
        cost = "{4}{U}",
        type = "Creature — Serpent",
        pt = "3/5",
        text = """
            Flying
            {2}: This creature gets +1/-1 until end of turn.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(Keyword.Flying, activated(cost(mana("{2}")), pump(Objects.Self, 1, -1)), Keyword.Typecycling(and(ObjectFilter.Super(Supertype.Basic), land), "Basic landcycling", cost(mana("{2}"))))

    @Test
    fun recallToHand() = card(
        name = "Recall to Hand",
        cost = "{U}",
        type = "Instant",
        text = "Return target creature to its owner's hand.",
    ) parsesTo abilities(spell(move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))))
}
