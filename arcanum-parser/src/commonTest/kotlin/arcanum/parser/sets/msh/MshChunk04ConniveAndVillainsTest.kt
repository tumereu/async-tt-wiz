package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Total
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 04: connive (CR 701.50) and Villains. A permanent conniving as it enters, attacks or by an ability; a
 * replacement of conniving (CR 614.1a); "you may have it connive. Do this only once each turn" (CR 603.2h); a spell putting a
 * creature second from the top or on the bottom by its owner's choice; boast with an exile cost by mana symbols (CR 702.142a, 107.4)
 * and copies of cards cast (CR 707.12); Villain anthems, cost reductions and token makers.
 */
class MshChunk04ConniveAndVillainsTest {
    @Test
    fun syndicateTechnicians() = card(
        name = "Syndicate Technicians",
        cost = "{3}{U}",
        type = "Creature — Human Scientist Villain",
        text = """
            When this creature enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, connive()),
        Keyword.Typecycling(basicLand(), "Basic landcycling", manaCost("{2}")),
    )

    // CR 614.1a, 614.5: the replacement's own connive isn't replaced again
    @Test
    fun masterMindGammaGenius() = card(
        name = "Mastermind, Gamma Genius",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Gamma Scientist Villain",
        text = """
            If a creature you control would connive, instead you draw a card, then that creature connives.
            At the beginning of combat on your turn, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Connive(creatureYouControl),
            instead = Instructions(steps = listOf(draw(1), connive(Objects.EventObject(EventRole.Object)))))),
        triggered(beginningOfCombatOnYourTurn, connive(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // the owner chooses (CR 401.4: the library position); the second target is up to one
    @Test
    fun deceiversGambit() = card(
        name = "Deceiver's Gambit",
        cost = "{3}{U}",
        type = "Sorcery",
        text = "The owner of target creature an opponent controls puts it into their library second from the top or on the bottom. Then up to one target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("second from the top", "on the bottom")), bind = "position", chooser = Players.OwnerOf(target("creature"))),
            Step.If(Condition.Chose("position", "second from the top"),
                then = listOf(move(target("creature"), Zone.Library, position = 1)),
                otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
            connive(target("second creature")),
            targets = listOf(slot("creature", creatureOpponentControls), slot("second creature", creatureYouControl, min = 0))),
    )

    // CR 702.142a: boast; the exile cost counts black mana symbols, hybrid ones too (CR 107.4); CR 707.12: copies of cards cast
    @Test
    fun baronVexHeirOfMalice() = card(
        name = "Baron Vex, Heir of Malice",
        cost = "{B}{B}{B}",
        type = "Legendary Creature — Human Noble Villain",
        text = """
            Whenever you cast a black spell from your hand, Baron Vex connives.
            Boast — Exile any number of black cards from your graveyard with fifteen or more black mana symbols among their mana costs: Copy those exiled cards. You may cast up to three of the copies without paying their mana costs. (Activate only if this creature attacked this turn and only once each turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Black)), spellFilter), PlayerFilter.You, from = Zone.Hand)), connive()),
        Ability.Activated(Cost(listOf(CostPart.Exile(CostObjects.Choose(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Black)), cardFilter), count = null,
                total = Total(NumericProperty.ManaSymbols(Color.Black), n(15))), Zone.Graveyard, postBind = "exiled"))),
            Instructions(steps = listOf(
                Step.Do(Effect.CopyCard(Objects.Bound("exiled"), Players.You, bind = "copies")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(3), from = Objects.Bound("copies")), bind = "chosen", chooser = Players.You),
                Step.ForEach(arcanum.api.Each.Objects(Objects.Bound("chosen")), "copy", listOf(
                    Step.Do(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))))),
            anyTime, label = AbilityLabel.Boast),
    )

    // CR 603.2h: "Do this only once each turn"
    @Test
    fun baronStrakeTyrantOverlord() = card(
        name = "Baron Strake, Serpent Overlord",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Villain",
        text = """
            Villain spells you cast cost {1} less to cast.
            Whenever another Villain you control enters, you may have it connive. Do this only once each turn. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(villainFilter, spellFilter, you), arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1)))),
        Ability.Triggered(enters(and(another, villainFilter, you)), Instructions(steps = listOf(
            Step.May(listOf(connive(Objects.EventObject(EventRole.After))), player = Players.You))), actionOncePerTurn = true),
    )

    @Test
    fun ladyMaskOfSecrets() = card(
        name = "Lady Visage",
        cost = "{4}{B}",
        type = "Legendary Creature — Human Villain",
        text = """
            When Lady Visage enters, she connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
            Whenever you draw your second card each turn, create a 2/1 black Villain creature token with menace. (It can't be blocked except by two or more creatures.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, connive()),
        triggered(secondCardEachTurn, create(villain2)),
    )

    // flavor words (CR 207.2d); "Activate only during your turn"
    @Test
    fun bigBrainTyrant() = card(
        name = "B.R.A.I.N.",
        cost = "{3}{B}{B}",
        type = "Legendary Artifact Creature — Villain",
        text = """
            Flying, lifelink
            Mental Organism — Pay 3 life: B.R.A.I.N. connives. Activate only during your turn. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
            Designed Only for Killing — Creatures your opponents control get -1/-1.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        Ability.Activated(Cost(listOf(CostPart.PayLife(n(3)))), Instructions(steps = listOf(connive())),
            anyTime.copy(condition = Condition.YourTurn), abilityWord = "Mental Organism"),
        Ability.Static(listOf(pt(-1, -1)), affects = arcanum.api.Affected.Objects(Objects.Each(creatureOpponentControls)), abilityWord = "Designed Only for Killing"),
    )

    @Test
    fun crimsonRoomRecruit() = card(
        name = "Crimson Room Recruit",
        cost = "{1}{B}",
        type = "Creature — Human Spy Villain",
        text = "When this creature enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)",
        pt = "1/2",
    ) parsesTo abilities(
        triggered(entersSelf, connive()),
    )

    // CR 701.3: attach up to one target Equipment to target creature; "an equipped creature you control" (CR 301.5)
    @Test
    fun bladeDuelistScoundrel() = card(
        name = "Bladesman, Sharp Rogue",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Hero Villain",
        text = """
            Whenever another Villain you control enters, attach up to one target Equipment you control to target creature you control.
            Whenever an equipped creature you control attacks, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(enters(and(another, villainFilter, you)), Step.Do(Effect.Attach(target("Equipment"), arcanum.api.Recipients(objects = target("creature")))),
            targets = listOf(slot("Equipment", and(equipment, you), min = 0), slot("creature", creatureYouControl))),
        triggered(on(EventPattern.Attack(and(creature, you, ObjectFilter.Attached(attachments = equipment)))), connive(Objects.EventObject(EventRole.Object))),
    )

    @Test
    fun chronoTyrant() = card(
        name = "Chrono Tyrant",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Human Villain",
        text = """
            Whenever Chrono Tyrant attacks, he connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
            Whenever you draw your second card each turn, each opponent loses 1 life and you gain 1 life.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(attacksSelf, connive()),
        triggered(secondCardEachTurn, loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // "This ability triggers only once each turn" (CR 603.2i)
    @Test
    fun boneBreakerMercenary() = card(
        name = "Bonebreaker, Cruel Mercenary",
        cost = "{3}{B}",
        type = "Legendary Creature — Human Mercenary Villain",
        text = """
            Deathtouch
            Whenever another Villain you control enters, put a +1/+1 counter on Bonebreaker. He deals 2 damage to each opponent. This ability triggers only once each turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(enters(and(another, villainFilter, you)), counters(Objects.Self), damage(2, eachOpponent), perTurn = 1),
    )

    @Test
    fun waspstingMarauder() = card(
        name = "Waspsting Marauder",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Rogue Villain",
        text = """
            Flying
            Whenever another Villain you control enters, Waspsting Marauder gets +1/+0 and gains lifelink until end of turn.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(another, villainFilter, you)), pump(Objects.Self, 1, 0, has(Keyword.Lifelink))),
    )

    @Test
    fun madameSerpentine() = card(
        name = "Madame Serpentine",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Human Villain",
        text = "Whenever you cast a Villain spell, create a 2/1 black Villain creature token with menace. (It can't be blocked except by two or more creatures.)",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(youCast(villainFilter), create(villain2)),
    )

    @Test
    fun theSinisterCouncil() = card(
        name = "The Sinister Council",
        cost = "{5}{B}",
        type = "Legendary Creature — Human Villain",
        text = """
            Other Villains you control get +2/+1.
            {1}{B}, Discard this card: Search your library for a Plan card, reveal it, put it into your hand, then shuffle.
        """,
        pt = "5/6",
    ) parsesTo abilities(
        static(pt(2, 1), affects = Objects.Each(and(another, villainFilter, you))),
        activated(cost(mana("{1}{B}"), CostPart.Discard(CostObjects.Self)), search(sub("Plan"), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle, functionsIn = listOf(Zone.Hand)),
    )

}
