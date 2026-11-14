package arcanum.parser.sets.mkm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 16: move, must-attack, put-counters, reflexive-trigger, replacement, sacrifice, search and tap cards. */
class MkmChunk16MovesCountersAndSacrificesTest {
    private val yourHand = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val faceDown = ObjectFilter.HasStatus(Status.FaceDown)
    private val anyColorMana = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))

    // CR 708.2, 700.2: a split card has two faces; the first exiles up to two cards from a single graveyard (CR 109.1)
    @Test
    fun hushAndRuin() = cardFaces(
        Layout.Split,
        card(name = "Hush Test", cost = "{1}{B/G}", type = "Instant",
            text = "Exile up to two target cards from a single graveyard. Target player gains 2 life and draws a card.") to abilities(
            spell(move(target("card"), Zone.Exile), gainLife(2, Players.Target("player")), draw(1, Players.Target("player")),
                targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 2).copy(sameOwner = true), targetPlayer))),
        card(name = "Ruin Test", cost = "{4}{G/W}{G/W}", type = "Sorcery", text = "Destroy all artifacts and enchantments.") to abilities(
            spell(destroy(Objects.Each(or(artifact, enchantment))))),
    )

    // CR 607.2a: the cards exiled by the trigger are the ability's card memory, so the second ability returns all of them
    @Test
    fun hiddenBounty() = card(
        name = "Hidden Bounty Test",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            Whenever a creature you control attacks, exile the top card of your library face down. (You can't look at it.)
            {1}{R}, Discard your hand, Sacrifice this enchantment: Put all cards exiled with this enchantment into their owners' hands.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl)),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled")))),
        Ability.Activated(cost(mana("{1}{R}"), CostPart.Discard(CostObjects.These(Objects.Each(yourHand))), sacrificeSelf),
            Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Remembered("exiled"), Destination(Zone.Hand))))), anyTime),
    )

    // CR 113.6m: the ability works from the graveyard it returns the card from
    @Test
    fun bonesOfTheClue() = card(
        name = "Bones of the Clue Test",
        cost = "{2}{U}{B}",
        type = "Creature — Zombie Detective",
        pt = "3/1",
        text = """
            Flying
            When you sacrifice a Clue, return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youSacrificeAClue, move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 610.3: the exile lasts until this enchantment leaves the battlefield
    @Test
    fun lockingChain() = card(
        name = "Locking Chain Test",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target creature an opponent controls until this enchantment leaves the battlefield. You gain 2 life.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)), gainLife(2),
            targets = listOf(slot("creature", and(creature, opponents)))),
    )

    // CR 506.2: attacking creatures
    @Test
    fun strikeDownRaider() = card(
        name = "Strike Down Raider Test",
        cost = "{1}{W}",
        type = "Instant",
        text = "Exile target attacking creature.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 400.3: the owner of the object puts it into their library (CR 401.4: the owner chooses top or bottom)
    @Test
    fun turnedBackTide() = card(
        name = "Turned Back Tide Test",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = "The owner of target spell or nonland permanent puts it on their choice of the top or bottom of their library.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            Step.If(Condition.Chose("position", "top"),
                then = listOf(move(target("permanent"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", or(spellFilter, and(nonland, permanentFilter)))),
        ),
    )

    @Test
    fun graveRecon() = card(
        name = "Grave Recon Test",
        cost = "{1}{G}{G}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, return target land card from your graveyard to the battlefield tapped.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), move(target("card"), Zone.Battlefield, tapped = true),
            targets = listOf(slot("card", inYourGraveyard(land)))),
    )

    // CR 701.9: the opponent chooses the card to exile from their hand
    @Test
    fun slyInformant() = card(
        name = "Sly Informant Test",
        cost = "{1}{B}",
        type = "Creature — Elf Detective",
        pt = "1/1",
        text = "When this creature enters, target opponent exiles a card from their hand.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
                bind = "chosen", chooser = Players.Target("opponent")),
            move(Objects.Bound("chosen"), Zone.Exile),
            targets = listOf(targetOpponent)),
    )

    // CR 508.1d: attacks each combat if able
    @Test
    fun crimsonHerring() = card(
        name = "Crimson Herring Test",
        cost = "{1}{R}",
        type = "Artifact Creature — Clue Fish",
        pt = "2/2",
        text = """
            Haste
            This creature attacks each combat if able.
            {2}, Sacrifice this creature: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)),
        activated(cost(mana("{2}"), sacrificeSelf), draw(1)),
    )

    // CR 701.21: sacrificing an artifact is the event; the mana is added after the counter
    @Test
    fun scrapNovelist() = card(
        name = "Scrap Novelist Test",
        cost = "{2}{R}",
        type = "Creature — Goblin Bard",
        pt = "1/3",
        text = "Whenever you sacrifice an artifact, put a +1/+1 counter on this creature and add {R}.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Sacrifice(and(artifact, you))), counters(Objects.Self),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))),
    )

    // CR 601.2d: three counters divided among one to three targets, then life equal to the greatest toughness
    @Test
    fun threadWeaver() = card(
        name = "Thread Weaver Test",
        cost = "{5}{G}{G}",
        type = "Creature — Spider",
        pt = "3/3",
        text = """
            Reach
            When this creature enters, distribute three +1/+1 counters among one, two, or three target creatures, then you gain life equal to the greatest toughness among creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(entersSelf, Instructions(
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = n(1), max = n(3), divided = n(3))),
            steps = listOf(doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
                doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.Toughness, Objects.Each(creatureYouControl), Combine.Greatest)))))),
    )

    // CR 603.2: the trigger fires for entering and for attacking
    @Test
    fun guardOfTheCourt() = card(
        name = "Guard of the Court Test",
        cost = "{4}{W}",
        type = "Creature — Giant Soldier",
        pt = "4/4",
        text = "Whenever this creature enters or attacks, put a +1/+1 counter on target creature you control with power 2 or less.",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, counters(target("creature")),
            targets = listOf(slot("creature", and(creature, you, power(Comparison.AtMost, 2))))),
    )

    // CR 701.21, 603.12: "{T}, Sacrifice an artifact" is a cost; the pay of {R} is optional; the haste follows the token
    @Test
    fun goblinBaron() = card(
        name = "Goblin Baron Test",
        cost = "{2}{R}",
        type = "Legendary Creature — Goblin",
        pt = "3/3",
        text = """
            Haste
            {T}, Sacrifice an artifact: Put a +1/+1 counter on each Goblin you control.
            Whenever an artifact is put into a graveyard from the battlefield, you may pay {R}. If you do, create a 1/1 red Goblin creature token. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(tap, sacrificeA(artifact)), counters(Objects.Each(and(sub("Goblin"), you)))),
        triggered(dies(artifact),
            Step.Pay(manaCost("{R}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                create(creatureToken("Goblin", listOf(Color.Red), 1, 1), bind = "token"),
                gains(Objects.Bound("token"), Keyword.Haste)))),
    )

    // CR 708.8: a face-down permanent turned face up; the new object is the same permanent (CR 708.9)
    @Test
    fun watchfulSentry() = card(
        name = "Watchful Sentry Test",
        cost = "{G}{W}",
        type = "Creature — Elf Archer",
        pt = "1/3",
        text = """
            Reach
            Whenever a face-down permanent you control is turned face up, put a +1/+1 counter on it and a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(on(EventPattern.TurnedFaceUp(and(faceDown, permanentFilter, you))),
            counters(Objects.EventObject(EventRole.Object)), counters(Objects.Self)),
    )

    // CR 603.4: the end step check looks at what entered this turn face down
    @Test
    fun burrowingTipster() = card(
        name = "Burrowing Tipster Test",
        cost = "{1}{G}",
        type = "Creature — Mole Scout",
        pt = "1/1",
        text = """
            At the beginning of your end step, if a face-down creature entered the battlefield under your control this turn, put a +1/+1 counter on this creature.
            {T}: Add {G}.
        """,
    ) parsesTo abilities(
        triggered(yourEndStep, counters(Objects.Self),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(faceDown, creature, you), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
    )

    // CR 603.12: the reflexive trigger has its own target, chosen when it is put on the stack
    @Test
    fun scrapperCrook() = card(
        name = "Scrapper Crook Test",
        cost = "{4}{R}",
        type = "Creature — Lizard Warrior",
        pt = "5/4",
        text = "When this creature enters, you may sacrifice an artifact. When you do, this creature deals 3 damage to any target.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(sacrificeA(artifact)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(damage(3, toAny), targets = listOf(anyTarget))))),
    )

    @Test
    fun removerOfTheCourt() = card(
        name = "Remover of the Court Test",
        cost = "{3}{B}{B}",
        type = "Creature — Gorgon Assassin",
        pt = "3/3",
        text = "When this creature enters, you may sacrifice an artifact or creature. When you do, exile target creature an opponent controls.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(sacrificeA(or(artifact, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(move(target("creature"), Zone.Exile),
                targets = listOf(slot("creature", and(creature, opponents))))))),
    )

    // CR 601.2h: tapping an untapped artifact or land is the unless-cost; failing it sacrifices the land
    @Test
    fun crossingPoint() = card(
        name = "Crossing Point Test",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, sacrifice it unless you tap an untapped artifact or land you control.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf,
            Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, land), count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Sacrifice(Objects.Self))))),
        anyColorMana,
    )

    // CR 602.2b: the tapped creature is an untapped creature you control (the filter names only the kind)
    @Test
    fun lootedHideout() = card(
        name = "Looted Hideout Test",
        cost = null,
        type = "Artifact Land — Clue",
        text = """
            This land enters tapped.
            {T}: Add {C}.
            {T}, Tap an untapped creature you control: Add one mana of any color.
            {2}, Sacrifice this land: Draw a card.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(creature, count = n(1)))),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{2}"), sacrificeSelf), draw(1)),
    )

    // CR 701.21: each opponent chooses what they sacrifice
    @Test
    fun stingingChoice() = card(
        name = "Stinging Choice Test",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Each opponent sacrifices an artifact of their choice.
            • Each opponent sacrifices an enchantment of their choice.
            • Each opponent sacrifices a creature with flying of their choice.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            eachOpponentSacrifices(artifact),
            eachOpponentSacrifices(enchantment),
            eachOpponentSacrifices(and(creature, kw(Keyword.Flying))),
        ))),
    )

    // CR 701.23: both abilities leave the battlefield with the land as a cost
    @Test
    fun hollowTunnel() = card(
        name = "Hollow Tunnel Test",
        cost = null,
        type = "Land",
        text = """
            {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            {T}, Sacrifice this land: Target creature with power 2 or less can't be blocked this turn.
        """,
    ) parsesTo abilities(
        activated(cost(tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        activated(cost(tap, sacrificeSelf), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
            targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 2))))),
    )

    // CR 122.1g: a stun counter; "if you do" follows the optional counter
    @Test
    fun stoutWall() = card(
        name = "Stout Wall Test",
        cost = "{1}{W}",
        type = "Artifact Creature — Wall",
        pt = "0/4",
        text = """
            Defender
            {2}{W}, {T}: Tap target creature. You may put a stun counter on it. If you do, put a stun counter on this creature. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{2}{W}"), tap), tapIt(target("creature")),
            Step.Pay(null, steps = listOf(doing(Effect.PutCounters(target("creature"), CounterKind.Stun, n(1)))), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.PutCounters(Objects.Self, CounterKind.Stun, n(1)))),
            targets = listOf(slot("creature", creature))),
    )

    private fun eachOpponentSacrifices(filter: ObjectFilter): Mode = mode(
        Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
            Step.Choose(Choice.Objects(and(filter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)), bind = "sacrificed",
                chooser = Players.Bound("opponent")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true))
}
