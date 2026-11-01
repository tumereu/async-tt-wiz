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
 * basic land types (CR 305.6), so they parse to no abilities. Shock lands add "as this enters" (CR 614.12, written as
 * "unless", CR 118.12a). Fetch lands are one activated ability that searches.
 */
class Chunk02ManaLandsTest {
    // ---- Fetch lands: "{T}, Pay 1 life, Sacrifice this land: Search your library for a [A] or [B] card, put it onto the
    // battlefield, then shuffle."
    private val fetchCost = Cost(listOf(CostPart.TapSelf, CostPart.PayLife(Value.Fixed(1)), CostPart.Sacrifice(CostObjects.Self)))
    private fun fetch(filter: ObjectFilter) = abilities(Ability.Activated(fetchCost, Instructions(steps = listOf(
        Step.Do(Effect.Search(Players.You, filter, bind = "found")),
        Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
        Step.Do(Effect.Shuffle(Players.You))))))
    private fun either(a: String, b: String) = ObjectFilter.Or(listOf(ObjectFilter.Sub(Subtype(a)), ObjectFilter.Sub(Subtype(b))))
    private fun fetchText(a: String, b: String) =
        "{T}, Pay 1 life, Sacrifice this land: Search your library for a $a or $b card, put it onto the battlefield, then shuffle."

