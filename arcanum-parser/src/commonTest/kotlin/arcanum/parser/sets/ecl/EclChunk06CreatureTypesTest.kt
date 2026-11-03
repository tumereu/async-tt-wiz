package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 06: creature types (CR 205.3m). A type chosen as a permanent enters and kept as its chosen type (CR 607.2d, from
 * any creature type or from a list: "choose Elemental, Elf, …"), or chosen as a spell or ability resolves ("that type", "the chosen
 * type"); creatures that share a creature type (CR 205.3, Winnowing, each player's sacrifice at once, CR 608.2e), two targets that share
 * a creature type, gaining all creature types, a colorless Noggle (layers 4, 5, 6, 7b), doubled damage (CR 614.1a), the next instant or
 * sorcery copied (CR 603.7c, 707.10), and mana spent only on spells or abilities of the chosen type (CR 106.6).
 */
class EclChunk06CreatureTypesTest {
    private val chosenTypeAsEnters = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.You, remember = true)))))), affects = selfOnly)
    private val lorwynTypes = listOf("Elemental", "Elf", "Faerie", "Giant", "Goblin", "Kithkin", "Merfolk", "Treefolk").map { Subtype(it) }
    private val chosenTypeAmongAsEnters = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureTypeAmong(lorwynTypes), bind = "type", chooser = Players.You, remember = true)))))),
        affects = selfOnly)

    // CR 608.2e: all choices first, then every player's sacrifice at once
    @Test
    fun cullingSift() = card(
        name = "Culling Sift",
        cost = "{4}{W}{W}",
        type = "Sorcery",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            For each player, you choose a creature that player controls. Then each player sacrifices all other creatures they control that don't share a creature type with the chosen creature they control.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        run {
            val theirs = and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))
            spell(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(theirs, n(1), n(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Sacrifice(Objects.Each(and(theirs, not(ObjectFilter.Is(Objects.Bound("chosen"))),
                    not(ObjectFilter.Shares(SharedQuality.CreatureType, Objects.Bound("chosen"))))))))))
        },
    )

    // layers 7b and 4, until end of turn
    @Test
    fun glamourGiver() = card(
        name = "Glamour Giver",
        cost = "{1}{U}",
        type = "Creature — Faerie Wizard",
        text = """
            Flash
            Flying
            When this creature enters, choose up to one other target creature. Until end of turn, that creature has base power and toughness 4/4 and gains all creature types.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(4), n(4)), Mechanic.AllCreatureTypes),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    @Test
    fun chorusedSwell() = card(
        name = "Chorused Swell",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Choose a creature type. Draw a card for each permanent you control of that type.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(chooseCreatureType, doing(Effect.Draw(Players.You, count(and(permanentFilter, you, ofThatType))))),
    )

    // CR 613.1: no abilities (layer 6), colorless (layer 5), only the Noggle creature type (layer 4), base 1/1 (layer 7b)
    @Test
    fun noggleTheWits() = card(
        name = "Noggle the Wits",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature loses all abilities and is a colorless Noggle with base power and toughness 1/1. (It loses all colors and all other creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.LoseAbilities(), Mechanic.SetColors(emptyList()), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Noggle"))),
            replaceSubtypes = true), Mechanic.SetPowerToughness(n(1), n(1)), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 603.7c: the next instant or sorcery spell this turn is copied, once
    @Test
    fun frostflameCollar() = card(
        name = "Frostflame Collar",
        cost = "{1}{U}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a creature type.
            Whenever a permanent you control of the chosen type enters, put a charge counter on this artifact.
            {T}, Remove three charge counters from this artifact: When you next cast an instant or sorcery spell this turn, copy it. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        chosenTypeAsEnters,
        triggered(enters(and(permanentFilter, you, ofChosenType)), counters(Objects.Self, 1, CounterKind.Named("charge"))),
        activated(cost(tap, arcanum.api.CostPart.RemoveCounters(CounterKind.Named("charge"), n(3), arcanum.api.CostObjects.Self)),
            doing(Effect.CreateDelayedTrigger(youCast(and(or(instant, sorcery), spellFilter)), arcanum.api.Instructions(steps = listOf(
                doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))), duration = Duration.EndOfTurn))),
    )

    // the type chosen as it resolves
    @Test
    fun kinlineSummons() = card(
        name = "Kinline Summons",
        cost = "{6}{B}{B}",
        type = "Sorcery",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Choose a creature type. Return all creature cards of the chosen type from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(chooseCreatureType, move(Objects.Each(inYourGraveyard(creature, ofThatType)), Zone.Battlefield)),
    )

    // CR 601.2c: two targets that share a creature type
    @Test
    fun disinter() = card(
        name = "Disinter",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Return target creature card from your graveyard to your hand.
            • Return two target creature cards that share a creature type from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(arcanum.api.Instructions(modes = Modes(listOf(
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
            mode(move(target("creature cards"), Zone.Hand), targets = listOf(TargetSlot("creature cards", TargetKind.Objects(inYourGraveyard(creature)),
                min = n(2), max = n(2), sharing = SharedQuality.CreatureType)))),
            min = 1, max = 1, chooser = Players.You))),
    )

    // CR 614.1a
    @Test
    fun gatheredBlaze() = card(
        name = "Gathered Blaze",
        cost = "{3}{R}{R}",
        type = "Enchantment",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            As this enchantment enters, choose a creature type.
            Double all damage that sources you control of the chosen type would deal.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        chosenTypeAsEnters,
        static(Mechanic.Replacement(EventPattern.Damage(and(you, ofChosenType), RecipientFilter()), modify = listOf(Modification.MultiplyAmount(2)))),
    )

    @Test
    fun devotedKeeper() = card(
        name = "Devoted Keeper",
        cost = "{3}{G}{G}",
        type = "Creature — Elf Warrior",
        text = """
            Flash
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            When this creature enters, choose a creature type. Other permanents you control of that type gain hexproof and indestructible until end of turn.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Convoke,
        triggered(entersSelf, chooseCreatureType, gains(Objects.Each(and(another, permanentFilter, you, ofThatType)), Keyword.Hexproof, Keyword.Indestructible)),
    )

    // a spell of the chosen type: a creature or Kindred spell with that subtype (CR 205.3d)
    @Test
    fun annalsOfTriumph() = card(
        name = "Annals of Triumph",
        cost = "{6}",
        type = "Legendary Artifact",
        text = """
            As Annals of Triumph enters, choose a creature type.
            Creatures you control of the chosen type get +2/+2 and have first strike and trample.
            Whenever you cast a spell of the chosen type, draw a card.
        """,
    ) parsesTo abilities(
        chosenTypeAsEnters,
        static(pt(2, 2), has(Keyword.FirstStrike, Keyword.Trample), affects = Objects.Each(and(creature, you, ofChosenType))),
        triggered(youCast(ofChosenType), draw(1)),
    )

    // CR 601.2f; one ability with two events
    @Test
    fun musteringStone() = card(
        name = "Mustering Stone",
        cost = "{4}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a creature type.
            Spells you cast of the chosen type cost {1} less to cast.
            When this artifact enters and at the beginning of your upkeep, look at the top card of your library. If it's a card of the chosen type, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.
        """,
    ) parsesTo abilities(
        chosenTypeAsEnters,
        static(Mechanic.CostChange(and(spellFilter, you, ofChosenType), arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1)))),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "seen")),
            Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound("seen"), ofChosenType), then = listOf(
                Step.Pay(steps = listOf(doing(Effect.Reveal(Objects.Bound("seen"))), move(Objects.Bound("seen"), Zone.Hand)), payer = Players.You, bind = "put"))),
            Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("put")), then = listOf(
                Step.May(listOf(move(Objects.Bound("seen"), Zone.Graveyard)), player = Players.You)))),
    )

    // CR 205.3m: one of the listed creature types
    @Test
    fun mornTouchedBanner() = card(
        name = "Morn-Touched Banner",
        cost = "{1}",
        type = "Artifact",
        text = """
            As this artifact enters, choose Elemental, Elf, Faerie, Giant, Goblin, Kithkin, Merfolk, or Treefolk.
            Whenever a permanent you control of the chosen type enters, you gain 1 life.
            {2}, {T}, Sacrifice this artifact: Return target card of the chosen type from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        chosenTypeAmongAsEnters,
        triggered(enters(and(permanentFilter, you, ofChosenType)), gainLife(1)),
        activated(cost(mana("{2}"), tap, sacrificeSelf), move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(cardFilter, ofChosenType)))),
    )

    // CR 106.6: spent only on spells and abilities of the chosen type
    @Test
    fun shadowedDomains() = card(
        name = "Shadowed Domains",
        cost = null,
        type = "Land",
        text = """
            As this land enters, choose Elemental, Elf, Faerie, Giant, Goblin, Kithkin, Merfolk, or Treefolk.
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a spell of the chosen type or activate an ability of a source of the chosen type.
        """,
    ) parsesTo abilities(
        chosenTypeAmongAsEnters,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = or(and(spellFilter, ofChosenType),
            ObjectFilter.AbilityOf(source = ofChosenType))), Players.You))),
    )
}
