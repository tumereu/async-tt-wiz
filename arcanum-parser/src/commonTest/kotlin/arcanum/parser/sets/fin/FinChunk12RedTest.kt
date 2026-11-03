package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
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
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 12: red cards: damage counted from equipped creatures and Equipment, copies of Equipment that equip for less
 * (CR 707.9b), mana only for Equipment, Equipment put onto the battlefield from the top of the library and attached by a reflexive trigger
 * (CR 603.12), the top card exiled to play until your next end step, Equipment destroyed along with a creature, milled creatures put onto
 * the battlefield and returned at the next end step, ward paid in life (CR 702.21a), a creature dealing damage to another target and to
 * itself, graveyard or flashback exile returns, each player's top card played for free by mana value, borrowed creatures with an Equipment
 * that comes off later, casting cards from an opponent's library, spells you don't own.
 */
class FinChunk12RedTest {
    @Test
    fun barrickWallace() = card(
        name = "Barrick Wallace",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Rebel",
        pt = "4/4",
        text = """
            Reach
            Whenever Barrick Wallace attacks, it deals damage equal to the number of equipped creatures you control to defending player.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(attacksSelf, damage(count(and(ObjectFilter.Attached(to = null, attachments = equipment), creature, you)),
            Recipients(players = Players.DefendingPlayerOf(Objects.Self)))),
    )

    /** "create a token that's a copy of it, except it has "This Equipment's equip abilities cost {2} less to activate."" (CR 707.9b, 601.2f) */
    @Test
    fun ferrionWildRose() = card(
        name = "Ferrion, Wild Rose Rebel",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Rebel Warrior",
        pt = "3/3",
        text = """
            Equipped creatures you control have haste.
            Whenever a nontoken Equipment you control enters, create a token that's a copy of it, except it has "This Equipment's equip abilities cost {2} less to activate." Sacrifice that token at the beginning of the next upkeep.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(and(ObjectFilter.Attached(to = null, attachments = equipment), creature, you))),
        triggered(enters(and(not(token), equipment, you)),
            doing(Effect.CreateToken(TokenSpec.CopyOf(enteredOne, except = listOf(CopyException.AddAbilities(listOf(static(Mechanic.CostChange(
                ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind, source = ObjectFilter.Is(Objects.Self)), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))))))),
                n(1), Players.You, bind = "tokens")),
            delayed(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("tokens"))))),
    )

    // CR 106.6
    @Test
    fun freyaRatKnight() = card(
        name = "Freja Crescentia",
        cost = "{R}",
        type = "Legendary Creature — Rat Knight",
        pt = "1/1",
        text = """
            Jump — During your turn, Freja Crescentia has flying.
            {T}: Add {R}. Spend this mana only to cast an Equipment spell or activate an equip ability.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.YourTurn).copy(abilityWord = "Jump"),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!),
            restriction = or(and(equipment, spellFilter), ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind))), Players.You))),
    )

    // CR 603.12
    @Test
    fun gildemarMasterAtArms() = card(
        name = "Gildemar, Master-at-Arms",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Human Samurai",
        pt = "6/6",
        text = "Whenever Gildemar enters or attacks, look at the top six cards of your library. You may put any number of Equipment cards from among them onto the battlefield. Put the rest on the bottom of your library in a random order. When you put one or more Equipment onto the battlefield this way, you may attach one of them to a Samurai you control.",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(equipment, n(0), null, from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            ifThen(Condition.NonEmpty("chosen"), reflexive(
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("chosen")), bind = "equipment", chooser = Players.You),
                Step.Choose(Choice.Objects(and(sub("Samurai"), you), n(1), n(1)), bind = "samurai", chooser = Players.You),
                doing(Effect.Attach(Objects.Bound("equipment"), Recipients(objects = Objects.Bound("samurai"))))))),
    )

    @Test
    fun hasteSpell() = card(
        name = "Quickening Spell",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature gets +3/+1 and gains haste until end of turn. Exile the top card of your library. You may play it until your next end step.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 1, has(Keyword.Haste)),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilStep(TurnStep.End, PlayerFilter.You))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun gearShopkeeper() = card(
        name = "Gear Shopkeeper",
        cost = "{1}{R}",
        type = "Creature — Human Citizen",
        pt = "2/2",
        text = "Whenever you attack, target attacking equipped creature gains menace until end of turn. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        triggered(youAttack, gains(target("creature"), Keyword.Menace),
            targets = listOf(slot("creature", and(attacking, creature, ObjectFilter.Attached(to = null, attachments = equipment))))),
    )

    @Test
    fun judgmentsLight() = card(
        name = "Judgment's Light",
        cost = "{4}{R}",
        type = "Instant",
        text = "Judgment's Light deals 6 damage to target creature. Destroy up to one Equipment attached to that creature.",
    ) parsesTo abilities(
        spell(damage(6, toObjects(target("creature"))),
            Step.Choose(Choice.Objects(and(equipment, ObjectFilter.Attached(to = target("creature"))), n(0), n(1)), bind = "chosen", chooser = Players.You),
            destroy(Objects.Bound("chosen")),
            targets = listOf(slot("creature", creature))),
    )

    // CR 701.17, 603.7
    @Test
    fun suddenEncounter() = card(
        name = "Sudden Encounter",
        cost = "{4}{R}{R}",
        type = "Sorcery",
        text = """
            Shuffle your library, then mill four cards. Put each creature card milled this way onto the battlefield. They gain haste. At the beginning of the next end step, return those creatures to their owner's hand.
            Flashback {6}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(shuffle, mill(4, postBind = "milled"),
            move(Objects.Where(Objects.Bound("milled"), and(creature, inZone(Zone.Graveyard))), Zone.Battlefield, postBind = "returned"),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Bound("returned"), Zone.Hand))),
        Keyword.Flashback(manaCost("{6}{R}{R}")),
    )

    // CR 702.21a
    @Test
    fun rauvanBullOfTheHighlands() = card(
        name = "Rauvan, Bull of the Highlands",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Warrior",
        pt = "2/2",
        text = """
            Ward—Pay life equal to Rauvan's power.
            Whenever Rauvan attacks, attach up to one target Equipment you control to target attacking creature.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost(CostPart.PayLife(power(Objects.Self)))),
        triggered(attacksSelf, doing(Effect.Attach(target("Equipment"), Recipients(objects = target("creature")))),
            targets = listOf(slot("Equipment", and(equipment, you), min = 0), slot("creature", and(attacking, creature)))),
    )

    @Test
    fun selfDetonate() = card(
        name = "Self-Detonate",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature you control deals X damage to any other target and X damage to itself, where X is its power.",
    ) parsesTo abilities(
        spell(
            damage(power(target("creature")), toAny.copy(), source = target("creature")),
            damage(power(target("creature")), toObjects(target("creature")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), anyTarget.copy(distinctFrom = listOf("creature")))),
    )

    /** "target instant or sorcery card from your graveyard or exiled card with flashback you own" (CR 702.34a) */
    @Test
    fun sorceressPlots() = card(
        name = "Sorceress's Plots",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Return target instant or sorcery card from your graveyard or exiled card with flashback you own to your hand. Add {R}.
            Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You)),
            targets = listOf(slot("card", or(inYourGraveyard(or(instant, sorcery)),
                and(inZone(Zone.Exile), kw(Keyword.Flashback.Kind), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
        Keyword.Flashback(manaCost("{4}{R}")),
    )

    /** Your card and each other card with lesser mana value than it played free this turn (CR 118.9). */
    @Test
    fun tripleTriumph() = card(
        name = "Triple Triumph",
        cost = "{3}{R}{R}{R}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, each player exiles the top card of their library. Until end of turn, you may play the card you own exiled this way and each other card exiled this way with lesser mana value than it without paying their mana costs.",
    ) parsesTo abilities(
        triggered(yourUpkeep,
            move(Objects.LibraryCards(Players.Each(PlayerFilter.Any), n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, withoutPaying = true, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Union(listOf(mine, Objects.Where(Objects.Bound("exiled"), and(not(ObjectFilter.OwnedBy(PlayerFilter.You)),
                    ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue, mine, Combine.Greatest))))))), Duration.EndOfTurn))),
    )

    /** "You may attach an Equipment you control to that creature. If you do, unattach it at the beginning of the next end step." */
    @Test
    fun unexpectedSummons() = card(
        name = "Unexpected Summons",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. You may attach an Equipment you control to that creature. If you do, unattach it at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            untapIt(target("creature")), gains(target("creature"), Keyword.Haste),
            Step.Choose(Choice.Objects(and(equipment, you), n(0), n(1)), bind = "equipment", chooser = Players.You),
            doing(Effect.Attach(Objects.Bound("equipment"), Recipients(objects = target("creature")))),
            ifThen(Condition.NonEmpty("equipment"), delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Unattach(Objects.Bound("equipment"))))),
            targets = listOf(slot("creature", creature))),
    )

    /** "Whenever one or more Scouts, Pirates, and/or Rogues you control deal combat damage to a player" (one trigger per player, CR 603.2c). */
    @Test
    fun vanoStreetThief() = card(
        name = "Vano, Street Thief",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Scout",
        pt = "2/2",
        text = """
            Whenever one or more Scouts, Pirates, and/or Rogues you control deal combat damage to a player, exile the top card of that player's library. You may cast it. If you don't, create a Treasure token.
            Whenever you cast a spell you don't own, put a +1/+1 counter on each Scout, Pirate, and Rogue you control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(arcanum.api.Trigger.On(listOf(EventPattern.Damage(and(rogues, you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            oneOrMore = true, batchPerPlayer = true), Instructions(steps = listOf(
            move(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast, caster = Players.You))), payer = Players.You, bind = "cast"),
            ifThen(Condition.Not(Condition.Did("cast")), createTreasure)))),
        triggered(youCast(not(ObjectFilter.OwnedBy(PlayerFilter.You))), counters(Objects.Each(and(rogues, you)))),
    )

    @Test
    fun judgmentStrike() = card(
        name = "Judgment Strike",
        cost = "{3}{R}",
        type = "Instant",
        text = "Judgment Strike deals 5 damage to target creature and X damage to that creature's controller, where X is the number of Equipment you control.",
    ) parsesTo abilities(
        spell(damage(5, toObjects(target("creature"))), damage(count(and(equipment, you)), Recipients(players = Players.ControllerOf(target("creature")))),
            targets = listOf(slot("creature", creature))),
    )

    private val rogues = or(sub("Scout"), sub("Pirate"), sub("Rogue"))
    private val mine = Objects.Where(Objects.Bound("exiled"), ObjectFilter.OwnedBy(PlayerFilter.You))
}
