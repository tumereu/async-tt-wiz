package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
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
 * Gatecrash chunk 05: cipher. Cipher is a keyword (CR 702.99a): the spell's last instruction lets its controller exile
 * the card encoded on a creature they control, which then has "Whenever this creature deals combat damage to a player, you
 * may copy the encoded card and you may cast the copy without paying its mana cost"; its meaning comes from the shared
 * keyword table. The spells' own instructions: tap and keep tapped through the next untap step, a delayed return, a
 * player's actions in order, and every player discarding their hand and drawing the greatest number discarded.
 */
class GtcChunk05CipherTest {
    private fun spell(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList()))

    // CR 502.3: it stays tapped through its controller's next untap step
    @Test
    fun shacklingCipher() = card(
        name = "Shackling Cipher",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Tap target creature an opponent controls. That creature doesn't untap during its controller's next untap step.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(Objects.Target("creature")),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(Objects.Target("creature"))))),
            targets = listOf(slot("creature", creatureOpponentControls)),
        ),
        Keyword.Cipher,
    )

    @Test
    fun finalWhisper() = card(
        name = "Final Whisper",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Draw a card.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(spell(Step.Do(Effect.Draw(Players.You, n(1)))), Keyword.Cipher)

    // CR 707.2: the token copies the copiable values
    @Test
    fun borrowedVisage() = card(
        name = "Borrowed Visage",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Create a token that's a copy of target artifact or creature.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Target("permanent")), count = n(1), controller = Players.You)),
            targets = listOf(slot("permanent", ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature)))),
        ),
        Keyword.Cipher,
    )

    // CR 603.7: a delayed trigger at the next end step, returning the new object (CR 400.7)
    @Test
    fun phaseStride() = card(
        name = "Phase Stride",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Exile target creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                steps(Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
            )),
            targets = listOf(slot("creature", creature)),
        ),
        Keyword.Cipher,
    )

    @Test
    fun encodedTorment() = card(
        name = "Encoded Torment",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Target player discards a card.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.Discard(Players.Target("player"), n(1), filter = ObjectFilter.Any)), targets = listOf(targetPlayer)),
        Keyword.Cipher,
    )

    @Test
    fun midnightExhumation() = card(
        name = "Midnight Exhumation",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Return target creature card from your graveyard to your hand.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))),
            targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
        ),
        Keyword.Cipher,
    )

    @Test
    fun shadowIncision() = card(
        name = "Shadow Incision",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            Target opponent loses 3 life.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.LoseLife(Players.Target("opponent"), n(3))), targets = listOf(targetOpponent)),
        Keyword.Cipher,
    )

    // CR 608.2c: the instructions in order, all by the same target player
    @Test
    fun cellarPlague() = card(
        name = "Cellar Plague",
        cost = "{4}{B}{B}",
        type = "Sorcery",
        text = """
            Target player loses 1 life, discards a card, then sacrifices a permanent of their choice.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LoseLife(Players.Target("player"), n(1))),
            Step.Do(Effect.Discard(Players.Target("player"), n(1), filter = ObjectFilter.Any)),
            Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), n(1), n(1)), bind = "sacrificed", chooser = Players.Target("player")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetPlayer),
        ),
        Keyword.Cipher,
    )

    @Test
    fun nightwingSummons() = card(
        name = "Nightwing Summons",
        cost = "{2}{U}{B}",
        type = "Sorcery",
        text = """
            Create a 1/1 blue and black Horror creature token with flying.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.CreateToken(creatureToken("Horror", listOf(Color.Blue, Color.Black), 1, 1, listOf(Keyword.Flying)), n(1), Players.You))),
        Keyword.Cipher,
    )

    @Test
    fun paranoidVisions() = card(
        name = "Paranoid Visions",
        cost = "{U}{B}",
        type = "Sorcery",
        text = """
            Target player mills three cards.
            Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.Mill(Players.Target("player"), n(3))), targets = listOf(targetPlayer)),
        Keyword.Cipher,
    )

    // CR 101.4: each player discards at once, then each draws; the number is the greatest any one player discarded
    @Test
    fun whisperedFrenzy() {
        val each = Players.Each(PlayerFilter.Any)
        card(
            name = "Whispered Frenzy",
            cost = "{2}{U}{B}",
            type = "Sorcery",
            text = """
                Each player discards their hand, then draws cards equal to the greatest number of cards a player discarded this way.
                Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
            """,
        ) parsesTo abilities(
            spell(
                Step.Do(Effect.Discard(each, n(1), filter = ObjectFilter.Any, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)), postBind = "discarded")),
                Step.Do(Effect.Draw(each, Value.GreatestFor(each,
                    Value.Count(Objects.Where(Objects.Bound("discarded"), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested))))))),
            ),
            Keyword.Cipher,
        )
    }
}
