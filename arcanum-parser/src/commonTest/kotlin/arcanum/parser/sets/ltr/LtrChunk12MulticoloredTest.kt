package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardTypeRef
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 12: multicoloured cards, second part. Excess damage dealt to the controller instead (CR 120.4a), the
 * third resolution of an ability this turn (CR 113.7), phasing out when targeted and phasing in (CR 702.26), an action once each turn,
 * attacking with another legendary creature, milling then amassing by the cards milled, protection from a chosen card type
 * (CR 702.16), a search for a card sharing a colour with a legendary creature (CR 105.4), each player sacrificing and a permanent card
 * returned when you did.
 */
class LtrChunk12MulticoloredTest {
    // CR 700.2: modes that haven't been chosen
    @Test
    fun mithrandirTheGrey() = card(
        name = "Mithrandir the Grey",
        cost = "{3}{U}{R}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Whenever you cast an instant or sorcery spell, choose one that hasn't been chosen —
            • You may tap or untap target permanent.
            • Mithrandir deals 3 damage to each opponent.
            • Copy target instant or sorcery spell you control. You may choose new targets for the copy.
            • Put Mithrandir on top of its owner's library.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Ability.Triggered(youCast(or(instant, sorcery)), Instructions(modes = modes(
            mode(Step.May(listOf(Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(Condition.Chose("tapOrUntap", "tap"), then = listOf(tapIt(target("permanent"))), otherwise = listOf(untapIt(target("permanent"))))), Players.You),
                targets = listOf(slot("permanent", permanentFilter))),
            mode(damage(3, eachOpponent)),
            mode(doing(Effect.CopySpell(target("spell"), Players.You, newTargets = true)), targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter, you)))),
            mode(move(Objects.Self, Zone.Library, position = 0)),
            onlyUnchosen = true))),
    )

    // CR 120.4a: the excess is dealt to the controller instead
    @Test
    fun wizardsSanction() = card(
        name = "Wizard's Sanction",
        cost = "{1}{U}{R}",
        type = "Sorcery",
        text = "Wizard's Sanction deals X damage to target creature, where X is the number of instant and sorcery cards in your graveyard. Excess damage is dealt to that creature's controller instead.",
    ) parsesTo abilities(
        spell(doing(Effect.DealDamage(toObjects(target("creature")), spellsInGraveyard, Objects.Self, bind = "damage")),
            doing(Effect.DealDamage(Recipients(players = Players.ControllerOf(target("creature"))), Value.Excess("damage"), Objects.Self)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 113.7: the third resolution this turn
    @Test
    fun thrainMournfulAvenger() = card(
        name = "Thráin, Mournful Avenger",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Dwarf Warrior",
        text = """
            Thráin has indestructible as long as two or more creatures died under your control this turn.
            Whenever another creature you control dies, put a +1/+1 counter on Thráin. When this ability resolves for the third time this turn, Thráin fights up to one target creature you don't control.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        static(has(Keyword.Indestructible), condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard),
            atLeast = 2, measure = arcanum.api.Measure.Events)),
        triggered(on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(3)), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0)),
                steps = listOf(doing(Effect.Fight(Objects.Self, target("creature")))))))))),
    )

    @Test
    fun thorondorTheWindlord() = card(
        name = "Thorondor the Windlord",
        cost = "{4}{W}{U}",
        type = "Legendary Creature — Bird Noble",
        text = """
            This spell costs {2} less to cast as long as you've drawn two or more cards this turn.
            Flying, vigilance
            Other Birds you control have vigilance.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = arcanum.api.Measure.Events)),
        Keyword.Flying,
        Keyword.Vigilance,
        static(has(Keyword.Vigilance), affects = Objects.Each(and(another, sub("Bird"), you))),
    )

    // CR 702.26: phasing out when a spell targets it; phasing in makes a Spirit
    @Test
    fun kingOfTheForsworn() = card(
        name = "King of the Forsworn",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Spirit Noble",
        text = """
            Flying
            Whenever King of the Forsworn or another Spirit you control becomes the target of a spell, it phases out. (Treat it and anything attached to it as though they don't exist until your next turn.)
            Whenever King of the Forsworn or another Spirit you control phases in, create a tapped 1/1 white Spirit creature token with flying.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.BecomesTarget(spirits, spellFilter)), doing(Effect.PhaseOut(Objects.EventObject(EventRole.Object)))),
        triggered(on(EventPattern.PhaseIn(spirits)), doing(Effect.CreateToken(spiritFlyer, n(1), Players.You, tapped = true))),
    )

    // "Do this only once each turn": the untap at most once a turn
    @Test
    fun thranduilCounterOfKills() = card(
        name = "Thranduil, Counter of Kills",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Elf Archer",
        text = """
            Reach
            Whenever you scry, if Thranduil is tapped, you may untap it. Do this only once each turn.
            Whenever a creature an opponent controls dies, put a +1/+1 counter on Thranduil.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(youScry, Instructions(steps = listOf(Step.May(listOf(untapIt(Objects.Self)), Players.You))),
            interveningIf = Condition.ObjectMatches(Objects.Self, tapped), actionOncePerTurn = true),
        triggered(on(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self)),
    )

    @Test
    fun tedCrookedShirriff() = card(
        name = "Ted, Crooked Shirriff",
        cost = "{W}{B}",
        type = "Legendary Creature — Halfling Rogue",
        text = """Whenever a player casts their second spell each turn, you lose 1 life and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), nthThisTurn = 2), loseLife(1), createTreasure()),
    )

    // CR 614.1a: one more +1/+1 counter
    @Test
    fun lugdushUrukHaiCaptain() = card(
        name = "Lugdush, Uruk-hai Captain",
        cost = "{B}{R}",
        type = "Legendary Creature — Orc Soldier",
        text = """
            Menace
            If one or more +1/+1 counters would be put on an Army, Goblin, or Orc you control, that many plus one +1/+1 counters are put on it instead.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Menace,
        static(Mechanic.Replacement(EventPattern.CountersPut(and(or(sub("Army"), sub("Goblin"), sub("Orc")), you), p1p1),
            modify = listOf(arcanum.api.Modification.AddAmount(n(1))))),
    )

    // CR 508.1: attacking with it and another legendary creature
    @Test
    fun berilacEsquireOfRohan() = card(
        name = "Berilac, Esquire of Rohan",
        cost = "{R}{W}",
        type = "Legendary Creature — Halfling Knight",
        text = """
            Haste
            Berilac has first strike as long as it's equipped.
            Whenever you attack with Berilac and another legendary creature, draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Haste,
        static(has(Keyword.FirstStrike), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(attachments = equipment))),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), draw(1),
            triggersOnlyIf = Condition.Exists(and(another, legendary, creature, attacking), atLeast = 1)),
    )

    // CR 701.13: milled, then amass by that player's instants and sorceries
    @Test
    fun theVoiceOfTheDarkTower() = card(
        name = "The Voice of the Dark Tower",
        cost = "{3}{U}{B}",
        type = "Legendary Creature — Human Advisor",
        text = "When The Voice of the Dark Tower enters, target player mills three cards. Then amass Orcs X, where X is the number of instant and sorcery cards in that player's graveyard. (Put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf, mill(3, Players.Target("player")),
            amassOrcs(count(and(or(instant, sorcery), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))))),
            targets = listOf(targetPlayer)),
    )

    // CR 604.3; a reflexive trigger after an optional sacrifice
    @Test
    fun oldGreyWillow() = card(
        name = "Old Grey Willow",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Treefolk",
        text = """
            Old Grey Willow's power and toughness are each equal to the number of lands you control.
            Whenever Old Grey Willow attacks, you may sacrifice another creature or a token. When you do, target creature an opponent controls gets -2/-2 until end of turn.
        """,
        pt = "*/*",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you)))), affects = Affected.Objects(Objects.Self), characteristicDefining = true),
        triggered(attacksSelf, Step.Pay(cost(sacrificeA(and(another, or(creature, token)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(targets = listOf(slot("creature", creatureOpponentControls)),
                steps = listOf(pump(target("creature"), -2, -2)))))))),
    )

    // CR 702.16: protection from the card type of your choice
    @Test
    fun bolgerGuardOfTheCitadel() = card(
        name = "Bolger, Guard of the Citadel",
        cost = "{W}{U}",
        type = "Legendary Creature — Halfling Soldier",
        text = """
            Vigilance, ward {1}
            {T}: Another target creature you control gains protection from the card type of your choice until end of turn. (It can't be blocked, targeted, dealt damage, enchanted, or equipped by anything of that type.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Ward(manaCost("{1}")),
        activated(cost(tap), Step.Choose(Choice.CardType(), bind = "type", chooser = Players.You),
            gains(target("creature"), Keyword.Protection(ObjectFilter.Type(CardTypeRef.Chosen("type")))),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    @Test
    fun princeAdrahilTheFair() = card(
        name = "Prince Adrahil the Fair",
        cost = "{W}{U}",
        type = "Legendary Creature — Human Noble",
        text = "Whenever you draw your second card each turn, create a 1/1 white Human Soldier creature token.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(drawSecondCard, create(humanSoldier)),
    )

    // CR 105.4: sharing a colour with a legendary creature you control
    @Test
    fun ringVision() = card(
        name = "Ring-vision",
        cost = "{1}{U}{B}",
        type = "Sorcery",
        text = "The Ring tempts you. Search your library for a card that shares a color with a legendary creature you control, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        spell(ringTempts, search(ObjectFilter.Shares(SharedQuality.Color, Objects.Each(and(legendary, creature, you))), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 701.21a: each player sacrifices; you may return another permanent card if you did
    @Test
    fun riseOfTheDarkLord() = card(
        name = "Rise of the Dark Lord",
        cost = "{2}{B}{G}",
        type = "Sorcery",
        text = "Each player sacrifices a creature of their choice. If you sacrificed a creature this way, you may return another permanent card from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "yours", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("yours"), postBind = "your card")),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "theirs", chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("theirs")))), together = true),
            Step.If(Condition.NonEmpty("yours"), then = listOf(
                Step.Choose(Choice.Objects(and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You), not(ObjectFilter.Is(Objects.Bound("your card")))), n(0), n(1)),
                    bind = "returned", chooser = Players.You),
                move(Objects.Bound("returned"), Zone.Battlefield)))),
    )

    private val spirits = or(ObjectFilter.Is(Objects.Self), and(another, sub("Spirit"), you))
    private val spellsInGraveyard = count(and(or(instant, sorcery), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))
    @Suppress("unused") private val unused = listOf(CostObjects.Self, CostPart.TapSelf, Duration.EndOfTurn)
}
