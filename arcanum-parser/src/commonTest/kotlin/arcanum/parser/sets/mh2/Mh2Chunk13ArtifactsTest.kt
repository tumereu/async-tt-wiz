package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 13: artifacts: token creation replaced by one of each (CR 614.1a), "attacks alone" with card types in all
 * graveyards, living weapon (CR 702.92), drawing then putting cards back, imprint and becoming a copy with an exception (CR 707.9b), a
 * mana ability with a discarded hand, a granted quoted trigger, becoming an artifact (CR 613.1d), modular's replacement, domain cost
 * reduction with colour-conditioned keywords, artifact landcycling and landwalk (CR 702.29e, 702.14), suspend without a mana cost,
 * entering tapped with counters, moving counters (CR 122.5), a sword's combat-damage trigger putting two cards onto the battlefield, and
 * countering spells cast without coloured mana.
 */
class Mh2Chunk13ArtifactsTest {
    // CR 614.1a: each such token creation creates one of each instead
    @Test
    fun academyFabricator() = card(
        name = "Academy Fabricator",
        cost = "{3}",
        type = "Artifact Creature — Assembly-Worker",
        text = "If you would create a Clue, Food, or Treasure token, instead create one of each.",
        pt = "1/3",
    ) parsesTo abilities(
        // CR 614.5: one replacement effect, applied once to an event
        static(
            Mechanic.Replacement(EventPattern.CreateToken(and(ObjectFilter.Or(listOf(sub("Clue"), sub("Food"), sub("Treasure"))), you)),
                modify = listOf(Modification.CreateInstead(clue), Modification.AlsoCreate(food, Value.EventAmount), Modification.AlsoCreate(treasure, Value.EventAmount))),
        ),
    )

    // CR 506.5: a creature attacking alone; X counted as the trigger resolves
    @Test
    fun shrineOfTheGoyf() = card(
        name = "Shrine of the Goyf",
        cost = "{5}",
        type = "Kindred Artifact — Lhurgoyf",
        text = """
            Whenever a creature you control attacks alone, it gets +X/+X until end of turn, where X is the number of card types among cards in all graveyards.
            Lhurgoyf creatures you control have trample.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(creatureYouControl, alone = true)), untilEndOfTurn(thatCreature, Mechanic.ModifyPowerToughness(
            Value.CountDistinct(Objects.Each(inZone(Zone.Graveyard)), SharedQuality.CardType), Value.CountDistinct(Objects.Each(inZone(Zone.Graveyard)), SharedQuality.CardType)))),
        static(has(Keyword.Trample), affects = Objects.Each(and(sub("Lhurgoyf"), creature, you))),
    )

    // CR 702.92a living weapon
    @Test
    fun battlemarrow() = card(
        name = "Battlemarrow",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +1/+1 and has vigilance and lifelink.
            Equip {5}
        """,
    ) parsesTo abilities(Keyword.LivingWeapon, static(pt(1, 1), has(Keyword.Vigilance, Keyword.Lifelink), affects = equipped), equip("{5}"))

