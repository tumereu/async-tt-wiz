package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventRole
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 08: ward costs (CR 702.21a), a skipped draw (CR 614.1b, 614.11), "must be blocked each combat" (CR 509.1c),
 * nameless face-down blockers (CR 708.2a), face-down spell costs and mana, a cost of collect evidence X (CR 701.59a) and modal spells (CR 700.2). */
class MkmChunk08WardAndMoreTest {
    private val faceDown = ObjectFilter.HasStatus(Status.FaceDown)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val libraryEmpty = Condition.Not(Condition.Exists(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))

    // CR 702.21a: a ward cost that isn't mana
    @Test
    fun gorehollowReaper() = card(
        name = "Gorehollow Reaper",
        cost = "{3}{B}{B}{B}",
        type = "Creature — Vampire Assassin",
        pt = "6/5",
        text = """
            Flying
            Ward—Sacrifice a creature.
            Whenever a creature dies, target opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(cost(sacrificeA(creature))),
        triggered(dies(creature), loseLife(2, Players.Target("opponent")), gainLife(2), targets = listOf(targetOpponent)),
    )

    // CR 122.1: X counters on each creature; a draw for each Wolf
    @Test
    fun vornaFangsOfTheMoot() = card(
        name = "Vorna, Fangs of the Moot",
        cost = "{2}{R}{G}{W}",
        type = "Legendary Creature — Wolf",
        pt = "5/5",
        text = """
            Vigilance, trample, ward {3}
            Whenever Vorna attacks, put X +1/+1 counters on each creature you control, where X is the number of Elves you control. Draw a card for each Wolf you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Trample,
        Keyword.Ward(manaCost("{3}")),
        triggered(attacksSelf,
            counters(Objects.Each(creatureYouControl), count(and(sub("Elf"), you))),
            doing(Effect.Draw(Players.You, count(and(sub("Wolf"), you))))),
    )

