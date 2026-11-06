package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 13: multicoloured cards, third part. A ward cost of discarding (CR 702.21a), a reflexive trigger on cards
 * milled with a copy of an exiled card cast free (CR 707.12), piles separated by an opponent (CR 700.3), a creature card with lesser power
 * put onto the battlefield attacking, Equipment attached by a trigger and counted, opponents' lands' activated abilities stopped and
 * gained (CR 113.6, 605), a copy token that's a Food artifact (CR 707.9b), revealing until a land put under your control, lore counters
 * among Sagas and a final chapter ability resolving (CR 714.2), and a creature's dying subtypes as it last existed.
 */
class LtrChunk13MulticoloredTest {
    // CR 707.12: a copy of an exiled card cast without paying its mana cost
    @Test
    fun curumoOfManyColors() = card(
        name = "Curumo of Many Colors",
        cost = "{3}{W}{U}{B}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Ward—Discard an enchantment, instant, or sorcery card.
            Whenever you cast your second spell each turn, each opponent mills two cards. When one or more cards are milled this way, exile target enchantment, instant, or sorcery card with equal or lesser mana value than that spell from an opponent's graveyard. Copy the exiled card. You may cast the copy without paying its mana cost.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Ward(cost(CostPart.Discard(CostObjects.Choose(or(enchantment, instant, sorcery), count = n(1))))),
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            mill(2, Players.Each(PlayerFilter.Opponent), postBind = "milled"),
            Step.If(Condition.Compare(Value.Count(Objects.Bound("milled")), Comparison.AtLeast, n(1)), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("card", and(or(enchantment, instant, sorcery), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single))))),
                steps = listOf(move(target("card"), Zone.Exile, postBind = "exiled"),
                    doing(Effect.CopyCard(Objects.Bound("exiled"), Players.You, "copy")),
                    Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You)))))))),
    )

    // CR 701.54a; amass on an opponent's spell; an Army's combat damage
    @Test
    fun maironTheDarkLord() = card(
        name = "Mairon, the Dark Lord",
        cost = "{3}{U}{B}{R}",
        type = "Legendary Creature — Avatar Horror",
        text = """
            Ward—Sacrifice a legendary artifact or legendary creature.
            Whenever an opponent casts a spell, amass Orcs 1.
            Whenever an Army you control deals combat damage to a player, the Ring tempts you.
            Whenever the Ring tempts you, you may discard your hand. If you do, draw four cards.
        """,
        pt = "7/6",
    ) parsesTo abilities(
        Keyword.Ward(cost(sacrificeA(or(and(legendary, artifact), and(legendary, creature))))),
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)), amassOrcs(1)),
        triggered(on(EventPattern.Damage(and(sub("Army"), you), RecipientFilter(players = PlayerFilter.Any), combat = true)), ringTempts),
        triggered(ringTemptsYou,
            Step.Pay(cost(CostPart.Discard(CostObjects.These(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(4)))),
    )

    // CR 700.3: piles separated by the chosen opponent, one face down
    @Test
    fun theDarkLordsRansom() = card(
        name = "The Dark Lord's Ransom",
        cost = "{1}{U}{B}",
        type = "Instant",
        text = "Choose an opponent. They look at the top four cards of your library and separate them into a face-down pile and a face-up pile. Put one pile into your hand and the other into your graveyard. The Ring tempts you.",
    ) parsesTo abilities(
        spell(Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.SeparatePiles(Objects.LibraryCards(Players.You, n(4), fromTop = true), separator = Players.Bound("opponent"), chooser = Players.You,
                chosenBind = "chosen", otherBind = "other", faceDownPile = true)),
            move(Objects.Bound("chosen"), Zone.Hand), move(Objects.Bound("other"), Zone.Graveyard), ringTempts),
    )

    @Test
    fun shadeSummoning() = card(
        name = "Shade Summoning",
        cost = "{W}{B}",
        type = "Sorcery",
        text = "Create two tapped 1/1 white Spirit creature tokens with flying.",
    ) parsesTo abilities(
        spell(doing(Effect.CreateToken(spiritFlyer, n(2), Players.You, tapped = true))),
    )

    // CR 508.4: a creature card with lesser power put onto the battlefield attacking
    @Test
    fun felarofLordOfHorses() = card(
        name = "Felaróf, Lord of Horses",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — Horse",
        text = """
            Horses you control have haste. (They can attack and {T} as soon as they come under your control.)
            Whenever Felaróf attacks, you may put a creature card with lesser power from your hand onto the battlefield tapped and attacking.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(and(sub("Horse"), you))),
        triggered(attacksSelf, Step.Choose(Choice.Objects(and(creature, ObjectFilter.Power(Comparison.Less, power(Objects.Self)), inZone(Zone.Hand),
            ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true, attacking = arcanum.api.AttackTarget.Chosen)))),
    )

    // CR 701.3a: an Equipment attached; amass by the Equipment attached
    @Test
    fun muzgashLootBearer() = card(
        name = "Muzgash, Loot Bearer",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Orc Soldier",
        text = "Whenever Muzgash attacks, attach up to one target Equipment to it. Then amass Orcs X, where X is the number of Equipment attached to Muzgash. (Control of the Equipment doesn't change. To amass Orcs X, put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(attacksSelf, doing(Effect.Attach(target("Equipment"), Recipients(objects = Objects.Self))),
            amassOrcs(Value.Count(Objects.AttachmentsOf(Objects.Self, equipment))), targets = listOf(slot("Equipment", equipment, min = 0))),
    )

    // CR 605: mana abilities stay; the other activated abilities of opponents' lands are stopped and gained
    @Test
    fun sandymanTyrantOfTheShire() = card(
        name = "Sandyman, Tyrant of the Shire",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Avatar Rogue",
        text = """
            Activated abilities of lands your opponents control can't be activated unless they're mana abilities.
            Sandyman has all activated abilities of lands your opponents control except mana abilities.
            Mana of any type can be spent to activate Sandyman's abilities.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        static(Mechanic.CantBeActivated(manaAbilities = false), affects = Objects.Each(and(land, opponents))),
        static(Mechanic.GainAbilitiesOf(Objects.Each(and(land, opponents)), exceptMana = true)),
        playerStatic(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = ObjectFilter.Is(Objects.Self)))),
    )

    // CR 707.9b: a copy that's only a Food artifact with the Food ability
    @Test
    fun mirkspawnChildOfUngoliant() = card(
        name = "Mirkspawn, Child of Ungoliant",
        cost = "{4}{B}{G}",
        type = "Legendary Creature — Spider Demon",
        text = """
            Deathtouch, ward {2}
            Other Spiders you control have deathtouch and ward {2}.
            Whenever another creature dealt damage this turn by a Spider you controlled dies, create a token that's a copy of that creature, except it's a Food artifact with "{2}, {T}, Sacrifice this token: You gain 3 life," and it loses all other card types.
        """,
        pt = "8/8",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Ward(manaCost("{2}")),
        static(has(Keyword.Deathtouch, Keyword.Ward(manaCost("{2}"))), affects = Objects.Each(and(another, sub("Spider"), you))),
        triggered(on(EventPattern.ZoneChange(and(another, creature, ObjectFilter.ThisTurn(EventPattern.Damage(and(sub("Spider"), you), RecipientFilter()), role = EventRole.Object)),
            from = Zone.Battlefield, to = Zone.Graveyard)),
            create(TokenSpec.CopyOf(Objects.EventObject(EventRole.Before), except = listOf(
                CopyException.Types(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Food"))), replaceTypes = true),
                CopyException.AddAbilities(listOf(activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)))))))),
    )

    // CR 701.20: revealed until a land, which comes under your control
    @Test
    fun deagolHelpfulGuide() = card(
        name = "Déagol, Helpful Guide",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Halfling Horror",
        text = """
            At the beginning of your end step, if a creature died under your control this turn, the Ring tempts you.
            Whenever the Ring tempts you, target opponent reveals cards from the top of their library until they reveal a land card. Put that card onto the battlefield tapped under your control and the rest into their graveyard.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), ringTempts, interveningIf = creatureDiedUnderYourControl),
        triggered(ringTemptsYou,
            doing(Effect.RevealUntil(land, Players.Target("opponent"), n(1), reveal = true, foundBind = "found", restBind = "rest")),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true, controller = Players.You))),
            move(Objects.Bound("rest"), Zone.Graveyard), targets = listOf(targetOpponent)),
    )

    // first strike if its power is 4 or greater after the pump
    @Test
    fun halbaradRangerOfTheNorth() = card(
        name = "Halbarad, Ranger of the North",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Human Ranger",
        text = "Landfall — Whenever a land you control enters, target creature gets +1/+1 until end of turn. Then if that creature has power 4 or greater, it gains first strike until end of turn.",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(landfall, pump(target("creature"), 1, 1),
            Step.If(Condition.ObjectMatches(target("creature"), power(Comparison.AtLeast, 4)), then = listOf(gains(target("creature"), Keyword.FirstStrike))),
            targets = listOf(slot("creature", creature)), abilityWord = "Landfall"),
    )

    @Test
    fun thengelKingOfRohan() = card(
        name = "Thengel, King of Rohan",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Noble",
        text = "Whenever Thengel or another Human you control enters, target creature gains double strike until end of turn.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Human"), you))), gains(target("creature"), Keyword.DoubleStrike),
            targets = listOf(slot("creature", creature))),
    )

    // CR 714.2: lore counters among Sagas; the final chapter ability resolving; once each turn
    @Test
    fun iarwainBenAdar() = card(
        name = "Iarwain Ben-adar",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — God Bard",
        text = """
            As long as there are four or more lore counters among Sagas you control, Iarwain Ben-adar has hexproof and indestructible.
            Whenever the final chapter ability of a Saga you control resolves, reveal cards from the top of your library until you reveal a Saga card. Put that card onto the battlefield and the rest on the bottom of your library in a random order. This ability triggers only once each turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(has(Keyword.Hexproof, Keyword.Indestructible), condition = Condition.Compare(
            Value.Of(NumericProperty.Counters(CounterKind.Lore), Objects.Each(and(sub("Saga"), you)), Combine.Total), Comparison.AtLeast, n(4))),
        triggered(on(EventPattern.FinalChapterResolves(and(sub("Saga"), you))),
            doing(Effect.RevealUntil(sub("Saga"), Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Battlefield),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))), perTurn = 1),
    )

    // CR 603.10a: the creature's subtypes as it last existed
    @Test
    fun lurtzOfTheWhiteHand() = card(
        name = "Lurtz of the White Hand",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Orc Soldier",
        text = "Whenever another creature you control dies, put a +1/+1 counter on Lurtz. If that creature was a Goblin or Orc, put two +1/+1 counters on Lurtz instead.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.If(Condition.ObjectMatches(Objects.EventObject(EventRole.Before), goblinOrOrc), then = listOf(counters(Objects.Self, 2)), otherwise = listOf(counters(Objects.Self)))),
    )

    @Test
    fun berenAndLuthienWed() = card(
        name = "Beren and Lúthien, Wed",
        cost = "{4}{G}{W}",
        type = "Legendary Creature — Human Elf Noble",
        text = """
            Vigilance
            Whenever Beren and Lúthien enters or attacks, put a +1/+1 counter on each other creature you control. You gain 1 life for each other creature you control.
        """,
        pt = "3/6",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersOrAttacksSelf, counters(Objects.Each(and(another, creature, you))), Step.Do(Effect.GainLife(Players.You, count(and(another, creature, you))))),
    )

    @Test
    fun gorthaurTheLidlessEye() = card(
        name = "Gorthaur, the Lidless Eye",
        cost = "{3}{B}{R}",
        type = "Legendary Creature — Avatar Horror",
        text = """
            When Gorthaur enters, gain control of target creature an opponent controls until end of turn. Untap it. It gains haste until end of turn.
            {1}{B}{R}: Creatures you control get +2/+0 until end of turn. Each opponent loses 2 life.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersSelf, untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)), untapIt(target("creature")), gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", creatureOpponentControls))),
        activated(cost(mana("{1}{B}{R}")), pump(Objects.Each(creatureYouControl), 2, 0), loseLife(2, Players.Each(PlayerFilter.Opponent))),
    )

    @Test
    fun theFireDemonFlameOfUdun() = card(
        name = "The Fire Demon, Flame of Udûn",
        cost = "{3}{B}{R}",
        type = "Legendary Creature — Avatar Demon",
        text = """
            Trample
            When a legendary creature an opponent controls dies, put The Fire Demon on the bottom of its owner's library.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.ZoneChange(and(legendary, creature, opponents), from = Zone.Battlefield, to = Zone.Graveyard)), move(Objects.Self, Zone.Library, position = -1)),
    )

    @Suppress("unused") private val unused = listOf(Affected.Players(Players.You), Ability.Unparsed(""))
}
