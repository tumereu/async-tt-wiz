package arcanum.parser.sets.fdn

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** The FDN life-loss, must-attack, hand-size, poison, reflexive, search, set-P/T and untap cards (chunk 27). The invented names carry the oracle text of the real cards. */
class FdnChunk27LifeAttackSearchSetPtTest {
    private val vampire = sub("Vampire")
    private val opponentLoses1 = loseLife(1, Players.Each(PlayerFilter.Opponent))
    private val basic = basicLand()
    private val toBattlefieldTapped = Destination(Zone.Battlefield, tapped = true)
    private val toHand = Destination(Zone.Hand)

    // CR 119.3, 119.4: each opponent loses X life (X announced, CR 107.3), you gain the life actually lost
    @Test
    fun drainingTithe() = card(
        name = "Draining Tithe", cost = "{X}{B}{B}", type = "Sorcery",
        text = "Each opponent loses X life. You gain life equal to the life lost this way.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Announced("X"), bind = "lost")),
            Step.Do(Effect.GainLife(Players.You, Value.Bound("lost"))),
            announced = listOf("X"),
        ),
    )

    // CR 603.12, 118.12: "you may pay {B}. If you do, ..." both the loss and the gain happen only when the payment was made (Audit: the gain was outside the If)
    @Test
    fun crimsonHeir() = card(
        name = "Crimson Heir", cost = "{B}{B}", type = "Creature — Vampire Shaman", pt = "2/2",
        text = "Whenever this creature or another Vampire you control dies, you may pay {B}. If you do, target player loses 2 life and you gain 2 life.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, vampire, you)), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Pay(cost(mana("{B}")), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(loseLife(2, Players.Target("player")), gainLife(2))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 508.1m, 119.3
    @Test
    fun veinTracker() = card(
        name = "Vein Tracker", cost = "{B}", type = "Creature — Vampire Rogue", pt = "1/1",
        text = "Whenever this creature attacks, each opponent loses 1 life.",
    ) parsesTo abilities(triggered(attacksSelf, opponentLoses1))

    // CR 508.1m, 119.3, 119.1
    @Test
    fun bloodSyphoner() = card(
        name = "Blood Syphoner", cost = "{1}{B}", type = "Creature — Vampire Warlock", pt = "1/3",
        text = "Whenever this creature attacks, each opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(triggered(attacksSelf, opponentLoses1, gainLife(1)))

    // CR 602.1, 119.3
    @Test
    fun vampireInfant() = card(
        name = "Vampire Infant", cost = "{B}", type = "Creature — Vampire", pt = "0/3",
        text = "{2}, {T}: Each opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(activated(cost(mana("{2}"), tap), opponentLoses1, gainLife(1)))

    // CR 700.4, 603.10a: this creature or another creature you control
    @Test
    fun vengefulLeech() = card(
        name = "Vengeful Leech", cost = "{1}{B}", type = "Creature — Vampire Warlock", pt = "1/1",
        text = "Whenever this creature or another creature you control dies, target opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, creature, you)), from = Zone.Battlefield, to = Zone.Graveyard)),
            loseLife(1, Players.Target("opponent")), gainLife(1),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 508.1d (must attack), 702.2, 702.10; Ferocious (CR 207.2c) is an ability word; "you may pay {R}" is optional (Audit: the pay is mandatory = false)
    @Test
    fun emberPhoenix() = card(
        name = "Ember Phoenix", cost = "{1}{R}{R}", type = "Creature — Phoenix", pt = "2/2", text = """
            Flying, haste
            This creature attacks each combat if able.
            Ferocious — At the beginning of combat on your turn, if you control a creature with power 4 or greater, you may pay {R}. If you do, return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        static(Mechanic.MustAttack()),
        triggered(
            beginningOfCombatOnYourTurn,
            Step.Pay(cost(mana("{R}")), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(move(Objects.Self, Zone.Battlefield, postBind = "returned"))),
            interveningIf = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1),
            abilityWord = "Ferocious",
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    // CR 508.1d, 509.1b
    @Test
    fun ironRam() = card(
        name = "Iron Ram", cost = "{4}", type = "Artifact Creature — Juggernaut", pt = "5/3", text = """
            This creature attacks each combat if able.
            This creature can't be blocked by Walls.
        """,
    ) parsesTo abilities(
        static(Mechanic.MustAttack()),
        static(Mechanic.CantBeBlockedBy(sub("Wall"))),
    )

    // CR 402.2: a static ability that applies to you; CR 120.2b noncombat damage, 120.3 "that many"
    @Test
    fun dragonSage() = card(
        name = "Dragon Sage", cost = "{4}{U}{R}", type = "Legendary Creature — Dragon Wizard", pt = "5/5", text = """
            Flying
            You have no maximum hand size.
            Whenever a source you control deals noncombat damage to an opponent, you draw that many cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        playerStatic(Mechanic.NoMaximumHandSize),
        triggered(
            on(EventPattern.Damage(you, RecipientFilter(players = PlayerFilter.Opponent), combat = false)),
            Step.Do(Effect.Draw(Players.You, Value.EventAmount)),
        ),
    )

    // CR 122.1f, 704.5c, 120.3: the damaged player gets two poison counters
    @Test
    fun venomWarrior() = card(
        name = "Venom Warrior", cost = "{1}{G}", type = "Legendary Creature — Human Warrior", pt = "1/3", text = """
            Deathtouch
            Whenever a creature you control with deathtouch deals combat damage to a player, that player gets two poison counters.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            on(EventPattern.Damage(and(creature, you, kw(Keyword.Deathtouch.kind)), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            Step.Do(Effect.GivePlayerCounters(Players.EventPlayer, CounterKind.Poison, n(2))),
        ),
    )

    // CR 603.12 (reflexive trigger), 107.3 (X chosen as paid) (Audit: X is chosen with a Number choice, any amount from 0 up)
    @Test
    fun woodlandKeeper() = card(
        name = "Woodland Keeper", cost = "{1}{G}", type = "Creature — Elf Archer", pt = "2/2", text = """
            Flash
            Reach
            Whenever another non-Human creature you control enters, you may pay {X}. When you do, put X +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Reach,
        triggered(
            enters(and(another, not(sub("Human")), creature, you)),
            Step.Choose(Choice.Number(n(0)), bind = "X", chooser = Players.You),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X")))), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(reflexive(counters(Objects.Self, Value.Bound("X"))))),
        ),
    )

    // CR 701.23a: search for up to two basic lands, put them onto the battlefield tapped, shuffle
    @Test
    fun copperDeer() = card(
        name = "Copper Deer", cost = "{3}", type = "Artifact Creature — Elk", pt = "2/2",
        text = "{3}, Sacrifice this creature: Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        activated(cost(mana("{3}"), sacrificeSelf), search(basic, max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 701.23a: "and/or" is one filter, basic lands or Gates
    @Test
    fun wanderingPath() = card(
        name = "Wandering Path", cost = "{3}{G}", type = "Sorcery",
        text = "Search your library for up to two basic land cards and/or Gate cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(search(or(basic, sub("Gate")), max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 701.23a, 701.20a (reveal)
    @Test
    fun surveyorsMap() = card(
        name = "Surveyor's Map", cost = "{1}", type = "Artifact",
        text = "{2}, {T}, Sacrifice this artifact: Search your library for a land card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, sacrificeSelf), search(land, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 701.23a, 608.2c: "you may" covers the search, the put and the shuffle
    @Test
    fun giantSeeker() = card(
        name = "Giant Seeker", cost = "{2}{G}", type = "Creature — Elf", pt = "1/1",
        text = "When this creature enters, you may search your library for a creature card with mana value 6 or greater, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        whenThisEnters(Step.May(listOf(
            search(and(creature, manaValue(Comparison.AtLeast, 6)), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle,
        ), player = Players.You)),
    )

    // CR 202.3: mana value 1; "instant or sorcery" is one filter
    @Test
    fun cheapSpellFinder() = card(
        name = "Cheap Spell Finder", cost = "{3}{U}", type = "Creature — Human Wizard", pt = "3/3",
        text = "When this creature enters, you may search your library for an instant or sorcery card with mana value 1, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        whenThisEnters(Step.May(listOf(
            search(and(or(instant, sorcery), manaValue(Comparison.Equal, 1)), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle,
        ), player = Players.You)),
    )

    // CR 701.23a: any card, not optional, not revealed
    @Test
    fun hellishTutor() = card(
        name = "Hellish Tutor", cost = "{5}{B}{B}", type = "Creature — Demon", pt = "6/6", text = """
            Flying
            When this creature enters, search your library for a card, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 701.23a, 614.1 (put tapped); CR 700.4 dies, 603.10a: the second trigger is a separate "may"
    @Test
    fun brassGolem() = card(
        name = "Pewter Golem", cost = "{4}", type = "Artifact Creature — Golem", pt = "2/2", text = """
            When this creature enters, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
            When this creature dies, you may draw a card.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.May(listOf(search(basic), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle), player = Players.You)),
        triggered(diesSelf, Step.May(listOf(draw(1)), player = Players.You)),
    )

    // CR 701.21a (sacrifice), 118.12, 701.23a: the sacrifice is optional (Audit: Pay mandatory = false); the search happens only if it was paid
    @Test
    fun mossbackDruid() = card(
        name = "Mossback Druid", cost = "{2}{G}", type = "Creature — Elf Druid", pt = "1/1",
        text = "When this creature enters, you may sacrifice a land. If you do, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Pay(cost(sacrificeA(land)), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(search(basic, max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle)),
        ),
    )

    private val opponentsGraveyardCards = count(and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)))

    // CR 604.3, 208.2a: a characteristic-defining ability sets both; CR 701.20 reveal until, 404.1 graveyard
    @Test
    fun hungryHorror() = card(
        name = "Hungry Horror", cost = "{3}{U}{B}", type = "Creature — Horror", pt = "*/*", text = """
            Hungry Horror's power and toughness are each equal to the number of cards in your opponents' graveyards.
            Whenever you cast a spell, each opponent reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(opponentsGraveyardCards, opponentsGraveyardCards), characteristicDefining = true),
        triggered(
            youCast(ObjectFilter.Any),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                doing(Effect.RevealUntil(land, Players.Bound("opponent"), n(1), reveal = true, foundBind = "found", restBind = "rest")),
                doing(Effect.Move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Destination(Zone.Graveyard))),
            ), together = true),
        ),
    )

    // CR 604.3, 208.2a
    @Test
    fun legionOfLight() = card(
        name = "Legion of Light", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "*/*",
        text = "Legion of Light's power and toughness are each equal to the number of creatures you control.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(creatureYouControl), count(creatureYouControl)), characteristicDefining = true),
    )

    // CR 604.3, 208.2a: only the power is defined (the printed toughness is 4)
    @Test
    fun stormDrake() = card(
        name = "Storm Drake", cost = "{1}{U}{R}", type = "Creature — Drake", pt = "*/4", text = """
            Flying
            Storm Drake's power is equal to the number of instant and sorcery cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(count(inYourGraveyard(or(instant, sorcery))), null), characteristicDefining = true),
    )

    // CR 508.1, 603.2c (first time each turn), 502.3 untap (not a phase), 500.8 additional combat phase, 702.20 vigilance
    @Test
    fun auroraWarleader() = card(
        name = "Aurora, the Warleader", cost = "{2}{R}{R}{W}{W}", type = "Legendary Creature — Angel", pt = "3/4", text = """
            Flying, vigilance, haste
            Whenever Aurora attacks for the first time each turn, untap all creatures you control. After this phase, there is an additional combat phase.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Haste,
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), nthThisTurn = 1),
            Instructions(steps = listOf(untapIt(Objects.Each(creatureYouControl)), doing(Effect.ExtraCombat()))),
        ),
    )
}
