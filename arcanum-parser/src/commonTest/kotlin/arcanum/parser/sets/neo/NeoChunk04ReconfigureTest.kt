package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.abilities
import kotlin.test.Test

/** Chunk 4: reconfigure (CR 702.151) and the "this creature or equipped creature" triggers of its Equipment. */
class NeoChunk04ReconfigureTest {
    private fun reconfigureLine(cost: String) =
        "Reconfigure $cost ($cost: Attach to target creature you control; or unattach from a creature. Reconfigure only as a sorcery. While attached, this isn't a creature.)"
    private fun reconfigure(cost: String) = Keyword.Reconfigure(manaCost(cost))
    private fun equipment(name: String, cost: String, type: String, pt: String, reconfigureCost: String, vararg lines: String) =
        card(name = name, cost = cost, type = "Artifact Creature — Equipment $type", pt = pt,
            text = (lines.toList() + reconfigureLine(reconfigureCost)).joinToString("\n"))
    /** "this creature or equipped creature": the Equipment as a creature, or the creature it is attached to */
    private val selfOrEquipped = or(ObjectFilter.Is(Objects.Self), ObjectFilter.Is(enchanted))
    /** The creature the trigger saw: this one or the equipped one (CR 603.2) */
    private val thatCreature = Objects.EventObject(EventRole.Object)
    /** The creature that dealt the combat damage: this one or the equipped one (CR 603.2) */
    private val damageSource = Objects.EventObject(EventRole.Source)
    private fun equipped(vararg mechanics: Mechanic) = static(*mechanics, affects = enchanted)
    private val attachedToACreature = Condition.ObjectMatches(enchanted, creature)
    private val yours = ObjectFilter.OwnedBy(PlayerFilter.You)

    // CR 510.3a, 603.2; CR 702.151a-b
    @Test
    fun filchingCircuitOctopus() = equipment("Filching Circuit Octopus", "{2}{U}", "Octopus", "2/2", "{2}",
        "Whenever this creature or equipped creature deals combat damage to a player, draw a card.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(selfOrEquipped, RecipientFilter(players = PlayerFilter.Any), combat = true)), draw(1)),
        reconfigure("{2}"),
    )

    // CR 613.4b (base power and toughness, layer 7b), 613.1d-e (a black Demon in addition, layers 4 and 5), 613.1f (menace)
    @Test
    fun maskOfTheAkuma() = equipment("Mask of the Akuma", "{1}{B}", "Demon", "3/1", "{2}{B}{B}",
        "Menace",
        "Equipped creature has base power and toughness 5/5, has menace, and is a black Demon in addition to its other colors and types.",
    ) parsesTo abilities(
        Keyword.Menace,
        equipped(
            Mechanic.SetPowerToughness(n(5), n(5)),
            has(Keyword.Menace),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Demon")))),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black)), add = true),
        ),
        reconfigure("{2}{B}{B}"),
    )

    // CR 508.1m, 603.2: "it" is the creature that attacked (the equipped one while attached)
    @Test
    fun whipchainCentipede() = equipment("Whipchain Centipede", "{2}{B}", "Insect", "2/2", "{2}",
        "Whenever this creature or equipped creature attacks, it gets +2/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(selfOrEquipped)), pump(thatCreature, 2, 0)),
        reconfigure("{2}"),
    )

