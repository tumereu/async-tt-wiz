package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 04: Alliance (CR 207.2c), connive (CR 701.50) and Vehicles with crew (CR 702.122). */
class SncChunk04AllianceConniveCrewTest {
    private val anotherCreatureYouControlEnters = enters(and(another, creatureYouControl))
    private fun alliance(vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        triggered(anotherCreatureYouControlEnters, *steps, targets = targets, abilityWord = "Alliance")
    private val secondResolution = Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2))
    private fun connive(objects: Objects = Objects.Self, amount: Value = n(1)): Step = Step.Do(Effect.Action(KeywordAction.Connive(objects, amount)))
    private val enchanted: Objects = Objects.AttachedTo(Objects.Self)
    private val exiledWith = Objects.Remembered("exiled")
    private fun remember(bound: String = "exiled"): Step = doing(Effect.Remember("exiled", Objects.Bound(bound)))
    private fun becomesArtifactCreature(objects: Objects): Step = untilEndOfTurn(objects, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)))

    // CR 207.2c, 509.1b (can't block this turn), 508.1d (a requirement to attack), 603.4; the requirement lasts through the declare
    // attackers step of its controller's next combat phase
    @Test
    fun sizzlingSoloist() = card(
        name = "Blazing Crooner",
        cost = "{3}{R}",
        type = "Creature — Human Citizen",
        pt = "3/2",
        text = "Alliance — Whenever another creature you control enters, target creature an opponent controls can't block this turn. If this is the second time this ability has resolved this turn, that creature attacks during its controller's next combat phase if able.",
    ) parsesTo abilities(
        alliance(
            untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            ifThen(secondResolution, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MustAttack()), Affected.Objects(target("creature")),
                Duration.ThroughNextStep(TurnStep.DeclareAttackers, Players.ControllerOf(target("creature")))))),
            targets = listOf(slot("creature", creatureOpponentControls)),
        ),
    )

    // CR 701.10a (double its power: it gets +X/+0, X its power as the ability resolves), 207.2c
    @Test
    fun devilishValet() = card(
        name = "Infernal Doorman",
        cost = "{2}{R}",
        type = "Creature — Devil Warrior",
        pt = "1/3",
        text = """
            Trample, haste
            Alliance — Whenever another creature you control enters, double this creature's power until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        alliance(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(power(Objects.Self), n(0)))),
    )

    // CR 115.3 ("other than this creature"), 207.2c
    @Test
    fun elegantEntourage() = card(
        name = "Graceful Retinue",
        cost = "{3}{G}",
        type = "Creature — Elf Druid",
        pt = "4/4",
        text = "Alliance — Whenever another creature you control enters, target creature other than this creature gets +1/+1 and gains trample until end of turn.",
    ) parsesTo abilities(
        alliance(untilEndOfTurn(target("creature"), pt(1, 1), has(Keyword.Trample)), targets = listOf(slot("creature", and(creature, another)))),
    )

    // CR 700.2 (a mode chosen this turn can't be chosen again this turn), 111.10a (a tapped Treasure)
    @Test
    fun galaGreeters() = card(
        name = "Soiree Welcomers",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        pt = "1/1",
        text = """
            Alliance — Whenever another creature you control enters, choose one that hasn't been chosen this turn —
            • Put a +1/+1 counter on this creature.
            • Create a tapped Treasure token.
            • You gain 2 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(anotherCreatureYouControlEnters, Instructions(modes = Modes(listOf(mode(counters(Objects.Self)), mode(createTreasure(tapped = true)),
            mode(gainLife(2))), min = 1, max = 1, onlyUnchosenThisTurn = true, chooser = Players.You)), abilityWord = "Alliance"),
    )

    // CR 614.1a ("instead": the draw replaces the scry), 603.4; "the second time this ability has resolved this turn" counts this resolution
    @Test
    fun rumorGatherer() = card(
        name = "Whisper Collector",
        cost = "{1}{W}{W}",
        type = "Creature — Elf Wizard",
        pt = "2/1",
        text = "Alliance — Whenever another creature you control enters, scry 1. If this is the second time this ability has resolved this turn, draw a card instead.",
    ) parsesTo abilities(
        alliance(ifThen(secondResolution, draw(1), otherwise = listOf(scry(1)))),
    )

    // CR 701.50a (connive), 603.10a (its power as it last existed), 400.7 (its own card in the graveyard is another object)
    @Test
    fun bodyLaunderer() = card(
        name = "Corpse Washer",
        cost = "{2}{B}{B}",
        type = "Creature — Ogre Rogue",
        pt = "3/3",
        text = """
            Deathtouch
            Whenever another nontoken creature you control dies, this creature connives.
            When this creature dies, return another target non-Rogue creature card with equal or lesser power from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.ZoneChange(and(another, not(token), creatureYouControl), from = Zone.Battlefield, to = Zone.Graveyard)), connive()),
        triggered(diesSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", and(
            not(ObjectFilter.Is(Objects.EventObject(EventRole.After))), not(sub("Rogue")), creature,
            ObjectFilter.Power(Comparison.AtMost, power(Objects.EventObject(EventRole.Before))), *yourGraveyard.toTypedArray())))),
    )

    // CR 701.50a, 603.12 (the reflexive trigger: it connives even when it left the battlefield, CR 701.50c), 115.6 (up to one target)
    @Test
    fun obscuraInterceptor() = card(
        name = "Shadowveil Intercepter",
        cost = "{1}{W}{U}{B}",
        type = "Creature — Octopus Wizard",
        pt = "3/1",
        text = """
            Flash
            Lifelink
            When this creature enters, it connives. When it connives this way, return up to one target spell to its owner's hand. (To have a creature connive, draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Lifelink,
        triggered(entersSelf, connive(), reflexive(move(target("spell"), Zone.Hand), targets = listOf(slot("spell", spellFilter, min = 0)))),
    )

    // CR 701.50a, 603.12
    @Test
    fun psychicPickpocket() = card(
        name = "Mindreach Cutpurse",
        cost = "{4}{U}",
        type = "Creature — Octopus Rogue",
        pt = "3/2",
        text = "When this creature enters, it connives. When it connives this way, return up to one target nonland permanent to its owner's hand. (To have a creature connive, draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)",
    ) parsesTo abilities(
        triggered(entersSelf, connive(),
            reflexive(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter), min = 0)))),
    )

    // CR 506.5 (attacking alone), 509.1b (can't be blocked), 303.4; the granted trigger connives the creature it's on (CR 701.50a)
    @Test
    fun securityBypass() = card(
        name = "Lockpick Charm",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            As long as enchanted creature is attacking alone, it can't be blocked.
            Enchanted creature has "Whenever this creature deals combat damage to a player, it connives." (Its controller draws a card, then discards a card. If they discarded a nonland card, they put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantBeBlocked, affects = enchanted, condition = Condition.ObjectMatches(enchanted, ObjectFilter.AttackingAlone)),
        static(has(triggered(combatDamageToAPlayer, connive())), affects = enchanted),
    )

    // CR 701.50a, 607.2a (the cards exiled with it), 400.7 (the discarded cards, still in the graveyard), 603.2c (one or more cards: once)
    @Test
    fun toluzCleverConductor() = card(
        name = "Veska, Sly Porter",
        cost = "{W/U}{U}{U/B}",
        type = "Legendary Creature — Human Rogue",
        pt = "3/1",
        text = """
            When Veska enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
            Whenever you discard one or more cards, exile them from your graveyard.
            When Veska dies, put the cards exiled with it into their owner's hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, connive()),
        triggered(Trigger.On(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), oneOrMore = true),
            Step.Do(Effect.Move(Objects.Where(Objects.EventObject(EventRole.After), inZone(Zone.Graveyard)), Destination(Zone.Exile), postBind = "exiled")),
            remember()),
        triggered(diesSelf, move(exiledWith, Zone.Hand)),
    )

    // CR 701.50a, 602.1
    @Test
    fun hypnoticGrifter() = card(
        name = "Mesmer Hustler",
        cost = "{U}",
        type = "Creature — Human Rogue",
        pt = "1/2",
        text = "{3}: This creature connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)",
    ) parsesTo abilities(
        activated(manaCost("{3}"), connive()),
    )

    // CR 702.4b, 115.9a (becomes the target of a spell you control), 701.50a
    @Test
    fun illuminatorVirtuoso() = card(
        name = "Lamplight Duelist",
        cost = "{1}{W}",
        type = "Creature — Human Rogue",
        pt = "1/1",
        text = """
            Double strike
            Whenever this creature becomes the target of a spell you control, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), by = and(spellFilter, you))), connive(Objects.EventObject(EventRole.Object))),
    )

    // CR 701.50a; 603.10a (X is its power as it last existed)
    @Test
    fun raffinesSilencer() = card(
        name = "Hushblade Assassin",
        cost = "{2}{B}",
        type = "Creature — Human Assassin",
        pt = "1/1",
        text = """
            When this creature enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
            When this creature dies, target creature an opponent controls gets -X/-X until end of turn, where X is this creature's power.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, connive()),
        triggered(diesSelf, run {
            val x = Value.Times(power(Objects.EventObject(EventRole.Before)), -1)
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(x, x))
        }, targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 701.50e (connive N), 508.1 ("whenever you attack": once per combat), 702.21a (ward)
    @Test
    fun raffineSchemingSeer() = card(
        name = "Veyra, Plotting Oracle",
        cost = "{W}{U}{B}",
        type = "Legendary Creature — Sphinx Demon",
        pt = "1/4",
        text = """
            Flying, ward {1}
            Whenever you attack, target attacking creature connives X, where X is the number of attacking creatures. (Draw X cards, then discard X cards. Put a +1/+1 counter on that creature for each nonland card discarded this way.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(manaCost("{1}")),
        triggered(youAttack, connive(target("creature"), count(and(attacking, creature))), targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 510.1, 111.10a; "they" is the player dealt damage (CR 608.2c)
    @Test
    fun hoardHauler() = card(
        name = "Trove Wagon",
        cost = "{3}{R}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            Trample
            Whenever this Vehicle deals combat damage to a player, create a Treasure token for each artifact they control.
            Crew 3
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(combatDamageToAPlayer, createTreasure(count(and(artifact, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer)))))),
        Keyword.Crew(3),
    )

    // CR 610.3 (exiled until it leaves), 607.2a (the cards exiled with it), 400.7
    @Test
    fun mysteriousLimousine() = card(
        name = "Uncanny Town Car",
        cost = "{3}{W}{W}",
        type = "Artifact — Vehicle",
        pt = "4/4",
        text = """
            Whenever this Vehicle enters or attacks, exile up to one other target creature until this Vehicle leaves the battlefield. If a creature is put into exile this way, return each other card exiled with this Vehicle to the battlefield under its owner's control.
            Crew 2
        """,
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf,
            Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self, postBind = "exiled")), remember(),
            ifThen(Condition.NonEmpty("exiled"), Objects.Except(exiledWith, Objects.Bound("exiled")).let { others ->
                move(others, Zone.Battlefield, controller = Players.OwnerOf(others)) }),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
        Keyword.Crew(2),
    )

    // CR 604.3 (characteristic-defining), 607.2a (the cards exiled with it), 115.1 (from a single graveyard)
    @Test
    fun unlicensedHearse() = card(
        name = "Unbonded Funeral Coach",
        cost = "{2}",
        type = "Artifact — Vehicle",
        pt = "*/*",
        text = """
            {T}: Exile up to two target cards from a single graveyard.
            Unbonded Funeral Coach's power and toughness are each equal to the number of cards exiled with it.
            Crew 2
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")), remember(),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 2).copy(sameOwner = true))),
        static(Mechanic.SetPowerToughness(Value.Count(exiledWith), Value.Count(exiledWith)), characteristicDefining = true),
        Keyword.Crew(2),
    )

    // CR 205.1b (an artifact creature until end of turn), 111.10a, 702.122a
    @Test
    fun capennaExpress() = card(
        name = "Metro Freight Line",
        cost = "{3}{G}",
        type = "Artifact — Vehicle",
        pt = "6/6",
        text = """
            Sacrifice a Treasure: This Vehicle becomes an artifact creature until end of turn.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        activated(cost(sacrificeA(sub("Treasure"))), becomesArtifactCreature(Objects.Self)),
        Keyword.Crew(3),
    )

    // CR 702.122b ("crewed it this turn"), 115.6
    @Test
    fun getawayCar() = card(
        name = "Escape Sedan",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "4/3",
        text = """
            Haste
            Whenever this Vehicle attacks or blocks, return up to one target creature that crewed it this turn to its owner's hand.
            Crew 1
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(blocker = ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any))),
            move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(creature, ObjectFilter.CrewedThisTurn(Objects.Self)), min = 0))),
        Keyword.Crew(1),
    )
}
