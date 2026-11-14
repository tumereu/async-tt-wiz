package arcanum.parser.sets.bro

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CardType
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Brothers' War chunk 01: Powerstone tokens (CR 111.10h), created tapped by triggers, spells and activated abilities, and the
 * cards' other wordings: each player who controls something creating a token, the sacrificed artifact's characteristics, a copy of an
 * artifact that is also a 1/1 Soldier creature.
 */
class BroChunk01PowerstoneTest {
    private val powerstoneReminder = "(It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")"
    private val colorless = ManaKind.Fixed(ManaCost.parse("{C}")!!)

    // CR 111.10h: a tapped Powerstone token
    @Test
    fun thicketOpportunist() = card(
        name = "Thicket Opportunist",
        cost = "{2}{G}",
        type = "Creature — Human Scout",
        text = "When this creature enters, create a tapped Powerstone token. $powerstoneReminder",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, createPowerstone()),
    )

    // CR 111.10h
    @Test
    fun quarryBlast() = card(
        name = "Quarry Blast",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Quarry Blast deals 3 damage to any target. Create a tapped Powerstone token. $powerstoneReminder",
    ) parsesTo abilities(
        spell(damage(3, toAny), createPowerstone(), targets = listOf(anyTarget)),
    )

    // CR 608.2c: which players control an artifact or enchantment is checked as the spell resolves, before the destruction
    @Test
    fun fadedHistories() = card(
        name = "Faded Histories",
        cost = "{2}{G}{G}",
        type = "Sorcery",
        text = "Each player who controls an artifact or enchantment creates a 2/2 green Bear creature token. Then destroy all artifacts and enchantments.",
    ) parsesTo abilities(
        spell(
            doing(Effect.CreateToken(creatureToken("Bear", listOf(Color.Green), 2, 2), n(1), Players.Each(PlayerFilter.Where(Condition.Exists(
                and(or(artifact, enchantment), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))), atLeast = 1))))),
            destroy(Objects.Each(or(artifact, enchantment))),
        ),
    )

    // CR 111.10h: all three tokens enter tapped
    @Test
    fun duneExcavation() = card(
        name = "Dune Excavation",
        cost = "{3}{G}{G}",
        type = "Sorcery",
        text = "Create three tapped Powerstone tokens. You gain 3 life. (The tokens are artifacts with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")",
    ) parsesTo abilities(
        spell(createPowerstone(n(3)), gainLife(3)),
    )

    // CR 513.1a
    @Test
    fun rockhoundScholar() = card(
        name = "Rockhound Scholar",
        cost = "{3}{U}{U}",
        type = "Creature — Human Artificer",
        text = """
            At the beginning of your end step, create a tapped Powerstone token. $powerstoneReminder
            {6}: Draw a card and put a +1/+1 counter on this creature.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        triggered(yourEndStep, createPowerstone()),
        activated(manaCost("{6}"), draw(1), counters(Objects.Self)),
    )

    // CR 701.20a: the opponent reveals; you choose a nonland card
    @Test
    fun cruelCaress() = card(
        name = "Cruel Caress",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
            Create a tapped Powerstone token. $powerstoneReminder
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            createPowerstone(), targets = listOf(targetOpponent)),
    )

    // CR 608.2h: the number of other creatures you control as the trigger resolves
    @Test
    fun sandseaProspector() = card(
        name = "Sandsea Prospector",
        cost = "{4}{W}",
        type = "Creature — Human Artificer",
        text = "When this creature enters, create a tapped Powerstone token for each other creature you control. " +
            "(They're artifacts with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, createPowerstone(count(and(another, creature, you)))),
    )

    // CR 605.1a: the first two are mana abilities
    @Test
    fun hallOfRuinStones() = card(
        name = "Hall of Ruin Stones",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
            {4}, {T}: Create a tapped Powerstone token. $powerstoneReminder
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(colorless), Players.You))),
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{4}"), tap), createPowerstone()),
    )

    // CR 603.6c: a leaves-the-battlefield trigger; sacrificing a Powerstone is a choice as it resolves
    @Test
    fun crestedStoneseeker() = card(
        name = "Crested Stoneseeker",
        cost = "{1}{R}",
        type = "Creature — Lizard",
        text = """
            Menace
            When this creature enters, create a tapped Powerstone token. $powerstoneReminder
            When this creature leaves the battlefield, sacrifice a Powerstone.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, createPowerstone()),
        triggered(Trigger.On(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)),
            Step.Choose(Choice.Objects(and(sub("Powerstone"), you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))),
    )

    // CR 613.4c, 613.1f: +1/+0 and menace and haste to the other creatures you control as it resolves
    @Test
    fun scrapheapGenius() = card(
        name = "Scrapheap Genius",
        cost = "{1}{B}{R}",
        type = "Creature — Human Artificer",
        text = """
            When this creature enters, create a tapped Powerstone token. $powerstoneReminder
            {1}{B}{R}, Sacrifice another creature or artifact: Until end of turn, other creatures you control get +1/+0 and gain menace and haste.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, createPowerstone()),
        activated(cost(mana("{1}{B}{R}"), sacrificeA(and(another, or(creature, artifact)))),
            pump(Objects.Each(and(another, creature, you)), 1, 0, has(Keyword.Menace, Keyword.Haste))),
    )

    // CR 702.8a, 702.9a
    @Test
    fun mesaRoc() = card(
        name = "Mesa Roc",
        cost = "{4}{U}",
        type = "Creature — Bird",
        text = """
            Flash
            Flying
            When this creature enters, create a tapped Powerstone token. $powerstoneReminder
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, createPowerstone()),
    )

    // CR 700.4: dies
    @Test
    fun stoneworkEngineer() = card(
        name = "Stonework Engineer",
        cost = "{1}{W}",
        type = "Creature — Human Artificer",
        text = "When this creature dies, create a tapped Powerstone token. $powerstoneReminder",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(diesSelf, createPowerstone()),
    )

    // CR 115.1
    @Test
    fun mendAndRecharge() = card(
        name = "Mend and Recharge",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Return target artifact, enchantment, or planeswalker card from your graveyard to the battlefield. Create a tapped Powerstone token. $powerstoneReminder",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), createPowerstone(),
            targets = listOf(slot("card", inYourGraveyard(or(artifact, enchantment, ObjectFilter.Type(CardType.Planeswalker)))))),
    )

    // CR 603.2: any land entering, under any player's control
    @Test
    fun basinGreatwurm() = card(
        name = "Basin Greatwurm",
        cost = "{4}{R}{G}",
        type = "Creature — Wurm",
        text = """
            Trample
            Whenever a land enters, create a tapped Powerstone token. $powerstoneReminder
        """,
        pt = "7/6",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(enters(land), createPowerstone()),
    )

    // CR 613.1b: control until end of turn; CR 302.6: haste lets it attack
    @Test
    fun brotherlySpite() = card(
        name = "Brotherly Spite",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Gain control of target artifact or creature until end of turn. Untap it. It gains haste until end of turn. Create a tapped Powerstone token. $powerstoneReminder",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("permanent"), Mechanic.ControlledBy(Players.You)), untapIt(target("permanent")),
            gains(target("permanent"), Keyword.Haste), createPowerstone(), targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 608.2h: the sacrificed artifact as it last existed on the battlefield
    @Test
    fun cleavingTheStone() = card(
        name = "Cleaving the Stone",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact.
            Create two tapped Powerstone tokens. If the sacrificed artifact was legendary, draw a card. (The tokens are artifacts with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(artifact, preBind = "sacrificed")))),
        spell(createPowerstone(n(2)), ifThen(Condition.ObjectMatches(Objects.Bound("sacrificed"), legendary), draw(1))),
    )

    // CR 610.3: exiled until this enchantment leaves the battlefield
    @Test
    fun chargedSnare() = card(
        name = "Charged Snare",
        cost = "{3}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.
            When this enchantment enters, you gain 2 life and create a tapped Powerstone token. $powerstoneReminder
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)), targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
        triggered(entersSelf, gainLife(2), createPowerstone()),
    )

    // CR 121.1
    @Test
    fun harshLesson() = card(
        name = "Harsh Lesson",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw two cards, then discard a card. Create a tapped Powerstone token. $powerstoneReminder",
    ) parsesTo abilities(
        spell(draw(2), discard(1), createPowerstone()),
    )

    // CR 111.10h
    @Test
    fun stoneSalvageUnit() = card(
        name = "Stone Salvage Unit",
        cost = "{4}",
        type = "Artifact Creature — Construct",
        text = "When this creature enters, create a tapped Powerstone token. $powerstoneReminder",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, createPowerstone()),
    )

    // CR 111.2: the opponent's token is created under their control; CR 701.20a: reveal and put into your hand
    @Test
    fun vaultSpider() = card(
        name = "Vault Spider",
        cost = "{3}",
        type = "Artifact Creature — Spider",
        text = """
            Reach
            When this creature enters, you and target opponent each create a tapped Powerstone token.
            {7}: Look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, createPowerstone(), createPowerstone(players = Players.Target("opponent")), targets = listOf(targetOpponent)),
        activated(manaCost("{7}"),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(artifact, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 603.2c: one or more artifact cards discarded at once trigger once; "only once each turn"
    @Test
    fun ozarPowerstoneProdigy() = card(
        name = "Ozar, Powerstone Prodigy",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Artificer",
        text = """
            Vigilance
            {1}, {T}: Draw a card, then discard a card.
            Whenever you discard one or more artifact cards, create a tapped Powerstone token. This ability triggers only once each turn. (The token is an artifact with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(mana("{1}"), tap), draw(1), discard(1)),
        triggered(Trigger.On(EventPattern.Discard(artifact, PlayerFilter.You), oneOrMore = true), createPowerstone(), perTurn = 1),
    )

    // CR 707.9b: the copy is also a 1/1 Soldier creature (and stays legendary if the original is); CR 613.4c: then +2/+2
    @Test
    fun ozarPrinceOfKelm() = card(
        name = "Ozar, Prince of Kelm",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Human Artificer",
        text = """
            Artifact creatures you control get +2/+2.
            {6}: Create a token that's a copy of target artifact you control, except it's a 1/1 Soldier creature in addition to its other types.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        static(pt(2, 2), affects = Objects.Each(and(artifact, creature, you))),
        activated(manaCost("{6}"), doing(Effect.CreateToken(TokenSpec.CopyOf(target("artifact"), except = listOf(
            CopyException.PowerToughness(1, 1),
            CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Soldier")))))), n(1), Players.You, bind = "token")),
            targets = listOf(slot("artifact", and(artifact, you)))),
    )
}
