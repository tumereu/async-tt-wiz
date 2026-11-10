package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN cards that move cards between zones (chunk 20): graveyard hate and recursion. The invented names carry the oracle text of the real cards. */
class FdnChunk20MoveTest {
    private val cardInAGraveyard = and(cardFilter, inZone(Zone.Graveyard))
    private val creatureCardMv2 = inYourGraveyard(creature, manaValue(Comparison.AtMost, 2))

    private fun exileTargetCard() = move(target("card"), Zone.Exile, postBind = "exiled")

    // CR 702.8a, 115.1d, 601.2c: "up to one" allows no target at all; CR 404.1: any player's graveyard
    @Test
    fun thicketProwler() = card(
        name = "Thicket Prowler",
        cost = "{2}{G}",
        type = "Creature — Wolf",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            When this creature enters, exile up to one target card from a graveyard.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, exileTargetCard(), targets = listOf(slot("card", cardInAGraveyard, min = 0))),
    )

    // CR 115.1d: the player is the target, the cards aren't; CR 404.1
    @Test
    fun seraphOfLastRites() = card(
        name = "Seraph of Last Rites",
        cost = "{3}{W}",
        type = "Creature — Angel",
        text = """
            Flying
            When this creature enters, exile target player's graveyard.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 202.3, 603.6c: a leaves-the-battlefield trigger; the card's mana value is what limits the target
    @Test
    fun hearseCoachman() = card(
        name = "Hearse Coachman",
        cost = "{3}{B}",
        type = "Creature — Vampire",
        text = "When this creature dies, return target creature card with mana value 2 or less from your graveyard to the battlefield.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(diesSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", creatureCardMv2))),
    )

    // CR 110.4a: a permanent card is an artifact, battle, creature, enchantment, land or planeswalker card
    @Test
    fun fernrootReclaimer() = card(
        name = "Fernroot Reclaimer",
        cost = "{2}{G}{G}",
        type = "Creature — Elf Druid",
        text = "When this creature enters, return target permanent card from your graveyard to your hand.",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(permanentCard)))),
    )

    // CR 701.26a, 603.2e: tapping is the event; CR 608.2c: the counter comes with or without a target.
    // CR 701.21a: the sacrifice is the cost. "Tap it": this creature, the only object the ability names
    @Test
    fun cindermawGorger() = card(
        name = "Cindermaw Gorger",
        cost = "{2}{B}{R}",
        type = "Creature — Vampire Dragon",
        text = """
            Flying
            Whenever this creature becomes tapped, exile up to one target card from a graveyard and put a +1/+1 counter on this creature.
            Sacrifice another creature: This creature gains indestructible until end of turn. Tap it.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))),
            exileTargetCard(),
            counters(Objects.Self),
            targets = listOf(slot("card", cardInAGraveyard, min = 0)),
        ),
        activated(cost(sacrificeA(and(another, creature))), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
    )

    // CR 700.2b, 603.7a: chosen as the trigger goes on the stack; the delayed trigger returns the exiled card, CR 400.7, CR 110.2a: to its owner
    @Test
    fun ozzikGaleCaller() = card(
        name = "Ozzik, Gale Caller",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Bird Wizard",
        text = """
            Flying
            Whenever you cast a noncreature spell, choose one —
            • Exile another target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
            • Create a 1/1 white Spirit creature token with flying.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(youCast(noncreatureSpell), Instructions(modes = modes(
            mode(
                move(target("creature"), Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                    move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
                targets = listOf(slot("creature", and(another, creature, you))),
            ),
            mode(create(creatureToken("Spirit", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying)))),
        ))),
    )

    // CR 601.2c: zero, one or two targets; CR 608.2c: "then" keeps the order, so a returned card can be discarded
    @Test
    fun gravesideMinuet() = card(
        name = "Graveside Minuet",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Return up to two target creature cards from your graveyard to your hand, then discard a card.",
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), discard(1), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))),
    )

    // CR 115.10: not targeted, every card that matches as it resolves
    @Test
    fun bygoneMuster() = card(
        name = "Bygone Muster",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = "Return all creature cards with mana value 2 or less from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(move(Objects.Each(creatureCardMv2), Zone.Battlefield)),
    )

    // CR 400.7j: "it" is the exiled object, which the effect can still find in the public zone it moved to
    @Test
    fun marrowsopSlime() = card(
        name = "Marrowsop Slime",
        cost = "{1}{G}",
        type = "Creature — Ooze",
        text = "{G}: Exile target card from a graveyard. If it was a creature card, put a +1/+1 counter on this creature and you gain 1 life.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(
            manaCost("{G}"),
            exileTargetCard(),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), creature), then = listOf(counters(Objects.Self), gainLife(1))),
            targets = listOf(slot("card", cardInAGraveyard)),
        ),
    )

    // CR 701.21a: both abilities cost the artifact itself; CR 102.3: each opponent's graveyard, not targeted
    @Test
    fun wraithwickLamp() = card(
        name = "Wraithwick Lamp",
        cost = "{1}",
        type = "Artifact",
        text = """
            When this artifact enters, exile target card from a graveyard.
            {T}, Sacrifice this artifact: Exile each opponent's graveyard.
            {1}, {T}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, exileTargetCard(), targets = listOf(slot("card", cardInAGraveyard))),
        activated(cost(tap, sacrificeSelf), move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent))), Zone.Exile)),
        activated(cost(mana("{1}"), tap, sacrificeSelf), draw(1)),
    )

    // CR 110.2a: under the control of the player who put it there
    @Test
    fun coldReanimation() = card(
        name = "Cold Reanimation",
        cost = "{3}{B}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )
}
