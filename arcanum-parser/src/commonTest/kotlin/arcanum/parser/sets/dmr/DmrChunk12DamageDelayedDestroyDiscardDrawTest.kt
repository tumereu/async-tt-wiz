package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** DMR chunk 12: damage, delayed triggers, destroy, discard and draw. */
class DmrChunk12DamageDelayedDestroyDiscardDrawTest {
    private val creatureSlot = listOf(slot("creature", creature))
    private val goblinToken = creatureToken("Goblin", listOf(Color.Red), 1, 1)

    // CR 709.3: a split card's faces; CR 120.3: damage; CR 111.1: a token
    @Test
    fun burstGrow() = cardFaces(
        Layout.Split,
        card(name = "Burst", cost = "{R}", type = "Sorcery", text = "Burst deals 2 damage to any target.") to abilities(
            spell(damage(2, toAny), targets = listOf(anyTarget)),
        ),
        card(name = "Grow", cost = "{3}{G}", type = "Sorcery", text = "Create a 3/3 green Elephant creature token.") to abilities(
            spell(create(creatureToken("Elephant", listOf(Color.Green), 3, 3))),
        ),
    )

    // CR 602.1, 701.21a: sacrifice a Zombie as a cost (only one you control can be chosen)
    @Test
    fun deadCart() = card(
        name = "Dead Cart", cost = "{2}{R}", type = "Enchantment",
        text = "{R}, Sacrifice a Zombie: This enchantment deals 2 damage to any target.",
    ) parsesTo abilities(
        activated(cost(mana("{R}"), sacrificeA(sub("Zombie"))), damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 601.2d: damage divided among one or two targets; CR 701.26a: tap; CR 121.1
    @Test
    fun scorchFrost() = cardFaces(
        Layout.Split,
        card(name = "Scorch", cost = "{1}{R}", type = "Instant", text = "Scorch deals 2 damage divided as you choose among one or two targets.") to abilities(
            spell(
                doing(Effect.DealDamage(to = Recipients(target = "any"), amount = Value.DividedShare, source = Objects.Self)),
                targets = listOf(TargetSlot("any", anyTarget.what, min = n(1), max = n(2), divided = n(2))),
            ),
        ),
        card(name = "Frost", cost = "{1}{U}", type = "Instant", text = "Tap target permanent.\nDraw a card.") to abilities(
            spell(tapIt(target("permanent")), draw(1), targets = listOf(slot("permanent", permanentFilter))),
        ),
    )

    // CR 701.26b, 603.2: "becomes tapped" is a trigger event
    @Test
    fun goblinMenders() = card(
        name = "Goblin Menders", cost = "{2}{R}", type = "Creature — Goblin Shaman", pt = "1/1",
        text = "Whenever this creature becomes tapped, it deals 1 damage to any target.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(isSelf)), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 700.4, 603.10a: dies triggers of itself or another Goblin; CR 701.21a
    @Test
    fun pasharHill() = card(
        name = "Pashar Hill", cost = "{2}{R}", type = "Legendary Creature — Goblin Warrior", pt = "2/2",
        text = """
            Whenever Pashar Hill or another Goblin you control dies, Pashar Hill deals 1 damage to any target.
            {3}{R}, Sacrifice a Goblin: Create two 1/1 red Goblin creature tokens.
        """,
    ) parsesTo abilities(
        triggered(dies(or(isSelf, and(another, sub("Goblin"), you))), damage(1, toAny), targets = listOf(anyTarget)),
        activated(cost(mana("{3}{R}"), sacrificeA(sub("Goblin"))), create(goblinToken, n(2))),
    )

    // CR 120.3: damage to each attacking creature
    @Test
    fun sandwind() = card(
        name = "Sandwind", cost = "{G}", type = "Instant", text = "Sandwind deals 1 damage to each attacking creature.",
    ) parsesTo abilities(
        spell(damage(1, toObjects(Objects.Each(and(attacking, creature))))),
    )

    // CR 603.7c, 603.5: the may is made when the delayed trigger resolves; the delayed trigger returns the card that went to the graveyard; CR 603.7a
    @Test
    fun tianaHullKeeper() = card(
        name = "Tiana, Hull Keeper", cost = "{3}{R}{W}", type = "Legendary Creature — Angel Artificer", pt = "3/3",
        text = """
            Flying, first strike
            Whenever an Aura or Equipment you control is put into a graveyard from the battlefield, you may return that card to its owner's hand at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.FirstStrike,
        triggered(dies(and(or(sub("Aura"), equipment), you)),
            Step.Bind(Objects.EventObject(EventRole.After), "sacrificed"),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(Step.May(listOf(move(Objects.Bound("sacrificed"), Zone.Hand)), Players.You)))))),
    )

    // CR 120.3, 700.4: a creature dealing damage to you is destroyed
    @Test
    fun noPity() = card(
        name = "No Pity", cost = "{2}{B}{B}", type = "Enchantment", text = "Whenever a creature deals damage to you, destroy it.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.You))),
            destroy(Objects.EventObject(EventRole.Source))),
    )

    // CR 701.8a: destroy a tapped creature
    @Test
    fun royalExecutioner() = card(
        name = "Royal Executioner", cost = "{1}{B}{B}", type = "Creature — Human Assassin", pt = "1/1",
        text = "{T}: Destroy target tapped creature.",
    ) parsesTo abilities(
        activated(cost(tap), destroy(target("creature")), targets = listOf(slot("creature", and(tapped, creature)))),
    )

    // CR 701.8a, 701.19c: destroy; it can't be regenerated
    @Test
    fun dreadMark() = card(
        name = "Dread Mark", cost = "{1}{B}", type = "Instant",
        text = "Destroy target nonartifact, nonblack creature. It can't be regenerated.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Destroy(target("permanent"), noRegeneration = true)), targets = listOf(slot("permanent", and(not(artifact), not(ObjectFilter.OfColor(Color.Black)), creature)))),
    )

    // CR 701.9a: each player discards their hand; 700.4
    @Test
    fun mindrender() = card(
        name = "Mindrender", cost = "{2}{B}{B}", type = "Creature — Horror", pt = "4/3",
        text = "When this creature dies, each player discards their hand.",
    ) parsesTo abilities(
        triggered(diesSelf, Step.Do(Effect.Discard(Players.Each(PlayerFilter.Any), count = Value.Fixed(1), filter = ObjectFilter.Any, objects = Objects.Each(inZone(Zone.Hand))))),
    )

    // CR 701.9a
    @Test
    fun acheAnguish() = cardFaces(
        Layout.Split,
        card(name = "Ache", cost = "{B}", type = "Sorcery", text = "Target player discards a card.") to abilities(
            spell(discard(1, Players.Target("player")), targets = listOf(targetPlayer)),
        ),
        card(name = "Anguish", cost = "{3}{R}", type = "Sorcery", text = "Destroy target land.") to abilities(
            spell(destroy(target("land")), targets = listOf(slot("land", land))),
        ),
    )

    // CR 601.2i, 603.2: a "may" draw on casting an enchantment spell
    @Test
    fun mesaSeer() = card(
        name = "Mesa Seer", cost = "{1}{W}{W}", type = "Creature — Human Druid", pt = "0/2",
        text = "Whenever you cast an enchantment spell, you may draw a card.",
    ) parsesTo abilities(
        triggered(youCast(enchantment), Step.May(listOf(draw(1)), Players.You)),
    )

    // CR 121.1, 119.3
    @Test
    fun phyrexianReader() = card(
        name = "Phyrexian Reader", cost = "{2}{B}", type = "Creature — Phyrexian Horror", pt = "2/2",
        text = "When this creature enters, you draw a card and you lose 1 life.",
    ) parsesTo abilities(whenThisEnters(draw(1), loseLife(1)))

    // CR 107.3a, 601.2b: X is announced
    @Test
    fun strokeOfInsight() = card(
        name = "Stroke of Insight", cost = "{X}{2}{U}", type = "Instant", text = "Target player draws X cards.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Draw(Players.Target("player"), Value.Announced("X"))), targets = listOf(targetPlayer), announced = listOf("X")),
    )

    // CR 602.5a, 121.1
    @Test
    fun xiraAren() = card(
        name = "Xira Aren", cost = "{B}{R}{G}", type = "Legendary Creature — Insect Wizard", pt = "1/2",
        text = "Flying\n{B}{R}{G}, {T}: Target player draws a card.",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{B}{R}{G}"), tap), draw(1, Players.Target("player")), targets = listOf(targetPlayer)),
    )
}
