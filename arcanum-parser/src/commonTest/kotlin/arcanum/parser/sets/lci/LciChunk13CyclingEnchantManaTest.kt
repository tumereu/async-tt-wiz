package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Destination
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 13: typecycling, Auras with "enchant", Caves and restricted mana abilities. */
class LciChunk13CyclingEnchantManaTest {
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val cave = sub("Cave")
    private val addColorless = addOne("{C}")
    private val anyColor = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))
    private fun typecycling(land: String, mana: String) = Keyword.Typecycling(sub(land), "${land}cycling", manaCost(mana))

    // CR 702.29e: typecycling is cycling that searches for a card with the subtype; CR 702.21a: ward
    @Test
    fun tidalHulkasaur() = card(
        name = "Tidal Hulkasaur",
        cost = "{5}{U}{U}",
        type = "Creature — Dinosaur",
        pt = "6/7",
        text = """
            Ward {3} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {3}.)
            Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(Keyword.Ward(manaCost("{3}")), typecycling("Island", "{2}"))

    // CR 702.29e, 111.1
    @Test
    fun thicketBristlehide() = card(
        name = "Thicket Bristlehide",
        cost = "{5}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "5/5",
        text = """
            When this creature enters, create a 3/3 green Dinosaur creature token.
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Dinosaur", listOf(Color.Green), 3, 3))),
        typecycling("Forest", "{2}"),
    )

    // CR 611.2a: both effects last until end of turn
    @Test
    fun gravefangSpiketail() = card(
        name = "Gravefang Spiketail",
        cost = "{4}{B}{B}",
        type = "Creature — Dinosaur",
        pt = "5/6",
        text = """
            When this creature enters, target creature you control gets +2/+0 and gains indestructible until end of turn.
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(target("creature"), 2, 0, has(Keyword.Indestructible)), targets = listOf(slot("creature", creatureYouControl))),
        typecycling("Swamp", "{2}"),
    )

    // CR 702.29a: cycling; CR 120.3: damage from the artifact
    @Test
    fun rollingTumblestone() = card(
        name = "Rolling Tumblestone",
        cost = "{6}",
        type = "Artifact",
        text = """
            Flash
            When this artifact enters, it deals 6 damage to target creature an opponent controls.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, damage(6, toObjects(target("creature"))), targets = listOf(slot("creature", and(creature, opponents)))),
        Keyword.Cycling(manaCost("{2}")),
    )

    // CR 701.21a: sacrifice a land as a cost
    @Test
    fun quakeBehemoth() = card(
        name = "Quake Behemoth",
        cost = "{4}{R}{R}",
        type = "Creature — Dinosaur",
        pt = "6/5",
        text = """
            Trample
            {2}{R}, Sacrifice a land: Draw a card.
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(mana("{2}{R}"), sacrificeA(land)), draw(1)),
        typecycling("Mountain", "{2}"),
    )

    // CR 303.4, 613.4c
    @Test
    fun witheringBurden() = card(
        name = "Withering Burden",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -2/-2.
        """,
    ) parsesTo abilities(enchantCreature, static(Mechanic.ModifyPowerToughness(n(-2), n(-2)), affects = enchanted))

    // CR 613.1d, 613.1e, 613.1f, 613.4b
    @Test
    fun piranhaTide() = card(
        name = "Piranha Tide",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Enchant creature
            Enchanted creature loses all abilities and is a black Skeleton creature with base power and toughness 1/1. (It loses all other colors, card types, and creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(
            Mechanic.LoseAbilities(),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black))),
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Skeleton"))), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)),
            affects = enchanted,
        ),
    )

    // CR 700.4: dies; CR 303.4g: the Aura's own leaves; 110.2: returns under its owner's control
    @Test
    fun graveResilience() = card(
        name = "Grave Resilience",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets +2/+0.
            When enchanted creature dies, return it to the battlefield tapped under its owner's control.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(Mechanic.ModifyPowerToughness(n(2), n(0)), affects = enchanted),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, tapped = true,
                controller = Players.OwnerOf(Objects.EventObject(EventRole.After)))))),
    )

    // CR 508.1c, 509.1b, 602.5
    @Test
    fun stonebind() = card(
        name = "Stonebind",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            Enchanted permanent can't attack or block, and its activated abilities can't be activated.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(or(artifact, creature))),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true), affects = enchanted),
    )

    // CR 701.17a: mill; CR 110.4a: permanent cards; fathomless descent is an ability word
    @Test
    fun lullabyOfSilence() = card(
        name = "Lullaby of Silence",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature or Vehicle
            When this Aura enters, you may mill two cards. (You may put the top two cards of your library into your graveyard.)
            Fathomless descent — Enchanted permanent gets -X/-0, where X is the number of permanent cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(or(creature, sub("Vehicle")))),
        triggered(entersSelf, Step.May(listOf(Step.Do(Effect.Mill(Players.You, n(2)))), player = Players.You)),
        static(Mechanic.ModifyPowerToughness(arcanum.api.Value.Times(count(inYourGraveyard(permanentCard)), -1), n(0)), affects = enchanted)
            .copy(abilityWord = "Fathomless descent"),
    )

    // CR 605.1a: mana abilities; CR 602.5: the Cave's two abilities both need {T}; CR 106.1b
    @Test
    fun hiddenHollowCave() = card(
        name = "Hidden Hollow Cave",
        cost = null,
        type = "Land — Cave",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
            {4}, {T}, Sacrifice this land: Put two +1/+1 counters on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{4}"), tap, sacrificeSelf), counters(target("creature"), 2), targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 613.1f, 205.3m: other Caves gain a mana ability with a life payment
    @Test
    fun shrineOfHollows() = card(
        name = "Shrine of Hollows",
        cost = null,
        type = "Land — Cave",
        text = """
            {T}: Add {C}.
            Other Caves you control have "{T}, Pay 1 life: Add one mana of any color."
        """,
    ) parsesTo abilities(
        addColorless,
        static(Mechanic.GainAbilities(listOf(activated(cost(tap, CostPart.PayLife(n(1))), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))))),
            affects = Objects.Each(and(another, cave, you))),
    )

    // CR 106.6: spend this mana only to cast a Dinosaur spell or activate an ability of a Dinosaur source
    @Test
    fun dinosaurHerbalist() = card(
        name = "Dinosaur Herbalist",
        cost = "{G}",
        type = "Creature — Human Druid",
        pt = "1/1",
        text = "{T}: Add one mana of any color. Spend this mana only to cast a Dinosaur spell or activate an ability of a Dinosaur source.",
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)),
            restriction = or(and(spellFilter, sub("Dinosaur")), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = sub("Dinosaur")))), Players.You))),
    )

    // CR 106.6: artifact spell or an ability of an artifact source
    @Test
    fun clockworkSiren() = card(
        name = "Clockwork Siren",
        cost = "{1}{U}",
        type = "Artifact Creature — Siren Pirate",
        pt = "1/2",
        text = """
            Flying, vigilance
            {T}: Add {U}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!),
            restriction = or(and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact))), Players.You))),
    )

    // CR 611.2a
    @Test
    fun bogFrog() = card(
        name = "Bog Frog",
        cost = "{1}{G}",
        type = "Creature — Frog",
        pt = "1/1",
        text = """
            Reach
            {T}: Add one mana of any color.
            {2}: This creature gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        anyColor,
        activated(manaCost("{2}"), gains(Objects.Self, Keyword.Deathtouch)),
    )

    // CR 701.23a: search; CR 305.7, 701.21a
    @Test
    fun prospectorsCave() = card(
        name = "Prospector's Cave",
        cost = null,
        type = "Land — Cave",
        text = """
            {T}: Add {C}.
            {1}, {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{1}"), tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )
}
