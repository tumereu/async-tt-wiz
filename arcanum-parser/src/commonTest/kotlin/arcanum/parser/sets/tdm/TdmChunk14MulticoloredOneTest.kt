package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 14: multicolored cards: an additional combat when it's your main phase, doubled power and toughness then a fight, counters moved when a creature dies, exiling the top X of a damaged player's library and casting spells from among them, three color searches into the graveyard.
 */
class TdmChunk14MulticoloredOneTest {
    @Test
    fun totalOnslaught() = card(
        name = "Total Onslaught",
        cost = "{2}{R}{W}{B}",
        type = "Enchantment",
        text = """
            Creatures you control get +1/+1 and have deathtouch.
            When this enchantment enters, if it's your main phase, there is an additional combat phase after this phase followed by an additional main phase. When you next attack this turn, untap each creature you control.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(power = n(1), toughness = n(1)),
                Mechanic.GainAbilities(abilities = listOf(Keyword.Deathtouch)),
            ),
            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
        ),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.ExtraCombat(followedByMain = true)),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.Attack(attacker = you, withOthers = 0)), oneOrMore = true),
                            instructions = Instructions(steps = listOf(doing(Effect.Untap(objects = Objects.Each(filter = and(creature, you)))))),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            interveningIf = Condition.And(
                all = listOf(
                    Condition.YourTurn,
                    Condition.Or(
                        any = listOf(Condition.StepIs(step = TurnStep.PrecombatMain), Condition.StepIs(step = TurnStep.PostcombatMain)),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun rouseTheReveredDead() = card(
        name = "Rouse the Revered Dead",
        cost = "{B}{G}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Destroy target nonland permanent.
            II — Mill three cards.
            III — You may discard a card. When you do, return target creature or land card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(1)),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = and(not(land), permanentFilter)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Destroy(objects = target("permanent")))),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(2)),
            instructions = Instructions(steps = listOf(doing(Effect.Mill(players = Players.You, count = n(3))))),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(3)),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(parts = listOf(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1))))),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(
                            doing(
                                Effect.ReflexiveTrigger(
                                    instructions = Instructions(
                                        targets = listOf(
                                            TargetSlot(
                                                name = "card",
                                                what = TargetKind.Objects(filter = and(or(creature, land), inZone(Zone.Graveyard), ownedByYou)),
                                                min = n(1),
                                                max = n(1),
                                            ),
                                        ),
                                        steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Hand)))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun deathBreedsLife() = card(
        name = "Death Breeds Life",
        cost = "{5}{B}{G}{U}",
        type = "Sorcery",
        text = "Destroy all creatures and enchantments. Draw a card for each permanent destroyed this way.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Destroy(objects = Objects.Each(filter = or(creature, enchantment)), postBind = "destroyed")),
                    doing(Effect.Draw(players = Players.You, count = Value.Count(objects = bound("destroyed")))),
                ),
            ),
        ),
    )

    @Test
    fun joltingCurrent() = card(
        name = "Jolting Current",
        cost = "{2/R}{2/W}{2/B}",
        type = "Sorcery",
        text = "Jolting Current deals 4 damage to target creature or planeswalker and you gain 2 life.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "permanent", what = TargetKind.Objects(filter = or(creature, planeswalker)), min = n(1), max = n(1)),
                ),
                steps = listOf(
                    doing(Effect.DealDamage(to = Recipients(objects = target("permanent")), amount = n(4), source = Objects.Self)),
                    doing(Effect.GainLife(players = Players.You, amount = n(2))),
                ),
            ),
        ),
    )

    @Test
    fun wyrmclawStrike() = card(
        name = "Wyrmclaw Strike",
        cost = "{2/G}{2/U}{2/R}",
        type = "Sorcery",
        text = "Double the power and toughness of target creature you control until end of turn. Then it fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1)),
                    TargetSlot(
                        name = "second creature",
                        what = TargetKind.Objects(filter = and(creature, opponents)),
                        min = n(0),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.ModifyPowerToughness(
                                    power = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                                    toughness = Value.Of(property = NumericProperty.Toughness, objects = target("creature"), how = Combine.Single),
                                ),
                            ),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(Effect.Fight(first = target("creature"), second = target("second creature"))),
                ),
            ),
        ),
    )

    @Test
    fun fangwardensFamiliar() = card(
        name = "Fangwarden's Familiar",
        cost = "{1}{B}{G}{U}",
        type = "Creature — Snake",
        pt = "3/3",
        text = """
            Flash
            When this creature enters, choose one —
            • You gain 3 life and surveil 3. (Look at the top three cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            • Destroy target enchantment.
            • Counter target creature spell.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(Effect.GainLife(players = Players.You, amount = n(3))),
                                    doing(Effect.Surveil(players = Players.You, count = n(3))),
                                ),
                            ),
                            pawprints = 0,
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "enchantment",
                                        what = TargetKind.Objects(filter = enchantment),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Destroy(objects = target("enchantment")))),
                            ),
                            pawprints = 0,
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "spell",
                                        what = TargetKind.Objects(filter = and(creature, spellFilter)),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Counter(objects = target("spell")))),
                            ),
                            pawprints = 0,
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
    fun odrunDawnOfTheHouses() = card(
        name = "Odrun, Dawn of the Houses",
        cost = "{W}{B}{G}",
        type = "Legendary Creature — Human Warrior",
        pt = "3/3",
        text = """
            Trample
            Whenever Odrun enters or attacks, you may sacrifice a nonland permanent. When you do, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    EventPattern.Attack(attacker = isSelf, withOthers = 0),
                ),
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(
                            parts = listOf(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(land), permanentFilter), count = n(1)))),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(
                            doing(
                                Effect.ReflexiveTrigger(
                                    instructions = Instructions(
                                        steps = listOf(
                                            doing(
                                                Effect.PutCounters(
                                                    objects = Objects.Each(filter = and(creature, you)),
                                                    kind = p1p1,
                                                    amount = n(1),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun vanguardRush() = card(
        name = "Vanguard Rush",
        cost = "{R}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Create two 1/1 red Goblin creature tokens.
            • Target creature gets +X/+X until end of turn, where X is the number of creatures you control.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.CreateToken(
                                            token = TokenSpec.Defined(
                                                face = FaceDefinition(
                                                    name = "Goblin Token",
                                                    colorIndicator = listOf(Color.Red),
                                                    types = listOf(CardType.Creature),
                                                    subtypes = listOf(Subtype(name = "Goblin")),
                                                    power = PrintedNumber.Fixed(value = 1),
                                                    toughness = PrintedNumber.Fixed(value = 1),
                                                ),
                                            ),
                                            count = n(2),
                                            controller = Players.You,
                                        ),
                                    ),
                                ),
                            ),
                            pawprints = 0,
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                                steps = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(
                                                Mechanic.ModifyPowerToughness(
                                                    power = Value.Count(objects = Objects.Each(filter = and(creature, you))),
                                                    toughness = Value.Count(objects = Objects.Each(filter = and(creature, you))),
                                                ),
                                            ),
                                            affects = Affected.Objects(objects = target("creature")),
                                            duration = Duration.EndOfTurn,
                                        ),
                                    ),
                                ),
                            ),
                            pawprints = 0,
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
    fun fenmireNightwatch() = card(
        name = "Fenmire Nightwatch",
        cost = "{2/B}{2/G}{2/U}",
        type = "Creature — Human Ranger",
        pt = "3/3",
        text = "When this creature enters, look at the top three cards of your library. You may put one of those cards back on top of your library. Put the rest into your graveyard.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(3), fromTop = true),
                            players = Players.You,
                            bind = "looked",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(0), max = n(1), from = bound("looked")),
                        bind = "kept",
                        chooser = Players.You,
                    ),
                    doing(
                        Effect.Move(
                            objects = Objects.Except(base = bound("looked"), minus = bound("kept")),
                            to = Destination(zone = Zone.Graveyard),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun hardbittenTactician() = card(
        name = "Hardbitten Tactician",
        cost = "{1}{W}{B}",
        type = "Creature — Human Warrior",
        pt = "2/4",
        text = "{1}, Sacrifice a token: Draw a card.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}"), CostPart.Sacrifice(what = CostObjects.Choose(filter = token, count = n(1))))),
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
            rules = anyTime,
        ),
    )

    @Test
    fun hostOfTheAfterlife() = card(
        name = "Host of the Afterlife",
        cost = "{2}{B}{G}",
        type = "Creature — Zombie Warlock",
        pt = "2/2",
        text = """
            This creature enters with two +1/+1 counters on it.
            Whenever this creature or another creature you control dies, if it had counters on it, put its counters on up to one target creature you control.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = n(2))),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(
                        objects = or(isSelf, and(not(isSelf), creature, you)),
                        from = Zone.Battlefield,
                        to = Zone.Graveyard,
                    ),
                ),
            ),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(0), max = n(1))),
                steps = listOf(doing(Effect.PutCountersOf(objects = target("creature"), of = Objects.EventObject(role = EventRole.Before)))),
            ),
            interveningIf = Condition.ObjectMatches(
                objects = Objects.EventObject(role = EventRole.Before),
                filter = ObjectFilter.HasCounter(kind = null, atLeast = 1),
            ),
        ),
    )

    @Test
    fun certainDefeat() = card(
        name = "Certain Defeat",
        cost = "{1}{R}{W}{B}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Exile target nonland permanent. Its controller loses 3 life and you gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Static(mechanics = listOf(Mechanic.CantBeCountered), affects = Affected.Objects(objects = Objects.Self)),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = and(not(land), permanentFilter)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile))),
                    doing(Effect.LoseLife(players = Players.ControllerOf(objects = target("permanent")), amount = n(3))),
                    doing(Effect.GainLife(players = Players.You, amount = n(3))),
                ),
            ),
        ),
    )

    @Test
    fun wayfarerRevelation() = card(
        name = "Wayfarer Revelation",
        cost = "{4}{U}{R}{W}",
        type = "Instant",
        text = "Return target spell or permanent to its owner's hand. Wayfarer Revelation deals 4 damage to any target. Create two 1/1 white Monk creature tokens with prowess. Draw two cards. You gain 4 life.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = or(spellFilter, permanentFilter)),
                        min = n(1),
                        max = n(1),
                    ),
                    TargetSlot(
                        name = "any",
                        what = TargetKind.Either(
                            objects = or(creature, planeswalker, ObjectFilter.Type(cardType = CardTypeRef.Literal(cardType = CardType.Battle))),
                            players = PlayerFilter.Any,
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Hand))),
                    doing(Effect.DealDamage(to = Recipients(target = "any"), amount = n(4), source = Objects.Self)),
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Monk Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Monk")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                    abilities = listOf(Keyword.Prowess),
                                ),
                            ),
                            count = n(2),
                            controller = Players.You,
                        ),
                    ),
                    doing(Effect.Draw(players = Players.You, count = n(2))),
                    doing(Effect.GainLife(players = Players.You, amount = n(4))),
                ),
            ),
        ),
    )

    @Test
    fun kinrootSeverance() = card(
        name = "Kinroot Severance",
        cost = "{2/W}{2/B}{2/G}",
        type = "Instant",
        text = "Exile target permanent with mana value 3 or greater.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(
                            filter = and(permanentFilter, ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(3))),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile)))),
            ),
        ),
    )

    @Test
    fun riverbankSkimmer() = card(
        name = "Riverbank Skimmer",
        cost = "{G}{U}",
        type = "Creature — Bird Scout",
        pt = "2/2",
        text = """
            Flying
            Whenever a card leaves your graveyard during your turn, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(cardFilter, ownedByYou), from = Zone.Graveyard))),
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
            perTurn = 1,
            triggersOnlyIf = Condition.YourTurn,
        ),
    )

    @Test
    fun varrekTheFangwarden() = card(
        name = "Varrek, the Fangwarden",
        cost = "{1}{B}{G}{U}",
        type = "Legendary Creature — Zombie Warrior",
        pt = "2/1",
        text = """
            Indestructible
            Whenever Varrek deals combat damage to a player, exile the top X cards of their library, where X is the amount of damage dealt. You may cast any number of spells with mana value X or less from among them without paying their mana costs.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Damage(source = isSelf, to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Move(
                            objects = Objects.LibraryCards(of = Players.EventPlayer, count = Value.EventAmount, fromTop = true),
                            to = Destination(zone = Zone.Exile),
                            postBind = "exiled",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(not(land), ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = Value.EventAmount)),
                            min = n(0),
                            max = null,
                            from = bound("exiled"),
                        ),
                        bind = "toCast",
                        chooser = Players.You,
                    ),
                    doing(Effect.Cast(objects = bound("toCast"), how = PlayKind.Cast, withoutPaying = true, caster = Players.You)),
                ),
            ),
        ),
    )

    @Test
    fun lurkInAmbush() = card(
        name = "Lurk in Ambush",
        cost = "{B}{G}{U}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to your hand. Lurk in Ambush deals damage equal to that card's power to target creature.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(filter = and(creature, inZone(Zone.Graveyard), ownedByYou)),
                        min = n(1),
                        max = n(1),
                    ),
                    TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1)),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Hand), postBind = "returned")),
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("creature")),
                            amount = Value.Of(property = NumericProperty.Power, objects = bound("returned"), how = Combine.Single),
                            source = Objects.Self,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun lilyglowDancers() = card(
        name = "Lilyglow Dancers",
        cost = "{2}{B}{G}{U}",
        type = "Creature — Zombie Bard",
        pt = "3/6",
        text = """
            Lifelink
            When this creature enters, search your library for a black card, a green card, and a blue card. Put those cards into your graveyard, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Black)),
                            max = n(1),
                            zones = listOf(Zone.Library),
                            bind = "found1",
                            searcher = Players.You,
                        ),
                    ),
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(
                                ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Green)),
                                not(ObjectFilter.Is(objects = bound("found1"))),
                            ),
                            max = n(1),
                            zones = listOf(Zone.Library),
                            bind = "found2",
                            searcher = Players.You,
                        ),
                    ),
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(
                                ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Blue)),
                                not(ObjectFilter.Is(objects = bound("found1"))),
                                not(ObjectFilter.Is(objects = bound("found2"))),
                            ),
                            max = n(1),
                            zones = listOf(Zone.Library),
                            bind = "found3",
                            searcher = Players.You,
                        ),
                    ),
                    doing(Effect.Move(objects = bound("found1"), to = Destination(zone = Zone.Graveyard))),
                    doing(Effect.Move(objects = bound("found2"), to = Destination(zone = Zone.Graveyard))),
                    doing(Effect.Move(objects = bound("found3"), to = Destination(zone = Zone.Graveyard))),
                    doing(Effect.Shuffle(library = Players.You)),
                ),
            ),
        ),
    )
}
