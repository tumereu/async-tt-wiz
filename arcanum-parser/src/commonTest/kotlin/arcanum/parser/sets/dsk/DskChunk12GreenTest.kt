package arcanum.parser.sets.dsk

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
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.enchantment
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 12: the green cards. Becoming the target of an opponent's spell (CR 702.21a's event); an activated
 * ability from the hand; "discard a card or pay {2}. When you do" (a reflexive trigger, CR 603.12); shuffling into
 * libraries; tapped creatures each dealing damage by their power; lands put into the graveyard from the library; a modal
 * activated ability (CR 700.2); "choose a creature you control or reveal a creature card from your hand" (behold, CR
 * 701.4a); searching several zones for a card by name; counters on a mana ability's source; "the greatest power among".
 */
class DskChunk12GreenTest {
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private val untapped = ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped))
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val sorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
    private val drawACard = Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))

    /** An ability that works from the hand by discarding the card (the CR 113.6m default). */
    @Test
    fun altanorTheThriceSummoned() = card(
        name = "Altanor, the Thrice-Summoned",
        cost = "{5}{G}{G}",
        type = "Legendary Creature — Insect Beast",
        pt = "9/9",
        text = """
            Trample
            Whenever Altanor becomes the target of a spell or ability an opponent controls, draw a card.
            {1}{G}, Discard this card: Return target land card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), ObjectFilter.ControlledBy(PlayerFilter.Opponent))),
            instructions = Instructions(steps = listOf(drawACard)),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{G}")!!), CostPart.Discard(CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land)) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield, tapped = true)))),
            ),
            rules = any,
        ),
    )

    @Test
    fun anthrocrawler() = card(
        name = "Anthrocrawler",
        cost = "{3}{G}",
        type = "Creature — Insect",
        pt = "3/4",
        text = """
            Reach
            When this creature enters, you may discard a card or pay {2}. When you do, destroy target Room.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            Step.Pay(Cost(listOf(CostPart.OneOf(listOf(
                Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1))))),
                manaCost("{2}"),
            )))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("Room", ObjectFilter.Sub(Subtype("Room")))),
                steps = listOf(Step.Do(Effect.Destroy(Objects.Target("Room")))),
            ))))),
        ))),
    )

    /** Its owner shuffles it into their library; "you may shuffle up to four target cards" (zero to four, then a shuffle). */
    @Test
    fun freeingParting() = card(
        name = "Freeing Parting",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "The owner of target artifact or enchantment an opponent controls shuffles it into their library. You may shuffle up to four target cards from your graveyard into your library.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Library))),
            Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Target("permanent")))),
            Step.May(listOf(
                Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(Players.You)),
            ), player = Players.You),
            targets = listOf(
                slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), enchantment)),
                    ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
                slot("cards", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card)) + yourGraveyard), min = 0, max = 4),
            ),
        ),
    )

    /** "They each deal damage equal to their power": each tapped creature is a source of its own damage (CR 120.1). */
    @Test
    fun concertedClobbering() = card(
        name = "Concerted Clobbering",
        cost = "{G}",
        type = "Sorcery",
        text = "Tap one or two target untapped creatures you control. They each deal damage equal to their power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Tap(Objects.Target("creatures"))),
            Step.ForEach(Each.Objects(Objects.Target("creatures")), bind = "source", steps = listOf(Step.Do(Effect.DealDamage(
                Recipients(objects = Objects.Target("creature")), Value.Of(NumericProperty.Power, Objects.Bound("source"), Combine.Single),
                source = Objects.Bound("source"))))),
            targets = listOf(
                slot("creatures", ObjectFilter.And(listOf(untapped, creature, youControl)), max = 2),
                slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
            ),
        ),
    )

    @Test
    fun fleshBorer() = card(
        name = "Flesh Borer",
        cost = "{1}{G}",
        type = "Creature — Insect",
        pt = "2/2",
        text = """
            Deathtouch
            Whenever this creature attacks, another target creature you control gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, youControl)))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Deathtouch))), Affected.Objects(Objects.Target("creature")),
                Duration.EndOfTurn))),
        )),
    )

    /** "Put them onto the battlefield tapped": the batch's land cards, now in the graveyard (CR 603.10a: not looking back). */
    @Test
    fun hedgeMulcher() = card(
        name = "Hedge Mulcher",
        cost = "{2}{G}{G}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            Whenever this Vehicle attacks, you may mill two cards.
            Whenever one or more land cards are put into your graveyard from your library, put them onto the battlefield tapped.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(
            Step.May(listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(2)))), player = Players.You),
        ))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), ObjectFilter.OwnedBy(PlayerFilter.You))),
                from = Zone.Library, to = Zone.Graveyard), oneOrMore = true),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, tapped = true))))),
        ),
        Keyword.Crew(1),
    )

    /** A modal activated ability (CR 700.2): its modes are chosen as it is activated. */
    @Test
    fun insidiousMold() = card(
        name = "Insidious Mold",
        cost = "{G}",
        type = "Creature — Fungus",
        pt = "1/2",
        text = """
            {2}, Sacrifice this creature: Choose one —
            • Destroy target artifact.
            • Destroy target enchantment.
            • Draw a card. Then you may put a land card from your hand onto the battlefield tapped.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{2}")!!), CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(modes = Modes(
                options = listOf(
                    Mode(Instructions(targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact))),
                        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("artifact")))))),
                    Mode(Instructions(targets = listOf(slot("enchantment", enchantment)),
                        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("enchantment")))))),
                    Mode(Instructions(steps = listOf(
                        drawACard,
                        Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), ObjectFilter.InZone(Zone.Hand),
                            ObjectFilter.OwnedBy(PlayerFilter.You))), Value.Fixed(0), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                        Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true))),
                    ))),
                ),
                min = 1,
                max = 1,
                chooser = Players.You,
            )),
            rules = any,
        ),
    )

    /** "Choose a creature you control or reveal a creature card from your hand" is beholding a creature (CR 701.4a); its power
     * as it was chosen or revealed. */
    @Test
    fun monstrousArrival() = card(
        name = "Monstrous Arrival",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, choose a creature you control or reveal a creature card from your hand.
            Monstrous Arrival deals damage equal to the power of the creature you chose or the card you revealed to target creature.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Action(KeywordAction.Behold(creature, arcanum.api.Value.Fixed(1)), bind = "beheld"))))),
            affects = Affected.Objects(Objects.Self),
        ),
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Of(NumericProperty.Power, Objects.Bound("beheld"), Combine.Single),
                source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    /** A search of the graveyard, hand and library (CR 701.23); "if you search your library this way, shuffle". */
    @Test
    fun utterItsName() = card(
        name = "Utter Its Name",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Mill three cards. Then you may return a creature or land card from your graveyard to your hand.
            Exile this card and two other cards named Utter Its Name from your graveyard: Search your graveyard, hand, and/or library for a card named Altanor, the Thrice-Summoned and put it onto the battlefield. If you search your library this way, shuffle. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Mill(Players.You, Value.Fixed(3))),
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Land)))) + yourGraveyard),
                Value.Fixed(0), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
        ),
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.Exile(CostObjects.Self, from = Zone.Graveyard),
                CostPart.Exile(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Named("Utter Its Name"))),
                    count = Value.Fixed(2)), from = Zone.Graveyard),
            )),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Search(Players.You, ObjectFilter.Named("Altanor, the Thrice-Summoned"), max = Value.Fixed(1),
                    zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                Step.If(Condition.Searched("found", Zone.Library), then = listOf(Step.Do(Effect.Shuffle(Players.You)))),
            )),
            rules = sorcery,
        ),
    )

    /** A mana ability that also puts a counter on its source (CR 605.1a); tokens counted by its counters. */
    @Test
    fun twitchyDoll() = card(
        name = "Twitchy Doll",
        cost = "{1}{G}",
        type = "Artifact Creature — Spider Toy",
        pt = "2/2",
        text = """
            {T}: Add one mana of any color. Put a nest counter on this creature.
            {T}, Sacrifice this creature: Create a 2/2 green Spider creature token with reach for each counter on this creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)),
                Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Named("nest"), Value.Fixed(1))),
            )),
            rules = any,
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(
                token(listOf("Spider"), listOf(Color.Green), 2, 2, abilities = listOf(Keyword.Reach)),
                count = Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single),
                controller = Players.You,
            )))),
            rules = sorcery,
        ),
    )

    /** "Tap another untapped creature you control" as a cost; "the greatest power among creatures you control". */
    @Test
    fun tavikThePummeler() = card(
        name = "Tavik, the Pummeler",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Elf Warrior",
        pt = "3/3",
        text = """
            Tap another untapped creature you control: Tavik gains indestructible until end of turn. Tap it.
            {3}{G}{G}: Creatures you control get +X/+X until end of turn, where X is the greatest power among creatures you control.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapObjects(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)),
                count = Value.Fixed(1))))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Indestructible))), Affected.Objects(Objects.Self), Duration.EndOfTurn)),
                Step.Do(Effect.Tap(Objects.Self)),
            )),
            rules = any,
        ),
        Ability.Activated(
            cost = manaCost("{3}{G}{G}"),
            instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(
                    Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest),
                    Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest),
                )),
                Affected.Objects(Objects.Each(creatureYouControl)),
                Duration.EndOfTurn,
            )))),
            rules = any,
        ),
    )

    @Test
    fun slobberingBranchsnapper() = card(
        name = "Slobbering Branchsnapper",
        cost = "{4}{G}{G}",
        type = "Creature — Lizard",
        pt = "7/6",
        text = """
            Trample
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Typecycling(ObjectFilter.Sub(Subtype("Forest")), "Forestcycling", manaCost("{2}")),
    )
}
