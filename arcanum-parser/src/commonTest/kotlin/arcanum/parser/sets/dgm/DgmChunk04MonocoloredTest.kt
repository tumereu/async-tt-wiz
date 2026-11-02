package arcanum.parser.sets.dgm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TextWord
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTime
import arcanum.parser.sets.gtc.costOf
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.entersSelf
import arcanum.parser.sets.gtc.mana
import arcanum.parser.sets.gtc.manaPart
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.p1p1
import arcanum.parser.sets.gtc.playerOrPlaneswalker
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.steps
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.untilEndOfTurn
import kotlin.test.Test

/**
 * Dragon's Maze chunk 04: the monocoloured cards. Sacrifices chosen by each player at once (CR 101.4), a turn-long
 * prevention shield (CR 615), exile and return at the next end step (CR 603.7), two different targets ("another target",
 * CR 115.3), "unless its controller pays" (CR 118.12), a text-changing effect (CR 612.2), looking at and revealing cards,
 * a draw replacement while the hand is empty (CR 614.11), a random card revealed, an Aura's attack-or-block trigger,
 * spells cast from exile without paying (Possibility Storm, CR 118.9), overload and regeneration.
 */
class DgmChunk04MonocoloredTest {
    private val permanent = ObjectFilter.Category(ObjectCategory.Permanent)
    private fun tapOrUntap(target: String) = Step.May(listOf(
        Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
        Step.If(Condition.Chose("tapOrUntap", "tap"), then = listOf(Step.Do(Effect.Tap(Objects.Target(target)))),
            otherwise = listOf(Step.Do(Effect.Untap(Objects.Target(target))))),
    ), Players.You)

