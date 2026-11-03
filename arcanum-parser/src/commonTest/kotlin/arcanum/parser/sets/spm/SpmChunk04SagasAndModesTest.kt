package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 04: Sagas (CR 714) and modes (CR 700.2) with flavor words (CR 207.2d): becoming a legendary Spider Hero, a copy of the next creature spell that isn't legendary (CR 707.10), a chosen card name, a discard or life choice for each player, graveyards exiled, a forced attack on another player, modes of one or both.
 */
class SpmChunk04SagasAndModesTest {

    @Test
    fun birthOfTheMaskedClimber() = card(
        name = "Birth of the Masked Climber",
        cost = "{1}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create a 2/1 green Spider creature token with reach.
            II — Put a +1/+1 counter on target creature you control. It becomes a legendary Spider Hero in addition to its other types.
            III — Target creature you control gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.Chapter(listOf(1)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Spider Token",
                            colorIndicator = listOf(Color.Green),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Spider")),
                            power = PrintedNumber.Fixed(2),
                            toughness = PrintedNumber.Fixed(1),
                            abilities = listOf(Keyword.Reach),
                        ),
                    ),
                    count = n(1),
                    controller = Players.You,
                )),
        ),
        triggered(
            Trigger.Chapter(listOf(2)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.SetTypes(
                            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spider")), SubtypeRef.Literal(Subtype("Hero"))),
                            addSupertypes = listOf(Supertype.Legendary),
                        ),
                    ),
                    affects = Affected.Objects(target("creature")),
                    duration = Duration.Indefinitely,
                )),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        triggered(
            Trigger.Chapter(listOf(3)),
            untilEndOfTurn(target("creature"), has(Keyword.DoubleStrike)),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun theMirroredLineage() = card(
        name = "The Mirrored Lineage",
        cost = "{3}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Surveil 3.
            II — When you next cast a creature spell this turn, copy it, except the copy isn't legendary.
            III — Choose a card name. Whenever a creature with the chosen name deals combat damage to a player this turn, draw a card.
        """,
    ) parsesTo abilities(
        triggered(Trigger.Chapter(listOf(1)), doing(Effect.Surveil(players = Players.You, count = n(3)))),
        triggered(
            Trigger.Chapter(listOf(2)),
            doing(Effect.CreateDelayedTrigger(
                    trigger = on(EventPattern.Cast(spell = and(creature, ObjectFilter.Category(ObjectCategory.Spell)), by = PlayerFilter.You)),
                    instructions = Instructions(
                        steps = listOf(
                            doing(Effect.CopySpell(
                                    objects = Objects.EventObject(EventRole.Object),
                                    controller = Players.You,
                                    newTargets = false,
                                    except = listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary))),
                                )),
                        ),
                    ),
                    duration = Duration.EndOfTurn,
                )),
        ),
        triggered(
            Trigger.Chapter(listOf(3)),
            Step.Choose(choice = Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You),
            doing(Effect.CreateDelayedTrigger(
                    trigger = on(EventPattern.Damage(
                            source = and(creature, ObjectFilter.ChosenName("name")),
                            to = RecipientFilter(players = PlayerFilter.Any),
                            combat = true,
                        )),
                    instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
                    duration = Duration.EndOfTurn,
                    repeats = true,
                )),
        ),
    )

    @Test
    fun theBridgeFallTragedy() = card(
        name = "The Bridge Fall Tragedy",
        cost = "{2}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Destroy target creature.
            II — Each player may discard a card. Each player who doesn't loses 3 life.
            III — Exile any number of target players' graveyards.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.Chapter(listOf(1)),
            doing(Effect.Destroy(target("creature"))),
            targets = listOf(slot("creature", creature)),
        ),
        triggered(
            Trigger.Chapter(listOf(2)),
            Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Any)),
                bind = "player",
                steps = listOf(
                    Step.Pay(
                        cost = cost(
                            CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                        ),
                        payer = Players.Bound("player"),
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(Condition.Did("paid")),
                        then = listOf(doing(Effect.LoseLife(players = Players.Bound("player"), amount = n(3)))),
                    ),
                ),
                together = true,
            ),
        ),
        triggered(
            Trigger.Chapter(listOf(3)),
            doing(Effect.Move(
                    objects = Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("players"))))),
                    to = Destination(Zone.Exile),
                )),
            targets = listOf(TargetSlot(name = "players", what = TargetKind.Players(PlayerFilter.Any), min = n(0), max = null)),
        ),
    )

    @Test
    fun citywideBloodbath() = card(
        name = "Citywide Bloodbath",
        cost = "{4}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Until your next turn, each creature attacks each combat if able and attacks a player other than you if able.
            II — Add {R}{R}{R}.
            III — This Saga deals 5 damage to each opponent.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.Chapter(listOf(1)),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.MustAttack(),
                        Mechanic.MustAttack(Recipients(players = Players.Each(PlayerFilter.Not(PlayerFilter.You)))),
                    ),
                    affects = Affected.Objects(Objects.Each(creature)),
                    duration = Duration.UntilYourNextTurn,
                )),
        ),
        triggered(
            Trigger.Chapter(listOf(2)),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}{R}")!!)), players = Players.You)),
        ),
        triggered(
            Trigger.Chapter(listOf(3)),
            doing(Effect.DealDamage(to = Recipients(players = Players.Each(PlayerFilter.Opponent)), amount = n(5), source = Objects.Self)),
        ),
    )

    @Test
    fun theStalkerSFinalPursuit() = card(
        name = "The Stalker's Final Pursuit",
        cost = "{3}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Mill five cards. When you do, this Saga deals damage equal to the greatest power among creature cards in your graveyard to target creature.
            II — Target creature you control gets +2/+2 until end of turn.
            III — Return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.Chapter(listOf(1)),
            doing(Effect.Mill(players = Players.You, count = n(5))),
            doing(Effect.ReflexiveTrigger(
                    Instructions(
                        targets = listOf(slot("creature", creature)),
                        steps = listOf(
                            doing(Effect.DealDamage(
                                    to = Recipients(target("creature")),
                                    amount = Value.Of(
                                        property = NumericProperty.Power,
                                        objects = Objects.Each(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                                        how = Combine.Greatest,
                                    ),
                                    source = Objects.Self,
                                )),
                        ),
                    ),
                )),
        ),
        triggered(
            Trigger.Chapter(listOf(2)),
            untilEndOfTurn(target("creature"), pt(2, 2)),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        triggered(
            Trigger.Chapter(listOf(3)),
            doing(Effect.Move(objects = target("card"), to = Destination(Zone.Hand))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        ),
    )

    @Test
    fun morningHeraldNewshounds() = card(
        name = "Morning Herald Newshounds",
        cost = "{3}{W}",
        type = "Creature — Human Citizen",
        pt = "2/3",
        text = """
            When this creature enters, choose one —
            • Puff Piece — Put a +1/+1 counter on each of up to two target creatures.
            • Investigative Journalism — Return target creature card with mana value 2 or less from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                targets = listOf(slot("creatures", creature, min = 0, max = 2)),
                                steps = listOf(doing(Effect.PutCounters(objects = target("creatures"), kind = p1p1, amount = n(1)))),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(
                                    slot(
                                        "card",
                                        and(
                                            creature,
                                            ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2)),
                                            inZone(Zone.Graveyard),
                                            ObjectFilter.OwnedBy(PlayerFilter.You),
                                        ),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(Zone.Hand)))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun brockHaleDevotedFan() = card(
        name = "Brock Hale, Devoted Fan",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Citizen",
        pt = "2/2",
        text = """
            Flash
            When Brock Hale enters, choose one or both —
            • Heckle — Tap target creature.
            • Hero Worship — Untap target creature.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(doing(Effect.Tap(target("creature"))))),
                        ),
                        Mode(
                            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(doing(Effect.Untap(target("creature"))))),
                        ),
                    ),
                    min = 1,
                    max = 2,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun dazzlingManeuvers() = card(
        name = "Dazzling Maneuvers",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Put a +1/+1 counter on target creature you control. It gains hexproof until end of turn.
            • Destroy target creature with power 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                targets = listOf(slot("creature", and(creature, you))),
                                steps = listOf(
                                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                                    untilEndOfTurn(target("creature"), has(Keyword.Hexproof)),
                                ),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(slot("creature", and(creature, ObjectFilter.Power(comparison = Comparison.AtLeast, value = n(4))))),
                                steps = listOf(doing(Effect.Destroy(target("creature")))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun astoundingTumble() = card(
        name = "Astounding Tumble",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Choose one or both —
            • Counter target spell.
            • Tap one or two target creatures.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
                                steps = listOf(doing(Effect.Counter(target("spell")))),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(slot("creatures", creature, max = 2)),
                                steps = listOf(doing(Effect.Tap(target("creatures")))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 2,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun classroomReverie() = card(
        name = "Classroom Reverie",
        cost = "{3}{U}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Do Homework — Draw three cards.
            • Fight Crime — Counter target spell. Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(3)))))),
                        Mode(
                            Instructions(
                                targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
                                steps = listOf(doing(Effect.Counter(target("spell"))), doing(Effect.Draw(players = Players.You, count = n(1)))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun hiddenPersona() = card(
        name = "Hidden Persona",
        cost = "{U}",
        type = "Instant",
        text = """
            Choose one —
            • Conceal — Until end of turn, target creature you control becomes a Citizen with base power and toughness 1/1 and gains hexproof.
            • Reveal — Until end of turn, target creature you control becomes a Hero with base power and toughness 3/4 and gains flying and vigilance.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                targets = listOf(slot("creature", and(creature, you))),
                                steps = listOf(
                                    untilEndOfTurn(
                                        target("creature"),
                                        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Citizen"))), replaceSubtypes = true),
                                        Mechanic.SetPowerToughness(power = n(1), toughness = n(1)),
                                        has(Keyword.Hexproof),
                                    ),
                                ),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(slot("creature", and(creature, you))),
                                steps = listOf(
                                    untilEndOfTurn(
                                        target("creature"),
                                        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Hero"))), replaceSubtypes = true),
                                        Mechanic.SetPowerToughness(power = n(3), toughness = n(4)),
                                        has(Keyword.Flying, Keyword.Vigilance),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun vigilanteDiner() = card(
        name = "Vigilante Diner",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Date Night — Exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.
            • Patrol Night — One or two target creatures each get +1/+0 and gain first strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                steps = listOf(
                                    doing(Effect.Move(
                                            objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true),
                                            to = Destination(Zone.Exile),
                                            postBind = "exiled",
                                        )),
                                    Step.Choose(
                                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("exiled")),
                                        bind = "chosen",
                                        chooser = Players.You,
                                    ),
                                    doing(Effect.ApplyMechanics(
                                            mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                                            affects = Affected.Objects(Objects.Bound("chosen")),
                                            duration = Duration.UntilEndOfYourNextTurn,
                                        )),
                                ),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(slot("creatures", creature, max = 2)),
                                steps = listOf(untilEndOfTurn(target("creatures"), pt(1, 0), has(Keyword.FirstStrike))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun surveyTheSkyline() = card(
        name = "Survey the Skyline",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Look Around — Mill three cards. You may put a permanent card from among them into your hand. You gain 3 life. (To mill three cards, put the top three cards of your library into your graveyard.)
            • Bring Down — Destroy target creature with flying.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                steps = listOf(
                                    doing(Effect.Mill(players = Players.You, count = n(3), postBind = "milled")),
                                    Step.Choose(
                                        choice = Choice.Objects(
                                            filter = or(
                                                artifact,
                                                ObjectFilter.Type(CardType.Battle),
                                                creature,
                                                enchantment,
                                                land,
                                                ObjectFilter.Type(CardType.Planeswalker),
                                            ),
                                            min = n(0),
                                            max = n(1),
                                            from = Objects.Bound("milled"),
                                        ),
                                        bind = "chosen",
                                        chooser = Players.You,
                                    ),
                                    doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(Zone.Hand))),
                                    doing(Effect.GainLife(players = Players.You, amount = n(3))),
                                ),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(slot("creature", and(creature, ObjectFilter.HasKeyword(Keyword.Flying)))),
                                steps = listOf(doing(Effect.Destroy(target("creature")))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun rubbleRepairSquad() = card(
        name = "Rubble Repair Squad",
        cost = "{3}{G}",
        type = "Creature — Human Citizen",
        pt = "3/3",
        text = """
            When this creature enters, choose one —
            • Repair — Return target card with mana value 4 or greater from your graveyard to your hand.
            • Impound — Exile target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            Instructions(
                                targets = listOf(
                                    slot(
                                        "card",
                                        and(
                                            ObjectFilter.Category(ObjectCategory.Card),
                                            ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                                            inZone(Zone.Graveyard),
                                            ObjectFilter.OwnedBy(PlayerFilter.You),
                                        ),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(Zone.Hand)))),
                            ),
                        ),
                        Mode(
                            Instructions(
                                targets = listOf(slot("permanent", or(artifact, enchantment))),
                                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(Zone.Exile)))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )
}
