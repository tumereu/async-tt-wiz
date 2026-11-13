package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Color
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Affected
import arcanum.api.Duration
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 04: surveil (CR 701.25), suspected creatures (CR 701.60) and the surveil lands. */
class MkmChunk04SurveilTest {
    private fun surveil(count: Int): Step = Step.Do(Effect.Surveil(Players.You, n(count)))
    private val suspected: ObjectFilter = ObjectFilter.HasStatus(Status.Marked(ObjectMark.Suspected))

    // CR 701.60a: "other" leaves the source out and "up to one" lets the slot be empty; CR 508.3d: one trigger for the declaration
    @Test
    fun furtiveFixer() = card(
        name = "Furtive Fixer",
        cost = "{2}{B}",
        type = "Creature — Vampire Rogue",
        pt = "3/2",
        text = """
            When this creature enters, suspect up to one other target creature you control. (A suspected creature has menace and can't block.)
            Whenever one or more suspected creatures you control attack, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Mark(target("creature"), ObjectMark.Suspected)),
            targets = listOf(slot("creature", and(another, creature, you), min = 0))),
        triggered(Trigger_attack(and(suspected, creature, you)), surveil(1)),
    )

    private fun Trigger_attack(filter: ObjectFilter) = arcanum.api.Trigger.On(EventPattern.Attack(filter), oneOrMore = true)

    // CR 603.2c: one trigger for the batch of exiled objects, from the battlefield and from your graveyard; CR 707.9b: a copy "except it has flying"
    // (the exception's ability is added); CR 701.25a surveil, CR 701.13a exile a card, CR 601.2c one target per other player
    @Test
    fun sigridVerdictOfSpirits() = card(
        name = "Sigrid, Verdict of Spirits",
        cost = "{2}{W}{B}",
        type = "Legendary Planeswalker — Sigrid",
        loyalty = "3",
        text = """
            Whenever one or more creatures you control and/or creature cards in your graveyard are put into exile, you may choose a creature card from among them. Until end of turn, target token you control becomes a copy of it, except it has flying.
            [+2]: Surveil 2, then exile a card from a graveyard.
            [+1]: Create a 1/1 white and black Spirit creature token with flying.
            [−2]: Exile target creature you control. For each other player, exile up to one target creature that player controls.
        """,
    ) parsesTo abilities(
        triggered(
            arcanum.api.Trigger.On(listOf(
                EventPattern.ZoneChange(and(creature, you), from = Zone.Battlefield, to = Zone.Exile),
                EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard, to = Zone.Exile)),
                oneOrMore = true),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.EventObject(EventRole.After)), bind = "chosen", chooser = Players.You),
            Step.If(Condition.NonEmpty("chosen"), listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("chosen"),
                except = listOf(CopyException.AddAbilities(listOf(Keyword.Flying))))), Affected.Objects(target("token")), Duration.EndOfTurn)))),
            targets = listOf(slot("token", and(token, you)))),
        loyalty(2, surveil(2),
            Step.Choose(Choice.Objects(and(cardFilter, inZone(Zone.Graveyard)), n(1), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile)),
        loyalty(1, create(creatureToken("Spirit", listOf(Color.White, Color.Black), 1, 1, abilities = listOf(Keyword.Flying)))),
        loyalty(-2, move(target("creature"), Zone.Exile), move(target("second creature"), Zone.Exile),
            targets = listOf(slot("creature", and(creature, you)),
                arcanum.api.TargetSlot("second creature", arcanum.api.TargetKind.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer)))),
                    min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent))),
    )

    // CR 305.6: the basic land types give the mana abilities; CR 614.1c: enters tapped
    private fun surveilLand(name: String, types: String) = card(
        name = name,
        cost = null,
        type = "Land — $types",
        text = """
            ({T}: Add {X} or {Y}.)
            This land enters tapped.
            When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """.trimIndent().replace("{X} or {Y}", MANA.getValue(types)),
    ) parsesTo abilities(entersTapped, triggered(entersSelf, surveil(1)))

    private companion object {
        val MANA = mapOf("Mountain Forest" to "{R} or {G}", "Mountain Plains" to "{R} or {W}", "Forest Island" to "{G} or {U}",
            "Forest Plains" to "{G} or {W}", "Plains Island" to "{W} or {U}", "Swamp Mountain" to "{B} or {R}", "Plains Swamp" to "{W} or {B}",
            "Island Mountain" to "{U} or {R}", "Island Swamp" to "{U} or {B}", "Swamp Forest" to "{B} or {G}")
    }

    @Test fun hushedAlcove() = surveilLand("Hushed Alcove", "Mountain Forest")
    @Test fun sootyGrotto() = surveilLand("Sooty Grotto", "Mountain Plains")
    @Test fun mossyCloister() = surveilLand("Mossy Cloister", "Forest Island")
    @Test fun verdantCrypt() = surveilLand("Verdant Crypt", "Forest Plains")
    @Test fun tidalCatacomb() = surveilLand("Tidal Catacomb", "Plains Island")
    @Test fun ashenConservatory() = surveilLand("Ashen Conservatory", "Swamp Mountain")
    @Test fun brackishVestry() = surveilLand("Brackish Vestry", "Plains Swamp")
    @Test fun cinderGallery() = surveilLand("Cinder Gallery", "Island Mountain")
    @Test fun dimPassage() = surveilLand("Dim Passage", "Island Swamp")
    @Test fun mouldyMortuary() = surveilLand("Mouldy Mortuary", "Swamp Forest")

    // CR 709.3: the two halves of a split card; CR 701.25a
    @Test
    fun hubbubRuckus() = cardFaces(
        Layout.Split,
        card(
            name = "Hubbub",
            cost = "{2}{R/W}",
            type = "Instant",
            text = "Put a +1/+1 counter on each attacking creature you control.",
        ) to abilities(
            spell(counters(Objects.Each(and(attacking, creature, you)))),
        ),
        card(
            name = "Ruckus",
            cost = "{4}{W/U}{W/U}",
            type = "Sorcery",
            text = "Create three 1/1 colorless Thopter artifact creature tokens with flying. Surveil 2.",
        ) to abilities(
            spell(create(thopter, n(3)), surveil(2)),
        ),
    )

    // CR 701.25a surveil 2; CR 602.5d: the cost exiles this card from the graveyard; CR 602.5d sorcery timing
    @Test
    fun mossbackScrapper() = card(
        name = "Mossback Scrapper",
        cost = "{G}",
        type = "Creature — Human Detective",
        pt = "1/1",
        text = """
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            {G}, Exile this card from your graveyard: Put a +1/+1 counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, surveil(2)),
        activated(cost(mana("{G}"), CostPart.Exile(CostObjects.Self, Zone.Graveyard)), counters(target("creature")),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 701.25a; CR 603.2: a creature with power 2 or less other than this one, checked as it enters
    @Test
    fun waryTerrier() = card(
        name = "Wary Terrier",
        cost = "{B}",
        type = "Creature — Dog",
        pt = "1/1",
        text = """
            Menace
            Whenever another creature you control with power 2 or less enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(enters(and(another, creature, you, power(Comparison.AtMost, 2))), surveil(1)),
    )
}
