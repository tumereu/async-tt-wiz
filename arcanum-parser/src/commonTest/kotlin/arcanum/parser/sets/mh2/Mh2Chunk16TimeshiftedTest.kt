package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 16: the timeshifted reprints, second part: enchantment cast triggers, storm, returning a land as a cost, a
 * Squirrel count, land death triggers, a dies trigger searching for two lands, casting a creature from the graveyard once (CR 601.2a) with
 * haste for creatures not cast from the hand, a split card dividing damage (CR 601.2d), an extra mana trigger (CR 605.1b, 106.12),
 * shroud for other enchantments with a tutor to the top (CR 701.24b), creatures' abilities that can't be activated (CR 602.5), milling as a
 * cost, regeneration (CR 701.19), mana for each Swamp, a land that becomes an artifact creature and stays a land (CR 613.1d) and a number
 * chosen as it enters for spells that can't be cast.
 */
class Mh2Chunk16TimeshiftedTest {
    @Test
    fun enchantresssInfluence() = card(
        name = "Enchantress's Influence",
        cost = "{2}{G}",
        type = "Enchantment",
        text = "Whenever you cast an enchantment spell, draw a card.",
    ) parsesTo abilities(triggered(youCast(enchantment), draw(1)))

    @Test
    fun prowlingPack() = card(
        name = "Prowling Pack",
        cost = "{5}{G}{G}",
        type = "Instant",
        text = """
            Create a 4/4 green Beast creature token.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(spell(create(creatureToken("Beast", listOf(Color.Green), 4, 4))), Keyword.Storm)

    // CR 602.1a: returning a Forest is the cost
    @Test
    fun quirionWarden() = card(
        name = "Quirion Warden",
        cost = "{G}",
        type = "Creature — Elf Ranger",
        text = "Return a Forest you control to its owner's hand: Untap target creature. Activate only once each turn.",
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(CostPart.ReturnToHand(CostObjects.Choose(and(sub("Forest"), you), count = n(1)))), untapIt(target("creature")),
            targets = listOf(slot("creature", creature)), rules = anyTime.copy(perTurn = 1)),
    )

    @Test
    fun squirrelThrong() = card(
        name = "Squirrel Throng",
        cost = "{1}{G}{G}",
        type = "Creature — Squirrel",
        text = "This creature gets +1/+1 for each other Squirrel on the battlefield.",
        pt = "2/2",
    ) parsesTo abilities(static(Mechanic.ModifyPowerToughness(count(and(another, sub("Squirrel"))), count(and(another, sub("Squirrel"))))))

    // CR 700.4: "put into a graveyard from the battlefield", any land of yours, not only dying creatures
    @Test
    fun titanyaKeeperOfArgoth() = card(
        name = "Titanya, Keeper of Argoth",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Elemental",
        text = """
            When Titanya enters, return target land card from your graveyard to the battlefield.
            Whenever a land you control is put into a graveyard from the battlefield, create a 5/3 green Elemental creature token.
        """,
        pt = "5/3",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(land)))),
        triggered(on(EventPattern.ZoneChange(and(land, you), from = Zone.Battlefield, to = Zone.Graveyard)), create(creatureToken("Elemental", listOf(Color.Green), 5, 3))),
    )

    @Test
    fun yavimayaAncient() = card(
        name = "Yavimaya Ancient",
        cost = "{1}{G}{G}",
        type = "Creature — Human Druid",
        text = """
            When this creature dies, you may search your library for up to two basic land cards, reveal them, put them into your hand, then shuffle.
            {2}, Sacrifice this creature: Draw a card.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(diesSelf, Step.May(listOf(search(basicLand, max = n(2), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), Players.You)),
        activated(cost(mana("{2}"), sacrificeSelf), draw(1)),
    )

    // CR 601.2a: one creature spell from your graveyard; CR 603.4: not cast from your hand
    @Test
    fun charnelNightmareAdept() = card(
        name = "Charnel, Nightmare Adept",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Human Minion",
        text = """
            Discard a card: You may cast a creature spell from your graveyard this turn. Activate only once each turn.
            Whenever a nontoken creature you control enters, if you didn't cast it from your hand, it gains haste until your next turn.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        activated(cost(discardACard), doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, perTurn = 1, holder = PlayerFilter.You)),
            Affected.Objects(Objects.Each(inYourGraveyard(creature))), Duration.EndOfTurn)), rules = anyTime.copy(perTurn = 1)),
        triggered(enters(and(nontoken, creature, you)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(enteredOne), Duration.UntilYourNextTurn)),
            interveningIf = Condition.Not(Condition.CastFrom(Zone.Hand, enteredOne))),
    )

    // CR 601.2d: 2 damage divided among one or two targets
    @Test
    fun smolderSleet() = cardFaces(
        Layout.Split,
        card(name = "Smolder", cost = "{1}{R}", type = "Instant", text = "Smolder deals 2 damage divided as you choose among one or two targets.") to abilities(
            spell(damage(Value.DividedShare, Recipients(target = "any")), targets = listOf(anyTarget.copy(max = n(2), divided = n(2))))),
        card(name = "Sleet", cost = "{1}{U}", type = "Instant", text = """
            Tap target permanent.
            Draw a card.
        """) to abilities(spell(tapIt(target("permanent")), draw(1), targets = listOf(slot("permanent", permanentFilter)))),
    )

    // CR 605.1b: a triggered mana ability adding one mana of a type the land produced (CR 106.12)
    @Test
    fun mirarisEcho() = card(
        name = "Mirari's Echo",
        cost = "{3}{G}{W}",
        type = "Enchantment",
        text = """
            Creatures you control get +1/+1.
            Whenever you tap a land for mana, add one mana of any type that land produced.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(creatureYouControl)),
        triggered(on(EventPattern.Tap(and(land, you), forMana = true)), doing(Effect.AddMana(ManaProduction(ManaKind.SameAsProduced(n(1))), Players.You))),
    )

