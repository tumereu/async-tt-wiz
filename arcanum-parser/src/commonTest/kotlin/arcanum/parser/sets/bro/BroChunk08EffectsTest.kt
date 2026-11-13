package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 08: damage, discard, draw, exile, life and control effects of single cards. */
class BroChunk08EffectsTest {
    private val yourHand = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val handOfYou = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You)))
    private val cardCategory = ObjectFilter.Category(ObjectCategory.Card)
    private fun theirs(player: Players) = ObjectFilter.ControlledBy(PlayerFilter.Is(player))
    private fun reflexive(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()): Step =
        Step.Do(Effect.ReflexiveTrigger(Instructions(targets = targets, steps = steps.toList())))

    // CR 608.2h: the sacrificed creature's power as it last existed; CR 601.2b: the additional cost
    @Test
    fun cinderpyreOffering() = card(
        name = "Cinderpyre Offering",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Cinderpyre Offering deals damage equal to the sacrificed creature's power to any target. Draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature, preBind = "sacrificed")))),
        spell(damage(power(Objects.Bound("sacrificed")), toAny), draw(1), targets = listOf(anyTarget)),
    )

    // CR 115.1: two targets, the land controlled by the chosen opponent; CR 120.2: damage to that player and each creature they control
    @Test
    fun theSackOfOstmere() = card(
        name = "The Sack of Ostmere",
        cost = "{4}{R}{R}",
        type = "Sorcery",
        text = "Choose target opponent. Destroy target land that player controls. The Sack of Ostmere deals 3 damage to that player and 1 damage to each creature they control.",
    ) parsesTo abilities(
        spell(
            destroy(target("land")),
            damage(3, Recipients(players = Players.Target("opponent"))),
            damage(1, toObjects(Objects.Each(and(creature, theirs(Players.Target("opponent")))))),
            targets = listOf(targetOpponent, slot("land", and(land, theirs(Players.Target("opponent"))))),
        ),
    )

    // CR 701.9a: the whole hand discarded, then that many drawn; up to one discarded artifact or creature card of each mana value 1, 2 and 3
    // returns; CR 602.5d: activate only as a sorcery
    @Test
    fun duchessVarnaOfBrenmoor() = card(
        name = "Duchess Varna of Brenmoor",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Noble",
        pt = "2/3",
        text = "{4}, {T}: Discard all the cards in your hand, then draw that many cards. You may choose an artifact or creature card with mana value 1 you discarded this way, then do the same for artifact or creature cards with mana values 2 and 3. Return those cards to the battlefield. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(
            cost(mana("{4}"), tap),
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(handOfYou), postBind = "discarded")),
            doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("discarded")))),
            *(1..3).map { mv ->
                Step.Choose(Choice.Objects(and(or(artifact, creature), manaValue(Comparison.Equal, mv)), n(0), n(1), from = Objects.Bound("discarded")),
                    bind = "chosen ${mv - 1}", chooser = Players.You)
            }.toTypedArray(),
            move(Objects.Union((0..2).map { Objects.Bound("chosen $it") }), Zone.Battlefield),
            rules = asSorcery,
        ),
    )

    // CR 608.2h: the target's mana value as the spell resolves; CR 613.1b: an opponent you choose controls it from then on
    @Test
    fun grimBequest() = card(
        name = "Grim Bequest",
        cost = "{3}{B}",
        type = "Sorcery",
        text = "Draw cards equal to the mana value of target artifact or creature you control. An opponent gains control of that permanent.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Draw(Players.You, Value.Of(NumericProperty.ManaValue, target("permanent"), Combine.Single))),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("opponent"))), Affected.Objects(target("permanent")), Duration.Indefinitely)),
            targets = listOf(slot("permanent", and(or(artifact, creature), you))),
        ),
    )

    // CR 400.7: the exiled card; CR 404.1: "that player" is the graveyard's owner
    @Test
    fun gravepickLocust() = card(
        name = "Gravepick Locust",
        cost = "{2}{B}",
        type = "Creature — Insect Horror",
        pt = "2/1",
        text = """
            Flying
            When this creature enters, exile target card from an opponent's graveyard. If it was a creature card, that player loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            move(target("graveyard"), Zone.Exile),
            ifThen(Condition.ObjectMatches(target("graveyard"), creature), loseLife(1, Players.OwnerOf(target("graveyard")))),
            targets = listOf(slot("graveyard", and(cardCategory, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent))))),
    )

    // CR 120.3: "that many" is the damage dealt; CR 611.2: until the end of your next turn
    @Test
    fun orrinQuarryDelver() = card(
        name = "Orrin, Quarry Delver",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/2",
        text = """
            Haste
            Orrin can't block.
            Whenever Orrin is dealt damage, exile that many cards from the top of your library. Choose one of them. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = isSelf))),
            move(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("chosen")),
                Duration.UntilEndOfYourNextTurn))),
    )

    // CR 406.3: face-down exiled cards; one pile is turned face up (shown to all); an opponent chooses a pile
    @Test
    fun bitterParley() {
        val first = Objects.Bound("first pile")
        val second = Objects.Bound("second pile")
        fun exile(bind: String) = move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Zone.Exile, postBind = bind).let {
            val e = (it as Step.Do).effect as Effect.Move
            Step.Do(e.copy(to = e.to.copy(faceDown = true)))
        }
        card(
            name = "Bitter Parley",
            cost = "{3}{B}",
            type = "Instant",
            text = "Exile the top three cards of your library in a face-down pile, then exile the top three cards of your library in another face-down pile. Look at the cards in each pile, then turn a pile of your choice face up. An opponent chooses one of those piles. Put that pile into your hand and the other into your graveyard. You lose 3 life.",
        ) parsesTo abilities(
            spell(
                exile("first pile"),
                exile("second pile"),
                doing(Effect.LookAt(Objects.Union(listOf(first, second)), Players.You)),
                Step.Choose(Choice.Option(listOf("first", "second")), bind = "face up", chooser = Players.You),
                ifThen(Condition.Chose("face up", "first"), doing(Effect.Reveal(first)), otherwise = listOf(doing(Effect.Reveal(second)))),
                Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                Step.Choose(Choice.Option(listOf("first", "second")), bind = "pile", chooser = Players.Bound("opponent")),
                ifThen(Condition.Chose("pile", "first"), move(first, Zone.Hand), move(second, Zone.Graveyard),
                    otherwise = listOf(move(second, Zone.Hand), move(first, Zone.Graveyard))),
                loseLife(3),
            ),
        )
    }

    // CR 201.2: the same name; "that player" is the target's controller; CR 111.1: tokens
    @Test
    fun ranksToCinders() = card(
        name = "Ranks to Cinders",
        cost = "{1}{W}{B}",
        type = "Sorcery",
        text = "Exile target nonland permanent an opponent controls and all tokens that player controls with the same name as that permanent.",
    ) parsesTo abilities(
        spell(
            move(Objects.Union(listOf(target("permanent"), Objects.Each(and(token, ObjectFilter.SameNameAs(target("permanent")),
                theirs(Players.ControllerOf(target("permanent"))))))), Zone.Exile),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents))),
        ),
    )

    // CR 305.1, 601.2a: playing a card from exile is playing a land or casting a spell from there; CR 603.4: intervening if
    @Test
    fun glimpsesOfRuin() = card(
        name = "Glimpses of Ruin",
        cost = "{2}{R}{R}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, exile the top card of your library. You may play that card this turn.
            At the beginning of your end step, if you didn't play a card from exile this turn, create a tapped Powerstone token. (It's an artifact with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.EndOfTurn))),
        triggered(yourEndStep, createPowerstone(), interveningIf = Condition.Not(Condition.Or(listOf(
            Condition.HappenedThisTurn(EventPattern.PlayLand(land, PlayerFilter.You, from = Zone.Exile), atLeast = 1, measure = Measure.Events),
            Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Exile), atLeast = 1, measure = Measure.Events),
        )))),
    )

    // CR 603.7: the sacrifice is a delayed trigger at the next end step; the haste has no end
    @Test
    fun armoryRush() = card(
        name = "Armory Rush",
        cost = "{3}{R}",
        type = "Enchantment",
        text = "{3}{R}: You may put an artifact card from your hand onto the battlefield. That artifact gains haste. Sacrifice it at the beginning of the next end step.",
    ) parsesTo abilities(
        activated(manaCost("{3}{R}"),
            Step.Choose(Choice.Objects(and(artifact, yourHand), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("put")), Duration.Indefinitely)),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("put")))))))),
    )

    // CR 120.3: the life gained is the damage actually dealt
    @Test
    fun bogsoulSiphon() = card(
        name = "Bogsoul Siphon",
        cost = "{5}{B}",
        type = "Sorcery",
        text = "Bogsoul Siphon deals damage to any target equal to the number of Swamps you control. You gain life equal to the damage dealt this way.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toAny, count(and(sub("Swamp"), you)), Objects.Self, bind = "damage")),
            doing(Effect.GainLife(Players.You, Value.Bound("damage"))),
            targets = listOf(anyTarget),
        ),
    )

    // CR 603.12: the reflexive trigger; CR 118.12: "If you do" after the optional life gain; CR 400.7: this card in the graveyard
    @Test
    fun cloudnetSpider() = card(
        name = "Cloudnet Spider",
        cost = "{2}{B}{G}",
        type = "Creature — Spider",
        pt = "3/3",
        text = """
            Reach
            When this creature enters, you may sacrifice another creature. When you do, destroy target nonland permanent.
            When this creature dies, you may gain 1 life for each creature card in your graveyard. If you do, exile this card from your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter))))))),
        triggered(diesSelf,
            Step.Pay(steps = listOf(doing(Effect.GainLife(Players.You, count(inYourGraveyard(creature))))), bind = "paid", payer = Players.You),
            Step.If(Condition.Did("paid"), then = listOf(move(Objects.EventObject(EventRole.After), Zone.Exile)))),
    )

    // CR 614.12, 614.1c: the reveal and the check happen as it enters
    @Test
    fun garrisonedShoreline() = card(
        name = "Garrisoned Shoreline",
        cost = null,
        type = "Land",
        text = """
            As this land enters, you may reveal a Soldier card from your hand. This land enters tapped unless you revealed a Soldier card this way or you control a Soldier.
            {T}: Add {W} or {U}.
            {5}, {T}: Soldiers you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Objects(and(sub("Soldier"), yourHand), n(0), n(1)), bind = "revealed", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            ifThen(Condition.Not(Condition.Or(listOf(Condition.NonEmpty("revealed"), Condition.Exists(and(sub("Soldier"), you), atLeast = 1)))),
                Step.Modify(Modification.EntersTapped)),
        ))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Literal(Color.White),
            ColorRef.Literal(Color.Blue)))), Players.You))),
        activated(cost(mana("{5}"), tap), pump(Objects.Each(and(sub("Soldier"), you)), 1, 1)),
    )

    // CR 613.1d, 613.4b: it becomes blue and 5/4 from then on; CR 602.5b: activation conditions; "only once" for as long as it exists
    @Test
    fun wavecoilEngine() = card(
        name = "Wavecoil Engine",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "3/2",
        text = """
            Defender
            {U}: This creature loses defender and gains "This creature can't be blocked."
            {2}{U}: This creature becomes blue and has base power and toughness 5/4. Activate only if this creature doesn't have defender.
            {4}{U}{U}: Draw three cards. Activate only if this creature is blue and only once.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(manaCost("{U}"), doing(Effect.ApplyMechanics(listOf(Mechanic.LoseAbilities(Keyword.Defender.kind),
            has(static(Mechanic.CantBeBlocked))), Affected.Objects(Objects.Self), Duration.Indefinitely))),
        activated(manaCost("{2}{U}"), doing(Effect.ApplyMechanics(listOf(Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue))), Mechanic.SetPowerToughness(n(5), n(4))),
            Affected.Objects(Objects.Self), Duration.Indefinitely)),
            rules = anyTime.copy(condition = Condition.Not(Condition.ObjectMatches(Objects.Self, kw(Keyword.Defender.kind))))),
        activated(manaCost("{4}{U}{U}"), draw(3),
            rules = anyTime.copy(condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.OfColor(Color.Blue)), once = true)),
    )

    // CR 510.2: combat damage to an opponent; CR 118.12: its controller may pay; CR 107.3a: X announced with the discard cost; CR 305.2a,
    // 608.2g: lands played and spells cast as the ability resolves
    @Test
    fun vashkGrinningUsurper() = card(
        name = "Vashk, Grinning Usurper",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Phyrexian Praetor",
        pt = "3/3",
        text = """
            Whenever a creature deals combat damage to one of your opponents, its controller may pay 1 life. If they do, they draw a card.
            {4}{B}{B}{B}, Discard X cards: Exile the top X cards of target opponent's library. You may play lands and cast spells from among cards exiled this way without paying their mana costs.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.Opponent), combat = true)),
            Step.Pay(Cost(listOf(CostPart.PayLife(n(1)))), payer = Players.ControllerOf(Objects.EventObject(EventRole.Source)), bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1, Players.ControllerOf(Objects.EventObject(EventRole.Source)))))),
        Ability.Activated(
            cost(mana("{4}{B}{B}{B}"), CostPart.Discard(CostObjects.Choose(cardCategory, count = Value.Announced("X")))),
            Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(
                    move(Objects.LibraryCards(Players.Target("opponent"), Value.Announced("X"), fromTop = true), Zone.Exile, postBind = "exiled"),
                    Step.ForEach(Each.Objects(Objects.Bound("exiled")), bind = "card", steps = listOf(
                        Step.May(listOf(doing(Effect.Cast(Objects.Bound("card"), PlayKind.Play, withoutPaying = true, caster = Players.You))), player = Players.You))),
                ),
                announced = listOf("X"),
            ),
            anyTime,
        ),
    )
}
