package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 6: creatures that make mana: tap for one colour, for any colour, for one of three, for each creature or Elf or
 * Forest; tapping other Elves; a -1/-1 counter as a cost; a lord ("Other Elf creatures you control get +1/+1").
 */
class Chunk06ManaCreaturesTest {
    // ---- Shared shapes

    private fun add(kind: ManaKind) = Step.Do(Effect.AddMana(ManaProduction(kind)))
    private fun fixed(mana: String) = ManaKind.Fixed(ManaCost.parse(mana)!!)
    private fun oneOf(vararg colors: Color) = ManaKind.Choose(amount = Value.Fixed(1), colors = colors.map { ColorRef.Literal(it) })

    /** "{T}: Add [mana]." */
    private fun tapFor(kind: ManaKind) = Ability.Activated(
        cost = Cost(listOf(CostPart.TapSelf)),
        instructions = Instructions(
            steps = listOf(add(kind)),
        ),
    )

    /** "{T}: Add {[mana]} for each [what]." */
    private fun tapForEach(mana: String, what: ObjectFilter) = tapFor(
        ManaKind.Fixed(ManaCost.parse(mana)!!, times = Value.Count(Objects.Each(what))),
    )

    private fun yourOwn(vararg filters: ObjectFilter) = ObjectFilter.And(filters.toList() + ObjectFilter.ControlledBy(PlayerFilter.You))
    private val elf = ObjectFilter.Sub(Subtype("Elf"))

    /** "Tap [count] untapped Elves you control": tapping as a cost taps untapped permanents you control (CR 602.2b). */
    private fun tapElves(count: Int) = CostPart.TapObjects(CostObjects.Choose(
        filter = elf,
        count = Value.Fixed(count),
    ))

    // ---- Tap for one mana

    @Test
    fun saintsWayfarer() = card(
        name = "Saint's Wayfarer",
        cost = "{G}",
        type = "Creature — Human Monk",
        text = "{T}: Add {W}.",
        pt = "1/1",
    ) parsesTo abilities(
        tapFor(fixed("{W}")),
    )

    @Test
    fun rimefrostDruid() = card(
        name = "Rimefrost Druid",
        cost = "{G}",
        type = "Snow Creature — Elf Druid",
        text = "{T}: Add {C}.",
        pt = "1/1",
    ) parsesTo abilities(
        tapFor(fixed("{C}")),
    )

    @Test
    fun elvishSeer() = card(
        name = "Elvish Seer",
        cost = "{G}",
        type = "Creature — Elf Druid",
        text = "{T}: Add {G}.",
        pt = "1/1",
    ) parsesTo abilities(
        tapFor(fixed("{G}")),
    )

    @Test
    fun frosthornElves() = card(
        name = "Frosthorn Elves",
        cost = "{G}",
        type = "Creature — Elf Druid",
        text = "{T}: Add {G}.",
        pt = "1/1",
    ) parsesTo abilities(
        tapFor(fixed("{G}")),
    )

    @Test
    fun thicketwoodElves() = card(
        name = "Thicketwood Elves",
        cost = "{G}",
        type = "Creature — Elf Druid",
        text = "{T}: Add {G}.",
        pt = "1/1",
    ) parsesTo abilities(
        tapFor(fixed("{G}")),
    )

