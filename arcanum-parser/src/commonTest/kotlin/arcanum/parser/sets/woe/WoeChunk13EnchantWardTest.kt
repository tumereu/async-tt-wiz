package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 13: Auras, ward, Adventure faces, mana, cost changes, counterspells. */
class WoeChunk13EnchantWardTest {
    private val enchantedCreature = Objects.AttachedTo(Objects.Self)

    // CR 303.4 (enchant), 613.4c (+2/+2), 113.6m (graveyard-return ability works in the graveyard)
    @Test
    fun feralLineage() = card(
        name = "Feral Lineage",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2.
            {4}{G}: Return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(2, 2), affects = enchantedCreature),
        activated(cost(mana("{4}{G}")), move(Objects.Self, Zone.Hand)).copy(functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 303.4, 603.6a, 502.3 (doesn't untap), 700.4 (put into a graveyard from the battlefield), 608.2d (may pay, if you do)
    @Test
    fun bleakFrostbite() = card(
        name = "Bleak Frostbite",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
            When this Aura is put into a graveyard from the battlefield, you may pay {1}. If you do, scry 1, then draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(enchantedCreature)),
        static(Mechanic.DoesntUntap, affects = enchantedCreature),
        triggered(diesSelf,
            Step.Pay(manaCost("{1}"), mandatory = false, bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), scry(1), draw(1))),
    )

