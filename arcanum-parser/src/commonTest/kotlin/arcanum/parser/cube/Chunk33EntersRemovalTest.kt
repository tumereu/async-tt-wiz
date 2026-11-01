package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 33: creatures whose enters trigger removes or takes something: sacrifice, exile from a hand (until it leaves, or
 * linked to a leaves trigger that gives it back), destroy, exile (also "up to one"), bounce, damage, counter, gain
 * control, and spell copies that wait for the next spell. Linked abilities: "the exiled card" in the leaves trigger is
 * the card the enters trigger exiled (CR 607.2a).
 */
class Chunk33EntersRemovalTest {
    // ---- Shared shapes

    private val targetOpponent = TargetSlot(name = "opponent", what = TargetKind.Players(PlayerFilter.Opponent))

    /** The cards in target opponent's hand. */
    private val opponentsHand = listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))

    /** "When this creature leaves the battlefield, [steps]" (CR 603.6c). */
    private fun whenThisLeaves(vararg steps: Step) = Ability.Triggered(
        trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
        instructions = Instructions(steps = steps.toList()),
    )

    /** Exile [objects] and keep them as card memory for "the exiled card" (CR 607.2a). */
    private fun exileAndRemember(objects: Objects) = arrayOf(
        Step.Do(Effect.Move(objects, to = Destination(Zone.Exile), postBind = "exiled")),
        Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
    )

    private val exiledCard = Objects.Remembered("exiled")

    /** "Target opponent reveals their hand and you choose a nonland card from it. Exile that card." and the leaves
     * trigger that returns it. */
    private val takeANonlandCardUntilItLeaves = abilities(
        whenThisEnters(
            Step.Do(Effect.Reveal(Objects.Each(ObjectFilter.And(opponentsHand)))),
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land))) + opponentsHand), min = Value.Fixed(1), max = Value.Fixed(1)),
                bind = "chosen",
            ),
            *exileAndRemember(Objects.Bound("chosen")),
            targets = listOf(targetOpponent),
        ),
        whenThisLeaves(Step.Do(Effect.Move(exiledCard, to = Destination(Zone.Hand)))),
    )

    private val artifactOrEnchantment = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment)))
    private val creature = ObjectFilter.Type(CardType.Creature)

    // ---- Hands

    // CR 101.4, 608.2e: each player chooses in turn order, then all sacrifice at once.
    @Test
    fun cursedPillager() = card(
        name = "Cursed Pillager",
        cost = "{1}{B}",
        type = "Creature — Zombie Warrior",
        pt = "2/1",
        text = "When this creature enters, each player sacrifices a nontoken creature of their choice.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Any)),
                bind = "player",
                together = true,
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = allOf(
                                ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                                creature,
                                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))),
                            ),
                            min = Value.Fixed(1),
                            max = Value.Fixed(1),
                        ),
                        bind = "sacrificed",
                        chooser = Players.Bound("player"),
                    ),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                ),
            ),
        ),
    )

    // CR 610.3: exiled until this creature leaves the battlefield.
    @Test
    fun deepGrottoBat() = card(
        name = "Deep-Grotto Bat",
        cost = "{1}{B}",
        type = "Creature — Bat",
        pt = "1/1",
        text = """
            Flying, lifelink
            When this creature enters, look at target opponent's hand. You may exile a nonland card from it until this creature leaves the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        whenThisEnters(
            Step.Do(Effect.LookAt(Objects.Each(ObjectFilter.And(opponentsHand)))),
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land))) + opponentsHand), min = Value.Fixed(0), max = Value.Fixed(1)),
                bind = "chosen",
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 607.2a: the leaves trigger is linked to the enters trigger.
    @Test
    fun hypnoticFiend() = card(
        name = "Hypnotic Fiend",
        cost = "{1}{B}",
        type = "Creature — Nightmare Horror",
        pt = "1/1",
        text = """
            When this creature enters, target opponent reveals their hand and you choose a nonland card from it. Exile that card.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
    ) parsesTo takeANonlandCardUntilItLeaves

    @Test
    fun ebbmarshPilferer() = card(
        name = "Ebbmarsh Pilferer",
        cost = "{W}{B}",
        type = "Artifact Creature — Zombie",
        pt = "2/2",
        text = """
            When this creature enters, target opponent reveals their hand and you choose a nonland card from it. Exile that card.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
    ) parsesTo takeANonlandCardUntilItLeaves

    // ---- Permanents

    @Test
    fun velisMenderOfTheLostWay() = card(
        name = "Velis, Mender of the Lost Way",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/1",
        text = """
            Vigilance
            When Velis enters, destroy up to one target artifact or enchantment.
            {T}: You and target opponent each draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        whenThisEnters(
            Step.Do(Effect.Destroy(Objects.Target("permanent"))),
            targets = listOf(objectSlot("permanent", artifactOrEnchantment, min = 0)),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(drawCards(1, Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(Players.Target("opponent"))))))),
            ),
        ),
    )

    @Test
    fun overgrowthSage() = card(
        name = "Overgrowth Sage",
        cost = "{2}{G}",
        type = "Creature — Elf Shaman",
        pt = "2/1",
        text = "When this creature enters, you may destroy target artifact or enchantment.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.May(listOf(Step.Do(Effect.Destroy(Objects.Target("permanent"))))),
            targets = listOf(objectSlot("permanent", artifactOrEnchantment)),
        ),
    )

    // CR 118.12: exiling a card from your graveyard is a cost paid on resolution; the target is chosen with the trigger.
    @Test
    fun veiledWrecker() = card(
        name = "Veiled Wrecker",
        cost = "{1}{G}",
        type = "Creature — Shapeshifter",
        pt = "1/3",
        text = """
            Changeling (This card is every creature type.)
            When this creature enters, you may exile a creature card from your graveyard. If you do, exile target artifact or enchantment an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        whenThisEnters(
            Step.Pay(cost = Cost(listOf(CostPart.Exile(CostObjects.Choose(creature), from = Zone.Graveyard))), bind = "paid"),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Exile)))),
            ),
            targets = listOf(objectSlot("permanent", allOf(artifactOrEnchantment, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
        ),
    )

    // CR 607.2a: the token's size and controller come from the card the enters trigger exiled.
    @Test
    fun cloudhavenPhantasm() = card(
        name = "Cloudhaven Phantasm",
        cost = "{1}{W}{W}",
        type = "Creature — Kor Spirit",
        pt = "2/2",
        text = """
            When this creature enters, exile up to one target nonland, nontoken permanent you don't control with mana value 4 or less.
            When this creature leaves the battlefield, the exiled card's owner creates an X/X blue Illusion creature token, where X is the mana value of the exiled card.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            *exileAndRemember(Objects.Target("permanent")),
            targets = listOf(objectSlot("permanent", allOf(
                ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                ObjectFilter.Category(ObjectCategory.Permanent),
                ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)),
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(4)),
            ), min = 0)),
        ),
        whenThisLeaves(
            Step.Do(Effect.CreateToken(
                token = TokenSpec.Defined(
                    face = FaceDefinition(
                        name = "Illusion Token",
                        colorIndicator = listOf(Color.Blue),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Illusion")),
                        power = PrintedNumber.X,
                        toughness = PrintedNumber.X,
                    ),
                    power = Value.Of(NumericProperty.ManaValue, exiledCard, Combine.Total),
                    toughness = Value.Of(NumericProperty.ManaValue, exiledCard, Combine.Total),
                ),
                controller = Players.OwnerOf(exiledCard),
            )),
        ),
    )

    // CR 702.74a: evoke with a cost that isn't mana.
    @Test
    fun seclusion() = card(
        name = "Seclusion",
        cost = "{3}{W}{W}",
        type = "Creature — Elemental Incarnation",
        pt = "3/2",
        text = """
            Flash
            Lifelink
            When this creature enters, exile up to one other target creature. That creature's controller gains life equal to its power.
            Evoke—Exile a white card from your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Lifelink,
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Exile))),
            Step.Do(Effect.GainLife(
                players = Players.ControllerOf(Objects.Target("creature")),
                amount = Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single),
            )),
            targets = listOf(objectSlot("creature", allOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature), min = 0)),
        ),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(Color.White)), from = Zone.Hand)))),
    )

    // CR 613.1b: gaining control is a control-changing effect with no end. CR 107.3m: X in the enters trigger is the X
    // chosen for the spell (here its kicker's).
    @Test
    fun pilferingSkydiver() = card(
        name = "Pilfering Skydiver",
        cost = "{1}{U}",
        type = "Creature — Merfolk Rogue",
        pt = "2/1",
        text = """
            Kicker {X}. X can't be 0. (You may pay an additional {X} as you cast this spell.)
            Flying
            When this creature enters, if it was kicked, gain control of target artifact with mana value X or less. If that artifact is an Equipment, attach it to this creature.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{X}"))),
        Ability.Static(listOf(Mechanic.XAtLeast(1))),
        Keyword.Flying,
        whenThisEnters(
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.ControlledBy(Players.You)),
                affects = Affected.Objects(Objects.Target("artifact")),
                duration = Duration.Indefinitely,
            )),
            Step.If(
                condition = Condition.ObjectMatches(Objects.Target("artifact"), ObjectFilter.Sub(Subtype("Equipment"))),
                then = listOf(Step.Do(Effect.Attach(Objects.Target("artifact"), to = Recipients(objects = Objects.Self)))),
            ),
            targets = listOf(objectSlot("artifact", allOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.ManaValue(Comparison.AtMost, Value.CastX())))),
            interveningIf = Condition.Paid(Keyword.Kicker.Kind),
        ),
    )

    // ---- Creatures and players

    @Test
    fun mirrorwardMage() = card(
        name = "Mirrorward Mage",
        cost = "{1}{W}{U}",
        type = "Creature — Human Wizard",
        pt = "2/3",
        text = "When this creature enters, return target creature an opponent controls to its owner's hand. That creature's owner can't cast spells with the same name as that creature until your next turn.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Hand))),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.CantCast(allOf(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.SameNameAs(Objects.Target("creature"))))),
                affects = Affected.Players(Players.OwnerOf(Objects.Target("creature"))),
                duration = Duration.UntilYourNextTurn,
            )),
            targets = listOf(objectSlot("creature", allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
        ),
    )

    // CR 115.4: any target. CR 702.79a: persist.
    @Test
    fun bloodthirstyRedcap() = card(
        name = "Bloodthirsty Redcap",
        cost = "{2}{B/R}{B/R}",
        type = "Creature — Goblin Assassin",
        pt = "2/2",
        text = """
            When this creature enters, it deals damage equal to its power to any target.
            Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single))),
            targets = listOf(TargetSlot(
                name = "any",
                what = TargetKind.Either(
                    objects = ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))),
                    players = PlayerFilter.Any,
                ),
            )),
        ),
        Keyword.Persist,
    )

    // ---- Spells

    @Test
    fun spellStifler() = card(
        name = "Spell Stifler",
        cost = "{1}{W}{U}",
        type = "Creature — Spirit",
        pt = "2/3",
        text = """
            Flash
            Flying
            When this creature enters, exile target spell with mana value 4 or less.
            When this creature leaves the battlefield, the exiled card's owner may cast that card without paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        whenThisEnters(
            *exileAndRemember(Objects.Target("spell")),
            targets = listOf(objectSlot("spell", allOf(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(4))))),
        ),
        whenThisLeaves(
            Step.May(
                player = Players.OwnerOf(exiledCard),
                steps = listOf(Step.Do(Effect.Cast(exiledCard, withoutPaying = true, caster = Players.OwnerOf(exiledCard)))),
            ),
        ),
    )

    // CR 107.3c: X is defined by the text; it is counted again when the target is checked on resolution (CR 608.2b).
    @Test
    fun spellstammerPixie() = card(
        name = "Spellstammer Pixie",
        cost = "{1}{U}",
        type = "Creature — Faerie Wizard",
        pt = "1/1",
        text = """
            Flash
            Flying
            When this creature enters, counter target spell with mana value X or less, where X is the number of Faeries you control.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        whenThisEnters(
            Step.Do(Effect.Counter(Objects.Target("spell"))),
            targets = listOf(objectSlot("spell", allOf(
                ObjectFilter.Category(ObjectCategory.Spell),
                ObjectFilter.ManaValue(
                    Comparison.AtMost,
                    Value.Count(Objects.Each(allOf(ObjectFilter.Sub(Subtype("Faerie")), ObjectFilter.ControlledBy(PlayerFilter.You)))),
                ),
            ))),
        ),
    )

    // CR 603.7: a delayed trigger that fires once, the next time such a spell is cast this turn. CR 707.10c: new targets.
    @Test
    fun harborGateTempestcaller() = card(
        name = "Harbor Gate Tempestcaller",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Kicker {4}{U}
            When this creature enters, copy the next instant or sorcery spell with mana value 2 or less you cast this turn when you cast it. If this creature was kicked, copy that spell twice instead. You may choose new targets for the copies.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}{U}"))),
        whenThisEnters(
            Step.If(
                condition = Condition.Paid(Keyword.Kicker.Kind),
                then = listOf(copyNextCheapSpell(Step.Repeat(Value.Fixed(2), listOf(copyIt)))),
                otherwise = listOf(copyNextCheapSpell(copyIt)),
            ),
        ),
    )

    /** "the spell that triggered it", copied with new targets allowed */
    private val copyIt = Step.Do(Effect.CopySpell(Objects.EventObject(), newTargets = true))

    /** "the next instant or sorcery spell with mana value 2 or less you cast this turn" */
    private fun copyNextCheapSpell(step: Step) = Step.Do(Effect.CreateDelayedTrigger(
        trigger = Trigger.On(EventPattern.Cast(
            spell = allOf(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                ObjectFilter.Category(ObjectCategory.Spell),
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(2)),
            ),
            by = PlayerFilter.You,
        )),
        instructions = Instructions(steps = listOf(step)),
        duration = Duration.EndOfTurn,
    ))
}
