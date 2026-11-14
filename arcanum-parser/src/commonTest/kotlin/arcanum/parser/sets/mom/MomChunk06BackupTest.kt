package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 06: backup (CR 702.165): the keyword followed by the abilities it may grant; the granting is the keyword's own meaning. */
class MomChunk06BackupTest {
    private fun backupReminder(n: Int, plural: Boolean = false) = "Backup $n (When this creature enters, put ${if (n == 1) "a +1/+1 counter" else "two +1/+1 counters"} on target creature. " +
        "If that's another creature, it gains the following abilit${if (plural) "ies" else "y"} until end of turn.)"
    private val isSelf = ObjectFilter.Is(Objects.Self)
    private val combatDamageToPlayerOrBattle = combatDamageToAPlayerOrBattle

    // CR 702.165a; CR 510.2 (combat damage), 603.6a (return to the battlefield)
    @Test
    fun archpriestOfShadows() = card(
        name = "Gravebound Prelate", cost = "{3}{B}{B}", type = "Creature — Human Warlock", pt = "4/4", text = """
            ${backupReminder(1, plural = true)}
            Deathtouch
            Whenever this creature deals combat damage to a player or battle, return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Backup(1),
        Keyword.Deathtouch,
        triggered(combatDamageToPlayerOrBattle, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 702.165a; CR 510.2: "that many" is the damage dealt; CR 401.4: the rest go to the bottom in a random order chosen by the player
    @Test
    fun doomskarWarrior() = card(
        name = "Trailblazing Vanguard", cost = "{2}{G}{G}", type = "Creature — Human Warrior", pt = "4/3", text = """
            ${backupReminder(1)}
            Trample
            Whenever this creature deals combat damage to a player or battle, look at that many cards from the top of your library. You may reveal a creature or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Backup(1),
        Keyword.Trample,
        triggered(combatDamageToPlayerOrBattle,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), Players.You, bind = "seen")),
            Step.Choose(arcanum.api.Choice.Objects(or(creature, land), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                arcanum.api.Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 510.1c: damage assigned by toughness
    @Test
    fun streetwiseNegotiator() = card(
        name = "Stalwart Wallkeeper", cost = "{1}{G}", type = "Creature — Cat Citizen", pt = "0/2", text = """
            ${backupReminder(1)}
            This creature assigns combat damage equal to its toughness rather than its power.
        """,
    ) parsesTo abilities(Keyword.Backup(1), static(Mechanic.AssignsDamageByToughness))

    // CR 702.165a; CR 508.1 (attack trigger)
    @Test
    fun bolaSlinger() = card(
        name = "Snare Skirmisher", cost = "{3}{W}", type = "Creature — Cat Soldier", pt = "2/2", text = """
            ${backupReminder(1)}
            Whenever this creature attacks, tap target artifact or creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Backup(1),
        triggered(attacksSelf, tapIt(target("permanent")),
            targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
    )

    // CR 702.165a
    @Test
    fun boonBringerValkyrie() = card(
        name = "Blessing Seraph", cost = "{3}{W}{W}", type = "Creature — Angel Warrior", pt = "4/4", text = """
            ${backupReminder(1, plural = true)}
            Flying, first strike, lifelink
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.Flying, Keyword.FirstStrike, Keyword.Lifelink)

    // CR 702.165a; CR 509.1b (blocking restriction)
    @Test
    fun chompingKavu() = card(
        name = "Gnashing Kavu", cost = "{3}{G}", type = "Creature — Kavu", pt = "3/3", text = """
            ${backupReminder(1)}
            This creature can't be blocked by creatures with power 2 or less.
        """,
    ) parsesTo abilities(Keyword.Backup(1), static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))))

    // CR 702.165a
    @Test
    fun consumingAetherborn() = card(
        name = "Siphoning Aetherborn", cost = "{3}{B}", type = "Creature — Aetherborn Vampire", pt = "2/2", text = """
            ${backupReminder(1)}
            Lifelink
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.Lifelink)

    // CR 702.165a, 702.29 (typecycling)
    @Test
    fun cragsmasherYeti() = card(
        name = "Boulderfall Yeti", cost = "{4}{R}{R}", type = "Creature — Yeti", pt = "4/2", text = """
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
            ${backupReminder(2)}
            Trample
        """,
    ) parsesTo abilities(Keyword.Typecycling(sub("Mountain"), "Mountaincycling", manaCost("{2}")), Keyword.Backup(2), Keyword.Trample)

    // CR 702.165a; CR 122.2 (counters move), 700.4 (dies)
    @Test
    fun enduringBondwarden() = card(
        name = "Heirloom Warden", cost = "{W}", type = "Creature — Human Scout", pt = "0/1", text = """
            ${backupReminder(1)}
            When this creature dies, put its counters on target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Backup(1),
        triggered(diesSelf,
            doing(Effect.MoveCounters(from = Objects.Self, to = target("creature"), kind = null,
                amount = Value.Of(NumericProperty.Counters(kind = null), Objects.Self, Combine.Single))),
            targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 702.165a
    @Test
    fun fearlessSkald() = card(
        name = "Reckless Skald", cost = "{4}{R}", type = "Creature — Dwarf Berserker", pt = "3/2", text = """
            ${backupReminder(1)}
            Double strike
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.DoubleStrike)

    // CR 702.165a, 702.29
    @Test
    fun gloomfangMauler() = card(
        name = "Duskmire Mauler", cost = "{5}{B}{B}", type = "Creature — Nightmare", pt = "5/5", text = """
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
            ${backupReminder(2)}
            Menace
        """,
    ) parsesTo abilities(Keyword.Typecycling(sub("Swamp"), "Swampcycling", manaCost("{2}")), Keyword.Backup(2), Keyword.Menace)

    // CR 702.165a
    @Test
    fun goldenScaleAeronaut() = card(
        name = "Gilded Skyhand", cost = "{4}{W}", type = "Creature — Dwarf Pilot", pt = "2/3", text = """
            ${backupReminder(1)}
            Flying
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.Flying)

    // CR 702.165a; CR 603.2 (becomes tapped)
    @Test
    fun hangarScrounger() = card(
        name = "Scrapyard Rummager", cost = "{2}{R}", type = "Creature — Dwarf Pilot", pt = "2/1", text = """
            ${backupReminder(1)}
            Whenever this creature becomes tapped, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Backup(1),
        Ability.Triggered(Trigger.On(events = listOf(EventPattern.Tap(objects = isSelf))), Instructions(steps = listOf(
            Step.Pay(cost = Cost(parts = listOf(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))))))),
    )

    // CR 702.165a
    @Test
    fun redcapHeelslasher() = card(
        name = "Cinder Footpad", cost = "{3}{R}", type = "Creature — Goblin Rogue", pt = "2/3", text = """
            ${backupReminder(1)}
            First strike
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.FirstStrike)

    // CR 702.165a
    @Test
    fun saibaCryptomancer() = card(
        name = "Shroud Adept", cost = "{1}{U}", type = "Creature — Moonfolk Ninja", pt = "0/1", text = """
            Flash
            ${backupReminder(1)}
            Hexproof
        """,
    ) parsesTo abilities(Keyword.Flash, Keyword.Backup(1), Keyword.Hexproof)

    // CR 702.165a; CR 602.1 (activated ability), 701.21 (sacrifice)
    @Test
    fun scornBladeBerserker() = card(
        name = "Spiteful Cutthroat", cost = "{B}", type = "Creature — Human Berserker", pt = "0/1", text = """
            ${backupReminder(1)}
            {1}, Sacrifice this creature: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Backup(1),
        activated(cost(mana("{1}"), CostPart.Sacrifice(what = CostObjects.Self)), doing(Effect.Draw(players = Players.You, count = n(1)))),
    )

    // CR 702.165a
    @Test
    fun serpentBladeAssailant() = card(
        name = "Viper Fencer", cost = "{2}{G}", type = "Creature — Elf Warrior", pt = "2/1", text = """
            ${backupReminder(1)}
            Deathtouch
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.Deathtouch)

    // CR 702.165a
    @Test
    fun sigiledSentinel() = card(
        name = "Crested Watchman", cost = "{2}{W}", type = "Creature — Human Knight", pt = "2/2", text = """
            ${backupReminder(1)}
            Vigilance
        """,
    ) parsesTo abilities(Keyword.Backup(1), Keyword.Vigilance)

    // CR 702.165a; CR 608.2h: its power as it last existed
    @Test
    fun voldarenThrillseeker() = card(
        name = "Crimson Kamikaze", cost = "{2}{R}", type = "Creature — Vampire Warrior", pt = "1/1", text = """
            ${backupReminder(2)}
            {1}, Sacrifice this creature: It deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        Keyword.Backup(2),
        activated(cost(mana("{1}"), CostPart.Sacrifice(what = CostObjects.Self)),
            damage(Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), toAny),
            targets = listOf(anyTarget)),
    )
}
