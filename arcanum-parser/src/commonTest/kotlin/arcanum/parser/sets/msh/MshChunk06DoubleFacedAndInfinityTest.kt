package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 06: modal double-faced legends that transform (CR 712.3: either face can be cast, and an ability turns
 * it over, CR 701.27a) and an Infinity Stone: "Harness [this permanent]" (CR 701.64a) and its ∞ ability, which works only while it
 * is harnessed (CR 702.186b). Opponents unable to cast spells during your turn; damage to your creatures dealt again once each turn
 * (CR 603.2h); an additional combat phase (CR 500.8); prevention of all damage to a creature (CR 615.1).
 */
class MshChunk06DoubleFacedAndInfinityTest {
    private val opponentsCantCastDuringYourTurn = Ability.Static(listOf(Mechanic.CantCast(ObjectFilter.Any)),
        affects = Affected.Players(Players.Each(PlayerFilter.Opponent)), condition = Condition.YourTurn)
    private fun transform(cost: String) = activated(manaCost(cost), Step.Do(Effect.Transform(Objects.Self)), rules = asSorcery)

    @Test
    fun counselorWrenHale() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Counselor Wren Hale",
            cost = "{1}{W}",
            type = "Legendary Creature — Human Advisor Hero",
            text = """
                Your opponents can't cast spells during your turn.
                {3}{G}{W}{W}: Transform Counselor Wren Hale. Activate only as a sorcery.
            """,
            pt = "2/3",
        ) to abilities(
            opponentsCantCastDuringYourTurn,
            transform("{3}{G}{W}{W}"),
        ),
        // CR 603.2h: "Do this only once each turn"
        card(
            name = "The Emerald Amazon",
            cost = "{3}{G}{W}{W}",
            type = "Legendary Creature — Gamma Hero",
            text = """
                Reach, trample
                Your opponents can't cast spells during your turn.
                Whenever a creature you control is dealt damage, you may have The Emerald Amazon deal that much damage to any target. Do this only once each turn.
            """,
            pt = "6/6",
        ) to abilities(
            Keyword.Reach,
            Keyword.Trample,
            opponentsCantCastDuringYourTurn,
            Ability.Triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = creatureYouControl))), Instructions(targets = listOf(anyTarget),
                steps = listOf(Step.May(listOf(damage(Value.EventAmount, toAny)), player = Players.You))), actionOncePerTurn = true),
        ),
    )

    @Test
    fun lieutenantMaraBell() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Lieutenant Mara Bell",
            cost = "{2}{W}",
            type = "Legendary Creature — Human Hero",
            text = """
                Flying, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
                {2}{R}{W}{W}: Transform Lieutenant Mara Bell. Activate only as a sorcery.
            """,
            pt = "3/3",
        ) to abilities(
            Keyword.Flying,
            Keyword.Prowess,
            transform("{2}{R}{W}{W}"),
        ),
        card(
            name = "Radiant, Living Light",
            cost = "{2}{R}{W}{W}",
            type = "Legendary Creature — Elemental Hero",
            text = """
                Flying, hexproof, prowess
                Whenever you cast a noncreature spell, put a +1/+1 counter on each other creature you control.
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.Flying,
            Keyword.Hexproof,
            Keyword.Prowess,
            triggered(youCast(noncreatureSpell), counters(Objects.Each(and(another, creatureYouControl)))),
        ),
    )

    // CR 107.3a: {X}{X}; CR 500.8: an additional combat phase
    @Test
    fun doctorBrennanBayne() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Doctor Brennan Bayne",
            cost = "{U}",
            type = "Legendary Creature — Human Scientist Hero",
            text = """
                {X}{X}, {T}: Draw X cards. Activate only as a sorcery.
                {2}{R}{R}{G}{G}: Transform Doctor Brennan Bayne. Activate only as a sorcery.
            """,
            pt = "1/1",
        ) to abilities(
            Ability.Activated(cost(mana("{X}{X}"), tap), Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Announced("X")))), announced = listOf("X")), asSorcery),
            transform("{2}{R}{R}{G}{G}"),
        ),
        card(
            name = "The Unstoppable Brute",
            cost = "{2}{R}{R}{G}{G}",
            type = "Legendary Creature — Gamma Berserker Hero",
            text = """
                Reach, trample
                Enrage — Whenever The Unstoppable Brute is dealt damage, put a +1/+1 counter on him. If he's attacking, untap him and there is an additional combat phase after this phase.
            """,
            pt = "8/8",
        ) to abilities(
            Keyword.Reach,
            Keyword.Trample,
            triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
                counters(Objects.EventObject(EventRole.Object)),
                Step.If(Condition.ObjectMatches(Objects.Self, attacking), then = listOf(untapIt(Objects.Self), Step.Do(Effect.ExtraCombat()))),
                abilityWord = "Enrage"),
        ),
    )

    // the rest in a random order (CR 401.4); an artifact put onto the battlefield and, if it's an Equipment, attached
    @Test
    fun antonSteele() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Anton Steele",
            cost = "{1}{U}",
            type = "Legendary Creature — Human Artificer Hero",
            text = """
                {1}, {T}: Look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
                {4}{U}{R}: Transform Anton Steele. Activate only as a sorcery.
            """,
            pt = "1/3",
        ) to abilities(
            activated(cost(mana("{1}"), tap),
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(artifact, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
            transform("{4}{U}{R}"),
        ),
        card(
            name = "The Invincible Steel Sentinel",
            cost = "{4}{U}{R}",
            type = "Legendary Artifact Creature — Human Hero",
            text = """
                Flying, haste
                At the beginning of combat on your turn, you may put an artifact card from your hand onto the battlefield. If it's an Equipment, attach it to The Invincible Steel Sentinel.
            """,
            pt = "5/5",
        ) to abilities(
            Keyword.Flying,
            Keyword.Haste,
            triggered(beginningOfCombatOnYourTurn,
                Step.Choose(Choice.Objects(and(artifact, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
                Step.If(Condition.ObjectMatches(Objects.Bound("put"), equipment),
                    then = listOf(Step.Do(Effect.Attach(Objects.Bound("put"), Recipients(objects = Objects.Self)))))),
        ),
    )

    // each player's second draw; CR 615.1: all damage to it prevented
    @Test
    fun kingTavari() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "King Tavari",
            cost = "{1}{W}{U}",
            type = "Legendary Creature — Human Noble Hero",
            text = """
                Flash
                Whenever a player draws their second card each turn, you draw a card.
                {4}{W}{U}: Transform King Tavari. Activate only as a sorcery.
            """,
            pt = "3/2",
        ) to abilities(
            Keyword.Flash,
            triggered(arcanum.api.Trigger.On(listOf(EventPattern.Draw(PlayerFilter.Any)), nthThisTurn = 2), draw(1)),
            transform("{4}{W}{U}"),
        ),
        card(
            name = "Shadow Panther, Hope Enduring",
            cost = "{4}{W}{U}",
            type = "Legendary Creature — Human Warrior Hero",
            text = """
                Flash
                Double strike
                Prevent all damage that would be dealt to Shadow Panther.
                Whenever Shadow Panther deals combat damage to a player, draw a card.
            """,
            pt = "3/3",
        ) to abilities(
            Keyword.Flash,
            Keyword.DoubleStrike,
            static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))))),
            triggered(combatDamageToAPlayer, draw(1)),
        ),
    )

    // CR 701.64a: harness; CR 702.186b: the ∞ ability works only while it is harnessed (the label's rules)
    @Test
    fun thePsycheJewel() = card(
        name = "The Psyche Jewel",
        cost = "{1}{W}",
        type = "Legendary Artifact — Infinity Stone",
        text = """
            Indestructible
            {T}: Add {W}.
            {5}{W}, {T}: Harness The Psyche Jewel. (Once harnessed, its ∞ ability is active.)
            ∞ — At the beginning of your end step, exile up to one other target nonland permanent you control, then return that card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        activated(cost(tap), Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{W}")!!)), Players.You))),
        activated(cost(mana("{5}{W}"), tap), Step.Do(Effect.Mark(Objects.Self, ObjectMark.Harnessed))),
        Ability.Triggered(stepBegins(arcanum.api.TurnStep.End, PlayerFilter.You), Instructions(
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter, you), min = 0)),
            steps = listOf(move(target("permanent"), Zone.Exile, postBind = "exiled"),
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
            label = AbilityLabel.Infinity),
    )
}
