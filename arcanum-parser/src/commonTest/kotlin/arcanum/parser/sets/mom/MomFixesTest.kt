package arcanum.parser.sets.mom

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM fix chunk: parser fixes found while working the set. */
class MomFixesTest {
    // CR 608.2c: chosen as it resolves, not targeted; CR 401.4: several cards put on top at once are ordered by their owner as they
    // go (the engine asks), so the wording reads for more than one card
    @Test
    fun momentideExhumer() = card(
        name = "Momentide Exhumer",
        cost = "{3}{B}",
        type = "Creature — Zombie",
        pt = "3/3",
        text = "When this creature enters, put up to two creature cards from your graveyard on top of your library.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(inYourGraveyard(creature), n(0), n(2)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Library, position = 0)),
    )

    private val onBattlefield = ObjectFilter.InZone(Zone.Battlefield)

    // Front: CR 701.20a-b (exiled until two are found), 608.2g (cast while it resolves, without paying, CR 118.9), 400.7 (one cast is a new
    // object: "one of them" is put into your hand from those still exiled), 401.4 (the rest on the bottom in a random order).
    // Back: CR 604.3, 105.2 (all colours, in every zone); CR 608.2c-d (a copy of a permanent you control and the counters' creatures chosen
    // as it resolves, not targeted, at least one counter each); CR 707.2 (the token copies the copiable values).
    @Test
    fun invasionOfMomentidePrism() = cardFaces(Layout.Transform,
        OracleCard("Invasion of Momentide Prism", "{W}{U}{B}{R}{G}", "Battle — Siege", siegeReminder + "\n" +
            "When this Siege enters, exile cards from the top of your library until you exile two nonland cards with mana value 4 or less. You may cast one of those two cards without paying its mana cost. Put one of them into your hand. Then put the other cards exiled this way on the bottom of your library in a random order.",
            defense = "7") to abilities(
            triggered(entersSelf,
                Step.Do(Effect.RevealUntil(and(nonland, manaValue(Comparison.AtMost, 4)), Players.You, n(2), reveal = true, into = Zone.Exile,
                    foundBind = "found", restBind = "rest")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Cast(Objects.Bound("chosen"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
                Step.Choose(Choice.Objects(inZone(Zone.Exile), n(1), n(1), from = Objects.Bound("found")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Where(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), inZone(Zone.Exile)),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        ),
        OracleCard("Awaken the Momentide", null, "Sorcery", """
            Awaken the Momentide is all colors.
            Target player draws two cards. You may put an artifact card from your hand onto the battlefield. Create a token that's a copy of a permanent you control. Distribute three +1/+1 counters among one, two, or three creatures you control. Destroy target permanent an opponent controls.
        """.trimIndent()) to abilities(
            static(Mechanic.SetColors(Color.all.map { ColorRef.Literal(it) }), characteristicDefining = true),
            spell(
                draw(2, Players.Target("player")),
                Step.Choose(Choice.Objects(and(artifact, inZone(Zone.Hand), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), n(0), n(1)),
                    bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield),
                Step.Choose(Choice.Objects(and(permanentFilter, you, onBattlefield), n(1), n(1)), bind = "copied", chooser = Players.You),
                Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("copied")), n(1), Players.You, bind = "token")),
                Step.Choose(Choice.Objects(and(creature, you, onBattlefield), n(1), n(3)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.PutCounters(Objects.Bound("chosen"), CounterKind.PowerToughness(1, 1), n(3), distributed = true)),
                destroy(target("permanent")),
                targets = listOf(targetPlayer, slot("permanent", and(permanentFilter, opponents)))),
        ),
    )
}
