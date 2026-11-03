package arcanum.parser.sets.ecl

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 07: the double-faced legends that transform at the beginning of your first main phase if you pay a mana of
 * the other face's colour (CR 505.1a, 701.27a, 712). "Whenever this creature enters or transforms into [this face]" (CR 701.27e, the
 * face's name has a comma), "transforms into [this face] and at the beginning of your first main phase" (one ability, two events),
 * X mana of one of two colours, convoke and persist given to other objects, planeswalker loyalty abilities (CR 606) with an emblem
 * of the chosen type (CR 114), a granted combat damage trigger, protection from each colour, and a tapped and attacking copy of the
 * blighted creature (CR 701.68c, 508.4, 707.9b).
 */
class EclChunk07TransformingTest {
    @Test
    fun maewenHamletsHearth() = cardFaces(Layout.Transform,
        card(
            name = "Maewen, Hamlet's Hearth",
            cost = "{2}{W}",
            type = "Legendary Creature — Kithkin Warrior",
            text = """
                Whenever this creature enters or transforms into Maewen, Hamlet's Hearth, create a 1/1 green and white Kithkin creature token.
                At the beginning of your first main phase, you may pay {G}. If you do, transform Maewen.
            """,
            pt = "3/2",
        ) to abilities(
            triggered(entersOrTransformsInto("Maewen, Hamlet's Hearth"), create(kithkin)),
            mayPayToTransform("{G}"),
        ),
        card(
            name = "Maewen, Hilltop's Wit",
            cost = null,
            type = "Legendary Creature — Kithkin Soldier",
            text = """
                {T}: Add X {G} or X {W}, where X is the number of other creatures you control.
                At the beginning of your first main phase, you may pay {W}. If you do, transform Maewen.
            """,
            pt = "3/2",
            colorIndicator = "G",
        ) to abilities(
            // CR 106.1b: X mana, all of one of the two colours
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(count(and(another, creature, you)),
                listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White)))), Players.You))),
            mayPayToTransform("{W}"),
        ),
    )

    @Test
    fun solvanneBearerOfMorning() = cardFaces(Layout.Transform,
        card(
            name = "Solvanne, Bearer of Morning",
            cost = "{3}{W}{W}",
            type = "Legendary Creature — Elemental God",
            text = """
                Flying, lifelink
                Creature spells you cast have convoke. (Your creatures can help cast those spells. Each creature you tap while casting a creature spell pays for {1} or one mana of that creature's color.)
                At the beginning of your first main phase, you may pay {B}. If you do, transform Solvanne.
            """,
            pt = "5/5",
        ) to abilities(
            Keyword.Flying,
            Keyword.Lifelink,
            // CR 702.51a: convoke works while the spell is on the Stack
            static(has(Keyword.Convoke), affects = Objects.Each(and(creature, spellFilter, you))),
            mayPayToTransform("{B}"),
        ),
        card(
            name = "Nocthra, Bearer of Dusk",
            cost = null,
            type = "Legendary Creature — Elemental God",
            text = """
                Flying, lifelink
                Each other nontoken creature you control has persist. (When it dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
                At the beginning of your first main phase, you may pay {W}. If you do, transform Nocthra.
            """,
            pt = "5/5",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            Keyword.Lifelink,
            // CR 702.79a
            static(has(Keyword.Persist), affects = Objects.Each(and(another, not(token), creature, you))),
            mayPayToTransform("{W}"),
        ),
    )

    // CR 606.3: loyalty abilities; CR 114.1: an emblem
    @Test
    fun fennMeadowSovereign() = cardFaces(Layout.Transform,
        card(
            name = "Fenn, Meadow Sovereign",
            cost = "{2}{U}",
            type = "Legendary Planeswalker — Oko",
            text = """
                At the beginning of your first main phase, you may pay {G}. If you do, transform Fenn.
                [+2]: Up to one target creature gains all creature types. (This effect doesn't end.)
                [+1]: Target creature gets -2/-0 until your next turn.
            """,
            loyalty = "3",
        ) to abilities(
            mayPayToTransform("{G}"),
            activated(cost(CostPart.Loyalty(n(2))), doing(Effect.ApplyMechanics(listOf(Mechanic.AllCreatureTypes),
                arcanum.api.Affected.Objects(target("creature")), Duration.Indefinitely)), targets = listOf(slot("creature", creature, min = 0))),
            activated(cost(CostPart.Loyalty(n(1))), doing(Effect.ApplyMechanics(listOf(pt(-2, 0)), arcanum.api.Affected.Objects(target("creature")),
                Duration.UntilYourNextTurn)), targets = listOf(slot("creature", creature))),
        ),
        card(
            name = "Fenn, Gloomvale Heir",
            cost = null,
            type = "Legendary Planeswalker — Oko",
            text = """
                At the beginning of your first main phase, you may pay {U}. If you do, transform Fenn.
                [−1]: Mill three cards. You may put a permanent card from among them into your hand.
                [−3]: Create two 3/3 green Elk creature tokens.
                [−6]: Choose a creature type. You get an emblem with "Creatures you control of the chosen type get +3/+3 and have vigilance and hexproof."
            """,
            loyalty = "3",
            colorIndicator = "G",
        ) to abilities(
            mayPayToTransform("{U}"),
            activated(cost(CostPart.Loyalty(n(-1))), mill(3, postBind = "milled"),
                Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand)),
            activated(cost(CostPart.Loyalty(n(-3))), create(creatureToken("Elk", listOf(Color.Green), 3, 3), n(2))),
            activated(cost(CostPart.Loyalty(n(-6))), chooseCreatureType, doing(Effect.CreateEmblem(listOf(
                static(pt(3, 3), has(Keyword.Vigilance, Keyword.Hexproof), affects = Objects.Each(and(creature, you, ofThatType)))), Players.You))),
        ),
    )

    @Test
    fun tellisRiverwineSage() = cardFaces(Layout.Transform,
        card(
            name = "Tellis, Riverwine Sage",
            cost = "{1}{U}",
            type = "Legendary Creature — Merfolk Wizard",
            text = """
                Tellis can't be blocked.
                Whenever this creature enters or transforms into Tellis, Riverwine Sage, target creature gains "Whenever this creature deals combat damage to a player or planeswalker, draw a card" until end of turn.
                At the beginning of your first main phase, you may pay {W}. If you do, transform Tellis.
            """,
            pt = "2/2",
        ) to abilities(
            static(Mechanic.CantBeBlocked),
            // CR 113.10: in the granted ability "this creature" is the creature that has it
            triggered(entersOrTransformsInto("Tellis, Riverwine Sage"), gains(target("creature"),
                triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(objects = ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                    players = PlayerFilter.Any), combat = true)), draw(1))), targets = listOf(slot("creature", creature))),
            mayPayToTransform("{W}"),
        ),
        card(
            name = "Tellis, Brinewalk Guard",
            cost = null,
            type = "Legendary Creature — Merfolk Rogue",
            text = """
                Tellis can't be blocked.
                Whenever this creature transforms into Tellis, Brinewalk Guard, target creature you control gains protection from each color until your next turn.
                At the beginning of your first main phase, you may pay {U}. If you do, transform Tellis.
            """,
            pt = "2/2",
            colorIndicator = "W",
        ) to abilities(
            static(Mechanic.CantBeBlocked),
            // CR 702.16: protection from each colour is protection from every object that has a colour
            triggered(transformsInto("Tellis, Brinewalk Guard"), doing(Effect.ApplyMechanics(listOf(has(Keyword.Protection(not(ObjectFilter.Colorless)))),
                arcanum.api.Affected.Objects(target("creature")), Duration.UntilYourNextTurn)), targets = listOf(slot("creature", creatureYouControl))),
            mayPayToTransform("{U}"),
        ),
    )

    @Test
    fun nettleFabledElder() = cardFaces(Layout.Transform,
        card(
            name = "Nettle, Fabled Elder",
            cost = "{2}{B}",
            type = "Legendary Creature — Goblin Warlock",
            text = """
                Menace
                Whenever this creature enters or transforms into Nettle, Fabled Elder, return up to one target Goblin card from your graveyard to your hand.
                At the beginning of your first main phase, you may pay {R}. If you do, transform Nettle.
            """,
            pt = "2/1",
        ) to abilities(
            Keyword.Menace,
            triggered(entersOrTransformsInto("Nettle, Fabled Elder"), move(target("cards"), Zone.Hand),
                targets = listOf(slot("cards", inYourGraveyard(sub("Goblin")), min = 0))),
            mayPayToTransform("{R}"),
        ),
        card(
            name = "Nettle, Infamous Granny",
            cost = null,
            type = "Legendary Creature — Goblin Warrior",
            text = """
                Menace
                Whenever Nettle attacks, you may blight 1. If you do, create a tapped and attacking token that's a copy of the blighted creature, except it has "At the beginning of the end step, sacrifice this token."
                At the beginning of your first main phase, you may pay {B}. If you do, transform Nettle.
            """,
            pt = "2/1",
            colorIndicator = "R",
        ) to abilities(
            Keyword.Menace,
            triggered(attacksSelf, *mayPay(blight(1), then = listOf(doing(Effect.CreateToken(
                TokenSpec.CopyOf(Objects.Bound("blighted"), listOf(CopyException.AddAbilities(listOf(triggered(eachEndStep, sacrificeIt))))), n(1), Players.You,
                tapped = true, attacking = arcanum.api.AttackTarget.Chosen)))).toTypedArray()),
            mayPayToTransform("{B}"),
        ),
    )

    @Test
    fun cinnaRelit() = cardFaces(Layout.Transform,
        card(
            name = "Cinna, Relit",
            cost = "{1}{R}",
            type = "Legendary Creature — Elemental Sorcerer",
            text = """
                Whenever this creature enters or transforms into Cinna, Relit, you may discard a card. If you do, draw a card.
                At the beginning of your first main phase, you may pay {U}. If you do, transform Cinna.
            """,
            pt = "1/3",
        ) to abilities(
            triggered(entersOrTransformsInto("Cinna, Relit"), *mayPay(discardACard, then = listOf(draw(1))).toTypedArray()),
            mayPayToTransform("{U}"),
        ),
        card(
            name = "Cinna, Frostbound",
            cost = null,
            type = "Legendary Creature — Elemental Wizard",
            text = """
                Whenever this creature transforms into Cinna, Frostbound and at the beginning of your first main phase, add two mana of any one color. Spend this mana only to cast spells with mana value 4 or greater.
                At the beginning of your first main phase, you may pay {R}. If you do, transform Cinna.
            """,
            pt = "1/3",
            colorIndicator = "U",
        ) to abilities(
            // CR 106.6: mana with a restriction
            triggered(arcanum.api.Trigger.On(listOf(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Cinna, Frostbound"))),
                EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You))),
                doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2)), restriction = and(spellFilter, manaValue(arcanum.api.Comparison.AtLeast, 4))), Players.You))),
            mayPayToTransform("{R}"),
        ),
    )

    @Test
    fun orwenHeartlessGrower() = cardFaces(Layout.Transform,
        card(
            name = "Orwen, Heartless Grower",
            cost = "{2}{G}",
            type = "Legendary Creature — Elf Druid",
            text = """
                Deathtouch
                Whenever this creature enters or transforms into Orwen, Heartless Grower, mill three cards. Then if there is an Elf card in your graveyard, you gain 2 life.
                At the beginning of your first main phase, you may pay {B}. If you do, transform Orwen.
            """,
            pt = "3/4",
        ) to abilities(
            Keyword.Deathtouch,
            triggered(entersOrTransformsInto("Orwen, Heartless Grower"), mill(3),
                Step.If(arcanum.api.Condition.Exists(inYourGraveyard(sub("Elf")), atLeast = 1), then = listOf(gainLife(2)))),
            mayPayToTransform("{B}"),
        ),
        card(
            name = "Orwen, Remorseful Reaper",
            cost = null,
            type = "Legendary Creature — Elf Warlock",
            text = """
                Deathtouch
                Whenever this creature transforms into Orwen, Remorseful Reaper, mill three cards, then you may exile an Elf card from your graveyard. If you do, each opponent loses 2 life.
                At the beginning of your first main phase, you may pay {G}. If you do, transform Orwen.
            """,
            pt = "3/4",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Deathtouch,
            triggered(transformsInto("Orwen, Remorseful Reaper"), mill(3),
                *mayPay(CostPart.Exile(CostObjects.Choose(sub("Elf"), count = n(1)), from = Zone.Graveyard),
                    then = listOf(loseLife(2, Players.Each(PlayerFilter.Opponent)))).toTypedArray()),
            mayPayToTransform("{G}"),
        ),
    )
}
