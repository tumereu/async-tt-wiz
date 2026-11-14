package arcanum.parser.sets.bro

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 16: moving cards between zones, a replacement effect and revealing a hand. */
class BroChunk16MoveReplacementRevealTest {
    private val ownedByYou = ObjectFilter.OwnedBy(PlayerFilter.You)
    private val charge = CounterKind.Named("charge")
    private val artifactOrCreature = or(artifact, creature)

    // CR 400.3: returned to its owner's hand; CR 707.10: copying a spell, the copy keeping its targets (new ones only when the text says so,
    // CR 707.10c)
    @Test
    fun drafnasKin() = card(
        name = "Drafna's Kin", cost = "{1}{U}", type = "Legendary Creature — Human Artificer Advisor", pt = "2/1",
        text = """
            {1}{U}: Return target artifact you control to its owner's hand.
            {3}, {T}: Copy target artifact spell you control. (The copy becomes a token.)
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{U}")), move(target("artifact"), Zone.Hand), targets = listOf(slot("artifact", and(artifact, you)))),
        activated(cost(mana("{3}"), tap), doing(Effect.CopySpell(target("spell"), Players.You, newTargets = false)),
            targets = listOf(slot("spell", and(artifact, spellFilter, you)))),
    )

    // CR 400.3, 111.1: a card back to hand, then a token
    @Test
    fun weldingRite() = card(
        name = "Welding Rite", cost = "{1}{B}", type = "Sorcery",
        text = "Return target artifact or creature card from your graveyard to your hand. Create a 1/1 colorless Soldier artifact creature token.",
    ) parsesTo abilities(spell(
        move(target("card"), Zone.Hand),
        create(creatureToken("Soldier", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))),
        targets = listOf(slot("card", and(artifactOrCreature, inZone(Zone.Graveyard), ownedByYou))),
    ))

    // CR 400.3, 202.3: a card of mana value 3 or less returns to the battlefield; a static boost keyed on mana value
    @Test
    fun heroOfTheSands() = card(
        name = "Hero of the Sands", cost = "{3}{W}{B}", type = "Creature — Human Soldier", pt = "3/2",
        text = """
            When this creature enters, return target artifact or creature card with mana value 3 or less from your graveyard to the battlefield.
            Creatures you control with mana value 3 or less get +1/+0.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(artifactOrCreature, manaValue(Comparison.AtMost, 3))))),
        static(Mechanic.ModifyPowerToughness(n(1), n(0)), affects = Objects.Each(and(creature, you, manaValue(Comparison.AtMost, 3)))),
    )

    // CR 401.4, 400.3: a card from any graveyard goes to the bottom of its owner's library
    @Test
    fun keeperOfThePulse() = card(
        name = "Keeper of the Pulse", cost = "{4}{U}", type = "Creature — Human Wizard", pt = "2/5",
        text = "{3}: Put target artifact, instant, or sorcery card from a graveyard on the bottom of its owner's library.",
    ) parsesTo abilities(
        activated(cost(mana("{3}")), move(target("card"), Zone.Library, position = -1),
            targets = listOf(slot("card", and(or(artifact, instant, sorcery), inZone(Zone.Graveyard))))),
    )

    // CR 202.3, 119.1: exile by a count of lands; the creature's controller gains the life
    @Test
    fun layDownSpears() = card(
        name = "Lay Down Spears", cost = "{W}", type = "Sorcery",
        text = "Exile target creature with mana value less than or equal to the number of Plains you control. Its controller gains 3 life.",
    ) parsesTo abilities(spell(
        move(target("creature"), Zone.Exile),
        gainLife(3, Players.ControllerOf(target("creature"))),
        targets = listOf(slot("creature", and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Count(Objects.Each(and(sub("Plains"), you))))))),
    ))

    // CR 603.2, 400.3: a trigger on itself or another legendary creature entering
    @Test
    fun lorinStudentOfTales() = card(
        name = "Lorin, Student of Tales", cost = "{3}{W}", type = "Legendary Creature — Human Artificer", pt = "3/3",
        text = "Whenever Lorin or another legendary creature you control enters, return target artifact card from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(enters(or(isSelf, and(another, legendary, creature, you))), move(target("card"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(artifact)))),
    )

    // CR 700.1: a target of one of three kinds, the last restricted by flying
    @Test
    fun shootUp() = card(
        name = "Shoot Up", cost = "{3}{G}", type = "Sorcery",
        text = "Exile target artifact, enchantment, or creature with flying.",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying)))))),
    )

    // CR 700.2d: two of four modes; CR 701.23: search for two lands; CR 122.1: counters on each creature
    @Test
    fun titansCommand() = card(
        name = "Titan's Command", cost = "{4}{G}{G}", type = "Sorcery",
        text = """
            Choose two —
            • Exile target player's graveyard. You gain 1 life for each card exiled this way.
            • Search your library for up to two land cards, put them onto the battlefield tapped, then shuffle.
            • Create two 2/2 green Bear creature tokens.
            • Put two +1/+1 counters on each creature you control.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = modes(min = 2, max = 2, options = arrayOf(
        mode(
            move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.GainLife(Players.You, Value.Times(Value.Count(Objects.Bound("exiled")), 1))),
            targets = listOf(targetPlayer),
        ),
        mode(search(land, max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        mode(create(creatureToken("Bear", listOf(Color.Green), 2, 2), n(2))),
        mode(counters(Objects.Each(creatureYouControl), 2)),
    )))))

    // CR 400.7, 205.3: "if it was a Soldier" looks at the returned creature as it was on the battlefield
    @Test
    fun zephyrScout() = card(
        name = "Zephyr Scout", cost = "{1}{U}", type = "Creature — Human Soldier", pt = "2/1",
        text = """
            Flash
            Flying
            When this creature enters, return up to one other target creature you control to its owner's hand. If it was a Soldier, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, move(target("creature"), Zone.Hand),
            Step.If(Condition.ObjectMatches(target("creature"), sub("Soldier")), then = listOf(counters(Objects.Self))),
            targets = listOf(slot("creature", and(another, creature, you), min = 0))),
    )

    // CR 122.1, 701.8: counters removed as the land is sacrificed (last known information, CR 608.2h)
    @Test
    fun blastPlain() = card(
        name = "Blast Plain", cost = null, type = "Land",
        text = """
            This land enters with a charge counter on it.
            {T}: Add {C}.
            {X}{X}, {T}: Put X charge counters on this land.
            {3}, {T}, Sacrifice this land: Destroy each nonland permanent with mana value equal to the number of charge counters on this land.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(charge, n(1))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        Ability.Activated(cost(mana("{X}{X}"), tap), Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, charge, Value.Announced("X")))), announced = listOf("X")), anyTime),
        activated(cost(mana("{3}"), tap, sacrificeSelf), destroy(Objects.Each(and(nonland, permanentFilter,
            ObjectFilter.ManaValue(Comparison.Equal, Value.Of(NumericProperty.Counters(charge), Objects.Self, Combine.Single)))))),
    )

    // CR 614.1c: enters tapped; a graveyard-only return
    @Test
    fun clayHeir() = card(
        name = "Clay Heir", cost = "{1}", type = "Artifact Creature — Golem", pt = "1/2",
        text = """
            This creature enters tapped.
            {2}{B}: Return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        activated(manaCost("{2}{B}"), move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 614.6, 700.4: an opponent's creature that would die is exiled instead
    @Test
    fun miserysPall() = card(
        name = "Misery's Pall", cost = "{1}{B}", type = "Creature — Shade", pt = "2/2",
        text = """
            If a creature an opponent controls would die, exile it instead.
            {1}: This creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        activated(manaCost("{1}"), pump(Objects.Self, 1, 1)),
    )

    // CR 701.20a, 701.8: reveal a hand; choose from the hand, then from the graveyard; exile both
    @Test
    fun dreamsOfIronAndOil() = card(
        name = "Dreams of Iron and Oil", cost = "{B}", type = "Sorcery",
        text = "Target opponent reveals their hand. You choose an artifact or creature card from it, then choose an artifact or creature card from their graveyard. Exile the chosen cards.",
    ) parsesTo abilities(spell(
        Step.Do(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
        Step.Choose(Choice.Objects(artifactOrCreature, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
        Step.Choose(Choice.Objects(and(artifactOrCreature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
            bind = "chosenFromGraveyard", chooser = Players.You),
        move(Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("chosenFromGraveyard"))), Zone.Exile),
        targets = listOf(targetOpponent),
    ))
}
