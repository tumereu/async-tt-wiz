package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 13: move: modes that make an opponent exile or return cards, a pair of exile-from-graveyard returns, a leave-or-enter trigger and an
 * exile-instead replacement, an "attacks alone" exile, and a return counting modified creatures. */
class NeoChunk13MoveTest {
    private val enters = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private fun bound(name: String) = Objects.Bound(name)
    private val theOpponent = PlayerFilter.Is(Players.Target("opponent"))

    /** "Target opponent exiles a <filter> they control": that opponent chooses, then the chosen permanent is exiled. */
    private fun opponentExiles(filter: ObjectFilter) = mode(
        Step.Choose(Choice.Objects(and(filter, ObjectFilter.ControlledBy(theOpponent)), min = n(1), max = n(1)), bind = "chosen", chooser = Players.Target("opponent")),
        doing(Effect.Move(bound("chosen"), to = Destination(Zone.Exile))),
        targets = listOf(targetOpponent),
    )

    private fun exileAll(filter: ObjectFilter) = mode(doing(Effect.Move(Objects.Each(filter), to = Destination(Zone.Exile))))
    private fun exileTarget(slotName: String, filter: ObjectFilter) =
        mode(doing(Effect.Move(target(slotName), to = Destination(Zone.Exile))), targets = listOf(slot(slotName, filter)))
    private fun returnTarget(filter: ObjectFilter) =
        mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(filter))))
    @Test
    fun debtToTheKamis() = card(
        name = "Debt to the Kamis",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Target opponent exiles a creature they control.
            • Target opponent exiles an enchantment they control.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(opponentExiles(creature), opponentExiles(enchantment)))),
    )

    @Test
    fun experimentalSynthesis() = card(
        name = "Experimental Synthesis",
        cost = "{R}",
        type = "Artifact",
        text = """
            When this artifact enters or leaves the battlefield, exile the top card of your library. Until end of turn, you may play that card.
            {2}{R}, Sacrifice this artifact: Create a 2/2 white Samurai creature token with vigilance. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        // One ability with two trigger events (CR 603.10a: the leaving one looks back in time)
        triggered(
            Trigger.On(listOf(enters, EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))),
            doing(Effect.Move(Objects.LibraryCards(Players.You, count = n(1), fromTop = true), to = Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                affects = Affected.Objects(bound("exiled")),
                duration = Duration.EndOfTurn,
            )),
        ),
        activated(
            cost(mana("{2}{R}"), sacrificeSelf),
            create(creatureToken("Samurai", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun fadeIntoObscurity() = card(
        name = "Fade into Obscurity",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Exile target artifact or enchantment.",
    ) parsesTo abilities(
        spell(doing(Effect.Move(target("permanent"), to = Destination(Zone.Exile))), targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    @Test
    fun farewellTour() = card(
        name = "Farewell Tour",
        cost = "{4}{W}{W}",
        type = "Sorcery",
        text = """
            Choose one or more —
            • Exile all artifacts.
            • Exile all creatures.
            • Exile all enchantments.
            • Exile all graveyards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            exileAll(artifact), exileAll(creature), exileAll(enchantment), exileAll(inZone(Zone.Graveyard)),
            min = 1, max = 4))),
    )

    @Test
    fun gloomcrier() = card(
        name = "Gloomcrier",
        cost = "{1}{B}{G}",
        type = "Enchantment Creature — Cat Beast",
        pt = "2/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, return target permanent card from your graveyard to your hand.
            If this creature would die, exile it instead.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(permanentCard)))),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun spiritOfCraft() = card(
        name = "Spirit of Craft",
        cost = "{4}{R}",
        type = "Creature — Spirit",
        pt = "3/6",
        text = "When this creature enters, return target artifact card with mana value 3 or less from your graveyard to the battlefield. It gains haste. Sacrifice it at the beginning of the next end step.",
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Move(target("card"), to = Destination(Zone.Battlefield), postBind = "returned")),
            // No duration given: the effect lasts indefinitely (CR 611.2a)
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), affects = Affected.Objects(bound("returned")), duration = Duration.Indefinitely)),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(bound("returned"), by = Players.You))),
            targets = listOf(slot("card", inYourGraveyard(artifact, manaValue(Comparison.AtMost, 3)))),
        ),
    )

    @Test
    fun spiritOfRestlessDusk() = card(
        name = "Spirit of Restless Dusk",
        cost = "{4}{B}",
        type = "Creature — Spirit",
        pt = "3/3",
        text = """
            When this creature enters, choose one —
            • Return up to one target Ninja or Rogue creature card from your graveyard to your hand.
            • Put target creature card from your graveyard on top of your library.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(move(target("cards"), Zone.Hand),
                targets = listOf(slot("cards", inYourGraveyard(or(sub("Ninja"), sub("Rogue")), creature), min = 0))),
            mode(doing(Effect.Move(target("card"), to = Destination(Zone.Library, libraryPosition = 0))),
                targets = listOf(slot("card", inYourGraveyard(creature)))),
        ))),
    )

    @Test
    fun repelTheWicked() = card(
        name = "Repel the Wicked",
        cost = "{3}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Exile target creature with power 4 or greater.
            • Exile target enchantment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            exileTarget("creature", and(creature, power(Comparison.AtLeast, 4))), exileTarget("enchantment", enchantment)))),
    )

    @Test
    fun seasonOfRebirth() = card(
        name = "Season of Rebirth",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Choose one or both —
            • Return target creature card from your graveyard to your hand.
            • Return target enchantment card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(returnTarget(creature), returnTarget(enchantment), min = 1, max = 2))),
    )

    @Test
    fun temperedInStillness() = card(
        name = "Tempered in Stillness",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Whenever a creature you control attacks alone, exile the top card of your library. You may play that card this turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(and(creature, you), alone = true)),
            doing(Effect.Move(Objects.LibraryCards(Players.You, count = n(1), fromTop = true), to = Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                affects = Affected.Objects(bound("exiled")),
                duration = Duration.EndOfTurn,
            )),
        ),
    )

    @Test
    fun unrelentingOne() = card(
        name = "Unrelenting One",
        cost = "{2}{B}",
        type = "Creature — Spirit",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever this creature attacks, return target creature card with mana value X or less from your graveyard to the battlefield, where X is the number of modified creatures you control. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature, ObjectFilter.ManaValue(Comparison.AtMost, count(and(creature, you, ObjectFilter.Modified))))))),
    )
}
