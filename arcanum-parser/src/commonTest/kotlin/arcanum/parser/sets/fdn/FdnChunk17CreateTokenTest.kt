package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 17 of FDN: create-token wordings. The invented names carry the oracle text of the real cards. */
class FdnChunk17CreateTokenTest {
    private val upkeep = stepBegins(TurnStep.Upkeep, PlayerFilter.You)
    private val dragon = creatureToken("Dragon", listOf(Color.Red), 5, 5, abilities = listOf(Keyword.Flying))
    private val elfWarrior = creatureToken("Elf Warrior", listOf(Color.Green), 1, 1)
    private val faerie = creatureToken("Faerie", listOf(Color.Blue), 1, 1, abilities = listOf(Keyword.Flying))
    private val knight = creatureToken("Knight", listOf(Color.White), 3, 3)
    private val insect = creatureToken("Insect", listOf(Color.Black, Color.Green), 1, 1, abilities = listOf(Keyword.Flying))
    private val goblin = creatureToken("Goblin", listOf(Color.Red), 1, 1)
    private val zombie = creatureToken("Zombie", listOf(Color.Black), 2, 2)
    private val cat = creatureToken("Cat", listOf(Color.White), 1, 1)
    private val dog = creatureToken("Dog", listOf(Color.White), 1, 1)
    private val rat = creatureToken("Rat", listOf(Color.Black), 1, 1, abilities = listOf(Ability.Static(
        listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))))

    // "exile the top card of your library. You may play that card this turn." (CR 601.2a, 305.1)
    private val exileTop: Step =
        Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), arcanum.api.Destination(Zone.Exile), postBind = "exiled"))
    private val mayPlayExiled: Step = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
        Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))

    // CR 603.4 (intervening if), 111.2 (the controller of the trigger creates the token)
    @Test
    fun dragonmasterOutcast() = card(
        name = "Dragonbinder Outcast",
        cost = "{R}",
        type = "Creature — Human Shaman",
        pt = "1/1",
        text = "At the beginning of your upkeep, if you control six or more lands, create a 5/5 red Dragon creature token with flying.",
    ) parsesTo abilities(
        triggered(upkeep, create(dragon), interveningIf = Condition.Exists(and(land, you), atLeast = 6)),
    )

    // CR 603.4, 111.2
    @Test
    fun dwynensElite() = card(
        name = "Dwenna's Elite",
        cost = "{1}{G}",
        type = "Creature — Elf Warrior",
        pt = "2/2",
        text = "When this creature enters, if you control another Elf, create a 1/1 green Elf Warrior creature token.",
    ) parsesTo abilities(
        triggered(entersSelf, create(elfWarrior), interveningIf = Condition.Exists(and(another, sub("Elf"), you), atLeast = 1)),
    )

    // CR 707.2 (a token that's a copy), 115.1 (target), 111.2
    @Test
    fun extravagantReplication() = card(
        name = "Lavish Duplication",
        cost = "{4}{U}{U}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, create a token that's a copy of another target nonland permanent you control.",
    ) parsesTo abilities(
        triggered(upkeep, create(TokenSpec.CopyOf(target("permanent"))),
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter, you)))),
    )

    // CR 603.12 (reflexive trigger "When you do")
    @Test
    fun faebloomTrick() = card(
        name = "Faerie Gambit",
        cost = "{2}{U}",
        type = "Instant",
        text = "Create two 1/1 blue Faerie creature tokens with flying. When you do, tap target creature an opponent controls.",
    ) parsesTo abilities(
        spell(create(faerie, n(2)),
            reflexive(tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls)))),
    )

    // CR 702.15 (lifelink), 603.6a (enters trigger)
    @Test
    fun guardedHeir() = card(
        name = "Warded Heir",
        cost = "{5}{W}",
        type = "Creature — Human Noble",
        pt = "1/1",
        text = """
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
            When this creature enters, create two 3/3 white Knight creature tokens.
        """,
    ) parsesTo abilities(Keyword.Lifelink, triggered(entersSelf, create(knight, n(2))))

    // CR 611.2a (effect until end of turn), 702.10 (haste)
    @Test
    fun heroicReinforcements() = card(
        name = "Valiant Reinforcements",
        cost = "{2}{R}{W}",
        type = "Sorcery",
        text = "Create two 1/1 white Soldier creature tokens. Until end of turn, creatures you control get +1/+1 and gain haste. (They can attack and {T} this turn.)",
    ) parsesTo abilities(
        spell(create(soldier, n(2)), pump(Objects.Each(creatureYouControl), 1, 1, has(Keyword.Haste))),
    )

    // CR 121.2 (second card drawn each turn), 707.2
    @Test
    fun homunculusHorde() = card(
        name = "Homunculus Swarm",
        cost = "{3}{U}",
        type = "Creature — Homunculus",
        pt = "2/2",
        text = "Whenever you draw your second card each turn, create a token that's a copy of this creature.",
    ) parsesTo abilities(triggered(drawSecondCard, create(TokenSpec.CopyOf(Objects.Self))))

    // CR 700.4 (dies), 111.2
    @Test
    fun infestationSage() = card(
        name = "Blight Sage",
        cost = "{B}",
        type = "Creature — Elf Warlock",
        pt = "1/1",
        text = "When this creature dies, create a 1/1 black and green Insect creature token with flying.",
    ) parsesTo abilities(triggered(diesSelf, create(insect)))

    // CR 107.3 (X defined by the ability), 111.2
    @Test
    fun krennoMobBoss() = card(
        name = "Grenko, Horde Boss",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Goblin Warrior",
        pt = "3/3",
        text = "{T}: Create X 1/1 red Goblin creature tokens, where X is the number of Goblins you control.",
    ) parsesTo abilities(
        activated(cost(tap), create(goblin, count(and(sub("Goblin"), you)))),
    )

    // CR 603.2 (another nontoken Dragon entering), 702.9 (flying), 602.1 (activated ability)
    @Test
    fun lathlissDragonQueen() = card(
        name = "Lethlass, Drake Queen",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Dragon",
        pt = "6/6",
        text = """
            Flying
            Whenever another nontoken Dragon you control enters, create a 5/5 red Dragon creature token with flying.
            {1}{R}: Dragons you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(another, not(token), sub("Dragon"), you)), create(dragon)),
        activated(manaCost("{1}{R}"), pump(Objects.Each(and(sub("Dragon"), you)), 1, 0)),
    )

    // CR 700.4, 111.2
    @Test
    fun maalfeldTwins() = card(
        name = "Maalmark Twins",
        cost = "{5}{B}",
        type = "Creature — Zombie",
        pt = "4/4",
        text = "When this creature dies, create two 2/2 black Zombie creature tokens.",
    ) parsesTo abilities(triggered(diesSelf, create(zombie, n(2))))

    // CR 121.2, 702.9
    @Test
    fun mischievousMystic() = card(
        name = "Playful Mystic",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Flying
            Whenever you draw your second card each turn, create a 1/1 blue Faerie creature token with flying.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(drawSecondCard, create(faerie)))

    // CR 702.20 (vigilance)
    @Test
    fun pridefulParent() = card(
        name = "Proud Guardian",
        cost = "{2}{W}",
        type = "Creature — Cat",
        pt = "2/2",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            When this creature enters, create a 1/1 white Cat creature token.
        """,
    ) parsesTo abilities(Keyword.Vigilance, triggered(entersSelf, create(cat)))

    // CR 111.4 (token with a quoted ability), 509.1b (can't block), 701.21 (sacrifice), 603.12 ("If you do")
    @Test
    fun redcapGutterDweller() = card(
        name = "Redhat Gutter-Dweller",
        cost = "{2}{R}{R}",
        type = "Creature — Goblin Warrior",
        pt = "3/3",
        text = """
            Menace
            When this creature enters, create two 1/1 black Rat creature tokens with "This token can't block."
            At the beginning of your upkeep, you may sacrifice another creature. If you do, put a +1/+1 counter on this creature and exile the top card of your library. You may play that card this turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, create(rat, n(2))),
        triggered(upkeep,
            Step.Pay(cost = cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self), exileTop)),
            mayPlayExiled),  // "You may play that card this turn." is its own sentence, not part of "If you do" (CR 608.2c)
    )

    // CR 111.2
    @Test
    fun releaseTheHounds() = card(
        name = "Release the Hounds",
        cost = "{3}{W}",
        type = "Sorcery",
        text = "Create four 1/1 white Dog creature tokens.",
    ) parsesTo abilities(spell(create(dog, n(4))))

    // CR 601.2 (casting), 111.2
    @Test
    fun riteOfTheDragoncaller() = card(
        name = "Rite of the Drakecaller",
        cost = "{4}{R}{R}",
        type = "Enchantment",
        text = "Whenever you cast an instant or sorcery spell, create a 5/5 red Dragon creature token with flying.",
    ) parsesTo abilities(triggered(youCast(or(instant, sorcery)), create(dragon)))

    // CR 602.5d (activate only as a sorcery), 113.6b (the cost exiles it from the graveyard, so it works there)
    @Test
    fun suspiciousShambler() = card(
        name = "Dubious Shambler",
        cost = "{3}{B}",
        type = "Creature — Zombie",
        pt = "4/2",
        text = "{4}{B}{B}, Exile this card from your graveyard: Create two 2/2 black Zombie creature tokens. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{4}{B}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), create(zombie, n(2)),
            rules = asSorcery),
    )
}
