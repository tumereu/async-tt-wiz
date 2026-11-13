package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD sweep: siblings of the bugs ISD set work found, probed with invented cards in the wordings each fix's root cause could equally
 * affect. Each probe stays as a regression test.
 */
class IsdSweepTest {
    private val m1m1 = CounterKind.PowerToughness(-1, -1)
    private fun damageTo(source: ObjectFilter, to: RecipientFilter) = on(EventPattern.Damage(source, to, combat = true))
    private val toSelf = RecipientFilter(objects = ObjectFilter.Is(Objects.Self))
    private val dealer = Objects.EventObject(EventRole.Source)
    private val damaged = Objects.EventObject(EventRole.Object)

    // ---- "that creature" in a damage trigger is the creature the trigger names besides this one (chunk 13 fixed "Destroy that
    // creature." only; the general reading took the damaged object even when that was this creature or a player)

    // CR 120.2, 603.2: the creature that dealt the damage, not this creature it was dealt to
    @Test
    fun kilnwardSentryExilesTheCreatureThatDamagedIt() = card(
        name = "Kilnward Sentry", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "1/4",
        text = "Whenever a creature deals combat damage to this creature, exile that creature.",
    ) parsesTo abilities(
        triggered(damageTo(creature, toSelf), move(dealer, Zone.Exile)),
    )

    // CR 120.2, 122.1: the counter goes on the creature that dealt the damage
    @Test
    fun gravefenSentryCountersTheCreatureThatDamagedIt() = card(
        name = "Gravefen Sentry", cost = "{2}{B}", type = "Creature — Zombie Soldier", pt = "1/4",
        text = "Whenever a creature deals combat damage to this creature, put a -1/-1 counter on that creature.",
    ) parsesTo abilities(
        triggered(damageTo(creature, toSelf), counters(dealer, 1, m1m1)),
    )

    // CR 120.2: damage dealt to a player names one creature, the one that dealt it (as "put a +1/+1 counter on that creature" already read)
    @Test
    fun rookeryRansackExilesTheCreatureThatDamagedYou() = card(
        name = "Rookery Ransack", cost = "{3}{W}", type = "Enchantment",
        text = "Whenever a creature deals combat damage to you, exile that creature.",
    ) parsesTo abilities(
        triggered(damageTo(creature, RecipientFilter(players = PlayerFilter.You)), move(dealer, Zone.Exile)),
    )

    // CR 120.2: when this creature dealt the damage, "that creature" is the one it damaged (regression for the reading above)
    @Test
    fun ashmoorDuelistExilesTheCreatureItDamaged() = card(
        name = "Ashmoor Duelist", cost = "{2}{B}", type = "Creature — Vampire Warrior", pt = "2/2",
        text = "Whenever this creature deals combat damage to a creature, exile that creature.",
    ) parsesTo abilities(
        triggered(damageTo(ObjectFilter.Is(Objects.Self), RecipientFilter(objects = creature)), move(damaged, Zone.Exile)),
    )

    // ---- "that player" of a zone change is the player the trigger names: the permanent's controller, or the graveyard's owner
    // (chunk 03 fixed "loses 1 life" for a permanent entering only)

    // CR 110.2, 603.10a: the opponent who controlled the creature as it died (its owner may be another player); the event's player is
    // that controller, as for "that player loses 2 life"
    @Test
    fun graveWardLevyTaxesTheDeadCreaturesController() = card(
        name = "Graveward Levy", cost = "{2}{B}", type = "Enchantment",
        text = "Whenever a creature an opponent controls dies, that player loses 1 life.",
    ) parsesTo abilities(
        triggered(dies(and(creature, opponents)), loseLife(1, Players.EventPlayer)),
    )

    // CR 404.1: a card goes to its owner's graveyard; "that player" is that opponent, the card's owner, even when another player
    // controlled it on the battlefield
    @Test
    fun marrowtitheIdolMillsTheGraveyardsOwner() = card(
        name = "Marrowtithe Idol", cost = "{3}", type = "Artifact",
        text = "Whenever a creature card is put into an opponent's graveyard from anywhere, that player mills a card.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(creature, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.Opponent)), to = Zone.Graveyard)),
            mill(1, Players.OwnerOf(Objects.EventObject(EventRole.After)))),
    )

    // ---- block triggers once per creature (chunk 04): "becomes blocked by a creature" names the blocker (regression)

    // CR 509.3d: once for each blocking creature; "that creature" is that blocker
    @Test
    fun thornmouthLevelerShrinksEachBlocker() = card(
        name = "Thornmouth Leveler", cost = "{3}{R}", type = "Creature — Beast", pt = "4/3",
        text = "Whenever this creature becomes blocked by a creature, that creature gets -1/-1 until end of turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), creature, perBlocker = true)),
            untilEndOfTurn(Objects.EventObject(EventRole.OtherCombatant), Mechanic.ModifyPowerToughness(n(-1), n(-1)))),
    )

    // ---- what an earlier step moved is what "it" names later (chunk 07 bound the graveyard card; the hand card too, regression)

    // CR 400.7, 608.2c: "it" is the card exiled from the hand
    @Test
    fun fenwickProwlerReadsTheExiledHandCard() = card(
        name = "Fenwick Prowler", cost = "{2}", type = "Artifact",
        text = "{2}, {T}: Target player exiles a card from their hand. If it's a creature card, you gain 2 life.",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "chosen", chooser = Players.Target("player")),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), creature), listOf(gainLife(2))),
            targets = listOf(targetPlayer)),
    )
}
