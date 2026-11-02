package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 11: Orzhov (white and black). Creatures in combat with this creature (CR 509.1a, h), protection from a
 * chosen colour, a token with a quoted ability (CR 111.4), a creature that dealt damage this turn, triggers on being
 * dealt damage, modal spells (CR 700.2), exiling a creature until your next upkeep with a delayed trigger (CR 603.7), an
 * Aura that stops attacks, blocks and activated abilities (CR 602.5), delayed triggers for the rest of the turn (CR
 * 603.7b), and every creature card with mana value X returning (CR 107.3a).
 */
class GtcChunk11OrzhovTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val nonlandPermanent = ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Category(ObjectCategory.Permanent)))

    // CR 509.1a, 509.1h: the creatures blocking it and those it blocks
    @Test
    fun charityBeast() = card(
        name = "Charity Beast",
        cost = "{2}{W}{B}",
        type = "Creature — Beast",
        pt = "6/6",
        text = "Creatures blocking or blocked by this creature have lifelink.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Lifelink))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature,
            ObjectFilter.Or(listOf(ObjectFilter.Blocking(Objects.Self), ObjectFilter.BlockedBy(Objects.Self)))))))),
    )

    // CR 702.16: protection from the colour chosen as it resolves
    @Test
    fun syndicateAristocrat() = card(
        name = "Syndicate Aristocrat",
        cost = "{W}{B}",
        type = "Creature — Human Advisor",
        pt = "2/2",
        text = "Sacrifice another creature: This creature gains protection from the color of your choice until end of turn.",
    ) parsesTo abilities(
        Ability.Activated(
            costOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)), count = n(1)))),
            steps(
                Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
                gains(Objects.Self, Keyword.Protection(ObjectFilter.OfColor(ColorRef.Chosen("color")))),
            ),
            rules = anyTime,
        ),
    )

    // CR 111.4: the token has the quoted ability; the named card is chosen, not targeted
    @Test
    fun oathpactAngel() = card(
        name = "Oathpact Angel",
        cost = "{3}{W}{B}{B}",
        type = "Creature — Angel",
        pt = "5/5",
        text = """
            Flying
            When this creature dies, create a 1/1 white and black Cleric creature token. It has "{3}{W}{B}{B}, {T}, Sacrifice this token: Return a card named Oathpact Angel from your graveyard to the battlefield."
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(diesSelf, steps(Step.Do(Effect.CreateToken(
            creatureToken("Cleric", listOf(Color.White, Color.Black), 1, 1, listOf(Ability.Activated(
                costOf(manaPart("{3}{W}{B}{B}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self)),
                steps(
                    Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Named("Oathpact Angel"), ObjectFilter.InZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(PlayerFilter.You))), n(1), n(1)), bind = "chosen", chooser = Players.You),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
                ),
                rules = anyTime,
            ))),
            n(1), Players.You,
        )))),
    )

    // A creature that was the source of damage this turn
    @Test
    fun headsmansSwing() = card(
        name = "Headsman's Swing",
        cost = "{W}{B}",
        type = "Instant",
        text = "Target creature that dealt damage this turn gets -5/-5 until end of turn.",
    ) parsesTo abilities(
        spell(
            pump(Objects.Target("creature"), -5, -5),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature,
                ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Source))))),
        ),
    )

    @Test
    fun highPriestOfAtonement() = card(
        name = "High Priest of Atonement",
        cost = "{W}{B}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = "Whenever this creature is dealt damage, you may destroy target nonland permanent.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
            Instructions(targets = listOf(slot("permanent", nonlandPermanent)), steps = listOf(Step.May(listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))), Players.You))),
        ),
    )

    // CR 700.2: one mode
    @Test
    fun ruthlessEviction() {
        fun exileAll(type: CardType) = Mode(steps(Step.Do(Effect.Move(Objects.Each(ObjectFilter.Type(type)), Destination(Zone.Exile)))))
        card(
            name = "Ruthless Eviction",
            cost = "{4}{W}{B}",
            type = "Sorcery",
            text = """
                Choose one —
                • Exile all artifacts.
                • Exile all creatures.
                • Exile all enchantments.
                • Exile all planeswalkers.
            """,
        ) parsesTo abilities(
            Ability.Spell(Instructions(modes = Modes(listOf(exileAll(CardType.Artifact), exileAll(CardType.Creature), exileAll(CardType.Enchantment),
                exileAll(CardType.Planeswalker)), min = 1, max = 1, chooser = Players.You))),
        )
    }

    // CR 603.7: returned at your next upkeep as a new object (CR 400.7), with haste
    @Test
    fun obzeratPhantomCouncil() = card(
        name = "Obzerat, Phantom Council",
        cost = "{1}{W}{W}{B}{B}",
        type = "Legendary Creature — Spirit Advisor",
        pt = "5/5",
        text = """
            When Obzerat enters, target opponent loses 2 life and you gain 2 life.
            At the beginning of your end step, you may exile Obzerat. If you do, return it to the battlefield under its owner's control at the beginning of your next upkeep. It gains haste.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.LoseLife(Players.Target("opponent"), n(2))), Step.Do(Effect.GainLife(Players.You, n(2))), targets = listOf(targetOpponent)),
        Ability.Triggered(
            Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
            steps(
                Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Self, from = Zone.Battlefield, postBind = "exiled"))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.CreateDelayedTrigger(
                    Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                    steps(
                        Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))), postBind = "returned")),
                        Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
                    ),
                )))),
            ),
        ),
    )

    // CR 602.5: none of its activated abilities, mana abilities included; the upkeep of the creature's controller
    @Test
    fun thousandLashShackle() = card(
        name = "Thousand Lash Shackle",
        cost = "{2}{W}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block, and its activated abilities can't be activated.
            At the beginning of the upkeep of enchanted creature's controller, that player loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true)), affects = Affected.Objects(enchanted)),
        Ability.Triggered(
            Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.Is(Players.ControllerOf(enchanted)))),
            steps(Step.Do(Effect.LoseLife(Players.EventPlayer, n(1)))),
        ),
    )

    // CR 700.2; the Auras you control attached to it go too; life lost equal to the toughness as it last existed
    @Test
    fun syndicateCharm() = card(
        name = "Syndicate Charm",
        cost = "{W}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Return target creature you control and all Auras you control attached to it to their owner's hand.
            • Destroy target creature and you lose life equal to its toughness.
            • Return target creature card with mana value 1 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(
                targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(Step.Do(Effect.Move(Objects.Union(listOf(Objects.Target("creature"),
                    Objects.AttachmentsOf(Objects.Target("creature"), ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Aura")), you))))), Destination(Zone.Hand)))),
            )),
            Mode(Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature"))), Step.Do(Effect.LoseLife(Players.You, toughness(Objects.Target("creature"))))),
            )),
            Mode(Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, n(1)),
                    ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield)))),
            )),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun cleanseTheProfane() = card(
        name = "Cleanse the Profane",
        cost = "{2}{W}{B}",
        type = "Sorcery",
        text = "Target opponent discards two cards and you gain 2 life.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Discard(Players.Target("opponent"), n(2), filter = ObjectFilter.Any)), Step.Do(Effect.GainLife(Players.You, n(2))), targets = listOf(targetOpponent)),
    )

    // CR 603.7b: a delayed trigger for the rest of the turn, each time you gain life
    @Test
    fun tithingGuildmage() = card(
        name = "Tithing Guildmage",
        cost = "{W}{B}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            {1}{W}{B}: Target creature gains lifelink until end of turn.
            {1}{W}{B}: Whenever you gain life this turn, each opponent loses that much life.
        """,
    ) parsesTo abilities(
        Ability.Activated(mana("{1}{W}{B}"), Instructions(targets = listOf(slot("creature", creature)), steps = listOf(gains(Objects.Target("creature"), Keyword.Lifelink))), rules = anyTime),
        Ability.Activated(mana("{1}{W}{B}"), steps(Step.Do(Effect.CreateDelayedTrigger(
            Trigger.On(EventPattern.GainLife(PlayerFilter.You)),
            steps(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.EventAmount))),
            duration = Duration.EndOfTurn, repeats = true,
        ))), rules = anyTime),
    )

    @Test
    fun summonApparition() = card(
        name = "Summon Apparition",
        cost = "{W/B}",
        type = "Instant",
        text = "Exile target card from a graveyard. Create a 1/1 white and black Spirit creature token with flying.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CreateToken(creatureToken("Spirit", listOf(Color.White, Color.Black), 1, 1, listOf(Keyword.Flying)), n(1), Players.You)),
            targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard))))),
        ),
    )

    @Test
    fun blessingOfOrzhova() = card(
        name = "Blessing of Orzhova",
        cost = "{1}{W/B}{W/B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has flying and lifelink.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(1)), Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.Lifelink))), affects = Affected.Objects(enchanted)),
    )

    // CR 107.3a: X announced as it is cast
    @Test
    fun eternalServitude() = card(
        name = "Eternal Servitude",
        cost = "{X}{W/B}{W/B}{W/B}",
        type = "Sorcery",
        text = "Return each creature card with mana value X from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, Value.Announced("X")),
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), Destination(Zone.Battlefield), postBind = "returned")),
            announced = listOf("X"),
        ),
    )
}
