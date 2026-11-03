package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 02: tiered spells (CR 702.183a: choose one mode and pay its cost as an additional cost), their mode names flavor
 * words (CR 207.2d); a tiered paragraph each mode completes with a base power and toughness; modal spells and abilities whose modes have
 * flavor words or other shapes (CR 700.2): stun counters on one of the tapped targets, lore counters on Sagas, a fight.
 */
class FinChunk02TieredAndModesTest {
    private val hexproofIndestructible = Mechanic.GainAbilities(listOf(Keyword.Hexproof, Keyword.Indestructible))
    private fun creatureSlot() = listOf(slot("creature", creature))

    // CR 702.183a
    @Test
    fun mendingLitany() = card(
        name = "Mending Litany",
        cost = "{W}",
        type = "Instant",
        text = """
            Tiered (Choose one additional cost.)
            • Cure — {0} — Target permanent gains hexproof and indestructible until end of turn.
            • Cura — {1} — Target permanent gains hexproof and indestructible until end of turn. You gain 3 life.
            • Curaga — {3}{W} — Permanents you control gain hexproof and indestructible until end of turn. You gain 6 life.
        """,
    ) parsesTo abilities(
        Keyword.Tiered,
        tiered(
            tier("{0}", untilEndOfTurn(target("permanent"), hexproofIndestructible), targets = listOf(slot("permanent", permanentFilter))),
            tier("{1}", untilEndOfTurn(target("permanent"), hexproofIndestructible), gainLife(3), targets = listOf(slot("permanent", permanentFilter))),
            tier("{3}{W}", untilEndOfTurn(Objects.Each(and(permanentFilter, you)), hexproofIndestructible), gainLife(6)),
        ),
    )

