package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 2: lands that make mana or find lands. Dual lands have only reminder text: their mana abilities come from their
 * basic land types (CR 305.6), so they parse to no abilities. Shock lands add "as this enters" (CR 614.12), written as
 * "unless" (CR 118.12a). Fetch lands are one activated ability that searches.
 */
class Chunk02ManaLandsTest {
    // ---- Shared shapes

    /** "{T}, Pay 1 life, Sacrifice this land: Search your library for a [A] or [B] card, put it onto the battlefield,
     *  then shuffle." */
    private fun fetchLand(finds: ObjectFilter) = abilities(
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.PayLife(Value.Fixed(1)),
                CostPart.Sacrifice(CostObjects.Self),
            )),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Search(Players.You, filter = finds, bind = "found")),
                    Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
                    Step.Do(Effect.Shuffle(Players.You)),
                ),
            ),
        ),
    )

    private fun subtypes(a: String, b: String) = ObjectFilter.Or(listOf(
        ObjectFilter.Sub(Subtype(a)),
        ObjectFilter.Sub(Subtype(b)),
    ))

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." */
    private val shockLand = abilities(
        Ability.Static(listOf(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(listOf(
                        Step.Unless(
                            payer = Players.You,
                            cost = Cost(listOf(CostPart.PayLife(Value.Fixed(2)))),
                            steps = listOf(Step.Modify(Modification.EntersTapped)),
                        ),
                    )),
                ),
            ),
        )),
    )

    /** "{T}: Add {C}." */
    private val tapForColorless = Ability.Activated(
        cost = Cost(listOf(CostPart.TapSelf)),
        instructions = Instructions(
            steps = listOf(
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)))),
            ),
        ),
    )

    // ---- Fetch lands

    @Test
    fun sunbakedMesa() = card(
        name = "Sunbaked Mesa",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Mountain or Plains card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Mountain", "Plains"),
    )

    @Test
    fun cinderSoakedBog() = card(
        name = "Cinder-Soaked Bog",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Mountain card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Swamp", "Mountain"),
    )

    @Test
    fun tidewashedShore() = card(
        name = "Tidewashed Shore",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Plains or Island card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Plains", "Island"),
    )

    @Test
    fun gloomlitFen() = card(
        name = "Gloomlit Fen",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Plains or Swamp card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Plains", "Swamp"),
    )

    @Test
    fun mistveilCanopy() = card(
        name = "Mistveil Canopy",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Forest or Island card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Forest", "Island"),
    )

    @Test
    fun brackishEstuary() = card(
        name = "Brackish Estuary",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Swamp card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Island", "Swamp"),
    )

    @Test
    fun steamingCaldera() = card(
        name = "Steaming Caldera",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Mountain card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Island", "Mountain"),
    )

    @Test
    fun mossyOssuary() = card(
        name = "Mossy Ossuary",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Forest card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Swamp", "Forest"),
    )

    @Test
    fun gustingMoorland() = card(
        name = "Gusting Moorland",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Forest or Plains card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Forest", "Plains"),
    )

    @Test
    fun timberedRidge() = card(
        name = "Timbered Ridge",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a Mountain or Forest card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = subtypes("Mountain", "Forest"),
    )

    @Test
    fun refractingOverlook() = card(
        name = "Refracting Overlook",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield, then shuffle.",
    ) parsesTo fetchLand(
        finds = ObjectFilter.And(listOf(
            ObjectFilter.Super(Supertype.Basic),
            ObjectFilter.Type(CardType.Land),
        )),
    )

    // ---- Dual lands: only reminder text; mana from their basic land types (CR 305.6)

    @Test
    fun ashenWastes() = card(
        name = "Ashen Wastes",
        cost = null,
        type = "Land — Swamp Mountain",
        text = "({T}: Add {B} or {R}.)",
    ) parsesTo none

    @Test
    fun blackwaterMire() = card(
        name = "Blackwater Mire",
        cost = null,
        type = "Land — Swamp Forest",
        text = "({T}: Add {B} or {G}.)",
    ) parsesTo none

    @Test
    fun highlandMesa() = card(
        name = "Highland Mesa",
        cost = null,
        type = "Land — Mountain Plains",
        text = "({T}: Add {R} or {W}.)",
    ) parsesTo none

    @Test
    fun goldenGrassland() = card(
        name = "Golden Grassland",
        cost = null,
        type = "Land — Forest Plains",
        text = "({T}: Add {G} or {W}.)",
    ) parsesTo none

    @Test
    fun thornbrushFlats() = card(
        name = "Thornbrush Flats",
        cost = null,
        type = "Land — Plains Swamp",
        text = "({T}: Add {W} or {B}.)",
    ) parsesTo none

    @Test
    fun frostpineSlopes() = card(
        name = "Frostpine Slopes",
        cost = null,
        type = "Land — Mountain Forest",
        text = "({T}: Add {R} or {G}.)",
    ) parsesTo none

    @Test
    fun palmfringeAtoll() = card(
        name = "Palmfringe Atoll",
        cost = null,
        type = "Land — Forest Island",
        text = "({T}: Add {G} or {U}.)",
    ) parsesTo none

    @Test
    fun glacialPlain() = card(
        name = "Glacial Plain",
        cost = null,
        type = "Land — Plains Island",
        text = "({T}: Add {W} or {U}.)",
    ) parsesTo none

    @Test
    fun sunkenGrotto() = card(
        name = "Sunken Grotto",
        cost = null,
        type = "Land — Island Swamp",
        text = "({T}: Add {U} or {B}.)",
    ) parsesTo none

    @Test
    fun smolderingIsle() = card(
        name = "Smoldering Isle",
        cost = null,
        type = "Land — Island Mountain",
        text = "({T}: Add {U} or {R}.)",
    ) parsesTo none

    // ---- Shock lands

    @Test
    fun goreVault() = card(
        name = "Gore Vault",
        cost = null,
        type = "Land — Swamp Mountain",
        text = """
            ({T}: Add {B} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun spawningBasin() = card(
        name = "Spawning Basin",
        cost = null,
        type = "Land — Forest Island",
        text = """
            ({T}: Add {G} or {U}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun hereticsChapel() = card(
        name = "Heretic's Chapel",
        cost = null,
        type = "Land — Plains Swamp",
        text = """
            ({T}: Add {W} or {B}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun consecratedSpring() = card(
        name = "Consecrated Spring",
        cost = null,
        type = "Land — Plains Island",
        text = """
            ({T}: Add {W} or {U}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun ivyChokedCrypt() = card(
        name = "Ivy-Choked Crypt",
        cost = null,
        type = "Land — Swamp Forest",
        text = """
            ({T}: Add {B} or {G}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun holyForge() = card(
        name = "Holy Forge",
        cost = null,
        type = "Land — Mountain Plains",
        text = """
            ({T}: Add {R} or {W}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun geyserFissure() = card(
        name = "Geyser Fissure",
        cost = null,
        type = "Land — Island Mountain",
        text = """
            ({T}: Add {U} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun trampledGlade() = card(
        name = "Trampled Glade",
        cost = null,
        type = "Land — Mountain Forest",
        text = """
            ({T}: Add {R} or {G}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun cloisterGrove() = card(
        name = "Cloister Grove",
        cost = null,
        type = "Land — Forest Plains",
        text = """
            ({T}: Add {G} or {W}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    @Test
    fun drownedBarrow() = card(
        name = "Drowned Barrow",
        cost = null,
        type = "Land — Island Swamp",
        text = """
            ({T}: Add {U} or {B}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo shockLand

    // ---- Other mana lands

    @Test
    fun hearthOfTheEmberwillows() = card(
        name = "Hearth of the Emberwillows",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {R} or {G}. Each opponent gains 1 life.
        """,
    ) parsesTo abilities(
        tapForColorless,
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.AddMana(ManaProduction(
                        ManaKind.Choose(
                            amount = Value.Fixed(1),
                            colors = listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)),
                        ),
                    ))),
                    Step.Do(Effect.GainLife(Players.Each(PlayerFilter.Opponent), amount = Value.Fixed(1))),
                ),
            ),
        ),
    )

    @Test
    fun oblationSpire() = card(
        name = "Oblation Spire",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {C}.
            {T}, Sacrifice a creature: Add {B}{B}.
        """,
    ) parsesTo abilities(
        tapForColorless,
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Creature))),
            )),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}{B}")!!)))),
                ),
            ),
        ),
    )

    @Test
    fun quarryGouge() = card(
        name = "Quarry Gouge",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}, Sacrifice this land: Destroy target land.
        """,
    ) parsesTo abilities(
        tapForColorless,
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.Sacrifice(CostObjects.Self),
            )),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "land", what = TargetKind.Objects(ObjectFilter.Type(CardType.Land))),
                ),
                steps = listOf(
                    Step.Do(Effect.Destroy(Objects.Target("land"))),
                ),
            ),
        ),
    )

    @Test
    fun barrenExpanse() = card(
        name = "Barren Expanse",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}, Sacrifice this land: Destroy target nonbasic land.
        """,
    ) parsesTo abilities(
        tapForColorless,
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.Sacrifice(CostObjects.Self),
            )),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "land",
                        what = TargetKind.Objects(ObjectFilter.And(listOf(
                            ObjectFilter.Not(ObjectFilter.Super(Supertype.Basic)),
                            ObjectFilter.Type(CardType.Land),
                        ))),
                    ),
                ),
                steps = listOf(
                    Step.Do(Effect.Destroy(Objects.Target("land"))),
                ),
            ),
        ),
    )
}
