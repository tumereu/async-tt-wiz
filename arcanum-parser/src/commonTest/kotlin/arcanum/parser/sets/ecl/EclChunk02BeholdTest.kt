package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 02: behold (CR 701.4). "Behold a Kithkin" reveals a Kithkin card from your hand or chooses a Kithkin you control;
 * as one of two additional costs ("behold a Kithkin or pay {2}"), in a flashback cost ("Behold three Elementals", three different
 * objects), with a creature type chosen as part of the cost ("you may choose a creature type and behold two creatures of that type").
 * "Behold a Kithkin and exile it" exiles the Kithkin you control or the Kithkin card from your hand; the champion's "When this creature
 * leaves the battlefield, return the exiled card to its owner's hand" returns it (CR 607.2a, the exiled card kept with the spell and
 * the permanent it becomes). Also the third resolution of an ability this turn and a copy token with a sacrifice trigger (CR 707.9b).
 */
class EclChunk02BeholdTest {
    // CR 701.4a, 607.2a
    @Test
    fun exemplarOfTheHamlet() = card(
        name = "Exemplar of the Hamlet",
        cost = "{3}{W}",
        type = "Creature — Kithkin Knight",
        text = """
            Flash
            As an additional cost to cast this spell, behold a Kithkin and exile it. (Exile a Kithkin you control or a Kithkin card from your hand.)
            Other Kithkin you control get +1/+1.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flash,
        beholdAndExile(sub("Kithkin")),
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Kithkin"), you))),
        returnTheExiledCard,
    )

    // CR 701.4a, 601.2f
    @Test
    fun hearthvaleHopeful() = card(
        name = "Hearthvale Hopeful",
        cost = "{W}",
        type = "Creature — Kithkin Citizen",
        text = """
            As an additional cost to cast this spell, behold a Kithkin or pay {2}. (To behold a Kithkin, choose a Kithkin you control or reveal a Kithkin card from your hand.)
            Whenever another creature you control enters, this creature gets +1/+1 until end of turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        additionalCostOneOf(cost(behold(sub("Kithkin"))), manaCost("{2}")),
        triggered(anotherCreatureYouControlEnters, pump(Objects.Self, 1, 1)),
    )

    // CR 122.1d: stun counters
    @Test
    fun exemplarsOfTheReef() = card(
        name = "Exemplars of the Reef",
        cost = "{3}{U}",
        type = "Creature — Merfolk Soldier",
        text = """
            As an additional cost to cast this spell, behold a Merfolk and exile it. (Exile a Merfolk you control or a Merfolk card from your hand.)
            Whenever this creature enters or becomes tapped, tap up to one target creature and put a stun counter on it.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
        pt = "4/6",
    ) parsesTo abilities(
        beholdAndExile(sub("Merfolk")),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.Tap(ObjectFilter.Is(Objects.Self)))),
            *tapAndStun(target("creature")).toTypedArray(), targets = listOf(slot("creature", creature, min = 0))),
        returnTheExiledCard,
    )

    @Test
    fun brightfinTutor() = card(
        name = "Brightfin Tutor",
        cost = "{1}{U}",
        type = "Creature — Merfolk Wizard",
        text = """
            As an additional cost to cast this spell, behold a Merfolk or pay {2}. (To behold a Merfolk, choose a Merfolk you control or reveal a Merfolk card from your hand.)
            When this creature enters, create a 1/1 white and blue Merfolk creature token.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        additionalCostOneOf(cost(behold(sub("Merfolk"))), manaCost("{2}")),
        triggered(entersSelf, create(merfolk)),
    )

    @Test
    fun mudknotHexflinger() = card(
        name = "Mudknot Hexflinger",
        cost = "{B}",
        type = "Creature — Goblin Warlock",
        text = """
            As an additional cost to cast this spell, behold a Goblin or pay {2}. (To behold a Goblin, choose a Goblin you control or reveal a Goblin card from your hand.)
            This creature can't block.
            When this creature dies, destroy target creature an opponent controls with power 2 or less.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        additionalCostOneOf(cost(behold(sub("Goblin"))), manaCost("{2}")),
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(diesSelf, destroy(target("creature")), targets = listOf(slot("creature", and(creature, opponents, power(Comparison.AtMost, 2))))),
    )

    // CR 603.10a: "it" is the Elemental that entered, which deals the damage
    @Test
    fun exemplarOfTheTrail() = card(
        name = "Exemplar of the Trail",
        cost = "{3}{R}",
        type = "Creature — Elemental Sorcerer",
        text = """
            As an additional cost to cast this spell, behold an Elemental and exile it. (Exile an Elemental you control or an Elemental card from your hand.)
            Whenever another Elemental you control enters, it deals damage equal to its power to each opponent.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
        pt = "7/3",
    ) parsesTo abilities(
        beholdAndExile(sub("Elemental")),
        triggered(enters(and(another, sub("Elemental"), you)),
            damage(power(Objects.EventObject(EventRole.After)), eachOpponent, source = Objects.EventObject(EventRole.After))),
        returnTheExiledCard,
    )

