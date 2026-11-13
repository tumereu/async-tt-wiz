package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 10: requirements and type changes for a creature type (CR 508.1d, 613.1d), a limit on attackers of a
 * planeswalker (CR 508.1c), returning at that player's next end step (CR 603.7), choosing for each player and sacrificing the rest
 * (CR 701.21a), optional costs on resolution (CR 118.3, 603.12), cards that can't enter the battlefield and spells that can't be cast from
 * graveyards or exile (CR 101.2), activating loyalty abilities twice (CR 606.3), choices of one card per card type (CR 205.2a), "can't be the
 * target of" spells and abilities from sources of a colour (CR 702.11d), damage triggers (CR 120.3) and counters on permanents or players.
 */
class OneChunk10EffectsAndTriggersTest {
    private val juggernauts = and(sub("Juggernaut"), you)
    private val leftGraveyard = EventPattern.ZoneChange(and(another, not(token), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)
    private fun mayRemove(n: Int) = Step.Pay(cost(CostPart.RemoveCounters(CounterKind.Named("oil"), n(n), from = CostObjects.Self)), payer = Players.You,
        bind = "paid")

    // CR 508.1d: the attack requirement; CR 509.1b: the blocking restriction; CR 613.1d, 613.4b: the subtype in layer 4, base P/T in 7b
    @Test
    fun graazJuggernautWarden() = card(
        name = "Graaz Juggernaut Warden",
        cost = "{8}",
        type = "Legendary Artifact Creature — Juggernaut",
        pt = "7/5",
        text = """
            Juggernauts you control attack each combat if able.
            Juggernauts you control can't be blocked by Walls.
            Other creatures you control have base power and toughness 5/3 and are Juggernauts in addition to their other creature types.
        """,
    ) parsesTo abilities(
        static(Mechanic.MustAttack(), affects = Objects.Each(juggernauts)),
        static(Mechanic.CantBeBlockedBy(sub("Wall")), affects = Objects.Each(juggernauts)),
        static(Mechanic.SetPowerToughness(n(5), n(3)), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Juggernaut")))),
            affects = Objects.Each(and(another, creature, you))),
    )

    // CR 508.1c: a restriction on how many creatures attack it; CR 603.7: the card returns at its owner's next end step; CR 701.21a: one
    // sacrifice of every creature not chosen, the choices made for each player in turn order (CR 101.4)
    @Test
    fun eternalRoamer() = card(
        name = "Eternal Roamer",
        cost = "{4}{W}{W}",
        type = "Legendary Planeswalker",
        loyalty = "5",
        text = """
            No more than one creature can attack Eternal Roamer each combat.
            [+1]: Exile up to one target artifact or creature. Return that card to the battlefield under its owner's control at the beginning of that player's next end step.
            [0]: Create a 2/2 white Samurai creature token with double strike.
            [−4]: For each player, choose a creature that player controls. Each player sacrifices all creatures they control not chosen this way.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MaximumAttackers(1, target = Recipients(objects = Objects.Self))), affects = Affected.Objects(Objects.Each(creature))),
        loyalty(1,
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Is(Players.OwnerOf(Objects.Bound("exiled"))))),
                Instructions(steps = listOf(doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))))),
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(or(artifact, creature))), min = 0))),
        loyalty(0, create(creatureToken("Samurai", listOf(Color.White), 2, 2, abilities = listOf(Keyword.DoubleStrike)))),
        loyalty(-4,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(Step.Choose(Choice.Objects(and(creature,
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "chosen", chooser = Players.You)), together = true),
            doing(Effect.Sacrifice(Objects.Each(and(creature, not(ObjectFilter.Is(Objects.Bound("chosen")))))))),
    )

    // CR 118.3: pay 2 life and exile the card, only if both can be done; CR 707.9b: the copy's exceptions; CR 702.164a: toxic
    @Test
    fun kinzarBleakPatron() = card(
        name = "Kinzar Bleak Patron",
        cost = "{4}{B}",
        type = "Legendary Creature — Phyrexian Vampire",
        pt = "5/4",
        text = """
            Flying
            Whenever another nontoken creature you control dies, you may pay 2 life and exile it. If you do, create a token that's a copy of that creature, except it's 1/1 and has toxic 1. (Players dealt combat damage by it also get a poison counter.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(leftGraveyard),
            Step.Pay(cost(CostPart.PayLife(n(2)), CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.After)), from = Zone.Graveyard, postBind = "exiled")),
                payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.EventObject(EventRole.Before),
                listOf(CopyException.PowerToughness(1, 1), CopyException.AddAbilities(listOf(Keyword.Toxic(1))))), n(1), Players.You))))),
    )

    // CR 101.2: such cards stay where they are; CR 601.3: a card is tested as the spell it would become, where it is
    @Test
    fun hollowJailkeeper() = card(
        name = "Hollow Jailkeeper",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Golem",
        pt = "0/4",
        text = """
            Permanent cards in graveyards can't enter the battlefield.
            Players can't cast noncreature spells from graveyards or exile.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantEnterBattlefield, affects = Objects.Each(and(permanentCard, inZone(Zone.Graveyard)))),
        Ability.Static(listOf(Mechanic.CantCast(and(not(creature), spellFilter, or(inZone(Zone.Graveyard), inZone(Zone.Exile))))),
            affects = Affected.Players(Players.Each(PlayerFilter.Any))),
    )

    // CR 118.3: both counters or none; CR 603.12: a reflexive trigger
    @Test
    fun oilhammerCenturion() = card(
        name = "Oilhammer Centurion",
        cost = "{2}{R}",
        type = "Creature — Phyrexian Warrior",
        pt = "3/2",
        text = """
            Whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
            Whenever this creature attacks, you may remove two oil counters from it. When you do, target creature can't block this turn.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(another, or(creature, artifact), you), from = Zone.Battlefield, to = Zone.Graveyard)),
            counters(Objects.Self, 1, CounterKind.Named("oil"))),
        triggered(attacksSelf, mayRemove(2), Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
            targets = listOf(slot("creature", creature)),
            steps = listOf(untilEndOfTurn(target("creature"), Mechanic.CantBlock(attackers = ObjectFilter.Any))))))))),
    )

    // CR 118.3: "If you do" only if a counter was removed
    @Test
    fun latticedMantis() = card(
        name = "Latticed Mantis",
        cost = "{3}{G}",
        type = "Creature — Phyrexian Insect",
        pt = "4/3",
        text = """
            This creature enters with two oil counters on it.
            Whenever this creature attacks, you may remove an oil counter from it. If you do, untap it and it gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(arcanum.api.Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(arcanum.api.Modification.EntersWithCounters(CounterKind.Named("oil"), n(2))))), affects = Affected.Objects(Objects.Self)),
        triggered(attacksSelf, mayRemove(1), Step.If(Condition.Did("paid"), then = listOf(untapIt(Objects.Self), pump(Objects.Self, 1, 1)))),
    )

    // CR 603.7b: a delayed trigger for the next instant or sorcery spell this turn; CR 707.10c: new targets for the copy
    @Test
    fun mercurialOildancer() = card(
        name = "Mercurial Oildancer",
        cost = "{1}{U}",
        type = "Creature — Phyrexian Rogue",
        pt = "2/1",
        text = """
            This creature can't be blocked.
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            Whenever this creature deals combat damage to a player, you may remove two oil counters from it. If you do, when you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked),
        triggered(youCast(noncreatureSpell), counters(Objects.Self, 1, CounterKind.Named("oil"))),
        triggered(combatDamageToAPlayer, mayRemove(2), Step.If(Condition.Did("paid"), then = listOf(doing(Effect.CreateDelayedTrigger(
            youCast(and(or(instant, sorcery), spellFilter)),
            Instructions(steps = listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))), duration = Duration.EndOfTurn))))),
    )

    // CR 606.3: twice this turn; CR 400.7: one of the creatures that dealt the damage, still on the battlefield
    @Test
    fun kaisoShadowDancer() = card(
        name = "Kaiso, Shadow Dancer",
        cost = "{2}{U}{B}",
        type = "Legendary Planeswalker — Kaito",
        loyalty = "3",
        text = """
            Whenever one or more creatures you control deal combat damage to a player, you may return one of them to its owner's hand. If you do, you may activate loyalty abilities of Kaiso twice this turn rather than only once.
            [+1]: Up to one target creature can't attack or block until your next turn.
            [0]: Draw a card.
            [−2]: Create a 2/2 colorless Drone artifact creature token with deathtouch and "When this token leaves the battlefield, each opponent loses 2 life and you gain 2 life."
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)), oneOrMore = true,
            batchPerPlayer = true),
            Step.Pay(steps = listOf(Step.Choose(Choice.Objects(inZone(Zone.Battlefield), n(1), n(1), from = Objects.EventObject(EventRole.Source)), bind = "chosen",
                chooser = Players.You), move(Objects.Bound("chosen"), Zone.Hand)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.LoyaltyActivationsPerTurn(2)), Affected.Objects(Objects.Self),
                Duration.EndOfTurn))))),
        loyalty(1, doing(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(attackers = ObjectFilter.Any)), Affected.Objects(target("creature")),
            Duration.UntilYourNextTurn)), targets = listOf(slot("creature", creature, min = 0))),
        loyalty(0, draw(1)),
        loyalty(-2, create(creatureToken("Drone", emptyList(), 2, 2, types = listOf(CardType.Artifact, CardType.Creature), abilities = listOf(Keyword.Deathtouch,
            triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)))), bind = "token")),
    )

    // CR 608.2c: up to one creature card and up to one land card (a land creature card may be either); at X 5 or more each chosen card goes
    // onto the battlefield or into the hand, together with the rest going to the bottom
    @Test
    fun verdantSunsDusk() = card(
        name = "Verdant Sun's Dusk",
        cost = "{X}{G}",
        type = "Sorcery",
        text = "Reveal the top X plus one cards of your library. Choose a creature card and/or a land card from among them. Put those cards into your hand and the rest on the bottom of your library in a random order. If X is 5 or more, instead put the chosen cards onto the battlefield or into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        run {
            val x = Value.Announced(name = "X")
            val seen = Objects.Bound("seen")
            val chosen = Objects.Union(listOf(Objects.Bound("first"), Objects.Bound("second")))
            val rest = doing(Effect.Move(Objects.Except(seen, chosen), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))
            spell(
                doing(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Sum(listOf(x, n(1))), fromTop = true), bind = "seen")),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = seen), bind = "first", chooser = Players.You),
                Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Except(seen, Objects.Bound("first"))), bind = "second", chooser = Players.You),
                Step.If(Condition.Compare(x, Comparison.AtLeast, n(5)), then = listOf(
                    Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = chosen), bind = "onto battlefield", chooser = Players.You),
                    Step.Simultaneously(listOf(doing(Effect.Move(Objects.Bound("onto battlefield"), Destination(Zone.Battlefield))),
                        doing(Effect.Move(Objects.Except(chosen, Objects.Bound("onto battlefield")), Destination(Zone.Hand))), rest))),
                    otherwise = listOf(doing(Effect.Move(chosen, Destination(Zone.Hand))), rest)),
                announced = listOf("X"))
        },
    )

    // CR 205.1b, 613.1d: the card type added in layer 4, also to spells and to cards outside the battlefield
    @Test
    fun creepingMycosynth() = card(
        name = "Creeping Mycosynth",
        cost = "{3}{U}",
        type = "Artifact",
        text = "Nonland permanents you control are artifacts in addition to their other types. The same is true for permanent spells you control and nonland permanent cards you own that aren't on the battlefield.",
    ) parsesTo abilities(
        static(Mechanic.SetTypes(add = listOf(CardType.Artifact)), affects = Objects.Union(listOf(
            Objects.Each(and(nonland, permanentFilter, you)),
            Objects.Each(and(permanentCard, spellFilter, you)),
            Objects.Each(and(nonland, permanentCard, ObjectFilter.OwnedBy(PlayerFilter.You), or(*listOf(Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Command)
                .map { inZone(it) }.toTypedArray())))))),
    )

    // CR 603.4: the intervening if counts artifacts that entered under your control this turn
    @Test
    fun malcorusPurityWarden() = card(
        name = "Malcorus, Purity Warden",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Phyrexian Elephant Wizard",
        pt = "1/1",
        text = """
            When Malcorus enters, create a 3/3 colorless Phyrexian Golem artifact creature token.
            At the beginning of your end step, if three or more artifacts entered the battlefield under your control this turn, create a 3/3 colorless Phyrexian Golem artifact creature token.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(golem)),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), create(golem),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(artifact, you), to = Zone.Battlefield), atLeast = 3, measure = Measure.Objects)),
    )

    // CR 702.11d, 113.7: the spell itself or the ability's source is checked for its colour
    @Test
    fun thrukSilenceBreaker() = card(
        name = "Thruk, Silence Breaker",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Troll Shaman",
        pt = "5/5",
        text = """
            This spell can't be countered.
            Trample
            Thruk can't be the target of nongreen spells your opponents control or abilities from nongreen sources your opponents control.
            During your turn, Thruk has indestructible.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Trample,
        static(Mechanic.CantBeTargeted(by = opponents, fromSource = not(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Green))))),
        static(Mechanic.GainAbilities(listOf(Keyword.Indestructible)), condition = Condition.YourTurn),
    )

    // CR 205.2a: one card for each card type, a card of several types standing for one of them; CR 401.4: the rest in a random order
    @Test
    fun atraxisGrandJoiner() = card(
        name = "Atraxis, Grand Joiner",
        cost = "{3}{G}{W}{U}{B}",
        type = "Legendary Creature — Phyrexian Angel",
        pt = "7/7",
        text = """
            Flying, vigilance, deathtouch, lifelink
            When Atraxis enters, reveal the top ten cards of your library. For each card type, you may put a card of that type from among the revealed cards into your hand. Put the rest on the bottom of your library in a random order. (Artifact, battle, creature, enchantment, instant, land, planeswalker, and sorcery are card types.)
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Vigilance, Keyword.Deathtouch, Keyword.Lifelink,
        triggered(entersSelf,
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(10), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Bound("seen"), onePer = SharedQuality.CardType), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 118.12: each opponent's discard is known per opponent; 3+ players: one gain for each opponent who couldn't
    @Test
    fun grimCruelarch() = card(
        name = "Grim Cruelarch",
        cost = "{5}{B}",
        type = "Creature — Phyrexian Cleric",
        pt = "5/5",
        text = """
            Deathtouch
            When this creature enters, each opponent discards a card. For each opponent who can't, you gain 4 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(entersSelf, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.Bound("opponent"), mandatory = true, bind = "discarded"),
            Step.If(Condition.CouldNot("discarded"), then = listOf(gainLife(4)))))),
    )

    // CR 613.1b: control changes for good; CR 707.2: the copy of that creature
    @Test
    fun azureSunsDusk() = card(
        name = "Azure Sun's Dusk",
        cost = "{X}{U}{U}",
        type = "Sorcery",
        text = "Gain control of target creature with mana value X or less. If X is 5 or more, create a token that's a copy of that creature.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.Indefinitely)),
            Step.If(Condition.Compare(Value.Announced(name = "X"), Comparison.AtLeast, n(5)), then = listOf(
                doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.You)))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X"))))),
            announced = listOf("X")),
    )

    // CR 120.3: the damage dealt; CR 608.2h: the source's controller as it last existed
    @Test
    fun phyrexianAnnihilator() = card(
        name = "Phyrexian Annihilator",
        cost = "{B}{B}{B}{B}",
        type = "Creature — Phyrexian Horror",
        pt = "5/5",
        text = """
            Trample
            Whenever a source deals damage to this creature, that source's controller sacrifices that many permanents of their choice.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        run {
            val player = Players.ControllerOf(Objects.EventObject(EventRole.Source))
            triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
                Step.Choose(Choice.Objects(and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(player))), Value.EventAmount, Value.EventAmount),
                    bind = "sacrificed", chooser = player),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))))
        },
    )

    // CR 118.12a: sacrifice unless a card is discarded; CR 603.12: the reflexive trigger, the discarded card's mana value as it is in the
    // graveyard
    @Test
    fun argentMasticore() = card(
        name = "Argent Masticore",
        cost = "{5}",
        type = "Artifact Creature — Phyrexian Masticore",
        pt = "5/5",
        text = """
            First strike, protection from multicolored
            At the beginning of your upkeep, sacrifice this creature unless you discard a card. When you discard a card this way, destroy target nonland permanent an opponent controls with mana value less than or equal to the mana value of the discarded card.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike, Keyword.Protection(ObjectFilter.Multicolored),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)), preBind = "discarded")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Sacrifice(Objects.Self)))),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents, ObjectFilter.ManaValue(Comparison.AtMost,
                    Value.Of(NumericProperty.ManaValue, Objects.Bound("discarded"), Combine.Single))))),
                steps = listOf(destroy(target("permanent"))))))))),
    )

    // CR 701.3a: attaching each Equipment; CR 608.2h: what was attached to a creature that died, as it last existed
    @Test
    fun rukHexgoldSnatcher() = card(
        name = "Ruk, Hexgold Snatcher",
        cost = "{2}{R}",
        type = "Legendary Creature — Goblin Rebel",
        pt = "2/2",
        text = """
            Trample, haste
            Whenever an equipped creature you control other than Ruk attacks or dies, you may attach all Equipment attached to that creature to Ruk.
        """,
    ) parsesTo abilities(
        Keyword.Trample, Keyword.Haste,
        run {
            val equippedCreature = and(another, creature, you, ObjectFilter.Attached(attachments = equipment))
            triggered(Trigger.On(listOf(EventPattern.Attack(equippedCreature), EventPattern.ZoneChange(equippedCreature, from = Zone.Battlefield, to = Zone.Graveyard))),
                Step.May(listOf(doing(Effect.Attach(Objects.AttachmentsOf(Objects.EventObject(EventRole.Object), equipment), Recipients(objects = Objects.Self)))),
                    player = Players.You))
        },
    )

    // CR 603.2c: once for each permanent or player counters are put on, with that number
    @Test
    fun allBecomeOne() = card(
        name = "All Become One",
        cost = "{3}{R}{R}",
        type = "Enchantment",
        text = "Whenever you put one or more counters on a permanent or player, this enchantment deals that much damage to target opponent, creature an opponent controls, or planeswalker an opponent controls.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.CountersPut(permanentFilter, causedBy = you), EventPattern.PlayerCountersPut(PlayerFilter.Any, causedBy = you))),
            damage(Value.EventAmount, Recipients(target = "opponent or permanent")),
            targets = listOf(TargetSlot("opponent or permanent", TargetKind.Either(and(or(creature, planeswalker), opponents), PlayerFilter.Opponent), min = n(1),
                max = n(1)))),
    )

    private val golem = creatureToken("Phyrexian Golem", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature))
}
