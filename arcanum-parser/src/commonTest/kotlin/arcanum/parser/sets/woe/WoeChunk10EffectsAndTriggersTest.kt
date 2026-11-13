package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 10: copies of itself, a once-each-turn permission, prevention by a kind of source, sacrificing any number,
 * attack restrictions, block triggers, tap triggers and spell-damage triggers. */
class WoeChunk10EffectsAndTriggersTest {
    private val aura = sub("Aura")
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val instantOrSorcery = or(instant, sorcery)
    private val artifactEnchantmentOrToken = or(artifact, enchantment, token)

    // CR 603.4 (intervening if), 707.2 (copies of it), 111.1; CR 700.4 (dies), 201.2 (named: every creature with that name), 608.2h (its last power)
    @Test
    fun gruffTriplets() = card(
        name = "Surly Twins",
        cost = "{3}{G}{G}{G}",
        type = "Creature — Satyr Warrior",
        pt = "3/3",
        text = """
            Trample
            When this creature enters, if it isn't a token, create two tokens that are copies of it.
            When this creature dies, put a number of +1/+1 counters equal to its power on each creature you control named Surly Twins.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.EventObject(EventRole.After)), n(2), Players.You)),
            interveningIf = Condition.Not(Condition.ObjectMatches(Objects.EventObject(EventRole.After), token))),
        triggered(diesSelf, counters(Objects.Each(and(creature, you, ObjectFilter.Named("Surly Twins"))), power(Objects.EventObject(EventRole.Before)))),
    )

    // CR 601.3e (a permission to cast from the top of the library), 401.2; once each turn
    @Test
    fun johannApprenticeSorcerer() = card(
        name = "Orsolya, Tome Apprentice",
        cost = "{2}{U}{R}",
        type = "Legendary Creature — Human Wizard Sorcerer",
        pt = "2/5",
        text = """
            You may look at the top card of your library any time.
            Once each turn, you may cast an instant or sorcery spell from the top of your library. (You still pay its costs. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))), affects = Affected.Players(Players.You)),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, perTurn = 1, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(and(instantOrSorcery, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    )

    // CR 615.1 (prevention of damage from creatures to you and to creatures you control), 611.2c
    @Test
    fun eerieInterference() = card(
        name = "Uncanny Deflection",
        cost = "{2}{W}",
        type = "Instant",
        text = "Prevent all damage that would be dealt to you and creatures you control this turn by creatures.",
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(creature,
            RecipientFilter(objects = creatureYouControl, players = PlayerFilter.You)))), null, Duration.EndOfTurn))),
    )

    // CR 115.1 (a target card in any graveyard), 110.2a (under your control); CR 715 (Adventure)
    @Test
    fun virtueOfPersistence() = cardFaces(Layout.Adventure,
        card(name = "Creed of Tenacity", cost = "{5}{B}{B}", type = "Enchantment",
            text = "At the beginning of your upkeep, put target creature card from a graveyard onto the battlefield under your control.",
        ) to abilities(
            triggered(yourUpkeep, move(target("card"), Zone.Battlefield, controller = Players.You),
                targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard))))),
        ),
        card(name = "Bogmire Sneer", cost = "{1}{B}", type = "Sorcery — Adventure",
            text = "Target creature gets -3/-3 until end of turn. You gain 2 life.",
        ) to abilities(
            spell(pump(target("creature"), -3, -3), gainLife(2), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 715.2a (a card that has an Adventure), 305.6
    @Test
    fun edgewallInn() = card(
        name = "Hollowbrook Tavern",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            As this land enters, choose a color.
            {T}: Add one mana of the chosen color.
            {3}, {T}, Sacrifice this land: Return target card that has an Adventure from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        *edgewallLike().toTypedArray(),
        activated(cost(mana("{3}"), tap, sacrificeSelf), move(target("card"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(cardFilter, ObjectFilter.HasAdventure)))),
    )

    // CR 701.21a (sacrifice: any number of them, chosen as it resolves); "that many": the permanents sacrificed
    @Test
    fun lichKnightsConquest() = card(
        name = "Grave-Riders' Triumph",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Sacrifice any number of artifacts, enchantments, and/or tokens. Return that many creature cards from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(*sacrificeAnyNumber().toTypedArray(),
            Step.Choose(Choice.Objects(inYourGraveyard(creature), sacrificedCount, sacrificedCount), bind = "returned", chooser = Players.You),
            move(Objects.Bound("returned"), Zone.Battlefield)),
    )

    // CR 701.21a; "that many": the permanents sacrificed, not anything about the enters event
    @Test
    fun malevolentWitchkite() = card(
        name = "Spiteful Hexwing",
        cost = "{4}{B}{B}",
        type = "Creature — Dragon Warlock",
        pt = "5/4",
        text = """
            Flying
            When this creature enters, sacrifice any number of artifacts, enchantments, and/or tokens, then draw that many cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, *sacrificeAnyNumber().toTypedArray(), doing(Effect.Draw(Players.You, sacrificedCount))),
    )

    // CR 508.1c (an attack restriction: not you or your planeswalkers), 303.4 (enchanted by an Aura); CR 107.3a (X)
    @Test
    fun erietteOfTheCharmedApple() = card(
        name = "Marisol of the Gilded Pear",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "2/4",
        text = """
            Each creature that's enchanted by an Aura you control can't attack you or planeswalkers you control.
            At the beginning of your end step, each opponent loses X life and you gain X life, where X is the number of Auras you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantAttack(target = Recipients(objects = Objects.Each(and(planeswalker, you)), players = Players.You)),
            affects = Objects.Each(and(creature, ObjectFilter.Attached(attachments = and(aura, you))))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            loseLifeX(Players.Each(PlayerFilter.Opponent)), doing(Effect.GainLife(Players.You, count(and(aura, you))))),
    )

    // CR 509.1b (a blocking restriction), 611.2c (fixed as it resolves); CR 115.1 (up to two targets)
    @Test
    fun bellowingBruiser() = cardFaces(Layout.Adventure,
        card(name = "Roaring Lout", cost = "{4}{R}", type = "Creature — Ogre", pt = "4/4", text = "Haste") to abilities(Keyword.Haste),
        card(name = "Barge Through", cost = "{2}{R}", type = "Sorcery — Adventure",
            text = "Up to two target creatures can't block this turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(untilEndOfTurn(target("creatures"), Mechanic.CantBlock(ObjectFilter.Any)), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        ),
    )

    // CR 305.7-like land animation (611.2a), 613.4b (base power and toughness, layer 7b); "other target": not this land (CR 115.3)
    @Test
    fun restlessVinestalk() = card(
        name = "Wandering Thornvine",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G} or {U}.
            {3}{G}{U}: Until end of turn, this land becomes a 5/5 green and blue Plant creature with trample. It's still a land.
            Whenever this land attacks, up to one other target creature has base power and toughness 3/3 until end of turn.
        """,
    ).let { card ->
        val parsed = arcanum.parser.OracleParser.parse(card).abilities
        kotlin.test.assertEquals(
            triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(3), n(3))),
                targets = listOf(slot("creature", and(another, creature), min = 0))),
            arcanum.parser.cube.withoutText(parsed.last()),
        )
        kotlin.test.assertEquals(4, parsed.size)
        kotlin.test.assertTrue(parsed.none { it is Ability.Unparsed })
    }

    // CR 608.2h (who controlled each creature, as it last existed); for each such opponent, two tokens
    @Test
    fun faerieSlumberParty() = card(
        name = "Pixie Nap Gala",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = "Return all creatures to their owners' hands. For each opponent who controlled a creature returned this way, you create two 1/1 blue Faerie creature tokens with flying and \"This token can block only creatures with flying.\"",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(Objects.Each(creature), Destination(Zone.Hand), preBind = "returned")),
            create(faerie, Value.Times(Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
                Value.Count(Objects.Where(Objects.Bound("returned"), and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))))),
                Comparison.AtLeast, n(1))))))), 2), bind = "token"),
        ),
    )

    // CR 509.3a, 509.3d (once for each creature it blocks or that blocks it); that creature: the other combatant
    @Test
    fun skewerSlinger() = card(
        name = "Spit-Roast Hurler",
        cost = "{1}{R}",
        type = "Creature — Dwarf Knight",
        pt = "1/3",
        text = """
            Reach
            Whenever this creature blocks or becomes blocked by a creature, this creature deals 1 damage to that creature.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(Trigger.On(listOf(EventPattern.Block(ObjectFilter.Is(Objects.Self), creature, perAttacker = true),
            EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), creature, perBlocker = true))),
            damage(1, toObjects(Objects.EventObject(EventRole.OtherCombatant)))),
    )

    // CR 603.2 (the first such spell each turn); CR 401.4 (on top of the library)
    @Test
    fun aquaticAlchemist() = cardFaces(Layout.Adventure,
        card(name = "Tidepool Brewer", cost = "{1}{U}", type = "Creature — Elemental", pt = "1/3",
            text = "Whenever you cast your first instant or sorcery spell each turn, this creature gets +2/+0 until end of turn.",
        ) to abilities(
            triggered(Trigger.On(listOf(EventPattern.Cast(and(instantOrSorcery, spellFilter), PlayerFilter.You)), nthThisTurn = 1), pump(Objects.Self, 2, 0)),
        ),
        card(name = "Froth Upward", cost = "{2}{U}", type = "Sorcery — Adventure",
            text = "Put target instant or sorcery card from your graveyard on top of your library. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(move(target("card"), Zone.Library, position = 0),
                targets = listOf(slot("card", inYourGraveyard(instantOrSorcery)))),
        ),
    )

    // CR 105.2 (a spell of any of four colours), 601.2; CR 611.2a (until your next end step)
    @Test
    fun questingDruid() = cardFaces(Layout.Adventure,
        card(name = "Roving Druid", cost = "{1}{G}", type = "Creature — Human Druid", pt = "1/1",
            text = "Whenever you cast a spell that's white, blue, black, or red, put a +1/+1 counter on this creature.",
        ) to abilities(
            triggered(youCast(and(spellFilter, or(ObjectFilter.OfColor(Color.White), ObjectFilter.OfColor(Color.Blue), ObjectFilter.OfColor(Color.Black),
                ObjectFilter.OfColor(Color.Red)))), counters(Objects.Self)),
        ),
        card(name = "Hunt the Quarry", cost = "{1}{R}", type = "Instant — Adventure",
            text = "Exile the top two cards of your library. Until your next end step, you may play those cards.",
        ) to abilities(*exileTopTwoPlayUntilNextEndStep().toTypedArray()),
    )

    // CR 115.7 (a spell with a single target), 603.4 (the damage must be to that target), 120.3; "that much": the damage dealt
    @Test
    fun imodaneThePyrohammer() = card(
        name = "Bertrand, the Flamebrand",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Human Knight",
        pt = "4/4",
        text = "Whenever an instant or sorcery spell you control that targets only a single creature deals damage to that creature, Bertrand deals that much damage to each opponent.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(and(instantOrSorcery, spellFilter, you, ObjectFilter.SingleTarget(creature)), RecipientFilter(objects = creature))),
            Instructions(steps = listOf(damage(Value.EventAmount, eachOpponent))),
            triggersOnlyIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.TargetedBy(Objects.EventObject(EventRole.Source))),
        ),
    )

    // CR 603.12 (a reflexive trigger: only if a card was discarded); CR 508.1 (attacking with Faeries)
    @Test
    fun talionsMessenger() = card(
        name = "Courtly Wingbearer",
        cost = "{2}{U}",
        type = "Creature — Faerie Noble",
        pt = "1/3",
        text = """
            Flying
            Whenever you attack with one or more Faeries, draw a card, then discard a card. When you discard a card this way, put a +1/+1 counter on target Faerie you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Attack(and(sub("Faerie"), you)), oneOrMore = true),
            draw(1),
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, preBind = "discarded")),
            ifThen(Condition.NonEmpty("discarded"), reflexive(counters(target("Faerie")), targets = listOf(slot("Faerie", and(sub("Faerie"), you)))))),
    )

    // CR 701.26a (you tap it: an effect or cost of yours); CR 603.12 (paid on resolution: when you do)
    @Test
    fun icewroughtSentry() = card(
        name = "Frostforged Warden",
        cost = "{2}{U}",
        type = "Creature — Elemental Soldier",
        pt = "2/3",
        text = """
            Vigilance
            Whenever this creature attacks, you may pay {1}{U}. When you do, tap target creature an opponent controls.
            Whenever you tap an untapped creature an opponent controls, this creature gets +2/+1 until end of turn.
        """,
    ).let { card ->
        val parsed = arcanum.parser.OracleParser.parse(card).abilities.map { arcanum.parser.cube.withoutText(it) }
        kotlin.test.assertEquals(3, parsed.size)
        kotlin.test.assertTrue(parsed.none { it is Ability.Unparsed })
        kotlin.test.assertEquals(triggered(youTap(creatureOpponentControls), pump(Objects.Self, 2, 1)), parsed.last())
    }

    // CR 701.26a; CR 603.2c (one or more: once for the batch), 603.2h (only once each turn); CR 122.1c (stun counter)
    @Test
    fun sharaeOfNumbingDepths() = card(
        name = "Ondine of Stilling Tides",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Merfolk Wizard",
        pt = "2/3",
        text = """
            When Ondine enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Whenever you tap one or more untapped creatures your opponents control, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        tapAndStun(),
        triggered(youTap(creatureOpponentControls, oneOrMore = true), draw(1), perTurn = 1),
    )

    // CR 701.26a (you tap it); CR 122.1c
    @Test
    fun solitarySanctuary() = card(
        name = "Lonesome Refuge",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Whenever you tap an untapped creature an opponent controls, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        tapAndStun(),
        triggered(youTap(creatureOpponentControls), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    private val sacrificedCount = Value.Count(Objects.Bound("sacrificed"))

    private fun sacrificeAnyNumber(): List<Step> = listOf(
        Step.Choose(Choice.Objects(and(artifactEnchantmentOrToken, you), n(0), null), bind = "chosen", chooser = Players.You),
        doing(Effect.Sacrifice(Objects.Bound("chosen"), postBind = "sacrificed")),
    )

    private fun youTap(filter: ObjectFilter, oneOrMore: Boolean = false): Trigger = Trigger.On(EventPattern.Tap(filter, by = PlayerFilter.You), oneOrMore = oneOrMore)

    private val faerie = creatureToken("Faerie", listOf(Color.Blue), 1, 1, abilities = listOf(Keyword.Flying,
        static(Mechanic.CanBlockOnly(and(creature, kw(Keyword.Flying.kind))))))

    private fun loseLifeX(players: Players): Step = doing(Effect.LoseLife(players, count(and(aura, you))))

    private fun tapAndStun(): Ability = triggered(entersSelf, tapIt(target("creature")), counters(target("creature"), kind = CounterKind.Stun),
        targets = listOf(slot("creature", creatureOpponentControls)))

    private fun edgewallLike(): List<Ability> = listOf(
        arcanum.parser.sets.msh.entersTapped,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Remembered("color")))), Players.You))),
    )

    // the exiled cards may be played until your next end step begins (CR 611.2a)
    private fun exileTopTwoPlayUntilNextEndStep(): List<Ability> = listOf(spell(
        move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
        doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
            Affected.Objects(Objects.Bound("exiled")), Duration.UntilStep(TurnStep.End, PlayerFilter.You))),
    ))
}
