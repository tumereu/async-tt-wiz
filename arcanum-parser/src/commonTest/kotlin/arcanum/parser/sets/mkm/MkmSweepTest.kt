package arcanum.parser.sets.mkm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MKM sweep: siblings of the parser fixes the set's chunks made, in other wordings. */
class MkmSweepTest {
    private val zombie = creatureToken("Zombie", listOf(Color.Black), 2, 2)

    // CR 608.2c, 111.1 (sibling of chunk 16): after "If you do" of an optional sacrifice, "Put a +1/+1 counter on it." is part of what
    // the "If you do" does, and "it" is the token it created, not this creature
    @Test
    fun sacrificeIfYouDoTokenThenCounterOnIt() = card(
        name = "Grim Barter Test",
        cost = "{2}{B}",
        type = "Creature — Human Warlock",
        pt = "2/2",
        text = "When this creature enters, you may sacrifice a creature. If you do, create a 2/2 black Zombie creature token. Put a +1/+1 counter on it.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(creature)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(create(zombie, bind = "token"), counters(Objects.Bound("token"))))),
    )

    // CR 608.2c (sibling of chunk 16): the same after an optional mana payment
    @Test
    fun payIfYouDoTokenThenCounterOnIt() = card(
        name = "Paid Recruiter Test",
        cost = "{1}{B}",
        type = "Creature — Human",
        pt = "1/1",
        text = "When this creature enters, you may pay {2}. If you do, create a 2/2 black Zombie creature token. Put a +1/+1 counter on it.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(mana("{2}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(create(zombie, bind = "token"), counters(Objects.Bound("token"))))),
    )

    // CR 608.2c: in a spell, "it" after creating a token is that token
    @Test
    fun tokenThenCounterOnIt() = card(
        name = "Raise a Recruit Test",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Create a 2/2 black Zombie creature token. Put a +1/+1 counter on it.",
    ) parsesTo abilities(
        spell(create(zombie, bind = "token"), counters(Objects.Bound("token"))),
    )

    // CR 607.2a (sibling of chunk 03's collect evidence cost): a graveyard exile cost's cards are the ones "exiled with it"
    @Test
    fun graveyardExileCostIsLinked() = card(
        name = "Ossuary Hoarder Test",
        cost = "{1}{B}",
        type = "Creature — Rat",
        pt = "1/1",
        text = """
            {2}, Exile a card from your graveyard: Put a +1/+1 counter on this creature.
            When this creature leaves the battlefield, put each card exiled with it into its owner's hand.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Choose(cardFilter, count = n(1)), from = Zone.Graveyard, remember = "exiled")),
            counters(Objects.Self)),
        triggered(leavesSelf, move(Objects.Remembered("exiled"), Zone.Hand)),
    )

    // CR 608.2c, 603.7c: "it" in a delayed trigger after "If you do, create a token" is that token, and the delayed trigger is made only if
    // you did
    @Test
    fun payIfYouDoTokenThenExileItLater() = card(
        name = "Fleeting Summoner Test",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = "When this creature enters, you may pay {1}. If you do, create a 2/2 black Zombie creature token. Exile it at the beginning of the next end step.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(mana("{1}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(create(zombie, bind = "token"),
                delayed(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Bound("token"), Zone.Exile))))),
    )
}
