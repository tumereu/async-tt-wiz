package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 4: simple mana: rituals (an instant's instructions, CR 113.3a), Moxen and mana rocks, sacrifice outlets that
 * make mana, and a few mana artifacts with one more line (a condition, a draw when it enters or is put into a graveyard).
 */
class Chunk04SimpleManaTest {
    // ---- Shared shapes

    /** "Add [mana]." as one step. */
    private fun add(kind: ManaKind) = Step.Do(Effect.AddMana(ManaProduction(kind), players = Players.You))
    private fun fixed(mana: String) = ManaKind.Fixed(ManaCost.parse(mana)!!)

    /** "[cost]: Add [mana]." */
    private fun manaAbility(vararg cost: CostPart, kind: ManaKind) = Ability.Activated(
        cost = Cost(cost.toList()),
        instructions = Instructions(
            steps = listOf(add(kind)),
        ),
        rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
    )

    private fun spell(kind: ManaKind) = abilities(
        Ability.Spell(Instructions(
            steps = listOf(add(kind)),
        )),
    )

    private val sacrificeSelf = CostPart.Sacrifice(CostObjects.Self)
    private fun sacrificeA(type: CardType) = CostPart.Sacrifice(CostObjects.Choose(
        ObjectFilter.Type(type),
        count = Value.Fixed(1),
    ))
    private fun yourOwn(type: CardType) = ObjectFilter.And(listOf(
        ObjectFilter.Type(type),
        ObjectFilter.ControlledBy(PlayerFilter.You),
    ))
    private val drawACard = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))

    // ---- Rituals

    @Test
    fun grimOffering() = card(
        name = "Grim Offering",
        cost = "{B}",
        type = "Instant",
        text = "Add {B}{B}{B}.",
    ) parsesTo spell(fixed("{B}{B}{B}"))

    @Test
    fun roilingChant() = card(
        name = "Roiling Chant",
        cost = "{2}{R}",
        type = "Instant",
        text = "Add {R}{R}{R}{R}{R}.",
    ) parsesTo spell(fixed("{R}{R}{R}{R}{R}"))

    // ---- Moxen and rocks

    @Test
    fun verdantBauble() = card(
        name = "Verdant Bauble",
        cost = "{0}",
        type = "Artifact",
        text = "{T}: Add {G}.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, kind = fixed("{G}")),
    )

    @Test
    fun onyxBauble() = card(
        name = "Onyx Bauble",
        cost = "{0}",
        type = "Artifact",
        text = "{T}: Add {B}.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, kind = fixed("{B}")),
    )

    @Test
    fun nacreBauble() = card(
        name = "Nacre Bauble",
        cost = "{0}",
        type = "Artifact",
        text = "{T}: Add {W}.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, kind = fixed("{W}")),
    )

    @Test
    fun garnetBauble() = card(
        name = "Garnet Bauble",
        cost = "{0}",
        type = "Artifact",
        text = "{T}: Add {R}.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, kind = fixed("{R}")),
    )

    @Test
    fun azureBauble() = card(
        name = "Azure Bauble",
        cost = "{0}",
        type = "Artifact",
        text = "{T}: Add {U}.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, kind = fixed("{U}")),
    )

    @Test
    fun sunBand() = card(
        name = "Sun Band",
        cost = "{1}",
        type = "Artifact",
        text = "{T}: Add {C}{C}.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, kind = fixed("{C}{C}")),
    )

    @Test
    fun lotusFrond() = card(
        name = "Lotus Frond",
        cost = "{0}",
        type = "Artifact",
        text = "{T}, Sacrifice this artifact: Add one mana of any color.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, sacrificeSelf, kind = anyOneColor(1)),
    )

    @Test
    fun midnightLotus() = card(
        name = "Midnight Lotus",
        cost = "{0}",
        type = "Artifact",
        text = "{T}, Sacrifice this artifact: Add three mana of any one color.",
    ) parsesTo abilities(
        manaAbility(CostPart.TapSelf, sacrificeSelf, kind = anyOneColor(3)),
    )

    @Test
    fun opalineTrinket() = card(
        name = "Opaline Trinket",
        cost = "{0}",
        type = "Legendary Artifact",
        text = "Metalcraft — {T}: Add one mana of any color. Activate only if you control three or more artifacts.",
    ) parsesTo abilities(
        Ability.Activated(
            abilityWord = "Metalcraft",
            cost = Cost(listOf(CostPart.TapSelf)),
            rules = ActivationRules(
                condition = Condition.Exists(yourOwn(CardType.Artifact), atLeast = 3),
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
            ),
            instructions = Instructions(
                steps = listOf(add(anyOneColor(1))),
            ),
        ),
    )

    // ---- Sacrifice outlets and tapping creatures for mana

    @Test
    fun renderersAltar() = card(
        name = "Renderer's Altar",
        cost = "{3}",
        type = "Artifact",
        text = "Sacrifice a creature: Add {C}{C}.",
    ) parsesTo abilities(
        manaAbility(sacrificeA(CardType.Creature), kind = fixed("{C}{C}")),
    )

    @Test
    fun scrapperClanForge() = card(
        name = "Scrapper-Clan Forge",
        cost = "{4}",
        type = "Artifact",
        text = "Sacrifice an artifact: Add {C}{C}.",
    ) parsesTo abilities(
        manaAbility(sacrificeA(CardType.Artifact), kind = fixed("{C}{C}")),
    )

    @Test
    fun compleatAltar() = card(
        name = "Compleat Altar",
        cost = "{3}",
        type = "Artifact",
        text = "Sacrifice a creature: Add one mana of any color.",
    ) parsesTo abilities(
        manaAbility(sacrificeA(CardType.Creature), kind = anyOneColor(1)),
    )

    @Test
    fun leafbeatDrum() = card(
        name = "Leafbeat Drum",
        cost = "{1}",
        type = "Artifact",
        text = "{T}, Tap an untapped creature you control: Add one mana of any color.",
    ) parsesTo abilities(
        manaAbility(
            CostPart.TapSelf,
            // Tapping as a cost taps an untapped permanent you control (CR 602.2b): the filter names only the kind.
            CostPart.TapObjects(CostObjects.Choose(ObjectFilter.Type(CardType.Creature), count = Value.Fixed(1))),
            kind = anyOneColor(1),
        ),
    )

    // ---- Mana artifacts that also draw

    @Test
    fun prismaticMote() = card(
        name = "Prismatic Mote",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, {T}, Sacrifice this artifact: Add one mana of any color.
            When this artifact is put into a graveyard from the battlefield, draw a card.
        """,
    ) parsesTo abilities(
        manaAbility(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf, sacrificeSelf, kind = anyOneColor(1)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.Is(Objects.Self),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(
                steps = listOf(drawACard),
            ),
        ),
    )

    @Test
    fun rimewatchersOrrery() = card(
        name = "Rimewatcher's Orrery",
        cost = "{S}",
        type = "Snow Artifact",
        text = """
            ({S} can be paid with one mana from a snow source.)
            When this artifact enters, draw a card.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.Is(Objects.Self),
                to = Zone.Battlefield,
            )),
            instructions = Instructions(
                steps = listOf(drawACard),
            ),
        ),
        manaAbility(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf, kind = anyOneColor(1)),
    )
}
