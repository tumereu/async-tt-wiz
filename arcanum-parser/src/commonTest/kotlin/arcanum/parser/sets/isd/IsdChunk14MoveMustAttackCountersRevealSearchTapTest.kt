package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 14: returns to hand, an exile-and-return, must-attack, counters from triggers, reveal, search, set power/toughness, tap and untap.
 */
class IsdChunk14MoveMustAttackCountersRevealSearchTapTest {
    private val exiled = Objects.Bound("exiled")
    private val human = sub("Human")

    // CR 503.1a, 400.7: returns a Spirit card from your graveyard to your hand at your upkeep
    @Test
    fun seraphOfPaleWings() = card(
        name = "Seraph of Pale Wings", cost = "{4}{W}", type = "Creature — Angel", pt = "4/4",
        text = """
            Flying
            At the beginning of your upkeep, return target Spirit card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(yourUpkeep, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(sub("Spirit"))))),
    )

    // CR 610.3, 603.6c: the exiled card returns when this leaves (two linked abilities)
    @Test
    fun duskBanisher() = card(
        name = "Dusk Banisher", cost = "{1}{W}{W}", type = "Creature — Human Cleric", pt = "1/3",
        text = """
            When this creature enters, you may exile another target creature.
            When this creature leaves the battlefield, return the exiled card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.May(listOf(move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", exiled))), Players.You),
            targets = listOf(slot("creature", and(another, creature)))),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)),
            move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 700.2: a modal spell, one mode each; the second mode targets two Zombie cards
    @Test
    fun gravecallersHymn() = card(
        name = "Gravecaller's Hymn", cost = "{B}", type = "Sorcery",
        text = """
            Choose one —
            • Return target creature card from your graveyard to your hand.
            • Return two target Zombie cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        modal(
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(sub("Zombie")), min = 2, max = 2))),
        ),
    )

    // CR 602.2, 701.21a: sacrifice as a cost, then exile the enchantment
    @Test
    fun hollowbellFox() = card(
        name = "Hollowbell Fox", cost = "{1}{W}", type = "Creature — Fox", pt = "2/2",
        text = "{1}{W}, Sacrifice this creature: Exile target enchantment.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{W}"), sacrificeSelf), move(target("enchantment"), Zone.Exile), targets = listOf(slot("enchantment", enchantment))),
    )

    // CR 508.1d: attacks each combat if able; CR 122.1, 510.2: counter on combat damage to a player
    @Test
    fun thirstyWhelp() = card(
        name = "Thirsty Whelp", cost = "{1}{R}", type = "Creature — Vampire", pt = "2/1",
        text = """
            This creature attacks each combat if able.
            Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)),
        triggered(combatDamageToAPlayer, counters(Objects.Self)),
    )

    // CR 508.1d: attacks each combat if able; CR 502.3: doesn't untap; CR 701.26b untap
    @Test
    fun ironboundBrute() = card(
        name = "Ironbound Brute", cost = "{4}", type = "Artifact Creature — Juggernaut", pt = "5/5",
        text = """
            This creature attacks each combat if able.
            This creature doesn't untap during your untap step.
            Whenever another creature dies, untap this creature.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.Self)),
        triggered(dies(and(another, creature)), untapIt(Objects.Self)),
    )

    // CR 603.2, 122.1: another Human entering under your control
    @Test
    fun parishVanguard() = card(
        name = "Parish Vanguard", cost = "{W}", type = "Creature — Human Soldier", pt = "1/1",
        text = "Whenever another Human you control enters, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(enters(and(another, human, you)), counters(Objects.Self)),
    )

    // CR 700.4, 608.2c: the replacement reads the chosen creature as the ability resolves
    @Test
    fun oldCathar() = card(
        name = "Old Cathar", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "2/2",
        text = "When this creature dies, put a +1/+1 counter on target creature you control. If that creature is a Human, put two +1/+1 counters on it instead.",
    ) parsesTo abilities(
        triggered(diesSelf,
            Step.If(Condition.ObjectMatches(target("creature"), human), then = listOf(counters(target("creature"), 2)),
                otherwise = listOf(counters(target("creature")))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 510.2, 122.1: two counters on combat damage to a player
    @Test
    fun ashenRaiders() = card(
        name = "Ashen Raiders", cost = "{3}{R}{R}", type = "Creature — Vampire Warrior", pt = "2/2",
        text = """
            Flying
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            Whenever this creature deals combat damage to a player, put two +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(combatDamageToAPlayer, counters(Objects.Self, 2)),
    )

    // CR 700.4: dies; any creature including this one and opponents'
    @Test
    fun mossknot() = card(
        name = "Mossknot", cost = "{2}{G}{G}", type = "Creature — Treefolk", pt = "1/1",
        text = """
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            Whenever a creature dies, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        triggered(dies(creature), counters(Objects.Self)),
    )

    // CR 510.2, 122.1: a Vampire you control deals combat damage; the counter goes on that Vampire
    @Test
    fun ruddyHeir() = card(
        name = "Ruddy Heir", cost = "{2}{R}", type = "Creature — Vampire", pt = "2/2",
        text = "Whenever a Vampire you control deals combat damage to a player, put a +1/+1 counter on it.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(and(sub("Vampire"), you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            counters(Objects.EventObject(EventRole.Source))),
    )

    // CR 700.4: another Human creature dying, whoever controlled it
    @Test
    fun hamletSlayer() = card(
        name = "Hamlet Slayer", cost = "{2}{B}", type = "Creature — Human", pt = "2/2",
        text = "Whenever another Human creature dies, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(dies(and(another, human, creature)), counters(Objects.Self)),
    )

    // CR 701.20a: reveal, land cards to hand and the rest to the graveyard
    @Test
    fun mulchwood() = card(
        name = "Mulchwood", cost = "{1}{G}", type = "Sorcery",
        text = "Reveal the top four cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(4), fromTop = true), bind = "seen")),
            doing(Effect.Move(Objects.Where(Objects.Bound("seen"), land), Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Where(Objects.Bound("seen"), land)), Destination(Zone.Graveyard))),
        ),
    )

    // CR 701.20a, 400.7: the opponent reveals; you choose a nonland card, exiled instead of discarded
    @Test
    fun dreadMurmurs() = card(
        name = "Dread Murmurs", cost = "{2}{B}", type = "Sorcery",
        text = "Target player reveals their hand. You choose a nonland card from it. Exile that card.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            targets = listOf(targetPlayer)),
    )

    // CR 701.23a: search, reveal, put into hand, then shuffle (CR 701.24)
    @Test
    fun pilgrimsCharm() = card(
        name = "Pilgrim's Charm", cost = "{1}", type = "Artifact",
        text = "{1}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeSelf), search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 604.3, 208.2a: a characteristic-defining ability from the creature cards in your graveyard
    @Test
    fun graveWurm() = card(
        name = "Grave Wurm", cost = "{1}{G}", type = "Creature — Wurm", pt = "*/*",
        text = "Grave Wurm's power and toughness are each equal to the number of creature cards in your graveyard.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(graveyardCreatures, graveyardCreatures), characteristicDefining = true),
    )

    // CR 604.3, 208.2a, 111.1: counts creatures you control, tokens included, after the enter trigger resolves
    @Test
    fun spiritChoirMonk() = card(
        name = "Spirit Choir Monk", cost = "{3}{W}{W}", type = "Creature — Human Monk", pt = "*/*",
        text = """
            Vigilance
            Spirit Choir Monk's power and toughness are each equal to the number of creatures you control.
            When this creature enters, create two 1/1 white Spirit creature tokens with flying.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.SetPowerToughness(count(creatureYouControl), count(creatureYouControl)), characteristicDefining = true),
        triggered(entersSelf, create(creatureToken("Spirit", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying)), n(2))),
    )

    // CR 701.26a: tap target creature that isn't a Human
    @Test
    fun pilgrimPriest() = card(
        name = "Pilgrim Priest", cost = "{1}{W}", type = "Creature — Human Cleric", pt = "1/2",
        text = "{1}, {T}: Tap target non-Human creature.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", and(not(human), creature)))),
    )

    // CR 701.26b, 611.2a: untap, then +2/+4 and reach until end of turn
    @Test
    fun quickrootGrasp() = card(
        name = "Quickroot Grasp", cost = "{2}{G}", type = "Instant",
        text = "Untap target creature. It gets +2/+4 and gains reach until end of turn.",
    ) parsesTo abilities(
        spell(untapIt(target("creature")), pump(target("creature"), 2, 4, Mechanic.GainAbilities(listOf(Keyword.Reach))), targets = listOf(slot("creature", creature))),
    )

    // CR 701.26b: untap every creature you control
    @Test
    fun bellWarden() = card(
        name = "Bell Warden", cost = "{2}{W}", type = "Creature — Human Scout", pt = "1/4",
        text = """
            Flash
            When this creature enters, untap all creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, untapIt(Objects.Each(creatureYouControl))),
    )

    private val graveyardCreatures = count(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))

    private fun modal(vararg modes: Mode) = Ability.Spell(Instructions(modes = Modes(options = modes.toList(), min = 1, max = 1, chooser = Players.You)))
}
