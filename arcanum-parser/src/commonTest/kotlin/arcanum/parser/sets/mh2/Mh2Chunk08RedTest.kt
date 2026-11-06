package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 08: red cards, second part: shuffling your permanents away and putting revealed permanent cards onto the
 * battlefield (CR 701.24, 303.4f), coin flips (CR 705) with tokens tapped and attacking (CR 508.4), delirium on an attack trigger, a
 * triggered ability triggering an additional time (CR 603.2d), converge damage, an Equipment's sacrifice ability, an alternative cost
 * during your turn (CR 118.9), a reflexive trigger counting the discarded card's types, a cost reduction for opponents' lands that
 * could produce {C}, Ragavan's combat-damage trigger with dash (CR 702.109), madness, gaining control with storm and "whenever you win a
 * coin flip".
 */
class Mh2Chunk08RedTest {
    // CR 701.24a: shuffled into the library; CR 303.4f: an Aura put onto the battlefield is attached as it enters
    @Test
    fun glanceAtTomorrow() = card(
        name = "Glance at Tomorrow",
        cost = null,
        type = "Sorcery",
        text = """
            Suspend 3—{R}{R}
            Shuffle all permanents you own into your library, then reveal that many cards from the top of your library. Put all non-Aura permanent cards revealed this way onto the battlefield, then do the same for Aura cards, then put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(3, manaCost("{R}{R}")),
        spell(
            move(Objects.Each(and(permanentFilter, ObjectFilter.OwnedBy(PlayerFilter.You))), Zone.Library, postBind = "shuffled"),
            shuffle,
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Count(Objects.Bound("shuffled")), fromTop = true), bind = "revealed")),
            move(Objects.Where(Objects.Bound("revealed"), and(permanentCard, not(sub("Aura")))), Zone.Battlefield),
            move(Objects.Where(Objects.Bound("revealed"), sub("Aura")), Zone.Battlefield),
            doing(Effect.Move(Objects.Where(Objects.Bound("revealed"), inZone(Zone.Library)), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 705.2: each flip is won or lost; CR 508.4: created attacking, not declared as attackers
    @Test
    fun goblinSnarerunner() = card(
        name = "Goblin Snarerunner",
        cost = "{3}{R}",
        type = "Creature — Goblin",
        text = "Whenever this creature attacks, flip three coins. For each flip you win, create a 1/1 red Goblin creature token that's tapped and attacking.",
        pt = "4/2",
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Repeat(n(3), listOf(
            doing(Effect.FlipCoin(Players.You, bind = "flip")),
            Step.If(Condition.WonFlip("flip"), then = listOf(create(creatureToken("Goblin", listOf(Color.Red), 1, 1), tapped = true, attacking = AttackTarget.Chosen))),
        ))),
    )

    // CR 603.4: delirium checked as it triggers and resolves; the defending player's creatures
    @Test
    fun goredFanatic() = card(
        name = "Gored Fanatic",
        cost = "{3}{R}",
        type = "Creature — Cyclops Berserker",
        text = """
            Reach
            Delirium — Whenever this creature attacks, if there are four or more card types among cards in your graveyard, this creature deals 1 damage to each creature defending player controls.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(attacksSelf, damage(1, toObjects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))),
            interveningIf = delirium, abilityWord = "Delirium"),
    )

    // CR 603.2d: the ability triggers an additional time
    @Test
    fun resonantProdigy() = card(
        name = "Resonant Prodigy",
        cost = "{1}{R}",
        type = "Creature — Human Wizard",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            If a triggered ability of a Shaman or another Wizard you control triggers, that ability triggers an additional time.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Prowess,
        static(Mechanic.TriggersAdditionalTime(null, and(or(sub("Shaman"), and(another, sub("Wizard"))), you))),
    )

    @Test
    fun prismscorch() = card(
        name = "Prismscorch",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Converge — Prismscorch deals X damage to any target, where X is the number of colors of mana spent to cast this spell.
            Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(anyTarget), steps = listOf(damage(Value.ManaSpent(distinctColors = true, of = Objects.Self), toAny))),
            abilityWord = "Converge"),
        Keyword.Flashback(manaCost("{4}{R}")),
    )

    @Test
    fun thunderLance() = card(
        name = "Thunder Lance",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has trample.
            {2}{R}, Sacrifice this Equipment: It deals 3 damage to any target.
            Equip {1}
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Trample), affects = equipped),
        activated(cost(mana("{2}{R}"), sacrificeSelf), damage(3, toAny), targets = listOf(anyTarget)),
        equip("{1}"),
    )

    // CR 118.9: an alternative cost, only during your turn
    @Test
    fun tunnelCaveIn() = card(
        name = "Tunnel Cave-In",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            If it's your turn, you may sacrifice a Mountain rather than pay this spell's mana cost.
            Tunnel Cave-In deals 5 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(sub("Mountain"), count = n(1)))))), condition = Condition.YourTurn),
        spell(damage(5, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 603.12: the reflexive trigger; the number of card types the discarded card has
    @Test
    fun velusPeakManticore() = card(
        name = "Velus Peak Manticore",
        cost = "{2}{R}{R}",
        type = "Enchantment Creature — Manticore",
        text = "At the beginning of combat on your turn, you may discard a card. When you do, this creature deals X damage to any target, where X is the number of card types the discarded card has.",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)), preBind = "discarded"))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(
                damage(Value.CountDistinct(Objects.Bound("discarded"), SharedQuality.CardType), toAny), targets = listOf(anyTarget))))),
    )

    // CR 601.2f: {1} less for each such land
    @Test
    fun basaltCharmaw() = card(
        name = "Basalt Charmaw",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        text = """
            This spell costs {1} less to cast for each land your opponents control that could produce {C}.
            Flying
            When this creature enters, destroy target nonbasic land an opponent controls.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{1}")!!, count(and(land, opponents, ObjectFilter.CouldProduce(ManaCost.parse("{C}")!!)))))),
        Keyword.Flying,
        triggered(entersSelf, destroy(target("land")), targets = listOf(slot("land", and(not(ObjectFilter.Super(Supertype.Basic)), land, opponents)))),
    )

    // CR 702.109a dash; "that player" is the player dealt combat damage
    @Test
    fun rakavanQuickPilferer() = card(
        name = "Rakavan, Quick Pilferer",
        cost = "{R}",
        type = "Legendary Creature — Monkey Pirate",
        text = """
            Whenever Rakavan deals combat damage to a player, create a Treasure token and exile the top card of that player's library. Until end of turn, you may cast that card.
            Dash {1}{R} (You may cast this spell for its dash cost. If you do, it gains haste, and it's returned from the battlefield to its owner's hand at the beginning of the next end step.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer,
            createTreasure,
            doing(Effect.Move(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
        Keyword.Dash(manaCost("{1}{R}")),
    )

    @Test
    fun insurrectionist() = card(
        name = "Insurrectionist",
        cost = "{5}{R}",
        type = "Creature — Human Wizard",
        text = """
            When this creature enters, return target instant or sorcery card from your graveyard to your hand.
            Madness {3}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
        Keyword.Madness(manaCost("{3}{R}")),
    )

    @Test
    fun skophosRavager() = card(
        name = "Skophos Ravager",
        cost = "{2}{R}",
        type = "Creature — Minotaur Warrior",
        text = """
            During your turn, this creature gets +2/+0.
            Madness {1}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "2/3",
    ) parsesTo abilities(static(pt(2, 0), condition = Condition.YourTurn), Keyword.Madness(manaCost("{1}{R}")))

    @Test
    fun cinderStrider() = card(
        name = "Cinder Strider",
        cost = "{5}{R}{R}",
        type = "Creature — Elemental",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            {1}, Sacrifice an artifact: This creature deals 1 damage to any target.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Affinity(artifact, "artifacts"),
        activated(cost(mana("{1}"), sacrificeA(artifact)), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 702.40a storm: each copy may get new targets
    @Test
    fun spreadingRevolt() = card(
        name = "Spreading Revolt",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Gain control of target creature you don't control until end of turn. Untap that creature. It gains haste until end of turn.
            Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
        Keyword.Storm,
    )

    @Test
    fun hitTheJackpot() = card(
        name = "Hit the Jackpot",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Flashback {2}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(createTreasure), Keyword.Flashback(manaCost("{2}{R}")))

    // CR 705.2: winning a coin flip
    @Test
    fun taproomScoundrel() = card(
        name = "Taproom Scoundrel",
        cost = "{1}{R}",
        type = "Creature — Human Rogue",
        text = """
            Whenever you win a coin flip, create two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
            {1}, {T}, Sacrifice another permanent: Flip a coin.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.FlipCoin(PlayerFilter.You, won = true)), create(treasure, count = n(2))),
        activated(cost(mana("{1}"), tap, sacrificeA(and(another, permanentFilter))), doing(Effect.FlipCoin(Players.You, bind = "flip"))),
    )

    @Test
    fun profaneFever() = card(
        name = "Profane Fever",
        cost = "{R}",
        type = "Instant",
        text = """
            Profane Fever deals 2 damage to target creature or planeswalker.
            Delirium — Profane Fever deals 6 damage instead if there are four or more card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", or(creature, planeswalker))),
            steps = listOf(Step.If(delirium, then = listOf(damage(6, toObjects(target("permanent")))), otherwise = listOf(damage(2, toObjects(target("permanent")))))),
        ), abilityWord = "Delirium"),
    )

    @Test
    fun viashinoWhipclaw() = card(
        name = "Viashino Whipclaw",
        cost = "{1}{R}",
        type = "Creature — Lizard Warrior",
        text = "{T}, Discard a card: Creatures you control gain haste until end of turn.",
        pt = "2/2",
    ) parsesTo abilities(activated(cost(tap, discardACard), gains(Objects.Each(creatureYouControl), Keyword.Haste)))
}
