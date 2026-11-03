package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 11: multicoloured cards, A to P. Enters-or-dies Heartwood tokens and pumps counted by artifacts;
 * returning from the graveyard (to the hand, or with a finality counter when a condition holds, CR 122.1h); modes, one
 * "choose two" (CR 700.2); a counter instead of a pump after scrying or surveilling; an Aura returning its creature when it
 * dies (CR 603.10a); an attacking creature dealing damage itself; "ward {2}" in a keyword list; a named artifact token with a
 * mana ability, once each turn; vigilance while you control a Jace; fighting up to one creature; casting the exiled card with
 * mana of any type under threshold (CR 607.2a); a reflexive trigger after milling (CR 603.12); "this creature or another".
 */
class FraChunk11MulticoloredATest {
    @Test
    fun ostrelVandrakar() = card(
        name = "Ostrel Vandrakar",
        cost = "{1}{R}{G}{G}",
        type = "Legendary Creature — Elder Sphinx",
        pt = "5/4",
        text = """
            Flying
            When Ostrel Vandrakar enters or dies, create a Heartwood token. (It's a red and green artifact with "{T}: Add {R} or {G}.")
            {6}: Create a Heartwood token. Then Ostrel Vandrakar gets +X/+0 until end of turn, where X is the number of artifacts you control.
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            Ability.Triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))), Instructions(steps = listOf(createHeartwood))),
            activated(cost(mana("{6}")), createHeartwood, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(and(artifact, you))), n(0)))),
        )

    @Test
    fun hallowedGhast() = card(
        name = "Hallowed Ghast",
        cost = "{W/B}",
        type = "Creature — Zombie Cleric",
        pt = "1/1",
        text = """
            Lifelink
            {2}{W/B}: Return this card from your graveyard to your hand.
        """,
    // hob: the ability works in the graveyard it returns the card from (CR 113.6m)
    ) parsesTo abilities(Keyword.Lifelink, activated(cost(mana("{2}{W/B}")), move(Objects.Self, Zone.Hand)).copy(functionsIn = listOf(Zone.Graveyard)))

    // CR 603.2h
    @Test
    fun blossomhulk() = card(
        name = "Blossomhulk",
        cost = "{2}{G}{W}",
        type = "Creature — Plant Elemental",
        pt = "4/4",
        text = """
            Whenever you gain life, draw a card. This ability triggers only once each turn.
            {4}{G}{W}: Target creature gains trample and lifelink until end of turn.
        """,
    ) parsesTo abilities(
            triggered(youGainLife, draw(1), perTurn = 1),
            activated(cost(mana("{4}{G}{W}")), gains(target("creature"), Keyword.Trample, Keyword.Lifelink), targets = listOf(slot("creature", creature))),
        )

    @Test
    fun stormTheSanctuary() = card(
        name = "Storm the Sanctuary",
        cost = "{2}{R/W}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures you control get +2/+0 until end of turn.
            • Target creature gets +2/+0 and gains first strike until end of turn. Put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(pump(Objects.Each(creatureYouControl), 2, 0)))),
            Mode(Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(pump(target("creature"), 2, 0, Mechanic.GainAbilities(listOf(Keyword.FirstStrike))), counters(target("creature"))))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 118.12, 401.4
    @Test
    fun collisionOfForces() = card(
        name = "Collision of Forces",
        cost = "{1}{U}{R}",
        type = "Instant",
        text = "Choose target nonland permanent. Its owner may put it on top of their library. If they do, Collision of Forces deals 2 damage to them. If they didn't put the card on top of their library, they put it on the bottom.",
    ) parsesTo abilities(spell(
            Step.Pay(steps = listOf(move(target("permanent"), Zone.Library, position = 0)), payer = Players.OwnerOf(target("permanent")), bind = "top"),
            Step.If(Condition.Did("top"),
                then = listOf(damage(2, Recipients(players = Players.OwnerOf(target("permanent"))))),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ))

    // CR 700.2
    @Test
    fun handiworkSmasher() = card(
        name = "Handiwork Smasher",
        cost = "{3}{R}{R}{G}{G}",
        type = "Artifact Creature — Boar Construct",
        pt = "7/5",
        text = """
            Trample
            When this creature enters, choose two —
            • This creature deals 4 damage to target creature or planeswalker.
            • Create a 2/2 colorless Wizard Soldier creature token named Cadet.
            • Draw a card.
        """,
    ) parsesTo abilities(Keyword.Trample, Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("permanent", creatureOrPlaneswalker)), steps = listOf(damage(4, Recipients(objects = target("permanent")))))),
            Mode(Instructions(steps = listOf(createCadets(1)))),
            Mode(Instructions(steps = listOf(draw(1)))),
        ), min = 2, max = 2, chooser = Players.You))))

    @Test
    fun valdimereFatekeep() = card(
        name = "Valdimere Fatekeep",
        cost = "{1}{W}{U}{U}",
        type = "Legendary Creature — Elder Sphinx",
        pt = "3/4",
        text = """
            Flash
            Flying
            Whenever you scry or surveil, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(Keyword.Flash, Keyword.Flying, triggered(youScryOrSurveil, counters(Objects.Each(creatureYouControl))))

    // CR 608.2c: one or the other
    @Test
    fun franticFuturist() = card(
        name = "Frantic Futurist",
        cost = "{2}{W}{U}",
        type = "Creature — Kor Scout",
        pt = "3/4",
        text = """
            Flying
            At the beginning of combat on your turn, another target creature you control gets +1/+1 until end of turn. If you've scried or surveilled this turn, put a +1/+1 counter on that creature instead.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You),
            Step.If(scriedOrSurveilledThisTurn, then = listOf(counters(target("creature"))), otherwise = listOf(pump(target("creature"), 1, 1))),
            targets = listOf(slot("creature", and(anotherObject, creature, you)))))

    // CR 603.10a, 400.7
    @Test
    fun savageryOfTheChase() = card(
        name = "Savagery of the Chase",
        cost = "{1}{B/G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets +1/+0 and has deathtouch.
            When enchanted creature dies, return that card to the battlefield tapped under its owner's control.
        """,
    ) parsesTo abilities(
            Keyword.Flash,
            Keyword.Enchant(TargetKind.Objects(creature)),
            static(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.GainAbilities(listOf(Keyword.Deathtouch)), affects = Objects.AttachedTo(Objects.Self)),
            triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), from = Zone.Battlefield, to = Zone.Graveyard)),
                Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, tapped = true,
                    controller = Players.OwnerOf(Objects.EventObject(EventRole.After)))))),
        )

    @Test
    fun rimeburnElemental() = card(
        name = "Rimeburn Elemental",
        cost = "{U}{R}{R}",
        type = "Creature — Elemental",
        pt = "4/4",
        text = """
            Trample, haste
            Whenever this creature deals combat damage to a player, draw two cards.
            At the beginning of the end step, sacrifice this creature.
        """,
    ) parsesTo abilities(
            Keyword.Trample,
            Keyword.Haste,
            triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true)), draw(2)),
            triggered(stepBegins(TurnStep.End, PlayerFilter.Any), Step.Do(Effect.Sacrifice(Objects.Self))),
        )

    // CR 122.1h, 602.5b
    @Test
    fun direEncorer() = card(
        name = "Dire Encorer",
        cost = "{B}{R}",
        type = "Creature — Zombie Bard",
        pt = "2/2",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            {B}{R}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if an opponent has been dealt noncombat damage this turn. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(Keyword.Prowess, Ability.Activated(cost(mana("{B}{R}")),
            Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, n(1)))))))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = thisTurn(opponentDealtNoncombatDamage))))

    @Test
    fun varrusBarbquill() = card(
        name = "Varrus Barbquill",
        cost = "{B}{R}{R}",
        type = "Legendary Creature — Elder Sphinx",
        pt = "1/4",
        text = """
            Flying
            Whenever a creature you control attacks, that creature deals 1 damage to each opponent.
            {4}: Create a 2/2 colorless Wizard Soldier creature token named Cadet. Then creatures you control gain haste until end of turn.
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            triggered(on(EventPattern.Attack(creatureYouControl)), Step.Do(Effect.DealDamage(eachOpponent, n(1), Objects.EventObject(EventRole.Object)))),
            activated(cost(mana("{4}")), createCadets(1), gains(Objects.Each(creatureYouControl), Keyword.Haste)),
        )

    @Test
    fun tinkerforgeCharm() = card(
        name = "Tinkerforge Charm",
        cost = "{R}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Tinkerforge Charm deals 6 damage to target creature with flying.
            • Put two +1/+1 counters on target creature. It gains trample until end of turn.
            • Add {C}{C}{C}.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", and(creature, ObjectFilter.HasKeyword(Keyword.Flying)))), steps = listOf(damage(6, Recipients(objects = target("creature")))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(counters(target("creature"), 2), gains(target("creature"), Keyword.Trample)))),
            Mode(Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}{C}")!!)), Players.You))))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 111.4, 603.2h
    @Test
    fun thessalyVigorleaf() = card(
        name = "Thessaly Vigorleaf",
        cost = "{3}{G}{W}{W}",
        type = "Legendary Creature — Elder Sphinx",
        pt = "6/6",
        text = """
            Flying, vigilance, lifelink, ward {2}
            Whenever you gain life, create a colorless artifact token named Lotus with "{T}, Sacrifice this token: Add three mana of any one color." This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            Keyword.Vigilance,
            Keyword.Lifelink,
            Keyword.Ward(cost(mana("{2}"))),
            triggered(youGainLife, Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Lotus", colorIndicator = emptyList(), types = listOf(CardType.Artifact),
                abilities = listOf(activated(cost(tap, sacrificeSelf), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(3))), Players.You)))))), n(1), Players.You)),
                perTurn = 1),
        )

    // CR 701.14
    @Test
    fun thoughtWanderer() = card(
        name = "Thought Wanderer",
        cost = "{3}{G}{U}{U}",
        type = "Creature — Bird Fish Illusion",
        pt = "4/4",
        text = """
            Flying
            This creature has vigilance as long as you control a Jace planeswalker.
            When this creature enters, it fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            static(Mechanic.GainAbilities(listOf(Keyword.Vigilance)), condition = Condition.Exists(and(sub("Jace"), planeswalker, you), 1)),
            triggered(entersSelf, Step.Do(Effect.Fight(Objects.Self, target("creature"))), targets = listOf(slot("creature", creatureOpponentControls, min = 0))),
        )

    // CR 607.2a, 603.4
    @Test
    fun voidSummoner() = card(
        name = "Void Summoner",
        cost = "{2}{U}{B}",
        type = "Creature — Human Warlock",
        pt = "4/2",
        text = """
            When this creature enters, if you cast it, target opponent reveals their hand. You choose a nonland card from it. Exile that card.
            Threshold — As long as there are seven or more cards in your graveyard, you may cast the exiled card, and mana of any type can be spent to cast that spell.
        """,
    ) parsesTo abilities(
            triggered(entersSelf,
                Step.Do(Effect.Reveal(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
                Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
                targets = listOf(targetOpponent), interveningIf = Condition.CastFrom(null, Objects.Self)),
            static(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You, manaAsAnyColor = true), affects = Objects.Remembered("exiled"),
                condition = threshold, abilityWord = "Threshold"),
        )

    // CR 603.12
    @Test
    fun primevalHexstalker() = card(
        name = "Primeval Hexstalker",
        cost = "{1}{B}{G}",
        type = "Creature — Wolf",
        pt = "2/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, mill four cards. When you do, return target land card from your graveyard to the battlefield tapped. (To mill four cards, put the top four cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(Keyword.Menace, triggered(entersSelf, mill(4), Step.Do(Effect.ReflexiveTrigger(Instructions(
            targets = listOf(slot("card", and(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), steps = listOf(move(target("card"), Zone.Battlefield, tapped = true)))))))

    // CR 122.1h
    @Test
    fun examinerOfPromise() = card(
        name = "Examiner of Promise",
        cost = "{W}{U}",
        type = "Creature — Human Cleric",
        pt = "3/1",
        text = """
            Whenever this creature or another creature you control enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {W}{U}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if you've scried or surveilled this turn. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(anotherObject, creature, you)), to = Zone.Battlefield)), surveil(1)),
            Ability.Activated(cost(mana("{W}{U}")),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, n(1)))))))),
                ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = scriedOrSurveilledThisTurn)),
        )
}
