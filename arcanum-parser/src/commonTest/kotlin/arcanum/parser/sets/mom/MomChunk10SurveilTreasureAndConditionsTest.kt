package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 10: surveil (CR 701.25), Treasure (CR 111.10a) and conditions on counters, "it", "as long as" and protectors (CR 310.8). */
class MomChunk10SurveilTreasureAndConditionsTest {
    private fun surveil(n: Int) = doing(Effect.Surveil(Players.You, n(n)))
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val attackDespiteDefender = Mechanic.CanAttackDespiteDefender
    private val defense = CounterKind.Defense

    // CR 510.2, 310.6 (combat damage to a battle); CR 701.25a; CR 400.7 (a choice from the graveyard as it resolves, CR 608.2c)
    @Test
    fun rootwayPathseeker() = card(
        name = "Rootway Pathseeker",
        cost = "{1}{G}",
        type = "Creature — Merfolk Scout",
        pt = "2/3",
        text = """
            Whenever this creature deals combat damage to a player or battle, surveil 1, then you may return a land card from your graveyard to the battlefield tapped. (To surveil 1, look at the top card of your library. You may put that card into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(combatDamageToAPlayerOrBattle,
            surveil(1),
            Step.Choose(Choice.Objects(inYourGraveyard(land), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true)),
    )

    // CR 603.10a, 700.4 (put into a graveyard from the battlefield); "one or more" is one trigger for the batch (CR 603.2c)
    @Test
    fun watcherOfTakenGlimpses() = card(
        name = "Watcher of Taken Glimpses",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Warlock",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever one or more artifacts and/or creatures you control are put into a graveyard from the battlefield, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(Trigger.On(EventPattern.ZoneChange(and(or(artifact, creature), you), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            surveil(1)),
    )

    // CR 601.2f (a cost reduction by target), 701.25a
    @Test
    fun tidalScattering() = card(
        name = "Tidal Scattering",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets an attacking creature.
            Return target creature to its owner's hand. Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
            affects = arcanum.api.Affected.Objects(Objects.Self), condition = Condition.ObjectMatches(target("creature"), attacking)),
        spell(move(target("creature"), Zone.Hand), surveil(2), targets = listOf(slot("creature", creature))),
    )

    // CR 613.1b (control), 702.10 (haste), 113.2c (a granted triggered ability), 111.10a
    @Test
    fun forgeBridle() = card(
        name = "Forge Bridle",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Gain control of target creature until end of turn. Untap that creature. Until end of turn, it gains haste and "Whenever this creature deals combat damage to a player or battle, create a Treasure token." (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            untilEndOfTurn(target("creature"), Mechanic.GainAbilities(listOf(Keyword.Haste, triggered(combatDamageToAPlayerOrBattle, createTreasure())))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 700.2 (choose any number of modes), 111.10a, 701.21a (each player sacrifices, APNAP CR 101.4), 614.1a (a replacement for the turn)
    @Test
    fun griskAndVolmar() = card(
        name = "Grisk and Volmar",
        cost = "{1}{B}{B}{R}{R}",
        type = "Legendary Creature — Faerie Dwarf",
        pt = "3/4",
        text = """
            Flying, first strike, haste
            Whenever Grisk and Volmar deals combat damage to a player or battle, choose any number —
            • Each player creates a Treasure token.
            • Each player sacrifices a creature of their choice.
            • If a source would deal damage to a player or battle this turn, it deals that much damage plus 2 instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        Keyword.Haste,
        Ability.Triggered(combatDamageToAPlayerOrBattle, Instructions(modes = modes(
            mode(doing(Effect.CreateToken(treasure, n(1), Players.Each(PlayerFilter.Any)))),
            mode(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "sacrificed",
                    chooser = Players.Bound("player")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)),
            mode(doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = battle, players = PlayerFilter.Any)),
                modify = listOf(Modification.AddAmount(n(2))))), null, Duration.EndOfTurn))),
            min = 0, max = 3))),
    )

    // CR 122.6 (counters put), 111.10a; "for the first time each turn"
    @Test
    fun hammerholdSmith() = card(
        name = "Hammerhold Smith",
        cost = "{1}{R}",
        type = "Creature — Dwarf Artificer",
        pt = "2/1",
        text = """
            Whenever one or more +1/+1 counters are put on this creature for the first time each turn, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1)), oneOrMore = true, nthThisTurn = 1), createTreasure()),
    )

    // CR 701.8a, 111.10a
    @Test
    fun lethalMockery() = card(
        name = "Lethal Mockery",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = """
            Destroy target creature or planeswalker. Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(destroy(target("permanent")), createTreasure(), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 614.1a (one more +1/+1 counter on any permanent you control), 106.1 (X mana of one color)
    @Test
    fun spiritOfMurmuredWishes() = card(
        name = "Spirit of Murmured Wishes",
        cost = "{2}{G}",
        type = "Creature — Spirit",
        pt = "1/1",
        text = """
            If one or more +1/+1 counters would be put on a permanent you control, that many plus one +1/+1 counters are put on that permanent instead.
            {T}: Add X mana of any one color, where X is this creature's power.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CountersPut(and(permanentFilter, you), p1p1), modify = listOf(Modification.AddAmount(n(1))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(power(Objects.Self))), Players.You))),
    )

    // CR 702.16 (protection from colorless or a chosen color); "If it's a creature" is checked as it resolves (CR 608.2h)
    @Test
    fun seraphicInterposition() = card(
        name = "Seraphic Interposition",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Target creature or planeswalker you control gains protection from colorless or from the color of your choice until end of turn. If it's a creature, put a +1/+1 counter on it. (It can't be blocked, targeted, dealt damage, enchanted, or equipped by anything with that quality.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("colorless", "a color")), bind = "quality", chooser = Players.You),
            Step.If(Condition.Chose("quality", "colorless"),
                then = listOf(untilEndOfTurn(target("permanent"), Mechanic.GainAbilities(listOf(Keyword.Protection(ObjectFilter.Colorless))))),
                otherwise = listOf(
                    Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
                    untilEndOfTurn(target("permanent"), Mechanic.GainAbilities(listOf(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Chosen("color")))))),
                )),
            Step.If(Condition.ObjectMatches(target("permanent"), creature), then = listOf(counters(target("permanent")))),
            targets = listOf(slot("permanent", and(or(creature, planeswalker), you))),
        ),
    )

    // CR 508.1 ("whenever you attack"), 506.3 (what it attacks is checked as the ability resolves); "instead" replaces the pump
    @Test
    fun stampedingTerrashell() = card(
        name = "Stampeding Terrashell",
        cost = "{2}{R}{G}",
        type = "Creature — Dinosaur Beast",
        pt = "3/3",
        text = """
            Trample, haste
            Whenever you attack, target attacking creature gets +1/+1 until end of turn. If it's attacking a battle, put a +1/+1 counter on it instead.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        triggered(youAttack,
            Step.If(Condition.ObjectMatches(Objects.AttackedBy(target("creature")), battle),
                then = listOf(counters(target("creature"))),
                otherwise = listOf(pump(target("creature"), 1, 1))),
            targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 702.3b (attack as though it didn't have defender), 509.1b (can't be blocked)
    @Test
    fun voyageSentinel() = card(
        name = "Voyage Sentinel",
        cost = "{1}{U}",
        type = "Creature — Merfolk Rogue",
        pt = "2/3",
        text = """
            Defender
            As long as an opponent has eight or more cards in their graveyard, this creature can attack as though it didn't have defender and it can't be blocked.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(attackDespiteDefender, Mechanic.CantBeBlocked, condition = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent,
            PlayerFilter.Where(Condition.Exists(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested))), atLeast = 8)))), atLeast = 1)),
    )

    // CR 702.3b
    @Test
    fun magmadeepWarden() = card(
        name = "Magmadeep Warden",
        cost = "{2}{R}",
        type = "Creature — Lizard Warrior",
        pt = "4/3",
        text = """
            Defender
            As long as this creature's power is 5 or greater, it can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(attackDespiteDefender, condition = Condition.ObjectMatches(Objects.Self, power(Comparison.AtLeast, 5))),
    )

    // CR 702.7 (first strike); "another Knight entered … this turn" looks back over the turn
    @Test
    fun bladepledgedRider() = card(
        name = "Bladepledged Rider",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        pt = "3/1",
        text = "This creature has first strike as long as another Knight entered the battlefield under your control this turn.",
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(another, sub("Knight"), you), to = Zone.Battlefield),
            atLeast = 1, measure = Measure.Events)),
    )

    // CR 702.12 (indestructible), 506.3 (attacking a battle), 310.6
    @Test
    fun battleChronicler() = card(
        name = "Battle Chronicler",
        cost = "{2}{G}",
        type = "Creature — Human Monk",
        pt = "3/3",
        text = """
            Reach
            This creature has indestructible as long as it attacked a battle this turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        static(has(Keyword.Indestructible), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(
            EventPattern.Attack(ObjectFilter.Is(Objects.Self), target = RecipientFilter(objects = battle))))),
    )

    // CR 310.8 (a battle's protector), 310.4 (defense counters), 602.5d (as a sorcery)
    @Test
    fun omenStalker() = card(
        name = "Omen Stalker",
        cost = "{1}{G}",
        type = "Creature — Satyr Scout",
        pt = "1/1",
        text = """
            {T}: Untap target land.
            {T}: Choose target battle. If an opponent protects it, remove a defense counter from it. Otherwise, put a defense counter on it. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("land")), targets = listOf(slot("land", land))),
        activated(cost(tap),
            Step.If(Condition.PlayerMatches(Players.ProtectorOf(target("battle")), PlayerFilter.Opponent),
                then = listOf(doing(Effect.RemoveCounters(target("battle"), defense, n(1)))),
                otherwise = listOf(counters(target("battle"), 1, defense))),
            targets = listOf(slot("battle", battle)), rules = asSorcery),
    )
}
