package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 50: creatures that come back from the graveyard or care about it. Triggers that return the card itself ("return
 * this card from your graveyard to the battlefield"), which work only in the graveyard because they move it out of there
 * (CR 113.6m); casting it from the graveyard for an alternative cost (CR 118.9); "another creature"; cards put into
 * your graveyard from anywhere; "your third card in a turn", "the second creature spell you cast this turn" and a spell
 * cast from anywhere other than your hand (CR 601.2a), after a flavor word (CR 207.2d);
 * historic spells (CR 700.6); "sacrifice it unless you sacrifice a land" (CR 118.12a).
 */
class Chunk50GraveyardCreaturesTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val another = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
    private val inYourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))

    /** "Return this card from your graveyard to the battlefield[ tapped]." */
    private fun returnToBattlefield(tapped: Boolean = false) =
        Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield, tapped = tapped), postBind = "returned"))

    /** "a land card is put into your graveyard from anywhere": a card you own; land tokens aren't cards (CR 108.2b). */
    private val landCardToYourGraveyard = EventPattern.ZoneChange(
        objects = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You))),
        to = Zone.Graveyard,
    )

    // ---- Triggers that return the card itself (CR 113.6m: they work only in the graveyard)

    @Test
    fun netherboundTurncoat() = card(
        name = "Netherbound Turncoat",
        cost = "{B}{B}",
        type = "Creature — Spirit",
        pt = "1/1",
        text = """
            Haste
            Shadow (This creature can block or be blocked by only creatures with shadow.)
            Whenever another creature is put into your graveyard from the battlefield, you may pay {B}. If you do, return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Keyword.Shadow,
        Ability.Triggered(
            functionsIn = listOf(Zone.Graveyard),
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.And(listOf(another, creature, ObjectFilter.OwnedBy(PlayerFilter.You))),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(manaCost("{B}"), bind = "paid", payer = Players.You),
                    Step.If(Condition.Did("paid"), then = listOf(returnToBattlefield())),
                ),
            ),
        ),
    )

    @Test
    fun pilferingNibbler() = card(
        name = "Pilfering Nibbler",
        cost = "{U}{B}",
        type = "Creature — Faerie Rogue",
        pt = "2/1",
        text = """
            Flying
            When you draw your third card in a turn, return this card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            functionsIn = listOf(Zone.Graveyard),
            trigger = Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 3),
            instructions = Instructions(steps = listOf(returnToBattlefield(tapped = true))),
        ),
    )

    /** "Curse of the Walking Pox" is a flavor word (CR 207.2d): no rules meaning, kept like an ability word. A spell is
     * cast from the zone it was in (CR 601.2a). */
    @Test
    fun plagueShamblers() = card(
        name = "Plague Shamblers",
        cost = "{2}{B}",
        type = "Creature — Zombie",
        pt = "3/1",
        text = """
            Deathtouch
            Curse of the Walking Pox — Whenever you cast a spell from anywhere other than your hand, return this card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(
            functionsIn = listOf(Zone.Graveyard),
            abilityWord = "Curse of the Walking Pox",
            trigger = Trigger.On(EventPattern.Cast(
                spell = ObjectFilter.Not(ObjectFilter.WasCast(Zone.Hand)),
                by = PlayerFilter.You,
            )),
            instructions = Instructions(steps = listOf(returnToBattlefield(tapped = true))),
        ),
    )

    @Test
    fun skritGoblinDawdler() = card(
        name = "Skrit, Goblin Dawdler",
        cost = "{2}{R}",
        type = "Legendary Creature — Goblin",
        pt = "1/1",
        text = "At the beginning of your upkeep, you may return this card from your graveyard to your hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            functionsIn = listOf(Zone.Graveyard),
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(Step.May(
                    listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Hand)))),
                    player = Players.You,
                )),
            ),
        ),
    )

    /** CR 603.4: which creature spell of the turn the cast spell is can't change once it is cast, so the intervening
     * "if it's the second creature spell" is the trigger event itself: the second creature spell you cast this turn. */
    @Test
    fun vengeroot() = card(
        name = "Vengeroot",
        cost = "{2}{G}{G}",
        type = "Creature — Elemental",
        pt = "4/3",
        text = """
            Haste
            Whenever you cast a spell, if it's the second creature spell you cast this turn, you may return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            functionsIn = listOf(Zone.Graveyard),
            trigger = Trigger.On(listOf(EventPattern.Cast(creature, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(steps = listOf(Step.May(listOf(returnToBattlefield()), player = Players.You))),
        ),
    )

    // ---- Casting and playing from the graveyard

    /** CR 118.9: an alternative cost, here with the permission to cast it from the graveyard. */
    @Test
    fun wyrmOfAshenGraves() = card(
        name = "Wyrm of Ashen Graves",
        cost = "{5}{B}{B}",
        type = "Creature — Zombie Dragon",
        pt = "6/6",
        text = """
            Flying
            You may cast this creature from your graveyard by paying {B}{B} and sacrificing two creatures rather than paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.MayPlay(
            from = Zone.Graveyard,
            cost = Cost(listOf(
                CostPart.Mana(arcanum.api.ManaCost.parse("{B}{B}")!!),
                CostPart.Sacrifice(CostObjects.Choose(creature, count = Value.Fixed(2))),
            )),
            how = PlayKind.Cast,
            holder = PlayerFilter.You,
        )), affects = Affected.Objects(Objects.Self)),
    )

    /** CR 305.1: a land played this way uses a land play. */
    @Test
    fun dunescourDigger() = card(
        name = "Dunescour Digger",
        cost = "{2}{G}",
        type = "Creature — Snake Cleric",
        pt = "2/3",
        text = "You may play lands from your graveyard.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land)) + inYourGraveyard))),
        ),
    )

    /** CR 700.6: artifacts, legendaries and Sagas are historic. */
    @Test
    fun ossiraAncestralHerald() = card(
        name = "Ossira, Ancestral Herald",
        cost = "{3}{W}",
        type = "Legendary Creature — Bird Cleric",
        pt = "2/2",
        text = """
            Flying
            Whenever you cast a historic spell, return target creature card with mana value 3 or less from your graveyard to the battlefield. (Artifacts, legendaries, and Sagas are historic.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(
                spell = ObjectFilter.Or(listOf(
                    ObjectFilter.Type(CardType.Artifact),
                    ObjectFilter.Super(Supertype.Legendary),
                    ObjectFilter.Sub(Subtype("Saga")),
                )),
                by = PlayerFilter.You,
            )),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "card",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        creature,
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                    ) + inYourGraveyard)),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Battlefield)))),
            ),
        ),
    )

    // ---- Lands put into your graveyard

    @Test
    fun mirebloatTheUndermuck() = card(
        name = "Mirebloat, the Undermuck",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Ooze",
        pt = "3/3",
        text = """
            Trample
            Whenever a land card is put into your graveyard from anywhere, put a +1/+1 counter on Mirebloat.
            Remove three +1/+1 counters from Mirebloat: Return it to its owner's hand.
            When Mirebloat leaves the battlefield, return up to three target land cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(landCardToYourGraveyard),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1)))),
            ),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.RemoveCounters(
                CounterKind.PowerToughness(1, 1),
                Value.Fixed(3),
                from = CostObjects.Self,
            ))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Hand))))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        // CR 603.10a: a leaves-the-battlefield trigger looks back in time
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "cards",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land)) + inYourGraveyard)),
                    min = Value.Fixed(0),
                    max = Value.Fixed(3),
                )),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("cards"), to = Destination(Zone.Hand)))),
            ),
        ),
    )

    /** CR 118.12a: "sacrifice it unless you sacrifice a land" is a pay, then the sacrifice if not paid; CR 305.2: an additional land play. */
    @Test
    fun theBogmawHorror() = card(
        name = "The Bogmaw Horror",
        cost = "{3}{B}{G}",
        type = "Legendary Creature — Frog Horror",
        pt = "6/6",
        text = """
            Deathtouch
            At the beginning of your upkeep, sacrifice The Bogmaw Horror unless you sacrifice a land.
            You may play an additional land on each of your turns.
            Whenever one or more land cards are put into your graveyard from anywhere, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(
                            ObjectFilter.Type(CardType.Land),
                            count = Value.Fixed(1),
                        )))),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(Condition.Did("paid")),
                        then = listOf(Step.Do(Effect.Sacrifice(Objects.Self))),
                    ),
                ),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.ExtraLandPlays(1)),
            affects = Affected.Players(Players.You),
        ),
        Ability.Triggered(
            trigger = Trigger.On(landCardToYourGraveyard, oneOrMore = true),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
        ),
    )

    // ---- Another creature you control

    @Test
    fun cinderGatewarden() = card(
        name = "Cinder Gatewarden",
        cost = "{2}{R}",
        type = "Artifact Creature — Golem",
        pt = "2/3",
        text = """
            Whenever another creature you control enters, this creature deals 1 damage to each opponent.
            Unearth {R} ({R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.And(listOf(another, creature, ObjectFilter.ControlledBy(PlayerFilter.You))),
                to = Zone.Battlefield,
            )),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.DealDamage(
                    Recipients(players = Players.Each(PlayerFilter.Opponent)),
                    amount = Value.Fixed(1),
                    source = Objects.Self,
                ))),
            ),
        ),
        Keyword.Unearth(manaCost("{R}")),
    )
}
