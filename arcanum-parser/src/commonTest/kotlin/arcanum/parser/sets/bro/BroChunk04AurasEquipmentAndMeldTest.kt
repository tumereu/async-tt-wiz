package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * BRO chunk 04: Auras (CR 303.4), Equipment (CR 301.5, 702.6) and meld (CR 701.42, 712.4).
 */
class BroChunk04AurasEquipmentAndMeldTest {

    // ---- Auras

    // CR 613.1d, 613.1f, 613.4c: a granted activated ability, a subtype and a pump; CR 400.7: the Aura in the graveyard is a new object
    @Test
    fun wyrmbloodMantle() = card(
        name = "Wyrmblood Mantle",
        cost = "{1}{R}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has flying, haste, and "{1}: This creature gets +1/+0 until end of turn." It's a Dragon in addition to its other types.
            When enchanted creature dies, return this card to its owner's hand.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(
            pt(1, 1),
            has(Keyword.Flying, Keyword.Haste, activated(manaCost("{1}"), pump(Objects.Self, 1, 0))),
            Mechanic.SetTypes(addSubtypes = listOf(subtype("Dragon"))),
            affects = attached,
        ),
        triggered(dies(ObjectFilter.Is(attached)), move(Objects.SelfAfter, Zone.Hand)),
    )

    // CR 205.1b ("in addition to its other types"), 613.1d (layer 4), 613.4b (layer 7b)
    @Test
    fun ironwakeKindling() = card(
        name = "Ironwake Kindling",
        cost = "{3}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact
            When this Aura enters, draw a card.
            Enchanted artifact is a creature with base power and toughness 4/4 in addition to its other types.
        """,
    ) parsesTo abilities(
        enchant(artifact),
        triggered(entersSelf, draw(1)),
        static(Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.SetPowerToughness(n(4), n(4)), affects = attached),
    )

    // CR 611.3a: two static abilities, one while you control the enchanted creature and one while you don't
    @Test
    fun tyrantsLeash() {
        val youControlIt = Condition.ObjectMatches(attached, you)
        card(
            name = "Tyrant's Leash",
            cost = "{1}{R}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                As long as you control enchanted creature, it gets +2/+2. Otherwise, it can't block.
            """,
        ) parsesTo abilities(
            enchant(creature),
            static(pt(2, 2), affects = attached, condition = youControlIt),
            static(Mechanic.CantBlock(ObjectFilter.Any), affects = attached, condition = Condition.Not(youControlIt)),
        )
    }

    // CR 603.6c, 603.10a: a leaves-the-battlefield trigger of the Aura itself
    @Test
    fun brashOverreach() = card(
        name = "Brash Overreach",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+0 and has trample. (It can deal excess combat damage to the player or planeswalker it's attacking.)
            When this Aura is put into a graveyard from the battlefield, draw a card.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 0), has(Keyword.Trample), affects = attached),
        triggered(diesSelf, draw(1)),
    )

    // CR 613.4c: counted continuously
    @Test
    fun grovebarkPlating() {
        val forests = count(and(sub("Forest"), you))
        card(
            name = "Grovebark Plating",
            cost = "{2}{G}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                Enchanted creature gets +1/+1 for each Forest you control.
            """,
        ) parsesTo abilities(
            enchant(creature),
            static(Mechanic.ModifyPowerToughness(forests, forests), affects = attached),
        )
    }

    // CR 702.8a (flash), 611.2a (until end of turn)
    @Test
    fun drillmastersSash() = card(
        name = "Drillmaster's Sash",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, enchanted creature gains first strike until end of turn.
            Enchanted creature gets +1/+0.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(creature),
        triggered(entersSelf, gains(attached, Keyword.FirstStrike)),
        static(pt(1, 0), affects = attached),
    )

    // CR 701.22a (scry), 508.1c / 509.1b (can't attack or block), 602.5 (can't be activated, mana abilities included)
    @Test
    fun gaolersVerdict() = card(
        name = "Gaoler's Verdict",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, scry 2.
            Enchanted creature can't attack or block, and its activated abilities can't be activated.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(entersSelf, scry(2)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true), affects = attached),
    )

    // CR 613.1f: the granted trigger is the creature's own ("this creature")
    @Test
    fun skywardTether() = card(
        name = "Skyward Tether",
        cost = "{3}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+0 and has flying and "Whenever this creature attacks, draw a card."
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(1, 0), has(Keyword.Flying, triggered(attacksSelf, draw(1))), affects = attached),
    )

    // CR 118.8, 603.12: "you may pay {3}. If you do" is an optional payment (Audit (unsure): false alarm, mandatory = false is the
    // optional marker); CR 502.3 (doesn't untap)
    @Test
    fun dimstoneFetter() = card(
        name = "Dimstone Fetter",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            When this Aura enters, you may pay {3}. If you do, tap enchanted permanent.
            Enchanted permanent doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        triggered(entersSelf, Step.Pay(manaCost("{3}"), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), tapIt(attached))),
        static(Mechanic.DoesntUntap, affects = attached),
    )

    // ---- Equipment

    // CR 603.6a, 108.3: a creature entering from its owner's graveyard; CR 701.3a: attach to "it", the creature that entered
    @Test
    fun gravehookTalon() = card(
        name = "Gravehook Talon",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has menace. (It can't be blocked except by two or more creatures.)
            Whenever a creature enters from your graveyard, you may attach this Equipment to it.
            Equip {1}{B} ({1}{B}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Menace), affects = attached),
        triggered(on(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard, to = Zone.Battlefield)),
            Step.May(listOf(doing(Effect.Attach(Objects.Self, Recipients(objects = Objects.EventObject(EventRole.After))))), Players.You)),
        equip("{1}{B}"),
    )

    // CR 613.4c: counted continuously; CR 702.21a (ward)
    @Test
    fun ancestralExoframe() {
        val attachments = Value.Count(Objects.AttachmentsOf(attached, or(sub("Aura"), sub("Equipment"))))
        card(
            name = "Ancestral Exoframe",
            cost = "{2}",
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +1/+1 for each Aura and Equipment attached to it and has ward {2}. (Whenever equipped creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
                Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
            """,
        ) parsesTo abilities(
            static(Mechanic.ModifyPowerToughness(attachments, attachments), has(Keyword.Ward(manaCost("{2}"))), affects = attached),
            equip("{2}"),
        )
    }

    // CR 702.6a: one equip ability whose cost is either {2} or {B} (CR 601.2f, 602.2b: the cost is chosen as it's activated)
    @Test
    fun rotgiltDiadem() = card(
        name = "Rotgilt Diadem",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Whenever equipped creature dies, draw a card.
            Equip {2} or {B}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = attached),
        triggered(dies(ObjectFilter.Is(attached)), draw(1)),
        Keyword.Equip(Cost(listOf(CostPart.OneOf(listOf(manaCost("{2}"), manaCost("{B}")))))),
    )

    // CR 702.6a
    @Test
    fun kitewingHarness() = card(
        name = "Kitewing Harness",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has flying.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Flying), affects = attached),
        equip("{2}"),
    )

    // CR 702.6a, 702.6e: "Equip Soldier {W}" attaches only to a Soldier; two equip abilities
    @Test
    fun oldGuardsEdge() = card(
        name = "Old Guard's Edge",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Equip Soldier {W}
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = attached),
        Keyword.Equip(manaCost("{W}"), onto = sub("Soldier")),
        equip("{2}"),
    )

    // ---- Meld

    private fun partner(type: arcanum.api.ObjectFilter, name: String, vararg also: ObjectFilter) =
        and(*also, type, ObjectFilter.Named(name), you, ObjectFilter.OwnedBy(PlayerFilter.You))
    private val ownsSelf = Condition.ObjectMatches(Objects.Self, and(you, ObjectFilter.OwnedBy(PlayerFilter.You)))

    /** "exile them, then meld them into [into]": the partner is chosen as the ability resolves (CR 712.4a) */
    private fun meldSteps(partner: ObjectFilter, into: String, tapped: Boolean = false, attacking: AttackTarget? = null) = listOf(
        Step.Choose(Choice.Objects(partner, n(1), n(1)), bind = "partner", chooser = Players.You),
        move(Objects.Union(listOf(Objects.Self, Objects.Bound("partner"))), Zone.Exile, postBind = "exiled"),
        doing(Effect.Action(KeywordAction.Meld(Objects.Bound("exiled"), into, tapped = tapped, attacking = attacking))))

    // CR 701.42a, 712.4a: both attacking, both owned and controlled by you; CR 508.4: the melded permanent enters attacking what its
    // controller chooses, without having attacked; CR 603.2c ("whenever you attack" once per combat)
    @Test
    fun varkonClaimedByRuin() {
        val engine = partner(creature, "Ruin Drake Engine", attacking)
        val attackers = count(and(attacking, creature))
        cardFaces(Layout.Meld("Ruin Drake Engine"),
            card(
                name = "Varkon, Claimed by Ruin",
                cost = "{2}{B}{R}",
                type = "Legendary Creature — Phyrexian Human Artificer",
                pt = "3/5",
                text = "Whenever you attack, each opponent loses X life and you gain X life, where X is the number of attacking creatures. If Varkon, Claimed by Ruin and a creature named Ruin Drake Engine are attacking, and you both own and control them, exile them, then meld them into Varkon, Lost to Ruin. It enters tapped and attacking.",
            ) to abilities(
                triggered(youAttack,
                    doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), attackers)),
                    doing(Effect.GainLife(Players.You, attackers)),
                    ifThen(Condition.And(listOf(Condition.ObjectMatches(Objects.Self, and(attacking, you, ObjectFilter.OwnedBy(PlayerFilter.You))),
                        Condition.Exists(engine, atLeast = 1))),
                        *meldSteps(engine, "Varkon, Lost to Ruin", tapped = true, attacking = AttackTarget.Chosen).toTypedArray())),
            ),
            card(
                name = "Varkon, Lost to Ruin",
                cost = null,
                type = "Legendary Artifact Creature — Phyrexian Artificer",
                colorIndicator = "BR",
                pt = "9/9",
                text = """
                    Whenever Varkon enters or attacks, choose three —
                    • Target opponent discards two cards.
                    • Varkon deals 3 damage to any target.
                    • Destroy target artifact or planeswalker.
                    • Creatures you control gain menace and trample until end of turn.
                    • Creatures you don't control get -1/-1 until end of turn.
                    • Create two tapped Powerstone tokens.
                """,
            ) to abilities(
                Ability.Triggered(entersOrAttacksSelf, Instructions(modes = modes(min = 3, max = 3, options = arrayOf(
                    mode(Step.Do(Effect.Discard(Players.Target("opponent"), n(2), ObjectFilter.Any)), targets = listOf(targetOpponent.copy(name = "opponent"))),
                    mode(damage(3, toAny), targets = listOf(anyTarget)),
                    mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, ObjectFilter.Type(CardType.Planeswalker))))),
                    mode(gains(Objects.Each(creatureYouControl), Keyword.Menace, Keyword.Trample)),
                    mode(pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))), -1, -1)),
                    mode(createPowerstone(n(2))),
                )))),
            ),
        )
    }

    // CR 701.42a, 712.4a: a land partner, and a second condition of the intervening if (CR 603.4); CR 604.3 (characteristic-defining
    // power and toughness); CR 205.1b: "It's still a land" keeps its other types
    @Test
    fun sylvaraVoiceOfLoam() {
        val grove = partner(land, "Sylvgrove, Sanctum of Roots")
        val landCards = and(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
        val lands = count(and(land, you))
        cardFaces(Layout.Meld("Sylvgrove, Sanctum of Roots"),
            card(
                name = "Sylvara, Voice of Loam",
                cost = "{1}{G}{G}",
                type = "Legendary Creature — Elemental",
                pt = "3/4",
                text = """
                    Reach
                    Whenever one or more land cards are put into your graveyard from anywhere, you gain 2 life.
                    At the beginning of your upkeep, if there are four or more land cards in your graveyard and you both own and control Sylvara, Voice of Loam and a land named Sylvgrove, Sanctum of Roots, exile them, then meld them into Sylvara, Loam Incarnate.
                """,
            ) to abilities(
                Keyword.Reach,
                triggered(Trigger.On(EventPattern.ZoneChange(and(land, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard), oneOrMore = true), gainLife(2)),
                triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), *meldSteps(grove, "Sylvara, Loam Incarnate").toTypedArray(),
                    interveningIf = Condition.And(listOf(Condition.Exists(landCards, atLeast = 4), ownsSelf, Condition.Exists(grove, atLeast = 1)))),
            ),
            card(
                name = "Sylvara, Loam Incarnate",
                cost = null,
                type = "Legendary Creature — Elemental Avatar",
                colorIndicator = "G",
                pt = "*/*",
                text = """
                    Reach, vigilance, trample, haste
                    Sylvara's power and toughness are each equal to the number of lands you control.
                    When Sylvara enters, return all land cards from your graveyard to the battlefield tapped.
                    {3}{G}: Put four +1/+1 counters on target land you control. It becomes a 0/0 Elemental creature with haste. It's still a land.
                """,
            ) to abilities(
                Keyword.Reach, Keyword.Vigilance, Keyword.Trample, Keyword.Haste,
                static(Mechanic.SetPowerToughness(lands, lands), characteristicDefining = true),
                triggered(entersSelf, move(Objects.Each(landCards), Zone.Battlefield, tapped = true)),
                activated(manaCost("{3}{G}"),
                    counters(target("land"), 4),
                    doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(subtype("Elemental"))),
                        Mechanic.SetPowerToughness(n(0), n(0)), has(Keyword.Haste)), arcanum.api.Affected.Objects(target("land")), Duration.Indefinitely)),
                    targets = listOf(slot("land", and(land, you)))),
            ),
        )
    }

    // CR 701.42a, 712.4a: an artifact partner, an activated meld ability (CR 602.5d: as a sorcery); CR 606.3: loyalty abilities twice
    // each turn; CR 611.2c, 601.2f: a cost reduction for the turn
    @Test
    fun orenLordWarden() {
        val stones = partner(artifact, "The Brightstone and Dimstone")
        val spells = and(or(artifact, instant, sorcery), spellFilter, you)
        cardFaces(Layout.Meld("The Brightstone and Dimstone"),
            card(
                name = "Oren, Lord Warden",
                cost = "{1}{W}{U}",
                type = "Legendary Creature — Human Artificer",
                pt = "2/4",
                text = """
                    Artifact, instant, and sorcery spells you cast cost {1} less to cast.
                    {7}: If you both own and control Oren, Lord Warden and an artifact named The Brightstone and Dimstone, exile them, then meld them into Oren, Wardwalker. Activate only as a sorcery.
                """,
            ) to abilities(
                static(Mechanic.CostChange(spells, change = CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
                activated(manaCost("{7}"), ifThen(Condition.And(listOf(ownsSelf, Condition.Exists(stones, atLeast = 1))),
                    *meldSteps(stones, "Oren, Wardwalker").toTypedArray()), rules = asSorcery),
            ),
            card(
                name = "Oren, Wardwalker",
                cost = null,
                type = "Legendary Planeswalker — Urza",
                colorIndicator = "WU",
                loyalty = "7",
                text = """
                    You may activate the loyalty abilities of Oren twice each turn rather than only once.
                    [+2]: Artifact, instant, and sorcery spells you cast this turn cost {2} less to cast. You gain 2 life.
                    [+1]: Draw two cards, then discard a card.
                    [0]: Create two 1/1 colorless Soldier artifact creature tokens.
                    [−3]: Exile target nonland permanent.
                    [−10]: Artifacts and planeswalkers you control gain indestructible until end of turn. Destroy all nonland permanents.
                """,
            ) to abilities(
                static(Mechanic.LoyaltyActivationsPerTurn(2)),
                loyalty(2, doing(Effect.ApplyMechanics(listOf(Mechanic.CostChange(spells, CostChange.Less(ManaCost.parse("{2}")!!, n(1)))), null, Duration.EndOfTurn)),
                    gainLife(2)),
                loyalty(1, draw(2), discard(1)),
                loyalty(0, create(creatureToken("Soldier", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature)), n(2))),
                loyalty(-3, move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
                loyalty(-10, gains(Objects.Each(and(or(artifact, ObjectFilter.Type(CardType.Planeswalker)), you)), Keyword.Indestructible),
                    destroy(Objects.Each(and(nonland, permanentFilter)))),
            ),
        )
    }
}
