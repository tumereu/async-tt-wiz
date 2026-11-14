package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Total
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 12: red cards. An attack batch with total power 12 or greater, the first such each turn (CR 508.3d, 603.2c),
 * untapping the attackers and an additional combat phase (CR 500.8); mana in any combination of colours spent only on Dragon
 * spells (CR 106.6); 3 damage divided among one, two or three targets (CR 601.2d); thirteen cards revealed and a random creature
 * card among them (CR 701.20); top cards played until the end of your next turn; a quest counter and a search of the hand
 * and/or library (CR 701.23); "one or both" modes; a named artifact creature token with defender (CR 111.4).
 */
class HobChunk12RedTest {
    // CR 508.3d, 603.2c: the first batch of attackers with total power 12 or greater each turn; CR 500.8
    @Test
    fun duneWereWyrm() = card(
        name = "Dune Were-Wyrm",
        cost = "{4}{R}{R}",
        type = "Creature — Dragon Wurm",
        text = """
            This creature gets +2/+0 for each Mountain you control.
            Whenever you attack with creatures with total power 12 or greater for the first time each turn, untap all attacking creatures. After this phase, there is an additional combat phase.
        """,
        pt = "0/5",
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(Value.Times(count(and(sub("Mountain"), you)), 2), n(0))),
        triggered(Trigger.On(listOf(EventPattern.Attack(you)), oneOrMore = true, nthThisTurn = 1, batchTotal = Total(NumericProperty.Power, n(12))),
            untapIt(Objects.Each(and(attacking, creature))), Step.Do(Effect.ExtraCombat())),
    )

    // CR 106.6: mana spendable only on Dragon spells
    @Test
    fun devastationOfSkarg() = card(
        name = "Devastation of Skarg",
        cost = "{2}{R}{R}",
        type = "Sorcery",
        text = """
            Devastation of Skarg deals 3 damage to each non-Dragon creature.
            Add four mana in any combination of colors. Spend this mana only to cast Dragon spells.
        """,
    ) parsesTo abilities(
        spell(damage(3, toObjects(Objects.Each(and(not(sub("Dragon")), creature)))),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(4), combination = true), restriction = and(spellFilter, sub("Dragon"))), Players.You))),
    )

    @Test
    fun dorinCarrierOfComrades() = card(
        name = "Dorin, Carrier of Comrades",
        cost = "{2}{R}",
        type = "Legendary Creature — Dwarf Warrior",
        text = """
            Trample
            When Dorin enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, createTreasure()),
    )

    // CR 601.2d: divided as the ability is put on the Stack
    @Test
    fun grimbaldFireworkMaker() = card(
        name = "Grimbald, Firework-Maker",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Reach
            When Grimbald enters, he deals 3 damage divided as you choose among one, two, or three targets.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, damage(Value.DividedShare, toAny), targets = listOf(anyTarget.copy(max = n(3), divided = n(3)))),
    )

    // CR 701.20; the random card is picked by the host (D25); the rest to the bottom in a random order
    @Test
    fun escapeBarrel() = card(
        name = "Escape Barrel",
        cost = "{3}{R}",
        type = "Artifact",
        text = "When this artifact is put into a graveyard from the battlefield, reveal the top thirteen cards of your library. Put a random creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(13), fromTop = true), bind = "revealed")),
            Step.Bind(Objects.Random(Objects.Where(Objects.Bound("revealed"), creature), n(1)), "picked"),
            move(Objects.Bound("picked"), Zone.Battlefield),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("picked")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 611.2: until the end of your next turn
    @Test
    fun mountGrimOpportunist() = card(
        name = "Mount Grim Opportunist",
        cost = "{3}{R}",
        type = "Creature — Goblin Rogue",
        text = "When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.",
        pt = "4/2",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn))),
    )

    // CR 118.12, 701.23: hand and/or library; shuffled only if the library was searched
    @Test
    fun finalGlowOfTheDwarfKingsDay() = card(
        name = "Final Glow of the Dwarf-King's Day",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            Whenever a Mountain you control enters, put a quest counter on this enchantment. If it has six or more quest counters on it, sacrifice it. If you do, search your hand and/or library for a Dragon card and put it onto the battlefield. If you search your library this way, shuffle.
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(enters(and(sub("Mountain"), you)),
            counters(Objects.Self, kind = CounterKind.Named("quest")),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("quest"), atLeast = 6)), then = listOf(
                Step.Pay(cost(sacrificeSelf), payer = Players.You, mandatory = true, bind = "sacrificed"),
                Step.If(Condition.Did("sacrificed"), then = listOf(
                    Step.Do(Effect.Search(Players.You, sub("Dragon"), max = Value.Fixed(1), zones = listOf(Zone.Hand, Zone.Library), bind = "found",
                        searcher = Players.You, zonesChosen = true)),
                    move(Objects.Bound("found"), Zone.Battlefield),
                    Step.If(Condition.Searched("found", Zone.Library), then = listOf(shuffle))))))),
        Keyword.Typecycling(sub("Mountain"), "Mountaincycling", manaCost("{2}")),
    )

    // CR 700.2: one or both
    @Test
    fun acornVolley() = card(
        name = "Acorn Volley",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one or both —
            • Acorn Volley deals 3 damage to target creature. If that creature would die this turn, exile it instead.
            • Destroy target artifact token.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(target("creature"))), Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
                targets = listOf(slot("creature", creature))),
            mode(destroy(target("token")), targets = listOf(slot("token", and(artifact, token)))), max = 2))),
    )

    // CR 608.2h: counted as it resolves
    @Test
    fun skargTheSplendid() = card(
        name = "Skarg the Splendid",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Dragon",
        text = """
            Flying, haste
            Whenever Skarg attacks, he deals damage equal to the number of Treasures you control to any target.
            At the beginning of your upkeep, create a Treasure token.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(attacksSelf, damage(count(and(sub("Treasure"), you)), toAny), targets = listOf(anyTarget)),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), createTreasure()),
    )

    @Test
    fun skargsWrath() = card(
        name = "Skarg's Wrath",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature gets +3/+0 and gains reach and first strike until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 0, has(Keyword.Reach, Keyword.FirstStrike)), targets = listOf(slot("creature", creature))),
    )

    // CR 602.5b: only during your turn and only once each turn
    @Test
    fun iceslopeTracker() = card(
        name = "Iceslope Tracker",
        cost = "{2}{R}",
        type = "Creature — Goblin Ranger",
        text = "Sacrifice another creature or artifact: Exile the top card of your library. You may play it until the end of your next turn. Activate only during your turn and only once each turn.",
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(sacrificeA(and(another, or(creature, artifact)))),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn)),
            rules = anyTime.copy(perTurn = 1, condition = Condition.YourTurn)),
    )

    // CR 111.4: a named token
    @Test
    fun rockGiantOfTheUpperGap() = card(
        name = "Rock-Giant of the Upper Gap",
        cost = "{5}{R}{R}",
        type = "Creature — Giant",
        text = """
            Whenever this creature enters or attacks, create a 3/1 colorless Wall artifact creature token with defender named Stone Boulder.
            {2}{R}, Sacrifice an artifact: This creature deals 4 damage to any target.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, create(TokenSpec.Defined(FaceDefinition("Stone Boulder", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
            subtypes = listOf(Subtype("Wall")), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(1), abilities = listOf(Keyword.Defender))))),
        activated(cost(mana("{2}{R}"), sacrificeA(artifact)), damage(4, toAny), targets = listOf(anyTarget)),
    )
}