    // CR 702.34a, 701.4a: three different Elementals; CR 707.9b: the copy also has haste and the trigger
    @Test
    fun stokeTheHiddenBlaze() = card(
        name = "Stoke the Hidden Blaze",
        cost = "{3}{R}",
        type = "Kindred Sorcery — Elemental",
        text = """
            Create a token that's a copy of target creature you control, except it has haste and "At the beginning of the end step, sacrifice this token."
            Flashback—{1}{R}, Behold three Elementals. (You may cast this card from your graveyard for its flashback cost. Then exile it. To behold an Elemental, choose an Elemental you control or reveal an Elemental card from your hand.)
        """,
    ) parsesTo abilities(
        spell(create(TokenSpec.CopyOf(target("creature"), listOf(CopyException.AddAbilities(listOf(Keyword.Haste,
            triggered(eachEndStep, sacrificeIt)))))), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Flashback(cost(mana("{1}{R}"), behold(sub("Elemental"), count = 3))),
    )

    // CR 603.2: the third time it has resolved this turn
    @Test
    fun spiritglowSearcher() = card(
        name = "Spiritglow Searcher",
        cost = "{R}",
        type = "Creature — Elemental Sorcerer",
        text = """
            As an additional cost to cast this spell, behold an Elemental or pay {2}. (To behold an Elemental, choose an Elemental you control or reveal an Elemental card from your hand.)
            {R}: Target creature you control gains trample until end of turn. If this is the third time this ability has resolved this turn, add {R}{R}{R}{R}.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        additionalCostOneOf(cost(behold(sub("Elemental"))), manaCost("{2}")),
        activated(manaCost("{R}"),
            gains(target("creature"), Keyword.Trample),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(3)),
                then = listOf(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{R}{R}{R}{R}")!!)), Players.You)))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 601.2f: the creature type is chosen as the optional cost is paid; CR 701.4a: two creatures of that type
    @Test
    fun starlitHomecoming() = card(
        name = "Starlit Homecoming",
        cost = "{X}{G}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may choose a creature type and behold two creatures of that type.
            Search your library for a creature card with mana value X or less, reveal it, put it into your hand, then shuffle. If this spell's additional cost was paid and the revealed card is the chosen type, put that card onto the battlefield instead of putting it into your hand.
        """,
    ) parsesTo abilities(
        additionalCost(CostPart.Choose(arcanum.api.Choice.CreatureType, bind = "type"), behold(and(creature, ofThatType), count = 2), optional = true),
        spell(
            search(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X"))), reveal = true),
            Step.If(Condition.And(listOf(additionalCostPaid, Condition.ObjectMatches(Objects.Bound("found"), ofThatType))),
                then = listOf(move(Objects.Bound("found"), Zone.Battlefield)),
                otherwise = listOf(move(Objects.Bound("found"), Zone.Hand))),
            shuffle,
            announced = listOf("X")),
    )

    @Test
    fun exemplarsOfTheFlawless() = card(
        name = "Exemplars of the Flawless",
        cost = "{3}{G}",
        type = "Creature — Elf Warrior",
        text = """
            As an additional cost to cast this spell, behold an Elf and exile it. (Exile an Elf you control or an Elf card from your hand.)
            Whenever you cast a creature spell, draw a card.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        beholdAndExile(sub("Elf")),
        triggered(youCast(creature), draw(1)),
        returnTheExiledCard,
    )

    // CR 602.5b: "Activate only if …"
    @Test
    fun lysValenEnvoy() = card(
        name = "Lys Valen Envoy",
        cost = "{1}{G}",
        type = "Creature — Elf Advisor",
        text = """
            As an additional cost to cast this spell, behold an Elf or pay {2}. (To behold an Elf, choose an Elf you control or reveal an Elf card from your hand.)
            {T}: Add {G}{G}. Activate only if there is an Elf card in your graveyard.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        additionalCostOneOf(cost(behold(sub("Elf"))), manaCost("{2}")),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{G}{G}")!!)), Players.You)),
            rules = anyTime.copy(condition = Condition.Exists(inYourGraveyard(sub("Elf")), atLeast = 1))),
    )
}
