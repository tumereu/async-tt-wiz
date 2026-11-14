package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 09r: a condition fixed as the spell is cast, an owner's play permission, a delayed trigger with its own
 * targets, and a permanent that becomes a named Equipment. */
class WoeChunk09rLeftoversTest {
    private val minusX = Value.Times(Value.Announced("X"), -1)

    // CR 601.2i: "as you cast this spell" is answered as casting finishes; CR 611.2a (until end of turn)
    @Test
    fun pixieParry() = card(
        name = "Pixie Parry",
        cost = "{X}{B}",
        type = "Instant",
        text = "Target creature gets -X/-X until end of turn. That creature gets an additional -3/-3 until end of turn if you controlled a Faerie as you cast this spell.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(minusX, minusX)),
            Step.If(Condition.AsCast(Condition.Exists(and(sub("Faerie"), you), atLeast = 1)), then = listOf(pump(target("creature"), -3, -3))),
            targets = listOf(slot("creature", creature)),
            announced = listOf("X"),
        ),
    )

    // CR 107.3m (X of the permanent's spell in its enters trigger), 400.7 (a card that leaves exile is a new object: the permission ends);
    // CR 603.4 (it triggers only if one of them came from exile), 603.2h (only once each turn)
    @Test
    fun wondrousVoyage() = card(
        name = "Wondrous Voyage",
        cost = "{X}{X}{U}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile up to X target creatures. For each of those cards, its owner may play it for as long as it remains exiled.
            Whenever one or more nontoken creatures enter, if one or more of them entered from exile or was cast from exile, you draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            move(target("creatures"), Zone.Exile, postBind = "exiled"),
            Step.ForEach(Each.Objects(Objects.Bound("exiled")), "card", listOf(doing(Effect.ApplyMechanics(
                listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.Is(Players.OwnerOf(Objects.Bound("card"))))),
                Affected.Objects(Objects.Bound("card")), Duration.Indefinitely)))),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(and(creature)), min = n(0), max = Value.CastX(Objects.Self))),
        ),
        triggered(
            Trigger.On(listOf(
                EventPattern.ZoneChange(and(not(token), creature), from = Zone.Exile, to = Zone.Battlefield),
                EventPattern.ZoneChange(and(not(token), creature, ObjectFilter.WasCast(Zone.Exile)), from = Zone.Stack, to = Zone.Battlefield),
            ), oneOrMore = true),
            draw(1),
            perTurn = 1,
        ),
    )

    // CR 401.4 (the rest on the bottom); CR 603.7 (a delayed trigger), 603.3d (its targets chosen as it is put on the Stack); CR 506.1 (the
    // combat phase begins with the beginning of combat step)
    @Test
    fun wildAmbushHunt() = card(
        name = "Wild Ambush Hunt",
        cost = "{G}{G}",
        type = "Sorcery",
        text = "Look at the top five cards of your library. You may exile a creature card from among them. Put the rest on the bottom of your library " +
            "in a random order. You may cast the exiled card this turn. At the beginning of the next combat phase this turn, target creature you control " +
            "deals damage equal to its power to up to one target creature you don't control.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any),
                Instructions(
                    targets = listOf(slot("creature", creatureYouControl),
                        slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0)),
                    steps = listOf(damage(power(target("creature")), Recipients(objects = target("creature you don't control")), source = target("creature"))),
                ),
                duration = Duration.EndOfTurn)),
        ),
    )

    // CR 612.8 (a name change, layer 3), 205.1a (the new types replace the old), 613.1f (it loses its other abilities and gains these,
    // layer 6), 702.6a (equip), 611.2a (no duration: indefinite)
    @Test
    fun theCopperspire() = card(
        name = "The Copperspire",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            {T}: Add {C}.
            Whenever a legendary creature you control enters, you may have The Copperspire become a legendary Equipment artifact named Emberbrand, Hero's Heirloom. If you do, it gains equip {3} and "Equipped creature gets +3/+3" and loses all other abilities.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        triggered(enters(and(legendary, creature, you)),
            Step.May(listOf(doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetName("Emberbrand, Hero's Heirloom"),
                Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Equipment"))),
                    addSupertypes = listOf(Supertype.Legendary), replaceTypes = true, replaceSubtypes = true),
                Mechanic.LoseAbilities(),
                Mechanic.GainAbilities(listOf(Keyword.Equip(manaCost("{3}")),
                    Ability.Static(listOf(pt(3, 3)), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))))),
            ), Affected.Objects(Objects.Self), Duration.Indefinitely))), Players.You),
        ),
    )
}
