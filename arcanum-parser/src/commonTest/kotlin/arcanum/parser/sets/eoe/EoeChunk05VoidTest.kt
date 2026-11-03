package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 05: void (an ability word, CR 207.2c): "if a nonland permanent left the battlefield this turn or a spell was warped this turn" (CR 702.185c) as intervening ifs, "unless", cost reductions and spells that do more; ward with a random discard.
 */
class EoeChunk05VoidTest {

    @Test
    fun velunirRockchosen() = card(
        name = "Velunir, Rockchosen",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Human Cleric",
        pt = "3/3",
        text = """
            Ward—Discard a card at random.
            Void — Whenever Velunir attacks, if a nonland permanent left the battlefield this turn or a spell was warped this turn, defending player loses half their life, rounded up.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost = cost(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1)), random = true))),
        triggered(
            attacksSelf,
            doing(
                Effect.LoseLife(
                    players = Players.DefendingPlayerOf(attackers = Objects.Self),
                    amount = Value.Half(value = Value.LifeOf(players = Players.DefendingPlayerOf(attackers = Objects.Self)), roundUp = true),
                ),
            ),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )

    @Test
    fun hymnalOfTheAbyss() = card(
        name = "Hymnal of the Abyss",
        cost = "{3}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            Whenever enchanted creature attacks, put target creature card from defending player's graveyard onto the battlefield under your control tapped and attacking.
            Void — At the beginning of your end step, sacrifice this Aura unless a nonland permanent left the battlefield this turn or a spell was warped this turn.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = and(creature, you))),
        triggered(
            on(EventPattern.Attack(attacker = ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self)))),
            doing(
                Effect.Move(
                    objects = target("card"),
                    to = Destination(zone = Zone.Battlefield, tapped = true, controller = Players.You, attacking = AttackTarget.Chosen),
                ),
            ),
            targets = listOf(
                slot(
                    "card",
                    and(
                        creature,
                        inZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(
                            players = PlayerFilter.Is(
                                players = Players.DefendingPlayerOf(attackers = Objects.AttachedTo(of = Objects.Self)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            Step.If(condition = Condition.Not(condition = void), then = listOf(doing(Effect.Sacrifice(objects = Objects.Self)))),
            abilityWord = "Void",
        ),
    )

    @Test
    fun decipherSignals() = card(
        name = "Decipher Signals",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            You draw two cards and lose 2 life.
            Void — If a nonland permanent left the battlefield this turn or a spell was warped this turn, instead you draw two cards and each opponent loses 2 life.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    Step.If(
                        condition = void,
                        then = listOf(
                            doing(Effect.Draw(players = Players.You, count = n(2))),
                            doing(Effect.LoseLife(players = Players.Each(filter = PlayerFilter.Opponent), amount = n(2))),
                        ),
                        otherwise = listOf(
                            doing(Effect.Draw(players = Players.You, count = n(2))),
                            doing(Effect.LoseLife(players = Players.You, amount = n(2))),
                        ),
                    ),
                ),
            ),
            abilityWord = "Void",
        ),
    )

    @Test
    fun lamentNovice() = card(
        name = "Lament Novice",
        cost = "{2}{B}{B}",
        type = "Creature — Human Cleric",
        pt = "4/4",
        text = """
            Lifelink
            Whenever one or more creatures you control deal combat damage to a player, you draw a card and lose 1 life.
            Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, create a 2/2 colorless Robot artifact creature token.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.Damage(source = and(creature, you), to = RecipientFilter(players = PlayerFilter.Any), combat = true),
                ),
                oneOrMore = true,
                batchPerPlayer = true,
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.LoseLife(players = Players.You, amount = n(1))),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You)),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )

    @Test
    fun nullsteelEdge() = card(
        name = "Nullsteel Edge",
        cost = "{B}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+1.
            Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, attach this Equipment to target creature you control.
            Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(3, 1), affects = Objects.AttachedTo(of = Objects.Self)),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(objects = target("creature")))),
            targets = listOf(slot("creature", and(creature, you))),
            interveningIf = void,
            abilityWord = "Void",
        ),
        Keyword.Equip(cost = cost(mana("{4}"))),
    )

    @Test
    fun psalmOfTheFallenStar() = card(
        name = "Psalm of the Fallen Star",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Surveil 1, then you draw a card and lose 1 life. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)
            Void — If a nonland permanent left the battlefield this turn or a spell was warped this turn, draw another card.
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Surveil(players = Players.You, count = n(1))),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.LoseLife(players = Players.You, amount = n(1))),
        ),
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(Step.If(condition = void, then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))))),
            ),
            abilityWord = "Void",
        ),
    )

    @Test
    fun ravenousScuttlejaw() = card(
        name = "Ravenous Scuttlejaw",
        cost = "{2}{B}",
        type = "Creature — Insect Horror",
        pt = "2/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )

    @Test
    fun interdictionAutomaton() = card(
        name = "Interdiction Automaton",
        cost = "{2}{B}{R}",
        type = "Artifact Creature — Robot",
        pt = "2/2",
        text = """
            Flying
            When this creature enters, return target artifact or creature card from your graveyard to your hand.
            Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Hand))),
            targets = listOf(slot("card", and(or(artifact, creature), inZone(Zone.Graveyard), ownedByYou))),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )

    @Test
    fun pyrrhosCloudsentry() = card(
        name = "Pyrrhos Cloudsentry",
        cost = "{4}{R}",
        type = "Creature — Kavu Soldier",
        pt = "4/5",
        text = """
            Reach
            Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )

    @Test
    fun ionLance() = card(
        name = "Ion Lance",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Ion Lance deals 2 damage to any target.
            Void — Ion Lance deals 3 damage instead if a nonland permanent left the battlefield this turn or a spell was warped this turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(anyTarget),
                steps = listOf(
                    Step.If(
                        condition = void,
                        then = listOf(doing(Effect.DealDamage(to = toAny, amount = n(3), source = Objects.Self))),
                        otherwise = listOf(doing(Effect.DealDamage(to = toAny, amount = n(2), source = Objects.Self))),
                    ),
                ),
            ),
            abilityWord = "Void",
        ),
    )

    @Test
    fun wanderingServomotor() = card(
        name = "Wandering Servomotor",
        cost = "{3}{R}",
        type = "Artifact Creature — Robot",
        pt = "3/4",
        text = "Void — When this creature enters, if a nonland permanent left the battlefield this turn or a spell was warped this turn, exile up to one target instant or sorcery card with mana value 2 or less from your graveyard. Copy it. You may cast the copy without paying its mana cost.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
            doing(Effect.CopyCard(objects = bound("exiled"), owner = Players.You, bind = "copy")),
            Step.May(
                steps = listOf(doing(Effect.Cast(objects = bound("copy"), how = PlayKind.Cast, withoutPaying = true, caster = Players.You))),
                player = Players.You,
            ),
            targets = listOf(
                slot(
                    "card",
                    and(
                        or(instant, sorcery),
                        ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2)),
                        inZone(Zone.Graveyard),
                        ownedByYou,
                    ),
                    min = 0,
                ),
            ),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )

    @Test
    fun chronalInterference() = card(
        name = "Chronal Interference",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Void — This spell costs {2} less to cast if a nonland permanent left the battlefield this turn or a spell was warped this turn.
            Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1)))),
            affects = Affected.Objects(objects = Objects.Self),
            condition = void,
            abilityWord = "Void",
        ),
        spell(
            doing(
                Effect.Reveal(
                    objects = Objects.Each(
                        filter = and(
                            inZone(Zone.Hand),
                            ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Target(slot = "opponent"))),
                        ),
                    ),
                    bind = "revealed",
                ),
            ),
            Step.Choose(
                choice = Choice.Objects(filter = not(land), min = n(1), max = n(1), from = bound("revealed")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(
                Effect.Discard(
                    players = Players.Target(slot = "opponent"),
                    count = n(1),
                    filter = ObjectFilter.Any,
                    objects = bound("chosen"),
                ),
            ),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )

    @Test
    fun doomedArc() = card(
        name = "Doomed Arc",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Target creature gets -2/-2 until end of turn.
            Void — That creature gets -10/-10 until end of turn instead if a nonland permanent left the battlefield this turn or a spell was warped this turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(
                    Step.If(
                        condition = void,
                        then = listOf(untilEndOfTurn(target("creature"), pt(-10, -10))),
                        otherwise = listOf(untilEndOfTurn(target("creature"), pt(-2, -2))),
                    ),
                ),
            ),
            abilityWord = "Void",
        ),
    )

    @Test
    fun nullwroughtColossus() = card(
        name = "Nullwrought Colossus",
        cost = "{4}{B}",
        type = "Artifact Creature — Robot Warrior",
        pt = "5/4",
        text = "Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, you draw a card and lose 1 life.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.LoseLife(players = Players.You, amount = n(1))),
            interveningIf = void,
            abilityWord = "Void",
        ),
    )
}
