package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 13: green cards: damage kept through cleanup (CR 514.2) and damage redirected (CR 614.1a), each Bird dealing damage
 * by its power, returning to hand unless it entered this turn, a kicked spell dealing twice the damage, combat damage prevented, doubled
 * +1/+1 counters, a granted dies trigger, searches for basic lands or Towns (different names), a reflexive "when you search", one more
 * token when tokens are created, an enchantment that becomes a creature for good, doubled power, the first Human spell each turn, a land
 * milled into hand, playing lands and Bird spells from the top of the library, triggers from lands and Birds entering doubled (CR 603.2d).
 */
class FinChunk13GreenTest {
    // CR 514.2, 614.1a, 702.21a
    @Test
    fun ancientTortoise() = card(
        name = "Primeval Tortoise",
        cost = "{5}{G}{G}{G}",
        type = "Creature — Turtle",
        pt = "8/20",
        text = """
            Vigilance, ward {3}
            Damage isn't removed from this creature during cleanup steps.
            All damage that would be dealt to you and other permanents you control is dealt to this creature instead.
            When this creature dies, exile it and create ten tapped Treasure tokens.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Ward(manaCost("{3}")),
        static(Mechanic.DamageNotRemovedInCleanup),
        static(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = and(another, permanentFilter, you), players = PlayerFilter.You)),
            modify = listOf(Modification.ChangeRecipient(Recipients(objects = Objects.Self))))),
        triggered(diesSelf, move(enteredOne, Zone.Exile), create(treasure, n(10), tapped = true)),
    )

    @Test
    fun bardAndBirdie() = card(
        name = "Bard and Birdie",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Human Bird",
        pt = "4/3",
        text = """
            Affinity for Birds (This spell costs {1} less to cast for each Bird you control.)
            When Bard and Birdie enters, each other Bird you control deals damage equal to its power to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Bird"), "Birds"),
        triggered(entersSelf, Step.ForEach(Each.Objects(Objects.Each(and(another, sub("Bird"), you))), "bird", steps = listOf(
            damage(power(Objects.Bound("bird")), toObjects(target("creature")), source = Objects.Bound("bird")))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    @Test
    fun cactusSprinter() = card(
        name = "Cactus Sprinter",
        cost = "{G}",
        type = "Creature — Plant",
        pt = "3/3",
        text = """
            Trample
            At the beginning of your end step, if this creature didn't enter the battlefield this turn, return it to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(yourEndStep, move(Objects.Self, Zone.Hand), interveningIf = Condition.Not(Condition.HappenedThisTurn(
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events))),
    )

    // CR 702.33d
    @Test
    fun birdKick() = card(
        name = "Bird Kick",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Kicker—Return a land you control to its owner's hand. (You may return a land you control to its owner's hand in addition to any other costs as you cast this spell.)
            Target creature you control deals damage equal to its power to target creature an opponent controls. If this spell was kicked, the creature you control deals twice that much damage instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(CostPart.ReturnToHand(arcanum.api.CostObjects.Choose(and(land, you), n(1)))))),
        spell(ifThen(kicked,
            damage(Value.Times(power(target("creature")), 2), toObjects(target("second creature")), source = target("creature")),
            otherwise = listOf(damage(power(target("creature")), toObjects(target("second creature")), source = target("creature")))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls))),
    )

    // CR 615.1
    @Test
    fun crystalWeapon() = card(
        name = "Crystal Weapon",
        cost = "{7}{G}{G}",
        type = "Legendary Artifact Creature — Elemental",
        pt = "8/8",
        text = """
            This spell costs {1} less to cast for each permanent card in your graveyard.
            Reach
            Immune — Prevent all combat damage that would be dealt to Crystal Weapon.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(permanentCard))))),
        Keyword.Reach,
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), combat = true)))
            .copy(abilityWord = "Immune"),
    )

    // CR 614.1a, 122.6
    @Test
    fun theGroveCrystal() = card(
        name = "The Grove Crystal",
        cost = "{2}{G}{G}",
        type = "Legendary Artifact",
        text = """
            Green spells you cast cost {1} less to cast.
            If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on that creature instead.
            {4}{G}{G}, {T}: Distribute two +1/+1 counters among one or two target creatures you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Green)), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(Mechanic.Replacement(EventPattern.CountersPut(creatureYouControl, p1p1), modify = listOf(Modification.MultiplyAmount(2)))),
        activated(cost(mana("{4}{G}{G}"), tap), doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
            targets = listOf(slot("creatures", creatureYouControl, min = 1, max = 2).copy(divided = n(2)))),
    )

    /** A granted "When this creature dies, put a number of +1/+1 counters equal to its power on up to one target creature." (its last power) */
    @Test
    fun gaelsFinalStand() = card(
        name = "Gael's Final Stand",
        cost = "{1}{G}",
        type = "Instant",
        text = "Until end of turn, target creature gets +1/+0 and gains \"When this creature dies, put a number of +1/+1 counters equal to its power on up to one target creature.\"",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(1, 0), has(triggered(diesSelf, counters(target("creature"), power(leftOne)),
            targets = listOf(slot("creature", creature, min = 0))))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun grovePulseOchu() = card(
        name = "Grove Pulse Ochu",
        cost = "{G}",
        type = "Creature — Plant Beast",
        pt = "1/1",
        text = """
            Deathtouch
            {8}: Until end of turn, this creature gets +1/+1 for each permanent card in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        activated(manaCost("{8}"), untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(inYourGraveyard(permanentCard)), count(inYourGraveyard(permanentCard))))),
    )

    /** "When you search your library this way, put a +1/+1 counter on target creature you control." (CR 603.12) */
    @Test
    fun wanderersPath() = card(
        name = "Wanderer's Path",
        cost = "{2}{G}",
        type = "Instant",
        text = "Search your library for a basic land card or Town card, put it onto the battlefield tapped, then shuffle. When you search your library this way, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(
        spell(search(or(basicLand, town)), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle,
            reflexive(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)))),
    )

    // CR 614.1a, 111.1
    @Test
    fun quennaGourmet() = card(
        name = "Quenna, Gourmet",
        cost = "{2}{G}",
        type = "Legendary Creature — Qu",
        pt = "2/3",
        text = """
            If one or more tokens would be created under your control, those tokens plus a 1/1 green Frog creature token are created instead.
            {2}, Sacrifice a Frog: Put a +1/+1 counter on Quenna.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.AlsoCreate(creatureToken("Frog", listOf(Color.Green), 1, 1), n(1))))),
        activated(cost(mana("{2}"), sacrificeA(sub("Frog"))), counters(Objects.Self)),
    )

    @Test
    fun beyondTheHorizon() = card(
        name = "Beyond the Horizon",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Search your library for up to two basic land cards and/or Town cards with different names, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(doing(Effect.Search(Players.You, or(basicLand, town), max = n(2), reveal = false, zones = listOf(Zone.Library), differentNames = true, bind = "found",
            searcher = Players.You)), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 611.2a (no duration: for as long as it's on the battlefield)
    @Test
    fun rideTheBehemoth() = card(
        name = "Ride the Behemoth",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control.
            {5}{G}{G}: This enchantment becomes a 7/7 Beast creature in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(landfall, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)), abilityWord = "Landfall"),
        activated(manaCost("{5}{G}{G}"), doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Creature),
            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Beast")))), Mechanic.SetPowerToughness(n(7), n(7))), Affected.Objects(Objects.Self), Duration.Indefinitely))),
    )

    // CR 701.10a
    @Test
    fun tiraLockheart() = card(
        name = "Tira Lockheart",
        cost = "{1}{G}",
        type = "Legendary Creature — Human Monk",
        pt = "1/2",
        text = """
            Trample
            Landfall — Whenever a land you control enters, double Tira Lockheart's power until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(landfall, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(power(Objects.Self), n(0))), abilityWord = "Landfall"),
    )

    @Test
    fun torvalAFineHound() = card(
        name = "Torval, A Fine Hound",
        cost = "{1}{G}",
        type = "Legendary Creature — Wolf",
        pt = "2/2",
        text = """
            Whenever you cast your first Human creature spell each turn, that creature enters with an additional +1/+1 counter on it for each Dog and/or Wolf you control.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(and(sub("Human"), creature), PlayerFilter.You)), nthThisTurn = 1),
            doing(Effect.SpellGets(thatSpell, listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, count(and(or(sub("Dog"), sub("Wolf")), you))))))))),
        addAnyColor,
    )

    @Test
    fun villageGreeter() = card(
        name = "Village Greeter",
        cost = "{1}{G}",
        type = "Creature — Human Citizen",
        pt = "1/1",
        text = "When this creature enters, mill four cards. You may put a land card from among them into your hand. If you put a Town card into your hand this way, you gain 2 life. (To mill four cards, a player puts the top four cards of their library into their graveyard.)",
    ) parsesTo abilities(
        triggered(entersSelf, mill(4, postBind = "milled"),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Graveyard)), n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            ifThen(Condition.ObjectMatches(Objects.Bound("chosen"), town), gainLife(2))),
    )

    // CR 401.5, 603.2d
    @Test
    fun wanderingChocobo() = card(
        name = "Wandering Strider",
        cost = "{2}{G}",
        type = "Creature — Bird",
        pt = "3/2",
        text = """
            You may look at the top card of your library any time.
            You may play lands and cast Bird spells from the top of your library.
            If a land or Bird you control entering the battlefield causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(land, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(sub("Bird"), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        static(Mechanic.TriggersAdditionalTime(EventPattern.ZoneChange(and(or(land, sub("Bird")), you), to = Zone.Battlefield), and(permanentFilter, you))),
    )
}
