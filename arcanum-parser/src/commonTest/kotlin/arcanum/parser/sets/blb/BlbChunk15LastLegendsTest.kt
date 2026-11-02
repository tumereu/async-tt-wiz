package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
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
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaPaymentWay
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.drawCards
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 15: the set's last legends and an artifact (blb gaps 2). The first instant, sorcery or Otter spell of
 * the turn (CR 603.4), "you may have target opponent draw" before a copy (CR 707.10), mana for creature spells with {X} in
 * their mana costs (CR 106.6, 107.3), X of the permanent's spell in its enters trigger (CR 107.3m), exiling until a nonland
 * card and casting it by discarding (CR 118.9, 608.2g), a finality counter (CR 122.1h), an emblem granting storm (CR 114,
 * 702.40a), creatures exiled under opponents' control this turn, and tapping creatures for an activation's mana.
 */
class BlbChunk15LastLegendsTest {
    private val yourAbilities = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val spell = ObjectFilter.Category(ObjectCategory.Spell)
    private fun first(spells: ObjectFilter) = ObjectFilter.NthSpellThisTurn(spells, n = 1)
    private fun loyalty(amount: Int, instructions: Instructions) =
        Ability.Activated(Cost(listOf(CostPart.Loyalty(one(amount)))), instructions, yourAbilities)
    private val toPlayer = RecipientFilter(players = PlayerFilter.Any)