    // CR 303.4, 508.1 and 509.1b (can't attack or block), 602 (activated ability exiling the enchanted creature)
    @Test
    fun hutchedAway() = card(
        name = "Hutched Away",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
            {2}{W}: Exile enchanted creature.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = enchantedCreature),
        activated(cost(mana("{2}{W}")), move(enchantedCreature, Zone.Exile)),
    )

    // CR 715.2 (Adventure faces), 702.21 (ward), 702.36 (flash), 614.1c (enters tapped unless it's your turn), 715.4
    @Test
    fun antleredTarnWhale() = cardFaces(Layout.Adventure,
        card(
            name = "Antlered Tarn-Whale",
            cost = "{4}{U}{U}",
            type = "Creature — Whale",
            text = """
                Flash
                Ward {2}
                This creature enters tapped unless it's your turn.
            """,
            pt = "6/6",
        ) to abilities(
            Keyword.Flash,
            Keyword.Ward(manaCost("{2}")),
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self), condition = Condition.Not(Condition.YourTurn)),
        ),
        card(
            name = "Tarn Plunge",
            cost = "{1}{U}",
            type = "Instant — Adventure",
            text = "The owner of target attacking creature you don't control puts it on their choice of the top or bottom of their library.",
        ) to abilities(
            spell(
                Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("creature"))),
                Step.If(Condition.Chose("position", "top"), then = listOf(move(target("creature"), Zone.Library, position = 0)),
                    otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
                targets = listOf(slot("creature", and(attacking, creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
            ),
        ),
    )

    // CR 122.1g (stun counters), 702.21, 702.9 (flying), 602.2 (untap ability)
    @Test
    fun drowsySprite() = card(
        name = "Drowsy Sprite",
        cost = "{U}",
        type = "Creature — Faerie Wizard",
        text = """
            Flying, ward {2}
            This creature enters tapped with three stun counters on it. (If it would become untapped, remove a stun counter from it instead.)
            {1}{U}: Untap this creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(manaCost("{2}")),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped, Modification.EntersWithCounters(CounterKind.Stun, n(3))))), affects = Affected.Objects(Objects.Self)),
        activated(cost(mana("{1}{U}")), untapIt(Objects.Self)),
    )

    // CR 715.2, 702.21, 702.20 (vigilance), 610.3 and 603.7 (return at the beginning of the next end step)
    @Test
    fun mirroredTwosome() = cardFaces(Layout.Adventure,
        card(
            name = "Mirrored Twosome",
            cost = "{2}{U}{U}",
            type = "Creature — Faerie Wizard",
            text = "Flying, vigilance, ward {1}",
            pt = "4/4",
        ) to abilities(Keyword.Flying, Keyword.Vigilance, Keyword.Ward(manaCost("{1}"))),
        card(
            name = "Brief Spiral",
            cost = "{1}{W}",
            type = "Instant — Adventure",
            text = "Exile target nontoken creature. Return it to the battlefield under its owner's control at the beginning of the next end step.",
        ) to abilities(
            spell(
                Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.CreateDelayedTrigger(
                    Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                    Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound("exiled"),
                        Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))))),
                ),
                targets = listOf(slot("creature", and(not(token), creature))),
            ),
        ),
    )

    // CR 605.1a (mana abilities), 106.1b (any color), 602.5 (both abilities need the same tap)
    @Test
    fun thicketriderSatyr() = card(
        name = "Thicketrider Satyr",
        cost = "{1}{G}",
        type = "Creature — Satyr Scout",
        text = """
            {T}: Add {G}.
            {1}, {T}: Add one mana of any color.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
    )

    // CR 715.2, 509.1b (can't be blocked by creatures with power 2 or less), 701.8 (destroy)
    @Test
    fun stormcragVanguard() = cardFaces(Layout.Adventure,
        card(
            name = "Stormcrag Vanguard",
            cost = "{4}{G}{G}",
            type = "Creature — Giant Warrior",
            text = "This creature can't be blocked by creatures with power 2 or less.",
            pt = "6/7",
        ) to abilities(static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2))))),
        card(
            name = "Bear Under",
            cost = "{1}{G}",
            type = "Sorcery — Adventure",
            text = "Destroy target artifact or enchantment. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
        ),
    )

    // CR 707.10 (copy a spell), 707.10c (new targets), 715.4 (an Adventure spell is a spell cast as the Adventure face)
    @Test
    fun reeveOfYarns() = card(
        name = "Reeve of Yarns",
        cost = "{3}{U}",
        type = "Creature — Faerie Advisor",
        text = """
            Flying
            Whenever you cast an Adventure spell, you may copy it. You may choose new targets for the copy.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(sub("Adventure")),
            Step.May(listOf(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true))), Players.You),
        ),
    )

    // CR 601.2f (cost reduction), 117.7
    @Test
    fun jeeringPixie() = card(
        name = "Jeering Pixie",
        cost = "{2}{U}",
        type = "Creature — Faerie Rogue",
        text = """
            Flying
            Instant and sorcery spells you cast cost {1} less to cast.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 601.2f, 508.1a ("attacked this turn" counts creatures that were declared as attackers, even if they left combat)
    @Test
    fun unrulyInquiry() = card(
        name = "Unruly Inquiry",
        cost = "{6}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast for each creature that attacked this turn.
            Draw three cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Count(Objects.Each(and(creature, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any)))))))),
        spell(draw(3)),
    )

    // CR 601.2f, 508.1a, 120.3 (damage)
    @Test
    fun hunterkinFrenzy() = card(
        name = "Hunterkin Frenzy",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast for each creature that attacked this turn.
            Hunterkin Frenzy deals 5 damage to target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Count(Objects.Each(and(creature, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any)))))))),
        spell(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 701.6 (counter), 202.3 (mana value)
    @Test
    fun scornfulRebuff() = card(
        name = "Scornful Rebuff",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell with mana value 4 or greater.",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(spellFilter, manaValue(Comparison.AtLeast, 4))))),
    )

    // CR 715.2, 603.2 (opponent casts), 202.3
    @Test
    fun searingAdder() = cardFaces(Layout.Adventure,
        card(
            name = "Searing Adder",
            cost = "{1}{R}",
            type = "Creature — Elemental Snake",
            text = "Whenever an opponent casts a spell with mana value 3 or less, this creature deals 1 damage to that player.",
            pt = "2/1",
        ) to abilities(
            triggered(on(EventPattern.Cast(and(spellFilter, manaValue(Comparison.AtMost, 3)), PlayerFilter.Opponent)),
                damage(1, Recipients(players = Players.EventPlayer))),
        ),
        card(
            name = "Steam Rinse",
            cost = "{1}{U}",
            type = "Sorcery — Adventure",
            text = "Return target nonland permanent to its owner's hand. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
        ),
    )

    // CR 702.4 (defender), 602.2 (tap ability), 603.2 (untap trigger)
    @Test
    fun waywardTrebuchet() = card(
        name = "Wayward Trebuchet",
        cost = "{2}{R}",
        type = "Artifact Creature — Construct",
        text = """
            Defender
            {T}: This creature deals 1 damage to each opponent.
            Whenever you cast an instant or sorcery spell, untap this creature.
        """,
        pt = "0/4",
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), damage(1, eachOpponent)),
        triggered(youCast(or(instant, sorcery)), untapIt(Objects.Self)),
    )
}
