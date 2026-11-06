package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.CardTypeRef
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 02: white cards, second part: affinity for artifacts (CR 702.41), "that many" counters once each turn (CR 603.2h),
 * phasing out until a permanent leaves (CR 702.26, 610.3) with vanishing without a number (CR 702.63b), forecast (CR 702.57), converge
 * (CR 207.2c), suspend (CR 702.62), protection from two colours (CR 702.16g) with an exile replacement (CR 614.1a), a token count from an
 * exiled card's toughness, a card type chosen as it enters (CR 614.12) for protection of you and your creatures, an Aura's ability in
 * two zones (CR 113.6b), evoke (CR 702.74), typecycling and eternalize (CR 702.29e, 702.129) and entwine (CR 702.42).
 */
class Mh2Chunk02WhiteTest {
    // CR 702.41a: {1} less for each artifact you control
    @Test
    fun glintingGlare() = card(
        name = "Glinting Glare",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Glinting Glare deals 5 damage to target attacking or blocking creature.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(artifact, "artifacts"),
        spell(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", attackingOrBlockingCreature))),
    )

    @Test
    fun alabasterGargoyle() = card(
        name = "Alabaster Gargoyle",
        cost = "{2}{W}",
        type = "Artifact Creature — Gargoyle",
        text = """
            Flying
            {W}: This creature gets +0/+1 until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Flying, activated(cost(mana("{W}")), pump(Objects.Self, 0, 1)))

    // CR 603.2h: "Do this only once each turn" is checked as it would trigger; "that many" is the life gained
    @Test
    fun nyxbornExemplar() = card(
        name = "Nyxborn Exemplar",
        cost = "{4}{W}{W}",
        type = "Enchantment Creature — Human Soldier",
        text = "Whenever you gain life, you may put that many +1/+1 counters on each creature you control. Do this only once each turn.",
        pt = "4/6",
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)),
            Step.May(listOf(counters(Objects.Each(creatureYouControl), Value.EventAmount)), player = Players.You), actionOncePerTurn = true),
    )

    // CR 702.26: phased-out permanents are treated as though they don't exist; these phase in only when it leaves (CR 610.3);
    // CR 702.63b: vanishing without a number
    @Test
    fun untimelyStasis() = card(
        name = "Untimely Stasis",
        cost = "{1}{W}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, untap all creatures, then those creatures phase out until this enchantment leaves the battlefield. Put a time counter on this enchantment for each creature that phased out this way.
            Vanishing (At the beginning of your upkeep, remove a time counter from this enchantment. When the last is removed, sacrifice it.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Bind(Objects.Each(creature), "creatures"),
            untapIt(Objects.Bound("creatures")),
            doing(Effect.PhaseOut(Objects.Bound("creatures"), untilLeaves = Objects.Self, bind = "phased")),
            counters(Objects.Self, Value.Count(Objects.Bound("phased")), CounterKind.Time),
        ),
        Keyword.Vanishing(0),
    )

    // CR 702.57b: a forecast ability is activated from the hand, only during its owner's upkeep and only once each turn
    @Test
    fun lancingSunbeams() = card(
        name = "Lancing Sunbeams",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Exile target tapped creature.
            Forecast — {2}{W}, Reveal this card from your hand: Tap target untapped creature. (Activate this ability only during your upkeep and only once each turn.)
        """,
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(tapped, creature)))),
        activated(cost(mana("{2}{W}"), CostPart.Reveal(CostObjects.Self)), tapIt(target("creature")),
            targets = listOf(slot("creature", and(untapped, creature))), label = arcanum.api.AbilityLabel.Forecast),
    )

    // CR 207.2c: converge is an ability word; the number of colours of mana spent to cast it (CR 601.2h)
    @Test
    fun chromaticClosure() = card(
        name = "Chromatic Closure",
        cost = "{X}{W}",
        type = "Sorcery",
        text = "Converge — Exile target nonland permanent if its mana value is less than or equal to the number of colors of mana spent to cast this spell.",
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(nonland, permanentFilter)))),
                steps = listOf(Step.If(
                    Condition.ObjectMatches(target("permanent"), ObjectFilter.ManaValue(Comparison.AtMost, Value.ManaSpent(distinctColors = true, of = Objects.Self))),
                    then = listOf(move(target("permanent"), Zone.Exile)),
                )),
                announced = listOf("X"),
            ),
            abilityWord = "Converge",
        ),
    )

    // CR 702.62a: suspend; a card with no mana cost can be cast only some other way (CR 202.1b)
    @Test
    fun rekindledConviction() = card(
        name = "Rekindled Conviction",
        cost = null,
        type = "Sorcery",
        text = """
            Suspend 2—{1}{W} (Rather than cast this card from your hand, pay {1}{W} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)
            Return all enchantment cards from your graveyard to the battlefield. (Auras with nothing to enchant remain in your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Suspend(2, manaCost("{1}{W}")),
        spell(move(Objects.Each(inYourGraveyard(enchantment)), Zone.Battlefield)),
    )

    // CR 702.16g: protection from two qualities is two protection abilities; CR 614.1a: an exile replacement for any black or red object
    @Test
    fun purifierEnDal() = card(
        name = "Purifier en-Dal",
        cost = "{W}{W}",
        type = "Creature — Human Cleric",
        text = """
            Protection from black and from red
            When this creature enters, exile all cards that are black or red from all graveyards.
            If a black or red permanent, spell, or card not on the battlefield would be put into a graveyard, exile it instead.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Protection(ObjectFilter.OfColor(Color.Black)),
        Keyword.Protection(ObjectFilter.OfColor(Color.Red)),
        triggered(entersSelf, move(Objects.Each(and(cardFilter, or(ObjectFilter.OfColor(Color.Black), ObjectFilter.OfColor(Color.Red)), inZone(Zone.Graveyard))), Zone.Exile)),
        static(Mechanic.Replacement(EventPattern.ZoneChange(or(ObjectFilter.OfColor(Color.Black), ObjectFilter.OfColor(Color.Red)), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
    )

    // CR 608.2h: the exiled card's toughness as the spell resolves
    @Test
    fun combTheDunes() = card(
        name = "Comb the Dunes",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Exile target creature card from your graveyard. Create X 1/1 white Bird creature tokens with flying, where X is the exiled card's toughness.",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Exile, postBind = "exiled"),
            create(creatureToken("Bird", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying)),
                count = Value.Of(NumericProperty.Toughness, Objects.Bound("exiled"), Combine.Single)),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 701.16a investigate
    @Test
    fun sweepTheGrounds() = card(
        name = "Sweep the Grounds",
        cost = "{3}{W}",
        type = "Enchantment",
        text = "Whenever a creature attacks you or a planeswalker you control, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(creature, target = RecipientFilter(objects = and(planeswalker, you), players = PlayerFilter.You))),
            doing(Effect.Action(KeywordAction.Investigate(n(1))))),
    )

    // CR 614.12: the card type is chosen as it enters; CR 702.16: protection for you (CR 702.16k) and your creatures
    @Test
    fun serrasHerald() = card(
        name = "Serra's Herald",
        cost = "{4}{W}{W}{W}",
        type = "Creature — Angel",
        text = """
            Flying
            As this creature enters, choose a card type.
            You and creatures you control have protection from the chosen card type.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(arcanum.api.Choice.CardType(), bind = "type", chooser = Players.You, remember = true)))))),
        playerStatic(has(Keyword.Protection(ObjectFilter.Type(CardTypeRef.Remembered("type"))))),
        static(has(Keyword.Protection(ObjectFilter.Type(CardTypeRef.Remembered("type")))), affects = Objects.Each(creatureYouControl)),
    )

    // CR 113.6b: the ability works in the zones its text names
    @Test
    fun skyknightsGift() = card(
        name = "Skyknight's Gift",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has flying.
            {2}{W}: Return Skyknight's Gift to its owner's hand. Activate only if Skyknight's Gift is on the battlefield or in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(1, 1), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
        activated(cost(mana("{2}{W}")), move(Objects.Self, Zone.Hand),
            rules = anyTime.copy(condition = Condition.ObjectMatches(Objects.Self, or(inZone(Zone.Battlefield), inZone(Zone.Graveyard)))))
            .copy(functionsIn = listOf(Zone.Battlefield, Zone.Graveyard)),
    )

    // CR 702.74a evoke with a cost that isn't mana
    @Test
    fun quietude() = card(
        name = "Quietude",
        cost = "{3}{W}{W}",
        type = "Creature — Elemental Incarnation",
        text = """
            Flash
            Lifelink
            When this creature enters, exile up to one other target creature. That creature's controller gains life equal to its power.
            Evoke—Exile a white card from your hand.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Lifelink,
        triggered(entersSelf,
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            doing(Effect.GainLife(Players.ControllerOf(target("creature")), Value.Of(NumericProperty.Power, target("creature"), Combine.Single))),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(another, creature)), min = 0))),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(Color.White), count = n(1)), from = Zone.Hand)))),
    )

    @Test
    fun spiritOfPassage() = card(
        name = "Spirit of Passage",
        cost = "{5}{W}{W}",
        type = "Creature — Elemental",
        text = """
            Flying
            When this creature enters, create two 1/1 white Bird creature tokens with flying.
            Evoke {3}{W} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(creatureToken("Bird", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying)), count = n(2))),
        Keyword.Evoke(manaCost("{3}{W}")),
    )

    @Test
    fun thrabenOverseer() = card(
        name = "Thraben Overseer",
        cost = "{2}{W}{W}",
        type = "Creature — Angel",
        text = """
            Flying, vigilance
            Other nontoken creatures you control get +1/+1 and have vigilance.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(pt(1, 1), has(Keyword.Vigilance), affects = Objects.Each(and(another, not(token), creature, you))),
    )

    // CR 702.29e typecycling; CR 702.129a eternalize
    @Test
    fun agelessWyrm() = card(
        name = "Ageless Wyrm",
        cost = "{3}{W}{W}",
        type = "Creature — Dragon",
        text = """
            Flying
            Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
            Eternalize {2}{W}{W} ({2}{W}{W}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Dragon with no mana cost. Eternalize only as a sorcery.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Typecycling(sub("Plains"), "Plainscycling", manaCost("{2}")),
        Keyword.Eternalize(manaCost("{2}{W}{W}")),
    )

    // CR 702.42a entwine; CR 701.34a proliferate
    @Test
    fun boundlessPromise() = card(
        name = "Boundless Promise",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Put a +1/+1 counter on each of up to two target creatures.
            • Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
            Entwine {3}{W} (Choose both if you pay the entwine cost.)
        """,
    ) parsesTo abilities(
        modal(
            mode(counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
            mode(doing(Effect.Action(KeywordAction.Proliferate))),
        ),
        Keyword.Entwine(manaCost("{3}{W}")),
    )
}