    // CR 702.183a, 401.4 (top or bottom), 701.24 (shuffle into a library)
    @Test
    fun frostLitany() = card(
        name = "Frost Litany",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Tiered (Choose one additional cost.)
            • Blizzard — {0} — Return target creature to its owner's hand.
            • Blizzara — {2} — Target creature's owner puts it on their choice of the top or bottom of their library.
            • Blizzaga — {5}{U} — Target creature's owner shuffles it into their library.
        """,
    ) parsesTo abilities(
        Keyword.Tiered,
        tiered(
            tier("{0}", move(target("creature"), Zone.Hand), targets = creatureSlot()),
            tier("{2}",
                Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("creature"))),
                ifThen(Condition.Chose("position", "top"), move(target("creature"), Zone.Library, position = 0),
                    otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
                targets = creatureSlot()),
            tier("{5}{U}", move(target("creature"), Zone.Library), Step.Do(Effect.Shuffle(Players.OwnerOf(target("creature")))), targets = creatureSlot()),
        ),
    )

    @Test
    fun emberLitany() = card(
        name = "Ember Litany",
        cost = "{R}",
        type = "Instant",
        text = """
            Tiered (Choose one additional cost.)
            • Fire — {0} — Ember Litany deals 1 damage to each creature.
            • Fira — {2} — Ember Litany deals 2 damage to each creature.
            • Firaga — {5} — Ember Litany deals 3 damage to each creature.
        """,
    ) parsesTo abilities(
        Keyword.Tiered,
        tiered(
            tier("{0}", damage(1, toObjects(Objects.Each(creature)))),
            tier("{2}", damage(2, toObjects(Objects.Each(creature)))),
            tier("{5}", damage(3, toObjects(Objects.Each(creature)))),
        ),
    )

    @Test
    fun stormLitany() = card(
        name = "Storm Litany",
        cost = "{R}",
        type = "Instant",
        text = """
            Tiered (Choose one additional cost.)
            • Thunder — {0} — Storm Litany deals 2 damage to target creature.
            • Thundara — {3} — Storm Litany deals 4 damage to target creature.
            • Thundaga — {5}{R} — Storm Litany deals 8 damage to target creature.
        """,
    ) parsesTo abilities(
        Keyword.Tiered,
        tiered(
            tier("{0}", damage(2, toObjects(target("creature"))), targets = creatureSlot()),
            tier("{3}", damage(4, toObjects(target("creature"))), targets = creatureSlot()),
            tier("{5}{R}", damage(8, toObjects(target("creature"))), targets = creatureSlot()),
        ),
    )

    // CR 701.10e (double power and toughness), 701.11b (triple): +X/+Y from the values as the spell resolves
    @Test
    fun brawlersCrescendo() = card(
        name = "Brawler's Crescendo",
        cost = "{G}",
        type = "Instant",
        text = """
            Tiered (Choose one additional cost.)
            • Somersault — {0} — Target creature gets +2/+2 until end of turn.
            • Meteor Strikes — {2} — Double target creature's power and toughness until end of turn.
            • Final Heaven — {6}{G} — Triple target creature's power and toughness until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Tiered,
        tiered(
            tier("{0}", pump(target("creature"), 2, 2), targets = creatureSlot()),
            tier("{2}", untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), toughness(target("creature")))),
                targets = creatureSlot()),
            tier("{6}{G}", untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(arcanum.api.Value.Times(power(target("creature")), 2),
                arcanum.api.Value.Times(toughness(target("creature")), 2))), targets = creatureSlot()),
        ),
    )

    /** The paragraph's "the chosen base power and toughness" is the chosen mode's (CR 702.183a, 613.4b). */
    @Test
    fun beastformCrescendo() = card(
        name = "Beastform Crescendo",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Tiered (Choose one additional cost.)
            Until end of turn, target creature you control gains "When this creature dies, return it to the battlefield tapped under its owner's control" and has the chosen base power and toughness.
            • Galian Beast — {0} — 3/2.
            • Death Gigas — {1} — 5/2.
            • Hellmasker — {3} — 7/2.
        """,
    ) parsesTo abilities(
        Keyword.Tiered,
        listOf(3 to 2, 5 to 2, 7 to 2).zip(listOf("{0}", "{1}", "{3}")).map { (pt, cost) ->
            tier(cost, untilEndOfTurn(target("creature"),
                Mechanic.GainAbilities(listOf(triggered(diesSelf, move(enteredOne, Zone.Battlefield, tapped = true, controller = Players.OwnerOf(enteredOne))))),
                Mechanic.SetPowerToughness(n(pt.first), n(pt.second))), targets = listOf(slot("creature", creatureYouControl)))
        }.let { tiered(*it.toTypedArray()) },
    )

    @Test
    fun tacticsMenu() = card(
        name = "Tactics Menu",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Attack — Create a 2/2 white Knight creature token.
            • Ability — Target creature gets +0/+4 until end of turn.
            • Magic — Destroy target creature with power 4 or greater.
            • Item — You gain 4 life.
        """,
    ) parsesTo abilities(
        modal(
            mode(create(knightToken())),
            mode(pump(target("creature"), 0, 4), targets = creatureSlot()),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, power(arcanum.api.Comparison.AtLeast, 4))))),
            mode(gainLife(4)),
        ),
    )

    /** "Put a stun counter on one of them": one of the tapped targets, chosen as the spell resolves. */
    @Test
    fun rescueByTheStairs() = card(
        name = "Rescue by the Stairs",
        cost = "{3}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Take the Elevator — Create three 1/1 colorless Hero creature tokens.
            • Take 59 Flights of Stairs — Tap up to three target creatures. Put a stun counter on one of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        modal(
            mode(create(heroToken, n(3))),
            mode(tapIt(target("creatures")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = target("creatures")), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen"), 1, stun),
                targets = listOf(slot("creatures", creature, min = 0, max = 3))),
        ),
    )

    @Test
    fun serenadeOfTheStage() = card(
        name = "Serenade of the Stage",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Exile the top two cards of your library. You may play those cards until your next end step.
            • One or two target creatures each get +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        modal(
            mode(move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = arcanum.api.PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilStep(arcanum.api.TurnStep.End, arcanum.api.PlayerFilter.You)))),
            mode(pump(target("creature"), 2, 0), targets = listOf(slot("creature", creature, min = 1, max = 2))),
        ),
    )

    @Test
    fun taintedSpring() = card(
        name = "Tainted Spring",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • All creatures get -1/-1 until end of turn.
            • Target player reveals their hand. You choose an artifact or creature card from it. That player discards that card.
        """,
    ) parsesTo abilities(
        modal(
            mode(pump(Objects.Each(creature), -1, -1)),
            mode(
                doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.Is(Players.Target("player"))))),
                    bind = "revealed")),
                Step.Choose(Choice.Objects(or(artifact, creature), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.Target("player"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetPlayer)),
        ),
    )

    // CR 614.1a: "If that creature would die this turn, exile it instead."
    @Test
    fun ropeSlam() = card(
        name = "Rope Slam",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Rope Slam deals 3 damage to target creature. If that creature would die this turn, exile it instead.
            • Exile target artifact.
        """,
    ) parsesTo abilities(
        modal(
            mode(damage(3, toObjects(target("creature"))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(arcanum.api.EventPattern.ZoneChange(ObjectFilter.Is(target("creature")),
                    from = Zone.Battlefield, to = Zone.Graveyard), modify = listOf(arcanum.api.Modification.ChangeDestination(arcanum.api.Destination(Zone.Exile))))),
                    null, Duration.EndOfTurn)),
                targets = creatureSlot()),
            mode(move(target("artifact"), Zone.Exile), targets = listOf(slot("artifact", artifact))),
        ),
    )

    @Test
    fun callersHomecoming() = card(
        name = "Caller's Homecoming",
        cost = "{3}{G}{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Creatures you control get +3/+3 until end of turn.
            • Return up to two target permanent cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        modal(
            mode(pump(Objects.Each(creatureYouControl), 3, 3)),
            mode(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(permanentCard), min = 0, max = 2))),
        ),
    )

    // CR 701.14a (fight), 714.3b (lore counters removed don't trigger chapters)
    @Test
    fun clashOfTheTitans() = card(
        name = "Clash of the Titans",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Choose one or more —
            • Target creature you control fights target creature an opponent controls.
            • Remove a lore counter from target Saga you control. (Removing lore counters doesn't cause chapter abilities to trigger.)
            • Put a lore counter on target Saga you control.
        """,
    ) parsesTo abilities(
        modal(
            mode(doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls))),
            mode(doing(Effect.RemoveCounters(target("Saga"), lore, n(1))), targets = listOf(slot("Saga", and(saga, you)))),
            mode(counters(target("Saga"), 1, lore), targets = listOf(slot("Saga", and(saga, you)))),
            max = 3,
        ),
    )

    /** "creature that was dealt damage this turn"; "up to two target cards from a single graveyard" (one owner). */
    @Test
    fun graveGnawer() = card(
        name = "Grave Gnawer",
        cost = "{2}{B}",
        type = "Creature — Zombie Horror",
        pt = "3/2",
        text = """
            When this creature enters, choose one —
            • Destroy target creature that was dealt damage this turn.
            • Exile up to two target cards from a single graveyard.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.ThisTurn(arcanum.api.EventPattern.Damage(
                source = ObjectFilter.Any, to = arcanum.api.RecipientFilter()), arcanum.api.EventRole.Object))))),
            mode(move(target("card"), Zone.Exile), targets = listOf(slot("card", and(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), inZone(Zone.Graveyard)), min = 0, max = 2).copy(sameOwner = true))),
        ))),
    )

    @Test
    fun ironfistedJudge() = card(
        name = "Ironfisted Judge",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/2",
        text = """
            When Ironfisted Judge enters, choose one —
            • Each player sacrifices a creature token of their choice.
            • Each player sacrifices a nontoken creature of their choice.
            • Each player sacrifices an enchantment of their choice.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(eachSacrifices(and(creature, token))),
            mode(eachSacrifices(and(not(token), creature))),
            mode(eachSacrifices(enchantment)),
        ))),
    )

    @Test
    fun arenaColossus() = card(
        name = "Arena Colossus",
        cost = "{5}{G}{G}",
        type = "Creature — Beast",
        pt = "7/7",
        text = """
            Trample
            When this creature enters, choose one —
            • Destroy target artifact or enchantment.
            • Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            mode(draw(1)),
        ))),
    )

    @Test
    fun angelFeather() = card(
        name = "Angel Feather",
        cost = "{W}",
        type = "Artifact",
        text = """
            {1}{W}, {T}, Exile this artifact: Choose one —
            • Return target creature card with mana value 4 or less from your graveyard to the battlefield tapped.
            • Exile target Skeleton, Spirit, or Zombie.
        """,
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{1}{W}"), tap, CostPart.Exile(arcanum.api.CostObjects.Self, Zone.Battlefield)), Instructions(modes = modes(
            mode(move(target("card"), Zone.Battlefield, tapped = true),
                targets = listOf(slot("card", inYourGraveyard(creature, manaValue(arcanum.api.Comparison.AtMost, 4))))),
            mode(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(sub("Skeleton"), sub("Spirit"), sub("Zombie"))))),
        )), anyTime),
    )

    /** "Each player sacrifices a [kind] of their choice" (CR 701.21a): each player chooses one of theirs, then all are sacrificed at once. */
    private fun eachSacrifices(kind: ObjectFilter): Step = Step.ForEach(arcanum.api.Each.Players(Players.Each(arcanum.api.PlayerFilter.Any)), "player", steps = listOf(
        Step.Choose(Choice.Objects(and(kind, ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
            bind = "sacrificed", chooser = Players.Bound("player")),
        doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
    ), together = true)
}
