package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CostChange
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 09: white cards. A cost reduction by the ability's own target (CR 601.2f: targets are chosen before the
 * cost is determined); a creature that dealt damage this turn; a named legendary token for an opponent with "attacks each combat if
 * able" (CR 111.4, 508.1d); exile until it leaves (CR 610.3); an Aura that adds a supertype and a subtype (layer 4) and attaches any
 * number of Equipment as it attacks or blocks (CR 701.3); modes with "each of up to two target creatures" (CR 700.2).
 */
class MshChunk09WhiteTest {
    // a flavor word (CR 207.2d)
    @Test
    fun tremorAgentOfTheBureau() = card(
        name = "Tremor, Agent of the Bureau",
        cost = "{2}{W}",
        type = "Legendary Creature — Inhuman Spy Hero",
        text = "Seismic Takedown — Whenever you cast a noncreature spell, tap target creature or land.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), tapIt(target("permanent")), targets = listOf(slot("permanent", or(creature, land))), abilityWord = "Seismic Takedown"),
    )

    // CR 601.2f: {1} less for each target among its targets with power 3 or less
    @Test
    fun vaultSecurityOfficer() = card(
        name = "Vault Security Officer",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        text = "{2}, {T}: Tap target creature. This ability costs {1} less to activate if it targets a creature with power 3 or less.",
        pt = "1/3",
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{2}"), tap), Instructions(targets = listOf(slot("creature", creature)), steps = listOf(tapIt(target("creature")))),
            anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(Objects.Where(target("creature"),
                and(creature, power(arcanum.api.Comparison.AtMost, 3))))))),
    )

    // a creature that dealt damage this turn (the source of a damage event)
    @Test
    fun crimsonSentinelSuperSoldier() = card(
        name = "Crimson Sentinel, Super-Soldier",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Soldier Villain",
        text = """
            Flash
            When Crimson Sentinel enters, destroy target creature an opponent controls that dealt damage this turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature", and(creature, opponents,
            ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Source))))),
    )

    // CR 111.4: a named legendary token created under the target opponent's control
    @Test
    fun theAurumGuardian() = card(
        name = "The Aurum, Golden Guardian",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Hero",
        text = """
            Flying, vigilance, indestructible
            When The Aurum enters, target opponent creates The Abyss, a legendary 5/5 black Horror Villain creature token with flying, indestructible, and "The Abyss attacks each combat if able."
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Indestructible,
        triggered(entersSelf, Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("The Abyss", colorIndicator = listOf(Color.Black),
            supertypes = listOf(Supertype.Legendary), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Horror"), Subtype("Villain")),
            power = PrintedNumber.Fixed(5), toughness = PrintedNumber.Fixed(5),
            abilities = listOf(Keyword.Flying, Keyword.Indestructible, static(Mechanic.MustAttack())))), n(1), Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    // CR 610.3: back as soon as this enchantment leaves
    @Test
    fun superVillainHoldingCell() = card(
        name = "Super Villain Holding Cell",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, exile target tapped creature an opponent controls until this enchantment leaves the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.Do(Effect.Move(target("creature"), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", and(tapped, creature, opponents)))),
    )

    // layers 4, 6, 7c; CR 701.3: any number of target Equipment attached to it
    @Test
    fun superSoldierTonic() = card(
        name = "Super-Soldier Tonic",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2, has first strike and vigilance, and is a legendary Soldier in addition to its other types.
            Whenever enchanted creature attacks or blocks, attach any number of target Equipment you control to it.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        static(pt(2, 2), has(Keyword.FirstStrike, Keyword.Vigilance),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Soldier"))), addSupertypes = listOf(Supertype.Legendary)),
            affects = Objects.AttachedTo(Objects.Self)),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))),
                EventPattern.Block(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), attacker = ObjectFilter.Any))),
            Step.Do(Effect.Attach(target("Equipment"), Recipients(objects = Objects.EventObject(EventRole.Object)))),
            targets = listOf(slot("Equipment", and(equipment, you), min = 0, max = null))),
    )

    @Test
    fun raiseTheShield() = card(
        name = "Raise the Shield",
        cost = "{1}{W}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature. It gains lifelink and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(counters(target("creature")), gains(target("creature"), Keyword.Lifelink, Keyword.Indestructible), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun royalDroneFlock() = card(
        name = "Royal Drone Flock",
        cost = "{3}{W}",
        type = "Artifact Creature — Robot",
        text = """
            Flying
            When this creature enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, scry(2)),
    )

    @Test
    fun webbedIn() = card(
        name = "Webbed In",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
    )

    // CR 700.2: modes of an enters trigger; "each of up to two target creatures"
    @Test
    fun paleWidowFreeAgent() = card(
        name = "Pale Widow, Free Agent",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Hero Villain",
        text = """
            When Pale Widow enters, choose one —
            • Put a +1/+1 counter on each of up to two target creatures.
            • Return target artifact or enchantment card from your graveyard to your hand.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(artifact, enchantment)))))))),
    )
}