    // CR 701.21a, 603.2: "it" is the creature that dealt the damage; "If you do" after a sacrifice (CR 608.2c)
    @Test
    fun trollVisageHelm() = equipment("Troll-Visage Helm", "{1}{R}", "Ogre", "2/2", "{3}",
        "Equipped creature gets +2/+2.",
        "Whenever this creature or equipped creature deals combat damage to a player, you may sacrifice it. If you do, discard your hand, then draw three cards.",
    ) parsesTo abilities(
        equipped(pt(2, 2)),
        triggered(on(EventPattern.Damage(selfOrEquipped, RecipientFilter(players = PlayerFilter.Any), combat = true)),
            Step.Pay(steps = listOf(doing(Effect.Sacrifice(damageSource, by = Players.You))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
                draw(3))),
        reconfigure("{3}"),
    )

    // CR 509.1h, 508.5: "it" is the blocked creature and the defending player the one it attacked
    @Test
    fun primateSlingshot() = equipment("Primate Slingshot", "{R}", "Monkey", "1/1", "{2}",
        "Equipped creature gets +1/+1.",
        "Whenever this creature or equipped creature becomes blocked, it deals 1 damage to defending player.",
    ) parsesTo abilities(
        equipped(pt(1, 1)),
        triggered(on(EventPattern.BecomesBlocked(selfOrEquipped, ObjectFilter.Any)),
            damage(1, Recipients(players = Players.DefendingPlayerOf(thatCreature)), source = thatCreature)),
        reconfigure("{2}"),
    )

    // CR 702.151b (attached to a creature), 601.2a, 305.1 (lands and spells from the top of the library), 401.5
    @Test
    fun theDreamShard() = card(
        name = "The Dream Shard",
        cost = "{1}{U}",
        type = "Legendary Artifact Creature — Equipment Jellyfish",
        pt = "0/4",
        text = """
            You may look at the top card of your library any time.
            As long as The Dream Shard is attached to a creature, you may play lands and cast spells from the top of your library.
            ${reconfigureLine("{2}{U}")}
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(land, inZone(Zone.Library), yours)), condition = attachedToACreature),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(nonland, inZone(Zone.Library), yours)), condition = attachedToACreature),
        reconfigure("{2}{U}"),
    )

    // CR 702.21a (ward granted by the Equipment), 613.4c
    @Test
    fun bracerFamiliar() = equipment("Bracer Familiar", "{1}{U}", "Beast", "2/1", "{4}",
        "Ward {2} (Whenever this permanent becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)",
        "Equipped creature gets +2/+1 and has ward {2}.",
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        equipped(pt(2, 1), has(Keyword.Ward(manaCost("{2}")))),
        reconfigure("{4}"),
    )

    // CR 702.19 (trample), 613.4c
    @Test
    fun copperplateSwine() = equipment("Copperplate Swine", "{2}{R}", "Boar", "3/2", "{5}",
        "Trample",
        "Equipped creature gets +3/+2 and has trample.",
    ) parsesTo abilities(
        Keyword.Trample,
        equipped(pt(3, 2), has(Keyword.Trample)),
        reconfigure("{5}"),
    )

    // CR 104.3b (the granted ability: its controller can't lose), 613.1f
    @Test
    fun skysilverKirin() = equipment("Skysilver Kirin", "{2}{W}", "Kirin", "3/2", "{5}",
        "Flying",
        "Equipped creature has flying and \"You can't lose the game and your opponents can't win the game.\"",
    ) parsesTo abilities(
        Keyword.Flying,
        equipped(
            has(Keyword.Flying),
            has(
                playerStatic(Mechanic.CantLoseGame),
                playerStatic(Mechanic.CantWinGame, players = Players.Each(PlayerFilter.Opponent)),
            ),
        ),
        reconfigure("{5}"),
    )

    // CR 702.15 (lifelink)
    @Test
    fun tickGauntlet() = equipment("Tick Gauntlet", "{1}{B}", "Leech", "2/2", "{4}",
        "Lifelink",
        "Equipped creature has lifelink.",
    ) parsesTo abilities(
        Keyword.Lifelink,
        equipped(has(Keyword.Lifelink)),
        reconfigure("{4}"),
    )

    // CR 702.4 (double strike)
    @Test
    fun geckoBlades() = equipment("Gecko Blades", "{1}{R}", "Lizard", "1/1", "{2}",
        "Double strike",
        "Equipped creature has double strike.",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        equipped(has(Keyword.DoubleStrike)),
        reconfigure("{2}"),
    )

    // CR 702.10 (haste)
    @Test
    fun hareCapacitor() = equipment("Hare Capacitor", "{R}", "Rabbit", "1/1", "{R}",
        "Haste",
        "Equipped creature gets +1/+1 and has haste.",
    ) parsesTo abilities(
        Keyword.Haste,
        equipped(pt(1, 1), has(Keyword.Haste)),
        reconfigure("{R}"),
    )

    // CR 702.17 (reach)
    @Test
    fun silkweaverCuff() = equipment("Silkweaver Cuff", "{2}{G}", "Spider", "1/4", "{4}",
        "Reach",
        "Equipped creature gets +1/+4 and has reach.",
    ) parsesTo abilities(
        Keyword.Reach,
        equipped(pt(1, 4), has(Keyword.Reach)),
        reconfigure("{4}"),
    )

    // CR 110.4a (a permanent card), 122.1; the Equipment's own counters count while it is attached
    @Test
    fun tigerSash() = equipment("Tiger Sash", "{1}{W}", "Cat", "1/1", "{2}",
        "{W}: Exile target card from a graveyard. If it was a permanent card, put a +1/+1 counter on this permanent.",
        "Equipped creature gets +1/+1 for each +1/+1 counter on this Equipment.",
    ) parsesTo abilities(
        activated(manaCost("{W}"),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), permanentCard), counters(Objects.Self)),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single).let { counters ->
            equipped(Mechanic.ModifyPowerToughness(counters, counters))
        },
        reconfigure("{2}"),
    )
}
