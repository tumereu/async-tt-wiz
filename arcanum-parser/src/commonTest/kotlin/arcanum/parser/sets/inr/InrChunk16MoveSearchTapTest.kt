package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 16: returning cards (CR 400.7), must-attack (CR 508.1d), +1/+1 counters (CR 122.1), sacrificing (CR 701.21),
 * searching (CR 701.23), power/toughness definitions (CR 604.3, 613.4), tapping and untapping (CR 701.26).
 */
class InrChunk16MoveSearchTapTest {
    // CR 603.12: a reflexive trigger after the payment; CR 702.35 discard trigger from the graveyard
    @Test
    fun edgarsAwakening() = card(
        name = "Edgar's Awakening", cost = "{3}{B}{B}", type = "Sorcery",
        text = """
            Return target creature card from your graveyard to the battlefield.
            When you discard this card, you may pay {B}. When you do, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
        triggered(on(EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You)),
            Step.Pay(cost(mana("{B}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                reflexive(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))))),
    )

    // CR 400.7: the card returns as a new object; CR 611.2a: "it's a Spirit" is checked as the ability resolves
    @Test
    fun essenceFlux() = card(
        name = "Essence Flux", cost = "{U}", type = "Instant",
        text = "Exile target creature you control, then return that card to the battlefield under its owner's control. If it's a Spirit, put a +1/+1 counter on it.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), sub("Spirit")), counters(Objects.Bound("exiled"))),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    // CR 602.1: an activated ability of a creature on the battlefield
    @Test
    fun groundskeeper() = card(
        name = "Groundskeeper", cost = "{G}", type = "Creature — Human Druid", pt = "1/1",
        text = "{1}{G}: Return target basic land card from your graveyard to your hand.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{G}")), move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(ObjectFilter.Super(Supertype.Basic), land)))),
    )

    // CR 712.4: meld; CR 205.3m (Rogue)
    @Test
    fun midnightScavengers() = cardFaces(Layout.Meld("Graf Rats"),
        card(
            name = "Midnight Scavengers", cost = "{4}{B}", type = "Creature — Human Rogue", pt = "3/3",
            text = """
                When this creature enters, you may return target creature card with mana value 3 or less from your graveyard to your hand.
                (Melds with Graf Rats.)
            """,
        ) to abilities(
            triggered(entersSelf, Step.May(listOf(move(target("card"), Zone.Hand)), player = Players.You),
                targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))),
        ),
        card(
            name = "Chittering Host", cost = null, type = "Creature — Eldrazi Horror", pt = "5/6",
            text = """
                Haste
                Menace (This creature can't be blocked except by two or more creatures.)
                When this creature enters, other creatures you control get +1/+0 and gain menace until end of turn.
            """,
        ) to abilities(
            Keyword.Haste,
            Keyword.Menace,
            triggered(entersSelf, untilEndOfTurn(Objects.Each(and(another, creature, you)), pt(1, 0), Mechanic.GainAbilities(listOf(Keyword.Menace)))),
        ),
    )

    // CR 400.3: a permanent returns to its owner's hand
    @Test
    fun mistRaven() = card(
        name = "Mist Raven", cost = "{2}{U}{U}", type = "Creature — Bird", pt = "2/2",
        text = """
            Flying
            When this creature enters, return target creature to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun spectralShepherd() = card(
        name = "Spectral Shepherd", cost = "{2}{W}", type = "Creature — Spirit", pt = "2/2",
        text = """
            Flying
            {1}{U}: Return target Spirit you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{1}{U}")), move(target("Spirit"), Zone.Hand), targets = listOf(slot("Spirit", and(sub("Spirit"), you)))),
    )

    // CR 608.2b? exiled spell; CR 603.10a leaves-the-battlefield trigger
    @Test
    fun spellQueller() = card(
        name = "Spell Queller", cost = "{1}{W}{U}", type = "Creature — Spirit", pt = "2/3",
        text = """
            Flash
            Flying
            When this creature enters, exile target spell with mana value 4 or less.
            When this creature leaves the battlefield, the exiled card's owner may cast that card without paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf,
            doing(Effect.Move(target("spell"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("spell", and(spellFilter, manaValue(Comparison.AtMost, 4))))),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            Step.May(listOf(doing(Effect.Cast(Objects.Remembered("exiled"), withoutPaying = true, caster = Players.OwnerOf(Objects.Remembered("exiled")),
                how = PlayKind.Cast))), player = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 508.1d: attacks each combat if able; CR 502.3: doesn't untap; CR 701.26b untap
    @Test
    fun galvanicJuggernaut() = card(
        name = "Galvanic Juggernaut", cost = "{4}", type = "Artifact Creature — Juggernaut", pt = "5/5",
        text = """
            This creature attacks each combat if able.
            This creature doesn't untap during your untap step.
            Whenever another creature dies, untap this creature.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.Self)),
        triggered(dies(and(another, creature)), untapIt(Objects.Self)),
    )

    // CR 122.1: a counter on each creature you control
    @Test
    fun catharsCrusade() = card(
        name = "Cathars' Crusade", cost = "{3}{W}{W}", type = "Enchantment",
        text = "Whenever a creature you control enters, put a +1/+1 counter on each creature you control.",
    ) parsesTo abilities(
        triggered(enters(creatureYouControl), counters(Objects.Each(creatureYouControl))),
    )

    // CR 602.1, 118.1: sacrifice as a cost
    @Test
    fun indulgentAristocrat() = card(
        name = "Indulgent Aristocrat", cost = "{B}", type = "Creature — Vampire Noble", pt = "1/1",
        text = """
            Lifelink
            {2}, Sacrifice a creature: Put a +1/+1 counter on each Vampire you control.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(cost(mana("{2}"), sacrificeA(creature)), counters(Objects.Each(and(sub("Vampire"), you)))),
    )

    // CR 700.4: dies; any creature including this one and opponents'
    @Test
    fun lumberknot() = card(
        name = "Lumberknot", cost = "{2}{G}{G}", type = "Creature — Treefolk", pt = "1/1",
        text = """
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            Whenever a creature dies, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        triggered(dies(creature), counters(Objects.Self)),
    )

    // CR 613.1f, 122.1: a condition on the counters of this creature
    @Test
    fun voiceOfTheBlessed() = card(
        name = "Voice of the Blessed", cost = "{W}{W}", type = "Creature — Spirit Cleric", pt = "2/2",
        text = """
            Whenever you gain life, put a +1/+1 counter on this creature.
            As long as this creature has four or more +1/+1 counters on it, it has flying and vigilance.
            As long as this creature has ten or more +1/+1 counters on it, it has indestructible.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self)),
        static(has(Keyword.Flying, Keyword.Vigilance), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 4))),
        static(has(Keyword.Indestructible), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 10))),
    )

    // CR 701.21a: the controller chooses a creature other than this one
    @Test
    fun demonicTaskmaster() = card(
        name = "Demonic Taskmaster", cost = "{2}{B}", type = "Creature — Demon", pt = "4/3",
        text = """
            Flying
            At the beginning of your upkeep, sacrifice a creature other than this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Choose(Choice.Objects(and(creature, another, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))),
    )

    // CR 305.2: additional land play; CR 701.21a sacrifice unless
    @Test
    fun theGitrogMonster() = card(
        name = "The Gitrog Monster", cost = "{3}{B}{G}", type = "Legendary Creature — Frog Horror", pt = "6/6",
        text = """
            Deathtouch
            At the beginning of your upkeep, sacrifice The Gitrog Monster unless you sacrifice a land.
            You may play an additional land on each of your turns.
            Whenever one or more land cards are put into your graveyard from anywhere, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Pay(cost(sacrificeA(land)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), sacrificeIt)),
        Ability.Static(listOf(Mechanic.ExtraLandPlays(1)), affects = Affected.Players(Players.You)),
        Ability.Triggered(Trigger.On(EventPattern.ZoneChange(and(land, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard), oneOrMore = true),
            Instructions(steps = listOf(draw(1)))),
    )

    // CR 701.23a: search, reveal, put into hand, then shuffle (CR 701.24)
    @Test
    fun travelersAmulet() = card(
        name = "Traveler's Amulet", cost = "{1}", type = "Artifact",
        text = "{1}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeSelf), search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    @Test
    fun traverseTheUlvenwald() = card(
        name = "Traverse the Ulvenwald", cost = "{G}", type = "Sorcery",
        text = """
            Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Delirium — If there are four or more card types among cards in your graveyard, instead search your library for a creature or land card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(Step.If(
            condition = Condition.Compare(Value.CountDistinct(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), SharedQuality.CardType),
                Comparison.AtLeast, n(4)),
            then = listOf(search(or(creature, land), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            otherwise = listOf(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        ))), abilityWord = "Delirium"),
    )

    @Test
    fun wildFieldScarecrow() = card(
        name = "Wild-Field Scarecrow", cost = "{3}", type = "Artifact Creature — Scarecrow", pt = "1/4",
        text = """
            Defender
            {2}, Sacrifice this creature: Search your library for up to two basic land cards, reveal them, put them into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{2}"), sacrificeSelf), search(basicLand(), max = 2, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    @Test
    fun wretchedThrong() = card(
        name = "Wretched Throng", cost = "{1}{U}", type = "Creature — Zombie Horror", pt = "2/1",
        text = "When this creature dies, you may search your library for a card named Wretched Throng, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        triggered(diesSelf, Step.May(listOf(search(ObjectFilter.Named("Wretched Throng"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You)),
    )

    // CR 604.3: a characteristic-defining ability sets power and toughness
    @Test
    fun crusaderOfOdric() = card(
        name = "Crusader of Odric", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "*/*",
        text = "Crusader of Odric's power and toughness are each equal to the number of creatures you control.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(creatureYouControl), count(creatureYouControl)), characteristicDefining = true),
    )

    // CR 701.26a: tap target creature that isn't a Human
    @Test
    fun avacynianPriest() = card(
        name = "Avacynian Priest", cost = "{1}{W}", type = "Creature — Human Cleric", pt = "1/2",
        text = "{1}, {T}: Tap target non-Human creature.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", and(not(sub("Human")), creature)))),
    )

    // CR 603.2: this creature or another Spirit you control entering
    @Test
    fun nebelgastHerald() = card(
        name = "Nebelgast Herald", cost = "{2}{U}", type = "Creature — Spirit", pt = "2/1",
        text = """
            Flash
            Flying
            Whenever this creature or another Spirit you control enters, tap target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Spirit"), you))), tapIt(target("creature")),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 508.1: an attack trigger; the controller may tap
    @Test
    fun niblisOfTheUrn() = card(
        name = "Niblis of the Urn", cost = "{1}{W}", type = "Creature — Spirit", pt = "1/1",
        text = """
            Flying
            Whenever this creature attacks, you may tap target creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, Step.May(listOf(tapIt(target("creature"))), player = Players.You), targets = listOf(slot("creature", creature))),
    )

    // CR 701.26b, 613.4c: untap, then +2/+2 and reach until end of turn
    @Test
    fun aimHigh() = card(
        name = "Aim High", cost = "{1}{G}", type = "Instant",
        text = "Untap target creature. It gets +2/+2 and gains reach until end of turn.",
    ) parsesTo abilities(
        spell(untapIt(target("creature")), pump(target("creature"), 2, 2, Mechanic.GainAbilities(listOf(Keyword.Reach))), targets = listOf(slot("creature", creature))),
    )
}
