package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 51: creatures with activated abilities paid by sacrificing, discarding or returning permanents. "Activate only
 * once each turn", "Any player may activate this ability" (CR 602.2), "another creature or artifact", "three Foods",
 * protection from a colour (CR 702.16a) and a threshold bonus that holds "as long as" its condition does.
 */
class Chunk51SacrificeAndReturnOutletsTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val discardACard = CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card)))
    private fun sacrificeA(filter: ObjectFilter, count: Int = 1) = CostPart.Sacrifice(CostObjects.Choose(filter, count = Value.Fixed(count)))

    /** "Return a [land type or creature type] you control to its owner's hand: Untap target creature. Activate only once
     * each turn." */
    private fun returnToUntap(subtype: String) = Ability.Activated(
        cost = Cost(listOf(CostPart.ReturnToHand(CostObjects.Choose(
            ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype(subtype)), ObjectFilter.ControlledBy(PlayerFilter.You))),
        )))),
        instructions = Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(creature))),
            steps = listOf(Step.Do(Effect.Untap(Objects.Target("creature")))),
        ),
        rules = ActivationRules(perTurn = 1),
    )

    /** "[This creature] gains [keyword] until end of turn." */
    private fun selfGainsUntilEndOfTurn(keyword: Keyword) = Step.Do(Effect.ApplyMechanics(
        mechanics = listOf(Mechanic.GainAbilities(listOf(keyword))),
        affects = Affected.Objects(Objects.Self),
        duration = Duration.EndOfTurn,
    ))

    // ---- Return a permanent: untap

    @Test
    fun thicketRanger() = card(
        name = "Thicket Ranger",
        cost = "{G}",
        type = "Creature — Elf Ranger",
        pt = "1/1",
        text = "Return a Forest you control to its owner's hand: Untap target creature. Activate only once each turn.",
    ) parsesTo abilities(
        returnToUntap("Forest"),
    )

    /** CR 702.16a: protection from blue. */
    @Test
    fun gladeSpriteRanger() = card(
        name = "Glade Sprite Ranger",
        cost = "{1}{G}",
        type = "Creature — Faerie Ranger",
        pt = "1/1",
        text = """
            Flash
            Flying, protection from blue
            Return a Forest you control to its owner's hand: Untap target creature. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Keyword.Protection(ObjectFilter.OfColor(Color.Blue)),
        returnToUntap("Forest"),
    )

    @Test
    fun barkweaveSymbiote() = card(
        name = "Barkweave Symbiote",
        cost = "{G}",
        type = "Creature — Insect",
        pt = "1/1",
        text = "Return an Elf you control to its owner's hand: Untap target creature. Activate only once each turn.",
    ) parsesTo abilities(
        returnToUntap("Elf"),
    )

    // ---- Discard a card

    /** CR 602.2: any player may activate it, paying the cost with their own card. */
    @Test
    fun gloomwingSkulker() = card(
        name = "Gloomwing Skulker",
        cost = "{1}{B}",
        type = "Creature — Faerie Rogue",
        pt = "3/1",
        text = """
            Flying
            Discard a card: This creature gets -2/-0 until end of turn. Any player may activate this ability.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Activated(
            cost = Cost(listOf(discardACard)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-2), Value.Fixed(0))),
                affects = Affected.Objects(Objects.Self),
                duration = Duration.EndOfTurn,
            )))),
            rules = ActivationRules(activatedBy = PlayerFilter.Any),
        ),
    )

    @Test
    fun rancidImp() = card(
        name = "Rancid Imp",
        cost = "{B}",
        type = "Creature — Zombie Imp",
        pt = "1/1",
        text = """
            Discard a card: This creature gains flying until end of turn.
            Threshold — As long as there are seven or more cards in your graveyard, this creature gets +1/+1 and can't block.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(discardACard)),
            instructions = Instructions(steps = listOf(selfGainsUntilEndOfTurn(Keyword.Flying))),
        ),
        Ability.Static(
            abilityWord = "Threshold",
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1)), Mechanic.CantBlock()),
            condition = Condition.Exists(
                filter = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                atLeast = 7,
            ),
        ),
    )

    // ---- Sacrifice a permanent

    @Test
    fun entrailAugur() = card(
        name = "Entrail Augur",
        cost = "{B}",
        type = "Creature — Vampire Wizard",
        pt = "1/1",
        text = "Sacrifice a creature: Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(sacrificeA(creature))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Scry(Players.You, Value.Fixed(1))))),
        ),
    )

    @Test
    fun shadowcollarDevotee() = card(
        name = "Shadowcollar Devotee",
        cost = "{1}{B}",
        type = "Creature — Human Cleric",
        pt = "3/2",
        text = "Sacrifice another creature or artifact: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(sacrificeA(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Artifact))),
            ))))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Surveil(Players.You, Value.Fixed(1))))),
        ),
    )

    @Test
    fun mossboundKeeper() = card(
        name = "Mossbound Keeper",
        cost = "{G}",
        type = "Creature — Human Wizard",
        pt = "1/1",
        text = "Sacrifice a land: Target creature you control gains shroud until end of turn. (It can't be the target of spells or abilities.)",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(sacrificeA(ObjectFilter.Type(CardType.Land)))),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "creature",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You)))),
                )),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Shroud))),
                    affects = Affected.Objects(Objects.Target("creature")),
                    duration = Duration.EndOfTurn,
                ))),
            ),
        ),
    )

    /** CR 700.6: artifacts, legendaries and Sagas are historic; CR 111.10b: Food. */
    @Test
    fun podrickBurrowfoot() = card(
        name = "Podrick Burrowfoot",
        cost = "{G}{W}",
        type = "Legendary Creature — Halfling Peasant",
        pt = "2/2",
        text = """
            Whenever another nontoken creature you control enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Sacrifice three Foods: Return target historic card from your graveyard to your hand. (Artifacts, legendaries, and Sagas are historic.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                    creature,
                    ObjectFilter.ControlledBy(PlayerFilter.You),
                )),
                to = Zone.Battlefield,
            )),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Food"))))),
        ),
        Ability.Activated(
            cost = Cost(listOf(sacrificeA(ObjectFilter.Sub(Subtype("Food")), count = 3))),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "card",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(
                            ObjectFilter.Type(CardType.Artifact),
                            ObjectFilter.Super(Supertype.Legendary),
                            ObjectFilter.Sub(Subtype("Saga")),
                        )),
                        ObjectFilter.InZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                    ))),
                )),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
            ),
        ),
    )
}
