package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.CopyException
import arcanum.api.CostPart
import arcanum.api.CostObjects
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Players
import arcanum.api.PlayerFilter
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.api.Value
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.ActivationRules
import arcanum.api.Timing
import arcanum.api.Destination
import arcanum.api.ObjectFilter
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** The FDN cards with changeling, flashback, landfall and prowess (chunk 22). The invented names carry the oracle text of the real cards. */
class FdnChunk22ChangelingFlashbackLandfallProwessTest {
    private val oncePerTurn = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)
    private fun tokenCopyOfTargetYouControl(vararg except: CopyException) =
        create(TokenSpec.CopyOf(target("creature"), except.toList()))
    private val creatureYouControlSlot = slot("creature", and(creature, you))
    private val prowess = Keyword.Prowess

    // CR 702.73a (changeling), 603.1 (triggered), 601.2 (cast), 122.1
    @Test
    fun taureanMauler() = card(
        name = "Tauric Mangler",
        cost = "{2}{R}",
        type = "Creature — Shapeshifter",
        pt = "2/2",
        text = """
            Changeling (This card is every creature type.)
            Whenever an opponent casts a spell, you may put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)),
            Step.May(listOf(counters(Objects.Self)), player = Players.You)),
    )

    // CR 702.73a, 605.1a (a mana ability), 602.5b (activate only once each turn); "any color" is ManaKind.Choose with no colors listed
    @Test
    fun threeTreeMascot() = card(
        name = "Three Branch Mascot",
        cost = "{2}",
        type = "Artifact Creature — Shapeshifter",
        pt = "2/1",
        text = """
            Changeling (This card is every creature type.)
            {1}: Add one mana of any color. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        activated(cost(mana("{1}")), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)), rules = oncePerTurn),
    )

    // CR 707.2 (copy token), 707.9b (exceptions), 702.34a (flashback), 603.7 (delayed "at the beginning of the end step")
    @Test
    fun electroduplicate() = card(
        name = "Electrocopy",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Create a token that's a copy of target creature you control, except it has haste and "At the beginning of the end step, sacrifice this token."
            Flashback {2}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            tokenCopyOfTargetYouControl(CopyException.AddAbilities(listOf(Keyword.Haste,
                triggered(stepBegins(TurnStep.End, PlayerFilter.Any), sacrificeIt)))),
            targets = listOf(creatureYouControlSlot)),
        Keyword.Flashback(manaCost("{2}{R}{R}")),
    )

    // CR 701.17a (mill), 400.7, 702.34a
    @Test
    fun inspirationFromBeyond() = card(
        name = "Insight from Afar",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Mill three cards, then return an instant or sorcery card from your graveyard to your hand.
            Flashback {5}{U}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            mill(3),
            Step.Choose(arcanum.api.Choice.Objects(inYourGraveyard(or(instant, sorcery)), n(1), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        Keyword.Flashback(manaCost("{5}{U}{U}")),
    )

    // CR 701.23a (search), 702.34a; "an instant card or a card with flash" is instant OR has flash
    @Test
    fun mysticalTeachings() = card(
        name = "Arcane Tutelage",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            Search your library for an instant card or a card with flash, reveal it, put it into your hand, then shuffle.
            Flashback {5}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Search(library = Players.You, filter = or(instant, and(cardFilter, kw(Keyword.Flash.kind))), reveal = true, bind = "found",
                max = n(1), zones = listOf(Zone.Library), searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle),
        Keyword.Flashback(manaCost("{5}{B}")),
    )

    // CR 111.1 (token), 110.5b (tapped), 702.34a
    @Test
    fun revengeOfTheRats() = card(
        name = "Vengeance of Mice",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = """
            Create a tapped 1/1 black Rat creature token for each creature card in your graveyard.
            Flashback {2}{B}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.CreateToken(creatureToken("Rat", listOf(Color.Black), 1, 1),
            count(inYourGraveyard(creature)), Players.You, tapped = true))),
        Keyword.Flashback(manaCost("{2}{B}{B}")),
    )

    // CR 707.2, 702.34a
    @Test
    fun selfReflection() = card(
        name = "Self-Duplication",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Create a token that's a copy of target creature you control.
            Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(tokenCopyOfTargetYouControl(), targets = listOf(creatureYouControlSlot)),
        Keyword.Flashback(manaCost("{3}{U}")),
    )

    // CR 702.34a
    @Test
    fun thinkTwice() = card(
        name = "Ponder Twice",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Draw a card.
            Flashback {2}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(draw(1)), Keyword.Flashback(manaCost("{2}{U}")))

    // CR 702.17a (reach), 207.2c (landfall is an ability word), 603.2, 111.1
    @Test
    fun elfswornGiant() = card(
        name = "Elfbound Giant",
        cost = "{3}{G}{G}",
        type = "Creature — Giant",
        pt = "5/3",
        text = """
            Reach (This creature can block creatures with flying.)
            Landfall — Whenever a land you control enters, create a 1/1 green Elf Warrior creature token.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(landfall, create(creatureToken("Elf Warrior", listOf(Color.Green), 1, 1)), abilityWord = "Landfall"),
    )

    // CR 700.2 (modal triggered ability), 207.2c, 702.20a (vigilance)
    @Test
    fun felidarRetreat() = card(
        name = "Felid Haven",
        cost = "{3}{W}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, choose one —
            • Create a 2/2 white Cat Beast creature token.
            • Put a +1/+1 counter on each creature you control. Those creatures gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(landfall, Instructions(modes = modes(
            mode(create(creatureToken("Cat Beast", listOf(Color.White), 2, 2))),
            mode(counters(Objects.Each(creatureYouControl)), gains(Objects.Each(creatureYouControl), Keyword.Vigilance)),
        )), abilityWord = "Landfall"),
    )

    // CR 122.1g (stun counter), 701.26a (tap), 115.1
    @Test
    fun grapplingKraken() = card(
        name = "Gripping Leviathan",
        cost = "{4}{U}{U}",
        type = "Creature — Kraken",
        pt = "5/6",
        text = """
            Landfall — Whenever a land you control enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        triggered(landfall, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun),
            targets = listOf(slot("creature", creatureOpponentControls)), abilityWord = "Landfall"),
    )

    // CR 601.2 (cast trigger), 111.1, 122.1, 207.2c
    @Test
    fun primevalBounty() = card(
        name = "Primal Largesse",
        cost = "{5}{G}",
        type = "Enchantment",
        text = """
            Whenever you cast a creature spell, create a 3/3 green Beast creature token.
            Whenever you cast a noncreature spell, put three +1/+1 counters on target creature you control.
            Landfall — Whenever a land you control enters, you gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(youCast(creature), create(creatureToken("Beast", listOf(Color.Green), 3, 3))),
        triggered(youCast(noncreatureSpell), counters(target("creature"), 3), targets = listOf(creatureYouControlSlot)),
        triggered(landfall, gainLife(3), abilityWord = "Landfall"),
    )

    // CR 702.19b (trample), 207.2c, 111.1
    @Test
    fun rampagingBaloths() = card(
        name = "Charging Behemoths",
        cost = "{4}{G}{G}",
        type = "Creature — Beast",
        pt = "6/6",
        text = """
            Trample
            Landfall — Whenever a land you control enters, create a 4/4 green Beast creature token.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(landfall, create(creatureToken("Beast", listOf(Color.Green), 4, 4)), abilityWord = "Landfall"),
    )

    // CR 207.2c, 119.1 (gain life), 121.1 (draw)
    @Test
    fun tatyovaBenthicDruid() = card(
        name = "Tatiana, Pond Druid",
        cost = "{3}{G}{U}",
        type = "Legendary Creature — Merfolk Druid",
        pt = "3/3",
        text = "Landfall — Whenever a land you control enters, you gain 1 life and draw a card.",
    ) parsesTo abilities(triggered(landfall, gainLife(1), draw(1), abilityWord = "Landfall"))

    // CR 702.108a (prowess), 510.2/603.2 (combat damage trigger), 122.1, 602.2 (remove-counter cost)
    @Test
    fun drakeHatcher() = card(
        name = "Wyrmling Brooder",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/3",
        text = """
            Vigilance, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever this creature deals combat damage to a player, put that many incubation counters on it.
            Remove three incubation counters from this creature: Create a 2/2 blue Drake creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        prowess,
        triggered(combatDamageToAPlayer, counters(Objects.Self, Value.EventAmount, incubation)),
        activated(cost(CostPart.RemoveCounters(incubation, amount = n(3), from = CostObjects.Self)),
            create(creatureToken("Drake", listOf(Color.Blue), 2, 2, abilities = listOf(Keyword.Flying)))),
    )

    // CR 702.8a (flash), 702.108a
    @Test
    fun elementalistAdept() = card(
        name = "Sparkcaller Novice",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """,
    ) parsesTo abilities(Keyword.Flash, prowess)

    // CR 702.108a, 602.2 (sacrifice cost), 120.3 (damage equal to its power; last known information 608.2h)
    @Test
    fun heartfireImmolator() = card(
        name = "Hearthflame Burner",
        cost = "{1}{R}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            {R}, Sacrifice this creature: It deals damage equal to its power to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        prowess,
        activated(cost(mana("{R}"), sacrificeSelf), damage(power(Objects.Self), toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 702.108a, 701.25a (surveil)
    @Test
    fun lightshellDuo() = card(
        name = "Shellglow Pair",
        cost = "{3}{U}",
        type = "Creature — Rat Otter",
        pt = "3/4",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(prowess, whenThisEnters(Step.Do(Effect.Surveil(Players.You, n(2)))))

    // CR 702.108a, 115.1 (target), 400.3
    @Test
    fun shipwreckDowser() = card(
        name = "Wreckage Diviner",
        cost = "{3}{U}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "3/3",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, return target instant or sorcery card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        prowess,
        whenThisEnters(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
    )

    private val incubation = CounterKind.Named("incubation")
}
