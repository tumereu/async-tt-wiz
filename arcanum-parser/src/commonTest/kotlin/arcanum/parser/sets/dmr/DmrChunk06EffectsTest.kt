package arcanum.parser.sets.dmr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.CardType
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 06: effects (several targets, life for each, protection from a chosen color, type and color changes, attack limits, draw-step draws). */
class DmrChunk06EffectsTest {
    private fun becomeBlackAnd(subtype: String): List<Mechanic> = listOf(Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black))),
        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype(subtype)))))
    private val paid = "paid"
    private fun unlessPaid(payer: Players, life: Int, vararg then: Step): List<Step> = listOf(
        Step.Pay(cost(CostPart.PayLife(n(life))), payer = payer, bind = paid), Step.If(Condition.Not(Condition.Did(paid)), then = then.toList()))

    // CR 115.1, 601.2c (a target for each "target"), 701.8a (destroyed at once)
    @Test
    fun fourfoldRuin() = card(
        name = "Fourfold Ruin",
        cost = "{2}{R}{G}",
        type = "Sorcery",
        text = "Destroy target artifact, target creature, target enchantment, and target land. (You can't cast this spell unless you have legal choices for all its targets.)",
    ) parsesTo abilities(
        spell(destroy(Objects.Union(listOf(target("artifact"), target("creature"), target("enchantment"), target("land")))),
            targets = listOf(slot("artifact", artifact), slot("creature", creature), slot("enchantment", enchantment), slot("land", land))),
    )

    // CR 504.1 (the draw step), 400.7 (a drawn card stays that object in hand), 119.4 (pay life), 401.1 (on top of the library)
    @Test
    fun groveArchive() = card(
        name = "Grove Archive",
        cost = "{1}{G}",
        type = "Enchantment",
        text = "At the beginning of your draw step, you may draw two additional cards. If you do, choose two cards in your hand drawn this turn. " +
            "For each of those cards, pay 4 life or put the card on top of your library.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Draw, PlayerFilter.You), Step.May(listOf(
            draw(2),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.ThisTurn(EventPattern.Draw(PlayerFilter.Any), role = EventRole.Object)), n(2), n(2)), bind = "chosen", chooser = Players.You),
            Step.ForEach(Each.Objects(Objects.Bound("chosen")), "card", unlessPaid(Players.You, 4, move(Objects.Bound("card"), Zone.Library, position = 0)))),
            player = Players.You)),
    )

    // CR 119.3, 608.2h (counted as it resolves)
    @Test
    fun hallowedGathering() = card(
        name = "Hallowed Gathering",
        cost = "{3}{W}",
        type = "Instant",
        text = "Target player gains 2 life for each creature on the battlefield.",
    ) parsesTo abilities(
        spell(doing(Effect.GainLife(Players.Target("player"), Value.Times(count(creature), 2))), targets = listOf(targetPlayer)),
    )

    // CR 701.9a (the discarded cards), 608.2c
    @Test
    fun captainsJudgment() = card(
        name = "Captain's Judgment",
        cost = "{W}{B}",
        type = "Sorcery",
        text = "Target player discards two cards. You gain 3 life for each land card discarded this way.",
    ) parsesTo abilities(
        spell(doing(Effect.Discard(Players.Target("player"), n(2), ObjectFilter.Any, postBind = "discarded")),
            doing(Effect.GainLife(Players.You, Value.Times(Value.Count(Objects.Where(Objects.Bound("discarded"), land)), 3))),
            targets = listOf(targetPlayer)),
    )

    // CR 702.16a (protection from the color this ability's choice named), 611.2c (the creatures as it resolves)
    @Test
    fun radiantAvatar() = card(
        name = "Radiant Avatar",
        cost = "{3}{W}{W}",
        type = "Creature — Incarnation",
        pt = "3/3",
        text = """
            Flying
            {2}{W}: Choose a color. Creatures you control gain protection from the chosen color until end of turn. Activate only if this card is in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{2}{W}"), Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            untilEndOfTurn(Objects.Each(creatureYouControl), has(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Chosen("color"))))),
            rules = anyTime.copy(condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard))),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 702.10 (haste, for good), 603.7 (the delayed sacrifice of that creature)
    @Test
    fun ambushGate() = card(
        name = "Ambush Gate",
        cost = "{3}{R}",
        type = "Enchantment",
        text = "{R}: You may put a creature card from your hand onto the battlefield. That creature gains haste. Sacrifice the creature at the beginning of the next end step.",
    ) parsesTo abilities(
        activated(manaCost("{R}"),
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("put")), Duration.Indefinitely)),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                arcanum.api.Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("put"))))))),
        ),
    )

    // CR 613.4c ("All Nightmares": whoever controls them), 105.3 (a new color replaces the old), 205.1b (a creature type in addition),
    // 611.2a (for as long as it stays), 603.6c (a leaves-the-battlefield trigger)
    @Test
    fun vessarDreadKeeper() = card(
        name = "Vessar, Dread Keeper",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Human Minion",
        pt = "3/3",
        text = """
            All Nightmares get +1/+1.
            {B}{B}{B}, Pay 3 life: Put target creature card from a graveyard onto the battlefield under your control. That creature is black and is a Nightmare in addition to its other creature types.
            When Vessar leaves the battlefield, exile all Nightmares.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(sub("Nightmare"))),
        activated(cost(mana("{B}{B}{B}"), CostPart.PayLife(n(3))),
            move(target("card"), Zone.Battlefield, postBind = "returned", controller = Players.You),
            doing(Effect.ApplyMechanics(becomeBlackAnd("Nightmare"), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard))))),
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)), move(Objects.Each(sub("Nightmare")), Zone.Exile)),
    )

    // CR 613.1d, 613.1e (type and color layers), 205.1b, 105.3
    @Test
    fun warlordsPilgrimage() = card(
        name = "Warlord's Pilgrimage",
        cost = "{1}{B}{R}",
        type = "Enchantment",
        text = """
            All Goblins get +1/+1.
            All Goblins are black and are Zombies in addition to their other creature types.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(sub("Goblin"))),
        static(*becomeBlackAnd("Zombie").toTypedArray(), affects = Objects.Each(sub("Goblin"))),
    )

    // CR 614.12, 607.2a (the color chosen as it entered), 702.16a
    @Test
    fun chorusSeraph() = card(
        name = "Chorus Seraph",
        cost = "{2}{W}{W}",
        type = "Creature — Angel",
        pt = "2/2",
        text = """
            Flying
            As this creature enters, choose a color.
            This creature has protection from the chosen color.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
            affects = Affected.Objects(Objects.Self)),
        static(has(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Remembered("color"))))),
    )

    // CR 603.4 (intervening "if"), 608.2d
    @Test
    fun crowdedOssuary() = card(
        name = "Crowded Ossuary",
        cost = "{1}{B}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, if you have four or more creature cards in your graveyard, you may return target creature card from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(yourUpkeep, Step.May(listOf(move(target("card"), Zone.Hand)), player = Players.You), targets = listOf(slot("card", inYourGraveyard(creature))),
            interveningIf = Condition.Exists(inYourGraveyard(creature), atLeast = 4)),
    )

    // CR 709.3 (split cards), 205.1b (still lands), 613.1d, 613.4b (layers 4 and 7b), 611.2c; 608.2h (its mana value)
    @Test
    fun verdureDecay() = cardFaces(
        Layout.Split,
        card(name = "Verdure", cost = "{G}", type = "Sorcery", text = "All lands you control become 1/1 creatures until end of turn. They're still lands.") to abilities(
            spell(untilEndOfTurn(Objects.Each(and(land, you)), Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.SetPowerToughness(n(1), n(1)))),
        ),
        card(name = "Decay", cost = "{1}{B}", type = "Sorcery", text = "Return target creature card from your graveyard to the battlefield. You lose life equal to its mana value.") to abilities(
            spell(move(target("card"), Zone.Battlefield, postBind = "returned"),
                loseLifeEqual(Value.Of(NumericProperty.ManaValue, Objects.Bound("returned"), arcanum.api.Combine.Single)),
                targets = listOf(slot("card", inYourGraveyard(creature)))),
        ),
    )

    private fun loseLifeEqual(amount: Value): Step = doing(Effect.LoseLife(Players.You, amount))

    // CR 102.2-3 (every other player, opponents or not), 119.3; the life lost this way (CR 608.2c)
    @Test
    fun boglandLeechMage() = card(
        name = "Bogland Leech-Mage",
        cost = "{2}{B}",
        type = "Creature — Human Spellshaper",
        pt = "2/2",
        text = "{2}{B}, {T}, Discard a card: Each other player loses 2 life. You gain life equal to the life lost this way.",
    ) parsesTo abilities(
        activated(cost(mana("{2}{B}"), tap, discardACard),
            doing(Effect.LoseLife(Players.Each(PlayerFilter.Not(PlayerFilter.You)), n(2), bind = "lost")),
            doing(Effect.GainLife(Players.You, Value.Bound("lost")))),
    )

    // CR 119.3, 608.2c: "the life lost this way" is the amount the loss before it took (that loss names it; it named nothing before)
    @Test
    fun tithingShade() = card(
        name = "Tithing Shade",
        cost = "{3}{B}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = "At the beginning of your upkeep, each opponent loses 1 life. You gain life equal to the life lost this way.",
    ) parsesTo abilities(
        triggered(yourUpkeep, doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), n(1), bind = "lost")),
            doing(Effect.GainLife(Players.You, Value.Bound("lost")))),
    )

    // CR 508.1c (a restriction on how many attack you; your planeswalkers aren't you, CR 506.4)
    @Test
    fun narrowTunnel() = card(
        name = "Narrow Tunnel",
        cost = "{3}",
        type = "Artifact",
        text = "No more than two creatures can attack you each combat.",
    ) parsesTo abilities(
        static(Mechanic.MaximumAttackers(2, target = Recipients(players = Players.You)), affects = Objects.Each(creature)),
    )

    // CR 508.1c (can't attack), 509.1b (blocked only by Walls)
    @Test
    fun watchfulOrbOfKesh() = card(
        name = "Watchful Orb of Kesh",
        cost = "{4}{B}",
        type = "Creature — Eye",
        pt = "3/6",
        text = """
            Non-Eye creatures you control can't attack.
            This creature can't be blocked except by Walls.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantAttack(), affects = Objects.Each(and(not(sub("Eye")), creature, you))),
        static(Mechanic.CanBeBlockedOnlyBy(sub("Wall"))),
    )

    // CR 510.3a (combat damage trigger), 118.12 ("you may pay … If you do"), 111.1 (a token for each)
    @Test
    fun thalosTheVerdantWyrm() = card(
        name = "Thalos, the Verdant Wyrm",
        cost = "{3}{R}{G}{W}",
        type = "Legendary Creature — Dragon",
        pt = "6/6",
        text = """
            Flying
            Whenever Thalos deals combat damage to a player, you may pay {2}{G}. If you do, choose a color, then create a 1/1 green Saproling creature token for each permanent of that color.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, Step.Pay(manaCost("{2}{G}"), payer = Players.You, bind = paid), Step.If(Condition.Did(paid), then = listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            create(creatureToken("Saproling", listOf(Color.Green), 1, 1), count(and(permanentFilter, ObjectFilter.OfColor(ColorRef.Chosen("color")))))))),
    )

    // CR 503.1a, 603.2 (each player's upkeep: that player), 119.4, 608.2d (that player chooses the permanent)
    @Test
    fun tetheringCord() = card(
        name = "Tethering Cord",
        cost = "{4}",
        type = "Artifact",
        text = "At the beginning of each player's upkeep, that player may pay 2 life. If they don't, they return a permanent they control to its owner's hand.",
    ) parsesTo abilities(
        triggered(eachUpkeep, *unlessPaid(Players.EventPlayer, 2,
            Step.Choose(Choice.Objects(and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))), n(1), n(1)), bind = "returned",
                chooser = Players.EventPlayer),
            move(Objects.Bound("returned"), Zone.Hand)).toTypedArray()),
    )
}