    @Test
    fun umbralElves() = card(
        name = "Umbral Elves",
        cost = "{G}",
        type = "Creature — Elf Druid",
        text = "{T}: Add {B}. This creature deals 1 damage to you.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                steps = listOf(
                    add(fixed("{B}")),
                    Step.Do(Effect.DealDamage(Recipients(players = Players.You), amount = Value.Fixed(1))),
                ),
            ),
        ),
    )

    @Test
    fun canopyVisionary() = card(
        name = "Canopy Visionary",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        text = """
            When this creature enters, draw a card.
            {T}: Add {G}.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))),
            ),
        ),
        tapFor(fixed("{G}")),
    )

    @Test
    fun zealousDruid() = card(
        name = "Zealous Druid",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        text = """
            {T}: Add {G}.
            Put a -1/-1 counter on this creature: Untap this creature.
        """,
        pt = "0/2",
    ) parsesTo abilities(
        tapFor(fixed("{G}")),
        Ability.Activated(
            cost = Cost(listOf(CostPart.PutCounters(CounterKind.PowerToughness(-1, -1), amount = Value.Fixed(1)))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Untap(Objects.Self))),
            ),
        ),
    )

    // ---- Any colour, or one of three

    @Test
    fun plumesOfEden() = card(
        name = "Plumes of Eden",
        cost = "{G}",
        type = "Creature — Bird",
        text = """
            Flying
            {T}: Add one mana of any color.
        """,
        pt = "0/1",
    ) parsesTo abilities(
        Keyword.Flying,
        tapFor(anyOneColor(1)),
    )

    @Test
    fun gearwingOfEden() = card(
        name = "Gearwing of Eden",
        cost = "{2}",
        type = "Artifact Creature — Thopter",
        text = """
            Flying
            {T}: Add one mana of any color.
        """,
        pt = "0/2",
    ) parsesTo abilities(
        Keyword.Flying,
        tapFor(anyOneColor(1)),
    )

    @Test
    fun baseExarch() = card(
        name = "Base Exarch",
        cost = "{G}",
        type = "Creature — Goblin Shaman",
        text = """
            Exalted (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)
            {T}: Add {B}, {R}, or {G}.
        """,
        pt = "0/1",
    ) parsesTo abilities(
        Keyword.Exalted,
        tapFor(oneOf(Color.Black, Color.Red, Color.Green)),
    )

    @Test
    fun highbornExarch() = card(
        name = "Highborn Exarch",
        cost = "{G}",
        type = "Creature — Human Druid",
        text = """
            Exalted (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)
            {T}: Add {G}, {W}, or {U}.
        """,
        pt = "0/1",
    ) parsesTo abilities(
        Keyword.Exalted,
        tapFor(oneOf(Color.Green, Color.White, Color.Blue)),
    )

    @Test
    fun gemhideLion() = card(
        name = "Gemhide Lion",
        cost = "{2}",
        type = "Artifact Creature — Cat",
        text = "{T}, Discard your hand, Sacrifice this creature: Add three mana of any one color. Activate only as an instant.",
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.Discard(CostObjects.These(Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                ))))),
                CostPart.Sacrifice(CostObjects.Self),
            )),
            rules = ActivationRules(timing = Timing.Instant),
            instructions = Instructions(
                steps = listOf(add(anyOneColor(3))),
            ),
        ),
    )

    // ---- Mana for each

    @Test
    fun dreamcircleElder() = card(
        name = "Dreamcircle Elder",
        cost = "{G}{G}{G}",
        type = "Creature — Elf Druid",
        text = "{T}: Add {G} for each creature you control.",
        pt = "2/1",
    ) parsesTo abilities(
        tapForEach("{G}", what = yourOwn(ObjectFilter.Type(CardType.Creature))),
    )

    @Test
    fun acolyteOfTheGroveMother() = card(
        name = "Acolyte of the Grove Mother",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        text = "{T}: Add {G} for each Elf on the battlefield.",
        pt = "1/1",
    ) parsesTo abilities(
        tapForEach("{G}", what = elf),
    )

    @Test
    fun tavrosThicketEmissary() = card(
        name = "Tavros, Thicket Emissary",
        cost = "{G}{G}",
        type = "Legendary Creature — Elf Druid",
        text = "{T}: Add {G} for each Forest you control.",
        pt = "2/1",
    ) parsesTo abilities(
        tapForEach("{G}", what = yourOwn(ObjectFilter.Sub(Subtype("Forest")))),
    )

    @Test
    fun elvishHierophant() = card(
        name = "Elvish Hierophant",
        cost = "{1}{G}{G}",
        type = "Creature — Elf Druid",
        text = """
            Other Elf creatures you control get +1/+1.
            {T}: Add {G} for each Elf you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                elf,
                ObjectFilter.Type(CardType.Creature),
                ObjectFilter.ControlledBy(PlayerFilter.You),
            )))),
        ),
        tapForEach("{G}", what = yourOwn(elf)),
    )

    // ---- Tapping other Elves

    @Test
    fun barkwoodPathfinders() = card(
        name = "Barkwood Pathfinders",
        cost = "{G}",
        type = "Creature — Elf Druid Ranger",
        text = """
            Tap two untapped Elves you control: Add one mana of any color.
            Morph {G} (You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(tapElves(2))),
            instructions = Instructions(
                steps = listOf(add(anyOneColor(1))),
            ),
        ),
        Keyword.Morph(manaCost("{G}")),
    )

    @Test
    fun lineageDruid() = card(
        name = "Lineage Druid",
        cost = "{G}",
        type = "Creature — Elf Druid",
        text = "Tap three untapped Elves you control: Add {G}{G}{G}.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(tapElves(3))),
            instructions = Instructions(
                steps = listOf(add(fixed("{G}{G}{G}"))),
            ),
        ),
    )
}
