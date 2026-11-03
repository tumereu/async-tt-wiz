package arcanum.parser.sets.msh

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 03: teamwork (CR 702.194), an optional additional cost of tapping creatures with total power N or more
 * (the keyword's expansion), and "if this spell was cast using teamwork" (CR 702.194b): a bigger effect, both modes instead of one
 * (CR 700.2), another target in the teamwork version, chosen only when teamwork was used (CR 702.194c). A creature that triggers when
 * it becomes tapped to pay a teamwork cost.
 */
class MshChunk03TeamworkTest {
    private fun instead(n: Int, vararg options: Mode) =
        Modes(options.toList(), min = 1, max = 1, maxIf = teamwork, maxIfCount = n, chooser = Players.You)

    // CR 702.194a: tapped to pay the cost
    @Test
    fun fieldAgentRowanVale() = card(
        name = "Field Agent Rowan Vale",
        cost = "{W}",
        type = "Legendary Creature — Human Spy Hero",
        text = "Whenever Field Agent Rowan Vale becomes tapped to pay a teamwork cost, put a +1/+1 counter on her and draw a card.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self), forCost = Keyword.Teamwork.Kind)),
            counters(Objects.EventObject(arcanum.api.EventRole.Object)), draw(1)),
    )

    @Test
    fun skyshipBarrage() = card(
        name = "Skyship Barrage",
        cost = "{W}",
        type = "Instant",
        text = """
            Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
            Skyship Barrage deals 2 damage to target attacking or blocking creature. If this spell was cast using teamwork, it deals 4 damage to that creature instead.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(2),
        spell(Step.If(teamwork, then = listOf(damage(4, toObjects(target("creature")))), otherwise = listOf(damage(2, toObjects(target("creature"))))),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(arcanum.api.Status.Blocking)), creature)))),
    )

    // flavor words name the modes (CR 207.2d)
    @Test
    fun twofoldJustice() = card(
        name = "Twofold Justice",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
            Choose one. If this spell was cast using teamwork, choose both instead.
            • Street Justice — Exile target creature with toughness 4 or greater.
            • Legal Justice — Exile target enchantment with mana value 4 or greater.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(4),
        arcanum.api.Ability.Spell(Instructions(modes = instead(2,
            mode(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(arcanum.api.Comparison.AtLeast, n(4)))))),
            mode(move(target("enchantment"), Zone.Exile), targets = listOf(slot("enchantment", and(enchantment, manaValue(arcanum.api.Comparison.AtLeast, 4)))))))),
    )

    @Test
    fun sunkenKingdomStrikes() = card(
        name = "Sunken Kingdom Strikes",
        cost = "{5}{U}{U}",
        type = "Sorcery",
        text = """
            Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
            Choose one. If this spell was cast using teamwork, choose both instead.
            • Target player creates a 6/5 blue Leviathan creature token with hexproof.
            • Return one or two target nonland permanents to their owners' hands.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(4),
        arcanum.api.Ability.Spell(Instructions(modes = instead(2,
            mode(Step.Do(Effect.CreateToken(creatureToken("Leviathan", listOf(Color.Blue), 6, 5, abilities = listOf(Keyword.Hexproof)), n(1), Players.Target("player"))),
                targets = listOf(targetPlayer)),
            mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter), max = 2)))))),
    )

    // CR 118.12a: "counter … unless its controller pays", the amount by whether teamwork was used
    @Test
    fun weForbidThee() = card(
        name = "We Forbid Thee!",
        cost = "{1}{U}",
        type = "Instant — Arcane",
        text = """
            Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
            Counter target spell unless its controller pays {2}. Counter that spell unless its controller pays {4} instead if this spell was cast using teamwork.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(2),
        spell(Step.If(teamwork, then = unless("{4}"), otherwise = unless("{2}")), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 702.194c: each version's target is chosen only when it is the one that happens
    @Test
    fun unholyCompact() = card(
        name = "Unholy Compact",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
            Exile target creature with mana value 3 or less. If this spell was cast using teamwork, instead exile target creature and you gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(2),
        spell(Step.If(teamwork, then = listOf(move(target("teamwork creature"), Zone.Exile), gainLife(3)), otherwise = listOf(move(target("creature"), Zone.Exile))),
            targets = listOf(
                slot("creature", and(creature, manaValue(arcanum.api.Comparison.AtMost, 3))).copy(onlyIf = Condition.Not(teamwork)),
                slot("teamwork creature", creature).copy(onlyIf = teamwork))),
    )

    @Test
    fun tooWickedToRest() = card(
        name = "Too Wicked to Rest",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
            Choose target creature card in your graveyard with mana value 4 or less. If this spell was cast using teamwork, instead choose target creature card in your graveyard. Return the chosen card to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(4),
        spell(move(Objects.Union(listOf(target("card"), target("teamwork card"))), Zone.Battlefield),
            targets = listOf(
                slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You), manaValue(arcanum.api.Comparison.AtMost, 4)))
                    .copy(onlyIf = Condition.Not(teamwork)),
                slot("teamwork card", inYourGraveyard(creature)).copy(onlyIf = teamwork))),
    )

    @Test
    fun assassinsBite() = card(
        name = "Assassin's Bite",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Teamwork 3 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 3 or more.)
            Choose one. If this spell was cast using teamwork, choose both instead.
            • Target creature gains deathtouch until end of turn.
            • Target creature gets -2/-2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(3),
        arcanum.api.Ability.Spell(Instructions(modes = instead(2,
            mode(gains(target("creature"), Keyword.Deathtouch), targets = listOf(slot("creature", creature))),
            mode(pump(target("creature"), -2, -2), targets = listOf(slot("creature", creature)))))),
    )

    @Test
    fun bruteBash() = card(
        name = "BRUTE BASH!",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
            Choose one. If this spell was cast using teamwork, choose both instead.
            • Destroy target noncreature artifact.
            • Target creature you control deals damage equal to its power to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(4),
        arcanum.api.Ability.Spell(Instructions(modes = instead(2,
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", and(not(creature), artifact)))),
            mode(damage(power(target("creature")), toObjects(target("creature an opponent controls")), source = target("creature")),
                targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls)))))),
    )

    // the teamwork damage to the creature's controller (as it last existed if it has left, CR 608.2h)
    @Test
    fun gauntletBlast() = card(
        name = "Gauntlet Blast",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
            Gauntlet Blast deals 5 damage to target creature. If this spell was cast using teamwork, it also deals 2 damage to that creature's controller.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(2),
        spell(damage(5, toObjects(target("creature"))),
            Step.If(teamwork, then = listOf(damage(2, Recipients(players = Players.ControllerOf(target("creature")))))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun squadTactics() = card(
        name = "Squad Tactics",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Teamwork 1 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 1 or more.)
            Target creature gains double strike until end of turn. If this spell was cast using teamwork, that creature also gains trample until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(1),
        spell(gains(target("creature"), Keyword.DoubleStrike), Step.If(teamwork, then = listOf(gains(target("creature"), Keyword.Trample))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun earthsBravestChampions() = card(
        name = "Earth's Bravest Champions",
        cost = "{4}{G}{G}",
        type = "Sorcery",
        text = """
            Teamwork 5 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 5 or more.)
            Reveal the top eight cards of your library. You may put a creature card from among them onto the battlefield. If this spell was cast using teamwork, put any number of creature cards from among them onto the battlefield instead. Put the rest into your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(5),
        spell(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(8), fromTop = true), bind = "revealed")),
            Step.If(teamwork,
                then = listOf(Step.Choose(Choice.Objects(creature, n(0), null, from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                    move(Objects.Bound("chosen"), Zone.Battlefield)),
                otherwise = listOf(Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                    move(Objects.Bound("chosen"), Zone.Battlefield))),
            move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("chosen")), Zone.Graveyard)),
    )

    // the second mode's two targets named apart from the first mode's (CR 115.3)
    @Test
    fun goBananas() = card(
        name = "Go Bananas!",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Teamwork 3 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 3 or more.)
            Choose one. If this spell was cast using teamwork, choose both instead.
            • Put a +1/+1 counter on target creature.
            • Target creature you control fights target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Teamwork(3),
        arcanum.api.Ability.Spell(Instructions(modes = instead(2,
            mode(counters(target("creature")), targets = listOf(slot("creature", creature))),
            mode(Step.Do(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls)))))),
    )

    private fun unless(cost: String) = listOf(
        Step.Pay(Cost.mana(arcanum.api.ManaCost.parse(cost)!!), payer = Players.ControllerOf(target("spell")), bind = "paid"),
        Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))))

}
