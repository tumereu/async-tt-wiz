package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.CardType
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 5: mana cards with an extra twist: a conditional "instead" (threshold), splice, counting cards by name, imprint
 * (an exiled card remembered by the permanent, CR 607.2a), discarding your hand as a cost, "activate only as an
 * instant", coin flips, an optional upkeep payment, a replacement of entering, an Equipment granting a mana ability,
 * and X mana in any combination.
 */
class Chunk05TrickyManaTest {
    // ---- Shared shapes

    private fun add(kind: ManaKind) = Step.Do(Effect.AddMana(ManaProduction(kind), players = Players.You))
    private fun fixed(mana: String) = ManaKind.Fixed(ManaCost.parse(mana)!!)
    private fun manaPart(mana: String) = CostPart.Mana(ManaCost.parse(mana)!!)
    private val sacrificeSelf = CostPart.Sacrifice(CostObjects.Self)
    private val drawACard = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))

    /** "[cost]: Add [mana]." */
    private fun manaAbility(vararg cost: CostPart, kind: ManaKind) = Ability.Activated(
        cost = Cost(cost.toList()),
        instructions = Instructions(
            steps = listOf(add(kind)),
        ),
        rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
    )

    /** "When this artifact is put into a graveyard from the battlefield, draw a card." */
    private val drawWhenItDies = Ability.Triggered(
        trigger = Trigger.On(EventPattern.ZoneChange(
            objects = ObjectFilter.Is(Objects.Self),
            from = Zone.Battlefield,
            to = Zone.Graveyard,
        )),
        instructions = Instructions(
            steps = listOf(drawACard),
        ),
    )

    // ---- Rituals

    @Test
    fun covensRite() = card(
        name = "Coven's Rite",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Add {B}{B}{B}.
            Threshold — Add {B}{B}{B}{B}{B} instead if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            abilityWord = "Threshold",
            instructions = Instructions(
                steps = listOf(
                    Step.If(
                        condition = Condition.Exists(
                            filter = ObjectFilter.And(listOf(
                                ObjectFilter.InZone(Zone.Graveyard),
                                ObjectFilter.OwnedBy(PlayerFilter.You),
                            )),
                            atLeast = 7,
                        ),
                        then = listOf(add(fixed("{B}{B}{B}{B}{B}"))),
                        otherwise = listOf(add(fixed("{B}{B}{B}"))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun franticInvocation() = card(
        name = "Frantic Invocation",
        cost = "{1}{R}",
        type = "Instant — Arcane",
        text = """
            Add {R}{R}{R}.
            Splice onto Arcane {1}{R} (As you cast an Arcane spell, you may reveal this card from your hand and pay its splice cost. If you do, add this card's effects to that spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(add(fixed("{R}{R}{R}"))),
        )),
        Keyword.Splice(onto = ObjectFilter.Sub(Subtype("Arcane")), cost = manaCost("{1}{R}")),
    )

    @Test
    fun riteOfEmbers() = card(
        name = "Rite of Embers",
        cost = "{R}",
        type = "Sorcery",
        text = "Add {R}{R}, then add {R} for each card named Rite of Embers in each graveyard.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                add(fixed("{R}{R}")),
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(
                    mana = ManaCost.parse("{R}")!!,
                    times = Value.Count(Objects.Each(ObjectFilter.And(listOf(
                        ObjectFilter.Named("Rite of Embers"),
                        ObjectFilter.InZone(Zone.Graveyard),
                    )))),
                )), players = Players.You)),
            ),
        )),
    )

    // ---- Artifacts

    @Test
    fun prismaticOrb() = card(
        name = "Prismatic Orb",
        cost = "{1}",
        type = "Artifact",
        text = "{1}, {T}, Sacrifice this artifact: Add one mana of any color. Draw a card. (Activate only as an instant.)",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(manaPart("{1}"), CostPart.TapSelf, sacrificeSelf)),
            instructions = Instructions(
                steps = listOf(
                    add(anyOneColor(1)),
                    drawACard,
                ),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun chromedLocket() = card(
        name = "Chromed Locket",
        cost = "{0}",
        type = "Artifact",
        text = """
            Imprint — When this artifact enters, you may exile a nonartifact, nonland card from your hand.
            {T}: Add one mana of any of the exiled card's colors.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            abilityWord = "Imprint",
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = ObjectFilter.And(listOf(
                                ObjectFilter.Not(ObjectFilter.Type(CardType.Artifact)),
                                ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                                ObjectFilter.InZone(Zone.Hand),
                                ObjectFilter.OwnedBy(PlayerFilter.You),
                            )),
                            min = Value.Fixed(0),
                            max = Value.Fixed(1),
                        ),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Remember("imprinted", Objects.Bound("exiled"))),
                ),
            ),
        ),
        manaAbility(
            CostPart.TapSelf,
            kind = ManaKind.Choose(
                amount = Value.Fixed(1),
                colors = listOf(ColorRef.Of(Objects.Remembered("imprinted"))),
            ),
        ),
    )

    @Test
    fun jackalsEyeGem() = card(
        name = "Jackal's Eye Gem",
        cost = "{0}",
        type = "Artifact",
        text = "Discard your hand, Sacrifice this artifact: Add three mana of any one color. Activate only as an instant.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.Discard(CostObjects.These(Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                ))))),
                sacrificeSelf,
            )),
            rules = ActivationRules(timing = Timing.Instant, activatedBy = PlayerFilter.You),
            instructions = Instructions(
                steps = listOf(add(anyOneColor(3))),
            ),
        ),
    )

    @Test
    fun lotusBud() = card(
        name = "Lotus Bud",
        cost = null,
        type = "Artifact",
        text = """
            Suspend 3—{0} (Rather than cast this card from your hand, pay {0} and exile it with three time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)
            {T}, Sacrifice this artifact: Add three mana of any one color.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(3, manaCost("{0}")),
        manaAbility(CostPart.TapSelf, sacrificeSelf, kind = anyOneColor(3)),
    )

    @Test
    fun manaReliquary() = card(
        name = "Mana Reliquary",
        cost = "{0}",
        type = "Artifact",
        text = """
            At the beginning of your upkeep, flip a coin. If you lose the flip, this artifact deals 3 damage to you.
            {T}: Add {C}{C}.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.FlipCoin(bind = "flip", players = Players.You)),
                    Step.If(
                        condition = Condition.Not(Condition.WonFlip("flip")),
                        then = listOf(
                            Step.Do(Effect.DealDamage(
                                Recipients(players = Players.You),
                                amount = Value.Fixed(3),
                                source = Objects.Self,
                            )),
                        ),
                    ),
                ),
            ),
        ),
        manaAbility(CostPart.TapSelf, kind = fixed("{C}{C}")),
    )

    @Test
    fun manaStrongbox() = card(
        name = "Mana Strongbox",
        cost = "{1}",
        type = "Artifact",
        text = """
            This artifact doesn't untap during your untap step.
            At the beginning of your upkeep, you may pay {4}. If you do, untap this artifact.
            At the beginning of your draw step, if this artifact is tapped, it deals 1 damage to you.
            {T}: Add {C}{C}{C}.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(cost = manaCost("{4}"), bind = "paid", payer = Players.You),
                    Step.If(
                        condition = Condition.Did("paid"),
                        then = listOf(Step.Do(Effect.Untap(Objects.Self))),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Draw, PlayerFilter.You)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Tapped)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.DealDamage(
                        Recipients(players = Players.You),
                        amount = Value.Fixed(1),
                        source = Objects.Self,
                    )),
                ),
            ),
        ),
        manaAbility(CostPart.TapSelf, kind = fixed("{C}{C}{C}")),
    )

    @Test
    fun gemstoneCharm() = card(
        name = "Gemstone Charm",
        cost = "{0}",
        type = "Artifact",
        text = """
            If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the battlefield. If you don't, put it into its owner's graveyard.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                instead = Instructions(
                    steps = listOf(
                        Step.Pay(
                            cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(
                                ObjectFilter.Type(CardType.Land),
                                count = Value.Fixed(1),
                            )))),
                            bind = "discarded",
                            payer = Players.You,
                        ),
                        Step.If(
                            condition = Condition.Did("discarded"),
                            then = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield)))),
                            otherwise = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Graveyard)))),
                        ),
                    ),
                ),
            ),
        ), affects = Affected.Objects(Objects.Self)),
        manaAbility(CostPart.TapSelf, kind = anyOneColor(1)),
    )

    @Test
    fun edenCloak() = card(
        name = "Eden Cloak",
        cost = "{0}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has "{T}: Add one mana of any color."
            Equip {1}
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.GainAbilities(listOf(
                    manaAbility(CostPart.TapSelf, kind = anyOneColor(1)),
                )),
            ),
            affects = Affected.Objects(Objects.AttachedTo(of = Objects.Self)),
        ),
        Keyword.Equip(manaCost("{1}")),
    )

    @Test
    fun sorcerersFlares() = card(
        name = "Sorcerer's Flares",
        cost = "{1}",
        type = "Artifact",
        text = """
            This artifact enters tapped.
            {X}, {T}, Sacrifice this artifact: Add X mana in any combination of colors.
            When this artifact is put into a graveyard from the battlefield, draw a card.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ), affects = Affected.Objects(Objects.Self)),
        Ability.Activated(
            cost = Cost(listOf(manaPart("{X}"), CostPart.TapSelf, sacrificeSelf)),
            instructions = Instructions(
                announced = listOf("X"),
                steps = listOf(
                    add(ManaKind.Choose(amount = Value.Announced(name = "X"), combination = true)),
                ),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        drawWhenItDies,
    )
}