    // CR 101.4: each player chooses, then all are sacrificed at once
    @Test
    fun forsakeTheGuilds() = card(
        name = "Forsake the Guilds",
        cost = "{1}{W}",
        type = "Instant",
        text = "Each player sacrifices a multicolored permanent of their choice.",
    ) parsesTo abilities(
        Ability.Spell(steps(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Multicolored, permanent, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))),
                n(1), n(1)), bind = "sacrificed", chooser = Players.Bound("player")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ), together = true))),
    )

    // CR 615: a prevention effect with its own scope
    @Test
    fun riotQuelling() = card(
        name = "Riot Quelling",
        cost = "{2}{W}",
        type = "Instant",
        text = "You gain 1 life for each creature your opponents control. Prevent all damage that would be dealt to you this turn.",
    ) parsesTo abilities(
        Ability.Spell(steps(
            Step.Do(Effect.GainLife(Players.You, Value.Count(Objects.Each(creatureOpponentControls)))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)))),
                null, Duration.EndOfTurn)),
        )),
    )

    // CR 603.7: a delayed trigger returns the exiled card, a new object (CR 400.7)
    @Test
    fun aethershifter() = card(
        name = "Aethershifter",
        cost = "{4}{U}{U}",
        type = "Creature — Shapeshifter",
        pt = "4/5",
        text = """
            {U}: Exile this creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
            {U}: This creature can't be blocked this turn.
            {1}: This creature gets +1/-1 until end of turn.
            {1}: This creature gets -1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Activated(costOf(manaPart("{U}")), steps(
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                steps(Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))))),
        ), rules = anyTime),
        Ability.Activated(costOf(manaPart("{U}")), Instructions(steps = listOf(untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked))), rules = anyTime),
        Ability.Activated(costOf(manaPart("{1}")), Instructions(steps = listOf(pump(Objects.Self, 1, -1))), rules = anyTime),
        Ability.Activated(costOf(manaPart("{1}")), Instructions(steps = listOf(pump(Objects.Self, -1, 1))), rules = anyTime),
    )

    // CR 115.3: "another target permanent" is a different object from the first target
    @Test
    fun unseenStrings() = card(
        name = "Unseen Strings",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            You may tap or untap target permanent, then you may tap or untap another target permanent.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", permanent),
                TargetSlot("second permanent", TargetKind.Objects(permanent), min = n(1), max = n(1), distinctFrom = listOf("permanent"))),
            steps = listOf(tapOrUntap("permanent"), tapOrUntap("second permanent")),
        )),
        Keyword.Cipher,
    )

    // CR 118.12: unless its controller pays
    @Test
    fun staticMind() = card(
        name = "Static Mind",
        cost = "{3}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {6}.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
            steps = listOf(
                Step.Pay(mana("{6}"), payer = Players.ControllerOf(Objects.Target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(Objects.Target("spell"))))),
            ),
        )),
    )

    // CR 303.4a: enchant creature with power 3 or less
    @Test
    fun sprintersBane() = card(
        name = "Sprinter's Bane",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature with power 3 or less
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.Power(Comparison.AtMost, n(3)))))),
        Ability.Triggered(entersSelf, steps(Step.Do(Effect.Tap(Objects.AttachedTo(Objects.Self))))),
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
    )

    // CR 612.2: a colour word or a basic land type, chosen as it resolves; layer 3 (CR 613.1c)
    @Test
    fun traitSurgery() {
        val target = Objects.Target("permanent")
        card(
            name = "Trait Surgery",
            cost = "{U}",
            type = "Sorcery",
            text = """
                Change the text of target permanent by replacing all instances of one color word with another or one basic land type with another until end of turn.
                Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
            """,
        ) parsesTo abilities(
            Ability.Spell(Instructions(
                targets = listOf(slot("permanent", permanent)),
                steps = listOf(
                    Step.Choose(Choice.Option(listOf("color word", "basic land type")), bind = "word", chooser = Players.You),
                    Step.If(Condition.Chose("word", "color word"),
                        then = listOf(
                            Step.Choose(Choice.Color(), bind = "from", chooser = Players.You),
                            Step.Choose(Choice.Color(), bind = "to", chooser = Players.You),
                            untilEndOfTurn(target, Mechanic.ChangeText(TextWord.ColorWord(ColorRef.Chosen("from")), TextWord.ColorWord(ColorRef.Chosen("to")))),
                        ),
                        otherwise = listOf(
                            Step.Choose(Choice.BasicLandType, bind = "from", chooser = Players.You),
                            Step.Choose(Choice.BasicLandType, bind = "to", chooser = Players.You),
                            untilEndOfTurn(target, Mechanic.ChangeText(TextWord.LandType(SubtypeRef.Chosen("from")), TextWord.LandType(SubtypeRef.Chosen("to")))),
                        )),
                ),
            )),
            Keyword.Cipher,
        )
    }

    // CR 701.20: revealed cards go to the hand, the rest to the bottom
    @Test
    fun uncoveredSecrets() = card(
        name = "Uncovered Secrets",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Look at the top four cards of your library. You may reveal up to two instant and/or sorcery cards from among them and put the revealed cards into your hand. Put the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        Ability.Spell(steps(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))), n(0), n(2),
                from = Objects.Bound("seen")), bind = "revealed", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("revealed"))),
            Step.Do(Effect.Move(Objects.Bound("revealed"), Destination(Zone.Hand))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("revealed")), Destination(Zone.Library, libraryPosition = -1))),
        )),
    )

    // CR 614.11: a replacement of a draw, only while your hand is empty
    @Test
    fun bloodScribe() = card(
        name = "Blood Scribe",
        cost = "{1}{B}",
        type = "Creature — Zombie Wizard",
        pt = "2/1",
        text = "If you would draw a card while you have no cards in hand, instead you draw two cards and you lose 1 life.",
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = steps(
                Step.Do(Effect.Draw(Players.You, n(2))), Step.Do(Effect.LoseLife(Players.You, n(1))),
            ))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Compare(Value.HandSize(Players.You), Comparison.Equal, n(0)),
        ),
    )

    @Test
    fun cryptRaid() = card(
        name = "Crypt Raid",
        cost = "{2}{B}",
        type = "Instant",
        text = "Exile all creature cards from target player's graveyard. You gain 3 life for each card exiled this way.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))))),
                    Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.GainLife(Players.You, Value.Times(Value.Count(Objects.Bound("exiled")), 3))),
            ),
        )),
    )

    @Test
    fun fatalVapours() = card(
        name = "Fatal Vapours",
        cost = "{3}{B}",
        type = "Instant",
        text = "Target creature gets -4/-2 until end of turn.",
    ) parsesTo abilities(Ability.Spell(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(Objects.Target("creature"), -4, -2)))))

    // A card at random from their hand is revealed
    @Test
    fun hiredTormentor() = card(
        name = "Hired Tormentor",
        cost = "{2}{B}",
        type = "Creature — Human Rogue",
        pt = "2/3",
        text = """
            Defender
            {3}{B}, {T}: Target opponent loses 2 life, then reveals a card at random from their hand.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Ability.Activated(costOf(manaPart("{3}{B}"), arcanum.api.CostPart.TapSelf), Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                Step.Do(Effect.LoseLife(Players.Target("opponent"), n(2))),
                Step.Do(Effect.Reveal(Objects.Random(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))))), n(1)))),
            ),
        ), rules = anyTime),
    )

    // CR 508.1m, 509.1i: attacks or blocks; "its controller" is the enchanted creature's
    @Test
    fun sinisterHaunting() = card(
        name = "Sinister Haunting",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Whenever enchanted creature attacks or blocks, its controller loses 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))),
                EventPattern.Block(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), ObjectFilter.Any))),
            steps(Step.Do(Effect.LoseLife(Players.ControllerOf(Objects.EventObject(EventRole.Object)), n(2)))),
        ),
    )

    // CR 105.2b: monocoloured creatures
    @Test
    fun aweOfTheGuilds() = card(
        name = "Awe of the Guilds",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Monocolored creatures can't block this turn.",
    ) parsesTo abilities(Ability.Spell(steps(untilEndOfTurn(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Monocolored, creature))), Mechanic.CantBlock(ObjectFilter.Any)))))

    @Test
    fun clearTheWay() = card(
        name = "Clear the Way",
        cost = "{R}",
        type = "Sorcery",
        text = "Destroy target creature with defender.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.HasKeyword(Keyword.Defender))))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature")))))),
    )

    // CR 118.9: cast without paying; the cards exiled with it, wherever the spell went, are those still in exile
    @Test
    fun probabilityStorm() {
        val caster = Players.EventPlayer
        card(
            name = "Probability Storm",
            cost = "{3}{R}{R}",
            type = "Enchantment",
            text = "Whenever a player casts a spell from their hand, that player exiles it, then exiles cards from the top of their library until they exile a card that shares a card type with it. That player may cast that card without paying its mana cost. Then they put all cards exiled with this enchantment on the bottom of their library in a random order.",
        ) parsesTo abilities(
            Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.Any, from = Zone.Hand)), steps(
                Step.Do(Effect.Move(Objects.EventObject(EventRole.Object), Destination(Zone.Exile), postBind = "spell")),
                Step.Do(Effect.RevealUntil(ObjectFilter.Shares(SharedQuality.CardType, Objects.Bound("spell")), caster, n(1), reveal = true, into = Zone.Exile,
                    foundBind = "found", restBind = "rest")),
                Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("found"), PlayKind.Cast, withoutPaying = true, caster = caster))), caster),
                Step.Do(Effect.Move(Objects.Where(Objects.Union(listOf(Objects.Bound("spell"), Objects.Bound("found"), Objects.Bound("rest"))), ObjectFilter.InZone(Zone.Exile)),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            )),
        )
    }

    @Test
    fun punishTheFoe() = card(
        name = "Punish the Foe",
        cost = "{4}{R}",
        type = "Instant",
        text = "Punish the Foe deals 3 damage to target player or planeswalker and 3 damage to target creature.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(playerOrPlaneswalker, slot("creature", creature)),
            steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(target = "player or planeswalker"), n(3), source = Objects.Self)),
                Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), n(3), source = Objects.Self)),
            ),
        )),
    )

    // CR 508.1d: attacks each combat if able
    @Test
    fun riotSkirmisher() = card(
        name = "Riot Skirmisher",
        cost = "{1}{R}",
        type = "Creature — Goblin Berserker",
        pt = "2/1",
        text = """
            First strike
            This creature attacks each combat if able.
        """,
    ) parsesTo abilities(Keyword.FirstStrike, Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)))

    // CR 702.96a: overload replaces "target" with "each"
    @Test
    fun weaponSwell() = card(
        name = "Weapon Swell",
        cost = "{R}",
        type = "Instant",
        text = """
            Target creature you control gets +1/+0 and gains first strike until end of turn.
            Overload {1}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(pump(Objects.Target("creature"), 1, 0, Mechanic.GainAbilities(listOf(Keyword.FirstStrike)))))),
        Keyword.Overload(mana("{1}{R}"), steps(pump(Objects.Each(creatureYouControl), 1, 0, Mechanic.GainAbilities(listOf(Keyword.FirstStrike))))),
    )

    @Test
    fun kraulBrawler() = card(
        name = "Kraul Brawler",
        cost = "{1}{G}",
        type = "Creature — Insect Warrior",
        pt = "2/2",
        text = "{5}{G}: This creature gets +3/+3 until end of turn.",
    ) parsesTo abilities(Ability.Activated(costOf(manaPart("{5}{G}")), Instructions(steps = listOf(pump(Objects.Self, 3, 3))), rules = anyTime))

    // CR 701.19a: regenerate
    @Test
    fun mendingGraft() = card(
        name = "Mending Graft",
        cost = "{G}",
        type = "Instant",
        text = "Regenerate target creature.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Target("creature"))))))),
    )

    // CR 701.14a: fight
    @Test
    fun mutantsQuarry() = card(
        name = "Mutant's Quarry",
        cost = "{G}",
        type = "Instant",
        text = "Target creature you control with a +1/+1 counter on it fights target creature an opponent controls. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, arcanum.parser.sets.gtc.you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
                slot("second creature", creatureOpponentControls)),
            steps = listOf(Step.Do(Effect.Fight(Objects.Target("creature"), Objects.Target("second creature")))),
        )),
    )

    @Test
    fun phytosurge() = card(
        name = "Phytosurge",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Target creature gets +5/+5 until end of turn.",
    ) parsesTo abilities(Ability.Spell(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(Objects.Target("creature"), 5, 5)))))

    // CR 702.16: protection from blue; CR 101.2: can't be countered
    @Test
    fun skyswatter() = card(
        name = "Skyswatter",
        cost = "{1}{G}",
        type = "Creature — Insect",
        pt = "2/2",
        text = """
            Flash
            This spell can't be countered.
            Reach, protection from blue
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        Keyword.Reach,
        Keyword.Protection(ObjectFilter.OfColor(Color.Blue)),
    )
}