    @Test
    fun shardboundEnvoy() = card(
        name = "Shardbound Envoy",
        cost = "{1}{G}{U}",
        type = "Artifact Creature — Human Rogue",
        text = "Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)",
        pt = "2/2",
    ) parsesTo abilities(Keyword.Cascade)

    // CR 701.24b: shuffled, then the found card put on top
    @Test
    fun silverleafGrove() = card(
        name = "Silverleaf Grove",
        cost = "{G}{W}",
        type = "Enchantment",
        text = """
            Other enchantments you control have shroud. (They can't be the targets of spells or abilities.)
            {1}, Sacrifice this enchantment: Search your library for an enchantment card, reveal it, then shuffle and put that card on top.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Shroud), affects = Objects.Each(and(another, enchantment, you))),
        activated(cost(mana("{1}"), sacrificeSelf), search(enchantment, reveal = true), doing(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0)),
    )

    @Test
    fun vindication() = card(
        name = "Vindication",
        cost = "{1}{W}{B}",
        type = "Sorcery",
        text = "Destroy target permanent.",
    ) parsesTo abilities(spell(destroy(target("permanent")), targets = listOf(slot("permanent", permanentFilter))))

    // CR 602.5: no player can activate them, mana abilities included
    @Test
    fun cursedIdol() = card(
        name = "Cursed Idol",
        cost = "{2}",
        type = "Artifact",
        text = "Activated abilities of creatures can't be activated.",
    ) parsesTo abilities(playerStatic(Mechanic.CantActivate(ObjectFilter.AbilityOf(source = ObjectFilter.Or(listOf(creature)))), players = Players.Each(PlayerFilter.Any)))

    @Test
    fun extrusionGolem() = card(
        name = "Extrusion Golem",
        cost = "{4}",
        type = "Artifact Creature — Juggernaut",
        text = """
            Echo {4} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
            Sacrifice an artifact: Put a +1/+1 counter on target creature.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Echo(manaCost("{4}")),
        activated(cost(sacrificeA(artifact)), counters(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 605.1a: a mana ability whose cost mills
    @Test
    fun grindlet() = card(
        name = "Grindlet",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        text = "{T}, Mill a card: Add {C}. (Activate only as an instant. To mill a card, put the top card of your library into your graveyard.)",
        pt = "0/1",
    ) parsesTo abilities(activated(cost(tap, CostPart.Mill(n(1))), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))))

    @Test
    fun nevinyrralsPlatter() = card(
        name = "Nevinyrral's Platter",
        cost = "{4}",
        type = "Artifact",
        text = """
            This artifact enters tapped.
            {1}, {T}: Destroy all artifacts, creatures, and enchantments.
        """,
    ) parsesTo abilities(entersTapped, activated(cost(mana("{1}"), tap), destroy(Objects.Each(or(artifact, creature, enchantment)))))

    // CR 701.19a regenerate
    @Test
    fun quiltworkGnomes() = card(
        name = "Quiltwork Gnomes",
        cost = "{3}",
        type = "Artifact Creature — Gnome",
        text = "Discard a card: Regenerate this creature. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)",
        pt = "2/1",
    ) parsesTo abilities(activated(cost(discardACard), doing(Effect.Action(KeywordAction.Regenerate(Objects.Self)))))

    @Test
    fun zuranSphere() = card(
        name = "Zuran Sphere",
        cost = "{0}",
        type = "Artifact",
        text = "Sacrifice a land: You gain 2 life.",
    ) parsesTo abilities(activated(cost(sacrificeA(land)), gainLife(2)))

    // CR 106.1: {B} once for each Swamp
    @Test
    fun cabalVaults() = card(
        name = "Cabal Vaults",
        cost = null,
        type = "Land",
        text = "{2}, {T}: Add {B} for each Swamp you control.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!, times = count(and(sub("Swamp"), you)))), Players.You))),
    )

    // CR 613.1d: a 2/2 artifact creature until end of turn, still a land
    @Test
    fun mishrasWorkfloor() = card(
        name = "Mishra's Workfloor",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}: This land becomes a 2/2 Assembly-Worker artifact creature until end of turn. It's still a land.
            {T}: Target Assembly-Worker creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{1}")), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature),
            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Assembly-Worker")))), Mechanic.SetPowerToughness(n(2), n(2)))),
        activated(cost(tap), pump(target("creature"), 1, 1), targets = listOf(slot("creature", and(sub("Assembly-Worker"), creature)))),
    )

    @Test
    fun riptideWorkshop() = card(
        name = "Riptide Workshop",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}{U}, {T}: Return target Wizard you control to its owner's hand.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{1}{U}"), tap), move(target("Wizard"), Zone.Hand), targets = listOf(slot("Wizard", and(sub("Wizard"), you)))),
    )

    // CR 614.12: the number chosen as it enters; CR 101.2: those spells can't be cast by anyone
    @Test
    fun sanctumHierarch() = card(
        name = "Sanctum Hierarch",
        cost = "{1}{W}{W}",
        type = "Creature — Human Cleric",
        text = """
            As this creature enters, choose a number.
            Noncreature spells with mana value equal to the chosen number can't be cast.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Number(n(0)), bind = "number", chooser = Players.You, remember = true)))))),
        playerStatic(Mechanic.CantCast(and(noncreature, ObjectFilter.ManaValue(Comparison.Equal, Value.Remembered("number")))), players = Players.Each(PlayerFilter.Any)),
    )
}
