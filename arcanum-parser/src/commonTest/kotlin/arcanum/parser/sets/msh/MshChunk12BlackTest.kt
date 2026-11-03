package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 12: black cards. An ability usable only with two or more creature cards in your graveyard; exiling cards
 * until a nonland card (CR 701.20) and casting it with any mana (CR 609.4b-style permission); modes returning a Villain or a Hero card;
 * named tokens; indestructible while you control an artifact creature or a Plan; sneak (CR 702.190); a reflexive trigger returning a
 * creature tapped and attacking with a finality counter (CR 508.4, 122.1h); "if …, otherwise …"; the revealed cards a player chooses
 * by number; a discarded card exiled and playable until your next turn ends.
 */
class MshChunk12BlackTest {
    @Test
    fun agentsOfTheSerpentCult() = card(
        name = "Agents of the Serpent Cult",
        cost = "{1}{B}",
        type = "Creature — Human Spy Villain",
        text = "When this creature dies, create a 2/1 black Villain creature token with menace. (It can't be blocked except by two or more creatures.)",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(diesSelf, create(villain2)),
    )

    @Test
    fun doctorZolanBioFanatic() = card(
        name = "Doctor Zolan, Bio-Fanatic",
        cost = "{2}{B}",
        type = "Legendary Artifact Creature — Scientist Villain",
        text = "{3}, {T}: Create a tapped 2/1 black Villain creature token with menace. Activate only if there are two or more creature cards in your graveyard. (It can't be blocked except by two or more creatures.)",
        pt = "2/3",
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{3}"), tap), Instructions(steps = listOf(Step.Do(Effect.CreateToken(villain2, n(1), Players.You, tapped = true)))),
            anyTime.copy(condition = twoCreatureCards)),
    )

    // CR 701.20: exiled until a nonland card; the permission lets any mana pay for it
    @Test
    fun crimsonSpiderSuperSpy() = card(
        name = "Crimson Spider, Super Spy",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Spy Hero",
        text = """
            Menace
            Whenever Crimson Spider deals combat damage to a player, that player exiles cards from the top of their library until they exile a nonland card. You may put a +1/+1 counter on Crimson Spider. If you don't, you may cast the exiled nonland card until end of turn and mana of any type can be spent to cast that spell.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer,
            Step.Do(Effect.RevealUntil(nonland, Players.EventPlayer, n(1), reveal = true, into = Zone.Exile, foundBind = "found")),
            Step.Pay(steps = listOf(counters(Objects.Self)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast,
                holder = PlayerFilter.You, manaAsAnyColor = true)), arcanum.api.Affected.Objects(Objects.Bound("found")), Duration.EndOfTurn))))),
    )

    @Test
    fun decoyFeint() = card(
        name = "Decoy Feint",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Choose one or both —
            • Return target Villain card from your graveyard to your hand.
            • Return target Hero card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(villainFilter)))),
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(hero)))),
            max = 2))),
    )

    // CR 111.4: tokens with a name of their own
    @Test
    fun doctorDread() = card(
        name = "Doctor Dread",
        cost = "{4}{B}{B}",
        type = "Legendary Creature — Human Scientist Villain",
        text = """
            When Doctor Dread enters, create two 3/3 colorless Robot Villain artifact creature tokens named Dreadbot.
            As long as you control an artifact creature or a Plan, Doctor Dread has indestructible.
            At the beginning of your end step, you draw a card and lose 1 life.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, create(TokenSpec.Defined(FaceDefinition("Dreadbot", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
            subtypes = listOf(Subtype("Robot"), Subtype("Villain")), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3))), n(2), bind = "created")),
        static(has(Keyword.Indestructible), condition = Condition.Exists(and(or(and(artifact, creature), sub("Plan")), you), atLeast = 1)),
        triggered(stepBegins(arcanum.api.TurnStep.End, PlayerFilter.You), draw(1), loseLife(1)),
    )

    // CR 702.190: sneak, an alternative cost returning an unblocked attacker
    @Test
    fun katarinaDaughterOfTheFist() = card(
        name = "Katarina, Daughter of the Fist",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Human Ninja Villain",
        text = """
            Sneak {1}{B}{B} (You may cast this spell for {1}{B}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. She enters tapped and attacking.)
            When Katarina enters, destroy target creature an opponent controls with power 3 or less.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Sneak(manaCost("{1}{B}{B}")),
        triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature", and(creatureOpponentControls, power(arcanum.api.Comparison.AtMost, 3))))),
    )

    // CR 603.12, 508.4: returned tapped and attacking (not declared), with a finality counter (CR 122.1h)
    @Test
    fun scytheLethalLegionnaire() = card(
        name = "Scythe, Lethal Legionnaire",
        cost = "{3}{B}",
        type = "Legendary Creature — Human Villain",
        text = "Whenever Scythe attacks, you may pay {3}{B}. When you do, return target creature card from your graveyard to the battlefield tapped and attacking with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Pay(manaCost("{3}{B}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(Step.Do(Effect.Move(target("card"), arcanum.api.Destination(Zone.Battlefield, tapped = true,
                attacking = arcanum.api.AttackTarget.Chosen, counters = listOf(arcanum.api.CountersToPut(arcanum.api.CounterKind.Finality, n(1)))))),
                targets = listOf(slot("card", inYourGraveyard(creature))))))),
    )

    @Test
    fun hourOfDownfall() = card(
        name = "Hour of Downfall",
        cost = "{3}{B}",
        type = "Instant",
        text = "Destroy target creature. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(destroy(target("creature")), Step.Do(Effect.Surveil(Players.You, n(1))), targets = listOf(slot("creature", creature))),
    )

    // "… if …. Otherwise, …" as it resolves
    @Test
    fun serpentCultTroopers() = card(
        name = "Serpent Cult Troopers",
        cost = "{2}{B}",
        type = "Creature — Human Soldier Villain",
        text = "When this creature enters, create a tapped 2/1 black Villain creature token with menace if there are two or more creature cards in your graveyard. Otherwise, mill two cards. (Put the top two cards of your library into your graveyard.)",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.If(twoCreatureCards, then = listOf(Step.Do(Effect.CreateToken(villain2, n(1), Players.You, tapped = true))),
            otherwise = listOf(mill(2)))),
    )

    @Test
    fun overlordsEnforcers() = card(
        name = "Overlord's Enforcers",
        cost = "{2}{B}",
        type = "Creature — Human Villain",
        text = """
            Lifelink
            {2}{B}, Sacrifice an artifact or creature: Draw a card.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(cost(mana("{2}{B}"), CostPart.Sacrifice(CostObjects.Choose(or(artifact, creature), count = n(1)))), draw(1)),
    )

    // the player chooses which cards to reveal; you choose one of them; they discard it (CR 701.9)
    @Test
    fun resonatorSonicSubjugator() = card(
        name = "Resonator, Sonic Subjugator",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Rogue Villain",
        text = "Sonic Attack — When Resonator enters, target player reveals a number of cards from their hand equal to one plus the number of creature cards in your graveyard. You choose one of them. That player discards that card.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))), revealCount, revealCount),
                bind = "revealed", chooser = Players.Target("player")),
            Step.Do(Effect.Reveal(Objects.Bound("revealed"))),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Discard(Players.Target("player"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetPlayer), abilityWord = "Sonic Attack"),
    )

    // the discarded card in the graveyard (CR 400.7), exiled by choice and playable until the end of your next turn
    @Test
    fun lunarStoneHarshMistress() = card(
        name = "Lunar Stone, Harsh Mistress",
        cost = "{3}{B}",
        type = "Legendary Creature — Human Doctor Villain",
        text = """
            Flying
            Whenever you discard a card, you may exile that card from your graveyard. If you do, until the end of your next turn, you may play that card.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)),
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.After)), Zone.Graveyard, postBind = "exiled"))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))))),
    )

    // CR 113.6m: it works in the graveyard
    @Test
    fun projectRevenantSoldier() = card(
        name = "Project Revenant Soldier",
        cost = "{B}",
        type = "Artifact Creature — Zombie Soldier",
        text = "{2}{B}: Return this card from your graveyard to your hand.",
        pt = "1/2",
    ) parsesTo abilities(
        activated(manaCost("{2}{B}"), move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard)),
    )

    private val twoCreatureCards = Condition.Exists(inYourGraveyard(creature), atLeast = 2)
    private val revealCount = Value.Sum(listOf(n(1), count(inYourGraveyard(creature))))
}
