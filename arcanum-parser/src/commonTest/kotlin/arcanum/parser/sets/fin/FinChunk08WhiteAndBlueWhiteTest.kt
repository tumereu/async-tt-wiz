package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 08: white and white-blue cards: counts of Equipment and creatures, a player with protection from each opponent
 * (CR 702.16k), a bonus counting creatures you control and cards in your graveyard, "any number of cards named", milling by the life
 * gained this turn, artifact and Hero anthems, plainscycling.
 */
class FinChunk08WhiteAndBlueWhiteTest {
    @Test
    fun adelbaldSteadfast() = card(
        name = "Adelbald Steadfast",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Knight",
        pt = "2/1",
        text = """
            Lifelink
            Adelbald Steadfast gets +1/+1 for each Equipment you control.
        """,
    ) parsesTo abilities(Keyword.Lifelink, static(Mechanic.ModifyPowerToughness(count(and(equipment, you)), count(and(equipment, you)))))

    @Test
    fun ashaPrincessOfTheSands() = card(
        name = "Asha, Princess of the Sands",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Rebel Noble",
        pt = "3/2",
        text = "Whenever Asha attacks, look at the top five cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(arcanum.api.Choice.Objects(artifact, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), arcanum.api.Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 702.34a
    @Test
    fun ronanInspiration() = card(
        name = "Ronan's Inspiration",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Attacking creatures get +2/+0 until end of turn.
            Flashback {2}{W}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(pump(Objects.Each(and(attacking, creature)), 2, 0)), Keyword.Flashback(manaCost("{2}{W}{W}")))

    @Test
    fun skyboundMoogle() = card(
        name = "Skybound Moogle",
        cost = "{3}{W}{W}",
        type = "Creature — Moogle",
        pt = "2/3",
        text = """
            Flying
            When this creature enters, put a +1/+1 counter on target creature.
            Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creature))),
        Keyword.Typecycling(sub("Plains"), "Plainscycling", manaCost("{2}")),
    )

    @Test
    fun clawedProwler() = card(
        name = "Clawed Prowler",
        cost = "{1}{W}",
        type = "Creature — Cat Beast",
        pt = "2/2",
        text = "{1}{W}, {T}: Tap target nonenchantment creature.",
    ) parsesTo abilities(activated(cost(mana("{1}{W}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", and(not(enchantment), creature)))))

    @Test
    fun theLightsChosen() = card(
        name = "The Light's Chosen",
        cost = "{5}{W}{W}",
        type = "Sorcery",
        text = "Create four 1/1 colorless Hero creature tokens. Then put a +1/+1 counter on each creature you control.",
    ) parsesTo abilities(spell(create(heroToken, n(4)), counters(Objects.Each(creatureYouControl))))

    @Test
    fun stoneholdGuard() = card(
        name = "Stonehold Guard",
        cost = "{1}{W}",
        type = "Creature — Dwarf Soldier",
        pt = "2/1",
        text = "When this creature dies, create a 1/1 colorless Hero creature token.",
    ) parsesTo abilities(triggered(diesSelf, create(heroToken)))

    @Test
    fun crestedCat() = card(
        name = "Crested Cat",
        cost = "{2}{W}",
        type = "Creature — Cat",
        pt = "1/3",
        text = """
            Flying, vigilance
            As long as you control two or more artifacts, this creature gets +2/+0.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Vigilance, static(pt(2, 0), condition = Condition.Exists(and(artifact, you), atLeast = 2)))

    @Test
    fun ironcladWalker() = card(
        name = "Ironclad Walker",
        cost = "{3}{W}",
        type = "Artifact — Vehicle",
        pt = "4/4",
        text = """
            When this Vehicle enters, create a 1/1 colorless Hero creature token.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(triggered(entersSelf, create(heroToken)), Keyword.Crew(1))

    /** "Search your library for a card named [this card]" (CR 201.2). */
    @Test
    fun clockworkInfantry() = card(
        name = "Clockwork Infantry",
        cost = "{W}",
        type = "Artifact Creature — Robot Soldier",
        pt = "1/1",
        text = """
            This creature gets +1/+0 as long as you control another artifact.
            {2}{W}: Search your library for a card named Clockwork Infantry, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        static(pt(1, 0), condition = Condition.Exists(and(another, artifact, you), atLeast = 1)),
        activated(manaCost("{2}{W}"), search(ObjectFilter.Named("Clockwork Infantry")), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    @Test
    fun minaWhiteMage() = card(
        name = "Mina, White Mage",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "3/3",
        text = """
            Vigilance, lifelink
            Whenever you gain life, put a +1/+1 counter on each Cleric you control.
        """,
    ) parsesTo abilities(Keyword.Vigilance, Keyword.Lifelink, triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Each(and(sub("Cleric"), you)))))

    @Test
    fun mooglesStand() = card(
        name = "Moogles' Stand",
        cost = "{3}{W}{W}",
        type = "Instant",
        text = "For each creature you control, create a 1/2 white Moogle creature token with lifelink. Then creatures you control gain indestructible until end of turn.",
    ) parsesTo abilities(
        spell(create(creatureToken("Moogle", listOf(Color.White), 1, 2, abilities = listOf(Keyword.Lifelink)), count(creatureYouControl)),
            gains(Objects.Each(creatureYouControl), Keyword.Indestructible)),
    )

    @Test
    fun edgeOfDawn() = card(
        name = "Edge of Dawn",
        cost = "{1}{W}",
        type = "Instant",
        text = "Edge of Dawn deals damage equal to the number of creatures you control plus the number of Equipment you control to target creature.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(count(creatureYouControl), count(and(equipment, you)))), toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 604.3 (a characteristic-defining ability)
    @Test
    fun snowwardRebel() = card(
        name = "Snowe Valiers",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Rebel Monk",
        pt = "*/3",
        text = """
            Vigilance
            Snowe Valiers's power is equal to the number of creatures you control.
        """,
    ) parsesTo abilities(Keyword.Vigilance, static(Mechanic.SetPowerToughness(count(creatureYouControl), null), characteristicDefining = true))

    @Test
    fun neverAlone() = card(
        name = "Never Alone",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. If you control three or more creatures, it gets +4/+4 until end of turn instead.",
    ) parsesTo abilities(
        spell(ifThen(Condition.Exists(creatureYouControl, atLeast = 3), pump(target("creature"), 4, 4), otherwise = listOf(pump(target("creature"), 2, 2))),
            targets = listOf(slot("creature", creature))),
    )

    /** "You have protection from each of your opponents." (CR 702.16k) */
    @Test
    fun virtueAbsolute() = card(
        name = "Virtue Absolute",
        cost = "{6}{W}{U}",
        type = "Legendary Creature — Avatar Warrior",
        pt = "8/8",
        text = """
            This spell can't be countered.
            Flying
            You have protection from each of your opponents. (You can't be dealt damage, enchanted, or targeted by anything controlled by your opponents.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Flying,
        playerStatic(has(Keyword.Protection(ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
    )

    /** "for each Artificer you control and each Artificer card in your graveyard"; "A deck can have any number of cards named …" (CR 100.2a) */
    @Test
    fun cidelTimelessTinker() = card(
        name = "Cidel, Timeless Tinker",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Human Artificer",
        pt = "4/4",
        text = """
            Artifact creatures and Heroes you control get +1/+1 for each Artificer you control and each Artificer card in your graveyard.
            A deck can have any number of cards named Cidel, Timeless Tinker.
            Cycling {W}{U} ({W}{U}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(artificers, artificers), affects = Objects.Each(and(or(and(artifact, creature), sub("Hero")), you))),
        static(Mechanic.AnyNumberInDeck),
        Keyword.Cycling(manaCost("{W}{U}")),
    )

    @Test
    fun hollisEstheim() = card(
        name = "Hollis Estheim",
        cost = "{W}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/2",
        text = """
            Lifelink
            At the beginning of your end step, each opponent mills X cards, where X is the amount of life you gained this turn.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(yourEndStep, doing(Effect.Mill(Players.Each(PlayerFilter.Opponent), Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You),
            arcanum.api.Measure.Amount)))),
    )

    @Test
    fun tidesStarStriker() = card(
        name = "Tydes, Star Striker",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human Warrior",
        pt = "2/1",
        text = """
            Whenever an artifact you control enters, put a +1/+1 counter on Tydes.
            Whenever Tydes attacks, tap target creature an opponent controls.
        """,
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), counters(Objects.Self)),
        triggered(attacksSelf, tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    private val artificers = Value.Sum(listOf(count(and(sub("Artificer"), you)), count(inYourGraveyard(sub("Artificer")))))
}
