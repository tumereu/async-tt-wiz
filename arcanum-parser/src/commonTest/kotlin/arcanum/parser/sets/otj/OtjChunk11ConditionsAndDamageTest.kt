package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 11: a crime condition, affinity for outlaws, creatures you control but don't own, the top X cards of an opponent's library,
 * a revealed hand's other choice, copies with an exception, damage by power to several targets, counts of spells cast this turn, damage
 * "as you cast this spell", several "up to one" targets, and per-opponent discards.
 */
class OtjChunk11ConditionsAndDamageTest {
    private val crimeThisTurn = Condition.HappenedThisTurn(EventPattern.CommitCrime(PlayerFilter.You), atLeast = 1, measure = Measure.Events)

    // CR 509.1b: can't be blocked while the condition holds; CR 700.13: a crime; CR 510.1: combat damage to a player
    @Test
    fun quickfootCutpurse() = card(
        name = "Quickfoot Cutpurse",
        cost = "{2}{U}",
        type = "Creature — Human Rogue",
        pt = "1/3",
        text = """
            This creature can't be blocked if you've committed a crime this turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            Whenever this creature deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked, condition = crimeThisTurn),
        triggered(combatDamageToAPlayer, draw(1)),
    )

    // CR 111.4, 107.3: X tokens with the quoted ability; CR 602.5d
    @Test
    fun raiseTheCrew() = card(
        name = "Raise the Crew",
        cost = "{X}{R}{W}",
        type = "Sorcery",
        text = """Create X 1/1 red Mercenary creature tokens with "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery."""",
    ) parsesTo abilities(
        spell(create(creatureToken("Mercenary", listOf(Color.Red), 1, 1, abilities = listOf(
            activated(cost(tap), pump(target("creature"), 1, 0), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery))),
            count = Value.Announced("X")), announced = listOf("X")),
    )

    // CR 702.41a: affinity for outlaws (CR 700.12: Assassins, Mercenaries, Pirates, Rogues and Warlocks)
    @Test
    fun spurhornBruiser() = card(
        name = "Spurhorn Bruiser",
        cost = "{4}{R}",
        type = "Creature — Minotaur Mercenary",
        pt = "5/4",
        text = """
            Affinity for outlaws (This spell costs {1} less to cast for each Assassin, Mercenary, Pirate, Rogue, and/or Warlock you control.)
            Trample
        """,
    ) parsesTo abilities(
        Keyword.Affinity(outlaw, "outlaws"),
        Keyword.Trample,
    )

    // CR 108.3, 108.4: controlled but not owned; CR 205.1b: a subtype added; CR 400.7: the exiled cards; CR 601.2h: mana of any type
    @Test
    fun grinningJadeSpark() = card(
        name = "Grinning Jade Spark",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Lizard Rogue",
        pt = "4/3",
        text = """
            Creatures you control but don't own are Mercenaries in addition to their other types.
            At the beginning of your upkeep, exile the top X cards of target opponent's library, where X is the number of outlaws you control. Until end of turn, you may cast spells from among those cards, and mana of any type can be spent to cast those spells.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Mercenary")))),
            affects = Objects.Each(and(creature, you, not(ObjectFilter.OwnedBy(PlayerFilter.You))))),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            doing(Effect.Move(Objects.LibraryCards(Players.Target("opponent"), count(and(outlaw, you)), fromTop = true), Destination(Zone.Exile),
                postBind = "exiled")),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, manaAsAnyColor = true, holder = PlayerFilter.You)),
            targets = listOf(targetOpponent)),
    )

    // CR 701.9b: the player discards the card you chose; otherwise you may move a face-up exiled card of theirs (CR 406.3, 608.2c)
    @Test
    fun shackledBargain() = card(
        name = "Shackled Bargain",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You may choose a nonland card from it. If you do, they discard it. Otherwise, you may put a face-up exiled card they own into their graveyard.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Pay(steps = listOf(Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You)),
                payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                otherwise = listOf(
                    Step.Choose(Choice.Objects(and(cardFilter, inZone(Zone.Exile), not(ObjectFilter.HasStatus(Status.FaceDown)),
                        ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(0), n(1)), bind = "put", chooser = Players.You),
                    move(Objects.Bound("put"), Zone.Graveyard))),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 707.2, 707.9b: a copy except it has hexproof, until end of turn (CR 611.2a); CR 606: loyalty abilities; CR 700.13
    @Test
    fun veskTheMimicBoss() = card(
        name = "Vesk, the Mimic Boss",
        cost = "{2}{G}{U}",
        type = "Legendary Planeswalker — Vesk",
        loyalty = "3",
        text = """
            At the beginning of combat on your turn, Vesk becomes a copy of up to one target creature you control until end of turn, except he has hexproof.
            [+1]: Draw two cards. If you've committed a crime this turn, discard a card. Otherwise, discard two cards.
            [−1]: Create a 3/3 green Elk creature token.
            [−5]: For each other nonland permanent you control, create a token that's a copy of that permanent.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            untilEndOfTurn(Objects.Self, Mechanic.CopyOf(target("creature"), listOf(CopyException.AddAbilities(listOf(Keyword.Hexproof))))),
            targets = listOf(slot("creature", creatureYouControl, min = 0))),
        loyalty(1, draw(2), ifThen(crimeThisTurn, discard(1), otherwise = listOf(discard(2)))),
        loyalty(-1, create(creatureToken("Elk", listOf(Color.Green), 3, 3))),
        loyalty(-5, Step.ForEach(Each.Objects(Objects.Each(and(another, nonland, permanentFilter, you))), bind = "permanent",
            steps = listOf(doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("permanent")), n(1), Players.You))))),
    )

    // CR 120.3: only the first target deals damage; "other" (CR 115.3): two creatures, neither of them the first
    @Test
    fun treacheryAtTheDepot() = card(
        name = "Treachery at the Depot",
        cost = "{4}{G}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to each of two other target creatures.",
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(target("creatures")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl),
                TargetSlot("creatures", TargetKind.Objects(creature), min = n(2), max = n(2), distinctFrom = listOf("creature")))),
    )

    // CR 601.2i: spells cast this turn by the player who cast the spell, that one included
    @Test
    fun spellbiterGecko() = card(
        name = "Spellbiter Gecko",
        cost = "{1}{R}",
        type = "Creature — Lizard",
        pt = "1/4",
        text = "Whenever a player casts a noncreature spell, this creature deals damage to that player equal to the number of noncreature spells they've cast this turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(noncreatureSpell, PlayerFilter.Any)),
            damage(Value.EventsThisTurn(EventPattern.Cast(noncreatureSpell, PlayerFilter.Is(Players.EventPlayer)), Measure.Events),
                Recipients(players = Players.EventPlayer))),
    )

    // CR 603.10a: the creature's power as it last existed on the battlefield; CR 122.6: two additional counters
    @Test
    fun grimlordEnlists() = card(
        name = "Grimlord Enlists",
        cost = "{3}{B}{R}",
        type = "Legendary Enchantment",
        text = """
            When Grimlord Enlists enters, return target creature card from your graveyard to the battlefield with two additional +1/+1 counters on it.
            Whenever a legendary creature you control dies, Grimlord Enlists deals damage equal to that creature's power to target opponent.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(p1p1, n(2)))))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
        triggered(on(EventPattern.ZoneChange(and(legendary, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            damage(power(Objects.EventObject(EventRole.Before)), Recipients(players = Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    // CR 601.2i: whether you controlled a Mount is checked as the spell becomes cast, not as it resolves
    @Test
    fun veerAside() = card(
        name = "Veer Aside",
        cost = "{W}",
        type = "Instant",
        text = "Veer Aside deals 2 damage to target attacking or blocking creature. Veer Aside deals 4 damage to that creature instead if you controlled a Mount as you cast this spell.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.AsCast(Condition.Exists(and(sub("Mount"), you), atLeast = 1)),
                damage(4, toObjects(target("creature"))),
                otherwise = listOf(damage(2, toObjects(target("creature"))))),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature))),
        ),
    )

    // CR 107.3c: X defined by the text; "other spells": not this one (CR 601.2i)
    @Test
    fun rumblingVolley() = card(
        name = "Rumbling Volley",
        cost = "{1}{R}",
        type = "Instant",
        text = "Rumbling Volley deals X damage to target creature, where X is 2 plus the number of other spells you've cast this turn.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(n(2), Value.EventsThisTurn(EventPattern.Cast(another, PlayerFilter.You), Measure.Events))),
            toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 115.3: "other": the token can't be the first target; CR 115.1d: up to one
    @Test
    fun ricochetGambit() = card(
        name = "Ricochet Gambit",
        cost = "{4}{R}",
        type = "Instant",
        text = "Ricochet Gambit deals 6 damage to target creature and 2 damage to up to one other target creature token.",
    ) parsesTo abilities(
        spell(damage(6, toObjects(target("creature"))), damage(2, toObjects(target("token"))),
            targets = listOf(slot("creature", creature), TargetSlot("token", TargetKind.Objects(and(creature, token)), min = n(0), max = n(1),
                distinctFrom = listOf("creature")))),
    )

    // CR 115.1d: a slot for each "up to one target"; CR 701.8a: destroyed at once
    @Test
    fun blastCrate() = card(
        name = "Blast Crate",
        cost = "{2}",
        type = "Artifact",
        text = "{6}, {T}, Sacrifice this artifact: Destroy up to one target artifact, up to one target creature, and up to one target land.",
    ) parsesTo abilities(
        activated(cost(mana("{6}"), tap, sacrificeSelf), destroy(Objects.Union(listOf(target("artifact"), target("creature"), target("land")))),
            targets = listOf(slot("artifact", artifact, min = 0), slot("creature", creature, min = 0), slot("land", land, min = 0))),
    )

    // CR 700.12: a non-outlaw creature
    @Test
    fun gunDownTheMarshal() = card(
        name = "Gun Down the Marshal",
        cost = "{1}{B}",
        type = "Instant",
        text = "Destroy target non-outlaw creature. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws. Everyone else is fair game.)",
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(not(outlaw), creature)))),
    )

    // CR 601.2f: cost reduction; CR 101.4: the opponents discard at once; CR 400.3: each discards a card of their own
    @Test
    fun hollowedRaider() = card(
        name = "Hollowed Raider",
        cost = "{6}{B}",
        type = "Creature — Specter Rogue",
        pt = "4/2",
        text = """
            This spell costs {1} less to cast for each creature card in your graveyard.
            Flying
            When this creature enters, any number of target opponents each discard a card. For each of those opponents who didn't discard a card with mana value 4 or greater, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(creature))))),
        Keyword.Flying,
        triggered(entersSelf,
            doing(Effect.Discard(Players.Target("opponents"), n(1), ObjectFilter.Any, postBind = "discarded")),
            Step.ForEach(Each.Players(Players.Target("opponents")), bind = "player", together = true, steps = listOf(
                ifThen(Condition.Not(Condition.ObjectMatches(Objects.Where(Objects.Bound("discarded"), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))),
                    and(cardFilter, manaValue(Comparison.AtLeast, 4)))), draw(1)))),
            targets = listOf(TargetSlot("opponents", TargetKind.Players(PlayerFilter.Opponent), min = n(0), max = null))),
    )
}
