package arcanum.parser.sets.trk

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CopyException
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
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

/** TRK chunk 02: modal spells with named modes, "choose target … " wordings, trigger doubling while tapped. */
class TrkChunk02ChoicesTest {
    private val officers = creatureToken("Officer", listOf(Color.Red), 1, 1)
    private val opponentsHand = listOf(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))
    private fun choiceSpell(vararg options: arcanum.api.Mode) = Ability.Spell(Instructions(modes = modes(*options)))

    // CR 608.2c ("that player" is the spell's controller), 118.12 (they may exile it; "if they don't"), 601.2 (cast while resolving)
    @Test
    fun audaciousGambit() = card(
        name = "Audacious Gambit",
        cost = "{R}{R}",
        type = "Instant",
        text = "Choose target noncreature spell an opponent controls. That player may exile it. If they don't, you may cast a noncreature spell from your hand without paying its mana cost.",
    ) parsesTo abilities(
        spell(
            Step.Pay(steps = listOf(move(target("spell"), Zone.Exile)), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")),
                Step.Choose(Choice.Objects(and(not(creature), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "card", chooser = Players.You),
                doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
            targets = listOf(slot("spell", and(not(creature), spellFilter, opponents)))),
    )

    // CR 700.2 (modes), 118.12 (counter unless paid), 122.1g (stun counters)
    @Test
    fun weighThePrimeAccord() = card(
        name = "Weigh the Prime Accord",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Interfere — Counter target spell unless its controller pays {3}.
            • Observe — Tap up to one target creature and put a stun counter on it. Draw a card. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(
                Step.Pay(manaCost("{3}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
                targets = listOf(slot("spell", spellFilter))),
            mode(tapIt(target("creature")), counters(target("creature"), kind = CounterKind.Stun), draw(1), targets = listOf(slot("creature", creature, min = 0))),
        ),
    )

    // CR 700.2; CR 701.21a: each opponent sacrifices one they choose (CR 101.4: in turn order, all at once)
    @Test
    fun parleyAtDarokIsle() = card(
        name = "Parley at Darok Isle",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Temba, His Arms Wide — Target creature gets +3/+3 and gains trample until end of turn.
            • Shaka, When the Walls Fell — Each opponent sacrifices a nontoken artifact or enchantment of their choice.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(pump(target("creature"), 3, 3, has(Keyword.Trample)), targets = listOf(slot("creature", creature))),
            mode(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(not(token), or(artifact, enchantment), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
        ),
    )

    // CR 603.2 (cast trigger), 608.2c ("it" is the spell); CR 122.1, 122.6: one more counter of a kind already on it
    @Test
    fun tavrokHeartUnveiled() = card(
        name = "Tavrok, Heart Unveiled",
        cost = "{G}{U}{R}",
        type = "Legendary Creature — Human Officer",
        pt = "3/3",
        text = """
            Whenever you cast an enchantment spell, draw a card. If it's a Saga spell, Tavrok deals 2 damage to each opponent.
            {2}: Choose a counter on target enchantment you control. Put another counter of that kind on it. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youCast(enchantment), draw(1),
            ifThen(Condition.ObjectMatches(Objects.EventObject(arcanum.api.EventRole.Object), and(sub("Saga"), spellFilter)), damage(2, eachOpponent))),
        activated(manaCost("{2}"), doing(Effect.OneMoreCounterOfChosenKind(toObjects(target("enchantment")))),
            targets = listOf(slot("enchantment", and(enchantment, you))), rules = asSorcery),
    )

    // CR 707.9b (a copy with changeling), 115.1 (X targets), 603.7 (exiled at the next end step)
    @Test
    fun shapeshiftIncursion() = card(
        name = "Shapeshift Incursion",
        cost = "{X}{R}{R}{R}",
        type = "Sorcery",
        text = "Choose X target creatures your opponents control. For each of them, create a token that's a copy of that creature, except it has changeling. Those tokens gain haste. Exile those tokens at the beginning of the next end step. (They're every creature type.)",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Objects(target("creatures")), bind = "creature", steps = listOf(
                doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("creature"), listOf(CopyException.AddAbilities(listOf(Keyword.Changeling)))), n(1), Players.You, bind = "tokens")))),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("tokens")), Duration.Indefinitely)),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(move(Objects.Bound("tokens"), Zone.Exile))))),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(and(creature, opponents)), min = Value.Announced(name = "X"), max = Value.Announced(name = "X"))),
            announced = listOf("X")),
    )

    // CR 700.2; CR 120.3 (damage to each creature and each player)
    @Test
    fun meetTheUncharted() = card(
        name = "Meet the Uncharted",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Come in Peace — Exile the top two cards of your library. Until the end of your next turn, you may play those cards.
            • Shoot to Kill — Meet the Uncharted deals 2 damage to each creature and each player.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(
                doing(Effect.Move(Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true), Destination(zone = Zone.Exile), postBind = "exiled")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
            mode(damage(2, arcanum.api.Recipients(objects = Objects.Each(creature), players = Players.Each(PlayerFilter.Any)))),
        ),
    )

    // CR 700.2; CR 608.2h: "its controller" of the destroyed creature, as it last existed
    @Test
    fun unbearableDilemma() = card(
        name = "Unbearable Dilemma",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Surgical Separation — Destroy target creature. Its controller creates two 1/1 red Officer creature tokens.
            • Remain Fused — You draw two cards and each player loses 2 life.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(destroy(target("creature")),
                Step.Do(Effect.CreateToken(officers, n(2), Players.ControllerOf(target("creature")))),
                targets = listOf(slot("creature", creature))),
            mode(draw(2), loseLife(2, Players.Each(PlayerFilter.Any))),
        ),
    )

    // CR 700.2 (a mode chosen before can't be chosen again)
    @Test
    fun unwinnableDrill() = card(
        name = "Unwinnable Drill",
        cost = "{1}{U}{U}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, choose one that hasn't been chosen —
            • Hail — Return up to one target creature an opponent controls with mana value 3 or less to its owner's hand.
            • Sensors — Draw two cards.
            • Transport — Create two 1/1 red Officer creature tokens.
            • Abandon Ship — You lose the game.
        """,
    ) parsesTo abilities(
        Ability.Triggered(yourUpkeep, Instructions(modes = modes(
            mode(move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(creature, opponents, manaValue(Comparison.AtMost, 3)), min = 0))),
            mode(draw(2)),
            mode(create(officers, n(2))),
            mode(doing(Effect.LoseGame(Players.You))),
            onlyUnchosen = true,
        ))),
    )

    // CR 502.3 (may choose not to untap); CR 603.2d (triggers an additional time), 611.2b (for as long as it remains tapped)
    @Test
    fun velvayaMoorDotingEnvoy() = card(
        name = "Velvaya Moor, Doting Envoy",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/2",
        text = """
            You may choose not to untap Velvaya Moor during your untap step.
            {2}, {T}: Choose another target creature you control. For as long as Velvaya Moor remains tapped, if a triggered ability of that creature triggers, that ability triggers an additional time.
        """,
    ) parsesTo abilities(
        static(Mechanic.MayChooseNotToUntap),
        activated(cost(mana("{2}"), tap),
            doing(Effect.ApplyMechanics(listOf(Mechanic.TriggersAdditionalTime(null, ObjectFilter.Is(target("creature")))), null,
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, tapped)))),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 700.2
    @Test
    fun hazardsOfAntiquity() = card(
        name = "Hazards of Antiquity",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Use the Relic — Target creature gets +3/+2 until end of turn.
            • Destroy the Relic — Destroy target artifact.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(pump(target("creature"), 3, 2), targets = listOf(slot("creature", creature))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ),
    )

    // CR 613.1b (control change); CR 608.2c: "that opponent" is who controlled the permanent as the spell resolved; "if they do" checks the
    // new controller
    @Test
    fun edictsOfCommerce() = card(
        name = "Edicts of Commerce",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = """
            Choose one —
            • Follow the 3rd Rule — Gain control of target nonland permanent an opponent controls. That opponent draws three cards.
            • Follow the 98th Rule — Target opponent gains control of target nonland permanent you control. If they do, you draw three cards.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(Step.ForEach(Each.Players(Players.ControllerOf(target("permanent"))), bind = "opponent", together = true, steps = listOf(
                    doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("permanent")), Duration.Indefinitely)),
                    draw(3, Players.Bound("opponent")))),
                targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
            mode(doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Target("opponent"))), Affected.Objects(target("permanent")), Duration.Indefinitely)),
                ifThen(Condition.ObjectMatches(target("permanent"), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), draw(3)),
                targets = listOf(targetOpponent, slot("permanent", and(nonland, permanentFilter, you)))),
        ),
    )

    // CR 702.15 (lifelink); CR 608.2d: the card you may choose, then that player exiles it and draws
    @Test
    fun brineLeech() = card(
        name = "Brine Leech",
        cost = "{1}{B}",
        type = "Creature — Vampire Shapeshifter",
        pt = "2/2",
        text = """
            Lifelink
            When this creature enters, look at target opponent's hand. You may choose a nonland card from it. If you do, that player exiles that card, then draws a card.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.Each(and(*opponentsHand.toTypedArray())), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(nonland, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            ifThen(Condition.NonEmpty("chosen"), move(Objects.Bound("chosen"), Zone.Exile), draw(1, Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    // CR 700.2; CR 122.1g (stun); CR 506.4 (an attacking creature)
    @Test
    fun readyTheEmitters() = card(
        name = "Ready the Emitters",
        cost = "{W}",
        type = "Instant",
        text = """
            Choose one —
            • Stun — Tap target creature and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            • Kill — This spell deals 3 damage to target attacking creature.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(tapIt(target("creature")), counters(target("creature"), kind = CounterKind.Stun), targets = listOf(slot("creature", creature))),
            mode(damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", and(attacking, creature)))),
        ),
    )

    // CR 700.2; CR 115.1 (one or two targets); CR 701.20a (reveal, then you choose)
    @Test
    fun driftFromChronicle() = card(
        name = "Drift from Chronicle",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Rewrite the Past — Return one or two target creature and/or Spacecraft cards from your graveyard to your hand.
            • Repair the Timeline — Target opponent reveals their hand. You choose a nonland card from it. Exile that card.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(move(target("creature and/or Spacecraft cards"), Zone.Hand), targets = listOf(slot("creature and/or Spacecraft cards", inYourGraveyard(or(creature, sub("Spacecraft"))), max = 2))),
            mode(
                doing(Effect.Reveal(Objects.Each(and(*opponentsHand.toTypedArray())), bind = "revealed")),
                Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
                targets = listOf(targetOpponent)),
        ),
    )

    // CR 700.2; CR 122.1a (+1/+1 counters)
    @Test
    fun trialOfResolve() = card(
        name = "Trial of Resolve",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Battle Stations — Put a +1/+1 counter on each creature you control.
            • Begin Negotiations — Target opponent draws a card. You draw two cards.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(counters(Objects.Each(creatureYouControl))),
            mode(draw(1, Players.Target("opponent")), draw(2), targets = listOf(targetOpponent)),
        ),
    )

    // CR 700.2
    @Test
    fun trialOfDaring() = card(
        name = "Trial of Daring",
        cost = "{G}",
        type = "Instant",
        text = """
            Choose one —
            • Alter the Past — Exile target card from a graveyard. You gain 2 life.
            • Obliterate the Transwarp Network — Destroy target artifact or enchantment with mana value 3 or less.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), gainLife(2), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", and(or(artifact, enchantment), manaValue(Comparison.AtMost, 3))))),
        ),
    )

    // CR 700.2; CR 702.4 (double strike); CR 118.12 ("Discard a card. If you do", a mandatory discard that can fail)
    @Test
    fun trialOfSelfhood() = card(
        name = "Trial of Selfhood",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • I Am Klingon — Target Klingon gets +1/+1 and gains double strike until end of turn.
            • I Am Starfleet — Discard a card. If you do, draw two cards.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(pump(target("Klingon"), 1, 1, has(Keyword.DoubleStrike)), targets = listOf(slot("Klingon", sub("Klingon")))),
            mode(Step.Pay(Cost(listOf(discardACard)), payer = Players.You, mandatory = true, bind = "paid"), ifThen(Condition.Did("paid"), draw(2))),
        ),
    )

    // CR 700.2
    @Test
    fun trialOfAllegiance() = card(
        name = "Trial of Allegiance",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Plain, Simple Garak — Exile target card from a graveyard. Draw a card.
            • Of the Obsidian Order — Return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        choiceSpell(
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), draw(1), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        ),
    )
}
