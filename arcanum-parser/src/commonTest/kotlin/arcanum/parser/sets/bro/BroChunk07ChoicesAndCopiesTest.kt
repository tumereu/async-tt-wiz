package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 07: choices on resolution, Commands, copies, "for each {G}{G} spent", conditions on mana spent and opponents. */
class BroChunk07ChoicesAndCopiesTest {
    /** "{G}{G}" spent to cast this object: half the green mana spent, rounded down (CR 601.2h, 400.7d). */
    private fun pairs(color: Color): Value = Value.Half(Value.ManaSpent(color = color, of = Objects.Self), roundUp = false)

    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)

    // CR 601.2f: a cost reduction of the ability while one opponent alone controls four nonbasic lands (CR 102.3)
    @Test
    fun gravegrinReassembler() = card(
        name = "Gravegrin Reassembler",
        cost = "{2}",
        type = "Artifact Creature — Zombie",
        pt = "3/1",
        text = """
            This creature can't block.
            {4}{B}{B}: Return this card from your graveyard to the battlefield with a +1/+1 counter on it. This ability costs {4} less to activate if an opponent controls four or more nonbasic lands.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        activated(
            manaCost("{4}{B}{B}"),
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, counters = listOf(CountersToPut(p1p1, n(1)))))),
            rules = ActivationRules(
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
                costChange = CostChange.Less(
                    mana = ManaCost.parse("{4}")!!,
                    times = Value.Min(listOf(
                        Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(
                            PlayerFilter.Opponent,
                            PlayerFilter.Where(Condition.Exists(
                                and(not(ObjectFilter.Super(Supertype.Basic)), land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))),
                                atLeast = 4,
                            )),
                        )))),
                        n(1),
                    )),
                ),
            ),
        ),
    )

    // CR 601.2h: each pair of one colour spent counts once, X included; CR 400.7d: the permanent keeps the mana spent on it
    @Test
    fun coilspringWyrm() = card(
        name = "Coilspring Wyrm",
        cost = "{X}{6}",
        type = "Artifact Creature — Serpent",
        pt = "5/4",
        text = """
            When this creature enters, for each {U}{U} spent to cast it, draw a card.
            When this creature enters, for each {B}{B} spent to cast it, each opponent discards a card.
            When this creature enters, for each {R}{R} spent to cast it, it gets +1/+0 and gains trample and haste until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Draw(Players.You, pairs(Color.Blue)))),
        triggered(entersSelf, doing(Effect.Discard(Players.Each(PlayerFilter.Opponent), pairs(Color.Black), ObjectFilter.Any))),
        triggered(entersSelf, Step.Repeat(pairs(Color.Red), listOf(
            pump(Objects.EventObject(EventRole.After), 1, 0, has(Keyword.Trample, Keyword.Haste))))),
    )

    // CR 406.2: exile all graveyards; CR 101.2: players can't cast noncreature spells this turn
    @Test
    fun ashenAftermath() = card(
        name = "Ashen Aftermath",
        cost = "{1}{W}",
        type = "Instant",
        text = "Exile all graveyards. Players can't cast noncreature spells this turn. Exile Ashen Aftermath.",
    ) parsesTo abilities(
        spell(
            move(Objects.Each(inZone(Zone.Graveyard)), Zone.Exile),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CantCast(and(not(creature), spellFilter))), Affected.Players(Players.Each(PlayerFilter.Any)),
                Duration.EndOfTurn)),
            move(Objects.Self, Zone.Exile),
        ),
    )

    // CR 601.2h: the mana spent to cast that spell, compared with this creature's power as the trigger resolves too (CR 603.4)
    @Test
    fun vellikBrassSkimmer() = card(
        name = "Vellik, Brass Skimmer",
        cost = "{3}",
        type = "Legendary Artifact Creature — Thopter",
        pt = "1/2",
        text = """
            Flash
            Flying
            You may cast colorless spells and artifact spells as though they had flash.
            Whenever you cast a spell, if the amount of mana spent to cast that spell is greater than Vellik's power, put a +1/+1 counter on Vellik.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        playerStatic(Mechanic.CastAsThoughFlash(or(and(ObjectFilter.Colorless, spellFilter), and(artifact, spellFilter)))),
        triggered(youCast(ObjectFilter.Any), counters(Objects.Self),
            interveningIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.Object)), Comparison.Greater, power(Objects.Self))),
    )

    // CR 601.2f: the reduction is decided after the target is chosen
    @Test
    fun noneAbandoned() = card(
        name = "None Abandoned",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            This spell costs {3} less to cast if it targets a creature card with mana value 3 or less.
            Return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{3}")!!, n(1)))), affects = Affected.Objects(Objects.Self),
            condition = Condition.ObjectMatches(target("card"), and(creature, manaValue(Comparison.AtMost, 3)))),
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 106.6: mana that can't be spent on nonartifact spells; CR 700.2: a modal triggered ability
    @Test
    fun theBrightstoneAndDimstone() = card(
        name = "The Brightstone and Dimstone",
        cost = "{5}",
        type = "Legendary Artifact — Powerstone",
        text = """
            When The Brightstone and Dimstone enters, choose one —
            • Draw two cards.
            • Target creature gets -5/-5 until end of turn.
            {T}: Add {C}{C}. This mana can't be spent to cast nonartifact spells.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(draw(2)),
            mode(pump(target("creature"), -5, -5), targets = listOf(slot("creature", creature))),
        ))),
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{C}{C}")!!),
            restriction = not(and(spellFilter, not(artifact)))), Players.You))),
    )

    // CR 700.2: a modal triggered ability; CR 111.10h: a Powerstone
    @Test
    fun crossbowTinkerers() = card(
        name = "Crossbow Tinkerers",
        cost = "{1}{R}{G}",
        type = "Creature — Human Artificer",
        pt = "2/2",
        text = """
            When this creature enters, choose one —
            • This creature deals 1 damage to any target.
            • Put a +1/+1 counter on target creature. It gains trample and haste until end of turn.
            • Create a tapped Powerstone token. (It's an artifact with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(damage(1, toAny), targets = listOf(anyTarget)),
            mode(counters(target("creature")), gains(target("creature"), Keyword.Trample, Keyword.Haste), targets = listOf(slot("creature", creature))),
            mode(createPowerstone()),
        ))),
    )

    // CR 701.6a: countering a spell or an ability on the Stack; CR 614.15: the countered spell exiled instead
    @Test
    fun unmakeDevice() = card(
        name = "Unmake Device",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target artifact or enchantment spell. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.
            • Counter target activated or triggered ability.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            Mode(Instructions(
                targets = listOf(slot("spell", and(or(artifact, enchantment), spellFilter))),
                steps = listOf(doing(Effect.Counter(target("spell")))),
                replacements = listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(ObjectFilter.Is(target("spell")), from = Zone.Stack, to = Zone.Graveyard),
                    modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
            )),
            mode(doing(Effect.Counter(target("ability"))), targets = listOf(slot("ability", or(
                and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
                and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered)),
            )))),
        ))),
    )

    // CR 700.2d: two different modes; "up to one creature" and "up to two creature cards" are chosen as it resolves, not targeted (CR 115.1);
    // CR 101.4, 701.21a: each opponent chooses one of their creatures with the greatest power, then all are sacrificed at once
    @Test
    fun vornsCommand() {
        val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))
        card(
            name = "Vorn's Command",
            cost = "{3}{B}{B}",
            type = "Sorcery",
            text = """
                Choose two —
                • Put two +1/+1 counters on up to one creature. It gains lifelink until end of turn.
                • Destroy each creature with power 2 or less.
                • Return up to two creature cards from your graveyard to your hand.
                • Each opponent sacrifices a creature with the greatest power among creatures they control.
            """,
        ) parsesTo abilities(
            Ability.Spell(Instructions(modes = modes(min = 2, max = 2, options = arrayOf(
                mode(
                    Step.Choose(Choice.Objects(creature, n(0), n(1)), bind = "chosen", chooser = Players.You),
                    counters(Objects.Bound("chosen"), 2),
                    gains(Objects.Bound("chosen"), Keyword.Lifelink),
                ),
                mode(destroy(Objects.Each(and(creature, power(Comparison.AtMost, 2))))),
                mode(
                    Step.Choose(Choice.Objects(inYourGraveyard(creature), n(0), n(2)), bind = "chosen", chooser = Players.You),
                    move(Objects.Bound("chosen"), Zone.Hand),
                ),
                mode(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                    Step.Choose(Choice.Objects(and(creature, theirs, ObjectFilter.Power(Comparison.Equal,
                        Value.Of(arcanum.api.NumericProperty.Power, Objects.Each(and(creature, theirs)), arcanum.api.Combine.Greatest))), n(1), n(1)),
                        bind = "sacrificed", chooser = Players.Bound("opponent")),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                ))),
            )))),
        )
    }

    // CR 700.2d; CR 107.3a: X announced as it is cast; CR 701.9a: the target player picks the cards they discard
    @Test
    fun keldasCommand() {
        val player = Players.Target("player")
        val x = Value.Announced(name = "X")
        card(
            name = "Kelda's Command",
            cost = "{X}{R}",
            type = "Sorcery",
            text = """
                Choose two —
                • Choose target player. They may discard up to X cards. Then they draw a card for each card discarded this way.
                • This spell deals X damage to target creature.
                • This spell deals X damage to target planeswalker.
                • Target creature gets +X/+0 and gains haste until end of turn.
            """,
        ) parsesTo abilities(
            Ability.Spell(Instructions(modes = modes(min = 2, max = 2, options = arrayOf(
                mode(
                    Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player))), n(0), x), bind = "chosen", chooser = player),
                    doing(Effect.Discard(player, x, ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
                    doing(Effect.Draw(player, Value.Count(Objects.Bound("discarded")))),
                    targets = listOf(targetPlayer),
                ),
                mode(damage(x, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
                mode(damage(x, toObjects(target("planeswalker"))), targets = listOf(slot("planeswalker", planeswalker))),
                mode(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(x, n(0)), has(Keyword.Haste)), targets = listOf(slot("creature", creature))),
            )), announced = listOf("X"))),
        )
    }

    // CR 606.3: loyalty abilities; CR 608.2c: the opponent chooses as it resolves; CR 701.24a: each owner's library is shuffled
    @Test
    fun ilsabetTimeDrifter() {
        val opponent = Players.Target("opponent")
        card(
            name = "Ilsabet, Time Drifter",
            cost = "{3}{U}{U}",
            type = "Legendary Planeswalker — Teferi",
            loyalty = "4",
            text = """
                Whenever you draw a card, put a loyalty counter on Ilsabet.
                [0]: Draw a card.
                [−2]: Create a 2/2 blue Spirit creature token with vigilance and "Whenever you draw a card, put a +1/+1 counter on this token."
                [−12]: Target opponent chooses a permanent they control and returns it to its owner's hand. Then they shuffle each nonland permanent they control into its owner's library.
            """,
        ) parsesTo abilities(
            triggered(on(EventPattern.Draw(PlayerFilter.You)), counters(Objects.Self, 1, CounterKind.Loyalty)),
            loyalty(0, draw(1)),
            loyalty(-2, create(creatureToken("Spirit", listOf(Color.Blue), 2, 2, abilities = listOf(Keyword.Vigilance,
                triggered(on(EventPattern.Draw(PlayerFilter.You)), counters(Objects.Self, 1)))), bind = "token")),
            loyalty(-12,
                Step.Choose(Choice.Objects(and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(opponent))), n(1), n(1)), bind = "chosen", chooser = opponent),
                move(Objects.Bound("chosen"), Zone.Hand),
                move(Objects.Each(and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(opponent)))), Zone.Library, postBind = "shuffled"),
                doing(Effect.Shuffle(Players.OwnerOf(Objects.Bound("shuffled")))),
                targets = listOf(targetOpponent),
            ),
        )
    }

    // CR 101.4: each player chooses in turn order, then every permanent no one chose is destroyed at once; CR 603.10a: a leaves-the-battlefield
    // trigger on being exiled (here by its own cost)
    @Test
    fun halvardsReliquary() = card(
        name = "Halvard's Reliquary",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            {2}{W}{W}, {T}, Exile Halvard's Reliquary: Each player chooses six lands they control. Destroy all other permanents. Activate only as a sorcery.
            When Halvard's Reliquary is put into exile from the battlefield, you may pay {2}. If you do, search your library for a planeswalker card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        activated(
            cost(mana("{2}{W}{W}"), tap, arcanum.api.CostPart.Exile(arcanum.api.CostObjects.Self, from = Zone.Battlefield)),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(6), n(6)),
                    bind = "chosen", chooser = Players.Bound("player")),
            )),
            destroy(Objects.Except(Objects.Each(permanentFilter), Objects.Bound("chosen"))),
            rules = asSorcery,
        ),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, to = Zone.Exile)),
            Step.Pay(cost(mana("{2}")), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), search(planeswalker, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        ),
    )

    // CR 707.10, 707.10c: the copy of a creature spell becomes a token; CR 707.9b: the exception is part of its copiable values
    @Test
    fun pellTheToycrafter() = card(
        name = "Pell, the Toycrafter",
        cost = "{3}{G}{U}",
        type = "Legendary Creature — Human Artificer",
        pt = "3/5",
        text = "Whenever you cast a Beast or Bird creature spell, you may copy it, except the copy is an artifact in addition to its other types. (The copy becomes a token.)",
    ) parsesTo abilities(
        triggered(youCast(and(or(sub("Beast"), sub("Bird")), creature)), Step.May(listOf(doing(Effect.CopySpell(
            Objects.EventObject(EventRole.Object), Players.You, newTargets = false, except = listOf(CopyException.Types(add = listOf(CardType.Artifact)))))),
            player = Players.You)),
    )

    // CR 614.1c: three counters for each {G}{G} spent as it enters; CR 601.2h, 400.7d: the trigger counts the {W}{W} spent on the spell it was
    @Test
    fun loamParagon() = card(
        name = "Loam Paragon",
        cost = "{X}{4}",
        type = "Artifact Creature — Construct",
        pt = "2/2",
        text = """
            This creature enters with three +1/+1 counters on it for each {G}{G} spent to cast it.
            When this creature enters, choose up to two other target creatures you control. For each {W}{W} spent to cast this creature, put a +1/+1 counter on each of them.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.Times(pairs(Color.Green), 3))))),
        triggered(entersSelf, counters(target("creatures"), pairs(Color.White)),
            targets = listOf(slot("creatures", and(another, creature, you), min = 0, max = 2))),
    )

    // CR 205.2a: for each card type among the noncreature spells you cast this turn (as they were cast), up to one revealed card of that
    // type, each card standing for one type; CR 701.20a: the rest go to the bottom in a random order
    @Test
    fun brevikMasterArcanist() {
        val types = listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Instant, CardType.Kindred,
            CardType.Planeswalker, CardType.Sorcery)
        val picks = types.indices.map { Objects.Bound("picked $it") }
        val choices = types.mapIndexed { i, type ->
            ifThen(Condition.HappenedThisTurn(EventPattern.Cast(and(not(creature), ObjectFilter.Type(type)), PlayerFilter.You), 1,
                arcanum.api.Measure.Events),
                Step.Choose(Choice.Objects(ObjectFilter.Type(type), n(0), n(1),
                    from = if (i == 0) Objects.Bound("seen") else Objects.Except(Objects.Bound("seen"), Objects.Union(picks.take(i)))),
                    bind = "picked $i", chooser = Players.You))
        }
        card(
            name = "Brevik, Master Arcanist",
            cost = "{1}{U}{U}",
            type = "Legendary Creature — Human Wizard Advisor",
            pt = "2/4",
            text = "At the beginning of your end step, if you've cast a noncreature spell this turn, reveal the top five cards of your library. For each card type among noncreature spells you've cast this turn, you may put a card of that type from among the revealed cards into your hand. Put the rest on the bottom of your library in a random order.",
        ) parsesTo abilities(
            triggered(yourEndStep,
                doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "seen")),
                *choices.toTypedArray(),
                move(Objects.Union(picks), Zone.Hand, postBind = "picked"),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("picked")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
                interveningIf = Condition.HappenedThisTurn(EventPattern.Cast(not(creature), PlayerFilter.You), 1, arcanum.api.Measure.Events),
            ),
        )
    }
}
