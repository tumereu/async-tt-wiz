package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Duration
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MOM chunk 17: a damage spell with an optional put-back and draw, destroy and draw, counters and draw triggers, drains, a top-of-library
 * look and cast, lords, blink, exile-and-play, graveyard-return reflexive triggers, removing counters, tap and untap.
 */
class MomChunk17DamageDestroyDrawAndMoreTest {
    private val enteredOne = Objects.EventObject(EventRole.After)
    private val eachPlayer = Players.Each(PlayerFilter.Any)
    private val eachOpponentPlayers = Players.Each(PlayerFilter.Opponent)
    private fun whenThisEnters(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) = triggered(entersSelf, *steps, targets = targets)
    private fun steps(vararg steps: Step) = Instructions(steps = steps.toList())

    // CR 701.7 (destroy), 120.3 (damage), 608.2c ("if you do")
    @Test
    fun volcanicSpite() = card(
        name = "Cinder Rebuke", cost = "{1}{R}", type = "Instant",
        text = "Cinder Rebuke deals 3 damage to target creature, planeswalker, or battle. You may put a card from your hand on the bottom of your library. If you do, draw a card.",
    ) parsesTo abilities(
        spell(damage(3, toObjects(target("permanent"))),
            Step.Pay(steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    bind = "put", chooser = Players.You),
                move(Objects.Bound("put"), Zone.Library, position = -1)), payer = Players.You, bind = "bottom"),
            Step.If(Condition.Did("bottom"), then = listOf(draw(1))),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker), battle)))),
    )

    // CR 701.8
    @Test
    fun atraxasFall() = card(
        name = "Verdant Reckoning", cost = "{1}{G}", type = "Sorcery",
        text = "Destroy target artifact, battle, enchantment, or creature with flying.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, battle, enchantment, and(creature, kw(Keyword.Flying)))))),
    )

    // CR 701.8, 120.3 (the creature was dealt damage by any source)
    @Test
    fun mirrodinAvenged() = card(
        name = "Ashen Avenger", cost = "{B}", type = "Instant",
        text = """
            Destroy target creature that was dealt damage this turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")), draw(1),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(
                source = ObjectFilter.Any, to = RecipientFilter()), EventRole.Object))))),
    )

    // CR 603.2 (counters put by any source), 603.2i
    @Test
    fun duskLegionDuelist() = card(
        name = "Scarred Duelist", cost = "{1}{W}", type = "Creature — Vampire Soldier", pt = "2/2",
        text = """
            Vigilance
            Whenever one or more +1/+1 counters are put on this creature, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(Trigger.On(listOf(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1)), oneOrMore = true), draw(1), perTurn = 1),
    )

    // CR 121.2
    @Test
    fun faerieMastermind() = card(
        name = "Hushwing Mastermind", cost = "{1}{U}", type = "Creature — Faerie Rogue", pt = "2/1",
        text = """
            Flash
            Flying
            Whenever an opponent draws their second card each turn, you draw a card.
            {3}{U}: Each player draws a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.Opponent)), nthThisTurn = 2), draw(1)),
        activated(manaCost("{3}{U}"), draw(1, eachPlayer)),
    )

    // CR 702.20 (vigilance), 602.5 (a tap ability)
    @Test
    fun furtiveAnalyst() = card(
        name = "Quiet Analyst", cost = "{2}{U}", type = "Creature — Human Wizard", pt = "1/4",
        text = """
            Vigilance
            {2}, {T}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(mana("{2}"), tap), draw(1), discard(1)),
    )

    // CR 119.3, 121.1
    @Test
    fun phyrexianGargantua() = card(
        name = "Hollow Colossus", cost = "{4}{B}{B}", type = "Creature — Phyrexian Horror", pt = "4/4",
        text = "When this creature enters, you draw two cards and you lose 2 life.",
    ) parsesTo abilities(whenThisEnters(draw(2), loseLife(2)))

    // CR 603.4 is not used: the power is checked on resolution, as the creature entered (CR 608.2h)
    @Test
    fun tributeToTheWorldTree() = card(
        name = "Grove Tribute", cost = "{G}{G}{G}", type = "Enchantment",
        text = "Whenever a creature you control enters, draw a card if its power is 3 or greater. Otherwise, put two +1/+1 counters on it.",
    ) parsesTo abilities(
        triggered(enters(and(creature, you)),
            Step.If(Condition.ObjectMatches(enteredOne, ObjectFilter.Power(Comparison.AtLeast, n(3))),
                then = listOf(draw(1)), otherwise = listOf(counters(enteredOne, 2)))),
    )

    // CR 613.1f (trample granted), 702.19, 603.6a, 702.10 (haste)
    @Test
    fun surrakAndGoreclaw() = card(
        name = "Boulder and Maw", cost = "{4}{G}{G}", type = "Legendary Creature — Human Bear", pt = "6/5",
        text = """
            Trample
            Other creatures you control have trample.
            Whenever another nontoken creature you control enters, put a +1/+1 counter on it. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(has(Keyword.Trample), affects = Objects.Each(and(another, creature, you))),
        triggered(enters(and(another, not(token), creature, you)), counters(enteredOne), gains(enteredOne, Keyword.Haste)),
    )

    // CR 119.3, 118.8 (sacrifice as a cost)
    @Test
    fun dregRecycler() = card(
        name = "Ragged Recycler", cost = "{1}{B}", type = "Creature — Phyrexian Beast", pt = "2/2",
        text = "{T}, Sacrifice an artifact or creature: Each opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        activated(cost(tap, sacrificeA(or(artifact, creature))), loseLife(1, eachOpponentPlayers), gainLife(1)),
    )

    // CR 700.4 (dies)
    @Test
    fun etchedFamiliar() = card(
        name = "Gilded Familiar", cost = "{2}{B}", type = "Artifact Creature — Phyrexian Fox", pt = "3/2",
        text = "When this creature dies, each opponent loses 2 life and you gain 2 life.",
    ) parsesTo abilities(triggered(diesSelf, loseLife(2, eachOpponentPlayers), gainLife(2)))

    // CR 401.5 (look at the top card), 601.3 (cast from the library), 702.2 / 702.9 (the spell's keywords)
    @Test
    fun errantAndGiada() = card(
        name = "Seer and Guide", cost = "{1}{W}{U}", type = "Legendary Creature — Human Angel", pt = "2/3",
        text = """
            Flash
            Flying
            You may look at the top card of your library any time.
            You may cast spells with flash or flying from the top of your library.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(cardFilter, or(kw(Keyword.Flash), kw(Keyword.Flying)), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 613.4c (+1/+1 to the other Knights only), 701.26 (tap)
    @Test
    fun marshalOfZhalfir() = card(
        name = "Marshal of Dunmere", cost = "{W}{U}", type = "Creature — Human Knight", pt = "2/2",
        text = """
            Other Knights you control get +1/+1.
            {W}{U}, {T}: Tap another target creature.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Knight"), you))),
        activated(cost(mana("{W}{U}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", and(another, creature)))),
    )

    // CR 400.7, 603.7 (the delayed return), 110.2 (under its owner's control)
    @Test
    fun guardianOfGhirapur() = card(
        name = "Guardian of Orrin", cost = "{2}{W}", type = "Creature — Angel", pt = "3/3",
        text = """
            Flying
            When this creature enters, exile up to one other target creature or artifact you control. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, PlayerFilter.Any)),
                steps(Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
            )),
            targets = listOf(slot("permanent", and(another, or(creature, artifact), you), min = 0)),
        ),
    )

    // CR 400.7 (a new object), 110.2
    @Test
    fun scrollshift() = card(
        name = "Pagefold", cost = "{2}{W}", type = "Instant",
        text = """
            Exile up to one target artifact, creature, or enchantment you control, then return it to the battlefield under its owner's control.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
            draw(1),
            targets = listOf(slot("permanent", and(or(artifact, creature, enchantment), you), min = 0)),
        ),
    )

    // CR 406.3 (exiled cards may be played), 611.2 (until the end of your next turn)
    @Test
    fun wrennsResolve() = card(
        name = "Ember Resolve", cost = "{1}{R}", type = "Sorcery",
        text = "Exile the top two cards of your library. Until the end of your next turn, you may play those cards.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
        ),
    )

    // CR 602.5d (activate only as a sorcery), 113.6 (works from the graveyard), 118.8 (exile as a cost)
    @Test
    fun placidRottentail() = card(
        name = "Mossy Rottentail", cost = "{G}", type = "Creature — Fungus Rabbit", pt = "1/1",
        text = """
            Vigilance
            {2}{G}, Exile this card from your graveyard: Put two +1/+1 counters on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(mana("{2}{G}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), counters(target("creature"), 2),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 602.5d, 701.21 (sacrifice as a cost; "another")
    @Test
    fun stormclawRager() = card(
        name = "Gnarlclaw Rager", cost = "{1}{B}{R}", type = "Creature — Ogre Warrior", pt = "2/2",
        text = "{1}, Sacrifice another creature or artifact: Put a +1/+1 counter on this creature and draw a card. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(and(another, or(creature, artifact)))), counters(Objects.Self, 1), draw(1), rules = asSorcery),
    )

    // CR 603.12 (a reflexive trigger), 118.8
    @Test
    fun kroxaAndKunoros() = card(
        name = "Rhaxa and Kimora", cost = "{3}{R}{W}{B}", type = "Legendary Creature — Elder Giant Dog", pt = "6/6",
        text = """
            Vigilance, menace, lifelink
            Whenever Rhaxa and Kimora enters or attacks, you may exile five cards from your graveyard. When you do, return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance, Keyword.Menace, Keyword.Lifelink,
        triggered(entersOrAttacksSelf,
            Step.Pay(cost(CostPart.Exile(CostObjects.Choose(cardFilter, count = n(5)), from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature))))))),
    )

    // CR 603.12, 205.1b (a type added)
    @Test
    fun terrorOfTowashi() = card(
        name = "Terror of Dunmere", cost = "{2}{B}{B}", type = "Creature — Phyrexian Ogre", pt = "4/3",
        text = """
            Deathtouch
            Whenever this creature attacks, you may pay {3}{B}. When you do, return target creature card from your graveyard to the battlefield. It's a Phyrexian in addition to its other types.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(attacksSelf, Step.Pay(manaCost("{3}{B}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(
                move(target("card"), Zone.Battlefield, postBind = "returned"),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Phyrexian"))))),
                    Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
                targets = listOf(slot("card", inYourGraveyard(creature))))))),
    )

    // CR 122.8 ("up to"): the amount is chosen on resolution
    @Test
    fun renderInert() = card(
        name = "Render Dull", cost = "{2}{B}", type = "Sorcery",
        text = """
            Remove up to five counters from target permanent.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(Step.Choose(Choice.Number(n(0), n(5)), bind = "removed", chooser = Players.You),
            doing(Effect.RemoveCounters(target("permanent"), null, Value.Bound("removed"))), draw(1),
            targets = listOf(slot("permanent", permanentFilter))),
    )

    // CR 122.1c (stun counters), 608.2c (the condition checked on resolution)
    @Test
    fun protocolKnight() = card(
        name = "Protocol Squire", cost = "{3}{U}", type = "Creature — Human Knight", pt = "3/4",
        text = "When this creature enters, tap target creature an opponent controls. Put a stun counter on that creature if you control another Knight. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        whenThisEnters(
            tapIt(target("creature")),
            Step.If(youControl(and(another, sub("Knight")), 1), then = listOf(counters(target("creature"), 1, CounterKind.Stun))),
            targets = listOf(slot("creature", and(creature, opponents))),
        ),
    )

    // CR 701.26b (untap), 115.1 ("up to one")
    @Test
    fun zephyrWinder() = card(
        name = "Zephyr Binder", cost = "{1}{U}", type = "Creature — Elemental", pt = "2/1",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, untap up to one target creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, untapIt(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
    )
}
