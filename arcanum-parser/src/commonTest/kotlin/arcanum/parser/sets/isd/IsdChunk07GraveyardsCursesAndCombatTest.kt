package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 07: the card an additional cost exiled, Curses attached to a player, "that player" after a counter, an attacking token exiled
 * at end of combat, cards returned at random, a library's bottom card, protection from a non-subtype, and damage to the other combatant.
 */
class IsdChunk07GraveyardsCursesAndCombatTest {
    private val yourHand = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
    /** "return [count] [filter] at random from your graveyard to [to]": picked by the host, no player chooses (CR 608.2c). */
    private fun atRandom(count: Int, filter: ObjectFilter, to: Zone) = arrayOf(
        Step.Bind(Objects.Random(Objects.Each(inYourGraveyard(filter)), n(count)), "chosen"),
        move(Objects.Bound("chosen"), to),
    )

    // CR 601.2f, 601.2h: the exile is part of the cost; CR 608.2h: the exiled card's power as the spell resolves (a card in exile)
    @Test
    fun cadaverThrust() = card(
        name = "Cadaver Thrust",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, exile a creature card from your graveyard.
            Cadaver Thrust deals damage equal to the exiled card's power to target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.Exile(CostObjects.Choose(creature, count = n(1)), from = Zone.Graveyard,
            postBind = "exiled")))),
        spell(damage(power(Objects.Bound("exiled")), toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 303.4e: a Curse attached to you is an Aura enchanting you; CR 702.11d: "you have hexproof"
    @Test
    fun hexwardSphere() = card(
        name = "Hexward Sphere",
        cost = "{4}",
        type = "Artifact",
        text = """
            When this artifact enters, destroy all Curses attached to you.
            You have hexproof. (You can't be the target of spells or abilities your opponents control, including Aura spells.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, destroy(Objects.Each(and(sub("Curse"), ObjectFilter.Attached(toPlayers = Players.You))))),
        playerStatic(Mechanic.CantBeTargeted(by = opponents)),
    )

    // CR 118.12: the counter unless paid; "that player" is the spell's controller, who discards either way (CR 608.2c)
    @Test
    fun dreadfulFancy() = card(
        name = "Dreadful Fancy",
        cost = "{2}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {1}. That player discards a card.",
    ) parsesTo abilities(
        spell(
            Step.Pay(cost = manaCost("{1}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            discard(1, players = Players.ControllerOf(target("spell"))),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    // CR 508.4: the token attacks without being declared; CR 603.7: the delayed trigger exiles it at end of combat; CR 702.11a (hexproof)
    @Test
    fun shadeOfSaintVoren() = card(
        name = "Shade of Saint Voren",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Spirit Cleric",
        pt = "2/2",
        text = """
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            Whenever Shade of Saint Voren attacks, create a 4/4 white Angel creature token with flying that's tapped and attacking. Exile that token at end of combat.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        triggered(attacksSelf,
            doing(Effect.CreateToken(creatureToken("Angel", listOf(Color.White), 4, 4, abilities = listOf(Keyword.Flying)), n(1), Players.You,
                tapped = true, attacking = AttackTarget.Chosen, bind = "token")),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.EndOfCombat, PlayerFilter.Any),
                Instructions(steps = listOf(move(Objects.Bound("token"), Zone.Exile))))),
        ),
    )

    // CR 400.7: "it" is the card in exile; the target player chooses which card of their graveyard
    @Test
    fun tombSpade() = card(
        name = "Tomb Spade",
        cost = "{2}",
        type = "Artifact",
        text = "{2}, {T}: Target player exiles a card from their graveyard. If it's a creature card, you gain 2 life.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap),
            Step.Choose(Choice.Objects(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "chosen", chooser = Players.Target("player")),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), creature), gainLife(2)),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 702.16a: protection from non-Human creatures, gained until end of turn by the creatures you control as it resolves (CR 611.2c)
    @Test
    fun shieldFromWickedness() = card(
        name = "Shield from Wickedness",
        cost = "{1}{W}",
        type = "Instant",
        text = "Creatures you control gain protection from non-Human creatures until end of turn.",
    ) parsesTo abilities(
        spell(gains(Objects.Each(creatureYouControl), Keyword.Protection(and(not(sub("Human")), creature)))),
    )

    // CR 701.17a: milling is the top cards, so this isn't a mill; CR 400.7: "it" is the card in the graveyard
    @Test
    fun cryptHatch() = card(
        name = "Crypt Hatch",
        cost = "{2}",
        type = "Artifact",
        text = "{3}, {T}: Target player puts the bottom card of their library into their graveyard. If it's a creature card, you create a 2/2 black Zombie creature token.",
    ) parsesTo abilities(
        activated(cost(mana("{3}"), tap),
            doing(Effect.Move(Objects.LibraryCards(Players.Target("player"), n(1), fromTop = false), Destination(Zone.Graveyard), postBind = "put")),
            ifThen(Condition.ObjectMatches(Objects.Bound("put"), creature), create(zombie)),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 608.2c: chosen at random as the ability resolves, not targeted; CR 603.2: the cast trigger
    @Test
    fun spellsnapFiends() = card(
        name = "Spellsnap Fiends",
        cost = "{5}{R}",
        type = "Creature — Devil",
        pt = "4/4",
        text = """
            At the beginning of your upkeep, return an instant or sorcery card at random from your graveyard to your hand.
            Whenever you cast an instant or sorcery spell, this creature gets +4/+0 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, *atRandom(1, or(instant, sorcery), Zone.Hand)),
        triggered(youCast(or(instant, sorcery)), pump(Objects.Self, 4, 0)),
    )

    // CR 608.2c: a Zombie card chosen at random as the ability resolves
    @Test
    fun ghastlifter() = card(
        name = "Ghastlifter",
        cost = "{1}{B}{B}",
        type = "Creature — Zombie",
        pt = "2/2",
        text = "When this creature enters, return a Zombie card at random from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(entersSelf, *atRandom(1, sub("Zombie"), Zone.Hand)),
    )

    // CR 608.2c: two cards at random (all of them if there are fewer)
    @Test
    fun askTheStars() = card(
        name = "Ask the Stars",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Return two cards at random from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(*atRandom(2, cardFilter, Zone.Hand)),
    )

    // CR 400.7: "it" is the card in the graveyard; exiled first, it can't be one of the two returned (CR 608.2c, 702.19 trample)
    @Test
    fun mossgraveBehemoth() = card(
        name = "Mossgrave Behemoth",
        cost = "{4}{G}{G}{G}",
        type = "Creature — Insect",
        pt = "8/8",
        text = """
            Trample
            When this creature dies, exile it, then return two creature cards at random from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(diesSelf, move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled"), *atRandom(2, creature, Zone.Battlefield)),
    )

    // CR 702.34a: an exiled card with flashback you own, targeted
    @Test
    fun glyphRecurrence() = card(
        name = "Glyph Recurrence",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Return target exiled card with flashback you own to your hand.",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand),
            targets = listOf(slot("card", and(inZone(Zone.Exile), kw(Keyword.Flashback.Kind), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    )

    // CR 303.4f: an Aura put onto the battlefield without being cast is attached as it enters; the player is the ability's target;
    // CR 701.23a (search), 702.2b (deathtouch)
    @Test
    fun sourheartHexer() = card(
        name = "Sourheart Hexer",
        cost = "{4}{B}",
        type = "Creature — Human Shaman",
        pt = "1/2",
        text = """
            Deathtouch
            When this creature dies, you may search your library for a Curse card, put it onto the battlefield attached to target player, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(diesSelf, Step.May(listOf(
            search(sub("Curse")),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, attachedTo = Recipients(players = Players.Target("player"))))),
            shuffle,
        ), player = Players.You), targets = listOf(targetPlayer)),
    )

    // CR 604.3: a characteristic-defining ability, counted continuously; CR 510.3a: the combat damage trigger
    @Test
    fun gustwraith() = card(
        name = "Gustwraith",
        cost = "{3}{U}{U}",
        type = "Creature — Spirit",
        pt = "*/*",
        text = """
            Flying
            Gustwraith's power and toughness are each equal to the number of cards in your hand.
            Whenever this creature deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(Value.Count(yourHand), Value.Count(yourHand)), characteristicDefining = true),
        triggered(combatDamageToAPlayer, draw(1)),
    )

    // CR 509.3b, 509.3d: once for each creature it blocks or is blocked by; "that creature" is that other combatant
    @Test
    fun cindermawHound() = card(
        name = "Cindermaw Hound",
        cost = "{1}{R}",
        type = "Creature — Elemental Dog",
        pt = "2/1",
        text = "Whenever this creature blocks or becomes blocked by a creature, this creature deals 1 damage to that creature.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Block(ObjectFilter.Is(Objects.Self), creature, perAttacker = true),
            EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), creature, perBlocker = true))),
            damage(1, toObjects(Objects.EventObject(EventRole.OtherCombatant)))),
    )
}
