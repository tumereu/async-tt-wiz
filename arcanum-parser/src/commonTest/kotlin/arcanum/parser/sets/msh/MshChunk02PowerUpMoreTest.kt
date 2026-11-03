package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 02: power-up (CR 702.193) on red, green, multicoloured and colourless cards, and the cards that change
 * power-up abilities: "can be activated an additional time", "power-up abilities … cost {3} less" (CR 601.2f, 602.2b). A legendary
 * token with a quoted ability (CR 111.4), a fight with up to one target (CR 701.14), odd or even mana values (zero is even), a
 * keyword counter (CR 122.1b).
 */
class MshChunk02PowerUpMoreTest {
    // CR 603.4: the intervening if is checked as it triggers and as it resolves
    @Test
    fun blazingPyrokid() = card(
        name = "Blazing Pyrokid",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Hero",
        text = """
            Flying
            Whenever you draw a card, if you control another Hero, Blazing Pyrokid deals 1 damage to target opponent.
            Power-up — {6}{R}: Put three +1/+1 counters on Blazing Pyrokid. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Draw(PlayerFilter.You)), damage(1, arcanum.api.Recipients(players = Players.Target("opponent"))),
            targets = listOf(targetOpponent), interveningIf = Condition.Exists(and(another, hero, you), atLeast = 1)),
        powerUp("{6}{R}", counters(Objects.Self, 3)),
    )

    // CR 603.7: a delayed trigger for the next such spell this turn; "Loki's power" is read as the spell is cast
    @Test
    fun trickstersHeir() = card(
        name = "Trickster's Heir",
        cost = "{1}{R}",
        type = "Legendary Creature — God Sorcerer Villain",
        text = """
            {1}, {T}: When you next cast an instant or sorcery spell with mana value less than or equal to Trickster's Heir's power this turn, copy that spell. You may choose new targets for the copy.
            Power-up — {4}{R}: Put two +1/+1 counters on Trickster's Heir. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), Step.Do(Effect.CreateDelayedTrigger(
            youCast(and(or(instant, sorcery), spellFilter, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, power(Objects.Self)))),
            arcanum.api.Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(arcanum.api.EventRole.Object), Players.You, newTargets = true)))),
            duration = arcanum.api.Duration.EndOfTurn))),
        powerUp("{4}{R}", counters(Objects.Self, 2)),
    )

    // CR 103.6a: an opening-hand action; CR 122.1b: a double strike counter
    @Test
    fun flickerSprinter() = card(
        name = "Flicker, Sprinter",
        cost = "{R}",
        type = "Legendary Creature — Mutant Hero",
        text = """
            If Flicker, Sprinter is in your opening hand, you may begin the game with him on the battlefield.
            Haste
            Power-up — {4}{R}: Put a +1/+1 counter and a double strike counter on Flicker. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayBeginOnBattlefield), affects = arcanum.api.Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
        Keyword.Haste,
        powerUp("{4}{R}", counters(Objects.Self), counters(Objects.Self, 1, CounterKind.Keyword(Keyword.DoubleStrike.kind))),
    )

    @Test
    fun magmaRaider() = card(
        name = "Magma Raider",
        cost = "{2}{R}",
        type = "Creature — Elemental Villain",
        text = """
            Haste
            Power-up — {5}{R}: Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Haste,
        powerUp("{5}{R}", counters(Objects.Self, 2)),
    )

    // "only . . . once?" is the card's own joke in the reminder text, which has no rules meaning (CR 207.2)
    @Test
    fun marvelousMarquee() = card(
        name = "Marvelous Marquee, Screen Hero",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Human Performer Hero",
        text = """
            Flying
            Each power-up ability of permanents you control can be activated an additional time.
            Power-up — {5}{R}{R}: Put two +1/+1 counters on Marvelous Marquee. (Activate each power-up ability only . . . once? Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.LabelAdditionalUses(AbilityLabel.PowerUp, 1), affects = Objects.Each(and(permanentFilter, you))),
        powerUp("{5}{R}{R}", counters(Objects.Self, 2)),
    )

    @Test
    fun demigodOfMight() = card(
        name = "Demigod of Might",
        cost = "{2}{G}",
        type = "Legendary Creature — Demigod Warrior Hero",
        text = "Power-up — {4}{G}: Put a +1/+1 counter on Demigod of Might. He gains vigilance, indestructible, and haste until end of turn. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)",
        pt = "3/3",
    ) parsesTo abilities(
        powerUp("{4}{G}", counters(Objects.Self), gains(Objects.Self, Keyword.Vigilance, Keyword.Indestructible, Keyword.Haste)),
    )

    @Test
    fun menagerieOfHeroes() = card(
        name = "Menagerie of Heroes",
        cost = "{3}{G}",
        type = "Creature — Dragon Cat Dog Bird Frog Hero",
        text = """
            Reach
            Power-up — {6}{G}: Put a +1/+1 counter on this creature and create a 3/2 white Hero creature token with vigilance. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Reach,
        powerUp("{6}{G}", counters(Objects.Self), create(creatureToken("Hero", listOf(Color.White), 3, 2, abilities = listOf(Keyword.Vigilance)))),
    )

    @Test
    fun venomTongueAdept() = card(
        name = "Venom-Tongue Adept",
        cost = "{G}",
        type = "Creature — Human Snake Villain",
        text = """
            Deathtouch
            Power-up — {3}{G}: Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        powerUp("{3}{G}", counters(Objects.Self, 2)),
    )

    @Test
    fun emeraldJusticar() = card(
        name = "Emerald Justicar",
        cost = "{3}{G}",
        type = "Legendary Creature — Gamma Hero",
        text = """
            Reach, trample
            Power-up — {4}{G}{G}: Destroy up to one target artifact or enchantment. Put a +1/+1 counter on Emerald Justicar. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        powerUp("{4}{G}{G}", destroy(target("permanent")), counters(Objects.Self),
            targets = listOf(slot("permanent", or(artifact, enchantment), min = 0))),
    )

    // CR 111.4: a named legendary token with its quoted ability ("can't be blocked by more than one creature", CR 509.1b)
    @Test
    fun jadeTigerAvatar() = card(
        name = "Jade Tiger Avatar",
        cost = "{1}{G}",
        type = "Legendary Creature — Human Hero",
        text = "Power-up — {5}{G}: Put a +1/+1 counter on Jade Tiger Avatar and create The Tiger God, a legendary 4/4 green Cat God creature token with \"The Tiger God can't be blocked by more than one creature.\" (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)",
        pt = "2/2",
    ) parsesTo abilities(
        powerUp("{5}{G}", counters(Objects.Self), create(TokenSpec.Defined(FaceDefinition("The Tiger God", colorIndicator = listOf(Color.Green),
            supertypes = listOf(Supertype.Legendary), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Cat"), Subtype("God")),
            power = PrintedNumber.Fixed(4), toughness = PrintedNumber.Fixed(4), abilities = listOf(static(Mechanic.MaximumBlockers(1))))))),
    )

    // CR 701.14: a fight with up to one target; the hybrid cost reduction is by the permanent's mana cost (CR 702.193b)
    @Test
    fun hulkingHorror() = card(
        name = "Hulking Horror, Gamma Brute",
        cost = "{3}{R/G}",
        type = "Legendary Creature — Gamma Villain",
        text = """
            Trample
            Power-up — {5}{R/G}{R/G}: Put a +1/+1 counter on Hulking Horror. He fights up to one target creature an opponent controls. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Trample,
        powerUp("{5}{R/G}{R/G}", counters(Objects.Self), Step.Do(Effect.Fight(Objects.Self, target("creature"))),
            targets = listOf(slot("creature", creatureOpponentControls, min = 0))),
    )

    // CR 601.2f, 602.2b: other creatures' power-up abilities cost less
    @Test
    fun gammaColossus() = card(
        name = "Gamma Colossus",
        cost = "{3}{R}{G}",
        type = "Legendary Creature — Gamma Berserker Hero",
        text = """
            Reach, trample
            Power-up abilities of other creatures you control cost {3} less to activate.
            Power-up — {6}{R}{G}: Put five +1/+1 counters on Gamma Colossus. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(source = and(another, creature, you), label = AbilityLabel.PowerUp),
            CostChange.Less(ManaCost.parse("{3}")!!, n(1)))),
        powerUp("{6}{R}{G}", counters(Objects.Self, 5)),
    )

    // odd or even (CR 107: zero is even); "each other creature"
    @Test
    fun ravagerOfBalance() = card(
        name = "Ravager of Balance",
        cost = "{R}{W}{B}",
        type = "Legendary Creature — Eternal Villain",
        text = """
            Deathtouch, lifelink
            Power-up — {C}{W}{U}{B}{R}{G}: Put two +1/+1 counters on Ravager of Balance. Choose odd or even. Destroy each other creature with mana value of the chosen quality. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn. Zero is even.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Lifelink,
        powerUp("{C}{W}{U}{B}{R}{G}",
            counters(Objects.Self, 2),
            Step.Choose(Choice.Option(listOf("odd", "even")), bind = "quality", chooser = Players.You),
            destroy(Objects.Each(and(another, creature, ObjectFilter.Where(Condition.Or(listOf(
                Condition.And(listOf(Condition.Chose("quality", "odd"), Condition.Odd(Value.Of(NumericProperty.ManaValue, Objects.Tested, arcanum.api.Combine.Single)))),
                Condition.And(listOf(Condition.Chose("quality", "even"), Condition.Not(Condition.Odd(Value.Of(NumericProperty.ManaValue, Objects.Tested, arcanum.api.Combine.Single))))))))))),
        ),
    )

    @Test
    fun assemblyLineDrone() = card(
        name = "Assembly-Line Drone",
        cost = "{3}",
        type = "Artifact Creature — Robot Villain",
        text = "Power-up — {6}: Put two +1/+1 counters on this creature and create a 2/2 colorless Robot Villain artifact creature token. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)",
        pt = "2/3",
    ) parsesTo abilities(
        powerUp("{6}", counters(Objects.Self, 2),
            create(creatureToken("Robot Villain", emptyList(), 2, 2, types = listOf(CardType.Artifact, CardType.Creature)))),
    )

    // a flavor word (CR 207.2d); "draw a card if her power is 4 or greater" is checked as it resolves
    @Test
    fun syntheticScion() = card(
        name = "Synthetic Scion",
        cost = "{3}",
        type = "Legendary Artifact Creature — Robot Hero",
        text = """
            Flying
            Cybernetic Senses — Whenever Synthetic Scion attacks, draw a card if her power is 4 or greater.
            Power-up — {7}: Put two +1/+1 counters on Synthetic Scion. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, Step.If(Condition.ObjectMatches(Objects.Self, power(arcanum.api.Comparison.AtLeast, 4)), then = listOf(draw(1))),
            abilityWord = "Cybernetic Senses"),
        powerUp("{7}", counters(Objects.Self, 2)),
    )
}
