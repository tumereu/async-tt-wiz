package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 10: green cards. Damage equal to a creature's power or a planeswalker's loyalty (CR 120.3); losing all
 * abilities (layer 6); charge counters spent as mana at the beginning of the first main phase; searching for up to that much
 * damage of lands; a choice among three keywords; an Equipment granting a triggered ability (CR 613.1f); casting spells from
 * the hand without paying their mana costs by mana value (CR 118.9); an Aura making a permanent a 5/5 Construct (layers 4 and
 * 7b); milling and keeping a permanent card; Tarmogoyf's characteristic-defining ability (CR 604.3); land creature tokens.
 */
class FraChunk10GreenTest {
    @Test
    fun feralIncursion() = card(
        name = "Feral Incursion",
        cost = "{3}{G}",
        type = "Sorcery",
        text = """
            Create a 4/4 green Beast creature token with trample.
            Flashback {5}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(Step.Do(Effect.CreateToken(creatureToken("Beast", listOf(Color.Green), 4, 4, abilities = listOf(Keyword.Trample)), n(1), Players.You))), Keyword.Flashback(cost(mana("{5}{G}"))))

    // CR 608.2h: what it was as it was destroyed
    @Test
    fun blossomingRebel() = card(
        name = "Blossoming Rebel",
        cost = "{2}{G}",
        type = "Creature — Dryad Scout",
        pt = "3/3",
        text = """
            Vigilance
            Sacrifice this creature: Destroy target artifact or enchantment. If that permanent was a legendary enchantment, draw a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(Keyword.Vigilance, activated(cost(sacrificeSelf),
            Step.Do(Effect.Destroy(target("permanent"))),
            Step.If(Condition.ObjectMatches(target("permanent"), and(ObjectFilter.Super(Supertype.Legendary), ObjectFilter.Type(CardType.Enchantment))), then = listOf(draw(1))),
            targets = listOf(slot("permanent", or(artifact, ObjectFilter.Type(CardType.Enchantment)))), rules = asSorcery))

    // CR 120.3
    @Test
    fun compulsorySavagery() = card(
        name = "Compulsory Savagery",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature you control deals damage equal to its power to target creature or planeswalker an opponent controls.
            • Target planeswalker you control deals damage equal to its loyalty to target creature or planeswalker an opponent controls.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creatureYouControl), slot("permanent", and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Opponent)))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = target("permanent")), Value.Of(NumericProperty.Power, target("creature"), Combine.Single), target("creature")))))),
            Mode(Instructions(targets = listOf(slot("planeswalker", and(planeswalker, you)), slot("permanent", and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Opponent)))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = target("permanent")), Value.Of(NumericProperty.Loyalty, target("planeswalker"), Combine.Single),
                    target("planeswalker")))))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 613.1f
    @Test
    fun thrivingClinch() = card(
        name = "Thriving Clinch",
        cost = "{G}",
        type = "Instant",
        text = "Target creature or planeswalker an opponent controls that's red or white loses all abilities until end of turn. Target creature you control deals damage equal to its power to that permanent.",
    ) parsesTo abilities(spell(
            untilEndOfTurn(target("permanent"), Mechanic.LoseAbilities()),
            Step.Do(Effect.DealDamage(Recipients(objects = target("permanent")), Value.Of(NumericProperty.Power, target("creature"), Combine.Single), target("creature"))),
            targets = listOf(slot("permanent", and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Opponent), or(ObjectFilter.OfColor(Color.Red), ObjectFilter.OfColor(Color.White)))),
                slot("creature", creatureYouControl)),
        ))

    // CR 505.1a, 106.1
    @Test
    fun gardenwildBloom() = card(
        name = "Gardenwild Bloom",
        cost = "{1}{G}{G}",
        type = "Enchantment",
        text = """
            Whenever a creature you control dies, put a charge counter on this enchantment.
            At the beginning of your first main phase, add {G} for each charge counter on this enchantment.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self, kind = CounterKind.Named("charge"))),
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!,
                times = Value.Of(NumericProperty.Counters(CounterKind.Named("charge")), Objects.Self, Combine.Single))), Players.You))),
        )

    @Test
    fun hothouseCultivator() = card(
        name = "Hothouse Cultivator",
        cost = "{2}{G}",
        type = "Creature — Cat Druid",
        pt = "2/3",
        text = """
            Whenever another creature you control enters, you gain 1 life.
            {T}: Add {G}.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(anotherObject, creature, you), to = Zone.Battlefield)), gainLife(1)),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
        )

    // CR 120.3
    @Test
    fun hexwardRevitalizer() = card(
        name = "Hexward Revitalizer",
        cost = "{G}{G}{G}{G}",
        type = "Creature — Chimera Horror",
        pt = "6/6",
        text = """
            Vigilance
            Whenever this creature is dealt damage, you may search your library for up to that many land cards, put them onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(Keyword.Vigilance, triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))), Step.May(listOf(
            Step.Do(Effect.Search(Players.You, land, Value.EventAmount, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            Step.Do(Effect.Shuffle(Players.You))), player = Players.You)))

    @Test
    fun ravenousMarionette() = card(
        name = "Ravenous Marionette",
        cost = "{3}{G}{G}",
        type = "Artifact Creature — Beast Construct",
        pt = "5/5",
        text = """
            When this creature enters, create a Heartwood token. (It's a red and green artifact with "{T}: Add {R} or {G}.")
            {1}, Sacrifice another artifact: Put a +1/+1 counter on this creature. It gains your choice of trample, hexproof, or haste until end of turn.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, createHeartwood),
            activated(cost(mana("{1}"), CostPart.Sacrifice(CostObjects.Choose(and(anotherObject, artifact), n(1)))),
                counters(Objects.Self),
                Step.Choose(Choice.Option(listOf("trample", "hexproof", "haste")), bind = "keyword", chooser = Players.You),
                Step.If(Condition.Chose("keyword", "trample"), then = listOf(gains(Objects.Self, Keyword.Trample)), otherwise = listOf(
                    Step.If(Condition.Chose("keyword", "hexproof"), then = listOf(gains(Objects.Self, Keyword.Hexproof)), otherwise = listOf(gains(Objects.Self, Keyword.Haste)))))),
        )

    // CR 613.1f
    @Test
    fun stalkerSHatchet() = card(
        name = "Stalker's Hatchet",
        cost = "{G}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0 and has "Whenever this creature attacks, it gains your choice of trample or deathtouch until end of turn."
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
            static(Mechanic.ModifyPowerToughness(n(2), n(0)), Mechanic.GainAbilities(listOf(triggered(attacksSelf,
                Step.Choose(Choice.Option(listOf("trample", "deathtouch")), bind = "keyword", chooser = Players.You),
                Step.If(Condition.Chose("keyword", "trample"), then = listOf(gains(Objects.Self, Keyword.Trample)), otherwise = listOf(gains(Objects.Self, Keyword.Deathtouch)))))),
                affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(cost(mana("{2}"))),
        )

    // CR 118.9, 601.2a
    @Test
    fun everywhereAtOnce() = card(
        name = "Everywhere at Once",
        cost = "{5}{G}{G}{G}",
        type = "Enchantment",
        text = "You may cast spells with mana value less than or equal to the number of creatures you control from your hand without paying their mana costs.",
    ) parsesTo abilities(static(Mechanic.MayPlay(Zone.Hand, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(spellFilter, ObjectFilter.ManaValue(Comparison.AtMost, Value.Count(Objects.Each(creatureYouControl))), ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.You)))))

    // CR 613.1d, 613.4b
    @Test
    fun marionetteMaking() = card(
        name = "Marionette Making",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or non-Aura enchantment
            Enchanted permanent is a Construct creature with base power and toughness 5/5 in addition to its other types.
            {4}{G}: Return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
            Keyword.Enchant(TargetKind.Objects(or(artifact, and(not(sub("Aura")), ObjectFilter.Type(CardType.Enchantment))))),
            static(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Construct")))),
                Mechanic.SetPowerToughness(n(5), n(5)), affects = Objects.AttachedTo(Objects.Self)),
            activated(cost(mana("{4}{G}")), move(Objects.Self, Zone.Hand)),
        )

    @Test
    fun mendWithCompassion() = card(
        name = "Mend with Compassion",
        cost = "{2}{G}",
        type = "Instant",
        text = "Return target permanent card from your graveyard to your hand. You gain 4 life.",
    ) parsesTo abilities(spell(move(target("card"), Zone.Hand), gainLife(4), targets = listOf(slot("card", and(or(artifact, ObjectFilter.Type(CardType.Battle), creature, ObjectFilter.Type(CardType.Enchantment), land, planeswalker), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))))

    @Test
    fun facsimileShaper() = card(
        name = "Facsimile Shaper",
        cost = "{1}{G}{G}",
        type = "Creature — Elf Druid",
        pt = "2/2",
        text = """
            When this creature enters, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
            When this creature dies, draw a card.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.May(listOf(
                Step.Do(Effect.Search(Players.You, and(ObjectFilter.Super(Supertype.Basic), land), n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
                Step.Do(Effect.Shuffle(Players.You))), player = Players.You)),
            triggered(diesSelf, draw(1)),
        )

    @Test
    fun worthPreserving() = card(
        name = "Worth Preserving",
        cost = "{1}{G}",
        type = "Instant",
        text = "Mill four cards. You may put a permanent card from among them into your hand. You gain 1 life. (To mill four cards, put the top four cards of your library into your graveyard.)",
    ) parsesTo abilities(spell(
            Step.Do(Effect.Mill(Players.You, n(4), postBind = "milled")),
            Step.Choose(Choice.Objects(or(artifact, ObjectFilter.Type(CardType.Battle), creature, ObjectFilter.Type(CardType.Enchantment), land, planeswalker), n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            gainLife(1),
        ))

    @Test
    fun trueshotSeeder() = card(
        name = "Trueshot Seeder",
        cost = "{1}{G}",
        type = "Creature — Human Archer",
        pt = "3/1",
        text = """
            Reach
            {3}{G}, Discard this card: Destroy target creature with flying.
        """,
    ) parsesTo abilities(Keyword.Reach, activated(cost(mana("{3}{G}"), CostPart.Discard(CostObjects.Self)), Step.Do(Effect.Destroy(target("creature"))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.HasKeyword(Keyword.Flying))))))

    // CR 604.3, 205.2a
    @Test
    fun marlgoyf() = card(
        name = "Marlgoyf",
        cost = "{1}{G}",
        type = "Creature — Lhurgoyf",
        pt = "*/1+*",
        text = "Marlgoyf's power is equal to the number of card types among cards in all graveyards and its toughness is equal to that number plus 1.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.SetPowerToughness(
            Value.CountDistinct(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), SharedQuality.CardType),
            Value.Sum(listOf(Value.CountDistinct(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), SharedQuality.CardType), n(1))))),
            affects = Affected.Objects(Objects.Self), characteristicDefining = true))

    @Test
    fun tethererSEdge() = card(
        name = "Tetherer's Edge",
        cost = "{G}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains reach until end of turn. Untap it.",
    ) parsesTo abilities(spell(pump(target("creature"), 2, 2, Mechanic.GainAbilities(listOf(Keyword.Reach))), Step.Do(Effect.Untap(target("creature"))), targets = listOf(slot("creature", creature))))

    // CR 111.4, 302.6
    @Test
    fun verdureKraken() = card(
        name = "Verdure Kraken",
        cost = "{4}{G}{G}{G}",
        type = "Creature — Plant Kraken",
        pt = "6/6",
        text = "At the beginning of each player's upkeep, you create a 3/3 green Forest Tentacle land creature token. (It has \"{T}: Add {G}.\" It's affected by summoning sickness until your next turn.)",
    ) parsesTo abilities(triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), Step.Do(Effect.CreateToken(
            creatureToken("Forest Tentacle", listOf(Color.Green), 3, 3, types = listOf(CardType.Land, CardType.Creature)), n(1), Players.You))))

    @Test
    fun vinewhipAdept() = card(
        name = "Vinewhip Adept",
        cost = "{4}{G}{G}",
        type = "Creature — Rhino Soldier",
        pt = "2/4",
        text = """
            Reach
            When this creature enters, put three +1/+1 counters on target creature.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(Keyword.Reach, triggered(entersSelf, counters(target("creature"), 3), targets = listOf(slot("creature", creature))), Keyword.Typecycling(and(ObjectFilter.Super(Supertype.Basic), land), "Basic landcycling", cost(mana("{2}"))))

    @Test
    fun demolitionGecko() = card(
        name = "Demolition Gecko",
        cost = "{4}{G}",
        type = "Artifact Creature — Lizard Construct",
        pt = "5/5",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            {6}{G}{G}: This creature gets +4/+4 and gains trample until end of turn.
        """,
    ) parsesTo abilities(Keyword.Ward(cost(mana("{2}"))), activated(cost(mana("{6}{G}{G}")), pump(Objects.Self, 4, 4, Mechanic.GainAbilities(listOf(Keyword.Trample)))))
}
