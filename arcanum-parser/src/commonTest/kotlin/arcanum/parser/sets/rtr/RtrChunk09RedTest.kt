package arcanum.parser.sets.rtr

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.anyTarget
import arcanum.parser.sets.rtr.Rtr.artifact
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.create
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.creatureToken
import arcanum.parser.sets.rtr.Rtr.damage
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.enchant
import arcanum.parser.sets.rtr.Rtr.enchanted
import arcanum.parser.sets.rtr.Rtr.gains
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.sub
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import arcanum.parser.sets.rtr.Rtr.youCast
import arcanum.parser.sets.rtr.Rtr.yourUpkeep
import kotlin.test.Test

/**
 * Return to Ravnica chunk 09: the red cards. Damage with "if a creature dealt damage this way would die this turn, exile it
 * instead" (a replacement for the turn, CR 614.1a), casting from a graveyard as a trigger, a target opponent and you each
 * revealing three cards and the creatures put onto the battlefield fighting (CR 701.14), multicolored spells as triggers,
 * granted activated abilities of an Aura on a land, threaten with untapping and a pump (CR 611.2c), Dragons attacking.
 */
class RtrChunk09RedTest {
    /** The replacement applies to the creature dealt damage, if it is one, for the rest of the turn. */
    @Test
    fun obliteratingBlaze() = card(
        name = "Obliterating Blaze",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = "Obliterating Blaze deals 3 damage to any target. If a creature dealt damage this way would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(
            damage(Recipients(target = "any"), 3),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(all(ObjectFilter.Is(target("any")), creature), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(anyTarget),
        ),
    )