    // CR 603.10a: its power as it last existed
    @Test
    fun jarredGolems() = card(
        name = "Jarred Golems",
        cost = "{4}",
        type = "Artifact Creature — Golem",
        text = """
            Trample
            When this creature dies, you gain life equal to its power.
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Trample, triggered(diesSelf, doing(Effect.GainLife(Players.You, powerOf(Objects.Self)))))

    @Test
    fun thoughtrock() = card(
        name = "Thoughtrock",
        cost = "{1}",
        type = "Artifact",
        text = "{2}, {T}, Sacrifice this artifact: Draw three cards, then put two cards from your hand on top of your library in any order.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, sacrificeSelf), draw(3),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(2), n(2)), bind = "put", chooser = Players.You),
            move(Objects.Bound("put"), Zone.Library, position = 0)),
    )

    // CR 607.2a: the exiled card is linked to this Vehicle; CR 707.9b: a copy with an exception
    @Test
    fun dermocab() = card(
        name = "Dermocab",
        cost = "{2}",
        type = "Artifact — Vehicle",
        text = """
            Imprint — As this Vehicle enters, exile a creature card from a graveyard.
            Tap two untapped creatures you control: Until end of turn, this Vehicle becomes a copy of the exiled card, except it's a Vehicle artifact in addition to its other types.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Choose(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Any)), count = n(1)), from = Zone.Graveyard,
                postBind = "exiled", remember = "exiled"))), payer = Players.You, mandatory = true, bind = "paid"),
        ))))).copy(abilityWord = "Imprint"),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, count = n(2)))), untilEndOfTurn(Objects.Self, Mechanic.CopyOf(Objects.Remembered("exiled"),
            except = listOf(CopyException.Types(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vehicle")))))))),
    )

    // CR 605.1a: a mana ability, only as an instant (CR 602.5e)
    @Test
    fun adamantLion() = card(
        name = "Adamant Lion",
        cost = "{2}",
        type = "Artifact Creature — Cat",
        text = "{T}, Discard your hand, Sacrifice this creature: Add three mana of any one color. Activate only as an instant.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.Discard(CostObjects.These(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))), sacrificeSelf),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(3))), Players.You)), rules = anyTime.copy(timing = Timing.Instant)),
    )

    @Test
    fun chaffTosser() = card(
        name = "Chaff Tosser",
        cost = "{3}",
        type = "Artifact",
        text = "{T}, Discard a card: This artifact deals 2 damage to target player or planeswalker.",
    ) parsesTo abilities(
        activated(cost(tap, discardACard), damage(2, Recipients(target = "player or planeswalker")),
            targets = listOf(TargetSlot("player or planeswalker", TargetKind.Either(planeswalker, PlayerFilter.Any), min = n(1), max = n(1)))),
    )

    // CR 702.92a living weapon; a quoted triggered ability granted to the equipped creature
    @Test
    fun valdraCompleat() = card(
        name = "Valdra Compleat",
        cost = "{7}",
        type = "Legendary Artifact — Equipment",
        text = """
            Living weapon
            Indestructible
            Equipped creature gets +5/+5 and has first strike, trample, indestructible, haste, and "Whenever this creature deals combat damage to a creature, exile that creature."
            Equip {7}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        Keyword.Indestructible,
        static(pt(5, 5), has(Keyword.FirstStrike, Keyword.Trample, Keyword.Indestructible, Keyword.Haste,
            triggered(on(EventPattern.Damage(isSelf, RecipientFilter(objects = creature), combat = true)), move(thatCreature, Zone.Exile))), affects = equipped),
        equip("{7}"),
    )

    // CR 613.1d: an artifact in addition to its other types (layer 4)
    @Test
    fun liquimetalWrench() = card(
        name = "Liquimetal Wrench",
        cost = "{2}",
        type = "Artifact",
        text = """
            {T}: Add {C}.
            {T}: Target nonland permanent becomes an artifact in addition to its other types until end of turn.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    @Test
    fun singleskelion() = card(
        name = "Singleskelion",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        text = """
            This creature enters with a +1/+1 counter on it.
            {1}, Remove a +1/+1 counter from this creature: It deals 1 damage to any target.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
        activated(cost(mana("{1}"), CostPart.RemoveCounters(p1p1, n(1), from = CostObjects.Self)), damage(1, Recipients(target = "any")), targets = listOf(anyTarget)),
    )

    @Test
    fun myrScrapper() = card(
        name = "Myr Scrapper",
        cost = "{1}",
        type = "Artifact Creature — Myr",
        text = "Sacrifice this creature: Put a +1/+1 counter on target creature.",
        pt = "1/1",
    ) parsesTo abilities(activated(cost(sacrificeSelf), counters(target("creature")), targets = listOf(slot("creature", creature))))

    @Test
    fun briarcyst() = card(
        name = "Briarcyst",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)
            Equipped creature gets +1/+1 for each artifact and/or enchantment you control.
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.LivingWeapon,
        static(Mechanic.ModifyPowerToughness(count(and(or(artifact, enchantment), you)), count(and(or(artifact, enchantment), you))), affects = equipped),
        equip("{2}"),
    )

    @Test
    fun ornithopterOfBliss() = card(
        name = "Ornithopter of Bliss",
        cost = "{2}",
        type = "Artifact Creature — Thopter",
        text = """
            Flying
            {T}: Add one mana of any color.
        """,
        pt = "0/2",
    ) parsesTo abilities(Keyword.Flying, addAnyColor)

    // CR 603.4: three or more tokens as it triggers and resolves
    @Test
    fun havenRaptor() = card(
        name = "Haven Raptor",
        cost = "{3}",
        type = "Artifact Creature — Bird",
        text = """
            Flying
            Whenever this creature attacks, if you control three or more tokens, this creature gets +2/+0 and gains first strike until end of turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, untilEndOfTurn(Objects.Self, pt(2, 0), has(Keyword.FirstStrike)), interveningIf = Condition.Exists(and(token, you), atLeast = 3)),
    )

    // CR 601.2f: {2} less for each basic land type; one keyword for each of its colours
    @Test
    fun heirOfDraco() = card(
        name = "Heir of Draco",
        cost = "{12}",
        type = "Artifact Creature — Dragon",
        text = """
            Domain — This spell costs {2} less to cast for each basic land type among lands you control.
            Flying
            Each creature you control has vigilance if it's white, hexproof if it's blue, lifelink if it's black, first strike if it's red, and trample if it's green.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{2}")!!,
            Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.BasicLandType)))).copy(abilityWord = "Domain"),
        Keyword.Flying,
        static(has(Keyword.Vigilance), affects = Objects.Each(and(creature, you, ObjectFilter.OfColor(Color.White)))),
        static(has(Keyword.Hexproof), affects = Objects.Each(and(creature, you, ObjectFilter.OfColor(Color.Blue)))),
        static(has(Keyword.Lifelink), affects = Objects.Each(and(creature, you, ObjectFilter.OfColor(Color.Black)))),
        static(has(Keyword.FirstStrike), affects = Objects.Each(and(creature, you, ObjectFilter.OfColor(Color.Red)))),
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, you, ObjectFilter.OfColor(Color.Green)))),
    )

    // CR 702.29e: artifact landcycling looks for an artifact land card
    @Test
    fun wayfarersCompanion() = card(
        name = "Wayfarer's Companion",
        cost = "{7}",
        type = "Artifact Creature — Salamander",
        text = """
            Affinity for artifacts
            Artifact landcycling {2} ({2}, Discard this card: Search your library for an artifact land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), Keyword.Typecycling(and(artifact, land), "Artifact landcycling", manaCost("{2}")))

    @Test
    fun sunTalisman() = card(
        name = "Sun Talisman",
        cost = null,
        type = "Artifact",
        text = """
            Suspend 3—{1} (Rather than cast this card from your hand, pay {1} and exile it with three time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)
            {T}: Add {C}{C}.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(3, manaCost("{1}")),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You))),
    )

    // CR 614.1c: tapped and with counters as it enters; CR 122.5: moving a counter
    @Test
    fun ironDromedary() = card(
        name = "Iron Dromedary",
        cost = "{3}",
        type = "Artifact Creature — Camel",
        text = """
            This creature enters tapped with two +1/+1 counters on it.
            This creature doesn't untap during your untap step if it has a +1/+1 counter on it.
            At the beginning of combat on your turn, you may move a +1/+1 counter from this creature onto target creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersTapped, Modification.EntersWithCounters(p1p1, n(2))))),
        static(Mechanic.DoesntUntap, condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 1))),
        triggered(beginningOfCombatOnYourTurn, Step.May(listOf(doing(Effect.MoveCounters(Objects.Self, target("creature"), p1p1, n(1)))), Players.You),
            targets = listOf(slot("creature", creature))),
    )

    // CR 702.16g; both cards put onto the battlefield at once
    @Test
    fun bladeOfHearthAndField() = card(
        name = "Blade of Hearth and Field",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+2 and has protection from green and from white.
            Whenever equipped creature deals combat damage to a player, exile up to one target creature you own, then search your library for a basic land card. Put both cards onto the battlefield under your control, then shuffle.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 2), has(Keyword.Protection(ObjectFilter.OfColor(Color.Green)), Keyword.Protection(ObjectFilter.OfColor(Color.White))), affects = equipped),
        triggered(on(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            search(basicLand),
            move(Objects.Union(listOf(Objects.Bound("exiled"), Objects.Bound("found"))), Zone.Battlefield, controller = Players.You),
            shuffle,
            targets = listOf(slot("creature", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0))),
        equip("{2}"),
    )

    @Test
    fun tormodsGravekeeper() = card(
        name = "Tormod's Gravekeeper",
        cost = "{3}",
        type = "Artifact Creature — Golem",
        text = """
            Vigilance
            {T}, Sacrifice this creature: Exile target player's graveyard.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(tap, sacrificeSelf), move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile),
            targets = listOf(targetPlayer)),
    )

    @Test
    fun theNetherworldCookbook() = card(
        name = "The Netherworld Cookbook",
        cost = "{1}",
        type = "Artifact — Book",
        text = """
            {T}, Discard a card: Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {4}, {T}, Sacrifice this artifact: Return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        activated(cost(tap, discardACard), create(food)),
        activated(cost(mana("{4}"), tap, sacrificeSelf), move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 702.14c: artifact landwalk
    @Test
    fun vectisGauntlets() = card(
        name = "Vectis Gauntlets",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0 and has artifact landwalk. (It can't be blocked as long as defending player controls an artifact land.)
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), has(Keyword.Landwalk(and(artifact, land), "artifact landwalk")), affects = equipped),
        equip("{2}"),
    )

    // CR 603.4: no coloured mana spent to cast it, checked as it triggers and resolves
    @Test
    fun nullMirror() = card(
        name = "Null Mirror",
        cost = "{2}",
        type = "Artifact",
        text = "Whenever a player casts a spell, if no colored mana was spent to cast it, counter that spell.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), doing(Effect.Counter(thatCreature)),
            interveningIf = Condition.Compare(Value.ManaSpent(distinctColors = true, of = thatCreature), Comparison.Equal, n(0))),
    )

    // CR 614.1a: one more counter from a modular ability
    @Test
    fun zebazTheGlimmerwing() = card(
        name = "Zebaz, the Glimmerwing",
        cost = "{1}",
        type = "Legendary Artifact Creature — Insect",
        text = """
            Modular 1
            If a modular triggered ability would put one or more +1/+1 counters on a creature you control, that many plus one +1/+1 counters are put on it instead.
            {R}: Destroy target artifact you control.
            {W}: Zebaz gains flying until end of turn.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Modular(1),
        static(Mechanic.Replacement(EventPattern.CountersPut(creatureYouControl, p1p1, causedBy = ObjectFilter.AbilityOf(kind = AbilityKind.Triggered, keyword = Keyword.Modular.Kind)),
            modify = listOf(Modification.AddAmount(n(1))))),
        activated(cost(mana("{R}")), destroy(target("artifact")), targets = listOf(slot("artifact", and(artifact, you)))),
        activated(cost(mana("{W}")), gains(Objects.Self, Keyword.Flying)),
    )
}
