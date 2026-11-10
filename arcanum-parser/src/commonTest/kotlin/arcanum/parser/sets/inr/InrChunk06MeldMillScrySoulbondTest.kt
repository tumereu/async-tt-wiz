package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.api.AttackTarget
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 06: Meld (CR 701.42, 712.4), Mill (CR 701.17), Scry (CR 701.22), Soulbond (CR 702.95).
 */
class InrChunk06MeldMillScrySoulbondTest {
    private fun partner(name: String) = and(creature, ObjectFilter.Named(name), you, ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun ownsBoth(partner: ObjectFilter) = Condition.And(listOf(Condition.ObjectMatches(Objects.Self, ObjectFilter.OwnedBy(PlayerFilter.You)),
        Condition.Exists(partner, atLeast = 1)))

    /** "exile them, then meld them into [into]": the partner is chosen as the ability resolves (CR 712.4a) */
    private fun meldSteps(partner: ObjectFilter, into: String) = listOf(
        Step.Choose(Choice.Objects(partner, n(1), n(1)), bind = "partner", chooser = Players.You),
        move(Objects.Union(listOf(Objects.Self, Objects.Bound("partner"))), Zone.Exile, postBind = "exiled"),
        doing(Effect.Action(KeywordAction.Meld(Objects.Bound("exiled"), into))))

    // ---- Meld

    // CR 701.42a, 712.4a: no payment, at the beginning of your end step; CR 603.4 (intervening if); CR 101.2 (the back face's restriction)
    @Test
    fun gildaTheShatteredEdge() {
        val bruna = partner("Brunhild, the Dimming Light")
        cardFaces(Layout.Meld("Brunhild, the Dimming Light"),
            card(
                name = "Gilda, the Shattered Edge",
                cost = "{2}{W}{W}",
                type = "Legendary Creature — Angel Horror",
                pt = "4/3",
                text = """
                    Flying, first strike, lifelink
                    At the beginning of your end step, if you both own and control Gilda and a creature named Brunhild, the Dimming Light, exile them, then meld them into Briseis, Voice of Dread.
                """,
            ) to abilities(Keyword.Flying, Keyword.FirstStrike, Keyword.Lifelink,
                triggered(stepBegins(TurnStep.End, PlayerFilter.You), *meldSteps(bruna, "Briseis, Voice of Dread").toTypedArray(), interveningIf = ownsBoth(bruna))),
            card(
                name = "Briseis, Voice of Dread",
                cost = null,
                type = "Legendary Creature — Eldrazi Angel",
                colorIndicator = "W",
                pt = "9/10",
                text = """
                    Flying, first strike, vigilance, lifelink
                    Your opponents can't cast spells with mana value 3 or less.
                """,
            ) to abilities(Keyword.Flying, Keyword.FirstStrike, Keyword.Vigilance, Keyword.Lifelink,
                Ability.Static(listOf(Mechanic.CantCast(and(spellFilter, manaValue(Comparison.AtMost, 3)))), affects = Affected.Players(Players.Each(PlayerFilter.Opponent)))),
        )
    }

    // CR 701.42a, 712.4a: at the beginning of combat on your turn
    @Test
    fun gauntMice() {
        val gleaners = partner("Midnight Gleaners")
        cardFaces(Layout.Meld("Midnight Gleaners"),
            card(
                name = "Gaunt Mice",
                cost = "{1}{B}",
                type = "Creature — Rat",
                pt = "2/1",
                text = "At the beginning of combat on your turn, if you both own and control this creature and a creature named Midnight Gleaners, exile them, then meld them into Chittering Swarm.",
            ) to abilities(
                triggered(beginningOfCombatOnYourTurn, *meldSteps(gleaners, "Chittering Swarm").toTypedArray(), interveningIf = ownsBoth(gleaners))),
            card(
                name = "Chittering Swarm",
                cost = null,
                type = "Creature — Eldrazi Horror",
                colorIndicator = "B",
                pt = "5/6",
                text = """
                    Haste
                    Menace (This creature can't be blocked except by two or more creatures.)
                    When this creature enters, other creatures you control get +1/+0 and gain menace until end of turn.
                """,
            ) to abilities(Keyword.Haste, Keyword.Menace,
                triggered(entersSelf, pump(Objects.Each(and(another, creature, you)), 1, 0, has(Keyword.Menace)))),
        )
    }

    // CR 701.42a, 602.2: the condition is checked as the ability resolves (an activated ability has no intervening if)
    @Test
    fun harrowRamparts() {
        val garrison = partner("Harrow Garrison")
        cardFaces(Layout.Meld("Harrow Garrison"),
            card(
                name = "Harrow Ramparts",
                cost = null,
                type = "Land",
                text = """
                    {T}: Add {C}.
                    {R}, {T}: Target creature gains haste until end of turn.
                    {3}{R}{R}, {T}: If you both own and control this land and a creature named Harrow Garrison, exile them, then meld them into Harrow, the Squirming Hamlet.
                """,
            ) to abilities(
                activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))),
                activated(cost(mana("{R}"), tap), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", creature))),
                activated(cost(mana("{3}{R}{R}"), tap), Step.If(ownsBoth(garrison), then = meldSteps(garrison, "Harrow, the Squirming Hamlet"))),
            ),
            card(
                name = "Harrow, the Squirming Hamlet",
                cost = null,
                type = "Legendary Creature — Eldrazi Ooze",
                colorIndicator = "R",
                pt = "7/4",
                text = """
                    Trample, haste
                    Whenever Harrow attacks, create two 3/2 colorless Eldrazi Horror creature tokens that are tapped and attacking.
                """,
            ) to abilities(Keyword.Trample, Keyword.Haste,
                triggered(attacksSelf, doing(Effect.CreateToken(creatureToken("Eldrazi Horror", emptyList(), 3, 2), n(2), Players.You, tapped = true, attacking = AttackTarget.Chosen)))),
        )
    }

    // ---- Mill

    // CR 701.17a, 712.9 (transform): the milled card is checked as it is in the graveyard
    @Test
    fun aberrantScholar() = cardFaces(Layout.Transform,
        card(
            name = "Aberrant Scholar",
            cost = "{3}{U}",
            type = "Creature — Human Insect",
            pt = "3/2",
            text = """
                Flying
                At the beginning of your upkeep, mill a card. If an instant or sorcery card was milled this way, transform this creature. (To mill a card, put the top card of your library into your graveyard.)
            """,
        ) to abilities(Keyword.Flying,
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), mill(1, postBind = "milled"),
                Step.If(Condition.ObjectMatches(Objects.Bound("milled"), or(instant, sorcery)), then = listOf(transformSelf)))),
        card(
            name = "Perfected Shape",
            cost = null,
            type = "Creature — Insect Horror",
            colorIndicator = "U",
            pt = "5/4",
            text = "Flying",
        ) to abilities(Keyword.Flying),
    )

    // CR 701.17a, 701.27a, 508.1d (the requirement applies to each creature the opponents control)
    @Test
    fun grizzledFisher() = cardFaces(Layout.Transform,
        card(
            name = "Grizzled Fisher",
            cost = "{2}{U}",
            type = "Creature — Human",
            pt = "2/3",
            text = "{T}: Mill two cards. Then if there is a colorless creature card in your graveyard, transform this creature. (To mill two cards, put the top two cards of your library into your graveyard.)",
        ) to abilities(
            activated(cost(tap), mill(2),
                Step.If(Condition.Exists(inYourGraveyard(ObjectFilter.Colorless, creature), atLeast = 1), then = listOf(transformSelf)))),
        card(
            name = "Grisly Eelfish",
            cost = null,
            type = "Creature — Eldrazi Fish",
            colorIndicator = "U",
            pt = "4/5",
            text = "{6}: Creatures your opponents control attack this turn if able.",
        ) to abilities(
            activated(cost(mana("{6}")), untilEndOfTurn(Objects.Each(creatureOpponentControls), Mechanic.MustAttack()))),
    )

    // CR 701.17a: mill three, then a may-return; the graveyard is searched after the mill
    @Test
    fun hermitFarmer() = card(
        name = "Hermit Farmer",
        cost = "{2}{G}",
        type = "Creature — Human Peasant",
        pt = "2/3",
        text = "When this creature enters, mill three cards, then you may return a land card from your graveyard to your hand. (To mill three cards, put the top three cards of your library into your graveyard.)",
    ) parsesTo abilities(
        triggered(entersSelf, mill(3),
            Step.Choose(Choice.Objects(inYourGraveyard(land), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    // CR 601.2, 305.1 (cast from the graveyard), once during each of your turns (a once-per-turn permission, CR 603.4 not involved)
    @Test
    fun gisaAndGerda() = card(
        name = "Gisa and Gerda",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "4/4",
        text = """
            When Gisa and Gerda enters, mill four cards.
            Once during each of your turns, you may cast a Zombie creature spell from your graveyard.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(4)),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how = PlayKind.Cast, perTurn = 1, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(and(sub("Zombie"), creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            condition = Condition.YourTurn),
    )

    // CR 701.17a, 122.1: counters for each creature card in the graveyard after the mill
    @Test
    fun mossgrafMillipede() = card(
        name = "Mossgraf Millipede",
        cost = "{4}{G}",
        type = "Creature — Insect Horror",
        pt = "2/2",
        text = "When this creature enters, mill three cards, then put a +1/+1 counter on this creature for each creature card in your graveyard. (To mill three cards, put the top three cards of your library into your graveyard.)",
    ) parsesTo abilities(triggered(entersSelf, mill(3), counters(Objects.Self, count(inYourGraveyard(creature)))))

    // CR 604.3, 208.2a (a characteristic-defining ability), 701.17a
    @Test
    fun splinterwraith() = card(
        name = "Splinterwraith",
        cost = "{2}{G}",
        type = "Creature — Elemental",
        pt = "*/*",
        text = """
            Trample
            Splinterwraith's power and toughness are each equal to the number of creature cards in your graveyard.
            At the beginning of your upkeep, mill two cards. (Put the top two cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(Keyword.Trample,
        Ability.Static(listOf(Mechanic.SetPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature)))),
            affects = Affected.Objects(Objects.Self), characteristicDefining = true),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), mill(2)),
    )

    // CR 701.17a, 602.5d (sorcery speed), 111.4
    @Test
    fun vilespawnWeaver() = card(
        name = "Vilespawn Weaver",
        cost = "{G}{U}",
        type = "Creature — Spider",
        pt = "2/3",
        text = """
            Reach
            At the beginning of your upkeep, mill a card. (Put the top card of your library into your graveyard.)
            {2}{G}{U}, {T}, Sacrifice this creature: Create a 1/1 green Insect creature token for each creature card in your graveyard. Activate only as a sorcery.
        """,
    ) parsesTo abilities(Keyword.Reach,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), mill(1)),
        activated(cost(mana("{2}{G}{U}"), tap, sacrificeSelf),
            create(creatureToken("Insect", listOf(Color.Green), 1, 1), count(inYourGraveyard(creature))), rules = asSorcery),
    )

    // ---- Scry

    // CR 701.22a, 603.3 (the sacrifice trigger), 701.27a (it comes back transformed), 603.7 (delayed trigger), 400.7 (a new object)
    @Test
    fun biolumeRoe() = cardFaces(Layout.Transform,
        card(
            name = "Biolume Roe",
            cost = "{2}{U}",
            type = "Creature — Serpent Egg",
            pt = "0/4",
            text = """
                Defender
                When this creature enters, scry 2.
                When you sacrifice this creature, return it to the battlefield transformed under its owner's control at the beginning of the next end step.
            """,
        ) to abilities(Keyword.Defender,
            triggered(entersSelf, scry(2)),
            triggered(on(arcanum.api.EventPattern.Sacrifice(ObjectFilter.Is(Objects.Self))),
                Step.Bind(Objects.EventObject(arcanum.api.EventRole.After), "sacrificed"),
                doing(Effect.CreateDelayedTrigger(on(arcanum.api.EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Instructions(steps = listOf(
                    Step.Do(Effect.Move(Objects.Bound("sacrificed"), arcanum.api.Destination(Zone.Battlefield, transformed = true, controller = Players.OwnerOf(Objects.Bound("sacrificed"))))))))))),
        card(
            name = "Biolume Eel",
            cost = null,
            type = "Creature — Serpent",
            colorIndicator = "U",
            pt = "4/4",
            text = "Sacrifice two Islands: This creature can't be blocked this turn.",
        ) to abilities(
            activated(cost(CostPart.Sacrifice(arcanum.api.CostObjects.Choose(sub("Island"), count = n(2)))),
                untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked))),
    )

    // CR 701.21a, 603.2 (the trigger watches sacrifices), 701.22a (scry after the life)
    @Test
    fun fleshweaver() = card(
        name = "Fleshweaver",
        cost = "{W}{B}",
        type = "Creature — Human Assassin",
        pt = "2/2",
        text = """
            Whenever you sacrifice another creature, you gain 1 life and scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {1}, Sacrifice another creature: This creature gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(on(arcanum.api.EventPattern.Sacrifice(and(another, creature, you))), gainLife(1), scry(1)),
        activated(cost(mana("{1}"), sacrificeA(and(another, creature))), pump(Objects.Self, 2, 2)),
    )

    // CR 606.4 (a loyalty ability), 701.22a, 121.1
    @Test
    fun jaceUnwinderOfSecrets() = card(
        name = "Jace, Unwinder of Secrets",
        cost = "{3}{U}{U}",
        type = "Legendary Planeswalker — Jace",
        loyalty = "5",
        text = """
            [+1]: Scry 1, then draw a card.
            [−2]: Return target creature to its owner's hand.
            [−8]: You get an emblem with "Whenever an opponent casts their first spell each turn, counter that spell."
        """,
    ) parsesTo abilities(
        loyalty(1, scry(1), draw(1)),
        loyalty(-2, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))),
        loyalty(-8, doing(Effect.CreateEmblem(listOf(Ability.Triggered(
            arcanum.api.Trigger.On(listOf(arcanum.api.EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)), nthThisTurn = 1),
            Instructions(steps = listOf(doing(Effect.Counter(Objects.EventObject(arcanum.api.EventRole.Object))))))), Players.You))),
    )

    // ---- Soulbond

    private val pair = Affected.Objects(Objects.Union(listOf(Objects.Self, Objects.PairedWith(Objects.Self))))
    private val paired = Condition.ObjectMatches(Objects.Self, ObjectFilter.Paired)

    // CR 702.95b: each of those creatures has "[ability]": an ability that exiles and returns the creature (CR 400.7)
    @Test
    fun deadeyePilot() = card(
        name = "Deadeye Pilot",
        cost = "{4}{U}{U}",
        type = "Creature — Spirit",
        pt = "5/5",
        text = """
            Soulbond (You may pair this creature with another unpaired creature when either enters. They remain paired for as long as you control both of them.)
            As long as Deadeye Pilot is paired with another creature, each of those creatures has "{1}{U}: Exile this creature, then return it to the battlefield under your control."
        """,
    ) parsesTo abilities(Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(activated(cost(mana("{1}{U}")),
            move(Objects.Self, Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.You))))), pair, condition = paired),
    )

    // CR 702.95b: both creatures have haste
    @Test
    fun lightningMasher() = card(
        name = "Lightning Masher",
        cost = "{1}{R}",
        type = "Creature — Human Berserker",
        pt = "2/1",
        text = """
            Soulbond (You may pair this creature with another unpaired creature when either enters. They remain paired for as long as you control both of them.)
            As long as this creature is paired with another creature, both creatures have haste.
        """,
    ) parsesTo abilities(Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), pair, condition = paired),
    )
}
