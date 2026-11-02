package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.another
import arcanum.parser.sets.rtr.Rtr.combatDamageToAPlayer
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.create
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.creatureToken
import arcanum.parser.sets.rtr.Rtr.enchant
import arcanum.parser.sets.rtr.Rtr.enchanted
import arcanum.parser.sets.rtr.Rtr.enchantment
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonlandPermanent
import arcanum.parser.sets.rtr.Rtr.permanent
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.regenerate
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.sub
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisAttacks
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.thisLeaves
import arcanum.parser.sets.rtr.Rtr.token
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import kotlin.test.Test

/**
 * Return to Ravnica chunk 06: the white cards. Exile "until it leaves" of creatures and graveyard cards at once (linked,
 * CR 607.2a), a creature that dealt damage this turn, redirection of damage to you and your permanents (CR 614.9), every
 * graveyard exiled and replaced, "another target attacking creature", granted activated abilities of an Aura, an upkeep
 * tax on the enchanted permanent (CR 118.12a), a Gate condition, a "Until end of turn," pump.
 */
class RtrChunk06WhiteTest {
    private val knight = creatureToken("Knight", listOf(Color.White), 2, 2, Keyword.Vigilance)

    /** Targets on the battlefield or in graveyards, chosen together (CR 115.1); the exiled cards go to their owners' hands. */
    @Test
    fun seraphOfStillness() = card(
        name = "Seraph of Stillness",
        cost = "{4}{W}{W}{W}",
        type = "Creature — Angel",
        pt = "5/6",
        text = """
            Flying
            When this creature enters, you may exile up to three other target creatures from the battlefield and/or creature cards from graveyards.
            When this creature leaves the battlefield, return the exiled cards to their owners' hands.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisEnters,
            Step.May(listOf(
                Step.Do(Effect.Move(target("targets"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ), player = Players.You),
            targets = listOf(slot("targets", ObjectFilter.Or(listOf(
                all(another, creature, ObjectFilter.InZone(Zone.Battlefield)),
                all(creature, ObjectFilter.InZone(Zone.Graveyard)),
            )), min = 0, max = 3))),
        triggered(thisLeaves, Rtr.move(Objects.Remembered("exiled"), Zone.Hand)),
    )

    @Test
    fun gatehouseSentinel() = card(
        name = "Gatehouse Sentinel",
        cost = "{3}{W}",
        type = "Creature — Giant Soldier",
        pt = "2/5",
        text = "This creature has vigilance as long as you control a Gate.",
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.GainAbilities(listOf(Keyword.Vigilance)), condition = Condition.Exists(all(sub("Gate"), you), atLeast = 1)),
    )

    /** "That dealt damage this turn": the source of a damage event this turn, not its recipient. */
    @Test
    fun retributionBolt() = card(
        name = "Retribution Bolt",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target creature that dealt damage this turn.",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("creature")), targets = listOf(slot("creature", all(creature,
            ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Source))))),
    )

    /** "Another target attacking creature": not this one; "Untap that creature." the same target. */
    @Test
    fun marketPackBeast() = card(
        name = "Market Pack-Beast",
        cost = "{4}{W}",
        type = "Creature — Beast",
        pt = "2/5",
        text = "Whenever this creature attacks, another target attacking creature gets +0/+2 until end of turn. Untap that creature.",
    ) parsesTo abilities(
        triggered(thisAttacks, pump(target("creature"), 0, 2), Step.Do(Effect.Untap(target("creature"))),
            targets = listOf(slot("creature", all(another, ObjectFilter.HasStatus(Status.Attacking), creature)))),
    )

    @Test
    fun spiritPlate() = card(
        name = "Spirit Plate",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 for each enchantment you control and has first strike.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(all(enchantment, you))), Value.Count(Objects.Each(all(enchantment, you)))),
            Mechanic.GainAbilities(listOf(Keyword.FirstStrike))),
    )

    @Test
    fun wailingSpecter() = card(
        name = "Wailing Specter",
        cost = "{1}{W}",
        type = "Creature — Spirit",
        pt = "2/1",
        text = "Sacrifice this creature: Destroy target enchantment.",
    ) parsesTo abilities(
        activated(cost(CostPart.Sacrifice(CostObjects.Self)), Rtr.destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
    )

    @Test
    fun chivalricOath() = card(
        name = "Chivalric Oath",
        cost = "{4}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, create a 2/2 white Knight creature token with vigilance. (Attacking doesn't cause it to tap.)
            Enchanted creature gets +2/+2 and has vigilance.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(thisEnters, create(knight)),
        static(enchanted, Mechanic.ModifyPowerToughness(n(2), n(2)), Mechanic.GainAbilities(listOf(Keyword.Vigilance))),
    )

    /** A redirection to this creature (CR 614.9: it must still be a creature on the battlefield). */
    @Test
    fun rampartColossus() = card(
        name = "Rampart Colossus",
        cost = "{4}{W}{W}",
        type = "Creature — Giant Soldier",
        pt = "2/7",
        text = "All damage that would be dealt to you and other permanents you control is dealt to this creature instead.",
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = all(another, permanent, you), players = PlayerFilter.You)),
            modify = listOf(Modification.ChangeRecipient(Recipients(objects = Objects.Self))))),
    )

    @Test
    fun spectralCommander() = card(
        name = "Spectral Commander",
        cost = "{3}{W}",
        type = "Creature — Spirit Soldier",
        pt = "2/3",
        text = "Creature tokens you control get +1/+1.",
    ) parsesTo abilities(
        static(Objects.Each(all(creature, token, you)), Mechanic.ModifyPowerToughness(n(1), n(1))),
    )

    @Test
    fun wardCaptain() = card(
        name = "Ward Captain",
        cost = "{W}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            First strike
            Whenever this creature deals combat damage to a player, create a 1/1 white Soldier creature token.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(combatDamageToAPlayer(), create(creatureToken("Soldier", listOf(Color.White), 1, 1))),
    )

    /** Every graveyard exiled as it enters; afterwards cards and tokens go to exile instead of any graveyard (CR 614.1a). */
    @Test
    fun eternalRepose() = card(
        name = "Eternal Repose",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile all graveyards.
            If a card or token would be put into a graveyard from anywhere, exile it instead.
        """,
    ) parsesTo abilities(
        triggered(thisEnters, Rtr.move(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), Zone.Exile)),
        static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Or(listOf(Rtr.card, token)), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
    )

    /** A granted activated ability (CR 113.10): the enchanted land's controller activates it, "you" is that player. */
    @Test
    fun checkpointBarricade() = card(
        name = "Checkpoint Barricade",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, create a 2/2 white Knight creature token with vigilance.
            Enchanted land has "{T}: Prevent the next 1 damage that would be dealt to you this turn."
        """,
    ) parsesTo abilities(
        enchant(land),
        triggered(thisEnters, create(knight)),
        static(enchanted, Mechanic.GainAbilities(listOf(activated(Cost(listOf(CostPart.TapSelf)),
            Step.Do(Effect.PreventDamage(Recipients(players = Players.You), n(1), from = ObjectFilter.Any, duration = Duration.EndOfTurn)))))),
    )

    @Test
    fun conclaveLookout() = card(
        name = "Conclave Lookout",
        cost = "{2}{W}",
        type = "Creature — Elephant Soldier",
        pt = "2/3",
        text = "{5}{G}: Regenerate this creature.",
    ) parsesTo abilities(
        activated(cost(manaPart("{5}{G}")), regenerate(Objects.Self)),
    )

    @Test
    fun birdcageVendor() = card(
        name = "Birdcage Vendor",
        cost = "{2}{W}",
        type = "Creature — Human",
        pt = "1/2",
        text = "When this creature enters, create a 1/1 white Bird creature token with flying.",
    ) parsesTo abilities(
        triggered(thisEnters, create(creatureToken("Bird", listOf(Color.White), 1, 1, Keyword.Flying))),
    )

    /** CR 118.12a in the enchanted permanent's controller's upkeep: they pay its mana value or sacrifice it. */
    @Test
    fun spiritLevy() = card(
        name = "Spirit Levy",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant nonland permanent
            At the beginning of the upkeep of enchanted permanent's controller, that player sacrifices it unless they pay {X}, where X is its mana value.
        """,
    ) parsesTo abilities(
        enchant(nonlandPermanent),
        triggered(Rtr.step(TurnStep.Upkeep, PlayerFilter.Is(Players.ControllerOf(enchanted))),
            Step.Pay(Cost(listOf(CostPart.Mana(Rtr.mana("{X}"), x = Value.Of(arcanum.api.NumericProperty.ManaValue, enchanted, arcanum.api.Combine.Single)))),
                payer = Players.EventPlayer, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Sacrifice(enchanted))))),
    )

    /** "Until end of turn, …" in front: the same pump and keywords as written after. */
    @Test
    fun summaryJustice() = card(
        name = "Summary Justice",
        cost = "{W}",
        type = "Instant",
        text = "Until end of turn, target creature gets +1/+0 and gains first strike and lifelink.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 0, Mechanic.GainAbilities(listOf(Keyword.FirstStrike, Keyword.Lifelink))),
            targets = listOf(slot("creature", creature))),
    )
}
