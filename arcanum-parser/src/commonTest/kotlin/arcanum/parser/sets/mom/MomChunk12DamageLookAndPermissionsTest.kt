package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.ManaCost
import arcanum.api.Modification
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 12: damage from targets and to battles, looking and exiling with a permission, preventing, discarding and gaining abilities. */
class MomChunk12DamageLookAndPermissionsTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val creaturePlaneswalkerOrBattle = or(creature, planeswalker, battle)
    private val handOfYou = listOf(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You)))

    /** "another target <noun>" after a first target: a different object (CR 115.3) */
    private fun anotherSlot(name: String, filter: ObjectFilter, from: String) =
        TargetSlot(name, TargetKind.Objects(filter), min = n(1), max = n(1), distinctFrom = listOf(from))

    // CR 120.3 (a creature deals damage equal to its power), 115.3 (another target)
    @Test
    fun starvedLunge() = card(
        name = "Starved Lunge",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to another target creature, planeswalker, or battle.",
    ) parsesTo abilities(
        spell(
            damage(power(target("creature")), toObjects(target("permanent")), target("creature")),
            targets = listOf(slot("creature", creatureYouControl), anotherSlot("permanent", creaturePlaneswalkerOrBattle, from = "creature")),
        ),
    )

    // CR 101.2 (can't be countered), 608.2b (the target's colours as the spell resolves), 105.2
    @Test
    fun runestoneVolley() = card(
        name = "Runestone Volley",
        cost = "{R}",
        type = "Sorcery",
        text = """
            This spell can't be countered.
            Runestone Volley deals 1 damage to target creature or planeswalker. It deals 5 damage instead if that target is white and/or blue.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        spell(
            ifThen(
                Condition.ObjectMatches(target("permanent"), or(ObjectFilter.OfColor(Color.White), ObjectFilter.OfColor(Color.Blue))),
                damage(5, toObjects(target("permanent"))),
                otherwise = listOf(damage(1, toObjects(target("permanent")))),
            ),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )

    // CR 115.4 (target player or battle), 310.6 (damage to a battle)
    @Test
    fun cragbornSpearman() = card(
        name = "Cragborn Spearman",
        cost = "{4}{R}",
        type = "Creature — Ogre Spirit",
        pt = "5/4",
        text = """
            Reach
            {T}: This creature deals 2 damage to target player or battle.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(
            cost(tap),
            damage(2, Recipients(target = "player or battle")),
            targets = listOf(TargetSlot("player or battle", TargetKind.Either(battle, PlayerFilter.Any), min = n(1), max = n(1))),
        ),
    )

    // CR 510.2 (that much: the combat damage dealt), 310.8 (the battle's protector), 115.1
    @Test
    fun stampedingRaptorhorn() = card(
        name = "Stampeding Raptorhorn",
        cost = "{2}{R}{R}",
        type = "Creature — Dinosaur",
        pt = "4/4",
        text = """
            Trample, haste
            {2}{R}: This creature gets +2/+0 until end of turn.
            Whenever this creature deals combat damage to an opponent, it deals that much damage to target planeswalker that player controls or battle that player protects.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        activated(manaCost("{2}{R}"), pump(Objects.Self, 2, 0)),
        triggered(
            on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Opponent), combat = true)),
            damage(Value.EventAmount, toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(
                and(planeswalker, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))),
                and(battle, ObjectFilter.Where(Condition.PlayerMatches(Players.ProtectorOf(Objects.Tested), PlayerFilter.Is(Players.EventPlayer)))),
            ))),
        ),
    )

    // CR 702.11a (hexproof), 615.1a (a prevention effect), 120.1 (a source of damage)
    @Test
    fun waveOfSanctuary() = card(
        name = "Wave of Sanctuary",
        cost = "{W}",
        type = "Instant",
        text = "You and permanents you control gain hexproof until end of turn. Prevent all damage that black and/or red sources would deal to creatures you control this turn.",
    ) parsesTo abilities(
        spell(
            // a player with hexproof can't be the target of opponents' spells or abilities (CR 702.11d)
            doing(Effect.ApplyMechanics(listOf(Mechanic.CantBeTargeted(by = opponents)), Affected.Players(Players.You), Duration.EndOfTurn)),
            gains(Objects.Each(and(permanentFilter, you)), Keyword.Hexproof),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(
                or(ObjectFilter.OfColor(Color.Black), ObjectFilter.OfColor(Color.Red)), RecipientFilter(objects = creatureYouControl)))), null, Duration.EndOfTurn)),
        ),
    )

    // CR 120.1 (each creature is the source of its own damage), 115.3 (another target), 613.4c
    @Test
    fun pairedAmbush() = card(
        name = "Paired Ambush",
        cost = "{1}{G}{G}",
        type = "Instant",
        text = "Up to two target creatures you control each get +1/+0 until end of turn. They each deal damage equal to their power to another target creature, planeswalker, or battle.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creatures"), pt(1, 0)),
            Step.ForEach(
                each = arcanum.api.Each.Objects(target("creatures")),
                bind = "source",
                steps = listOf(doing(Effect.DealDamage(
                    to = toObjects(target("permanent")),
                    amount = Value.Of(NumericProperty.Power, Objects.Bound("source"), Combine.Single),
                    source = Objects.Bound("source"),
                ))),
            ),
            targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2), anotherSlot("permanent", creaturePlaneswalkerOrBattle, from = "creatures")),
        ),
    )

    // CR 701.9a (discarding any number: none is fine), 603.12 (a reflexive trigger), 401.4 (third from the top)
    @Test
    fun grumblorAndPip() = card(
        name = "Grumblor and Pip",
        cost = "{2}{G}{U}{R}",
        type = "Legendary Creature — Cyclops Homunculus",
        pt = "6/5",
        text = """
            Whenever Grumblor and Pip enters or attacks, draw a card, then you may discard any number of land cards. When you discard one or more cards this way, Grumblor and Pip deals twice that much damage to target creature.
            {1}{U}: Put Grumblor and Pip into its owner's library third from the top.
        """,
    ) parsesTo abilities(
        triggered(
            entersOrAttacksSelf,
            draw(1),
            Step.Choose(Choice.Objects(and(land, *handOfYou.toTypedArray()), n(0), null), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
            ifThen(
                Condition.Compare(Value.Count(Objects.Bound("discarded")), Comparison.AtLeast, n(1)),
                doing(Effect.ReflexiveTrigger(Instructions(
                    targets = listOf(slot("creature", creature)),
                    steps = listOf(damage(Value.Times(Value.Count(Objects.Bound("discarded")), 2), toObjects(target("creature")))),
                ))),
            ),
        ),
        activated(manaCost("{1}{U}"), move(Objects.Self, Zone.Library, position = 2)),
    )

    // CR 613.1f (gaining abilities), 602.5 (can't be activated), 609.4b (spend mana as though any colour)
    @Test
    fun vessaAndOrinel() = card(
        name = "Vessa and Orinel",
        cost = "{1}{W}{W}{B}",
        type = "Legendary Creature — Vampire Angel",
        pt = "3/4",
        text = """
            Flying, vigilance
            Activated abilities of creatures your opponents control can't be activated.
            Vessa and Orinel has all activated abilities of all creatures your opponents control. You may spend mana as though it were mana of any color to activate those abilities.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        playerStatic(Mechanic.CantActivate(ObjectFilter.AbilityOf(source = or(creatureOpponentControls))), players = Players.Each(PlayerFilter.Any)),
        static(Mechanic.GainAbilitiesOf(Objects.Each(creatureOpponentControls))),
        playerStatic(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = ObjectFilter.Is(Objects.Self)))),
    )

    // CR 401.4 (the rest on the bottom in a random order), 601.2a (a permission to cast), 118.9 (without paying its mana cost)
    @Test
    fun tamekAndRuhal() = card(
        name = "Tamek and Ruhal",
        cost = "{2}{R}{R}{W}",
        type = "Legendary Creature — Human God",
        pt = "5/4",
        text = """
            As long as you have one or fewer cards in hand, Tamek and Ruhal has vigilance and haste.
            Whenever Tamek and Ruhal attacks, look at the top six cards of your library. You may exile a legendary creature card from among them. Put the rest on the bottom of your library in a random order. Until end of turn, you may cast the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.GainAbilities(listOf(Keyword.Vigilance, Keyword.Haste)),
            condition = Condition.Not(Condition.Exists(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 2)),
        ),
        triggered(
            attacksSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(legendary, creature), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
        ),
    )

    /** "Create N token copies of another target creature you control. It gains haste (they gain haste). Sacrifice it (them) at the beginning of the
     * next end step." (CR 707.2, 603.7) */
    private fun copiesWithHaste(count: Int) = listOf(
        doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(count), Players.You, bind = "token")),
        doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("token")), Duration.Indefinitely)),
        delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("token")))),
    )

    // CR 707.2 (a token copy), 603.7 (a delayed trigger), 602.5d (as a sorcery), 115.3
    @Test
    fun varsanHeroOfEmberdeep() = card(
        name = "Varsan, Hero of Emberdeep",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/3",
        text = """
            {1}{R}, {T}: Create a token that's a copy of another target creature you control. It gains haste. Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
            {6}{R}{R}{R}, {T}: Create five tokens that are copies of another target creature you control. They gain haste. Sacrifice them at the beginning of the next end step. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{R}"), tap), *copiesWithHaste(1).toTypedArray(), targets = listOf(slot("creature", and(another, creatureYouControl))), rules = asSorcery),
        activated(cost(mana("{6}{R}{R}{R}"), tap), *copiesWithHaste(5).toTypedArray(), targets = listOf(slot("creature", and(another, creatureYouControl))), rules = asSorcery),
    )

    // CR 613.4c (both changes apply to green and white creatures)
    @Test
    fun gleamingDownpour() = card(
        name = "Gleaming Downpour",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = "All creatures get -1/-1 until end of turn. Creatures that are green and/or white get an additional -2/-2 until end of turn.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Each(creature), pt(-1, -1)),
            untilEndOfTurn(Objects.Each(and(creature, or(ObjectFilter.OfColor(Color.Green), ObjectFilter.OfColor(Color.White)))), pt(-2, -2)),
        ),
    )

    // CR 120.4a (excess damage), 401.4, 601.2a / 305.1 (a permission to play the card this turn)
    @Test
    fun kelvarasForgecraft() = card(
        name = "Kelvara's Forgecraft",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = "Kelvara's Forgecraft deals 5 damage to target creature, planeswalker, or battle. Look at the top X cards of your library, where X is the excess damage dealt this way. You may exile one of those cards. Put the rest on the bottom of your library in a random order. You may play the exiled card this turn.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toObjects(target("permanent")), n(5), Objects.Self, bind = "damage")),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Excess("damage"), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            targets = listOf(slot("permanent", creaturePlaneswalkerOrBattle)),
        ),
    )

    // CR 400.7 (it: the exiled card), 202.3 (its mana value), 608.2g / 118.9 (cast while the ability resolves, without paying)
    @Test
    fun oboroAndSenka() = card(
        name = "Oboro and Senka",
        cost = "{2}{U}{U}{B}",
        type = "Legendary Creature — Ogre Demon Dragon",
        pt = "5/4",
        text = """
            Flying
            When Oboro and Senka enters, draw three cards, then put two cards from your hand on top of your library in any order.
            When Oboro and Senka dies, exile the top card of your library. Target opponent loses life equal to its mana value. If it's an instant or sorcery card, you may cast it without paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            draw(3),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(2), n(2)), bind = "put", chooser = Players.You),
            move(Objects.Bound("put"), Zone.Library, position = 0),
        ),
        triggered(
            diesSelf,
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.LoseLife(Players.Target("opponent"), Value.Of(NumericProperty.ManaValue, Objects.Bound("exiled"), Combine.Single))),
            ifThen(
                Condition.ObjectMatches(Objects.Bound("exiled"), or(instant, sorcery)),
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You),
            ),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 509.1c (must be blocked if able), 205.1b (in addition to its other types), 611.2a
    @Test
    fun wrathfulSoil() = card(
        name = "Wrathful Soil",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature or land you control becomes a 4/4 Elemental creature with haste in addition to its other types until end of turn. It must be blocked this turn if able.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(
                target("permanent"),
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype("Elemental")))),
                Mechanic.SetPowerToughness(n(4), n(4)),
                Mechanic.GainAbilities(listOf(Keyword.Haste)),
            ),
            untilEndOfTurn(target("permanent"), Mechanic.MustBeBlocked),
            targets = listOf(slot("permanent", and(or(creature, land), you))),
        ),
    )

    // CR 107.3 (X chosen as you pay), 603.12 (when you do), 601.2a / 118.9, 614.1a (exiled instead of put into a graveyard)
    @Test
    fun ringScavenger() = card(
        name = "Ring Scavenger",
        cost = "{1}{U}{B}",
        type = "Creature — Faerie Rogue",
        pt = "3/1",
        text = """
            Flying
            When this creature enters, you may pay {X}. When you do, you may cast target instant or sorcery card with mana value X from a graveyard without paying its mana cost. If that spell would be put into a graveyard, exile it instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            Step.Choose(Choice.Number(n(0)), bind = "X", chooser = Players.You),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X")))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("card", and(or(instant, sorcery), ObjectFilter.ManaValue(Comparison.Equal, Value.Bound("X")), inZone(Zone.Graveyard)))),
                steps = listOf(
                    Step.May(listOf(doing(Effect.Cast(target("card"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You))), Players.You),
                    doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely)),
                ),
            )))),
        ),
    )
}