    // CR 614.1b, 614.11: the draw is skipped while the library is empty; CR 613.4b: base power and toughness
    @Test
    fun hollowRiddle() = card(
        name = "Hollow Riddle",
        cost = "{4}{U}",
        type = "Creature — Elemental",
        pt = "2/5",
        text = """
            Hexproof
            If you would draw a card while your library has no cards in it, skip that draw instead.
            As long as there are no cards in your library, this creature has base power and toughness 10/10 and has flying and vigilance.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), modify = listOf(Modification.Skip))),
            affects = Affected.Objects(Objects.Self), condition = libraryEmpty),
        static(Mechanic.SetPowerToughness(n(10), n(10)), has(Keyword.Flying, Keyword.Vigilance), condition = libraryEmpty),
    )

    // CR 608.2h: the sacrificed creature as it last existed, suspected or not
    @Test
    fun bureauMortician() = card(
        name = "Bureau Mortician",
        cost = "{4}{B}",
        type = "Creature — Ogre Cleric",
        pt = "3/6",
        text = "{2}{B}, Sacrifice another creature: Draw a card. If the sacrificed creature was suspected, draw two cards instead.",
    ) parsesTo abilities(
        activated(cost(mana("{2}{B}"), sacrificeA(and(another, creature), preBind = "sacrificed")),
            Step.If(Condition.ObjectMatches(Objects.Bound("sacrificed"), suspected), then = listOf(draw(2)), otherwise = listOf(draw(1)))),
    )

    // CR 509.1c: a blocking requirement for every combat of the turn; CR 506.1: an additional combat phase
    @Test
    fun grumboldTheTremorMole() = card(
        name = "Grumbold, the Tremor-Mole",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Mole God",
        pt = "8/4",
        text = """
            Whenever Grumbold becomes blocked, untap each creature you control. After this phase, there is an additional combat phase.
            {3}{R}{R}{G}{G}: Grumbold must be blocked each combat this turn if able.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
            untapIt(Objects.Each(creatureYouControl)), doing(Effect.ExtraCombat())),
        activated(manaCost("{3}{R}{R}{G}{G}"), untilEndOfTurn(Objects.Self, Mechanic.MustBeBlocked)),
    )

    // CR 603.2e: entering doesn't trigger abilities
    @Test
    fun gatewardenThrull() = card(
        name = "Gatewarden Thrull",
        cost = "{1}{W}",
        type = "Creature — Thrull",
        pt = "1/2",
        text = """
            Flash
            Flying
            Artifacts and creatures entering don't cause abilities to trigger.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        static(Mechanic.EventDoesntTrigger(EventPattern.ZoneChange(or(artifact, creature), to = Zone.Battlefield))),
    )

    // CR 508.1d, 509.1c: requirements for the turn; CR 708.8: turning a face-down creature face up
    @Test
    fun scamperBluster() = cardFaces(
        Layout.Split,
        card(name = "Scamper", cost = "{U/R}", type = "Instant", text = "Target creature attacks or blocks this turn if able.") to abilities(
            spell(untilEndOfTurn(target("creature"), Mechanic.MustAttack(), Mechanic.MustBlock(ObjectFilter.Any)), targets = listOf(slot("creature", creature))),
        ),
        card(name = "Bluster", cost = "{4}{R/G}{R/G}", type = "Sorcery",
            text = "Creatures you control get +2/+2 and gain trample until end of turn. You may turn a creature you control face up.") to abilities(
            spell(untilEndOfTurn(Objects.Each(creatureYouControl), pt(2, 2), has(Keyword.Trample)),
                Step.Choose(Choice.Objects(creatureYouControl, n(0), n(1)), bind = "chosen", chooser = Players.You),
                doing(Effect.TurnFaceUp(Objects.Bound("chosen")))),
        ),
    )

    // CR 708.2a: a face-down creature has no name
    @Test
    fun haughtyPromenader() = card(
        name = "Haughty Promenader",
        cost = "{2}{G}",
        type = "Creature — Human Citizen",
        pt = "4/2",
        text = """
            During your turn, this creature has hexproof.
            This creature can't be blocked by creatures that don't have a name.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), condition = Condition.YourTurn),
        static(Mechanic.CantBeBlockedBy(and(creature, not(ObjectFilter.HasName)))),
    )

    // CR 601.2f, 708.4: a face-down spell's cost reduced
    @Test
    fun goblinFacecarver() = card(
        name = "Goblin Facecarver",
        cost = "{R}",
        type = "Creature — Goblin Citizen",
        pt = "1/2",
        text = "Whenever this creature attacks, face-down spells you cast this turn cost {1} less to cast.",
    ) parsesTo abilities(
        triggered(attacksSelf, doing(Effect.ApplyMechanics(listOf(Mechanic.CostChange(and(faceDown, spellFilter, you),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)))), null, Duration.EndOfTurn))),
    )

    // CR 601.2f: a cost reduced if a creature card went to your graveyard this turn
    @Test
    fun grimReassembly() = card(
        name = "Grim Reassembly",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            This spell costs {2} less to cast if a creature card was put into your graveyard from anywhere this turn.
            Return up to two target creature cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(creature, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                atLeast = 1, measure = Measure.Objects)),
        spell(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))),
    )

    // CR 601.2f, 107.3: X is the total toughness; CR 702.3b: attacking despite defender
    @Test
    fun theBulwarkOfShellClade() = card(
        name = "The Bulwark of Shell Clade",
        cost = "{10}{G}",
        type = "Legendary Creature — Crocodile Elk Turtle",
        pt = "2/15",
        text = """
            This spell costs {X} less to cast, where X is the total toughness of creatures you control.
            Defender
            {2}{U}{U}: Until end of turn, target creature you control gets +1/+0, gains "Whenever this creature deals combat damage to a player, draw cards equal to its toughness," and can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Of(NumericProperty.Toughness, Objects.Each(creatureYouControl), Combine.Total)))),
        Keyword.Defender,
        activated(manaCost("{2}{U}{U}"),
            untilEndOfTurn(target("creature"), pt(1, 0),
                has(triggered(combatDamageToAPlayer, doing(Effect.Draw(Players.You, toughness(Objects.Self))))),
                Mechanic.CanAttackDespiteDefender),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 106.6, 116.2b: mana only for face-down spells and turning face up
    @Test
    fun copperLaneRumormonger() = card(
        name = "Copper Lane Rumormonger",
        cost = "{2}{R}{G}",
        type = "Creature — Lizard Advisor",
        pt = "4/4",
        text = """
            Vigilance
            {T}: Add {R}{G}. Spend this mana only to cast face-down spells or to turn creatures face up.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(kind = ManaKind.Fixed(ManaCost.parse("{R}{G}")!!),
            restriction = or(and(spellFilter, faceDown), ObjectFilter.AbilityOf(kind = AbilityKind.TurnFaceUp))), Players.You))),
    )

    // CR 601.2c, 601.2f, 701.59a: the targets come before the cost, whose X is their total mana value
    @Test
    fun hastyAutopsy() = card(
        name = "Hasty Autopsy",
        cost = "{2}{B}{G}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, collect evidence X, where X is the total mana value of the permanents this spell targets.
            Destroy up to one target artifact, up to one target creature, up to one target enchantment, and up to one target planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.Action(KeywordAction.CollectEvidence(Value.Of(NumericProperty.ManaValue,
            Objects.Each(and(permanentFilter, ObjectFilter.TargetedBy(Objects.Self))), Combine.Total)))))),
        spell(destroy(Objects.Union(listOf(target("artifact"), target("creature"), target("enchantment"), target("planeswalker")))),
            targets = listOf(slot("artifact", artifact, min = 0), slot("creature", creature, min = 0), slot("enchantment", enchantment, min = 0),
                slot("planeswalker", planeswalker, min = 0))),
    )

    // CR 700.2: three modes; CR 701.23a: a revealed search card put where its type says
    @Test
    fun elderdruidsCharm() = card(
        name = "Elderdruid's Charm",
        cost = "{G}{G}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Search your library for a creature or land card and reveal it. Put it onto the battlefield tapped if it's a land card. Otherwise, put it into your hand. Then shuffle.
            • Put a +1/+1 counter on target creature you control. It deals damage equal to its power to target creature you don't control.
            • Exile target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(search(or(creature, land), reveal = true),
                Step.If(Condition.ObjectMatches(Objects.Bound("found"), land),
                    then = listOf(move(Objects.Bound("found"), Zone.Battlefield, tapped = true)), otherwise = listOf(move(Objects.Bound("found"), Zone.Hand))),
                shuffle),
            mode(counters(target("creature")), damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")),
                targets = listOf(slot("creature", creatureYouControl), slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
            mode(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(artifact, enchantment))))))),
    )

    // CR 700.2d: one or both modes; CR 701.60a: no longer suspected
    @Test
    fun lethalEntanglement() = card(
        name = "Lethal Entanglement",
        cost = "{1}{B}{R}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Destroy target creature.
            • Put a +1/+1 counter on target suspected creature you control. You may have it become no longer suspected.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", creature))),
            mode(counters(target("creature")), Step.May(listOf(noLongerSuspected(target("creature"))), player = Players.You),
                targets = listOf(slot("creature", and(suspected, creature, you)))),
            min = 1, max = 2))),
    )

    // CR 400.7, 608.2h: the exiled card's mana value
    @Test
    fun spiritProbe() = card(
        name = "Spirit Probe",
        cost = "{W}{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You choose a nonland card from it. Exile that card. If the card's mana value is 1 or less, create a 1/1 white and black Spirit creature token with flying.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), manaValue(Comparison.AtMost, 1)),
                then = listOf(create(creatureToken("Spirit", listOf(Color.White, Color.Black), 1, 1, abilities = listOf(Keyword.Flying))))),
            targets = listOf(targetOpponent)),
    )

    // CR 608.2h: the sacrificed creatures' total power as they last existed; CR 608.2g: cast while the ability resolves
    @Test
    fun kelvoxVisionaryTinkerer() = card(
        name = "Kelvox, Visionary Tinkerer",
        cost = "{5}{U}{R}",
        type = "Legendary Creature — Lizard Artificer",
        pt = "4/4",
        text = """
            Menace, ward {2}, haste
            Whenever Kelvox attacks, sacrifice any number of other creatures, then exile the top X cards of your library, where X is their total power. You may cast any number of instant and/or sorcery spells from among the exiled cards without paying their mana costs.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Ward(manaCost("{2}")),
        Keyword.Haste,
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(another, creature, you), n(0), null), bind = "chosen", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("chosen"), preBind = "sacrificed")),
            move(Objects.LibraryCards(Players.You, Value.Of(NumericProperty.Power, Objects.Bound("sacrificed"), Combine.Total), fromTop = true), Zone.Exile,
                postBind = "exiled"),
            Step.Choose(Choice.Objects(or(instant, sorcery), n(0), null, from = Objects.Bound("exiled")), bind = "cast", chooser = Players.You),
            Step.ForEach(Each.Objects(Objects.Bound("cast")), bind = "card", steps = listOf(
                doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))))),
    )

    // CR 702.2b, 702.15b: a spell with deathtouch and lifelink; CR 400.7: only while it's that spell
    @Test
    fun jorunaCarnageCollector() = card(
        name = "Joruna, Carnage Collector",
        cost = "{3}{B}{R}",
        type = "Legendary Creature — Human Shaman",
        pt = "3/4",
        text = """
            Whenever you cast an instant or sorcery spell, choose one —
            • That spell gains deathtouch and lifelink.
            • Create a 2/2 red Imp creature token with "When this token dies, it deals 2 damage to each opponent."
        """,
    ) parsesTo abilities(
        Ability.Triggered(youCast(or(instant, sorcery)), Instructions(modes = modes(
            mode(doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Deathtouch, Keyword.Lifelink))),
                Affected.Objects(Objects.EventObject(EventRole.Object)), Duration.Indefinitely))),
            mode(create(creatureToken("Imp", listOf(Color.Red), 2, 2, abilities = listOf(triggered(diesSelf, damage(2, eachOpponent))))))))),
    )
}
