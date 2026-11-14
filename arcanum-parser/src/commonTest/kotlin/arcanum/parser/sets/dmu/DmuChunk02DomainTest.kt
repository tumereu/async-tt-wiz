package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 02: domain, "the number of basic land types among lands you control" (CR 305.6): counted for each, as a where-X
 * value, in a characteristic-defining ability (CR 604.3), in cost reductions (CR 601.2f), compared with a mana value or a toughness, and
 * "there are five basic land types"; looking at the top X cards and dealing with them (CR 401.4); an opponent separating piles.
 */
class DmuChunk02DomainTest {
    private val isSelf = ObjectFilter.Is(Objects.Self)
    private val domain = Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.BasicLandType)
    private fun domainLess(n: Int) = CostChange.Less(ManaCost.parse("{$n}")!!, domain)
    private fun Ability.domain(): Ability = when (this) {
        is Ability.Static -> copy(abilityWord = "Domain")
        is Ability.Triggered -> copy(abilityWord = "Domain")
        is Ability.Activated -> copy(abilityWord = "Domain")
        is Ability.Spell -> copy(abilityWord = "Domain")
        else -> error("no ability word on $this")
    }
    private val seen = Objects.Bound("seen")
    private val kept = Objects.Bound("kept")
    private fun lookAtTopX() = doing(Effect.LookAt(Objects.LibraryCards(Players.You, domain, fromTop = true), bind = "seen", players = Players.You))
    private fun choose(min: Int, max: Int) =
        Step.Choose(Choice.Objects(ObjectFilter.Any, min = n(min), max = n(max), from = seen), bind = "kept", chooser = Players.You)
    private val restOnBottom = doing(Effect.Move(Objects.Except(seen, kept), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))
    private val keptOnTop = doing(Effect.Move(kept, Destination(Zone.Library, libraryPosition = 0)))

    // CR 603.4 (intervening "if you cast it"), CR 305.6
    @Test
    fun gravecallerThornback() = card(
        name = "Gravecaller Thornback",
        cost = "{4}{B}{G}",
        type = "Legendary Creature — Troll Shaman",
        pt = "4/4",
        text = "Domain — When Gravecaller Thornback enters, if you cast it, choose target creature card in your graveyard. Return that card to the battlefield if its mana value is less than or equal to the number of basic land types among lands you control. Otherwise, put it into your hand.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.If(Condition.ObjectMatches(target("card"), ObjectFilter.ManaValue(Comparison.AtMost, domain)),
                then = listOf(move(target("card"), Zone.Battlefield)),
                otherwise = listOf(move(target("card"), Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(creature))),
            interveningIf = Condition.CastFrom(null, Objects.Self)).domain(),
    )

    // CR 305.6, 608.2h (counted once, as it resolves)
    @Test
    fun rootlandVigor() = card(
        name = "Rootland Vigor",
        cost = "{G}",
        type = "Instant",
        text = "Domain — Target creature gets +1/+1 until end of turn for each basic land type among lands you control.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(domain, domain)), targets = listOf(slot("creature", creature))).domain(),
    )

    // CR 305.6, 111.1
    @Test
    fun beastfoldExodus() = card(
        name = "Beastfold Exodus",
        cost = "{6}{G}",
        type = "Sorcery",
        text = """
            Domain — Create a 3/3 green Beast creature token for each basic land type among lands you control.
            {1}{G}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle. You gain 3 life.
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Beast", listOf(Color.Green), 3, 3), domain)).domain(),
        activated(cost(mana("{1}{G}"), CostPart.Discard(CostObjects.Self)),
            search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, gainLife(3)),
    )

    // CR 601.2f (an activation cost reduced)
    @Test
    fun wanderersAtlasTome() = card(
        name = "Wanderer's Atlas Tome",
        cost = "{5}",
        type = "Artifact — Book",
        text = "Domain — {5}, {T}: Draw a card. This ability costs {1} less to activate for each basic land type among lands you control.",
    ) parsesTo abilities(
        activated(cost(mana("{5}"), tap), draw(1), rules = anyTime.copy(costChange = domainLess(1))).domain(),
    )

    // CR 614.1a (a granted replacement effect), CR 601.2f
    @Test
    fun canopyWebstalker() = card(
        name = "Canopy Webstalker",
        cost = "{2}{G}",
        type = "Creature — Spider",
        pt = "4/3",
        text = """
            Reach, trample
            Domain — {7}{G}: Return this card from your graveyard to the battlefield tapped. It gains "If this permanent would leave the battlefield, exile it instead of putting it anywhere else." This ability costs {1} less to activate for each basic land type among lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        activated(cost(mana("{7}{G}")),
            move(Objects.Self, Zone.Battlefield, tapped = true, postBind = "returned"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, notTo = Zone.Exile),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))))),
                Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            rules = anyTime.copy(costChange = domainLess(1))).domain(),
    )

    // CR 401.4, 305.6 ("five basic land types": all of them)
    @Test
    fun ilsaCloudsailScout() = card(
        name = "Ilsa, Cloudsail Scout",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Elf Scout",
        pt = "2/4",
        text = """
            Flying
            Domain — Whenever Ilsa deals combat damage to a player, look at the top X cards of your library, where X is the number of basic land types among lands you control. Put up to one of them on top of your library and the rest on the bottom in a random order. Then if there are five basic land types among lands you control, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, lookAtTopX(), choose(0, 1), keptOnTop, restOnBottom,
            Step.If(Condition.Compare(domain, Comparison.Equal, n(5)), then = listOf(draw(1)))).domain(),
    )

    // CR 115.1 (the target's power compared with this creature's), CR 601.2f, 602.5b
    @Test
    fun emberlanceSkirmisher() = card(
        name = "Emberlance Skirmisher",
        cost = "{1}{R}",
        type = "Creature — Human Warrior",
        pt = "3/1",
        text = """
            Whenever this creature attacks, target creature defending player controls with power less than this creature's power can't block this turn.
            Domain — {5}{R}: This creature gets +2/+2 until end of turn. This ability costs {1} less to activate for each basic land type among lands you control. Activate only once each turn.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.CantBlock(attackers = ObjectFilter.Any)),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))),
                ObjectFilter.Power(Comparison.Less, power(Objects.Self)))))),
        activated(cost(mana("{5}{R}")), pump(Objects.Self, 2, 2), rules = anyTime.copy(perTurn = 1, costChange = domainLess(1))).domain(),
    )

    // CR 401.4
    @Test
    fun gloomsightOmen() = card(
        name = "Gloomsight Omen",
        cost = "{2}{B}",
        type = "Instant",
        text = "Domain — Look at the top X cards of your library, where X is the number of basic land types among lands you control. Put up to two of them into your hand and the rest into your graveyard. You lose 2 life.",
    ) parsesTo abilities(
        spell(lookAtTopX(), choose(0, 2), doing(Effect.Move(kept, Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(seen, kept), Destination(Zone.Graveyard))), loseLife(2)).domain(),
    )

    // CR 701.23b (up to two), 305.6
    @Test
    fun mossboundExpedition() = card(
        name = "Mossbound Expedition",
        cost = "{4}{G}",
        type = "Sorcery",
        text = "Domain — Search your library for up to two land cards that each have a basic land type, put them onto the battlefield tapped, then shuffle. Look at the top X cards of your library, where X is the number of basic land types among lands you control. Put up to one of them on top of your library and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Search(Players.You, and(land, or(sub("Plains"), sub("Island"), sub("Swamp"), sub("Mountain"), sub("Forest"))), max = n(2),
                zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
            lookAtTopX(), choose(0, 1), keptOnTop, restOnBottom).domain(),
    )

    // CR 701.20 (reveal), an opponent of your choice separates the piles
    @Test
    fun sphinxOfPartedFates() = card(
        name = "Sphinx of Parted Fates",
        cost = "{3}{U}{U}",
        type = "Creature — Sphinx",
        pt = "5/5",
        text = """
            Flying, ward {2}
            Domain — Whenever this creature deals combat damage to a player, reveal the top X cards of your library, where X is the number of basic land types among lands you control. An opponent separates those cards into two piles. Put one pile into your hand and the other into your graveyard. (Piles can be empty.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(manaCost("{2}")),
        triggered(combatDamageToAPlayer,
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, domain, fromTop = true), bind = "seen")),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.SeparatePiles(seen, separator = Players.Bound("opponent"), chooser = Players.You, chosenBind = "chosen pile", otherBind = "other pile")),
            move(Objects.Bound("chosen pile"), Zone.Hand),
            move(Objects.Bound("other pile"), Zone.Graveyard)).domain(),
    )

    // CR 611.2c (counted as it resolves), 602.5b
    @Test
    fun sunsoakBasker() = card(
        name = "Sunsoak Basker",
        cost = "{1}{G}",
        type = "Creature — Lizard",
        pt = "2/2",
        text = "Domain — {3}{G}: Until end of turn, this creature gets +1/+1 for each basic land type among lands you control. Activate only once each turn.",
    ) parsesTo abilities(
        activated(cost(mana("{3}{G}")), untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(domain, domain)),
            rules = anyTime.copy(perTurn = 1)).domain(),
    )

    // CR 604.3 (characteristic-defining)
    @Test
    fun realmboundBehemoth() = card(
        name = "Realmbound Behemoth",
        cost = "{4}{G}",
        type = "Creature — Elemental",
        pt = "*/*",
        text = "Domain — Realmbound Behemoth's power and toughness are each equal to twice the number of basic land types among lands you control.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(Value.Times(domain, 2), Value.Times(domain, 2)), characteristicDefining = true).domain(),
    )

    // CR 401.4
    @Test
    fun tidepoolGleaner() = card(
        name = "Tidepool Gleaner",
        cost = "{2}{U}",
        type = "Creature — Merfolk Rogue",
        pt = "3/2",
        text = "Domain — When this creature enters, look at the top X cards of your library, where X is the number of basic land types among lands you control. You may put one of those cards on top of your library. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(entersSelf, lookAtTopX(), choose(0, 1), keptOnTop, restOnBottom).domain(),
    )

    // CR 305.6 (compared as it resolves)
    @Test
    fun varoTessinPrideMarshal() = card(
        name = "Varo Tessin, Pride Marshal",
        cost = "{3}{G}{W}",
        type = "Legendary Creature — Cat Warrior",
        pt = "4/4",
        text = "Domain — Whenever Varo Tessin becomes tapped, put a +1/+1 counter on each creature you control with toughness less than the number of basic land types among lands you control.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(objects = isSelf)),
            counters(Objects.Each(and(creature, you, ObjectFilter.Toughness(Comparison.Less, domain))))).domain(),
    )

    // CR 305.6
    @Test
    fun siegeBatteryVolley() = card(
        name = "Siege Battery Volley",
        cost = "{1}{W}",
        type = "Instant",
        text = "Domain — Siege Battery Volley deals X damage to target tapped creature, where X is 1 plus the number of basic land types among lands you control.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(n(1), domain)), toObjects(target("creature"))), targets = listOf(slot("creature", and(tapped, creature)))).domain(),
    )

    // CR 510.1, 122.1
    @Test
    fun bramblecoilHydra() = card(
        name = "Bramblecoil Hydra",
        cost = "{5}{G}",
        type = "Creature — Plant Hydra",
        pt = "6/6",
        text = """
            Trample
            Domain — Whenever this creature deals combat damage to a player, put X +1/+1 counters on target creature you control, where X is the number of basic land types among lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(combatDamageToAPlayer, counters(target("creature"), domain), targets = listOf(slot("creature", creatureYouControl))).domain(),
    )

    // CR 611.2c (the creatures fixed as it resolves)
    @Test
    fun undertowOfRuin() = card(
        name = "Undertow of Ruin",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = "Domain — Each creature gets -X/-X until end of turn, where X is 1 plus the number of basic land types among lands you control.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Each(creature), Mechanic.ModifyPowerToughness(Value.Times(Value.Sum(listOf(n(1), domain)), -1),
            Value.Times(Value.Sum(listOf(n(1), domain)), -1)))).domain(),
    )

    // CR 601.2f, 610.3
    @Test
    fun leyTetherSeal() = card(
        name = "Ley Tether Seal",
        cost = "{5}{W}",
        type = "Enchantment",
        text = """
            Flash
            Domain — This spell costs {1} less to cast for each basic land type among lands you control.
            When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.CostChange(isSelf, domainLess(1))).domain(),
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
    )

    // CR 120.3, 305.6
    @Test
    fun thornwoodOutranger() = card(
        name = "Thornwood Outranger",
        cost = "{4}{R}",
        type = "Creature — Elf Archer",
        pt = "4/4",
        text = """
            Reach
            Domain — When this creature enters, it deals damage to each opponent equal to the number of basic land types among lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, damage(domain, eachOpponent)).domain(),
    )

    // CR 604.3
    @Test
    fun plainsprowlBrawler() = card(
        name = "Plainsprowl Brawler",
        cost = "{1}{G}",
        type = "Creature — Cat Warrior",
        pt = "*/3",
        text = """
            Trample
            Domain — Plainsprowl Brawler's power is equal to the number of basic land types among lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.SetPowerToughness(domain, null), characteristicDefining = true).domain(),
    )

    // CR 701.26 (becomes tapped), 608.2h
    @Test
    fun kazraAlliedWarchief() = card(
        name = "Kazra, Allied Warchief",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Elf Warrior",
        pt = "3/3",
        text = "Domain — Whenever Kazra becomes tapped, another target creature you control gets +X/+X until end of turn, where X is the number of basic land types among lands you control.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(objects = isSelf)), untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(domain, domain)),
            targets = listOf(slot("creature", and(not(isSelf), creature, you)))).domain(),
    )

    // CR 714.3a (read ahead), 714.2
    @Test
    fun theSeedwindAccord() = card(
        name = "The Seedwind Accord",
        cost = "{2}{G}",
        type = "Enchantment — Saga",
        text = """
            Read ahead (Choose a chapter and start with that many lore counters. Add one after your draw step. Skipped chapters don't trigger. Sacrifice after III.)
            I — Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            II — Create a 1/1 green Saproling creature token.
            III — Domain — Target creature you control gets +X/+X and gains trample until end of turn, where X is the number of basic land types among lands you control.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapter(1, search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        chapter(2, create(creatureToken("Saproling", listOf(Color.Green), 1, 1))),
        chapter(3, untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(domain, domain), has(Keyword.Trample)),
            targets = listOf(slot("creature", creatureYouControl))).domain(),
    )

    // CR 601.2f
    @Test
    fun elderbarkWanderer() = card(
        name = "Elderbark Wanderer",
        cost = "{7}{G}",
        type = "Creature — Treefolk",
        pt = "4/6",
        text = "Domain — This spell costs {1} less to cast for each basic land type among lands you control.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, domainLess(1))).domain(),
    )

    private fun chapter(number: Int, vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) =
        Ability.Triggered(Trigger.Chapter(listOf(number)), arcanum.api.Instructions(targets = targets, steps = steps.toList()))
}
