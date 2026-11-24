package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 7: Equipment (attach on an attack alone, a pump counting its own resolutions, keywords of the cards it exiled, tap on attach), mill and
 * scry: a dies trigger's modes, a comparison with the sacrificed permanent, a reflexive trigger about the card exiled, transforming Sagas. */
class NeoChunk07EquipMillScryTest {
    private val equipped = Objects.AttachedTo(Objects.Self)
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))
    private val sagaReminder = "(As this Saga enters and after your draw step, add a lore counter.)"
    private val chapterThree = "III — Exile this Saga, then return it to the battlefield transformed under your control."
    private fun front(name: String, cost: String, vararg chapters: String) =
        card(name = name, cost = cost, type = "Enchantment — Saga", text = (listOf(sagaReminder) + chapters + chapterThree).joinToString("\n"))
    private fun back(name: String, type: String, pt: String, color: String, vararg lines: String) =
        card(name = name, cost = null, type = type, text = lines.joinToString("\n"), pt = pt, colorIndicator = color)
    /** "you may pay {N}. When you do, …" (a cost paid on resolution, CR 118.12; the reflexive trigger, CR 603.12) */
    private fun mayPayWhenYouDo(mana: String, vararg steps: Step, targets: List<TargetSlot> = emptyList()) = arrayOf(
        Step.Pay(manaCost(mana), bind = "paid", payer = Players.You), ifThen(Condition.Did("paid"), reflexive(*steps, targets = targets)))
    private val spirit = creatureToken("Spirit", emptyList(), 1, 1)
    private val creatureCardInAGraveyard = and(creature, inZone(Zone.Graveyard))

    // CR 506.5 (attacks alone), 603.12 (when you do), 701.3a (attach to it: the creature that attacked), 702.6a
    @Test
    fun forebearsWakizashi() = card(
        name = "Forebear's Wakizashi",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            Whenever a Samurai or Warrior you control attacks alone, you may pay {1}. When you do, attach this Equipment to it.
            Equipped creature gets +2/+1.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(and(or(sub("Samurai"), sub("Warrior")), you), alone = true)),
            *mayPayWhenYouDo("{1}", doing(Effect.Attach(Objects.Self, Recipients(objects = Objects.EventObject(EventRole.Object)))))),
        static(pt(2, 1), affects = equipped),
        equip("{3}"),
    )

    // CR 608.2h: X is the number of times the ability has resolved this turn, this resolution counted, fixed as it resolves; CR 611.2c
    @Test
    fun brassKnuckleClubs() = card(
        name = "Brass Knuckle Clubs",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            {2}: Until end of turn, equipped creature gets +X/+0, where X is the number of times this ability has resolved this turn.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        activated(manaCost("{2}"), Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.AbilityUsesThisTurn(resolved = true), n(0))),
            Affected.Objects(equipped), Duration.EndOfTurn))),
        equip("{1}"),
    )

    // CR 607.2a (cards exiled with it), 603.10a (the creature that died), 702.16a (each protection with its quality), 702.11e (hexproof from)
    @Test
    fun devourerOfValor() = card(
        name = "Devourer of Valor",
        cost = "{1}",
        type = "Legendary Artifact — Equipment",
        text = """
            Whenever equipped creature dies, exile it.
            Equipped creature gets +2/+0.
            As long as a card exiled with Devourer of Valor has flying, equipped creature has flying. The same is true for first strike, double strike, deathtouch, haste, hexproof, indestructible, lifelink, menace, protection, reach, trample, and vigilance.
            Equip {1}
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(equipped), from = Zone.Battlefield, to = Zone.Graveyard)),
            move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled")))),
        static(pt(2, 0), affects = equipped),
        static(Mechanic.GainAbilitiesOf(Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile)), onlyKeywords = listOf(
            Keyword.Flying, Keyword.FirstStrike, Keyword.DoubleStrike, Keyword.Deathtouch, Keyword.Haste, Keyword.Hexproof, Keyword.Indestructible,
            Keyword.Lifelink, Keyword.Menace, Keyword.Protection.Kind, Keyword.Reach, Keyword.Trample, Keyword.Vigilance)), affects = equipped),
        equip("{1}"),
    )

    // CR 701.3a: "that creature" is the creature it became attached to; CR 701.26a (tap)
    @Test
    fun colossalPlasmaEdge() = card(
        name = "Colossal Plasma Edge",
        cost = "{2}{B}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +4/+0.
            Whenever this Equipment becomes attached to a creature, tap that creature.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(4, 0), affects = equipped),
        triggered(on(EventPattern.Attach(ObjectFilter.Is(Objects.Self), to = creature)), tapIt(Objects.EventObject(EventRole.To))),
        equip("{2}"),
    )

    // CR 602.2 (granted ability, its source the Equipment), 702.6a
    @Test
    fun shinobisThrowingBlade() = card(
        name = "Shinobi's Throwing Blade",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has "{1}, {T}, Sacrifice Shinobi's Throwing Blade: Shinobi's Throwing Blade deals 3 damage to any target."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(activated(cost(mana("{1}"), tap, CostPart.Sacrifice(CostObjects.These(Objects.Granter))),
            damage(3, toAny, source = Objects.Granter), targets = listOf(anyTarget))), affects = equipped),
        equip("{1}"),
    )

    // CR 700.2 (modes), 601.2c and 115.6 (any number of targets with a total mana value limit), 701.17a (mill)
    @Test
    fun soraiTheWhirlingHeavens() = card(
        name = "Sorai, the Whirling Heavens",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Dragon Spirit",
        pt = "6/6",
        text = """
            Flying, ward {3}
            When Sorai dies, choose one —
            • Return any number of target nonland permanents with total mana value 6 or less to their owners' hands.
            • Mill six cards, then return up to two instant and/or sorcery cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(manaCost("{3}")),
        Ability.Triggered(diesSelf, Instructions(modes = modes(
            mode(move(target("permanents"), Zone.Hand), targets = listOf(slot("permanents", and(nonland, permanentFilter), min = 0, max = null)
                .copy(totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(6))))),
            mode(mill(6),
                Step.Choose(Choice.Objects(inYourGraveyard(or(instant, sorcery)), n(0), n(2)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand)),
        ))),
    )

    // CR 601.2h (the sacrificed permanent), 608.2h (both mana values as the spell resolves; the sacrificed one as it last existed), 701.22a
    @Test
    fun tetherToThePresent() = card(
        name = "Tether to the Present",
        cost = "{2}{U}{U}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Search your library for an Equipment or Vehicle card, put that card onto the battlefield, then shuffle. If it has mana value less than the sacrificed permanent's mana value, scry 2.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature), preBind = "sacrificed")))),
        spell(search(or(equipment, sub("Vehicle"))), move(Objects.Bound("found"), Zone.Battlefield, postBind = "put found"), shuffle,
            // CR 400.7: "it" is the permanent the card became
            ifThen(Condition.Compare(Value.Of(NumericProperty.ManaValue, Objects.Bound("put found"), Combine.Single), Comparison.Less,
                Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single)), scry(2))),
    )

    // CR 603.12: the reflexive trigger only for a nonland card; "the exiled card" is the card exiled (CR 608.2c); CR 305.1, 601 (play it)
    @Test
    fun masatsuguRavenousDiscord() = card(
        name = "Masatsugu, Ravenous Discord",
        cost = "{3}{B}",
        type = "Legendary Creature — Ogre Demon",
        pt = "4/4",
        text = """
            {B}, Sacrifice a creature: Scry 2.
            {2}{R}, {T}: Exile the top card of your library. You may play that card this turn. When you exile a nonland card this way, Masatsugu deals damage equal to the exiled card's mana value to any target.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{B}"), sacrificeA(creature)), scry(2)),
        activated(cost(mana("{2}{R}"), tap),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.EndOfTurn)),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), nonland),
                reflexive(damage(Value.Of(NumericProperty.ManaValue, Objects.Bound("exiled"), Combine.Single), toAny), targets = listOf(anyTarget)))),
    )

    // CR 603.12 (when you do), 701.17a (target player mills), 205.3 (Shrines you control, counted as it resolves)
    @Test
    fun goShintaiOfFadedMemory() = card(
        name = "Go-Shintai of Faded Memory",
        cost = "{1}{U}",
        type = "Legendary Enchantment Creature — Shrine",
        pt = "0/4",
        text = """
            Flying
            At the beginning of your end step, you may pay {1}. When you do, target player mills X cards, where X is the number of Shrines you control. (To mill a card, a player puts the top card of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(yourEndStep, *mayPayWhenYouDo("{1}", Step.Do(Effect.Mill(Players.Target("player"), count(and(sub("Shrine"), you)))), targets = listOf(targetPlayer))),
    )

    // CR 701.17a; CR 401.2 (the bottom of its owner's library)
    @Test
    fun kaitoWarden() = card(
        name = "Kaito Warden",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        pt = "3/3",
        text = """
            Defender
            When this creature enters, target player mills three cards. (They put the top three cards of their library into their graveyard.)
            {3}: Put target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(entersSelf, mill(3, Players.Target("player")), targets = listOf(targetPlayer)),
        activated(manaCost("{3}"), move(target("card"), Zone.Library, position = -1), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 714.2b, 714.3b, 712.10; back face: CR 700.2, 603.12 (each mode's reflexive trigger, its own target)
    @Test
    fun lessonsOfTheQilin() = cardFaces(Layout.Transform,
        front("Lessons of the Qilin", "{1}{G}",
            "I — Mill three cards. Create a 1/1 colorless Spirit creature token.",
            "II — Put a +1/+1 counter on target creature you control.",
        ) to abilities(
            chapter(1, mill(3), create(spirit)),
            chapter(2, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
            exileAndReturnTransformed,
        ),
        back("Qilin-Marked Serpent", "Enchantment Creature — Snake Monk", "1/1", "G",
            "Whenever this creature attacks, choose one —",
            "• Exile target creature card from a graveyard. When you do, create a 1/1 colorless Spirit creature token.",
            "• Exile target noncreature card from a graveyard. When you do, put a +1/+1 counter on target creature you control.",
        ) to abilities(
            Ability.Triggered(attacksSelf, Instructions(modes = modes(
                mode(move(target("card"), Zone.Exile, postBind = "exiled"), reflexive(create(spirit)),
                    targets = listOf(slot("card", creatureCardInAGraveyard))),
                mode(move(target("card"), Zone.Exile, postBind = "exiled"),
                    reflexive(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
                    targets = listOf(slot("card", and(not(creature), inZone(Zone.Graveyard))))),
            ))),
        ),
    )

    // CR 611.2c (until your next turn, the creatures fixed as it resolves); CR 608.2c ("Otherwise"); back face: CR 613.4c, 402.1
    @Test
    fun witnessTheUnnameable() = cardFaces(Layout.Transform,
        front("Witness the Unnameable", "{3}{U}{U}",
            "I — Creatures you don't control get -2/-0 until your next turn.",
            "II — If you have one or fewer cards in hand, draw four cards. Otherwise, scry 2, then draw two cards.",
        ) to abilities(
            chapter(1, Step.Do(Effect.ApplyMechanics(listOf(pt(-2, 0)), Affected.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
                Duration.UntilYourNextTurn))),
            chapter(2, ifThen(Condition.Not(Condition.Exists(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 2)), draw(4),
                otherwise = listOf(scry(2), draw(2)))),
            exileAndReturnTransformed,
        ),
        back("Vision of the Unnameable", "Enchantment Creature — Spirit", "0/0", "U",
            "Flying, trample",
            "This creature gets +1/+1 for each card in your hand.",
        ) to abilities(
            Keyword.Flying,
            Keyword.Trample,
            static(Mechanic.ModifyPowerToughness(Value.HandSize(Players.You), Value.HandSize(Players.You))),
        ),
    )

    // CR 714.2b, 701.22a (scry), 119.3 (gain life)
    @Test
    fun ageOfAwakening() = cardFaces(Layout.Transform,
        front("Age of Awakening", "{1}{W}",
            "I — Scry 2.",
            "II — You gain 2 life.",
        ) to abilities(
            chapter(1, scry(2)),
            chapter(2, gainLife(2)),
            exileAndReturnTransformed,
        ),
        back("Hand of Awakening", "Enchantment Creature — Human Monk", "2/2", "W",
            "First strike",
        ) to abilities(
            Keyword.FirstStrike,
        ),
    )

    // CR 111.1 (tokens), 701.22a (scry after)
    @Test
    fun imperialVow() = card(
        name = "Imperial Vow",
        cost = "{5}{W}",
        type = "Sorcery",
        text = "Create three 2/2 white Samurai creature tokens with vigilance. Scry 3.",
    ) parsesTo abilities(
        spell(create(creatureToken("Samurai", listOf(Color.White), 2, 2, abilities = listOf(Keyword.Vigilance)), n(3)), scry(3)),
    )

    // CR 701.26a: "becomes tapped" for any reason (attacking, a cost, an effect)
    @Test
    fun moonfolkRiddlewright() = card(
        name = "Moonfolk Riddlewright",
        cost = "{2}{U}",
        type = "Artifact Creature — Moonfolk Wizard",
        pt = "1/4",
        text = """
            Flying
            Whenever this creature becomes tapped, scry 1.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), scry(1)),
    )
}
