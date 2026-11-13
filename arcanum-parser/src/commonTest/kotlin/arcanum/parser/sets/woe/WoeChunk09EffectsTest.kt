package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 09: counters, tokens, damage, Auras and other one-off effects. */
class WoeChunk09EffectsTest {
    private val aura = sub("Aura")
    private val smallerCantBlock: Ability = static(Mechanic.CantBeBlockedBy(and(creature, ObjectFilter.Power(Comparison.Less, power(Objects.Self)))))
    private val yourCreatureDied = EventPattern.ZoneChange(and(creature, you), from = Zone.Battlefield, to = Zone.Graveyard)

    // CR 614.1c (enters with counters), 700.4 (died: put into a graveyard from the battlefield); CR 115.3 ("any other target": not the first
    // target), 701.21a (sacrifice)
    @Test
    fun callousSellSword() = cardFaces(Layout.Adventure,
        card(name = "Callous Hireblade", cost = "{1}{B}", type = "Creature — Human Soldier", pt = "2/2",
            text = "This creature enters with a +1/+1 counter on it for each creature that died under your control this turn.",
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, Value.EventsThisTurn(yourCreatureDied, Measure.Objects))))),
                affects = Affected.Objects(Objects.Self)),
        ),
        card(name = "Fused Ruin", cost = "{R}", type = "Sorcery — Adventure",
            text = "Target creature you control deals damage equal to its power to any other target. Then sacrifice it.",
        ) to abilities(
            spell(
                damage(power(target("creature")), toAny, source = target("creature")),
                doing(Effect.Sacrifice(target("creature"))),
                targets = listOf(slot("creature", creatureYouControl), anyTarget.copy(distinctFrom = listOf("creature"))),
            ),
        ),
    )

    // CR 702.108a (prowess); CR 601.2d (divided as it is cast, each target at least one), 107.3a (X)
    @Test
    fun elusiveOtter() = cardFaces(Layout.Adventure,
        card(name = "Slippery Weasel", cost = "{U}", type = "Creature — Weasel", pt = "1/1",
            text = """
                Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
                Creatures with power less than this creature's power can't block it.
            """,
        ) to abilities(
            Keyword.Prowess,
            smallerCantBlock,
        ),
        card(name = "Orchard Gift", cost = "{X}{G}", type = "Sorcery — Adventure",
            text = "Distribute X +1/+1 counters among any number of target creatures you control. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(
                counters(target("creatures"), Value.DividedShare),
                targets = listOf(slot("creatures", creatureYouControl, min = 0, max = null).copy(divided = Value.Announced("X"))),
                announced = listOf("X"),
            ),
        ),
    )

    // CR 508.1m / 603.4 ("attacks while": checked as it triggers); CR 601.2d
    @Test
    fun picnicRuiner() = cardFaces(Layout.Adventure,
        card(name = "Feast Wrecker", cost = "{1}{R}", type = "Creature — Goblin Rogue", pt = "2/2",
            text = "Whenever this creature attacks while you control a creature with power 4 or greater, this creature gains double strike until end of turn.",
        ) to abilities(
            triggered(attacksSelf, gains(Objects.Self, Keyword.DoubleStrike),
                triggersOnlyIf = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1)),
        ),
        card(name = "Pilfered Treats", cost = "{3}{G}", type = "Sorcery — Adventure",
            text = "Distribute three +1/+1 counters among any number of target creatures you control.",
        ) to abilities(
            spell(
                counters(target("creatures"), Value.DividedShare),
                targets = listOf(slot("creatures", creatureYouControl, min = 0, max = null).copy(divided = n(3))),
            ),
        ),
    )

    // CR 603.10a (the creature as it last existed), 120.3 (all damage dealt to it this turn), 111.4 (token with an ability)
    @Test
    fun tangledColony() = card(
        name = "Snarled Rat Nest",
        cost = "{1}{B}",
        type = "Creature — Rat",
        pt = "3/2",
        text = """
            This creature can't block.
            When this creature dies, create X 1/1 black Rat creature tokens with "This token can't block," where X is the amount of damage dealt to it this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(diesSelf, doing(Effect.CreateToken(
            creatureToken("Rat", listOf(Color.Black), 1, 1, abilities = listOf(static(Mechanic.CantBlock(ObjectFilter.Any)))),
            Value.EventsThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.EventObject(EventRole.Before)))),
                Measure.Amount),
            Players.You))),
    )

    // CR 714 (Saga chapters), 707.9a (a copy with exceptions), 707.9b (an added type), 701.21a
    @Test
    fun theApprenticesFolly() = card(
        name = "The Pupil's Blunder",
        cost = "{2}{U}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Choose target nontoken creature you control that doesn't have the same name as a token you control. Create a token that's a copy of it, except it isn't legendary, is a Reflection in addition to its other types, and has haste.
            III — Sacrifice all Reflections you control.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2),
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("token"), except = listOf(
                CopyException.Types(removeSupertypes = listOf(Supertype.Legendary), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Reflection")))),
                CopyException.AddAbilities(listOf(Keyword.Haste)))), n(1), Players.You)),
            targets = listOf(slot("token", and(not(token), creature, you, not(ObjectFilter.SameNameAs(Objects.Each(and(token, you)))))))),
        chapter(3, doing(Effect.Sacrifice(Objects.Each(and(sub("Reflection"), you))))),
    )

    // CR 107.3a (X defined by the text), 715.2a (cards that have an Adventure)
    @Test
    fun franticFirebolt() = card(
        name = "Hasty Cinderbolt",
        cost = "{2}{R}",
        type = "Instant",
        text = "Hasty Cinderbolt deals X damage to target creature, where X is 2 plus the number of cards in your graveyard that are instant cards, sorcery cards, and/or have an Adventure.",
    ) parsesTo abilities(
        spell(
            damage(Value.Sum(listOf(n(2), count(and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                or(instant, sorcery, ObjectFilter.HasAdventure))))), toObjects(target("creature"))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 120.3 (each creature deals its own damage), 115.3 ("other target": not one of the first targets)
    @Test
    fun gracefulTakedown() = card(
        name = "Elegant Downfall",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Any number of target enchanted creatures you control and up to one other target creature you control each deal damage equal to their power to target creature you don't control.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Objects(target("creatures")), bind = "dealer", steps = listOf(
                damage(power(Objects.Bound("dealer")), toObjects(target("second creature")), source = Objects.Bound("dealer")))),
            damage(power(target("creature")), toObjects(target("second creature")), source = target("creature")),
            targets = listOf(
                slot("creatures", and(ObjectFilter.Attached(attachments = aura), creature, you), min = 0, max = null),
                slot("creature", creatureYouControl, min = 0).copy(distinctFrom = listOf("creatures")),
                slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 613.4b (layer 7b: base power and toughness), 613.1f (layer 6), 303.4 (enchanted by an Aura)
    @Test
    fun archonOfTheWildRose() = card(
        name = "Archon of the Thorned Bloom",
        cost = "{2}{W}{W}",
        type = "Creature — Archon",
        pt = "4/4",
        text = """
            Flying
            Other creatures you control that are enchanted by Auras you control have base power and toughness 4/4 and have flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.Flying),
            affects = Objects.Each(and(another, creature, you, ObjectFilter.Attached(attachments = and(aura, you))))),
    )

    // CR 613.4c (layer 7c, counted continuously), 301.5 / 303.4 (attached Equipment and Auras)
    @Test
    fun kellanTheFaeBlooded() = cardFaces(Layout.Adventure,
        card(name = "Arlen, the Fey-Touched", cost = "{2}{R}", type = "Legendary Creature — Human Faerie", pt = "2/2",
            text = """
                Double strike
                Other creatures you control get +1/+0 for each Aura and Equipment attached to Arlen.
            """,
        ) to abilities(
            Keyword.DoubleStrike,
            static(Mechanic.ModifyPowerToughness(Value.Count(Objects.AttachmentsOf(Objects.Self, or(aura, equipment))), n(0)),
                affects = Objects.Each(and(another, creature, you))),
        ),
        card(name = "Heirloom Gift", cost = "{1}{W}", type = "Sorcery — Adventure",
            text = "Search your library for an Aura or Equipment card, reveal it, put it into your hand, then shuffle.",
        ) to abilities(
            spell(search(or(aura, equipment), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        ),
    )

    // CR 603.12 (a reflexive trigger only when cards were exiled), 611.2c (the affected creatures fixed as it resolves)
    @Test
    fun specterOfMortality() = card(
        name = "Wraith of Endings",
        cost = "{3}{B}{B}",
        type = "Creature — Specter",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, you may exile one or more creature cards from your graveyard. When you do, each other creature gets -X/-X until end of turn, where X is the number of cards exiled this way.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.May(listOf(
                Step.Choose(Choice.Objects(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = n(1), max = null), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled")), Players.You),
            ifThen(Condition.NonEmpty("exiled"), reflexive(
                untilEndOfTurn(Objects.Each(and(another, creature)), Mechanic.ModifyPowerToughness(
                    Value.Times(Value.Count(Objects.Bound("exiled")), -1), Value.Times(Value.Count(Objects.Bound("exiled")), -1))))),
        ),
    )

    // CR 120.2b (noncombat damage), 603.2 (once for each such event; "that many" is the damage dealt), 305.1 (play: lands too)
    @Test
    fun virtueOfCourage() = cardFaces(Layout.Adventure,
        card(name = "Virtue of Valor", cost = "{3}{R}{R}", type = "Enchantment",
            text = "Whenever a source you control deals noncombat damage to an opponent, you may exile that many cards from the top of your library. You may play those cards this turn.",
        ) to abilities(
            triggered(on(EventPattern.Damage(you, RecipientFilter(players = PlayerFilter.Opponent), combat = false)),
                Step.May(listOf(move(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), Zone.Exile, postBind = "exiled")), Players.You),
                untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You))),
        ),
        card(name = "Ember Flare", cost = "{1}{R}", type = "Instant — Adventure",
            text = "Ember Flare deals 2 damage to any target. (Then exile this card. You may cast the enchantment later from exile.)",
        ) to abilities(
            spell(damage(2, toAny), targets = listOf(anyTarget)),
        ),
    )
}
