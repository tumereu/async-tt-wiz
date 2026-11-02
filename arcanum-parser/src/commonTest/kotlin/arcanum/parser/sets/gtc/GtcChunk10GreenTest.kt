package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 10: green. An Aura with hexproof and a blocking limit (CR 509.1b), a token copy of itself on combat
 * damage (CR 707.2), untapping a Gate, an X/X token with X counted as it resolves (CR 608.2h), life from power plus
 * toughness, a trigger on becoming blocked aimed at the defending player's permanents (CR 506.2), cards from a single
 * graveyard shuffled away with the spell (CR 601.2c), evasion limited to flying and reach, a target for each opponent and
 * a search for the number destroyed, and an additional mana of any colour from an enchanted land (CR 605.1b).
 */
class GtcChunk10GreenTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private val enchanted = Objects.AttachedTo(Objects.Self)

    // CR 509.1b: no more than one creature can block it
    @Test
    fun packAuthority() = card(
        name = "Pack Authority",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has hexproof and can't be blocked by more than one creature.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Hexproof)), Mechanic.MaximumBlockers(1)), affects = Affected.Objects(enchanted)),
    )

    @Test
    fun discipleOfTheFirstbornWays() = card(
        name = "Disciple of the Firstborn Ways",
        cost = "{1}{G}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = "{R}: This creature gains first strike until end of turn.",
    ) parsesTo abilities(Ability.Activated(mana("{R}"), steps(gains(Objects.Self, Keyword.FirstStrike)), rules = anyTime))

    // CR 707.2: the token copies the copiable values
    @Test
    fun colossalAdephage() = card(
        name = "Colossal Adephage",
        cost = "{5}{G}{G}",
        type = "Creature — Insect",
        pt = "7/7",
        text = """
            Trample
            Whenever this creature deals combat damage to a player, create a token that's a copy of this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(combatDamageToPlayer, steps(Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You)))),
    )

    @Test
    fun gatesideWatcher() = card(
        name = "Gateside Watcher",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        pt = "2/1",
        text = "{T}: Untap target Gate.",
    ) parsesTo abilities(
        Ability.Activated(costOf(CostPart.TapSelf), Instructions(targets = listOf(slot("Gate", ObjectFilter.Sub(Subtype("Gate")))),
            steps = listOf(Step.Do(Effect.Untap(Objects.Target("Gate"))))), rules = anyTime),
    )

    @Test
    fun ivyRowDenizen() = card(
        name = "Ivy Row Denizen",
        cost = "{3}{G}",
        type = "Creature — Elf Warrior",
        pt = "2/3",
        text = "Whenever another green creature you control enters, put a +1/+1 counter on target creature.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.OfColor(Color.Green), creature, you)),
                to = Zone.Battlefield)),
            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), p1p1, n(1))))),
        ),
    )

    // CR 608.2h, 707.2: X is counted as it resolves and is part of the token's copiable values
    @Test
    fun mimicSlime() {
        val greatest = Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)
        card(
            name = "Mimic Slime",
            cost = "{2}{G}",
            type = "Sorcery",
            text = "Create an X/X green Ooze creature token, where X is the greatest power among creatures you control.",
        ) parsesTo abilities(
            spell(Step.Do(Effect.CreateToken(
                TokenSpec.Defined(arcanum.api.FaceDefinition("Ooze Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Ooze")), power = PrintedNumber.X, toughness = PrintedNumber.X), power = greatest, toughness = greatest),
                n(1), Players.You,
            ))),
        )
    }

    @Test
    fun returnToNaturesWay() = card(
        name = "Return to Nature's Way",
        cost = "{1}{G}",
        type = "Instant",
        text = "Destroy target artifact or enchantment.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("permanent"))),
            targets = listOf(slot("permanent", ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment))))),
        ),
    )

    @Test
    fun predatorsBond() = card(
        name = "Predator's Bond",
        cost = "{2}{G}",
        type = "Instant",
        text = "Choose target creature you control. You gain life equal to that creature's power plus its toughness.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.GainLife(Players.You, Value.Sum(listOf(power(Objects.Target("creature")), toughness(Objects.Target("creature")))))),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    // CR 506.2: the player this creature attacks; the target is chosen as it triggers even with "you may" (CR 603.3d)
    @Test
    fun corrosionScarab() = card(
        name = "Corrosion Scarab",
        cost = "{4}{G}",
        type = "Creature — Insect",
        pt = "4/5",
        text = "Whenever this creature becomes blocked, you may destroy target artifact or enchantment defending player controls.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
            Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(
                    ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment))),
                    ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))),
                )))),
                steps = listOf(Step.May(listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))), Players.You)),
            ),
        ),
    )

    // CR 601.2c: the targets are cards in one graveyard; the spell itself goes into its owner's library too
    @Test
    fun sereneRecollection() {
        val all = Objects.Union(listOf(Objects.Self, Objects.Target("cards")))
        card(
            name = "Serene Recollection",
            cost = "{G}",
            type = "Sorcery",
            text = "Shuffle Serene Recollection and up to three target cards from a single graveyard into their owners' libraries.",
        ) parsesTo abilities(
            spell(
                Step.Do(Effect.Move(all, Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(Players.OwnerOf(all))),
                targets = listOf(TargetSlot("cards", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard)))),
                    min = n(0), max = n(3), sameOwner = true)),
            ),
        )
    }

    @Test
    fun spireStalker() = card(
        name = "Spire Stalker",
        cost = "{G}",
        type = "Creature — Elf Scout",
        pt = "1/1",
        text = "This creature can't be blocked except by creatures with flying or reach.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.And(listOf(creature, ObjectFilter.Or(listOf(ObjectFilter.HasKeyword(Keyword.Flying),
            ObjectFilter.HasKeyword(Keyword.Reach))))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 601.2c: one target for each opponent; X counts the permanents actually destroyed
    @Test
    fun verdantPrimordial() = card(
        name = "Verdant Primordial",
        cost = "{5}{G}{G}",
        type = "Creature — Avatar",
        pt = "6/8",
        text = """
            Reach
            When this creature enters, for each opponent, destroy target noncreature permanent that player controls. Search your library for up to X Forest cards, where X is the number of permanents destroyed this way, put those cards onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        whenThisEnters(
            Step.Do(Effect.Destroy(Objects.Target("permanent"), preBind = "destroyed")),
            Step.Do(Effect.Search(Players.You, ObjectFilter.Sub(Subtype("Forest")), max = Value.Count(Objects.Bound("destroyed")), zones = listOf(Zone.Library),
                bind = "found", searcher = Players.You)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
            Step.Do(Effect.Shuffle(Players.You)),
            targets = listOf(TargetSlot("permanent", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Permanent),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer))))), min = n(1), max = n(1), perPlayer = PlayerFilter.Opponent)),
        ),
    )

    @Test
    fun towerBulwark() = card(
        name = "Tower Bulwark",
        cost = "{1}{G}",
        type = "Instant",
        text = "Creatures you control get +0/+5 and gain reach until end of turn.",
    ) parsesTo abilities(spell(pump(Objects.Each(creatureYouControl), 0, 5, Mechanic.GainAbilities(listOf(Keyword.Reach)))))

    // CR 605.1b: a triggered mana ability; the land's controller gets the mana
    @Test
    fun lushHaven() = card(
        name = "Lush Haven",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, you gain 2 life.
            Whenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        whenThisEnters(Step.Do(Effect.GainLife(Players.You, n(2)))),
        Ability.Triggered(
            Trigger.On(EventPattern.Tap(ObjectFilter.Is(enchanted), forMana = true)),
            steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
        ),
    )

    @Test
    fun wildwoodRenewal() = card(
        name = "Wildwood Renewal",
        cost = "{1}{G}",
        type = "Instant",
        text = "Return target creature card from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))),
            targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
        ),
    )
}
