package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Total
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** lci chunk 09: life-loss doubling, "this way" conditions, lands that become creatures, linked exiles and restricted mana. */
class LciChunk09ConditionsAndManaTest {
    private val cave = sub("Cave")
    private val chosenColor = listOf(ColorRef.Remembered("color"))
    private val finality = Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(CounterKind.Named("finality"), n(1))))
    private fun becomesCreature(objects: Objects, subtype: String) = Step.Do(Effect.ApplyMechanics(listOf(
        Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype(subtype)))),
        Mechanic.SetPowerToughness(n(0), n(0)), Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(objects), Duration.Indefinitely))

    // CR 614.1a: a replacement that applies only during your turn; damage is life loss (CR 120.3a)
    @Test
    fun bloodtitheRevenant() = card(
        name = "Bloodtithe Revenant",
        cost = "{1}{B}{B}{B}",
        type = "Creature — Vampire Demon",
        pt = "2/4",
        text = """
            Flying
            If an opponent would lose life during your turn, they lose twice that much life instead. (Damage causes loss of life.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.LoseLife(PlayerFilter.Opponent), modify = listOf(Modification.MultiplyAmount(2))),
            condition = Condition.YourTurn),
    )

    // CR 608.2c: "this way" is the land the instruction before put onto the battlefield
    @Test
    fun cavernDelving() = card(
        name = "Cavern Delving",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, draw a card, then you may put a land card from your hand onto the battlefield. If you put a Cave onto the battlefield this way, you gain 4 life.
            Lands you control enter untapped.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "returned"),
            ifThen(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("returned"), cave)), Comparison.AtLeast, n(1)), gainLife(4))),
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield), modify = listOf(Modification.EntersUntapped))),
    )

    // CR 613.4c, 613.1f: the pump in layer 7c, the keyword and the quoted ability in layer 6, until end of turn
    @Test
    fun jawsnapFury() = card(
        name = "Jawsnap Fury",
        cost = "{R}",
        type = "Instant",
        text = """
            Until end of turn, target attacking creature gets +2/+2 and gains trample and "Whenever this creature deals combat damage to a player, destroy target artifact that player controls."
        """,
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 2, 2, has(Keyword.Trample, triggered(combatDamageToAPlayer,
                destroy(target("artifact")),
                targets = listOf(slot("artifact", and(artifact, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer)))))))),
            targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 205.1b: "in addition to its other types" keeps all its types; CR 613.1d, 613.4b
    @Test
    fun fossilBellow() = card(
        name = "Fossil Bellow",
        cost = "{U}",
        type = "Instant",
        text = """
            Until end of turn, target artifact or creature becomes a Dinosaur artifact creature with base power and toughness 4/3 in addition to its other types.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("permanent"),
                Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dinosaur")))),
                Mechanic.SetPowerToughness(n(4), n(3))),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 614.1a, 611.2c: a replacement for the rest of the turn, for any creature an opponent controls when it would die
    @Test
    fun spitefulUmbra() = card(
        name = "Spiteful Umbra",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            All creatures get -2/-2 until end of turn. If a creature an opponent controls would die this turn, exile it instead.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Each(creature), pt(-2, -2)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = Duration.EndOfTurn))),
    )

    // CR 107.3a: X announced; CR 601.2h: the exiled artifacts total exactly X; CR 106.6: restricted mana
    @Test
    fun assemblySmeltworks() = card(
        name = "Assembly Smeltworks",
        cost = "{1}{W}",
        type = "Artifact",
        text = """
            {T}: Add {W}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
            {2}{W}, {T}, Exile one or more other artifacts you control with total mana value X: Return target artifact card with mana value X or less from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{W}")!!), restriction = or(
            and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated, source = artifact))), Players.You))),
        Ability.Activated(
            cost = cost(mana("{2}{W}"), tap, CostPart.Exile(CostObjects.Choose(and(another, artifact, you), count = null,
                total = Total(NumericProperty.ManaValue, Value.Announced(name = "X"), exactly = true), atLeast = 1), from = Zone.Battlefield)),
            instructions = Instructions(
                targets = listOf(slot("card", inYourGraveyard(artifact, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X"))))),
                steps = listOf(move(target("card"), Zone.Battlefield)),
                announced = listOf("X")),
            rules = asSorcery),
    )

    // CR 607.2a: the cards its enters ability exiled; CR 106.7: one of their colours
    @Test
    fun hollowOfTribute() = card(
        name = "Hollow of Tribute",
        cost = null,
        type = "Land — Cave",
        text = """
            This land enters tapped.
            When this land enters, exile up to three target cards from graveyards.
            {T}: Add {C}.
            {T}: Add one mana of any of the exiled cards' colors.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf,
            move(target("cards"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("cards", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 3))),
        addOne("{C}"),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Of(Objects.Remembered("exiled"))))), Players.You))),
    )

    // CR 106.6: mana only for activated abilities of lands
    @Test
    fun drownedBastion() = card(
        name = "Drowned Bastion",
        cost = null,
        type = "Land — Cave",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
            {T}: Add two mana of the chosen color. Spend this mana only to activate abilities of land sources.
        """,
    ) parsesTo abilities(
        entersTapped,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
            affects = Affected.Objects(Objects.Self)),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), chosenColor)), Players.You))),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), chosenColor),
            restriction = ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated, source = land)), Players.You))),
    )

    // CR 611.2a: for good; CR 205.1b: "It's still a land" keeps its types; CR 613.1d, 613.4b
    @Test
    fun sporelingOfTheDeep() = card(
        name = "Sporeling of the Deep",
        cost = "{1}{G}",
        type = "Creature — Fungus Wizard",
        pt = "2/2",
        text = """
            {5}{G}{G}: Put seven +1/+1 counters on target noncreature land you control. It becomes a 0/0 Fungus creature with haste. It's still a land.
        """,
    ) parsesTo abilities(
        activated(manaCost("{5}{G}{G}"), counters(target("land"), 7), becomesCreature(target("land"), "Fungus"),
            targets = listOf(slot("land", and(not(creature), land, you)))),
    )

    // CR 603.4: "if you cast it"; CR 701.21a: each player their own; CR 400.7: the sacrificed cards are new objects in the graveyard
    @Test
    fun heraldOfTheFinalOffering() = card(
        name = "Herald of the Final Offering",
        cost = "{6}{B}{B}",
        type = "Creature — Vampire Demon",
        pt = "6/6",
        text = """
            Flying
            When this creature enters, if you cast it, each player sacrifices all other creatures they control. Then each player returns all creature cards from their graveyard that weren't put there this way to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Do(Effect.Sacrifice(Objects.Each(and(another, creature)), postBind = "sacrificed")),
            Step.Do(Effect.Move(Objects.Each(and(creature, inZone(Zone.Graveyard), not(ObjectFilter.Is(Objects.Bound("sacrificed"))))),
                Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Each(and(creature, inZone(Zone.Graveyard),
                    not(ObjectFilter.Is(Objects.Bound("sacrificed"))))))))),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    // CR 106.6, 106.6a: only for a creature spell of the chosen type, which can't be countered
    @Test
    fun grottoOfKinship() = card(
        name = "Grotto of Kinship",
        cost = null,
        type = "Land",
        text = """
            As this land enters, choose a creature type.
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type, and that spell can't be countered.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.You, remember = true)))))),
            affects = Affected.Objects(Objects.Self)),
        addOne("{C}"),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)),
            restriction = and(spellFilter, creature, ObjectFilter.Sub(SubtypeRef.Remembered("type"))), spellGets = listOf(Mechanic.CantBeCountered)), Players.You))),
    )

    // CR 601.2f: {1} less for each Cave you control plus each Cave card in your graveyard
    @Test
    fun caveboundLeech() = card(
        name = "Cavebound Leech",
        cost = "{7}{B}",
        type = "Creature — Leech",
        pt = "5/5",
        text = """
            This spell costs {1} less to cast for each Cave you control and each Cave card in your graveyard.
            Lifelink
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!,
            Value.Sum(listOf(count(and(cave, you)), count(inYourGraveyard(cave))))))), affects = Affected.Objects(Objects.Self)),
        Keyword.Lifelink,
    )

    // CR 607.2a: cards exiled with it; CR 122.1h: a finality counter; CR 400.7d: the spell and the permanent it becomes
    @Test
    fun boldFossilwright() = card(
        name = "Bold Fossilwright",
        cost = "{1}{G}",
        type = "Creature — Human Druid",
        pt = "2/2",
        text = """
            {T}: Add one mana of any color.
            {2}: Exile target card from a graveyard.
            You may cast Dinosaur creature spells from among cards you own exiled with this creature. If you cast a spell this way, that creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(manaCost("{2}"), move(target("card"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You, spellGets = listOf(finality))),
            affects = Affected.Objects(Objects.Each(and(sub("Dinosaur"), creature, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Is(Objects.Remembered("exiled")))))),
    )

    // CR 608.2g: the discarded card cast while the ability resolves; CR 601.2: without paying its mana cost
    @Test
    fun marisolSirenCorsair() = card(
        name = "Marisol, Siren Corsair",
        cost = "{1}{U}",
        type = "Legendary Creature — Siren Pirate",
        pt = "2/1",
        text = """
            Flash
            Flying
            Whenever Marisol deals combat damage to a player, put a chorus counter on it. Draw a card, then discard a card. If there are four or more chorus counters on Marisol, you may cast the discarded card without paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(combatDamageToAPlayer, counters(Objects.Self, 1, CounterKind.Named("chorus")), draw(1),
            Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, postBind = "discarded")),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("chorus"), atLeast = 4)),
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("discarded"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You))),
    )

    // CR 700.2d: the same mode more than once; CR 701.23a; CR 611.2a, 205.1b
    @Test
    fun starstoneConvergence() = card(
        name = "Starstone Convergence",
        cost = "{4}{G}",
        type = "Sorcery",
        text = """
            Choose three. You may choose the same mode more than once.
            • Search your library for a Cave card, put it onto the battlefield tapped, then shuffle.
            • Put three +1/+1 counters on a Cave you control. It becomes a 0/0 Elemental creature with haste. It's still a land.
            • Destroy target enchantment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(search(cave), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
            mode(Step.Choose(Choice.Objects(and(cave, you), n(1), n(1)), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen"), 3), becomesCreature(Objects.Bound("chosen"), "Elemental")),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
        ), min = 3, max = 3, repeatable = true, chooser = Players.You))),
    )

    // CR 700.2: one mode, or both if you descended this turn (CR 700.11)
    @Test
    fun searingCaveIn() = card(
        name = "Searing Cave-In",
        cost = "{B}{R}",
        type = "Sorcery",
        text = """
            Choose one. If you descended this turn, you may choose both instead. (You descended if a permanent card was put into your graveyard from anywhere.)
            • Destroy target creature or planeswalker.
            • Destroy target noncreature, nonland permanent with mana value 1 or less.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", and(not(creature), not(land), permanentFilter, manaValue(Comparison.AtMost, 1))))),
        ), min = 1, max = 1, maxIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentCard, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
            to = Zone.Graveyard), atLeast = 1, measure = arcanum.api.Measure.Objects), maxIfCount = 2, upToMaxIf = true, chooser = Players.You))),
    )
}
