package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 17: drawing, gaining abilities, gaining life, looking at cards, losing life, power and toughness changes,
 * moving cards and putting counters; with the "modified" cards (CR 700.9), a Saga (CR 714), three modal double-faced
 * cards (CR 712) and an Adventure (CR 715).
 */
class Mh3Chunk17DrawLifeCountersTest {
    private val modifiedCreatureYouControl = and(creature, you, ObjectFilter.Modified)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)

    /** "As this land enters, you may pay 3 life. If you don't, it enters tapped." (CR 614.1c) */
    private val payThreeLifeOrEntersTapped: Ability = Ability.Static(listOf(Mechanic.Replacement(
        event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(cost = cost(CostPart.PayLife(n(3))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), Step.Modify(Modification.EntersTapped)),
        ))),
    )), affects = Affected.Objects(Objects.Self))

    // CR 121.1, 121.2: draw; "that many" is the number the draw named (its power), as in CR 121.2's own "that many"; CR 701.9 discard
    @Test
    fun gloomyWebspinner() = card(
        name = "Gloomy Webspinner",
        cost = "{1}{U}{B}",
        type = "Creature — Horror",
        pt = "2/3",
        text = """
            Lifelink
            Whenever this creature attacks, you may draw cards equal to its power. If you do, discard that many cards.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(attacksSelf,
            Step.Pay(cost = null, steps = listOf(Step.Do(Effect.Draw(Players.You, power(Objects.Self)))), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), Step.Do(Effect.Discard(Players.You, power(Objects.Self), ObjectFilter.Any)))),
    )

    // CR 510.1, 121.1 (combat damage to a player or planeswalker), 118.12 (discard as a cost), 602.1 (exile from graveyard as a cost)
    @Test
    fun mindfulToad() = card(
        name = "Mindful Toad",
        cost = "{U}{B}",
        type = "Creature — Frog",
        pt = "1/2",
        text = """
            Whenever this creature deals combat damage to a player or planeswalker, draw a card.
            Discard a card: Put a +1/+1 counter on this creature.
            Exile three cards from your graveyard: This creature gains flying until end of turn.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(objects = planeswalker, players = PlayerFilter.Any), combat = true)), draw(1)),
        activated(cost(discardACard), counters(Objects.Self)),
        activated(cost(CostPart.Exile(CostObjects.Choose(cardFilter, count = n(3)), from = Zone.Graveyard)), gains(Objects.Self, Keyword.Flying)),
    )

    // CR 700.9: a permanent is modified if it has a counter, or an Equipment or Aura you control attached
    @Test
    fun heraldOfTheElders() = card(
        name = "Herald of the Elders",
        cost = "{2}{W}",
        type = "Creature — Human Cleric",
        pt = "2/3",
        text = """
            Outlast {W} ({W}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)
            Modified creatures you control have lifelink. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Outlast(manaCost("{W}")),
        static(has(Keyword.Lifelink), affects = Objects.Each(modifiedCreatureYouControl)),
    )

    // CR 700.9, 611.3a: a static ability that applies as long as the condition holds
    @Test
    fun stubbornStatue() = card(
        name = "Stubborn Statue",
        cost = "{1}{W}{B}",
        type = "Artifact Creature — Gargoyle",
        pt = "2/2",
        text = """
            This creature has flying as long as it's modified. (Equipment, Auras you control, and counters are modifications.)
            Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.ObjectMatches(objects = Objects.Self, filter = ObjectFilter.Modified)),
        Keyword.Persist,
    )

    // CR 603.10a, 701.8 (exile and return as a new object), 119.1 (gain life)
    @Test
    fun honoredIllusionist() = card(
        name = "Honored Illusionist",
        cost = "{1}{W}",
        type = "Creature — Human Wizard",
        pt = "1/2",
        text = """
            Whenever another creature you control enters, you gain 1 life.
            {4}{W}, {T}: Exile another target creature you control, then return it to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you)), gainLife(1)),
        activated(cost(mana("{4}{W}"), tap), doing(Effect.Move(target("creature"), to = Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.Move(Objects.Bound("exiled"), to = Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 701.20 (look at), 701.20a (reveal), 401.4 (random order on the bottom)
    @Test
    fun thornhedgeKnight() = card(
        name = "Thornhedge Knight",
        cost = "{4}{W}",
        type = "Creature — Human Knight",
        pt = "3/4",
        text = """
            Vigilance
            When this creature enters, look at the top six cards of your library. You may reveal an artifact or enchantment card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(
            entersSelf,
            doing(Effect.LookAt(objects = Objects.LibraryCards(of = Players.You, count = n(6), fromTop = true), players = Players.You, bind = "seen")),
            Step.Choose(
                choice = Choice.Objects(filter = or(artifact, enchantment), min = n(0), max = n(1), from = Objects.Bound("seen")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Reveal(objects = Objects.Bound("chosen"))),
            doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(zone = Zone.Hand))),
            doing(Effect.Move(
                objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("chosen")),
                to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
            )),
            ifThen(Condition.Not(Condition.NonEmpty("chosen")), counters(Objects.Self)),
        ),
    )

    // CR 701.43 (fabricate), 603.10a, 120.1 (lose life)
    @Test
    fun puppetPupil() = card(
        name = "Puppet Pupil",
        cost = "{1}{B}",
        type = "Creature — Human Artificer",
        pt = "1/2",
        text = """
            Fabricate 1 (When this creature enters, put a +1/+1 counter on it or create a 1/1 colorless Servo artifact creature token.)
            Whenever another creature or artifact you control is put into a graveyard from the battlefield, each opponent loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Fabricate(1),
        triggered(dies(and(another, or(creature, artifact), you)), loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    // CR 603.6c: leaves-the-battlefield trigger; CR 111.7 a token that left stays only as a last-known object
    @Test
    fun hadriasNightblade() = card(
        name = "Hadria's Nightblade",
        cost = "{2}{B}",
        type = "Creature — Elf Warrior",
        pt = "1/3",
        text = "Whenever a token you control leaves the battlefield, each opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(token, you), from = Zone.Battlefield)),
            loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 604.3 (a characteristic-defining count), 701.19 (search), 701.20 (shuffle)
    @Test
    fun wightOfTheOssuary() = card(
        name = "Wight of the Ossuary",
        cost = "{B}{G}",
        type = "Creature — Zombie Knight",
        pt = "2/2",
        text = """
            Vigilance
            This creature gets +1/+1 for each creature card in your graveyard.
            {T}, Sacrifice another creature: Search your library for a land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.ModifyPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature)))),
        activated(cost(tap, sacrificeA(and(another, creature))), search(land), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), Step.Do(Effect.Shuffle(Players.You))),
    )

    // CR 714.2 chapters, 701.8 exile, 111.1 token, 122.1b keyword counter, 702.4 double strike
    @Test
    fun sunderTheColossus() = card(
        name = "Sunder the Colossus",
        cost = "{3}{W}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Exile target creature an opponent controls with power 3 or greater.
            II — Create a 2/1 white Cat Warrior creature token, then put a vigilance counter on a creature you control.
            III — Target creature you control gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        triggered(Trigger.Chapter(listOf(1)), move(target("creature"), Zone.Exile),
            targets = listOf(slot("creature", and(creature, opponents, power(Comparison.AtLeast, 3))))),
        triggered(Trigger.Chapter(listOf(2)),
            create(creatureToken("Cat Warrior", listOf(Color.White), 2, 1)),
            Step.Choose(Choice.Objects(filter = creatureYouControl, min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.PutCounters(Objects.Bound("chosen"), CounterKind.Keyword(Keyword.Vigilance), n(1)))),
        triggered(Trigger.Chapter(listOf(3)),
            untilEndOfTurn(target("creature"), has(Keyword.DoubleStrike)),
            targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 712.2: the back face of a modal double-faced card is a land
    @Test
    fun gutterTrawler() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Gutter Trawler",
            cost = "{2}{B}",
            type = "Creature — Goblin",
            pt = "3/1",
            text = "When this creature enters, exile target player's graveyard.",
        ) to abilities(
            triggered(entersSelf,
                move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile),
                targets = listOf(targetPlayer)),
        ),
        card(
            name = "Gutter Bog",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {B}.
            """,
        ) to abilities(payThreeLifeOrEntersTapped, addOne("{B}")),
    )

    // CR 712.2, 405.1 (a spell as a target), 400.7
    @Test
    fun sinkIntoSlumber() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Sink into Slumber",
            cost = "{1}{U}{U}",
            type = "Instant",
            text = "Return target spell or nonland permanent an opponent controls to its owner's hand.",
        ) to abilities(
            spell(
                move(target("target"), Zone.Hand),
                targets = listOf(slot("target", or(and(spellFilter, opponents), and(not(land), ObjectFilter.Category(ObjectCategory.Permanent), opponents)))),
            ),
        ),
        card(
            name = "Slumbering Springs",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {U}.
            """,
        ) to abilities(payThreeLifeOrEntersTapped, addOne("{U}")),
    )

    // CR 122.1b: a reach counter and a deathtouch counter give those keywords; 701.21 untap
    @Test
    fun giftOfTheAdder() = card(
        name = "Gift of the Adder",
        cost = "{G}",
        type = "Instant",
        text = "Put a +1/+1 counter, a reach counter, and a deathtouch counter on target creature. Untap it.",
    ) parsesTo abilities(
        spell(
            counters(target("creature")),
            counters(target("creature"), 1, CounterKind.Keyword(Keyword.Reach)),
            counters(target("creature"), 1, CounterKind.Keyword(Keyword.Deathtouch)),
            untapIt(target("creature")),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 715.3 adventure, 700.9 modified creature dies, 122.1 counters, 601.2d divide counters
    @Test
    fun ondaKnotweaver() = cardFaces(
        Layout.Adventure,
        card(
            name = "Onda Knotweaver",
            cost = "{2}{W}{B}",
            type = "Creature — Kor Rogue",
            pt = "2/2",
            text = """
                Lifelink
                Whenever another modified creature you control dies, put two +1/+1 counters on this creature.
            """,
        ) to abilities(
            Keyword.Lifelink,
            triggered(dies(and(another, creature, you, ObjectFilter.Modified)), counters(Objects.Self, 2)),
        ),
        card(
            name = "Cast a Line",
            cost = "{W}{B}",
            type = "Sorcery — Adventure",
            text = "Distribute two +1/+1 counters among one or two target creatures. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
                targets = listOf(slot("creatures", creature, min = 1, max = 2).copy(divided = n(2)))),
        ),
    )

    @Test
    fun refreshingMeal() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Refreshing Meal",
            cost = "{B/G}",
            type = "Instant",
            text = "Put a +1/+1 counter on target creature. It gains indestructible until end of turn.",
        ) to abilities(
            spell(counters(target("creature")), gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creature))),
        ),
        card(
            name = "Overgrown Grove",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {B} or {G}.
            """,
        ) to abilities(
            entersTapped,
            addEither("B", "G"),
        ),
    )
}
