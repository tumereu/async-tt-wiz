package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 14: multicoloured cards, artifacts and lands. Lifelink while you control another Dwarf; looking at four
 * and revealing one (CR 701.20); an Aura returned from the graveyard attached to a target (CR 303.4f); "if you do" after returning
 * up to one permanent; X tapped Treasures by the artifacts your opponents control; mana from a Treasure spent on a spell
 * (CR 106.3, 603.4); the activated abilities of Elf cards in your graveyard (CR 613.1f); dying and returning as an artifact only
 * (CR 611.2e, 603.10a); a discard cost naming a legendary permanent you control; beholding to untap the land found (CR 701.4a);
 * dual taplands with a sacrifice ability; landcycling for Halflings (CR 702.29e).
 */
class HobChunk14MulticoloredArtifactsLandsTest {
    // CR 701.20: a Dwarf or Equipment card revealed and put into the hand
    @Test
    fun darvisRetinue() = card(
        name = "Darvi's Retinue",
        cost = "{R}{W}",
        type = "Creature — Dwarf Warrior",
        text = """
            This creature has lifelink as long as you control another Dwarf.
            When this creature enters, look at the top four cards of your library. You may reveal a Dwarf or Equipment card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(has(Keyword.Lifelink), condition = Condition.Exists(and(another, sub("Dwarf"), you), atLeast = 1)),
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(sub("Dwarf"), equipment), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 303.4f: put onto the battlefield attached, not cast; 113.6m: it works from the graveyard
    @Test
    fun hawksSalvation() = card(
        name = "Hawk's Salvation",
        cost = "{2}{W/U}{W/U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has flying.
            {2}{W/U}{W/U}: Return this card from your graveyard to the battlefield attached to target creature you control with power 1 or less. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        static(pt(2, 2), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
        activated(cost(mana("{2}{W/U}{W/U}")), Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, attachedTo = Recipients(objects = target("creature"))))),
            targets = listOf(slot("creature", and(creature, you, power(Comparison.AtMost, 1)))), rules = asSorcery),
    )

    // CR 608.2c: the counter only if a permanent was returned
    @Test
    fun gloomwoodCaretaker() = card(
        name = "Gloomwood Caretaker",
        cost = "{2}{G/U}",
        type = "Creature — Elf Ranger",
        text = "When this creature enters, return up to one other target permanent you control to its owner's hand. If you do, put a +1/+1 counter on this creature.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("permanent"), Zone.Hand, postBind = "returned"),
            Step.If(Condition.NonEmpty("returned"), then = listOf(counters(Objects.Self))),
            targets = listOf(slot("permanent", and(another, permanentFilter, you), min = 0))),
    )

    @Test
    fun norviSpinnerOfYarns() = card(
        name = "Norvi, Spinner of Yarns",
        cost = "{1}{R/W}",
        type = "Legendary Creature — Dwarf Bard",
        text = "Whenever Norvi attacks, target attacking creature gains first strike until end of turn.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(attacksSelf, gains(target("creature"), Keyword.FirstStrike), targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 106.3, 603.4: the spell's mana as it was spent, from Treasures
    @Test
    fun skargVileWyrm() = card(
        name = "Skarg, Vile Wyrm",
        cost = "{3}{B}{R}",
        type = "Legendary Creature — Dragon",
        text = """
            Flying
            When Skarg enters, create X tapped Treasure tokens, where X is the number of artifacts your opponents control.
            Whenever you cast a spell, if mana from a Treasure was spent to cast it, you draw a card and lose 1 life.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, createTreasure(count(and(artifact, opponents)), tapped = true)),
        triggered(youCast(ObjectFilter.Any), draw(1), loseLife(1), interveningIf = Condition.Compare(
            Value.ManaSpent(of = Objects.EventObject(EventRole.Object), source = sub("Treasure")), Comparison.Greater, n(0))),
    )

    // CR 613.1f: layer 6
    @Test
    fun thalorinTheElfKing() = card(
        name = "Thalorin, the Elf-King",
        cost = "{2}{B}{G}{U}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Thalorin has all activated abilities of all Elf cards in your graveyard.
            Whenever another legendary Elf you control enters, draw two cards, then discard a card.
        """,
        pt = "5/6",
    ) parsesTo abilities(
        static(Mechanic.GainAbilitiesOf(Objects.Each(inYourGraveyard(sub("Elf"))))),
        triggered(enters(and(another, legendary, sub("Elf"), you)), draw(2), discard(1)),
    )

    /** "If they were a creature": as they last existed (CR 603.10a); they return as an artifact only, as they enter (CR 611.2e), so
     * they can't die this way again. */
    // CR 611.2e
    @Test
    fun bobRobAndGob() = card(
        name = "Bob, Rob, and Gob",
        cost = "{3}{B}{G}",
        type = "Legendary Creature — Troll",
        text = """
            {1}, Sacrifice another creature: Draw cards equal to the sacrificed creature's power, then discard a card.
            When Bob, Rob, and Gob die, if they were a creature, return them to the battlefield. They're an artifact. (They're no longer a creature.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(and(another, creature), preBind = "sacrificed")),
            Step.Do(Effect.Draw(Players.You, power(Objects.Bound("sacrificed")))), discard(1)),
        triggered(diesSelf, Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(EventRole.After)),
            entersAs = listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact), replaceTypes = true))))),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), creature)),
    )

    // CR 106.1b
    @Test
    fun titansRock() = card(
        name = "Titan's Rock",
        cost = "{1}",
        type = "Artifact",
        text = """
            When this artifact enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
            {1}, {T}: Add one mana of any color.
            {7}, {T}, Sacrifice this artifact: Destroy target permanent.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{7}"), tap, sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", permanentFilter))),
    )

    // CR 602.2b: a discard cost naming a legendary permanent you control
    @Test
    fun keyToTheBackGate() = card(
        name = "Key to the Back Gate",
        cost = "{1}",
        type = "Artifact",
        text = """
            {2}, {T}: Target creature can't be blocked this turn.
            {1}, {T}, Discard a legendary card with the same name as a legendary permanent you control: Draw two cards.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{1}"), tap, CostPart.Discard(CostObjects.Choose(and(legendary, ObjectFilter.SameNameAs(Objects.Each(and(legendary, you)))), count = n(1)))),
            draw(2)),
    )

    // CR 701.23
    @Test
    fun grandsiresChart() = card(
        name = "Grandsire's Chart",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            When Grandsire's Chart enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {2}, {T}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(cost(mana("{2}"), tap), draw(1), discard(1)),
    )

    // CR 701.4a, 400.7: beholding untaps the land just put onto the battlefield
    @Test
    fun elvenByway() = card(
        name = "Elven Byway",
        cost = null,
        type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. You may behold an Elf. If you do, untap that land. (To behold an Elf, choose an Elf you control or reveal an Elf card from your hand.)",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.PayLife(n(1)), sacrificeSelf),
            search(basicLand()),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true, postBind = "land"),
            shuffle,
            Step.Pay(cost(CostPart.Action(KeywordAction.Behold(sub("Elf")), bind = "beheld")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(untapIt(Objects.Bound("land"))))),
    )

    // CR 305.7
    @Test
    fun elfLordsGalleries() = card(
        name = "Elf-lord's Galleries",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G} or {U}.
            {2}{G}{U}, {T}, Sacrifice this land: Put two +1/+1 counters on target Elf you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("G", "U"),
        activated(cost(mana("{2}{G}{U}"), tap, sacrificeSelf), counters(target("Elf"), 2), targets = listOf(slot("Elf", and(sub("Elf"), you))), rules = asSorcery),
    )

    @Test
    fun goblinBurrowtown() = card(
        name = "Goblin Burrowtown",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B} or {R}.
            {2}{B}{R}, {T}, Sacrifice this land: Put two +1/+1 counters on target Goblin or Orc you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("B", "R"),
        activated(cost(mana("{2}{B}{R}"), tap, sacrificeSelf), counters(target("permanent"), 2), targets = listOf(slot("permanent", and(or(sub("Goblin"), sub("Orc")), you))), rules = asSorcery),
    )

    // CR 702.29e: Halflingcycling
    @Test
    fun halflingBurrow() = card(
        name = "Halfling Burrow",
        cost = null,
        type = "Land",
        text = """
            {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Halflingcycling {4} ({4}, Discard this card: Search your library for a Halfling card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        activated(cost(tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        Keyword.Typecycling(sub("Halfling"), "Halflingcycling", manaCost("{4}")),
    )

    @Test
    fun copperFells() = card(
        name = "Copper Fells",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {R} or {W}.
            {2}{R}{W}, {T}, Sacrifice this land: Put two +1/+1 counters on target Dwarf you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("R", "W"),
        activated(cost(mana("{2}{R}{W}"), tap, sacrificeSelf), counters(target("Dwarf"), 2), targets = listOf(slot("Dwarf", and(sub("Dwarf"), you))), rules = asSorcery),
    )

    @Test
    fun mereTown() = card(
        name = "Mere-town",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
            {2}{W}{U}, {T}, Sacrifice this land: Put two +1/+1 counters on target Human you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("W", "U"),
        activated(cost(mana("{2}{W}{U}"), tap, sacrificeSelf), counters(target("Human"), 2), targets = listOf(slot("Human", and(sub("Human"), you))), rules = asSorcery),
    )

    @Test
    fun gloomwood() = card(
        name = "Gloomwood",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B} or {G}.
            {2}{B}{G}, {T}, Sacrifice this land: Put two +1/+1 counters on target Bear, Spider, or Wolf you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("B", "G"),
        activated(cost(mana("{2}{B}{G}"), tap, sacrificeSelf), counters(target("permanent"), 2), targets = listOf(slot("permanent", and(or(sub("Bear"), sub("Spider"), sub("Wolf")), you))), rules = asSorcery),
    )
}
