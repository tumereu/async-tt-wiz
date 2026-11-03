package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 05… 06: noncreature spells matter: cast triggers with "if at least four mana was spent to cast it" (an intervening
 * if, CR 603.4, read from the spell cast, CR 601.2h), the mana spent as an amount, Wizard tokens that ping, a Vehicle that becomes a creature,
 * X mana in any combination of two colours, an ability usable once each turn during your turn, scry-or-surveil triggers.
 */
class FinChunk06NoncreatureSpellsTest {
    // CR 603.4, 601.2h
    @Test
    fun deepSahuagin() = card(
        name = "Brineclaw Raider",
        cost = "{1}{U}",
        type = "Creature — Merfolk Warrior",
        pt = "1/3",
        text = "Whenever you cast a noncreature spell, if at least four mana was spent to cast it, put a +1/+1 counter on this creature and it can't be blocked this turn.",
    ) parsesTo abilities(
        triggered(youCast(noncreature), counters(Objects.Self), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked), interveningIf = manaSpentAtLeast(4)),
    )

    @Test
    fun theTheaterShip() = card(
        name = "The Grand Stagecraft",
        cost = "{4}{U}",
        type = "Legendary Artifact — Vehicle",
        pt = "5/3",
        text = """
            Flying
            Whenever you cast a noncreature spell, if at least four mana was spent to cast it, The Grand Stagecraft becomes an artifact creature until end of turn.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreature), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
            interveningIf = manaSpentAtLeast(4)),
        Keyword.Crew(2),
    )

    @Test
    fun ulrosInkyNuisance() = card(
        name = "Ulros, Inky Nuisance",
        cost = "{1}{U}",
        type = "Legendary Creature — Octopus",
        pt = "2/1",
        text = """
            Whenever you cast a noncreature spell, if at least four mana was spent to cast it, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Whenever you cast a noncreature spell, if at least eight mana was spent to cast it, put eight +1/+1 counters on Ulros.
        """,
    ) parsesTo abilities(
        triggered(youCast(noncreature), tapIt(target("creature")), counters(target("creature"), 1, stun), targets = listOf(slot("creature", creatureOpponentControls)),
            interveningIf = manaSpentAtLeast(4)),
        triggered(youCast(noncreature), counters(Objects.Self, 8), interveningIf = manaSpentAtLeast(8)),
    )

    /** "It deals damage equal to its power" after sacrificing it as a cost: its power as it last existed (CR 608.2h). */
    @Test
    fun cinderBomb() = card(
        name = "Cinder Bomb",
        cost = "{R}",
        type = "Creature — Elemental",
        pt = "1/1",
        text = """
            Whenever you cast a noncreature spell, if at least four mana was spent to cast it, put a +1/+1 counter on this creature.
            Blow Up — {T}, Sacrifice this creature: It deals damage equal to its power to target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youCast(noncreature), counters(Objects.Self), interveningIf = manaSpentAtLeast(4)),
        activated(cost(tap, sacrificeSelf), damage(power(Objects.Self), toObjects(target("creature"))), targets = listOf(slot("creature", creature)),
            rules = asSorcery).copy(abilityWord = "Blow Up"),
    )

    @Test
    fun pellaShutterbug() = card(
        name = "Pella, Shutterbug",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Scout",
        pt = "2/2",
        text = """
            Haste
            Selfie Shot — Whenever you cast a noncreature spell, if at least four mana was spent to cast it, create a Treasure token.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(youCast(noncreature), createTreasure, interveningIf = manaSpentAtLeast(4), abilityWord = "Selfie Shot"),
    )

    /** "it deals that much damage": the mana spent to cast that spell. */
    @Test
    fun telmarGreatSage() = card(
        name = "Telmar, Great Sage",
        cost = "{3}{U}{R}",
        type = "Legendary Creature — Human Wizard",
        pt = "3/3",
        text = "Whenever you cast a noncreature spell, create a 1/1 colorless Hero creature token. If four or more mana was spent to cast that spell, draw two cards. If eight or more mana was spent to cast that spell, sacrifice Telmar and it deals that much damage to each opponent.",
    ) parsesTo abilities(
        triggered(youCast(noncreature), create(heroToken), ifThen(manaSpentAtLeast(4), draw(2)),
            ifThen(manaSpentAtLeast(8), doing(Effect.Sacrifice(Objects.Self)), damage(Value.ManaSpent(of = thatSpell), eachOpponent))),
    )

    @Test
    fun shanaraTacticianMagician() = card(
        name = "Shanara, Tactician Magician",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Dwarf Wizard",
        pt = "0/4",
        text = "Whenever you cast a noncreature spell, Shanara gets +X/+0 until end of turn, where X is the amount of mana spent to cast that spell. If X is 4 or more, draw a card.",
    ) parsesTo abilities(
        triggered(youCast(noncreature), untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.ManaSpent(of = thatSpell), n(0))),
            ifThen(manaSpentAtLeast(4), draw(1))),
    )

    /** "Add X mana in any combination of {U} and/or {R}" (CR 106.1b); "only during your turn and only once each turn" (CR 602.5b). */
    @Test
    fun viraOrnitier() = card(
        name = "Vira Ornitier",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Wizard",
        pt = "0/3",
        text = """
            {0}: Add X mana in any combination of {U} and/or {R}, where X is Vira Ornitier's power. Activate only during your turn and only once each turn.
            Whenever you cast a noncreature spell, put a +1/+1 counter on Vira Ornitier and it deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        activated(manaCost("{0}"), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(power(Objects.Self), colors = listOf(ColorRef.Literal(Color.Blue),
            ColorRef.Literal(Color.Red)), combination = true)), Players.You)),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1, condition = Condition.YourTurn)),
        triggered(youCast(noncreature), counters(Objects.Self), damage(1, eachOpponent)),
    )

    @Test
    fun blackWaltzNoFive() = card(
        name = "Grim Waltzer No. 5",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Wizard",
        pt = "2/2",
        text = """
            Flying, deathtouch
            Whenever you cast a noncreature spell, Grim Waltzer No. 5 deals 2 damage to each opponent.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Deathtouch, triggered(youCast(noncreature), damage(2, eachOpponent)))

    @Test
    fun ringOfIncantation() = card(
        name = "Ring of Incantation",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            You draw two cards and you lose 2 life. Create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
            Wizards you control get +1/+0 and gain lifelink until end of turn.
        """,
    ) parsesTo abilities(
        spell(draw(2), loseLife(2), create(wizardToken), pump(Objects.Each(and(sub("Wizard"), you)), 1, 0, has(Keyword.Lifelink))),
    )

    @Test
    fun ambushedByMages() = card(
        name = "Ambushed by Mages",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            Target opponent sacrifices a creature of their choice.
            Create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(arcanum.api.Choice.Objects(and(creature, arcanum.api.ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            create(wizardToken),
            targets = listOf(targetOpponent)),
    )

    @Test
    fun mysticElder() = card(
        name = "Mystic Elder",
        cost = "{2}{R}",
        type = "Creature — Human Wizard",
        pt = "1/3",
        text = """
            When this creature enters, create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
        """,
    ) parsesTo abilities(triggered(entersSelf, create(wizardToken)))

    @Test
    fun queenOfBrahm() = card(
        name = "Queen of Brahm",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Noble",
        pt = "2/1",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever Queen of Brahm attacks, create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
        """,
    ) parsesTo abilities(Keyword.Prowess, triggered(attacksSelf, create(wizardToken)))

    /** "Whenever you scry or surveil" (CR 701.22, 701.25): one ability with two events. */
    @Test
    fun matronArchonElder() = card(
        name = "Matron, Archon Elder",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Warlock",
        pt = "1/4",
        text = "Whenever you scry or surveil, draw a card. (Draw after you scry or surveil.)",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Scry(PlayerFilter.You), EventPattern.Surveil(PlayerFilter.You))), draw(1)),
    )

    @Test
    fun xandraDarkMage() = card(
        name = "Xandra, Dark Mage",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "3/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Xandra gets +1/+1 for each noncreature, nonland card in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        static(Mechanic.ModifyPowerToughness(count(inYourGraveyard(noncreature, nonland)), count(inYourGraveyard(noncreature, nonland)))),
    )
}
