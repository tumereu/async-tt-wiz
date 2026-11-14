package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MH3 chunk 06: affinity for artifacts (with a prototype), annihilator (with hexproof from each color) and bestow (Auras that count their
 * own counters, grant keywords, or pay to copy the creature they enchant).
 */
class Mh3Chunk06AffinityAnnihilatorBestowTest {
    private val affinityForArtifacts = Keyword.Affinity(forWhat = artifact, printed = "artifacts")
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val countersOnThis = Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single)
    private fun bestow(cost: String) = Keyword.Bestow(manaCost(cost))
    private fun enchantedGets(vararg mechanics: Mechanic) = static(*mechanics, affects = enchanted)

    // CR 702.160a, 718.3: prototype's mana cost, power and toughness; CR 702.41a: affinity for artifacts
    @Test
    fun toadmyrBruiser() = card(
        name = "Toadmyr Bruiser",
        cost = "{7}",
        type = "Artifact Creature — Frog Myr",
        pt = "4/4",
        text = """
            Prototype {3}{R} — 2/2 (You may cast this spell with different mana cost, color, and size. It keeps its abilities and types.)
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        """,
    ) parsesTo abilities(
        Keyword.Prototype(manaCost("{3}{R}"), power = 2, toughness = 2),
        affinityForArtifacts,
    )

    // CR 107.1a: half, rounded down; CR 608.2h: the sacrificed artifact's mana value as it last existed
    @Test
    fun kelzarSlagGlutton() = card(
        name = "Kelzar Slag-Glutton",
        cost = "{6}{B}{R}",
        type = "Legendary Creature — Demon",
        pt = "5/5",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            When Kelzar enters, you draw X cards and you lose X life, where X is half the number of artifacts you control, rounded down.
            {3}{R}, Sacrifice an artifact: Kelzar deals damage equal to the sacrificed artifact's mana value to any target.
        """,
    ) parsesTo abilities(
        affinityForArtifacts,
        Value.Half(count(and(artifact, you)), roundUp = false).let { x ->
            triggered(entersSelf, Step.Do(Effect.Draw(Players.You, x)), Step.Do(Effect.LoseLife(Players.You, x)))
        },
        activated(cost(mana("{3}{R}"), sacrificeA(artifact, preBind = "sacrificed")),
            damage(Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single), toAny), targets = listOf(anyTarget)),
    )

    // CR 702.41a: affinity on a creature spell; CR 613.4c: a pump until end of turn
    @Test
    fun forgebellyDrake() = card(
        name = "Forgebelly Drake",
        cost = "{5}{R}{R}",
        type = "Artifact Creature — Dragon",
        pt = "5/5",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            {R}: This creature gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        affinityForArtifacts,
        Keyword.Flying,
        activated(manaCost("{R}"), pump(Objects.Self, 1, 0)),
    )

    // CR 701.9b: a player with no cards in hand can't discard; for each such opponent you draw
    @Test
    fun salvagedVermin() = card(
        name = "Salvaged Vermin",
        cost = "{3}{B}",
        type = "Artifact Creature — Zombie Rat",
        pt = "2/1",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            When this creature enters, each opponent discards a card. For each opponent who can't, you draw a card.
        """,
    ) parsesTo abilities(
        affinityForArtifacts,
        Keyword.Flying,
        triggered(entersSelf,
            Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Opponent)),
                bind = "opponent",
                together = true,
                steps = listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))),
                        payer = Players.Bound("opponent"),
                        mandatory = true,
                        bind = "discarded",
                    ),
                    Step.If(condition = Condition.CouldNot("discarded"), then = listOf(draw(1))),
                ),
            ),
        ),
    )

    // CR 603.2 (cast trigger), 702.11d: hexproof from each color is from every coloured source; CR 702.86a: annihilator
    @Test
    fun unmakerOfHues() = card(
        name = "Unmaker of Hues",
        cost = "{6}{C}{C}",
        type = "Creature — Eldrazi",
        pt = "8/4",
        text = """
            When you cast this spell, you gain 1 life for each colorless permanent you control.
            Hexproof from each color
            Annihilator 2 (Whenever this creature attacks, defending player sacrifices two permanents of their choice.)
        """,
    ) parsesTo abilities(
        triggered(youCastThis, Step.Do(Effect.GainLife(Players.You, count(and(ObjectFilter.Colorless, permanentFilter, you))))),
        Keyword.HexproofFrom(not(ObjectFilter.Colorless)),
        Keyword.Annihilator(2),
    )

    // CR 702.86a: annihilator; CR 113.6m: the return ability works in the graveyard; CR 702.29a: cycling
    @Test
    fun eldraziDespoiler() = card(
        name = "Eldrazi Despoiler",
        cost = "{5}{C}",
        type = "Creature — Eldrazi",
        pt = "6/6",
        text = """
            Annihilator 1 (Whenever this creature attacks, defending player sacrifices a permanent of their choice.)
            Sacrifice two Eldrazi: Return this card from your graveyard to your hand.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        Keyword.Annihilator(1),
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(sub("Eldrazi"), count = n(2)))), move(Objects.Self, Zone.Hand))
            .copy(functionsIn = listOf(Zone.Graveyard)),
        Keyword.Cycling(manaCost("{2}")),
    )

    // CR 603.2 (cast trigger, also when evoked), 702.74a: evoke
    @Test
    fun voidwakeDrifter() = card(
        name = "Voidwake Drifter",
        cost = "{7}",
        type = "Creature — Eldrazi Elemental",
        pt = "4/4",
        text = """
            When you cast this spell, draw two cards.
            Flying
            Annihilator 1 (Whenever this creature attacks, defending player sacrifices a permanent of their choice.)
            Evoke {2}{U} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
        """,
    ) parsesTo abilities(
        triggered(youCastThis, draw(2)),
        Keyword.Flying,
        Keyword.Annihilator(1),
        Keyword.Evoke(manaCost("{2}{U}")),
    )

    // CR 702.103b: bestowed, "this Aura" is this permanent; CR 613.4c: +1/+1 for each of its +1/+1 counters
    @Test
    fun sigilWisp() = card(
        name = "Sigil Wisp",
        cost = "{1}{W}",
        type = "Enchantment Creature — Elemental",
        pt = "2/2",
        text = """
            Bestow {1}{W} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
            Landfall — Whenever a land you control enters, put a +1/+1 counter on this permanent.
            Enchanted creature gets +1/+1 for each +1/+1 counter on this Aura.
        """,
    ) parsesTo abilities(
        bestow("{1}{W}"),
        triggered(landfall, counters(Objects.Self), abilityWord = "Landfall"),
        enchantedGets(Mechanic.ModifyPowerToughness(countersOnThis, countersOnThis)),
    )

    // CR 107.3 (X of the bestow cost), 614.1c: enters with X counters; CR 702.103b, 613.4c
    @Test
    fun starbornHydra() = card(
        name = "Starborn Hydra",
        cost = "{X}{G}",
        type = "Enchantment Creature — Hydra",
        pt = "0/1",
        text = """
            Bestow {X}{G}{G} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
            Reach, trample
            This permanent enters with X +1/+1 counters on it.
            Enchanted creature gets +1/+1 for each +1/+1 counter on this Aura and has reach and trample.
        """,
    ) parsesTo abilities(
        bestow("{X}{G}{G}"),
        Keyword.Reach,
        Keyword.Trample,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(of = Objects.Self))))),
        enchantedGets(Mechanic.ModifyPowerToughness(countersOnThis, countersOnThis), has(Keyword.Reach, Keyword.Trample)),
    )

    // CR 702.103b; CR 608.2c: the payment is offered only while it enchants a creature you control; without a copy made, an Insect
    // (CR 707.2: the copy has the copiable values of that creature)
    @Test
    fun thicketbloomMantis() = card(
        name = "Thicketbloom Mantis",
        cost = "{1}{G}",
        type = "Enchantment Creature — Insect Monk",
        pt = "1/1",
        text = """
            Bestow {1}{G}
            Enchanted creature gets +1/+1.
            Landfall — Whenever a land you control enters, you may pay {1}{G} if this permanent is attached to a creature you control. If you do, create a token that's a copy of that creature. If you didn't create a token this way, create a 1/1 green Insect creature token.
        """,
    ) parsesTo abilities(
        bestow("{1}{G}"),
        enchantedGets(pt(1, 1)),
        triggered(landfall,
            Step.If(Condition.ObjectMatches(enchanted, creatureYouControl), then = listOf(
                Step.Pay(manaCost("{1}{G}"), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(
                    Step.Do(Effect.CreateToken(TokenSpec.CopyOf(enchanted, emptyList()), n(1), Players.You, bind = "token")))),
            )),
            Step.If(Condition.Not(Condition.NonEmpty("token")), then = listOf(create(creatureToken("Insect", listOf(Color.Green), 1, 1)))),
            abilityWord = "Landfall",
        ),
    )

    // CR 702.103a: a bestow cost with collect evidence (CR 701.59a); CR 601.3e: cast from the graveyard only bestowed
    @Test
    fun inquestPhoenix() = card(
        name = "Inquest Phoenix",
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
        Keyword.Bestow(Cost(listOf(CostPart.Mana(ManaCost.parse("{R}")!!), CostPart.Action(KeywordAction.CollectEvidence(n(6)))))),
        Keyword.Flying,
        Keyword.Haste,
        enchantedGets(pt(2, 2), has(Keyword.Flying, Keyword.Haste)),
        static(Mechanic.MayPlay(Zone.Graveyard, viaAlternative = Keyword.Bestow.Kind, how = PlayKind.Cast, holder = PlayerFilter.You)),
    )

    // CR 702.103a; CR 702.135a: afterlife, also granted to the enchanted creature
    @Test
    fun owingShade() = card(
        name = "Owing Shade",
        cost = "{W}",
        type = "Enchantment Creature — Spirit",
        pt = "1/1",
        text = """
            Bestow {2}{W} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
            Afterlife 1 (When this permanent is put into a graveyard from the battlefield, create a 1/1 white and black Spirit creature token with flying.)
            Enchanted creature gets +1/+1 and has afterlife 1.
        """,
    ) parsesTo abilities(
        bestow("{2}{W}"),
        Keyword.Afterlife(1),
        enchantedGets(pt(1, 1), has(Keyword.Afterlife(1))),
    )

    // CR 702.103a; CR 702.134a: mentor, also granted to the enchanted creature
    @Test
    fun starbornUnicorn() = card(
        name = "Starborn Unicorn",
        cost = "{1}{W}",
        type = "Enchantment Creature — Unicorn",
        pt = "2/2",
        text = """
            Bestow {3}{W} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
            Enchanted creature gets +2/+2 and has mentor.
        """,
    ) parsesTo abilities(
        bestow("{3}{W}"),
        Keyword.Mentor,
        enchantedGets(pt(2, 2), has(Keyword.Mentor)),
    )

    // CR 702.103a; CR 613.1 (layers 4, 5, 6, 7b), 205.1a, 105.3: "is a green Elk creature" replaces its types and colours
    @Test
    fun pranksterStag() = card(
        name = "Prankster Stag",
        cost = "{2}{G}",
        type = "Enchantment Creature — Elk",
        pt = "3/3",
        text = """
            Bestow {1}{G} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
            Enchanted creature loses all abilities and is a green Elk creature with base power and toughness 3/3.
        """,
    ) parsesTo abilities(
        bestow("{1}{G}"),
        enchantedGets(
            Mechanic.LoseAbilities(),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Green))),
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elk"))), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(3), n(3)),
        ),
    )

    // CR 702.103a; CR 702.108a: prowess only while this permanent is a creature (not while bestowed, CR 702.103b)
    @Test
    fun tidewakeTriton() = card(
        name = "Tidewake Triton",
        cost = "{U}",
        type = "Enchantment Creature — Merfolk Wizard",
        pt = "1/1",
        text = """
            Bestow {1}{U} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
            As long as this permanent is a creature, it has prowess. (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Enchanted creature gets +1/+1 and has prowess.
        """,
    ) parsesTo abilities(
        bestow("{1}{U}"),
        Ability.Static(listOf(has(Keyword.Prowess)), Affected.Objects(Objects.Self), condition = Condition.ObjectMatches(Objects.Self, creature)),
        enchantedGets(pt(1, 1), has(Keyword.Prowess)),
    )
}
