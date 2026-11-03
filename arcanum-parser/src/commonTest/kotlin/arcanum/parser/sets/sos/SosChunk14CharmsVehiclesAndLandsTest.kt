package arcanum.parser.sets.sos

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
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
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.CardType
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 14: the college charms (CR 700.2) and other two-colour spells, a Vehicle with crew (CR 702.122), and the
 * lands: a land becoming a creature for good with a quoted trigger (CR 611.2a, 613), a chosen land card name kept for linked abilities
 * (CR 607.2a) restricting activated abilities and giving lands a mana ability, lands entering tapped unless you control two or more other
 * lands (CR 614.1c), and surveil lands.
 */
class SosChunk14CharmsVehiclesAndLandsTest {
    private val everyZone = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command).map { inZone(it) }.toTypedArray()
    /** "This land enters tapped unless you control two or more other lands." (CR 614.1c, checked as it would enter) */
    private val entersTappedUnlessTwoOtherLands = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(arcanum.api.Modification.EntersTapped)), condition = not(Condition.Exists(and(another, land, you), atLeast = 2)))
    private fun surveilLand(mana: String) = activated(cost(mana(mana), tap), doing(Effect.Surveil(Players.You, n(1))))

    // CR 701.21a: each opponent chooses which to sacrifice
    @Test
    fun emberloreTrinket() = card(
        name = "Emberlore Trinket",
        cost = "{R}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Each opponent sacrifices a nontoken artifact of their choice.
            • Return target artifact or creature card with mana value 2 or less from your graveyard to the battlefield.
            • Creatures you control get +1/+1 and gain trample until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Choose(Choice.Objects(and(not(token), artifact, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)), bind = "sacrificed",
                    chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)))),
            Mode(Instructions(targets = listOf(slot("card", inYourGraveyard(or(artifact, creature), manaValue(Comparison.AtMost, 2)))),
                steps = listOf(move(target("card"), Zone.Battlefield)))),
            Mode(Instructions(steps = listOf(doing(Effect.ApplyMechanics(listOf(pt(1, 1), has(Keyword.Trample)), Affected.Objects(Objects.Each(creatureYouControl)),
                Duration.EndOfTurn))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 115.4: one or two targets for "each of one or two targets"
    @Test
    fun huecraftTrinket() = card(
        name = "Huecraft Trinket",
        cost = "{U}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Surveil 2, then draw a card.
            • Huecraft Trinket deals 1 damage to each of one or two targets.
            • Return target nonland permanent to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(doing(Effect.Surveil(Players.You, n(2))), draw(1)))),
            Mode(Instructions(targets = listOf(TargetSlot("any", anyTarget.what, min = n(1), max = n(2))), steps = listOf(damage(1, toAny)))),
            Mode(Instructions(targets = listOf(slot("permanent", and(nonland, permanentFilter))), steps = listOf(move(target("permanent"), Zone.Hand)))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 118.12a: "unless its controller pays"; CR 613.4b: base P/T set until end of turn
    @Test
    fun tessellateTrinket() = card(
        name = "Tessellate Trinket",
        cost = "{G}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target spell unless its controller pays {2}.
            • Destroy target enchantment.
            • Target creature has base power and toughness 5/5 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("spell", spellFilter)), steps = listOf(
                Step.Pay(manaCost("{2}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                ifThen(not(Condition.Did("paid")), doing(Effect.Counter(target("spell"))))))),
            Mode(Instructions(targets = listOf(slot("enchantment", enchantment)), steps = listOf(destroy(target("enchantment"))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(5), n(5)))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun inkquillTrinket() = card(
        name = "Inkquill Trinket",
        cost = "{W}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Put two +1/+1 counters on target creature.
            • Exile target creature with power 2 or less.
            • Each opponent loses 3 life and you gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(counters(target("creature"), 2)))),
            Mode(Instructions(targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 2)))), steps = listOf(move(target("creature"), Zone.Exile)))),
            Mode(Instructions(steps = listOf(loseLife(3, Players.Each(PlayerFilter.Opponent)), gainLife(3)))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 118.12: "you may sacrifice a permanent. If you do" is a cost paid on resolution
    @Test
    fun rotbloomTrinket() = card(
        name = "Rotbloom Trinket",
        cost = "{B}{G}",
        type = "Instant",
        text = """
            Choose one —
            • You may sacrifice a permanent. If you do, draw two cards.
            • You gain 5 life.
            • Destroy target nonland permanent with mana value 2 or less.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(Step.Pay(cost(sacrificeA(permanentFilter)), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(2))))),
            Mode(Instructions(steps = listOf(gainLife(5)))),
            Mode(Instructions(targets = listOf(slot("permanent", and(nonland, permanentFilter, manaValue(Comparison.AtMost, 2)))), steps = listOf(destroy(target("permanent"))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun wrestleWithMortality() = card(
        name = "Wrestle with Mortality",
        cost = "{1}{B}{G}",
        type = "Sorcery",
        text = "Destroy target artifact or creature. You gain 1 life.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), gainLife(1), targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    @Test
    fun examinersGlance() = card(
        name = "Examiner's Glance",
        cost = "{2}{G}{U}",
        type = "Instant",
        text = "Return up to one target nonland permanent to its owner's hand. Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Hand),
            search(basicLand()),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
            targets = listOf(slot("permanent", and(nonland, permanentFilter), min = 0)),
        ),
    )

    @Test
    fun spatterMethod() = card(
        name = "Spatter Method",
        cost = "{1}{U}{U}{R}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Draw four cards.
            • Spatter Method deals 4 damage to each creature and planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(draw(4)))),
            Mode(Instructions(steps = listOf(damage(4, toObjects(Objects.Each(or(creature, ObjectFilter.Type(CardType.Planeswalker)))))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun vividEruption() = card(
        name = "Vivid Eruption",
        cost = "{U}{R}",
        type = "Instant",
        text = "Vivid Eruption deals 3 damage to any target. Tap up to one target creature.",
    ) parsesTo abilities(
        spell(damage(3, toAny), tapIt(target("creature")), targets = listOf(anyTarget, slot("creature", creature, min = 0))),
    )

    // CR 702.122a: crew
    @Test
    fun academyAircoach() = card(
        name = "Academy Aircoach",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "3/2",
        text = """
            Flying
            When this Vehicle enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, Step.May(listOf(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), Players.You)),
        Keyword.Crew(2),
    )

    // CR 611.2a: no duration is stated, so the effect lasts indefinitely; CR 205.1b: it's still a land
    @Test
    fun grandAtriumOfTheArchive() = card(
        name = "Grand Atrium of the Archive",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast an instant or sorcery spell.
            {5}: If this land isn't a creature, it becomes a 2/4 Wizard creature with "Whenever you cast an instant or sorcery spell, this creature gets +1/+0 until end of turn." It's still a land.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap, CostPart.PayLife(n(1))), doing(Effect.AddMana(ManaProduction(anyOneColor(1), restriction = and(spellFilter, instantOrSorcery)), Players.You))),
        activated(manaCost("{5}"), ifThen(not(Condition.ObjectMatches(Objects.Self, creature)), doing(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Wizard")))),
            Mechanic.SetPowerToughness(n(2), n(4)),
            has(triggered(youCastInstantOrSorcery, pump(Objects.Self, 1, 0)))), Affected.Objects(Objects.Self), Duration.Indefinitely)))),
    )

    // CR 607.2a: the chosen name is kept for the linked abilities
    @Test
    fun ossifiedVillage() = card(
        name = "Ossified Village",
        cost = null,
        type = "Land",
        text = """
            When this land enters, choose a land card name.
            Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
            Lands with the chosen name have "{T}: Add {C}."
            {T}: Add {C}.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Choose(Choice.CardName(land), bind = "name", remember = true, chooser = Players.You)),
        static(Mechanic.CantBeActivated(manaAbilities = false), affects = Objects.Each(and(ObjectFilter.RememberedName("name"), or(*everyZone)))),
        static(has(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))),
            affects = Objects.Each(and(land, ObjectFilter.RememberedName("name")))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
    )

    @Test
    fun mushroomHollow() = card(
        name = "Mushroom Hollow",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {B} or {G}.
        """,
    ) parsesTo abilities(
        entersTappedUnlessTwoOtherLands, addEither("B", "G"),
    )

    @Test
    fun reverieFalls() = card(
        name = "Reverie Falls",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {G} or {U}.
        """,
    ) parsesTo abilities(
        entersTappedUnlessTwoOtherLands, addEither("G", "U"),
    )

    @Test
    fun meadowsOfContention() = card(
        name = "Meadows of Contention",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {R} or {W}.
            {2}{R}{W}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        entersTapped, addEither("R", "W"), surveilLand("{2}{R}{W}"),
    )

    @Test
    fun agoraOfGoodwill() = card(
        name = "Agora of Goodwill",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {W} or {B}.
            {2}{W}{B}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        entersTapped, addEither("W", "B"), surveilLand("{2}{W}{B}"),
    )

    @Test
    fun contradictionTerrace() = card(
        name = "Contradiction Terrace",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G} or {U}.
            {2}{G}{U}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        entersTapped, addEither("G", "U"), surveilLand("{2}{G}{U}"),
    )

    @Test
    fun brokenSanctuary() = card(
        name = "Broken Sanctuary",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {W} or {B}.
        """,
    ) parsesTo abilities(
        entersTappedUnlessTwoOtherLands, addEither("W", "B"),
    )

    @Test
    fun pageantPeak() = card(
        name = "Pageant Peak",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {U} or {R}.
            {2}{U}{R}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        entersTapped, addEither("U", "R"), surveilLand("{2}{U}{R}"),
    )

    @Test
    fun galewornShore() = card(
        name = "Galeworn Shore",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {U} or {R}.
        """,
    ) parsesTo abilities(
        entersTappedUnlessTwoOtherLands, addEither("U", "R"),
    )

    @Test
    fun dusklightGap() = card(
        name = "Dusklight Gap",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {R} or {W}.
        """,
    ) parsesTo abilities(
        entersTappedUnlessTwoOtherLands, addEither("R", "W"),
    )

    @Test
    fun shapeshiftingSteppe() = card(
        name = "Shapeshifting Steppe",
        cost = null,
        type = "Land",
        text = "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        activated(cost(tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    @Test
    fun colossusBarrow() = card(
        name = "Colossus Barrow",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B} or {G}.
            {2}{B}{G}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        entersTapped, addEither("B", "G"), surveilLand("{2}{B}{G}"),
    )
}