    @Test fun sunbakedMesa() = card("Sunbaked Mesa", null, "Land", fetchText("Mountain", "Plains")) parsesTo fetch(either("Mountain", "Plains"))
    @Test fun cinderSoakedBog() = card("Cinder-Soaked Bog", null, "Land", fetchText("Swamp", "Mountain")) parsesTo fetch(either("Swamp", "Mountain"))
    @Test fun tidewashedShore() = card("Tidewashed Shore", null, "Land", fetchText("Plains", "Island")) parsesTo fetch(either("Plains", "Island"))
    @Test fun gloomlitFen() = card("Gloomlit Fen", null, "Land", fetchText("Plains", "Swamp")) parsesTo fetch(either("Plains", "Swamp"))
    @Test fun mistveilCanopy() = card("Mistveil Canopy", null, "Land", fetchText("Forest", "Island")) parsesTo fetch(either("Forest", "Island"))
    @Test fun brackishEstuary() = card("Brackish Estuary", null, "Land",
        "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Swamp card, put it onto the battlefield, then shuffle.") parsesTo
        fetch(either("Island", "Swamp"))
    @Test fun steamingCaldera() = card("Steaming Caldera", null, "Land",
        "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Mountain card, put it onto the battlefield, then shuffle.") parsesTo
        fetch(either("Island", "Mountain"))
    @Test fun mossyOssuary() = card("Mossy Ossuary", null, "Land", fetchText("Swamp", "Forest")) parsesTo fetch(either("Swamp", "Forest"))
    @Test fun gustingMoorland() = card("Gusting Moorland", null, "Land", fetchText("Forest", "Plains")) parsesTo fetch(either("Forest", "Plains"))
    @Test fun timberedRidge() = card("Timbered Ridge", null, "Land", fetchText("Mountain", "Forest")) parsesTo fetch(either("Mountain", "Forest"))
    @Test fun refractingOverlook() = card("Refracting Overlook", null, "Land",
        "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield, then shuffle.") parsesTo
        fetch(ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))))

    // ---- Dual lands: only reminder text; mana from their basic land types (CR 305.6).
    @Test fun ashenWastes() = card("Ashen Wastes", null, "Land — Swamp Mountain", "({T}: Add {B} or {R}.)") parsesTo none
    @Test fun blackwaterMire() = card("Blackwater Mire", null, "Land — Swamp Forest", "({T}: Add {B} or {G}.)") parsesTo none
    @Test fun highlandMesa() = card("Highland Mesa", null, "Land — Mountain Plains", "({T}: Add {R} or {W}.)") parsesTo none
    @Test fun goldenGrassland() = card("Golden Grassland", null, "Land — Forest Plains", "({T}: Add {G} or {W}.)") parsesTo none
    @Test fun thornbrushFlats() = card("Thornbrush Flats", null, "Land — Plains Swamp", "({T}: Add {W} or {B}.)") parsesTo none
    @Test fun frostpineSlopes() = card("Frostpine Slopes", null, "Land — Mountain Forest", "({T}: Add {R} or {G}.)") parsesTo none
    @Test fun palmfringeAtoll() = card("Palmfringe Atoll", null, "Land — Forest Island", "({T}: Add {G} or {U}.)") parsesTo none
    @Test fun glacialPlain() = card("Glacial Plain", null, "Land — Plains Island", "({T}: Add {W} or {U}.)") parsesTo none
    @Test fun sunkenGrotto() = card("Sunken Grotto", null, "Land — Island Swamp", "({T}: Add {U} or {B}.)") parsesTo none
    @Test fun smolderingIsle() = card("Smoldering Isle", null, "Land — Island Mountain", "({T}: Add {U} or {R}.)") parsesTo none

    // ---- Shock lands: "As this land enters, you may pay 2 life. If you don't, it enters tapped." = "it enters tapped
    // unless you pay 2 life" (CR 118.12a), part of entering (CR 614.12).
    private val shock = abilities(Ability.Static(listOf(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(Step.Unless(Players.You, Cost(listOf(CostPart.PayLife(Value.Fixed(2)))),
            listOf(Step.Modify(Modification.EntersTapped))))))))))
    private fun shockText(a: String, b: String) = "({T}: Add {$a} or {$b}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped."

    @Test fun goreVault() = card("Gore Vault", null, "Land — Swamp Mountain", shockText("B", "R")) parsesTo shock
    @Test fun spawningBasin() = card("Spawning Basin", null, "Land — Forest Island", shockText("G", "U")) parsesTo shock
    @Test fun hereticsChapel() = card("Heretic's Chapel", null, "Land — Plains Swamp", shockText("W", "B")) parsesTo shock
    @Test fun consecratedSpring() = card("Consecrated Spring", null, "Land — Plains Island", shockText("W", "U")) parsesTo shock
    @Test fun ivyChokedCrypt() = card("Ivy-Choked Crypt", null, "Land — Swamp Forest", shockText("B", "G")) parsesTo shock
    @Test fun holyForge() = card("Holy Forge", null, "Land — Mountain Plains", shockText("R", "W")) parsesTo shock
    @Test fun geyserFissure() = card("Geyser Fissure", null, "Land — Island Mountain", shockText("U", "R")) parsesTo shock
    @Test fun trampledGlade() = card("Trampled Glade", null, "Land — Mountain Forest", shockText("R", "G")) parsesTo shock
    @Test fun cloisterGrove() = card("Cloister Grove", null, "Land — Forest Plains", shockText("G", "W")) parsesTo shock
    @Test fun drownedBarrow() = card("Drowned Barrow", null, "Land — Island Swamp", shockText("U", "B")) parsesTo shock

    // ---- Other mana lands
    private val tapForColorless = Ability.Activated(Cost(listOf(CostPart.TapSelf)),
        Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)))))))

    @Test fun hearthOfTheEmberwillows() = card("Hearth of the Emberwillows", null, "Land",
        "{T}: Add {C}.\n{T}: Add {R} or {G}. Each opponent gains 1 life.") parsesTo abilities(
        tapForColorless,
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(steps = listOf(
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1), listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))))),
            Step.Do(Effect.GainLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1)))))))

    @Test fun oblationSpire() = card("Oblation Spire", null, "Legendary Land", "{T}: Add {C}.\n{T}, Sacrifice a creature: Add {B}{B}.") parsesTo abilities(
        tapForColorless,
        Ability.Activated(Cost(listOf(CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Creature))))),
            Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}{B}")!!))))))))

    @Test fun quarryGouge() = card("Quarry Gouge", null, "Land", "{T}: Add {C}.\n{T}, Sacrifice this land: Destroy target land.") parsesTo abilities(
        tapForColorless,
        Ability.Activated(Cost(listOf(CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))), Instructions(
            targets = listOf(TargetSlot("land", TargetKind.Objects(ObjectFilter.Type(CardType.Land)))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("land")))))))

    @Test fun barrenExpanse() = card("Barren Expanse", null, "Land", "{T}: Add {C}.\n{T}, Sacrifice this land: Destroy target nonbasic land.") parsesTo abilities(
        tapForColorless,
        Ability.Activated(Cost(listOf(CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))), Instructions(
            targets = listOf(TargetSlot("land", TargetKind.Objects(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Super(Supertype.Basic)), ObjectFilter.Type(CardType.Land)))))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("land")))))))
}
