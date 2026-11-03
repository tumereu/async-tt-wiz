package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.AbilityKind
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 11: Allies: Ally tokens, triggers on another Ally entering, counters and keywords for Allies, affinity for
 * Allies (CR 702.41a), the second resolution this turn, casting Ally spells from the top of the library (CR 601.2a), Ally triggers
 * triggering an additional time (CR 603.2d), giving an opponent control of a permanent, and Ally-only mana (CR 106.6).
 */
class TlaChunk11AlliesTest {
    private val allyTokens = create(allyToken)

    @Test
    fun heraldEnthusiasts() = card(
        name = "Herald Enthusiasts",
        cost = "{2}{W}",
        type = "Creature — Human Peasant Ally",
        text = "Whenever another Ally you control enters, put a +1/+1 counter on this creature.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(anotherAllyEnters, counters(Objects.Self)),
    )

    @Test
    fun stoneRealmProtectors() = card(
        name = "Stone Realm Protectors",
        cost = "{W}",
        type = "Creature — Human Soldier Ally",
        text = """
            Vigilance
            Sacrifice this creature: Another target Ally you control gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(sacrificeSelf), gains(target("Ally"), Keyword.Indestructible), targets = listOf(slot("Ally", and(another, ally, you)))),
    )

    // CR 111.1: counted as it resolves
    @Test
    fun gatherThePaleBlossom() = card(
        name = "Gather the Pale Blossom",
        cost = "{4}{W}",
        type = "Sorcery",
        text = "Create a 1/1 white Ally creature token for each Plains you control. Scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)",
    ) parsesTo abilities(
        spell(create(allyToken, count(and(sub("Plains"), you))), scry(2)),
    )

    // CR 601.2a (casting from the top of the library), 401.5
    @Test
    fun torvaldSelflessCommander() = card(
        name = "Torvald, Selfless Commander",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Vigilance
            You may look at the top card of your library any time.
            You may cast Ally spells from the top of your library.
            Sacrifice Torvald: Creatures you control get +0/+5 and gain indestructible until end of turn.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Vigilance,
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(ally, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        activated(cost(sacrificeSelf), pump(Objects.Each(creatureYouControl), 0, 5, has(Keyword.Indestructible))),
    )

    @Test
    fun siegeReinforcements() = card(
        name = "Siege Reinforcements",
        cost = "{1}{W}",
        type = "Creature — Human Warrior Ally",
        text = """
            Flash
            When this creature enters, create a 1/1 white Ally creature token.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, allyTokens),
    )

    @Test
    fun fanbladeWarriors() = card(
        name = "Fanblade Warriors",
        cost = "{3}{W}",
        type = "Creature — Human Warrior Ally",
        text = "When this creature enters, create a 1/1 white Ally creature token.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, allyTokens),
    )

    // "the second time this ability has resolved this turn" (counted on the ability's object)
    @Test
    fun southernIceVoyager() = card(
        name = "Southern Ice Voyager",
        cost = "{1}{W}",
        type = "Creature — Human Scout Ally",
        text = "Whenever this creature or another Ally you control enters, you gain 1 life. If this is the second time this ability has resolved this turn, draw a card.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, ally, you))), gainLife(1),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2)), then = listOf(draw(1)))),
    )

    // CR 603.6c (leaving the battlefield), "triggers only once each turn"
    @Test
    fun yarraCourageousRescuer() = card(
        name = "Yarra, Courageous Rescuer",
        cost = "{1}{W}{W}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Other creatures you control get +1/+0.
            Whenever another permanent you control leaves the battlefield during your turn, create a 1/1 white Ally creature token. This ability triggers only once each turn.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(and(another, creatureYouControl))),
        triggered(on(EventPattern.ZoneChange(and(another, permanentFilter, you), from = Zone.Battlefield)), allyTokens, perTurn = 1,
            triggersOnlyIf = Condition.YourTurn),
    )

    // CR 107.3
    @Test
    fun alliedVanguard() = card(
        name = "Allied Vanguard",
        cost = "{X}{W}{W}",
        type = "Sorcery",
        text = "Create X 1/1 white Ally creature tokens, then put a +1/+1 counter on each creature you control.",
    ) parsesTo abilities(
        spell(create(allyToken, x), counters(Objects.Each(creatureYouControl)), announced = listOf("X")),
    )

    @Test
    fun tideclanCaptain() = card(
        name = "Tideclan Captain",
        cost = "{2}{W}",
        type = "Creature — Human Soldier Ally",
        text = "{5}: Creatures you control get +1/+1 until end of turn.",
        pt = "3/3",
    ) parsesTo abilities(
        activated(cost(mana("{5}")), pump(Objects.Each(creatureYouControl), 1, 1)),
    )

    @Test
    fun canopyLiberators() = card(
        name = "Canopy Liberators",
        cost = "{2}{R}",
        type = "Creature — Human Rebel Ally",
        text = """
            Haste
            When this creature enters, create a 1/1 white Ally creature token.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, allyTokens),
    )

    // CR 603.6a: the creature that entered
    @Test
    fun wartimeDissenters() = card(
        name = "Wartime Dissenters",
        cost = "{3}{R}",
        type = "Creature — Human Rebel Ally",
        text = """
            Haste
            Whenever another Ally you control enters, put a +1/+1 counter on that creature and it gains haste until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(anotherAllyEnters, counters(enteredOne), gains(enteredOne, Keyword.Haste)),
    )

    // CR 702.41a (affinity for Allies), 120.3 (each dealer deals damage equal to its power)
    @Test
    fun comradesAtLast() = card(
        name = "Comrades at Last",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Affinity for Allies (This spell costs {1} less to cast for each Ally you control.)
            Up to two target creatures you control each deal damage equal to their power to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(ally, "Allies"),
        spell(Step.ForEach(arcanum.api.Each.Objects(target("creatures")), bind = "dealer", steps = listOf(
            damage(powerOf(Objects.Bound("dealer")), toObjects(target("creature")), source = Objects.Bound("dealer")))),
            targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2), slot("creature", creatureOpponentControls))),
    )

    // CR 613.1f: a mana ability granted to lands and Allies
    @Test
    fun grandChasmGuide() = card(
        name = "Grand Chasm Guide",
        cost = "{1}{G}",
        type = "Creature — Human Scout Ally",
        text = """
            Each land and Ally you control has "{T}: Add one mana of any color."
        """,
        pt = "2/3",
    ) parsesTo abilities(
        static(has(addAnyColor), affects = Objects.Each(and(or(land, ally), you))),
    )

    // CR 510.3a: one trigger for each player dealt combat damage by Allies
    @Test
    fun siegeTactics() = card(
        name = "Siege Tactics",
        cost = "{4}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, creatures you control get +2/+2 until end of turn.
            Whenever one or more Allies you control deal combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(Objects.Each(creatureYouControl), 2, 2)),
        triggered(Trigger.On(listOf(EventPattern.Damage(and(ally, you), RecipientFilter(players = PlayerFilter.Any), combat = true)), oneOrMore = true,
            batchPerPlayer = true), draw(1)),
    )

    @Test
    fun stoneSovereignsLieutenant() = card(
        name = "Stone Sovereign's Lieutenant",
        cost = "{G}{W}",
        type = "Creature — Human Soldier Ally",
        text = """
            Trample
            When this creature enters, put a +1/+1 counter on each other Ally creature you control.
            Whenever another Ally you control enters, put a +1/+1 counter on this creature.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, counters(Objects.Each(and(another, ally, creature, you)))),
        triggered(anotherAllyEnters, counters(Objects.Self)),
    )

    // CR 603.2d: a triggered ability of an Ally triggers an additional time
    @Test
    fun nerysTheFearless() = card(
        name = "Nerys, the Fearless",
        cost = "{G}{W}{U}",
        type = "Legendary Creature — Human Warrior Ally",
        text = "If a triggered ability of an Ally you control triggers, that ability triggers an additional time.",
        pt = "3/3",
    ) parsesTo abilities(
        static(Mechanic.TriggersAdditionalTime(event = null, sources = and(ally, you))),
    )

    // CR 613.1f
    @Test
    fun tammTenaciousTactician() = card(
        name = "Tamm, Tenacious Tactician",
        cost = "{1}{U}{R}{W}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Menace, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Other Allies you control have menace and prowess.
            Whenever you cast a noncreature spell, create a 1/1 white Ally creature token.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Prowess,
        static(has(Keyword.Menace, Keyword.Prowess), affects = Objects.Each(and(another, ally, you))),
        triggered(youCast(noncreature), allyTokens),
    )

    @Test
    fun paleBlossomReinforcements() = card(
        name = "Pale Blossom Reinforcements",
        cost = "{1}{G}{W}",
        type = "Creature — Human Soldier Ally",
        text = """
            Vigilance
            Other Allies you control get +1/+1.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(pt(1, 1), affects = Objects.Each(and(another, ally, you))),
    )

    // CR 613.1b (giving control away), 603.12
    @Test
    fun baldricBrewMaster() = card(
        name = "Baldric, Brew Master",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Citizen Ally",
        text = """
            When Baldric enters, create a Food token.
            At the beginning of combat on your turn, you may have target opponent gain control of target permanent you control. When you do, create a 1/1 white Ally creature token. Put a +1/+1 counter on that token for each permanent you own that your opponents control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        triggered(beginningOfCombatOnYourTurn,
            Step.Pay(steps = listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Target("opponent"))), arcanum.api.Affected.Objects(target("permanent")),
                arcanum.api.Duration.Indefinitely))), payer = Players.You, bind = "given"),
            Step.If(Condition.Did("given"), then = listOf(reflexive(create(allyToken, bind = "token"),
                counters(Objects.Bound("token"), count(and(permanentFilter, ObjectFilter.OwnedBy(PlayerFilter.You), opponents)))))),
            targets = listOf(targetOpponent, slot("permanent", and(permanentFilter, you)))),
    )

    // CR 301.5c: created, then attached
    @Test
    fun fanbladeWarFan() = card(
        name = "Fanblade War Fan",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a 1/1 white Ally creature token, then attach this Equipment to it.
            Equipped creature gets +1/+0.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(allyToken, bind = "token"), doing(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("token"))))),
        static(pt(1, 0), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 106.6: mana for Ally spells or abilities of Ally sources
    @Test
    fun goldenWyrmTeahouse() = card(
        name = "Golden Wyrm Teahouse",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast an Ally spell or activate an ability of an Ally source.
            {5}, {T}: Create a 1/1 white Ally creature token.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)),
            restriction = or(and(spellFilter, ally), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = ally))), Players.You))),
        activated(cost(mana("{5}"), tap), allyTokens),
    )
}
