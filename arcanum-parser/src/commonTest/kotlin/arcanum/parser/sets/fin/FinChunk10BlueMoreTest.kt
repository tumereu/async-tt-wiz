package arcanum.parser.sets.fin

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 10: more blue cards: mana for artifacts only, an Aura that leaves when its creature is dealt damage, a counter
 * unless {X} is paid that exiles (CR 118.12a), affinity for Towns (CR 702.41a), a cost reduced for each Town, a copy of an artifact,
 * creature or land (CR 707.2), a delayed copy of the next instant or sorcery, tapping all of an opponent's creatures, islandcycling.
 * Dragoon's Wyvern (an enters trigger making a Hero) and Il Mheg Pixie (surveil on attack) repeat tested templates.
 */
class FinChunk10BlueMoreTest {
    // CR 106.6 (mana with a restriction)
    @Test
    fun freightSkiff() = card(
        name = "Freight Skiff",
        cost = "{1}{U}",
        type = "Artifact — Vehicle",
        pt = "2/3",
        text = """
            Flying, vigilance
            {T}: Add {C}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Vigilance,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!),
            restriction = or(and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated, source = artifact))), Players.You))),
        Keyword.Crew(1),
    )

    @Test
    fun drillInstruction() = card(
        name = "Drill Instruction",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Target player draws two cards. Put a +1/+1 counter on up to one target creature you control.",
    ) parsesTo abilities(
        spell(draw(2, Players.Target("player")), counters(target("creature")), targets = listOf(targetPlayer, slot("creature", creatureYouControl, min = 0))),
    )

    @Test
    fun dreamOfTheFaraway() = card(
        name = "Dream of the Faraway",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Surveil 1, then draw a card. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)
            Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(surveil(1), draw(1)), Keyword.Flashback(manaCost("{3}{U}")))

    @Test
    fun ejectionSeat() = card(
        name = "Ejection Seat",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Return target nonland permanent to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(static(Mechanic.CantBeCountered), spell(move(target("permanent"), Zone.Hand), draw(1), targets = listOf(slot("permanent", nonlandPermanent))))

    /** "When you next cast an instant or sorcery spell this turn, copy that spell." after making mana (CR 603.7c). */
    @Test
    fun aetherTincture() = card(
        name = "Aether Tincture",
        cost = "{3}{U}",
        type = "Artifact",
        text = "{T}, Exile this artifact: Add {U}. When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.Exile(arcanum.api.CostObjects.Self, Zone.Battlefield)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)), Players.You)),
            doing(Effect.CreateDelayedTrigger(youCast(and(or(instant, sorcery), spellFilter)), Instructions(steps = listOf(
                doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)))), arcanum.api.Duration.EndOfTurn))),
    )

    @Test
    fun frostFlan() = card(
        name = "Frost Pudding",
        cost = "{4}{U}{U}",
        type = "Creature — Elemental Ooze",
        pt = "5/4",
        text = """
            When this creature enters, tap target artifact or creature an opponent controls. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("permanent")), counters(target("permanent"), 1, stun), targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
        Keyword.Typecycling(sub("Island"), "Islandcycling", manaCost("{2}")),
    )

    @Test
    fun spellDamper() = card(
        name = "Spell Damper",
        cost = "{U}",
        type = "Instant",
        text = "Target creature you control gets +1/+1 and gains hexproof until end of turn. Untap it.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1, has(Keyword.Hexproof)), untapIt(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 601.2f
    @Test
    fun hamletMerchant() = card(
        name = "Hamlet Merchant",
        cost = "{2}{U}",
        type = "Creature — Beast Citizen",
        pt = "1/4",
        text = """
            {1}, {T}: Draw a card, then discard a card.
            {7}, {T}, Sacrifice this creature: Draw three cards. This ability costs {1} less to activate for each Town you control.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), draw(1), discard(1)),
        activated(cost(mana("{7}"), tap, sacrificeSelf), draw(3), rules = anyTime.copy(costChange = arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!,
            count(and(town, you))))),
    )

    // CR 707.2
    @Test
    fun portraitSketch() = card(
        name = "Portrait Sketch",
        cost = "{2}{U}{U}",
        type = "Sorcery",
        text = "Create a token that's a copy of target artifact, creature, or land.",
    ) parsesTo abilities(
        spell(doing(Effect.CreateToken(TokenSpec.CopyOf(target("permanent")), n(1), Players.You)), targets = listOf(slot("permanent", or(artifact, creature, land)))),
    )

    @Test
    fun watchtowerTurret() = card(
        name = "Watchtower Turret",
        cost = "{3}{U}",
        type = "Artifact Creature — Construct",
        pt = "3/3",
        text = """
            Flying
            Whenever another artifact you control enters, you may draw a card. If you do, discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(another, artifact, you)), Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"), ifThen(arcanum.api.Condition.Did("paid"), discard(1))),
    )

    @Test
    fun stingerSentry() = card(
        name = "Stinger Sentry",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Scorpion",
        pt = "1/4",
        text = "As long as you control seven or more lands, this creature gets +3/+0.",
    ) parsesTo abilities(static(pt(3, 0), condition = arcanum.api.Condition.Exists(and(land, you), atLeast = 7)))

    // CR 303.4 (an Aura), 302.6
    @Test
    fun slumberCharm() = card(
        name = "Slumber Charm",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
            When enchanted creature is dealt damage, sacrifice this Aura.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(equipped)),
        static(Mechanic.DoesntUntap, affects = equipped),
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(equipped)), per = DamageOccurrence.Recipient)), doing(Effect.Sacrifice(Objects.Self))),
    )

    // CR 118.12a, 701.6a
    @Test
    fun syncopatedRhythm() = card(
        name = "Syncopated Rhythm",
        cost = "{X}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {X}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("spell", spellFilter)),
            steps = listOf(
                // CR 107.3a (set check): the {X} to pay is the X announced as the spell was cast
                Step.Pay(cost(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Announced("X"))), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                ifThen(arcanum.api.Condition.Not(arcanum.api.Condition.Did("paid")), doing(Effect.Counter(target("spell"))))),
            announced = listOf("X"),
            replacements = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(arcanum.api.Modification.ChangeDestination(Destination(Zone.Exile))))))),
    )

    // CR 702.41a
    @Test
    fun journeyAcrossTheWorld() = card(
        name = "Journey Across the World",
        cost = "{5}{U}{U}",
        type = "Sorcery",
        text = """
            Affinity for Towns (This spell costs {1} less to cast for each Town you control.)
            Draw four cards.
        """,
    ) parsesTo abilities(Keyword.Affinity(town, "Towns"), spell(draw(4)))

    @Test
    fun valkyrieGunship() = card(
        name = "Valkyrie Gunship",
        cost = "{5}{U}{U}",
        type = "Artifact Creature — Construct",
        pt = "5/4",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), Keyword.Flying, triggered(entersSelf, surveil(2)))

    @Test
    fun ultaraTemporalThreat() = card(
        name = "Ultara, Temporal Threat",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Human Warlock",
        pt = "4/4",
        text = """
            When Ultara enters, tap all creatures your opponents control.
            Whenever a creature you control deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(Objects.Each(creatureOpponentControls))),
        triggered(on(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)), draw(1)),
    )
}