    // CR 603.4: which spell of the turn it is can't change once cast; CR 707.10c: new targets for the copy
    @Test
    fun mirelleBranchingTempest() = card(
        name = "Mirelle, Branching Tempest",
        cost = "{3}{U}{R}",
        type = "Legendary Creature — Otter Wizard",
        pt = "3/5",
        text = "Whenever you cast a spell, if it's the first instant spell, the first sorcery spell, or the first Otter spell other than Mirelle you've cast this turn, you may have target opponent draw a card. If you do, copy that spell. You may choose new targets for the copy.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Cast(
                ObjectFilter.Or(listOf(
                    first(ObjectFilter.Type(CardType.Instant)),
                    first(ObjectFilter.Type(CardType.Sorcery)),
                    first(ObjectFilter.And(listOf(sub("Otter"), ObjectFilter.Not(ObjectFilter.Is(Objects.Self))))),
                )),
                by = PlayerFilter.You,
            )),
            Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(
                    Step.Pay(steps = listOf(Step.Do(Effect.Draw(Players.Target("opponent"), one()))), payer = Players.You, bind = "paid"),
                    Step.If(Condition.Did("paid"), then = listOf(
                        Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true)),
                    )),
                ),
            ),
        ),
    )

    // CR 106.6: restricted mana; CR 107.3: a spell with {X} in its mana cost
    @Test
    fun wennaStartledOracle() = card(
        name = "Wenna, Startled Oracle",
        cost = "{G}{W}{U}",
        type = "Legendary Creature — Frog Druid",
        pt = "1/3",
        text = """
            Whenever you cast a creature spell with mana value 4 or greater, you draw a card, gain 1 life, and put a +1/+1 counter on Wenna.
            {T}: Add X mana of any one color, where X is Wenna's power. Spend this mana only to cast creature spells with mana value 4 or greater or creature spells with {X} in their mana costs.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Cast(ObjectFilter.And(listOf(creature, ObjectFilter.ManaValue(Comparison.AtLeast, one(4)))), by = PlayerFilter.You)),
            steps(
                drawCards(1),
                Step.Do(Effect.GainLife(Players.You, one())),
                counters(Objects.Self),
            ),
        ),
        Ability.Activated(
            Cost(listOf(CostPart.TapSelf)),
            steps(Step.Do(Effect.AddMana(
                ManaProduction(
                    ManaKind.Choose(amount = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)),
                    restriction = ObjectFilter.Or(listOf(
                        ObjectFilter.And(listOf(spell, creature, ObjectFilter.ManaValue(Comparison.AtLeast, one(4)))),
                        ObjectFilter.And(listOf(spell, creature, ObjectFilter.XInManaCost)),
                    )),
                ),
                players = Players.You,
            ))),
            yourAbilities,
        ),
    )

    // CR 107.3m: X in the enters trigger is the X its spell was cast with; CR 305.2: an additional land
    @Test
    fun thornbristleGrimWarden() = card(
        name = "Thornbristle, Grim Warden",
        cost = "{X}{R}{R}{G}{G}",
        type = "Legendary Creature — Badger Warrior",
        pt = "5/5",
        text = """
            Trample
            When Thornbristle enters, exile the top X cards of your library. Until the end of your next turn, you may play those cards.
            You may play an additional land on each of your turns.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        whenThisEnters(
            Step.Do(Effect.Move(
                Objects.LibraryCards(Players.You, Value.CastX(of = Objects.Self), fromTop = true),
                Destination(Zone.Exile),
                postBind = "exiled",
            )),
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn,
            )),
        ),
        Ability.Static(listOf(Mechanic.ExtraLandPlays(1)), affects = Affected.Players(Players.You)),
    )

    // CR 118.9: discarding a card rather than paying its mana cost; CR 608.2g: cast while the ability resolves
    @Test
    fun theNotoriousSnaketooth() = card(
        name = "The Notorious Snaketooth",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Weasel Mercenary",
        pt = "3/3",
        text = """
            Menace
            Whenever The Notorious Snaketooth deals combat damage to a player, exile cards from the top of your library until you exile a nonland card. You may cast that card by discarding a card rather than paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), toPlayer, combat = true)),
            steps(
                Step.Do(Effect.RevealUntil(
                    ObjectFilter.Not(land),
                    library = Players.You,
                    count = one(),
                    reveal = true,
                    into = Zone.Exile,
                    foundBind = "found",
                    restBind = "rest",
                )),
                Step.May(
                    listOf(Step.Do(Effect.Cast(
                        Objects.Bound("found"),
                        PlayKind.Cast,
                        cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = one())))),
                        caster = Players.You,
                    ))),
                    player = Players.You,
                ),
            ),
        ),
    )

    // CR 122.1h: a finality counter; CR 603.2c: one trigger per player dealt damage by the batch
    @Test
    fun feathraTheGaleCrowned() = card(
        name = "Feathra, the Gale-Crowned",
        cost = "{3}{W}{U}",
        type = "Legendary Creature — Bird Scout",
        pt = "4/5",
        text = """
            Flying
            Whenever one or more Birds you control deal combat damage to a player, choose one —
            • You may put a Bird creature card from your hand or graveyard onto the battlefield with a finality counter on it.
            • Put a +1/+1 counter on each Bird you control.
            • Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            Trigger.On(
                listOf(EventPattern.Damage(ObjectFilter.And(listOf(sub("Bird"), you)), toPlayer, combat = true)),
                oneOrMore = true,
                batchPerPlayer = true,
            ),
            Instructions(modes = Modes(
                listOf(
                    Mode(steps(
                        Step.Choose(
                            Choice.Objects(
                                ObjectFilter.And(listOf(
                                    sub("Bird"),
                                    creature,
                                    ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Graveyard))),
                                    ObjectFilter.OwnedBy(PlayerFilter.You),
                                )),
                                min = one(0),
                                max = one(1),
                            ),
                            bind = "chosen",
                            chooser = Players.You,
                        ),
                        Step.Do(Effect.Move(
                            Objects.Bound("chosen"),
                            Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, one()))),
                        )),
                    )),
                    Mode(steps(counters(Objects.Each(ObjectFilter.And(listOf(sub("Bird"), you)))))),
                    Mode(steps(drawCards(1))),
                ),
                min = 1,
                max = 1,
                chooser = Players.You,
            )),
        ),
    )

    // CR 114.4: an emblem's abilities work in the command zone; CR 702.40a: storm triggers as the spell is cast
    @Test
    fun zelkaSparkingWit() = card(
        name = "Zelka, Sparking Wit",
        cost = "{2}{U}{R}",
        type = "Legendary Planeswalker — Ral",
        loyalty = "4",
        text = """
            Whenever you cast a noncreature spell, put a loyalty counter on Zelka.
            [+1]: Create a 1/1 blue and red Otter creature token with prowess.
            [−3]: Draw three cards, then discard two cards.
            [−10]: Draw three cards. You get an emblem with "Instant and sorcery spells you cast have storm." (Whenever you cast an instant or sorcery spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), by = PlayerFilter.You)),
            steps(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Loyalty, one()))),
        ),
        loyalty(1, steps(Step.Do(Effect.CreateToken(
            creatureToken("Otter", listOf(Color.Blue, Color.Red), 1, 1, listOf(Keyword.Prowess)),
            count = one(),
            controller = Players.You,
        )))),
        loyalty(-3, steps(drawCards(3), Step.Do(Effect.Discard(Players.You, one(2), filter = ObjectFilter.Any)))),
        loyalty(-10, steps(
            drawCards(3),
            Step.Do(Effect.CreateEmblem(
                listOf(Ability.Static(
                    listOf(Mechanic.GainAbilities(listOf(Keyword.Storm))),
                    affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                        spell,
                        you,
                    )))),
                )),
                players = Players.You,
            )),
        )),
    )

    // CR 614.1a, 700.4: dying replaced by exile; the creatures exiled from the battlefield under opponents' control this turn
    @Test
    fun gnawkTheUnyielding() = card(
        name = "Gnawk, the Unyielding",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Rat Rogue",
        pt = "3/4",
        text = """
            Ward {2}
            If a creature an opponent controls would die, exile it instead.
            At the beginning of each end step, create X 1/1 black Rat creature tokens with "This token gets +1/+1 for each other Rat you control," where X is the number of creatures that were exiled under your opponents' control this turn.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(creature, opponents)), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Triggered(
            Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any)),
            steps(Step.Do(Effect.CreateToken(
                creatureToken("Rat", listOf(Color.Black), 1, 1, listOf(Ability.Static(
                    listOf(Mechanic.ModifyPowerToughness(otherRats, otherRats)),
                    affects = Affected.Objects(Objects.Self),
                ))),
                count = Value.EventsThisTurn(
                    EventPattern.ZoneChange(ObjectFilter.And(listOf(creature, opponents)), from = Zone.Battlefield, to = Zone.Exile),
                    Measure.Objects,
                ),
                controller = Players.You,
            ))),
        ),
    )

    // CR 602.2b: the activation cost is paid as a spell's is; a creature may be tapped for any one mana of it
    @Test
    fun inheritedChronicle() = card(
        name = "Inherited Chronicle",
        cost = "{1}",
        type = "Artifact — Book",
        text = "{4}, {T}: Draw a card. For each mana in this ability's activation cost, you may tap an untapped creature you control rather than pay that mana. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(
            Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{4}")!!), CostPart.TapSelf)),
            steps(drawCards(1)),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You, payManaBy = listOf(ManaPaymentWay.TapCreaturesForAnyMana)),
        ),
    )

    private val otherRats = Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), sub("Rat"), you))))
}