    /** "That player": the player who cast the spell from a graveyard (flashback, escape, …). */
    @Test
    fun cinderZealot() = card(
        name = "Cinder Zealot",
        cost = "{R}{R}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = """
            First strike, haste
            Whenever a player casts a spell from a graveyard, this creature deals 3 damage to that player.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Haste,
        triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any, from = Zone.Graveyard)), damage(Recipients(players = Players.EventPlayer), 3)),
    )

    @Test
    fun rambasher() = card(
        name = "Rambasher",
        cost = "{4}{R}",
        type = "Creature — Beast",
        pt = "3/3",
        text = "When this creature enters, you may destroy target artifact.",
    ) parsesTo abilities(
        triggered(thisEnters, Step.May(listOf(Rtr.destroy(target("artifact"))), player = Players.You), targets = listOf(slot("artifact", artifact))),
    )

    @Test
    fun forgebreathLizard() = card(
        name = "Forgebreath Lizard",
        cost = "{R}",
        type = "Creature — Lizard",
        pt = "1/1",
        text = "{1}{R}: This creature gets +1/+0 until end of turn.",
    ) parsesTo abilities(
        activated(cost(manaPart("{1}{R}")), pump(Objects.Self, 1, 0)),
    )

    @Test
    fun shatteringImpact() = card(
        name = "Shattering Impact",
        cost = "{5}{R}",
        type = "Instant",
        text = "Shattering Impact deals 5 damage to any target.",
    ) parsesTo abilities(
        spell(damage(Recipients(target = "any"), 5), targets = listOf(anyTarget)),
    )

    @Test
    fun goblinMuster() = card(
        name = "Goblin Muster",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = "Create four 1/1 red Goblin creature tokens.",
    ) parsesTo abilities(
        spell(create(creatureToken("Goblin", listOf(Color.Red), 1, 1), count = 4)),
    )

    /**
     * Each player reveals their top three cards and may put a creature card among them onto the battlefield under their
     * control, the rest into their graveyard; two creatures put this way fight (CR 701.14).
     */
    @Test
    fun factionStrife() = card(
        name = "Faction Strife",
        cost = "{5}{R}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, target opponent reveals the top three cards of their library, may put a creature card from among them onto the battlefield, then puts the rest into their graveyard. You do the same with the top three cards of your library. If two creatures are put onto the battlefield this way, those creatures fight each other.",
    ) parsesTo abilities(
        triggered(yourUpkeep,
            *(revealPutRest(Players.Target("opponent"), "theirs") + revealPutRest(Players.You, "yours")).toTypedArray(),
            Step.If(Condition.And(listOf(Condition.NonEmpty("theirs put"), Condition.NonEmpty("yours put"))),
                then = listOf(Step.Do(Effect.Fight(Objects.Bound("theirs put"), Objects.Bound("yours put"))))),
            targets = listOf(Rtr.targetOpponent)),
    )

    private fun revealPutRest(player: Players, bind: String) = listOf(
        Step.Do(Effect.Reveal(Objects.LibraryCards(player, n(3), fromTop = true), bind = "$bind revealed")),
        Step.Choose(Choice.Objects(all(creature, Rtr.card), n(0), n(1), from = Objects.Bound("$bind revealed")), bind = bind, chooser = player),
        Step.Do(Effect.Move(Objects.Bound(bind), Destination(Zone.Battlefield, controller = player), postBind = "$bind put")),
        Rtr.move(Objects.Except(Objects.Bound("$bind revealed"), Objects.Bound(bind)), Zone.Graveyard),
    )

    @Test
    fun gutterspark() = card(
        name = "Gutterspark",
        cost = "{2}{R}",
        type = "Creature — Goblin Shaman",
        pt = "2/2",
        text = "Whenever you cast an instant or sorcery spell, this creature deals 2 damage to each opponent.",
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Or(listOf(Rtr.instant, Rtr.sorcery))), damage(Recipients(players = Players.Each(PlayerFilter.Opponent)), 2)),
    )

    /** CR 105.2c: a multicolored spell has two or more colours. */
    @Test
    fun catapultCrew() = card(
        name = "Catapult Crew",
        cost = "{2}{R}",
        type = "Creature — Goblin Warrior",
        pt = "0/4",
        text = """
            Defender
            {T}: This creature deals 1 damage to each opponent.
            Whenever you cast a multicolored spell, untap this creature.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(CostPart.TapSelf), damage(Recipients(players = Players.Each(PlayerFilter.Opponent)), 1)),
        triggered(youCast(ObjectFilter.Multicolored), Step.Do(Effect.Untap(Objects.Self))),
    )

    @Test
    fun questForWings() = card(
        name = "Quest for Wings",
        cost = "{1}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has "{U}: This creature gains flying until end of turn."
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.ModifyPowerToughness(n(2), n(2)), Mechanic.GainAbilities(listOf(
            activated(cost(manaPart("{U}")), gains(Objects.Self, Keyword.Flying))))),
    )

    @Test
    fun blazeconvergence() = card(
        name = "Blazeconvergence",
        cost = "{4}{R}",
        type = "Enchantment",
        text = "Whenever you cast a multicolored spell, this enchantment deals 2 damage to any target.",
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Multicolored), damage(Recipients(target = "any"), 2), targets = listOf(anyTarget)),
    )

    /** A land with a targeted ability: the land's controller chooses the target (CR 113.10). */
    @Test
    fun speedwayFrenzy() = card(
        name = "Speedway Frenzy",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "{T}: Target creature gains haste until end of turn."
        """,
    ) parsesTo abilities(
        enchant(land),
        static(enchanted, Mechanic.GainAbilities(listOf(activated(cost(CostPart.TapSelf), gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", creature)))))),
    )

    @Test
    fun siftTheRubble() = card(
        name = "Sift the Rubble",
        cost = "{4}{R}",
        type = "Sorcery",
        text = "Destroy target land. Create a 1/1 red Goblin creature token.",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("land")), create(creatureToken("Goblin", listOf(Color.Red), 1, 1)), targets = listOf(slot("land", land))),
    )

    /** Control until end of turn (layer 2), untapped, and "Until end of turn, it gets …" in front (CR 611.2c). */
    @Test
    fun turncoatUrge() = card(
        name = "Turncoat Urge",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap that creature. Until end of turn, it gets +2/+0 and gains haste.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), arcanum.api.Affected.Objects(target("creature")), Duration.EndOfTurn)),
            Step.Do(Effect.Untap(target("creature"))),
            pump(target("creature"), 2, 0, Mechanic.GainAbilities(listOf(Keyword.Haste))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    /** One trigger per attacking Dragon you control. */
    @Test
    fun utmostHellkite() = card(
        name = "Utmost Hellkite",
        cost = "{6}{R}{R}",
        type = "Creature — Dragon",
        pt = "6/6",
        text = """
            Flying
            Whenever a Dragon you control attacks, create a 6/6 red Dragon creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Attack(all(sub("Dragon"), you))), create(creatureToken("Dragon", listOf(Color.Red), 6, 6, Keyword.Flying))),
    )

    @Test
    fun viashinoHustler() = card(
        name = "Viashino Hustler",
        cost = "{2}{R}",
        type = "Creature — Lizard Rogue",
        pt = "2/1",
        text = "When this creature enters, you may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        triggered(thisEnters,
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(Rtr.card, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )
}
