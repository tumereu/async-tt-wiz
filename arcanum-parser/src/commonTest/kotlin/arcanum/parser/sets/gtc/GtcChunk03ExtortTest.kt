package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Gatecrash chunk 03: extort. Extort is a keyword (CR 702.101a: "Whenever you cast a spell, you may pay {W/B}. If you do,
 * each opponent loses 1 life and you gain life equal to the total life lost this way"); its meaning comes from the
 * shared keyword table, so the definitions keep the keyword. With it: other permanents entering tapped, a triggered mana
 * ability on tapping a Swamp, removing a counter of any kind, and paying any amount of life to see that many cards.
 */
class GtcChunk03ExtortTest {
    @Test
    fun bastionWarden() = card(
        name = "Bastion Warden",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "1/4",
        text = """
            Defender
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        """,
    ) parsesTo abilities(Keyword.Defender, Keyword.Extort)

    // CR 614.1c, 614.12: whether it enters tapped is decided as the permanent it would be on the battlefield
    @Test
    fun edictOfSubmission() = card(
        name = "Edict of Submission",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Artifacts and creatures your opponents control enter tapped.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature)), opponents)), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            )),
            affects = arcanum.api.Affected.Objects(Objects.Self),
        ),
    )

    @Test
    fun oathboundKnight() = card(
        name = "Oathbound Knight",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "2/4",
        text = """
            Vigilance
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        """,
    ) parsesTo abilities(Keyword.Vigilance, Keyword.Extort)

    @Test
    fun titheCollector() = card(
        name = "Tithe Collector",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "2/2",
        text = "Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)",
    ) parsesTo abilities(Keyword.Extort)

    @Test
    fun vaultwingBat() = card(
        name = "Vaultwing Bat",
        cost = "{1}{B}",
        type = "Creature — Bat",
        pt = "1/2",
        text = """
            Flying
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Extort)

    // CR 605.1b: a triggered mana ability, resolving at once
    @Test
    fun ledgerWraith() = card(
        name = "Ledger Wraith",
        cost = "{3}{B}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Whenever you tap a Swamp for mana, add an additional {B}.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        Ability.Triggered(
            Trigger.On(EventPattern.Tap(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Swamp")), you)), forMana = true)),
            steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!)), Players.You))),
        ),
    )

    @Test
    fun collectionsThug() = card(
        name = "Collections Thug",
        cost = "{3}{B}",
        type = "Creature — Human Rogue",
        pt = "3/2",
        text = "Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)",
    ) parsesTo abilities(Keyword.Extort)

    // A counter of any kind (CR 122.1), the kind chosen as it resolves
    @Test
    fun leechThrull() = card(
        name = "Leech Thrull",
        cost = "{B}",
        type = "Creature — Thrull",
        pt = "1/1",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            {T}, Pay 2 life: Remove a counter from target nonland permanent.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        Ability.Activated(
            cost = costOf(CostPart.TapSelf, CostPart.PayLife(n(2))),
            instructions = Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Category(ObjectCategory.Permanent))))),
                steps = listOf(Step.Do(Effect.RemoveCounters(Objects.Target("permanent"), kind = null, amount = n(1)))),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun tithingHound() = card(
        name = "Tithing Hound",
        cost = "{1}{W}{B}",
        type = "Creature — Thrull",
        pt = "2/2",
        text = """
            Flying
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Extort)

    @Test
    fun vaultThrull() = card(
        name = "Vault Thrull",
        cost = "{4}{W}{B}",
        type = "Creature — Thrull",
        pt = "4/4",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Whenever this creature attacks, you may return target artifact, creature, or enchantment card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        Ability.Triggered(
            attacksSelf,
            Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(
                    ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature, ObjectFilter.Type(CardType.Enchantment))),
                    ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                )))),
                steps = listOf(Step.May(listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand)))), Players.You)),
            ),
        ),
    )

    // CR 119.4, 107.1b: any amount from 0 up to your life total; CR 609.3: an opponent with fewer cards reveals them all
    @Test
    fun confessionExtractor() = card(
        name = "Confession Extractor",
        cost = "{3}{W}{B}",
        type = "Creature — Human Cleric",
        pt = "1/3",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            When this creature enters, pay any amount of life. Target opponent reveals that many cards from their hand. You choose one of them and exile it.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        Ability.Triggered(
            entersSelf,
            Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(
                    Step.Choose(Choice.Number(n(0), Value.LifeOf(Players.You)), bind = "life", chooser = Players.You),
                    Step.Pay(Cost(listOf(CostPart.PayLife(Value.Bound("life")))), payer = Players.You, mandatory = true, bind = "paid"),
                    Step.Choose(
                        Choice.Objects(
                            ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))),
                            min = Value.Bound("life"),
                            max = Value.Bound("life"),
                        ),
                        bind = "revealed",
                        chooser = Players.Target("opponent"),
                    ),
                    Step.Do(Effect.Reveal(Objects.Bound("revealed"))),
                    Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile))),
                ),
            ),
        ),
    )
}
