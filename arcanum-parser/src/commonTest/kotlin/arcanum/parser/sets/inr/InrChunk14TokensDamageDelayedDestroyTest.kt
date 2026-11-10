package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 14: creating tokens (CR 111, 701.7), damage (CR 120), delayed triggered abilities (CR 603.7) and destruction (CR 701.8).
 */
class InrChunk14TokensDamageDelayedDestroyTest {
    private val spirit = creatureToken("Spirit", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying))
    private val decayedZombie = creatureToken("Zombie", listOf(Color.Black), 2, 2, abilities = listOf(Keyword.Decayed))
    private val bloodToken = TokenSpec.Predefined("Blood")

    // CR 602.5d: only as a sorcery; CR 118.8: exiling itself from the graveyard is a cost
    @Test
    fun dauntlessCathar() = card(
        name = "Dauntless Cathar", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "3/2",
        text = "{1}{W}, Exile this card from your graveyard: Create a 1/1 white Spirit creature token with flying. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), create(spirit), rules = asSorcery),
    )

    // CR 111.1: the Zombie token; CR 602.2: the activated ability targets a Zombie
    @Test
    fun drunauCorpseTrawler() = card(
        name = "Drunau Corpse Trawler", cost = "{3}{U}", type = "Creature — Zombie", pt = "1/1",
        text = """
            When this creature enters, create a 2/2 black Zombie creature token.
            {2}{B}: Target Zombie gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(zombie)),
        activated(cost(mana("{2}{B}")), gains(target("Zombie"), Keyword.Deathtouch), targets = listOf(slot("Zombie", sub("Zombie")))),
    )

    // CR 702.147a (decayed), CR 603.2 and 603.2c (one or more creatures die: one trigger), CR 603.4 (once each turn)
    @Test
    fun ghoulishProcession() = card(
        name = "Ghoulish Procession", cost = "{1}{B}", type = "Enchantment",
        text = "Whenever one or more nontoken creatures die, create a 2/2 black Zombie creature token with decayed. This ability triggers only once each turn. (A creature with decayed can't block. When it attacks, sacrifice it at end of combat.)",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(not(token), creature), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            create(decayedZombie), perTurn = 1),
    )

    // CR 111.10g (Blood token), CR 701.21 (sacrifice)
    @Test
    fun gluttonousGuest() = card(
        name = "Gluttonous Guest", cost = "{2}{B}", type = "Creature — Vampire", pt = "1/4",
        text = """
            When this creature enters, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            Whenever you sacrifice a Blood token, you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(blood)),
        triggered(on(EventPattern.Sacrifice(and(sub("Blood"), token, you))), gainLife(1)),
    )

    // CR 701.7a, 508.4: tokens put onto the battlefield tapped and attacking; CR 712.4a: the meld pair
    @Test
    fun hanweirGarrison() = cardFaces(Layout.Meld("Hanweir, the Writhing Township"),
        card(
            name = "Hanweir Garrison", cost = "{2}{R}", type = "Creature — Human Soldier", pt = "2/3",
            text = """
                Whenever this creature attacks, create two 1/1 red Human creature tokens that are tapped and attacking.
                (Melds with Hanweir Battlements.)
            """,
        ) to abilities(triggered(attacksSelf, doing(Effect.CreateToken(creatureToken("Human", listOf(Color.Red), 1, 1), n(2), Players.You,
            tapped = true, attacking = AttackTarget.Chosen)))),
        card(
            name = "Hanweir, the Writhing Township", cost = null, type = "Legendary Creature — Eldrazi Ooze", colorIndicator = "R", pt = "7/4",
            text = """
                Trample, haste
                Whenever Hanweir attacks, create two 3/2 colorless Eldrazi Horror creature tokens that are tapped and attacking.
            """,
        ) to abilities(Keyword.Trample, Keyword.Haste,
            triggered(attacksSelf, doing(Effect.CreateToken(creatureToken("Eldrazi Horror", emptyList(), 3, 2), n(2), Players.You,
                tapped = true, attacking = AttackTarget.Chosen)))),
    )

    // CR 602.2: the ability works from the graveyard; CR 701.9 (discard two as a cost); CR 614.1 (tapped)
    @Test
    fun hauntedDead() = card(
        name = "Haunted Dead", cost = "{3}{B}", type = "Creature — Zombie", pt = "2/2",
        text = """
            When this creature enters, create a 1/1 white Spirit creature token with flying.
            {1}{B}, Discard two cards: Return this card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(spirit)),
        activated(cost(mana("{1}{B}"), CostPart.Discard(CostObjects.Choose(cardFilter, count = n(2)))), move(Objects.Self, Zone.Battlefield, tapped = true, postBind = "returned")),
    )

    // CR 700.4: dies = put into a graveyard from the battlefield
    @Test
    fun mausoleumGuard() = card(
        name = "Mausoleum Guard", cost = "{3}{W}", type = "Creature — Human Scout", pt = "2/2",
        text = "When this creature dies, create two 1/1 white Spirit creature tokens with flying.",
    ) parsesTo abilities(triggered(diesSelf, create(spirit, n(2))))

    // CR 608.2c: "if you do" depends on the optional discard; CR 701.9
    @Test
    fun packGuardian() = card(
        name = "Pack Guardian", cost = "{2}{G}{G}", type = "Creature — Wolf Spirit", pt = "4/3",
        text = """
            Flash
            When this creature enters, you may discard a land card. If you do, create a 2/2 green Wolf creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf,
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(land, count = n(1)))), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(create(creatureToken("Wolf", listOf(Color.Green), 2, 2))))),
    )

    // CR 111.4, 614.1: tapped tokens, one for each matching card in the graveyard
    @Test
    fun riseFromTheTides() = card(
        name = "Rise from the Tides", cost = "{5}{U}", type = "Sorcery",
        text = "Create a tapped 2/2 black Zombie creature token for each instant and sorcery card in your graveyard.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.CreateToken(zombie, count(inYourGraveyard(or(instant, sorcery))), Players.You, tapped = true))),
    )

    // CR 700.2: each mode has its own target; CR 120.3 (damage), CR 701.8 (destroy)
    @Test
    fun abrade() = card(
        name = "Abrade", cost = "{1}{R}", type = "Instant",
        text = """
            Choose one —
            • Abrade deals 3 damage to target creature.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(target("creature")), source = Objects.Self), targets = listOf(slot("creature", creature))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ))),
    )

    // CR 603.2: casting an instant or sorcery spell triggers; CR 701.26b: untap
    @Test
    fun thermoAlchemist() = card(
        name = "Thermo-Alchemist", cost = "{1}{R}", type = "Creature — Human Shaman", pt = "0/3",
        text = """
            Defender
            {T}: This creature deals 1 damage to each opponent.
            Whenever you cast an instant or sorcery spell, untap this creature.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), damage(1, eachOpponent)),
        triggered(youCast(or(instant, sorcery)), untapIt(Objects.Self)),
    )

    // CR 120.3: damage to each opponent, then the Blood token
    @Test
    fun voldarenEpicure() = card(
        name = "Voldaren Epicure", cost = "{R}", type = "Creature — Vampire", pt = "1/1",
        text = "When this creature enters, it deals 1 damage to each opponent. Create a Blood token. (It's an artifact with \"{1}, {T}, Discard a card, Sacrifice this token: Draw a card.\")",
    ) parsesTo abilities(triggered(entersSelf, damage(1, eachOpponent), create(blood)))

    // CR 603.7, 603.7c: a delayed trigger created by the dies trigger, naming the dead card now (the delayed trigger has another event); CR 614.1a: the opponent's creature is exiled instead
    @Test
    fun liesaForgottenArchangel() = card(
        name = "Liesa, Forgotten Archangel", cost = "{2}{W}{W}{B}", type = "Legendary Creature — Angel", pt = "4/5",
        text = """
            Flying, lifelink
            Whenever another nontoken creature you control dies, return that card to its owner's hand at the beginning of the next end step.
            If a creature an opponent controls would die, exile it instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Lifelink,
        triggered(dies(and(another, not(token), creature, you)),
            Step.Bind(Objects.EventObject(EventRole.After), "sacrificed"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Bound("sacrificed"), Zone.Hand))),
        static(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
    )

    // CR 701.8a, 119.3
    @Test
    fun infernalGrasp() = card(
        name = "Infernal Grasp", cost = "{1}{B}", type = "Instant",
        text = "Destroy target creature. You lose 2 life.",
    ) parsesTo abilities(spell(destroy(target("creature")), loseLife(2), targets = listOf(slot("creature", creature))))

    // CR 701.8a: a may destroy with a target of any of three subtypes
    @Test
    fun slayerOfTheWicked() = card(
        name = "Slayer of the Wicked", cost = "{3}{W}", type = "Creature — Human Soldier", pt = "3/2",
        text = "When this creature enters, you may destroy target Vampire, Werewolf, or Zombie.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(destroy(target("permanent"))), player = Players.You),
            targets = listOf(slot("permanent", or(sub("Vampire"), sub("Werewolf"), sub("Zombie"))))),
    )
}
