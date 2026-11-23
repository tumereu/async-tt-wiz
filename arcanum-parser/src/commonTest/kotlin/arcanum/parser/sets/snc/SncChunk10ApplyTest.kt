package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class SncChunk10ApplyTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)


    // CR 700.2 (modes), 608.2c (the pumped creature deals damage by its power as it resolves), 115.1 (targets of the chosen mode)
    @Test
    fun brokersCharm() = card(
        name = "Guild Accord",
        cost = "{G}{W}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature you control gets +1/+0 until end of turn. It deals damage equal to its power to target creature or planeswalker an opponent controls.
            • Destroy target enchantment.
            • Draw two cards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(pump(target("creature"), 1, 0),
                doing(Effect.DealDamage(to = Recipients(objects = target("permanent")), amount = power(target("creature")), source = target("creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("permanent", and(or(creature, planeswalker), opponents)))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(draw(2))))),
    )


    // CR 613.4b (base power and toughness set by an effect)
    @Test
    fun brokersInitiate() = card(
        name = "Guild Novice",
        cost = "{W}",
        type = "Creature — Cat Citizen",
        pt = "0/4",
        text = """
            {4}{G/U}: This creature has base power and toughness 5/5 until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{4}{G/U}")), untilEndOfTurn(Objects.Self, Mechanic.SetPowerToughness(n(5), n(5)))),
    )


    // CR 702.4a (double strike), 611.2a
    @Test
    fun cabarettiInitiate() = card(
        name = "Troupe Novice",
        cost = "{G}",
        type = "Creature — Raccoon Citizen",
        pt = "1/2",
        text = """
            {2}{R/W}: This creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}{R/W}")), gains(Objects.Self, Keyword.DoubleStrike)),
    )


    // CR 119.7 (can't gain life), 615.1 (damage that can't be prevented)
    @Test
    fun callInAProfessional() = card(
        name = "Summon the Fixer",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Players can't gain life this turn. Damage can't be prevented this turn. Summon the Fixer deals 3 damage to any target. (Shield counters don't prevent this damage as they're removed.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantGainLife), Affected.Players(Players.Each(PlayerFilter.Any)), Duration.EndOfTurn)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), null, Duration.EndOfTurn)),
            damage(3, toAny),
            targets = listOf(anyTarget)),
    )


    // CR 118.12 and 603.12 (may pay, if you do), 701.26a (tap a Citizen as an effect)
    @Test
    fun civilServant() = card(
        name = "Duty Clerk",
        cost = "{G}{W}",
        type = "Creature — Cat Citizen",
        pt = "2/3",
        text = """
            Whenever this creature attacks, you may tap another untapped Citizen you control. If you do, this creature gets +1/+0 and gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(and(another, sub("Citizen")), count = n(1)))), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), pump(Objects.Self, 1, 0, has(Keyword.Lifelink)))),
    )


    // CR 119.4 (paying life), 602.5b (once each turn)
    @Test
    fun cutthroatContender() = card(
        name = "Knifepoint Brawler",
        cost = "{B}",
        type = "Creature — Vampire Warrior",
        pt = "1/1",
        text = """
            Pay 1 life: This creature gets +1/+0 until end of turn. Activate only once each turn.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.PayLife(n(1))), pump(Objects.Self, 1, 0), rules = anyTime.copy(perTurn = 1)),
    )


    // CR 115.3 (a different target), 115.1 (up to one), 613.4b, 120.3 (damage to each creature, including the changed ones)
    @Test
    fun hostileTakeover() = card(
        name = "Corporate Raid",
        cost = "{2}{U}{B}{R}",
        type = "Sorcery",
        text = """
            Up to one target creature has base power and toughness 1/1 until end of turn. Up to one other target creature has base power and toughness 4/4 until end of turn. Then Corporate Raid deals 3 damage to each creature.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(1), n(1))),
            untilEndOfTurn(target("second creature"), Mechanic.SetPowerToughness(n(4), n(4))),
            damage(3, toObjects(Objects.Each(creature))),
            targets = listOf(slot("creature", creature, min = 0), TargetSlot("second creature", TargetKind.Objects(creature), min = n(0), max = n(1), distinctFrom = listOf("creature")))),
    )


    // CR 107.3 (X), 111.1 (token)
    @Test
    fun luxuriousLibation() = card(
        name = "Lavish Toast",
        cost = "{X}{G}",
        type = "Instant",
        text = """
            Target creature gets +X/+X until end of turn. Create a 1/1 green and white Citizen creature token.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Announced("X"), Value.Announced("X"))),
            create(creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)),
            targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )


    // CR 205.1b (artifact creature keeps its other types, Angel replaces creature types), 613.4b
    @Test
    fun majesticMetamorphosis() = card(
        name = "Grand Transfiguration",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Until end of turn, target artifact or creature becomes a 4/4 Angel artifact creature and gains flying.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature),
            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Angel"))), replaceTypes = false, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.Flying)), draw(1),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )


    // CR 113.6 (an ability that works from the hand), 400.7, 601.2 (cast from exile)
    @Test
    fun maskedBandits() = card(
        name = "Veiled Outlaws",
        cost = "{3}{B}{R}{G}",
        type = "Creature — Raccoon Rogue",
        pt = "5/5",
        text = """
            Vigilance
            Menace (This creature can't be blocked except by two or more creatures.)
            {2}, Exile this card from your hand: Target land gains "{T}: Add {B}, {R}, or {G}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Menace,
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Hand, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(activated(cost(tap),
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(Color.Black, Color.Red, Color.Green).map { ColorRef.Literal(it) })), Players.You)))))),
                Affected.Objects(target("land")), Duration.UntilCast(Objects.Bound("exiled")))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely)),
            targets = listOf(slot("land", land))),
    )


    // CR 603.4 (intervening if), 700.8 (multicolored permanents), 702.4a
    @Test
    fun scuttlingButler() = card(
        name = "Skittering Steward",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        pt = "4/1",
        text = """
            At the beginning of combat on your turn, if you control two or more multicolored permanents, this creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, gains(Objects.Self, Keyword.DoubleStrike),
            interveningIf = Condition.Exists(and(ObjectFilter.Multicolored, permanentFilter, you), atLeast = 2)),
    )


    // CR 508.1c and 509.1b (can't attack or block), 611.2b (until your next turn), 113.6
    @Test
    fun sparaSAdjudicators() = card(
        name = "Tribunal of Three",
        cost = "{2}{G}{W}{U}",
        type = "Creature — Cat Citizen",
        pt = "4/4",
        text = """
            When this creature enters, target creature an opponent controls can't attack or block until your next turn.
            {2}, Exile this card from your hand: Target land gains "{T}: Add {G}, {W}, or {U}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(target("creature")), Duration.UntilYourNextTurn)),
            targets = listOf(slot("creature", creatureOpponentControls))),
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Hand, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(activated(cost(tap),
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(Color.Green, Color.White, Color.Blue).map { ColorRef.Literal(it) })), Players.You)))))),
                Affected.Objects(target("land")), Duration.UntilCast(Objects.Bound("exiled")))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely)),
            targets = listOf(slot("land", land))),
    )


    // CR 702.3b (defender), 509.1b (can't be blocked)
    @Test
    fun suspiciousBookcase() = card(
        name = "Dubious Shelf",
        cost = "{2}",
        type = "Artifact Creature — Wall",
        pt = "0/4",
        text = """
            Defender
            {3}, {T}: Target creature can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{3}"), tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
    )


    // CR 611.2c (affects the creatures on resolution), 613.4c (two separate +N/+N)
    @Test
    fun takeToTheStreets() = card(
        name = "Rally the Block",
        cost = "{4}{G}",
        type = "Sorcery",
        text = """
            Creatures you control get +2/+2 until end of turn. Citizens you control get an additional +1/+1 and gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            pump(Objects.Each(creatureYouControl), 2, 2),
            pump(Objects.Each(and(sub("Citizen"), you)), 1, 1, has(Keyword.Vigilance))),
    )

}