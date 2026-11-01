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
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 45: creatures that come back from the graveyard or are cast from it, from the top of the library, and creatures
 * that can't block. Permissions to cast a card from a zone are [Mechanic.MayPlay] (on the card itself it works wherever
 * the card could be cast from, CR 113.6f); "as long as [condition]" is the static ability's condition, and "as long as
 * this card is in your graveyard" also states the zone it works in (CR 113.6b). Returning "this card from your
 * graveyard" works only in the graveyard without saying so (CR 113.6m). A suspended card is in exile with suspend and a
 * time counter (CR 702.62b). Bestow and companion keywords with costs and conditions that aren't only mana
 * (CR 702.103a, 702.139a).
 */
class Chunk45GraveyardCastingAndCantBlockTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val land = ObjectFilter.Type(CardType.Land)
    private fun controlledByYou(filter: ObjectFilter) = ObjectFilter.And(listOf(filter, ObjectFilter.ControlledBy(PlayerFilter.You)))
    /** Cards of a kind in your library or graveyard. */
    private fun yourCards(filter: ObjectFilter, zone: Zone) =
        ObjectFilter.And(listOf(filter, ObjectFilter.InZone(zone), ObjectFilter.OwnedBy(PlayerFilter.You)))

    /** "This creature can't block." */
    private val cantBlock = Ability.Static(listOf(Mechanic.CantBlock()))

    /** "Return this card from your graveyard to the battlefield." (CR 113.6m: works only in the graveyard) */
    private val returnThisCard = Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield), postBind = "returned"))

    /** "permanent card" (CR 110.4a) */
    private val permanentCard = ObjectFilter.Or(listOf(
        ObjectFilter.Type(CardType.Artifact),
        ObjectFilter.Type(CardType.Battle),
        ObjectFilter.Type(CardType.Creature),
        ObjectFilter.Type(CardType.Enchantment),
        ObjectFilter.Type(CardType.Land),
        ObjectFilter.Type(CardType.Planeswalker),
    ))

    // ---- Can't block

    @Test
    fun corpseGnawer() = card(
        name = "Corpse Gnawer",
        cost = "{B}",
        type = "Creature — Zombie",
        pt = "1/1",
        text = """
            This creature can't block.
            Sacrifice a creature: Put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        cantBlock,
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature)))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), amount = Value.Fixed(1)))),
            ),
        ),
    )

    /** Changeling is every creature type (CR 702.73a). */
    @Test
    fun shapelessExile() = card(
        name = "Shapeless Exile",
        cost = "{B}",
        type = "Creature — Shapeshifter",
        pt = "1/1",
        text = """
            Changeling (This card is every creature type.)
            This creature can't block and can't be blocked.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        Ability.Static(listOf(Mechanic.CantBlock(), Mechanic.CantBeBlocked)),
    )

    // ---- Back from the graveyard

    @Test
    fun gorehaunt() = card(
        name = "Gorehaunt",
        cost = "{B}{B}",
        type = "Creature — Vampire Spirit",
        pt = "2/1",
        text = """
            This creature can't block.
            This creature has haste as long as an opponent has 10 or less life.
            Landfall — Whenever a land you control enters, you may return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        cantBlock,
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
            condition = Condition.PlayerExists(PlayerFilter.And(listOf(
                PlayerFilter.Opponent,
                PlayerFilter.Life(Comparison.AtMost, Value.Fixed(10)),
            ))),
        ),
        Ability.Triggered(
            abilityWord = "Landfall",
            trigger = Trigger.On(EventPattern.ZoneChange(controlledByYou(land), to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(Step.May(listOf(returnThisCard))),
            ),
        ),
    )

    @Test
    fun bloodswornDuelist() = card(
        name = "Bloodsworn Duelist",
        cost = "{B}",
        type = "Creature — Human Warrior",
        pt = "2/1",
        text = """
            This creature can't block.
            Raid — {1}{B}: Return this card from your graveyard to the battlefield. Activate only if you attacked this turn.
        """,
    ) parsesTo abilities(
        cantBlock,
        Ability.Activated(
            abilityWord = "Raid",
            cost = manaCost("{1}{B}"),
            instructions = Instructions(steps = listOf(returnThisCard)),
            rules = ActivationRules(
                condition = Condition.HappenedThisTurn(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You))),
            ),
        ),
    )

    /** The intervening "if" counts creatures, not events: one board wipe killing two creatures is enough (CR 603.4). */
    @Test
    fun duskflamePhoenix() = card(
        name = "Duskflame Phoenix",
        cost = "{3}{B}",
        type = "Creature — Phoenix",
        pt = "3/2",
        text = """
            Flying, haste
            At the beginning of combat on your turn, if two or more creatures died this turn, return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You)),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 2,
                measure = Measure.Objects,
            ),
            instructions = Instructions(steps = listOf(returnThisCard)),
        ),
    )

    /** CR 113.6b: "as long as this card is in your graveyard" states where the ability works. */
    @Test
    fun smolderingSpite() = card(
        name = "Smoldering Spite",
        cost = "{3}{R}",
        type = "Creature — Incarnation",
        pt = "2/2",
        text = """
            Haste
            As long as this card is in your graveyard and you control a Mountain, creatures you control have haste.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
            affects = Affected.Objects(Objects.Each(controlledByYou(creature))),
            condition = Condition.And(listOf(
                Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
                Condition.Exists(controlledByYou(ObjectFilter.Sub(Subtype("Mountain")))),
            )),
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    // ---- Cast from the graveyard

    @Test
    fun tombscuttler() = card(
        name = "Tombscuttler",
        cost = "{B}",
        type = "Creature — Zombie",
        pt = "2/1",
        text = """
            This creature can't block.
            You may cast this card from your graveyard as long as you control a Zombie.
        """,
    ) parsesTo abilities(
        cantBlock,
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard)),
            condition = Condition.Exists(controlledByYou(ObjectFilter.Sub(Subtype("Zombie")))),
        ),
    )

    @Test
    fun grommakRisenCharnelhost() = card(
        name = "Grommak, Risen Charnelhost",
        cost = "{5}{B/G}{B/G}",
        type = "Legendary Creature — Avatar",
        pt = "8/8",
        text = """
            You can't spend mana to cast this spell.
            Convoke, delve (Each creature you tap while casting this spell pays for {1} or one mana of that creature's color. Each card you exile from your graveyard pays for {1}.)
            You may cast this card from your graveyard.
            Trample
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantSpendMana)),
        Keyword.Convoke,
        Keyword.Delve,
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard))),
        Keyword.Trample,
    )

    /** Casting it bestowed pays the bestow cost instead of its mana cost (CR 702.103a); the permission from the
     * graveyard is only for that alternative cost. Collect evidence as a cost (CR 701.59a). */
    @Test
    fun inspectorsFirebird() = card(
        name = "Inspector's Firebird",
        cost = "{2}{R}",
        type = "Enchantment Creature — Phoenix",
        pt = "2/2",
        text = """
            Bestow—{R}, Collect evidence 6. (To pay this bestow cost, pay {R} and exile cards with total mana value 6 or greater from your graveyard.)
            Flying, haste
            Enchanted creature gets +2/+2 and has flying and haste.
            You may cast this card from your graveyard using its bestow ability.
        """,
    ) parsesTo abilities(
        Keyword.Bestow(Cost(listOf(
            CostPart.Mana(ManaCost.parse("{R}")!!),
            CostPart.Action(KeywordAction.CollectEvidence(Value.Fixed(6))),
        ))),
        Keyword.Flying,
        Keyword.Haste,
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)),
                Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.Haste)),
            ),
            affects = Affected.Objects(Objects.AttachedTo()),
        ),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, viaAlternative = Keyword.Bestow.Kind))),
    )

    /** Companion (CR 702.139a): every card of the starting deck is a nonpermanent card or has mana value 2 or less.
     * "Once during each of your turns" is a limit of one card a turn, on your turns only. */
    @Test
    fun velmoraOfTheSleepDen() = card(
        name = "Velmora of the Sleep-Den",
        cost = "{1}{W/B}{W/B}",
        type = "Legendary Creature — Cat Nightmare",
        pt = "3/2",
        text = """
            Companion — Each permanent card in your starting deck has mana value 2 or less. (If this card is your chosen companion, you may put it into your hand from outside the game for {3} as a sorcery.)
            Lifelink
            Once during each of your turns, you may cast a permanent spell with mana value 2 or less from your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.CompanionAbility(
            requirement = "Each permanent card in your starting deck has mana value 2 or less.",
            eachCard = ObjectFilter.Or(listOf(
                ObjectFilter.Not(permanentCard),
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(2)),
            )),
        ),
        Keyword.Lifelink,
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, perTurn = 1)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                permanentCard,
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(2)),
                ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You),
            )))),
            condition = Condition.YourTurn,
        ),
    )

    // ---- Exile and library

    /** "Activate only if this card is suspended" states the zone it works in: exile (CR 113.6b, 702.62b). */
    @Test
    fun colossalRumblebeast() = card(
        name = "Colossal Rumblebeast",
        cost = "{9}{R}",
        type = "Creature — Beast",
        pt = "9/7",
        text = """
            Suspend 10—{R}
            Sacrifice an artifact, creature, or land: Remove a time counter from this card. Activate only if this card is suspended.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(10, manaCost("{R}")),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Or(listOf(
                ObjectFilter.Type(CardType.Artifact),
                creature,
                land,
            )))))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.RemoveCounters(Objects.Self, CounterKind.Time, amount = Value.Fixed(1)))),
            ),
            rules = ActivationRules(
                condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Exile),
                    ObjectFilter.HasKeyword(Keyword.Suspend.Kind),
                    ObjectFilter.HasCounter(CounterKind.Time),
                ))),
            ),
            functionsIn = listOf(Zone.Exile),
        ),
    )

    /** Coven: three or more different powers among creatures you control. */
    @Test
    fun seerOfHarvestide() = card(
        name = "Seer of Harvestide",
        cost = "{1}{G}{G}",
        type = "Creature — Human Druid",
        pt = "2/3",
        text = """
            You may look at the top card of your library any time.
            You may play lands from the top of your library.
            Coven — As long as you control three or more creatures with different powers, you may cast creature spells from the top of your library.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, Value.Fixed(1)))),
            affects = Affected.Players(Players.You),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true)),
            affects = Affected.Objects(Objects.Each(yourCards(land, Zone.Library))),
        ),
        Ability.Static(
            abilityWord = "Coven",
            mechanics = listOf(Mechanic.MayPlay(Zone.Library, topOnly = true)),
            affects = Affected.Objects(Objects.Each(yourCards(creature, Zone.Library))),
            condition = Condition.Compare(
                left = Value.CountDistinct(Objects.Each(controlledByYou(creature)), SharedQuality.Number(NumericProperty.Power)),
                comparison = Comparison.AtLeast,
                right = Value.Fixed(3),
            ),
        ),
    )
}
