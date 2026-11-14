package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.OracleParser
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Wilds of Eldraine chunk 07: fights, mills, Treasures, and cards that have an Adventure. */
class WoeChunk07FightMillTreasureTest {
    private val creatureYouDontControl = and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))
    private val milled = Objects.Bound("milled")
    // CR 110.4b: an artifact, battle, creature, enchantment, or planeswalker spell
    private val permanentSpell = and(or(artifact, ObjectFilter.Type(CardType.Battle), creature, enchantment, ObjectFilter.Type(CardType.Planeswalker)), spellFilter)
    private fun chooseOne(filter: ObjectFilter, from: Objects? = null): Step =
        Step.Choose(Choice.Objects(filter, n(1), n(1), from = from), bind = "chosen", chooser = Players.You)

    // CR 111.10n (Monster Role); CR 603.12: the reflexive trigger once the token is created, its target chosen then; CR 701.14a, b: a fight
    // with up to one target, none chosen: no fight
    @Test
    fun hexOfTheMoonhound() = card(
        name = "Hex of the Moonhound",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Create a Monster Role token attached to target creature you control. When you do, that creature fights up to one target creature you " +
            "don't control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample. " +
            "Creatures that fight each deal damage equal to their power to the other.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.CreateToken(role("Monster"), n(1), Players.You, attachedTo = toObjects(target("creature")), bind = "token")),
            ifThen(Condition.NonEmpty("token"),
                reflexive(doing(Effect.Fight(target("creature"), target("second creature"))),
                    targets = listOf(slot("second creature", creatureYouDontControl, min = 0)))),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    // CR 111.10n; CR 701.14a; CR 614.1a: the fought creature exiled instead if it would die this turn
    @Test
    fun glenbaneOgre() = card(
        name = "Glenbane Ogre",
        cost = "{2}{B}{G}",
        type = "Creature — Troll",
        pt = "4/4",
        text = """
            When this creature enters, create a Monster Role token attached to it. (Enchanted creature gets +1/+1 and has trample.)
            {1}, Sacrifice an Aura attached to this creature: This creature fights target creature you don't control. If that creature would die this turn, exile it instead. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createRole("Monster", Objects.EventObject(EventRole.After))),
        activated(cost(mana("{1}"), sacrificeA(and(sub("Aura"), ObjectFilter.Attached(to = Objects.Self)))),
            doing(Effect.Fight(Objects.Self, target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureYouDontControl)), rules = asSorcery),
    )

    // CR 715.2a: a spell that has an Adventure, cast as the creature (a permanent spell); CR 601.2f; CR 701.17a: mill, then each milled card
    // that has an Adventure goes to the hand
    @Test
    fun thessalyStormstride() = cardFaces(Layout.Adventure,
        card(name = "Thessaly Stormstride", cost = "{G}{U}{R}", type = "Legendary Creature — Giant Noble", pt = "4/4",
            text = """
                Trample
                Permanent spells you cast that have an Adventure cost {1} less to cast.
            """,
        ) to abilities(
            Keyword.Trample,
            static(Mechanic.CostChange(and(permanentSpell, you, ObjectFilter.HasAdventure), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        ),
        card(name = "Chase the Gale", cost = "{2}{G}{U}{R}", type = "Instant — Adventure",
            text = "Mill seven cards. Then put all cards that have an Adventure from among the milled cards into your hand.",
        ) to abilities(spell(mill(7, postBind = "milled"), move(Objects.Where(milled, and(cardFilter, ObjectFilter.HasAdventure)), Zone.Hand))),
    )

    // CR 603.2 (enters or attacks); CR 701.17a; the land card is chosen as it resolves, not targeted (CR 608.2c); CR 601.2f, 602.2b; CR 613.4c
    @Test
    fun buddingTerrapin() = card(
        name = "Budding Terrapin",
        cost = "{2}{G}{G}",
        type = "Creature — Turtle",
        pt = "3/3",
        text = """
            Whenever this creature enters or attacks, mill three cards, then return a land card from your graveyard to the battlefield tapped.
            Activated abilities of lands you control cost {1} less to activate.
            Land creatures you control get +1/+1.
        """,
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, mill(3), chooseOne(inYourGraveyard(land)), move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true)),
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = and(land, you)), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(pt(1, 1), affects = Objects.Each(and(land, creature, you))),
    )

    // CR 701.17a; CR 609.3: one creature, enchantment, or land card from among the milled cards, if there is one
    @Test
    fun thicketFamiliar() = cardFaces(Layout.Adventure,
        card(name = "Thicket Familiar", cost = "{1}{G}", type = "Creature — Elemental Raccoon", pt = "2/2",
            text = """
                {T}: Add {G}.
                {1}{G}, {T}, Discard a card: Return this creature to its owner's hand.
            """,
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
            activated(cost(mana("{1}{G}"), tap, discardACard), move(Objects.Self, Zone.Hand)),
        ),
        card(name = "Seek the Hoard", cost = "{5}{G}{G}", type = "Sorcery — Adventure",
            text = "Mill seven cards. Then put a creature, enchantment, or land card from among the milled cards onto the battlefield.",
        ) to abilities(spell(mill(7, postBind = "milled"), chooseOne(or(creature, enchantment, land), from = milled), move(Objects.Bound("chosen"), Zone.Battlefield))),
    )

    // CR 701.17a; CR 609.3
    @Test
    fun latchpickTrickster() = cardFaces(Layout.Adventure,
        card(name = "Latchpick Trickster", cost = "{1}{U}", type = "Creature — Faerie Rogue", pt = "1/3", text = "Flying, vigilance")
            to abilities(Keyword.Flying, Keyword.Vigilance),
        card(name = "Loose the Sprites", cost = "{1}{U}", type = "Instant — Adventure",
            text = "Mill four cards. Then put an instant, sorcery, or Faerie card from among the milled cards into your hand.",
        ) to abilities(spell(mill(4, postBind = "milled"), chooseOne(or(instant, sorcery, sub("Faerie")), from = milled), move(Objects.Bound("chosen"), Zone.Hand))),
    )

    // CR 604.3 (characteristic-defining); CR 701.17a; CR 715.3d
    @Test
    fun grimDreameater() = cardFaces(Layout.Adventure,
        card(name = "Grim Dreameater", cost = "{1}{B}", type = "Creature — Nightmare", pt = "*/*",
            text = "Grim Dreameater's power and toughness are each equal to the number of creature cards in all graveyards.",
        ) to abilities(
            static(Mechanic.SetPowerToughness(count(and(creature, inZone(Zone.Graveyard))), count(and(creature, inZone(Zone.Graveyard)))),
                characteristicDefining = true),
        ),
        card(name = "Endless Slumber", cost = "{1}{U}", type = "Sorcery — Adventure",
            text = "Target player mills four cards. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(mill(4, players = Players.Target("player")), targets = listOf(targetPlayer))),
    )

    // CR 406.3 (face down); the exiled cards may be looked at and played while they stay exiled (CR 400.7, 305.1)
    @Test
    fun gildedWyrm() = cardFaces(Layout.Adventure,
        card(name = "Gilded Wyrm", cost = "{2}{R}{R}", type = "Creature — Dragon", pt = "4/4",
            text = """
                Flying, trample
                Whenever this creature attacks, create a Treasure token.
            """,
        ) to abilities(Keyword.Flying, Keyword.Trample, triggered(attacksSelf, createTreasure())),
        card(name = "Rich Appetite", cost = "{2}{B}", type = "Instant — Adventure",
            text = "Exile the top two cards of target opponent's library face down. You may look at and play those cards for as long as they remain exiled.",
        ) to abilities(spell(
            doing(Effect.Move(Objects.LibraryCards(Players.Target("opponent"), n(2), fromTop = true), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound("exiled"))), Affected.Players(Players.You), Duration.Indefinitely)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely)),
            targets = listOf(targetOpponent),
        )),
    )

    // CR 603.4 (intervening if); CR 111.10a; another player chosen as it resolves gains control of the target Treasure (CR 613.1b)
    @Test
    fun shrewdMoneylender() = card(
        name = "Shrewd Moneylender",
        cost = "{2}{W}",
        type = "Creature — Human Noble",
        pt = "2/3",
        text = """
            At the beginning of your upkeep, if an opponent controls more lands than you, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            {2}{W}: Choose another player. That player gains control of target Treasure you control. You draw a card.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), createTreasure(), interveningIf = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent,
            PlayerFilter.Where(Condition.Compare(count(and(land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested)))), Comparison.Greater,
                count(and(land, you)))))), atLeast = 1)),
        activated(cost(mana("{2}{W}")),
            Step.Choose(Choice.Player(PlayerFilter.Not(PlayerFilter.You)), bind = "player", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("player"))), Affected.Objects(target("Treasure")), Duration.Indefinitely)),
            draw(1),
            targets = listOf(slot("Treasure", and(sub("Treasure"), you)))),
    )

    // CR 714.2b (chapters); CR 111.10a; the exiled cards may be played this turn (CR 305.1, lands too)
    @Test
    fun ravenousKingAndThePickpocket() = card(
        name = "Ravenous King and the Pickpocket",
        cost = "{3}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            III — Exile the top three cards of target opponent's library. You may play those cards this turn.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2), createTreasure()),
        chapter(3,
            doing(Effect.Move(Objects.LibraryCards(Players.Target("opponent"), n(3), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.EndOfTurn)),
            targets = listOf(targetOpponent)),
    )

    // CR 701.9a (draw, then discard); CR 111.10a
    @Test
    fun hoardersCoffer() = card(
        name = "Hoarder's Coffer",
        cost = "{2}",
        type = "Artifact",
        text = "{2}, {T}: Draw a card, then discard a card. Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), draw(1), discard(1), createTreasure()),
    )

    // CR 120.3; CR 111.10a
    @Test
    fun tossACopper() = card(
        name = "Toss a Copper",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Toss a Copper deals 1 damage to any target. You create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(damage(1, toAny), createTreasure(), draw(1), targets = listOf(anyTarget)),
    )

    // CR 602.1 (cost before the colon); CR 715.3d. Audit (cost span starts at "an", not "Sacrifice"): a false alarm: a part choosing objects
    // spans the objects' phrase ("an artifact or land"), as every sacrifice cost does
    @Test
    fun greedyColossus() {
        val giant = card(name = "Greedy Colossus", cost = "{3}{R}", type = "Creature — Giant", pt = "4/3",
            text = """
                Reach
                {2}{R}, Sacrifice an artifact or land: Draw a card.
            """)
        cardFaces(Layout.Adventure,
            giant to abilities(Keyword.Reach, activated(cost(mana("{2}{R}"), sacrificeA(or(artifact, land))), draw(1))),
            card(name = "Mine Now", cost = "{1}{R}", type = "Instant — Adventure",
                text = "Create a Treasure token. (Then exile this card. You may cast the creature later from exile.)",
            ) to abilities(spell(createTreasure())),
        )
        val ability = OracleParser.parse(giant).abilities[1] as Ability.Activated
        val text = "{2}{R}, Sacrifice an artifact or land: Draw a card."
        assertEquals(listOf("{2}{R}", "an artifact or land"), ability.cost.spans.map { text.substring(it.start, it.end) })
    }

    // CR 702.10 (haste); CR 715.2a: a card in exile you own that has an Adventure, used or not
    @Test
    fun bayingWindfang() = card(
        name = "Baying Windfang",
        cost = "{2}{G}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            Vigilance
            This creature has haste as long as you own a card in exile that has an Adventure.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(has(Keyword.Haste), condition = Condition.Exists(and(cardFilter, ObjectFilter.HasAdventure, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1)),
    )

    // CR 701.6a; CR 118.12: one generic amount, {2} plus {1} for each Faerie the caster controls as it resolves
    @Test
    fun charmFalter() = card(
        name = "Charm Falter",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {2} plus an additional {1} for each Faerie you control.",
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Sum(listOf(n(2), count(and(sub("Faerie"), you))))))),
                payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    // CR 118.12: the same wording counting cards with this card's name in every graveyard ({2} each, CR 201.2)
    @Test
    fun glyphHitch() = card(
        name = "Glyph Hitch",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {2} plus an additional {2} for each card named Glyph Hitch in each graveyard.",
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Sum(listOf(n(2),
                Value.Times(count(and(ObjectFilter.Named("Glyph Hitch"), inZone(Zone.Graveyard))), 2)))))),
                payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )
}
