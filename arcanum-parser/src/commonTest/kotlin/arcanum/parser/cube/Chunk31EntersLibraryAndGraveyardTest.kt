package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 31: creatures whose enters trigger works the library and the graveyards: milling, revealing or looking at the
 * top cards and taking one ("the rest" go elsewhere), getting cards back from the graveyard, giving flashback, putting
 * a land from hand onto the battlefield; with "enters or dies", a dies trigger with a reflexive "when you do", a
 * once-each-turn batch trigger, hideaway's linked card, and evoke.
 */
class Chunk31EntersLibraryAndGraveyardTest {
    // ---- Shared shapes

    private fun mill(n: Int) = Step.Do(Effect.Mill(Players.You, count = Value.Fixed(n)))
    private val bottomInRandomOrder = Destination(Zone.Library, libraryPosition = -1, randomOrder = true)

    /** "[Choose] up to one [filter] from among the cards [seen]" (CR 608.2d: a choice of zero or one). */
    private fun upToOneFromAmong(filter: ObjectFilter, seen: String, bind: String) = Step.Choose(
        choice = Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound(seen)),
        bind = bind,
    )

    private val theRest = Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen"))

    // ---- Mill

    @Test
    fun wreckageAnalyst() = card(
        name = "Wreckage Analyst",
        cost = "{1}{G}",
        type = "Creature — Elf Detective",
        pt = "1/3",
        text = """
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            {3}{G}, Sacrifice this creature: Return all land cards from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        whenThisEnters(mill(3)),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{3}{G}")!!), CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Move(
                    objects = Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land)) + inYourGraveyard)),
                    to = Destination(Zone.Battlefield, tapped = true),
                ))),
            ),
        ),
    )

    // CR 700.4: "dies" means "is put into a graveyard from the battlefield"; one ability with two trigger events.
    @Test
    fun stitchworkPorter() = card(
        name = "Stitchwork Porter",
        cost = "{B}",
        type = "Creature — Zombie",
        pt = "1/1",
        text = "When this creature enters or dies, mill three cards. (Put the top three cards of your library into your graveyard.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            )),
            instructions = Instructions(steps = listOf(mill(3))),
        ),
    )

    // CR 400.7: "exile it" in the dies trigger is the new card in the graveyard. CR 603.12: "When you do" is a
    // reflexive trigger, its target chosen when it is put on the Stack.
    @Test
    fun undyingValet() = card(
        name = "Undying Valet",
        cost = "{1}{B}",
        type = "Creature — Zombie",
        pt = "1/2",
        text = """
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            When this creature dies, you may exile it. When you do, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        whenThisEnters(mill(3)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.After)), from = Zone.Graveyard))),
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did("paid"),
                        then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                            targets = listOf(objectSlot("card", ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature)) + inYourGraveyard))),
                            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
                        )))),
                    ),
                ),
            ),
        ),
    )

    // CR 702.41a (affinity). "Choose target …" only targets; the permission lasts this turn.
    @Test
    fun ondraLurkerOfTheMere() = card(
        name = "Ondra, Lurker of the Mere",
        cost = "{2}{U}",
        type = "Legendary Creature — Merfolk Wizard",
        pt = "1/2",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            When Ondra enters, mill four cards.
            {T}: Choose target artifact card in your graveyard. You may cast that card this turn. (You still pay its costs. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        Keyword.Affinity(forWhat = ObjectFilter.Type(CardType.Artifact), printed = "artifacts"),
        whenThisEnters(mill(4)),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(objectSlot("card", ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact)) + inYourGraveyard))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(from = Zone.Graveyard)),
                    affects = Affected.Objects(Objects.Target("card")),
                    duration = Duration.EndOfTurn,
                ))),
            ),
        ),
    )

    // ---- The top of the library

    @Test
    fun twiningAugur() = card(
        name = "Twining Augur",
        cost = "{G}{U}",
        type = "Creature — Snake Elf Druid",
        pt = "1/1",
        text = "When this creature enters, reveal the top card of your library. If it's a land card, put it onto the battlefield. Otherwise, put that card into your hand.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(1)), bind = "revealed")),
            Step.If(
                condition = Condition.ObjectMatches(Objects.Bound("revealed"), ObjectFilter.Type(CardType.Land)),
                then = listOf(Step.Do(Effect.Move(Objects.Bound("revealed"), to = Destination(Zone.Battlefield)))),
                otherwise = listOf(Step.Do(Effect.Move(Objects.Bound("revealed"), to = Destination(Zone.Hand)))),
            ),
        ),
    )

    // CR 603.2c: "one or more" makes one trigger for a batch of artifacts entering at once.
    @Test
    fun cleverTinkersmith() = card(
        name = "Clever Tinkersmith",
        cost = "{1}{W}",
        type = "Creature — Human Artificer",
        pt = "1/1",
        text = """
            When this creature enters, look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Whenever one or more artifacts you control enter, put a +1/+1 counter on this creature. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(4)), bind = "seen")),
            upToOneFromAmong(ObjectFilter.Type(CardType.Artifact), seen = "seen", bind = "chosen"),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Hand))),
            Step.Do(Effect.Move(theRest, to = bottomInRandomOrder)),
        ),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.ZoneChange(
                    objects = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.ControlledBy(PlayerFilter.You))),
                    to = Zone.Battlefield,
                )),
                oneOrMore = true,
            ),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1)))),
            ),
            perTurn = 1,
        ),
    )

    @Test
    fun satyrPathseeker() = card(
        name = "Satyr Pathseeker",
        cost = "{1}{G}",
        type = "Creature — Satyr",
        pt = "1/1",
        text = "When this creature enters, reveal the top four cards of your library. You may put a land card from among them into your hand. Put the rest into your graveyard.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(4)), bind = "seen")),
            upToOneFromAmong(ObjectFilter.Type(CardType.Land), seen = "seen", bind = "chosen"),
            Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Hand))),
            Step.Do(Effect.Move(theRest, to = Destination(Zone.Graveyard))),
        ),
    )

    // CR 107.3c: X is defined by the text, for every X of the ability. CR 700.5: devotion.
    @Test
    fun oracleOfTheDeepTide() = card(
        name = "Oracle of the Deep Tide",
        cost = "{U}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "1/3",
        text = "When this creature enters, look at the top X cards of your library, where X is your devotion to blue. Put up to one of them on top of your library and the rest on the bottom of your library in a random order. If X is greater than or equal to the number of cards in your library, you win the game. (Each {U} in the mana costs of permanents you control counts toward your devotion to blue.)",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Devotion(listOf(Color.Blue))), bind = "seen")),
            upToOneFromAmong(ObjectFilter.Any, seen = "seen", bind = "kept"),
            Step.Do(Effect.Move(Objects.Bound("kept"), to = Destination(Zone.Library, libraryPosition = 0))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), to = bottomInRandomOrder)),
            Step.If(
                condition = Condition.Compare(
                    left = Value.Devotion(listOf(Color.Blue)),
                    comparison = Comparison.AtLeast,
                    right = Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))))),
                ),
                then = listOf(Step.Do(Effect.WinGame(Players.You))),
            ),
        ),
    )

    // CR 702.75a: hideaway's exiled card is linked to it; "the exiled card" is that card (CR 607.2a).
    @Test
    fun seerOfMorrows() = card(
        name = "Seer of Morrows",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Hideaway 4 (When this creature enters, look at the top four cards of your library, exile one face down, then put the rest on the bottom in a random order.)
            This creature enters tapped.
            When this creature leaves the battlefield, put the exiled card into its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Hideaway(4),
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped),
        ))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Move(Objects.LinkedTo(Keyword.Hideaway.Kind), to = Destination(Zone.Hand)))),
            ),
        ),
    )

    // ---- From the graveyard and the hand

    @Test
    fun undyingChronicler() = card(
        name = "Undying Chronicler",
        cost = "{1}{G}{G}",
        type = "Creature — Human Shaman",
        pt = "2/1",
        text = "When this creature enters, you may return target card from your graveyard to your hand.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.May(listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand))))),
            targets = listOf(objectSlot("card", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card)) + inYourGraveyard))),
        ),
    )

    // CR 702.34a: flashback; its cost here is the card's own mana cost.
    @Test
    fun echoflashAdept() = card(
        name = "Echoflash Adept",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Flash
            When this creature enters, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        whenThisEnters(
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
                affects = Affected.Objects(Objects.Target("card")),
                duration = Duration.EndOfTurn,
            )),
            targets = listOf(objectSlot("card", ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
            ) + inYourGraveyard))),
        ),
    )

    @Test
    fun canopyBrowser() = card(
        name = "Canopy Browser",
        cost = "{G}",
        type = "Creature — Sloth Beast",
        pt = "0/3",
        text = """
            Reach
            When this creature enters, you may put a land card from your hand onto the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        whenThisEnters(
            Step.Choose(
                choice = Choice.Objects(
                    filter = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))),
                    min = Value.Fixed(0),
                    max = Value.Fixed(1),
                ),
                bind = "chosen",
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Battlefield, tapped = true))),
        ),
    )

    // CR 702.74a: evoke, here with a cost that isn't mana.
    @Test
    fun persistence() = card(
        name = "Persistence",
        cost = "{1}{G}{G}",
        type = "Creature — Elemental Incarnation",
        pt = "3/4",
        text = """
            Flash
            Reach
            When this creature enters, up to one target player puts all the cards from their graveyard on the bottom of their library in a random order.
            Evoke—Exile a green card from your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Reach,
        whenThisEnters(
            Step.Do(Effect.Move(
                objects = Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))),
                ))),
                to = bottomInRandomOrder,
            )),
            targets = listOf(TargetSlot(name = "player", what = TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(0))),
        ),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(Color.Green)), from = Zone.Hand)))),
    )

    @Test
    fun goblinSalvager() = card(
        name = "Goblin Salvager",
        cost = "{1}{R}",
        type = "Creature — Goblin Artificer",
        pt = "1/2",
        text = """
            When this creature enters, you may search your library for an artifact card, put it into your graveyard, then shuffle.
            {R}, {T}, Sacrifice an artifact: Return target artifact card with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.May(listOf(
                Step.Do(Effect.Search(Players.You, ObjectFilter.Type(CardType.Artifact), bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Graveyard))),
                Step.Do(Effect.Shuffle(Players.You)),
            )),
        ),
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.Mana(ManaCost.parse("{R}")!!),
                CostPart.TapSelf,
                CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Artifact))),
            )),
            instructions = Instructions(
                targets = listOf(objectSlot("card", ObjectFilter.And(listOf(
                    ObjectFilter.Type(CardType.Artifact),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                ) + inYourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Battlefield)))),
            ),
        ),
    )
}
