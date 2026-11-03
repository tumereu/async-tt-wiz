package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 10: blue cards. Spells cast from anywhere other than the hand costing less (CR 601.2f) and casting a card
 * from the graveyard while an ability resolves, exiled instead if an instant or sorcery (CR 608.2g, 614.1a); "whenever you
 * activate an ability of a creature", mana abilities too (CR 605); a blink returning at the next end step (CR 603.7); an Aura
 * that taps, removes all counters and takes the abilities away (CR 613.1f); a permanent shuffled into its owner's library; {X}
 * less by the total power of flyers; two piles, one face down (CR 701.20); a counterspell exiling a permanent spell and letting
 * you cast it free while it stays exiled (CR 614.15); top or bottom of the library, the owner's choice.
 */
class HobChunk10BlueTest {
    // CR 601.2f; 608.2g: the card is cast while the ability resolves, its mana cost paid
    @Test
    fun tobboProwlerInTheDark() = card(
        name = "Tobbo, Prowler in the Dark",
        cost = "{1}{U}",
        type = "Legendary Creature — Halfling Rogue",
        text = """
            Spells you cast from anywhere other than your hand cost {1} less to cast.
            Whenever Tobbo attacks, you may cast an artifact, instant, or sorcery spell from your graveyard. If an instant or sorcery spell cast this way would be put into your graveyard, exile it instead.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(spellFilter, you, not(ObjectFilter.WasCast(Zone.Hand))), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(inYourGraveyard(or(artifact, instant, sorcery)), n(0), n(1)), bind = "card", chooser = Players.You),
            Step.Do(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, bind = "spell", caster = Players.You)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(ObjectFilter.Is(Objects.Bound("spell")), or(instant, sorcery)),
                from = Zone.Stack, to = Zone.Graveyard), modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely))),
    )

    // CR 118.12a
    @Test
    fun bamboozleAndBefuddle() = card(
        name = "Bamboozle and Befuddle",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target spell unless its controller pays {4}.
            • Draw two cards, then discard a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(Step.Pay(manaCost("{4}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))), targets = listOf(slot("spell", spellFilter))),
            mode(draw(2), discard(1))))),
    )

    // CR 602.1, 605: any activated ability of a creature, mana abilities too; 603.2h: once each turn
    @Test
    fun elwinMoonScholar() = card(
        name = "Elwin, Moon-Scholar",
        cost = "{2}{U}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever you activate an ability of a creature, draw a card. This ability triggers only once each turn.
            {5}{U}{U}: Exile up to two other target nonland permanents you control. Return those cards to the battlefield under their owner's control at the beginning of the next end step.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.Activate(creature, PlayerFilter.You, manaAbilities = true)), draw(1), perTurn = 1),
        activated(cost(mana("{5}{U}{U}")),
            move(target("permanents"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
            targets = listOf(slot("permanents", and(another, nonland, permanentFilter, you), min = 0, max = 2))),
    )

    // CR 509.1b
    @Test
    fun elfLordsLutist() = card(
        name = "Elf-lord's Lutist",
        cost = "{1}{U}",
        type = "Creature — Elf Bard",
        text = "{4}{U}: Target creature can't be blocked this turn.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{4}{U}")), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
    )

    // CR 122.8 (all counters, of every kind), 613.1f, 502.3
    @Test
    fun bewitchedStreamsClutch() = card(
        name = "Bewitched Stream's Clutch",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature and remove all counters from it.
            Enchanted creature loses all abilities and doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(Objects.AttachedTo(Objects.Self)), Step.Do(Effect.RemoveCounters(Objects.AttachedTo(Objects.Self), null,
            Value.Of(NumericProperty.Counters(null), Objects.AttachedTo(Objects.Self), Combine.Single)))),
        static(Mechanic.LoseAbilities(), Mechanic.DoesntUntap, affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 701.24: put into the library, then shuffled; its owner draws
    @Test
    fun grimbaldRoamingWizard() = card(
        name = "Grimbald, Roaming Wizard",
        cost = "{4}{U}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Ward {3} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {3}.)
            {6}: Grimbald's owner shuffles him into their library and draws three cards.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{3}")),
        activated(cost(mana("{6}")), move(Objects.Self, Zone.Library), Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))), draw(3, Players.OwnerOf(Objects.Self))),
    )

    // CR 601.2f: {1} less for each point of total power
    @Test
    fun theLordOfTheHawks() = card(
        name = "The Lord of the Hawks",
        cost = "{7}{U}{U}",
        type = "Legendary Creature — Bird Noble",
        text = """
            Flash
            This spell costs {X} less to cast, where X is the total power of creatures you control with flying.
            Flying
        """,
        pt = "8/8",
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Of(NumericProperty.Power, Objects.Each(and(creature, you, kw(Keyword.Flying))), Combine.Total)))),
        Keyword.Flying,
    )

    // CR 702.34a
    @Test
    fun raidTheTrollWoods() = card(
        name = "Raid the Troll-Woods",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Draw a card. If this spell was cast from a graveyard, draw two cards instead.
            Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(Step.If(Condition.CastFrom(Zone.Graveyard, Objects.Self), then = listOf(draw(2)), otherwise = listOf(draw(1)))),
        Keyword.Flashback(manaCost("{3}{U}")),
    )

    // CR 701.20: one pile face down, so the opponent sees only the face-up pile
    @Test
    fun puzzlesInTheGloom() = card(
        name = "Puzzles in the Gloom",
        cost = "{2}{U}",
        type = "Instant",
        text = "Look at the top four cards of your library and separate them into a face-down pile and a face-up pile. An opponent chooses one of the piles. Put that pile into your hand and the other into your graveyard.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            Step.Do(Effect.SeparatePiles(Objects.Bound("looked"), separator = Players.You, chooser = Players.Bound("opponent"), chosenBind = "chosen pile",
                otherBind = "other pile", faceDownPile = true)),
            move(Objects.Bound("chosen pile"), Zone.Hand),
            move(Objects.Bound("other pile"), Zone.Graveyard)),
    )

    // CR 614.15: the spell's own replacement of its countered permanent spell; CR 611.2: for as long as it remains exiled
    @Test
    fun elriansEdict() = card(
        name = "Elrian's Edict",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = "Counter target spell. If a permanent spell is countered this way, exile it instead of putting it into its owner's graveyard. You may cast that card without paying its mana cost for as long as it remains exiled.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Counter(target("spell"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)),
            targets = listOf(slot("spell", spellFilter)),
            replacements = listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(ObjectFilter.Is(target("spell")), permanentCard), from = Zone.Stack, to = Zone.Graveyard),
                instead = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.Before), Destination(Zone.Exile), postBind = "exiled"))))))),
    )

    // CR 601.2f; 401.4: the owner chooses top or bottom
    @Test
    fun awkwardFarewells() = card(
        name = "Awkward Farewells",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets an attacking nontoken creature.
            Target creature's owner puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), and(attacking, not(token)))),
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("creature"))),
            Step.If(Condition.Chose("position", "top"), then = listOf(move(target("creature"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
            targets = listOf(slot("creature", creature))),
    )
}
