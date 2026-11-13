package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** WOE chunk 05: cards that scry (CR 701.22) and the wordings found with them. */
class WoeChunk05ScryTest {
    private val entersTapped = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)))
    private val addColorless = activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))
    private val anyColor = ManaProduction(ManaKind.Choose(n(1)))
    private val enchantmentYouControl = and(enchantment, you)
    private val diesFromBattlefield = diesSelf
    private val eachOpp = Players.Each(PlayerFilter.Opponent)

    // CR 603.12 (reflexive trigger), 701.26a (tapping by a player's effect), 700.2 (modes)
    @Test
    fun hyldaOfTheIcyCrown() = card(
        name = "Hilvara of the Frosted Crown",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Human Warlock",
        pt = "3/4",
        text = """
            Whenever you tap an untapped creature an opponent controls, you may pay {1}. When you do, choose one —
            • Create a 4/4 white and blue Elemental creature token.
            • Put a +1/+1 counter on each creature you control.
            • Scry 2, then draw a card.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(creatureOpponentControls, by = PlayerFilter.You)),
            Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(modes = modes(
                mode(create(creatureToken("Elemental", listOf(Color.White, Color.Blue), 4, 4))).copy(text = "Create a 4/4 white and blue Elemental creature token."),
                mode(counters(Objects.Each(creatureYouControl))).copy(text = "Put a +1/+1 counter on each creature you control."),
                mode(scry(2), draw(1)).copy(text = "Scry 2, then draw a card.")))))))),
    )

    // CR 205.1b (it's still a land), 611.2a (until end of turn), 604.3a (a granted ability isn't characteristic-defining)
    @Test
    fun restlessSpire() = card(
        name = "Unquiet Pinnacle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {U} or {R}.
            {U}{R}: Until end of turn, this land becomes a 2/1 blue and red Elemental creature with "During your turn, this creature has first strike." It's still a land.
            Whenever this land attacks, scry 1.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Red)))), Players.You))),
        activated(cost(mana("{U}{R}")), Step.Do(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental"))), replaceSubtypes = true),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Red))),
            Mechanic.SetPowerToughness(n(2), n(1)),
            Mechanic.GainAbilities(listOf(static(has(Keyword.FirstStrike), condition = Condition.YourTurn)))),
            Affected.Objects(Objects.Self), Duration.EndOfTurn))),
        triggered(attacksSelf, scry(1)),
    )

    // CR 701.22a, 603.6a
    @Test
    fun slumberingKeepguard() = card(
        name = "Drowsing Wallguard",
        cost = "{W}",
        type = "Creature — Human Knight",
        pt = "1/1",
        text = """
            Whenever an enchantment you control enters, scry 1.
            {2}{W}: This creature gets +1/+1 until end of turn for each enchantment you control.
        """,
    ) parsesTo abilities(
        triggered(enters(enchantmentYouControl), scry(1)),
        activated(cost(mana("{2}{W}")), untilEndOfTurn(Objects.Self,
            Mechanic.ModifyPowerToughness(count(enchantmentYouControl), count(enchantmentYouControl)))),
    )

    // CR 707.2 (copiable values, not legendary), 111.1, 205.3 (the token's types)
    @Test
    fun yennaRedtoothRegent() = card(
        name = "Yarrow, Greenbark Regent",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Elf Noble",
        pt = "4/4",
        text = """
            {2}, {T}: Choose target enchantment you control that doesn't have the same name as another permanent you control. Create a token that's a copy of it, except it isn't legendary. If the token is an Aura, untap Yarrow, then scry 2. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap),
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(target("permanent"), except = listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)))),
                n(1), Players.You, bind = "token")),
            Step.If(Condition.ObjectMatches(Objects.Bound("token"), sub("Aura")), then = listOf(untapIt(Objects.Self), scry(2))),
            targets = listOf(slot("permanent", and(enchantmentYouControl, ObjectFilter.Not(ObjectFilter.SameNameAs(Objects.Each(and(permanentFilter, you)), other = true))))),
            rules = asSorcery),
    )

    // CR 701.22a, 111.10 (Food), Clue (CR 111.10f): the sacrifice ability is the card's own
    @Test
    fun candyTrail() = card(
        name = "Toffee Path",
        cost = "{1}",
        type = "Artifact — Food Clue",
        text = """
            When this artifact enters, scry 2.
            {2}, {T}, Sacrifice this artifact: You gain 3 life and draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
        activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3), draw(1)),
    )

    // CR 605.1a (mana abilities), 701.22a
    @Test
    fun crystalGrotto() = card(
        name = "Glassy Hollow",
        cost = null,
        type = "Land",
        text = """
            When this land enters, scry 1.
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(1)),
        addColorless,
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(anyColor, Players.You))),
    )

    // CR 122.1d (stun counters), 701.22a
    @Test
    fun freezeInPlace() = card(
        name = "Lock in Frost",
        cost = "{1}{U}",
        type = "Sorcery",
        text = "Tap target creature an opponent controls and put three stun counters on it. Scry 2. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        spell(tapIt(target("creature")), counters(target("creature"), 3, CounterKind.Stun), scry(2),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 700.4 (put into a graveyard from the battlefield), 701.22a
    @Test
    fun hopefulVigil() = card(
        name = "Patient Watch",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 2/2 white Knight creature token with vigilance.
            When this enchantment is put into a graveyard from the battlefield, scry 2.
            {2}{W}: Sacrifice this enchantment.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Knight", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance)))),
        triggered(diesFromBattlefield, scry(2)),
        activated(cost(mana("{2}{W}")), doing(Effect.Sacrifice(Objects.Self))),
    )

    // CR 700.4, 701.22a, 701.9a (discard), 119.3
    @Test
    fun hopelessNightmare() = card(
        name = "Futile Terror",
        cost = "{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, each opponent discards a card and loses 2 life.
            When this enchantment is put into a graveyard from the battlefield, scry 2.
            {2}{B}: Sacrifice this enchantment.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, discard(1, eachOpp), loseLife(2, eachOpp)),
        triggered(diesFromBattlefield, scry(2)),
        activated(cost(mana("{2}{B}")), doing(Effect.Sacrifice(Objects.Self))),
    )

    // CR 613.4c, 702.7a, 701.22a
    @Test
    fun kindledHeroism() = card(
        name = "Sparked Valor",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +1/+0 and gains first strike until end of turn. Scry 1.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(1, 0), has(Keyword.FirstStrike)), scry(1),
            targets = listOf(slot("creature", creature))),
    )

    // CR 700.2 (modes), 608.2h, 701.22a, 701.21a (sacrifice), 603.12
    @Test
    fun lordSkittersButcher() = card(
        name = "Lady Scurry's Cleaver",
        cost = "{2}{B}",
        type = "Creature — Rat Peasant",
        pt = "2/3",
        text = """
            When this creature enters, choose one —
            • Create a 1/1 black Rat creature token with "This token can't block."
            • You may sacrifice another creature. If you do, scry 2, then draw a card.
            • Creatures you control gain menace until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(create(creatureToken("Rat", listOf(Color.Black), 1, 1, listOf(static(Mechanic.CantBlock(ObjectFilter.Any)))))),
            mode(Step.Pay(cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(scry(2), draw(1)))),
            mode(gains(Objects.Each(creatureYouControl), Keyword.Menace))))),
    )

    // CR 700.4, 701.22a
    @Test
    fun merfolkCoralsmith() = card(
        name = "Sirenfolk Reefwright",
        cost = "{2}{U}",
        type = "Creature — Merfolk",
        pt = "2/3",
        text = """
            {1}: This creature gets +1/-1 until end of turn.
            When this creature dies, scry 2.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}")), pump(Objects.Self, 1, -1)),
        triggered(diesSelf, scry(2)),
    )

    // CR 700.4 (an enchantment put into a graveyard from the battlefield), 122.1 (+1/+1 counter), 701.22a
    @Test
    fun nevaStalkedByNightmares() = card(
        name = "Nessa, Haunted by Dreams",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Human Noble",
        pt = "2/2",
        text = """
            Menace
            When Nessa enters, return target creature or enchantment card from your graveyard to your hand.
            Whenever an enchantment you control is put into a graveyard from the battlefield, put a +1/+1 counter on Nessa, then scry 1.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(creature, enchantment))))),
        triggered(on(EventPattern.ZoneChange(enchantmentYouControl, from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self), scry(1)),
    )

    // CR 601.2c (up to one target), 701.22a, 121.1
    @Test
    fun plungeIntoWinter() = card(
        name = "Dive into Autumn",
        cost = "{1}{W}",
        type = "Instant",
        text = "Tap up to one target creature. Scry 1, then draw a card.",
    ) parsesTo abilities(
        spell(tapIt(target("creature")), scry(1), draw(1), targets = listOf(slot("creature", creature, min = 0))),
    )

    // CR 400.3 (owner's hand), 608.2c ("if you do" after a may)
    @Test
    fun stockpilingCelebrant() = card(
        name = "Hoarding Reveler",
        cost = "{2}{W}",
        type = "Creature — Dwarf Knight",
        pt = "3/2",
        text = "When this creature enters, you may return another target nonland permanent you control to its owner's hand. If you do, scry 2.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(steps = listOf(move(target("permanent"), Zone.Hand)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(scry(2))),
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter, you)))),
    )

    // CR 700.4 (an artifact put into a graveyard from the battlefield), 119.3, 608.2h (its power as it last existed)
    @Test
    fun syrGingerTheMealEnder() = card(
        name = "Sir Pepper, the Feast Breaker",
        cost = "{2}",
        type = "Legendary Artifact Creature — Food Knight",
        pt = "3/1",
        text = """
            Sir Pepper has trample, hexproof, and haste as long as an opponent controls a planeswalker.
            Whenever another artifact you control is put into a graveyard from the battlefield, put a +1/+1 counter on Sir Pepper and scry 1.
            {2}, {T}, Sacrifice Sir Pepper: You gain life equal to its power.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Trample, Keyword.Hexproof, Keyword.Haste), condition = Condition.Exists(and(ObjectFilter.Type(CardType.Planeswalker), opponents), 1)),
        triggered(on(EventPattern.ZoneChange(and(another, artifact, you), from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self), scry(1)),
        activated(cost(mana("{2}"), tap, sacrificeSelf), Step.Do(Effect.GainLife(Players.You, power(Objects.Self)))),
    )

    // CR 701.22a, 608.2c ("if you control an enchantment" checked on resolution)
    @Test
    fun takenByNightmares() = card(
        name = "Claimed by Dreams",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = "Exile target creature. If you control an enchantment, scry 2.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile),
            Step.If(Condition.Exists(enchantmentYouControl, 1), then = listOf(scry(2))),
            targets = listOf(slot("creature", creature))),
    )
}
