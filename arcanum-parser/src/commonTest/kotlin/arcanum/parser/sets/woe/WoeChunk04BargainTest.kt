package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** WOE chunk 04: Bargain (CR 702.166) and the cards that do more when bargained. */
class WoeChunk04BargainTest {
    private val bargain = "Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)"
    // CR 702.166b
    private val bargained = Condition.Paid(Keyword.Bargain, times = 1, of = Objects.Self)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val blocking = ObjectFilter.HasStatus(Status.Blocking)
    private val creatureOrPlaneswalker = slot("permanent", or(creature, planeswalker))
    private val x = Value.Announced(name = "X")
    private fun costsLessIfBargained(amount: String) = Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self),
        CostChange.Less(ManaCost.parse(amount)!!, n(1)))), affects = Affected.Objects(Objects.Self), condition = bargained)
    private fun lookAt(count: Int) = doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(count), fromTop = true), bind = "seen", players = Players.You))
    private val seen = Objects.Bound("seen")

    // CR 702.166b, 400.7: the card put onto the battlefield instead is chosen among the returned targets as the spell resolves
    @Test
    fun secondsReclaimed() = card(
        name = "Seconds Reclaimed",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            $bargain
            Return up to two target creature cards from your graveyard to your hand. If this spell was bargained, you may put one of those cards with mana value 4 or less onto the battlefield instead of putting it into your hand.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained,
                then = listOf(
                    Step.Choose(Choice.Objects(manaValue(Comparison.AtMost, 4), n(0), n(1), from = target("cards")), bind = "chosen", chooser = Players.You),
                    move(Objects.Bound("chosen"), Zone.Battlefield),
                    move(Objects.Except(target("cards"), Objects.Bound("chosen")), Zone.Hand)),
                otherwise = listOf(move(target("cards"), Zone.Hand))),
            targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2)),
        ),
    )

    // CR 702.166b, 406.3 (exiled face down), 601.2 (cast without paying its mana cost, the spell's mana value checked, CR 118.9)
    @Test
    fun pleadTheLookingGlass() = card(
        name = "Plead the Looking Glass",
        cost = "{1}{B}{B}{B}",
        type = "Sorcery",
        text = """
            $bargain
            Search your library for a card, exile it face down, then shuffle. If this spell was bargained, you may cast the exiled card without paying its mana cost if that spell's mana value is 4 or less. Put the exiled card into your hand if it wasn't cast this way.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            search(cardFilter),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            shuffle,
            Step.If(bargained, then = listOf(Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast, withoutPaying = true,
                caster = Players.You, resultingSpell = manaValue(Comparison.AtMost, 4)))), payer = Players.You, bind = "cast"))),
            Step.If(Condition.Not(Condition.Did("cast")), then = listOf(move(Objects.Bound("exiled"), Zone.Hand))),
        ),
    )

    // CR 702.166b; CR 601.2c: the land is a target only if the spell is bargained; CR 205.1b, 611.2a (a land creature for good)
    @Test
    fun dareTheThicket() = card(
        name = "Dare the Thicket",
        cost = "{G}",
        type = "Sorcery",
        text = """
            $bargain
            If this spell was bargained, target land you control becomes a 3/3 Elemental creature with haste that's still a land.
            Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental"))), replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(3), n(3)),
                Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(target("land")), Duration.Indefinitely)))),
            search(basicLand(), reveal = true),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle,
            targets = listOf(slot("land", and(land, you)).copy(onlyIf = bargained)),
        ),
    )

    // CR 702.166b, 603.4 (intervening "if"), CR 303.4 (an Aura token attached as it's created)
    @Test
    fun shrinkingHexer() = card(
        name = "Shrinking Hexer",
        cost = "{2}{U}",
        type = "Creature — Human Warlock",
        pt = "3/2",
        text = """
            $bargain
            When this creature enters, if it was bargained, create a Cursed Role token attached to target creature an opponent controls. (If you control another Role on it, put that one into the graveyard. Enchanted creature is 1/1.)
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        triggered(entersSelf, createRole("Cursed", target("creature")), targets = listOf(slot("creature", creatureOpponentControls)), interveningIf = bargained),
    )

    // CR 702.166b, 401.4: eight cards instead of four; the rest go to the bottom in a random order
    @Test
    fun longsightRite() = card(
        name = "Longsight Rite",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            $bargain
            Look at the top four cards of your library. If this spell was bargained, look at the top eight cards of your library instead. Put two of them into your hand and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(lookAt(8)), otherwise = listOf(lookAt(4))),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = seen), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            doing(Effect.Move(Objects.Except(seen, Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 702.166b, 601.2f: the reduction applies when the spell is bargained (the additional cost chosen before the total cost)
    @Test
    fun villageGorger() = card(
        name = "Village Gorger",
        cost = "{5}{G}{G}",
        type = "Creature — Giant",
        pt = "6/6",
        text = """
            $bargain
            This spell costs {2} less to cast if it's bargained.
            Trample
            When this creature enters, you gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        costsLessIfBargained("{2}"),
        Keyword.Trample,
        triggered(entersSelf, gainLife(3)),
    )

    // CR 601.2f
    @Test
    fun frostDenial() = card(
        name = "Frost Denial",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            $bargain
            This spell costs {1} less to cast if it's bargained.
            Counter target spell.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        costsLessIfBargained("{1}"),
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 601.2f
    @Test
    fun hollinsStopgap() = card(
        name = "Hollin's Stopgap",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            $bargain
            This spell costs {2} less to cast if it's bargained.
            Return target nonland permanent to its owner's hand. Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        costsLessIfBargained("{2}"),
        spell(move(target("permanent"), Zone.Hand), draw(1), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    // CR 702.166b, 608.2c: destroyed instead of dealt damage; CR 506.4 (attacking or blocking)
    @Test
    fun wendelsGlowblades() = card(
        name = "Wendel's Glowblades",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            $bargain
            Wendel's Glowblades deals 3 damage to target attacking or blocking creature. If this spell was bargained, destroy that creature instead.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(destroy(target("creature"))), otherwise = listOf(damage(3, toObjects(target("creature"))))),
            targets = listOf(slot("creature", and(or(attacking, blocking), creature))),
        ),
    )

    // CR 702.166b, 701.21: the cards not kept go to the graveyard, the kept ones back on top in an order of the player's choice (CR 401.4)
    @Test
    fun ardenasGrimProbe() = card(
        name = "Ardena's Grim Probe",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            $bargain
            If this spell was bargained, look at the top four cards of your library, then put up to two of them back on top of your library in any order and the rest into your graveyard.
            You draw two cards and you lose 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(
                lookAt(4),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(2), from = seen), bind = "kept", chooser = Players.You),
                move(Objects.Except(seen, Objects.Bound("kept")), Zone.Graveyard),
                doing(Effect.Reorder(Objects.Bound("kept"), chooser = Players.You)))),
            draw(2),
            loseLife(2),
        ),
    )

    // CR 702.166b, 107.3: twice X
    @Test
    fun rockcleaverBolt() = card(
        name = "Rockcleaver Bolt",
        cost = "{X}{R}",
        type = "Instant",
        text = """
            $bargain
            Rockcleaver Bolt deals X damage to target creature or planeswalker. If this spell was bargained, it deals twice X damage to that permanent instead.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(damage(Value.Times(x, 2), toObjects(target("permanent")))), otherwise = listOf(damage(x, toObjects(target("permanent"))))),
            targets = listOf(creatureOrPlaneswalker),
            announced = listOf("X"),
        ),
    )

    // CR 702.166b, 701.20 (reveal), 401.4
    @Test
    fun rumblingPremiere() = card(
        name = "Rumbling Premiere",
        cost = "{6}{G}{G}",
        type = "Sorcery",
        text = """
            $bargain
            Look at the top twenty cards of your library. You may reveal up to two creature cards from among them. If this spell was bargained, put the revealed cards onto the battlefield. Otherwise, put the revealed cards into your hand. Then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            lookAt(20),
            Step.Choose(Choice.Objects(creature, n(0), n(2), from = seen), bind = "revealed", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            Step.If(bargained, then = listOf(move(Objects.Bound("revealed"), Zone.Battlefield)), otherwise = listOf(move(Objects.Bound("revealed"), Zone.Hand))),
            shuffle,
        ),
    )

    // CR 702.166b; CR 614.1a: a replacement for the rest of the turn; CR 700.4: only a creature dies
    @Test
    fun burnTheBelfry() = card(
        name = "Burn the Belfry",
        cost = "{R}",
        type = "Instant",
        text = """
            $bargain
            Burn the Belfry deals 2 damage to target creature or planeswalker. If this spell was bargained, instead it deals 3 damage to that permanent and you scry 1.
            If a permanent dealt damage by Burn the Belfry would die this turn, exile it instead.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(damage(3, toObjects(target("permanent"))), scry(1)), otherwise = listOf(damage(2, toObjects(target("permanent"))))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(and(ObjectFilter.Is(target("permanent")), creature), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(creatureOrPlaneswalker),
        ),
    )

    // CR 702.166b, 603.4, 701.14a (fight; "up to one", none chosen: no fight)
    @Test
    fun morwensChampion() = card(
        name = "Morwen's Champion",
        cost = "{4}{G}",
        type = "Creature — Human Knight",
        pt = "4/4",
        text = """
            $bargain
            Trample
            When this creature enters, if it was bargained, it fights up to one target creature you don't control. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        Keyword.Trample,
        triggered(entersSelf, doing(Effect.Fight(Objects.Self, target("creature"))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0)), interveningIf = bargained),
    )

    // CR 702.166b
    @Test
    fun seraphsSplendor() = card(
        name = "Seraph's Splendor",
        cost = "{W}",
        type = "Instant",
        text = """
            $bargain
            Target creature gets +2/+2 until end of turn. If this spell was bargained, that creature also gains flying and lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            pump(target("creature"), 2, 2),
            Step.If(bargained, then = listOf(gains(target("creature"), Keyword.Flying, Keyword.Lifelink))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.166b
    @Test
    fun sweetmeatGrapple() = card(
        name = "Sweetmeat Grapple",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            $bargain
            Target creature gets -3/-3 until end of turn. If this spell was bargained, that creature gets -5/-5 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        spell(
            Step.If(bargained, then = listOf(pump(target("creature"), -5, -5)), otherwise = listOf(pump(target("creature"), -3, -3))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.166b, 603.4
    @Test
    fun faeCourtHaggler() = card(
        name = "Fae Court Haggler",
        cost = "{3}{B}{B}",
        type = "Creature — Faerie Warlock",
        pt = "3/5",
        text = """
            $bargain
            Flying
            When this creature enters, if it was bargained, each opponent loses 3 life and you gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        Keyword.Flying,
        triggered(entersSelf, loseLife(3, Players.Each(PlayerFilter.Opponent)), gainLife(3), interveningIf = bargained),
    )

    // CR 702.166b, 603.4, 106.1 (mana in any combination of colors)
    @Test
    fun landSearingWyrm() = card(
        name = "Land-Searing Wyrm",
        cost = "{4}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/6",
        text = """
            $bargain
            Flying, haste
            When this creature enters, if it was bargained, add four mana in any combination of colors.
            {1}{R}: This creature deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        Keyword.Flying,
        Keyword.Haste,
        triggered(entersSelf, doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(4), combination = true)), Players.You)), interveningIf = bargained),
        activated(manaCost("{1}{R}"), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 702.166b, 603.4
    @Test
    fun doggedBookhunter() = card(
        name = "Dogged Bookhunter",
        cost = "{2}{U}",
        type = "Creature — Human Knight",
        pt = "3/2",
        text = """
            $bargain
            When this creature enters, if it was bargained, return target instant or sorcery card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery)))), interveningIf = bargained),
    )

    // CR 702.166b, 603.4
    @Test
    fun mischiefOuphe() = card(
        name = "Mischief Ouphe",
        cost = "{1}{G}",
        type = "Creature — Ouphe",
        pt = "2/2",
        text = """
            $bargain
            When this creature enters, if it was bargained, exile target artifact or enchantment an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Bargain,
        triggered(entersSelf, move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(or(artifact, enchantment), opponents))),
            interveningIf = bargained),
    )
}
