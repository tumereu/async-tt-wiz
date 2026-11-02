package arcanum.parser.sets.dgm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureToken
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.gains
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.opponents
import arcanum.parser.sets.gtc.p1p1
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.untilEndOfTurn
import kotlin.test.Test

/**
 * Dragon's Maze chunk 01: split cards with fuse. Each half is a face of a split card (CR 709); fuse (CR 702.102) lets
 * its owner cast both halves from their hand as one spell, the left half's instructions first (702.102d), so each half
 * carries the keyword. The halves' own instructions: tokens, life for each creature, a delayed trigger for the turn,
 * milling and putting a creature from any graveyard onto the battlefield with haste, control until end of turn, each
 * player sacrificing one permanent of each type (CR 101.4), and others.
 */
class DgmChunk01FuseTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
    private fun half(name: String, cost: String, type: String, text: String) = card(name = name, cost = cost, type = type, text = text + "\n" + fuse)

    @Test
    fun livingThriving() = cardFaces(
        Layout.Split,
        half("Living", "{3}{G}", "Sorcery", "Create a 3/3 green Centaur creature token.") to
            abilities(spell(Step.Do(Effect.CreateToken(creatureToken("Centaur", listOf(Color.Green), 3, 3), n(1), Players.You))), Keyword.Fuse),
        half("Thriving", "{W}", "Sorcery", "You gain 2 life for each creature you control.") to
            abilities(spell(Step.Do(Effect.GainLife(Players.You, Value.Times(Value.Count(Objects.Each(creatureYouControl)), 2)))), Keyword.Fuse),
    )

    // CR 509.1c: every creature able to block it must
    @Test
    fun armoredHazardous() = cardFaces(
        Layout.Split,
        half("Armored", "{1}{R}", "Sorcery", "Target creature gets +1/+1 and gains double strike until end of turn.") to
            abilities(spell(pump(Objects.Target("creature"), 1, 1, Mechanic.GainAbilities(listOf(Keyword.DoubleStrike))), targets = listOf(slot("creature", creature))), Keyword.Fuse),
        half("Hazardous", "{3}{G}", "Sorcery", "All creatures able to block target creature this turn do so.") to
            abilities(spell(untilEndOfTurn(Objects.Each(creature), Mechanic.MustBlock(ObjectFilter.Is(Objects.Target("creature")))),
                targets = listOf(slot("creature", creature))), Keyword.Fuse),
    )

    // CR 603.7b: each time a creature enters this turn
    @Test
    fun beckonSummon() = cardFaces(
        Layout.Split,
        half("Beckon", "{G}{U}", "Sorcery", "Whenever a creature enters this turn, you may draw a card.") to
            abilities(spell(Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.ZoneChange(creature, to = Zone.Battlefield)),
                Instructions(steps = listOf(Step.May(listOf(Step.Do(Effect.Draw(Players.You, n(1)))), Players.You))),
                duration = Duration.EndOfTurn, repeats = true,
            ))), Keyword.Fuse),
        half("Summon", "{4}{W}{U}", "Sorcery", "Create four 1/1 white Bird creature tokens with flying.") to
            abilities(spell(Step.Do(Effect.CreateToken(creatureToken("Bird", listOf(Color.White), 1, 1, listOf(Keyword.Flying)), n(4), Players.You))), Keyword.Fuse),
    )

    // A creature card from any graveyard, chosen as it resolves; that permanent gains haste
    @Test
    fun shatteringInvading() = cardFaces(
        Layout.Split,
        half("Shattering", "{U}{B}", "Sorcery", "Target player mills eight cards.") to
            abilities(spell(Step.Do(Effect.Mill(Players.Target("player"), n(8))), targets = listOf(targetPlayer)), Keyword.Fuse),
        half("Invading", "{4}{B}{R}", "Sorcery", "Put a creature card from a graveyard onto the battlefield under your control. It gains haste until end of turn.") to
            abilities(spell(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard))), n(1), n(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))),
                gains(Objects.Bound("chosen"), Keyword.Haste),
            ), Keyword.Fuse),
    )

    // CR 101.4: each player chooses one of each, all sacrificed at once
    @Test
    fun seizeDiscard() {
        val player = Players.Bound("player")
        fun one(type: CardType, bind: String) = Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(type), ObjectFilter.ControlledBy(PlayerFilter.Is(player)))),
            n(1), n(1)), bind = bind, chooser = player)
        cardFaces(
            Layout.Split,
            half("Seize", "{1}{U}{R}", "Sorcery", "Gain control of target permanent until end of turn. Untap it. It gains haste until end of turn.") to
                abilities(spell(
                    untilEndOfTurn(Objects.Target("permanent"), Mechanic.ControlledBy(Players.You)),
                    Step.Do(Effect.Untap(Objects.Target("permanent"))),
                    gains(Objects.Target("permanent"), Keyword.Haste),
                    targets = listOf(slot("permanent", ObjectFilter.Category(ObjectCategory.Permanent))),
                ), Keyword.Fuse),
            half("Discard", "{4}{R}{W}", "Sorcery", "Each player sacrifices an artifact, a creature, an enchantment, a land, and a planeswalker of their choice.") to
                abilities(spell(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                    one(CardType.Artifact, "sacrificed1"), one(CardType.Creature, "sacrificed2"), one(CardType.Enchantment, "sacrificed3"),
                    one(CardType.Land, "sacrificed4"), one(CardType.Planeswalker, "sacrificed5"),
                    Step.Do(Effect.Sacrifice(Objects.Union((1..5).map { Objects.Bound("sacrificed$it") }))),
                ), together = true)), Keyword.Fuse),
        )
    }

    @Test
    fun lowMuddy() = cardFaces(
        Layout.Split,
        half("Low", "{3}{B}", "Sorcery", "Target player discards two cards.") to
            abilities(spell(Step.Do(Effect.Discard(Players.Target("player"), n(2), filter = ObjectFilter.Any)), targets = listOf(targetPlayer)), Keyword.Fuse),
        half("Muddy", "{2}{G}", "Sorcery", "Return target card from your graveyard to your hand.") to
            abilities(spell(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))),
                targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You)))))), Keyword.Fuse),
    )

    @Test
    fun nearAfar() = cardFaces(
        Layout.Split,
        half("Near", "{1}{U}", "Instant", "Return target creature to its owner's hand.") to
            abilities(spell(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand))), targets = listOf(slot("creature", creature))), Keyword.Fuse),
        half("Afar", "{2}{B}", "Instant", "Target player sacrifices a creature of their choice.") to
            abilities(spell(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Target("player")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                targets = listOf(targetPlayer),
            ), Keyword.Fuse),
    )

    // CR 608.2h: the exiled card's power as it last existed; the targeted creature is the source of its damage
    @Test
    fun boneMarrow() = cardFaces(
        Layout.Split,
        half("Bone", "{3}{B}{G}", "Sorcery", "Exile target creature card from a graveyard. Put X +1/+1 counters on target creature, where X is the power of the card you exiled.") to
            abilities(spell(
                Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.PutCounters(Objects.Target("creature"), p1p1, Value.Of(NumericProperty.Power, Objects.Bound("exiled"), Combine.Single))),
                targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard)))), slot("creature", creature)),
            ), Keyword.Fuse),
        half("Marrow", "{R}{G}", "Sorcery", "Target creature you control deals damage equal to its power to any target.") to
            abilities(spell(
                Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single), source = Objects.Target("creature"))),
                targets = listOf(slot("creature", creatureYouControl), anyTarget),
            ), Keyword.Fuse),
    )

    // The number of counters actually removed
    @Test
    fun grantCollect() = cardFaces(
        Layout.Split,
        half("Grant", "{2}{G}", "Sorcery", "Put three +1/+1 counters on target creature.") to
            abilities(spell(Step.Do(Effect.PutCounters(Objects.Target("creature"), p1p1, n(3))), targets = listOf(slot("creature", creature))), Keyword.Fuse),
        half("Collect", "{2}{U}", "Sorcery", "Remove all +1/+1 counters from target creature you control. Draw that many cards.") to
            abilities(spell(
                Step.Do(Effect.RemoveCounters(Objects.Target("creature"), p1p1, Value.Of(NumericProperty.Counters(p1p1), Objects.Target("creature"), Combine.Single), bind = "removed")),
                Step.Do(Effect.Draw(Players.You, Value.Bound("removed"))),
                targets = listOf(slot("creature", creatureYouControl)),
            ), Keyword.Fuse),
    )

    @Test
    fun gainBane() = cardFaces(
        Layout.Split,
        half("Gain", "{1}{W}", "Instant", "Creatures you control get +1/+1 until end of turn.") to abilities(spell(pump(Objects.Each(creatureYouControl), 1, 1)), Keyword.Fuse),
        half("Bane", "{2}{B}", "Instant", "Creatures your opponents control get -1/-1 until end of turn.") to
            abilities(spell(pump(Objects.Each(ObjectFilter.And(listOf(creature, opponents))), -1, -1)), Keyword.Fuse),
    )

    @Test
    fun guardAssist() = cardFaces(
        Layout.Split,
        half("Guard", "{2}{W}", "Instant", "Target creature gets +2/+4 until end of turn.") to
            abilities(spell(pump(Objects.Target("creature"), 2, 4), targets = listOf(slot("creature", creature))), Keyword.Fuse),
        half("Assist", "{1}{U}", "Instant", "Target creature gets -6/-0 until end of turn.") to
            abilities(spell(pump(Objects.Target("creature"), -6, 0), targets = listOf(slot("creature", creature))), Keyword.Fuse),
    )

    @Test
    fun steadyEager() = cardFaces(
        Layout.Split,
        half("Steady", "{1}{G}{W}", "Instant", "Creatures you control gain indestructible until end of turn. Untap each creature you control.") to
            abilities(spell(gains(Objects.Each(creatureYouControl), Keyword.Indestructible), Step.Do(Effect.Untap(Objects.Each(creatureYouControl)))), Keyword.Fuse),
        half("Eager", "{1}{W}{B}", "Instant", "Creatures you control gain deathtouch and lifelink until end of turn.") to
            abilities(spell(gains(Objects.Each(creatureYouControl), Keyword.Deathtouch, Keyword.Lifelink)), Keyword.Fuse),
    )

    @Test
    fun laborStrife() = cardFaces(
        Layout.Split,
        half("Labor", "{2}{B}", "Sorcery", "Target player draws two cards and loses 2 life.") to
            abilities(spell(Step.Do(Effect.Draw(Players.Target("player"), n(2))), Step.Do(Effect.LoseLife(Players.Target("player"), n(2))), targets = listOf(targetPlayer)), Keyword.Fuse),
        half("Strife", "{2}{R}", "Sorcery", "Strife deals damage to target player equal to the number of cards in that player's hand.") to
            abilities(spell(Step.Do(Effect.DealDamage(Recipients(players = Players.Target("player")), Value.HandSize(Players.Target("player")), source = Objects.Self)),
                targets = listOf(targetPlayer)), Keyword.Fuse),
    )

    // CR 205.1a: the creature type replaces its creature types; layers 4–7b
    @Test
    fun twistScorch() = cardFaces(
        Layout.Split,
        half("Twist", "{2}{U}", "Instant", "Until end of turn, target creature loses all abilities and becomes a red Weird with base power and toughness 0/1.") to
            abilities(spell(untilEndOfTurn(Objects.Target("creature"),
                Mechanic.LoseAbilities(),
                Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Weird"))), replaceSubtypes = true),
                Mechanic.SetColors(listOf(ColorRef.Literal(Color.Red))),
                Mechanic.SetPowerToughness(n(0), n(1))), targets = listOf(slot("creature", creature))), Keyword.Fuse),
        half("Scorch", "{1}{R}", "Instant", "Scorch deals 2 damage to any target.") to
            abilities(spell(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(2), source = Objects.Self)), targets = listOf(anyTarget)), Keyword.Fuse),
    )

    @Test
    fun rendRip() = cardFaces(
        Layout.Split,
        half("Rend", "{1}{R}", "Instant", "Destroy target artifact.") to
            abilities(spell(Step.Do(Effect.Destroy(Objects.Target("artifact"))), targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact)))), Keyword.Fuse),
        half("Rip", "{W}", "Instant", "Destroy target enchantment.") to
            abilities(spell(Step.Do(Effect.Destroy(Objects.Target("enchantment"))), targets = listOf(slot("enchantment", ObjectFilter.Type(CardType.Enchantment)))), Keyword.Fuse),
    )
}
