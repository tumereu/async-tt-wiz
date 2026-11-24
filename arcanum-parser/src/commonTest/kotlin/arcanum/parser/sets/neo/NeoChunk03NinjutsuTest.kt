package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.abilities
import kotlin.test.Test

/** Chunk 3: ninjutsu (CR 702.49) and the combat damage triggers of Ninjas. */
class NeoChunk03NinjutsuTest {
    private fun ninjutsuLine(cost: String) =
        "Ninjutsu $cost ($cost, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)"
    private fun ninjutsu(cost: String) = Keyword.Ninjutsu(manaCost(cost))
    private fun ninja(name: String, cost: String, type: String, pt: String, ninjutsuCost: String, vararg lines: String) =
        card(name = name, cost = cost, type = type, pt = pt, text = (listOf(ninjutsuLine(ninjutsuCost)) + lines).joinToString("\n"))
    private val thatPlayerControls = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))
    private fun entersWithCounter(keyword: KeywordKind) = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(CounterKind.Keyword(keyword), n(1)))))
    /** "you may remove a <keyword> counter from it. When you do, …" (a cost paid on resolution, CR 118.12; the reflexive trigger, CR 603.12) */
    private fun removeCounterWhenYouDo(keyword: KeywordKind, vararg steps: Step, targets: List<TargetSlot> = emptyList()) = listOf(
        Step.Pay(cost(CostPart.RemoveCounters(CounterKind.Keyword(keyword), n(1), CostObjects.Self)), payer = Players.You, bind = "paid"),
        Step.If(Condition.Did("paid"), then = listOf(reflexive(*steps, targets = targets))))
    private val ninjaOrRogue = or(sub("Ninja"), sub("Rogue"))

    // CR 702.49a, 122.1b (a menace counter), 603.12 ("When you do"), 701.20a (that player reveals their hand)
    @Test
    fun clenchedFistNinja() = card(
        name = "Clenched-Fist Ninja",
        cost = "{2}{B}",
        type = "Creature — Human Ninja",
        pt = "3/3",
        text = """
            Ninjutsu {2}{B}
            This creature enters with a menace counter on it.
            Whenever this creature deals combat damage to a player, you may remove a menace counter from it. When you do, that player reveals their hand and you choose a nonland card from it. Exile that card.
        """,
    ) parsesTo abilities(
        ninjutsu("{2}{B}"),
        entersWithCounter(Keyword.Menace.kind),
        triggered(combatDamageToAPlayer, *removeCounterWhenYouDo(Keyword.Menace.kind,
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer)))))),
            Step.Choose(Choice.Objects(and(nonland, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer))), n(1), n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile)).toTypedArray()),
    )

    // CR 122.1 (a creature without a +1/+1 counter as the target), 510.2
    @Test
    fun twiningProwler() = ninja("Twining Prowler", "{1}{G}", "Creature — Snake Ninja", "2/1", "{1}{G}",
        "Whenever this creature deals combat damage to a player, put a +1/+1 counter on target creature you control that doesn't have a +1/+1 counter on it.",
    ) parsesTo abilities(
        ninjutsu("{1}{G}"),
        triggered(combatDamageToAPlayer, counters(target("creature")),
            targets = listOf(slot("creature", and(creature, you, not(ObjectFilter.HasCounter(p1p1, atLeast = 1)))))),
    )

    // CR 120.3, 603.2c ("that damage": the damage the trigger saw), 110.2a (put onto the battlefield, not cast)
    @Test
    fun hiddenMachinist() = ninja("Hidden Machinist", "{2}{U}", "Artifact Creature — Human Ninja", "2/4", "{1}{U}",
        "Whenever this creature deals combat damage to a player, you may put an artifact card with mana value less than or equal to that damage from your hand onto the battlefield.",
    ) parsesTo abilities(
        ninjutsu("{1}{U}"),
        triggered(combatDamageToAPlayer,
            Step.Choose(Choice.Objects(and(artifact, ObjectFilter.ManaValue(Comparison.AtMost, Value.EventAmount), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 406.3, 601.2 (play one of the exiled cards this turn), 118.9 (life rather than its mana cost), 305.1 (a land is played, not paid for)
    @Test
    fun keshoStarSagesHeir() = ninja("Kesho, Star Sage's Heir", "{1}{B}{B}", "Legendary Creature — Rat Ninja", "3/2", "{3}{B}",
        "Whenever Kesho deals combat damage to a player, exile the top card of each player's library. Until end of turn, you may play one of those cards. If you cast a spell this way, pay life equal to its mana value rather than paying its mana cost.",
    ) parsesTo abilities(
        ninjutsu("{3}{B}"),
        triggered(combatDamageToAPlayer,
            doing(Effect.Move(Objects.LibraryCards(Players.Each(PlayerFilter.Any), n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, total = 1,
                cost = cost(CostPart.PayLife(Value.Of(NumericProperty.ManaValue, Objects.Affected, Combine.Single))))),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
    )

    // CR 601.2f, 602.2b (an activated ability's cost reduced), 613.4c (other Ninja and Rogue creatures)
    @Test
    fun ashPeltSensei() = ninja("Ash-Pelt Sensei", "{U}{B}", "Creature — Rat Ninja", "2/2", "{U}{B}",
        "Ninjutsu abilities you activate cost {1} less to activate.",
        "Other Ninja and Rogue creatures you control get +1/+1.",
    ) parsesTo abilities(
        ninjutsu("{U}{B}"),
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(keyword = Keyword.Ninjutsu.Kind), you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(pt(1, 1), affects = Objects.Each(and(another, ninjaOrRogue, creature, you))),
    )

    // CR 702.49a, 702.4b (double strike)
    @Test
    fun swordSquallKitsune() = ninja("Sword-Squall Kitsune", "{2}{W}", "Creature — Fox Ninja", "2/2", "{3}{W}",
        "Double strike",
    ) parsesTo abilities(ninjutsu("{3}{W}"), Keyword.DoubleStrike)

    // CR 702.49a (the reminder text is no rule)
    @Test
    fun ronsaiDuskStalker() = ninja("Ronsai Dusk-Stalker", "{4}{B}{B}", "Creature — Ogre Ninja", "5/5", "{3}{B}") parsesTo abilities(ninjutsu("{3}{B}"))

    // CR 118.12 (a discard paid on resolution), 603.12 ("When you do"), 510.2 (that player: the one dealt the damage)
    @Test
    fun ronsaiHushBlade() = ninja("Ronsai Hush-Blade", "{1}{B}", "Creature — Human Ninja", "2/1", "{1}{B}",
        "Whenever this creature deals combat damage to a player, you may discard a creature card. When you do, destroy target creature or planeswalker that player controls.",
    ) parsesTo abilities(
        ninjutsu("{1}{B}"),
        triggered(combatDamageToAPlayer,
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(creature, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("permanent")),
                targets = listOf(slot("permanent", and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), thatPlayerControls))))))),
    )

    // CR 122.1b (a deathtouch counter), 118.12, 603.12
    @Test
    fun kappaGearBreaker() = card(
        name = "Kappa Gear-Breaker",
        cost = "{1}{G}",
        type = "Creature — Turtle Ninja",
        pt = "1/3",
        text = """
            Ninjutsu {1}{G}
            This creature enters with a deathtouch counter on it.
            Whenever this creature deals combat damage to a player, you may remove a deathtouch counter from it. When you do, exile target artifact or enchantment that player controls.
        """,
    ) parsesTo abilities(
        ninjutsu("{1}{G}"),
        entersWithCounter(Keyword.Deathtouch.kind),
        triggered(combatDamageToAPlayer, *removeCounterWhenYouDo(Keyword.Deathtouch.kind, move(target("permanent"), Zone.Exile),
            targets = listOf(slot("permanent", and(or(artifact, enchantment), thatPlayerControls)))).toTypedArray()),
    )

    // CR 118.12 ("you may draw a card. If you do": an optional action), 603.4 n/a: "unless it entered this turn" is checked on resolution
    @Test
    fun starCircuitIntruder() = ninja("Star-Circuit Intruder", "{1}{U}", "Enchantment Creature — Human Ninja", "2/1", "{U}",
        "Whenever this creature deals combat damage to a player, you may draw a card. If you do, discard a card unless this creature entered this turn.",
    ) parsesTo abilities(
        ninjutsu("{U}"),
        triggered(combatDamageToAPlayer,
            Step.Pay(steps = listOf(draw(1)), bind = "paid", payer = Players.You),
            Step.If(Condition.Did("paid"), then = listOf(Step.If(
                Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield, objects = ObjectFilter.Any)))),
                then = listOf(discard(1)))))),
    )

    // CR 115.1 (up to one target), 400.3 (its owner's hand)
    @Test
    fun starsnareExpert() = ninja("Starsnare Expert", "{3}{U}", "Creature — Human Ninja", "2/2", "{2}{U}",
        "When this creature enters, return up to one target creature to its owner's hand.",
    ) parsesTo abilities(
        ninjutsu("{2}{U}"),
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature, min = 0))),
    )

    // CR 702.15 (lifelink)
    @Test
    fun gekkaiLurker() = ninja("Gekkai Lurker", "{3}{B}", "Artifact Creature — Rat Ninja", "3/2", "{1}{B}",
        "Lifelink",
    ) parsesTo abilities(ninjutsu("{1}{B}"), Keyword.Lifelink)

    // CR 702.2, 702.15 (two keywords until end of turn)
    @Test
    fun nezumiSkulker() = ninja("Nezumi Skulker", "{1}{B}", "Artifact Creature — Rat Ninja", "3/1", "{1}{B}",
        "When this creature enters, target creature you control gains deathtouch and lifelink until end of turn.",
    ) parsesTo abilities(
        ninjutsu("{1}{B}"),
        triggered(entersSelf, gains(target("creature"), Keyword.Deathtouch, Keyword.Lifelink), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.2c ("one or more … deal combat damage to a player": once per player dealt damage), 111.10a (Treasure)
    @Test
    fun thrivingPickpocket() = ninja("Thriving Pickpocket", "{2}{U}", "Creature — Human Ninja", "3/2", "{1}{U}",
        "Whenever one or more Ninja or Rogue creatures you control deal combat damage to a player, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(
        ninjutsu("{1}{U}"),
        Ability.Triggered(
            trigger = Trigger.On(listOf(EventPattern.Damage(and(ninjaOrRogue, creature, you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
                oneOrMore = true, batchPerPlayer = true),
            instructions = Instructions(steps = listOf(createTreasure())),
        ),
    )

    // CR 110.4b (a permanent card in a graveyard), 400.3
    @Test
    fun fallLeafAvenger() = ninja("Fall-Leaf Avenger", "{3}{G}{G}", "Creature — Insect Ninja", "6/5", "{3}{G}",
        "Whenever this creature deals combat damage to a player, return target permanent card from your graveyard to your hand.",
    ) parsesTo abilities(
        ninjutsu("{3}{G}"),
        triggered(combatDamageToAPlayer, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(permanentCard)))),
    )

    // CR 603.4 (an intervening "if"), 707.2 (a copy), 508.4 (a token put onto the battlefield attacking)
    @Test
    fun hundredFacedPhantom() = ninja("Hundred-Faced Phantom", "{U}", "Creature — Human Ninja", "1/1", "{2}{U}{U}",
        "Flying",
        "When this creature enters from your hand, if it's attacking, create a token that's a copy of another target attacking creature. The token enters tapped and attacking.",
    ) parsesTo abilities(
        ninjutsu("{2}{U}{U}"),
        Keyword.Flying,
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Hand, to = Zone.Battlefield)),
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.You, tapped = true, attacking = AttackTarget.Chosen)),
            targets = listOf(slot("creature", and(another, attacking, creature))),
            interveningIf = Condition.ObjectMatches(Objects.Self, attacking)),
    )
}
