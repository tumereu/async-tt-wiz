package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 07: mill, mostly "each player mills" (CR 701.17). */
class MomChunk07MillTest {
    private val eachPlayer = Players.Each(PlayerFilter.Any)
    private val eachOpponent = Players.Each(PlayerFilter.Opponent)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val inGraveyard = inZone(Zone.Graveyard)
    private val chosen = Objects.Bound("chosen")
    private fun chooseFromYourGraveyard(filter: ObjectFilter, min: Int, max: Int) =
        Step.Choose(Choice.Objects(inYourGraveyard(filter), n(min), n(max)), bind = "chosen", chooser = Players.You)
    /** "an opponent has eight or more cards in their graveyard" (CR 404) */
    private val opponentHasEightInGraveyard = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Exists(
        and(inGraveyard, ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested))), atLeast = 8)))), atLeast = 1)
    private fun transformsInto(name: String): Trigger = on(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named(name))))
    private fun siege(name: String, cost: String, text: String, defense: String) =
        OracleCard(name, cost, "Battle — Siege", siegeReminder + "\n" + text.trimIndent(), defense = defense)
    private fun chapter(number: Int, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        Ability.Triggered(Trigger.Chapter(listOf(number)), Instructions(targets = targets, steps = steps.toList()))

    // CR 701.17a; CR 608.2c: you choose one card in each player's graveyard as it resolves; CR 611.2c: the type change is for good, on the
    // creatures you control then
    @Test
    fun ruptureTheOmniverse() = card(
        name = "Rupture the Omniverse",
        cost = "{5}{B}{B}",
        type = "Sorcery",
        text = "Each player mills ten cards. For each player, choose a creature or planeswalker card in that player's graveyard. Put those cards onto the battlefield under your control. Then each creature you control becomes a Phyrexian in addition to its other types.",
    ) parsesTo abilities(
        spell(
            mill(10, eachPlayer),
            Step.ForEach(Each.Players(eachPlayer), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(or(creature, planeswalker), inGraveyard, ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                    bind = "chosen", chooser = Players.You))),
            move(chosen, Zone.Battlefield, controller = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Phyrexian"))))),
                Affected.Objects(Objects.Each(creatureYouControl)), Duration.Indefinitely)),
        ),
    )

    // CR 603.12: the reflexive trigger only if the card was exiled; "this card" is the card in the graveyard (CR 400.7)
    @Test
    fun dartingSkirmisher() = card(
        name = "Darting Skirmisher",
        cost = "{2}{B}",
        type = "Creature — Faerie Rogue",
        pt = "2/2",
        text = """
            Flying
            When this creature dies, each player mills two cards. Then you may exile this card. When you do, put target creature or battle card from your graveyard on top of your library. (To mill two cards, a player puts the top two cards of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelf,
            mill(2, eachPlayer),
            Step.Pay(cost(CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.After)), from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(move(target("card"), Zone.Library, position = 0),
                targets = listOf(slot("card", inYourGraveyard(or(creature, battle))))))),
        ),
    )

    // CR 608.2c: not a target, chosen as it resolves after the mill; "you may put a card" chooses zero or one
    @Test
    fun auraWiredStitchling() = card(
        name = "Aura-Wired Stitchling",
        cost = "{4}{U}",
        type = "Creature — Zombie",
        pt = "4/4",
        text = "When this creature enters, each player mills two cards. Then you may put an instant, sorcery, or battle card from your graveyard on top of your library. (To mill two cards, a player puts the top two cards of their library into their graveyard.)",
    ) parsesTo abilities(
        triggered(entersSelf,
            mill(2, eachPlayer),
            chooseFromYourGraveyard(or(instant, sorcery, battle), 0, 1),
            move(chosen, Zone.Library, position = 0)),
    )

    // CR 701.17a, 701.9a; CR 707.9b: the copy is a 4/4 black Zombie in addition to what it copies; CR 614.1c: "enter as a copy" replaces entering
    @Test
    fun invasionOfKhemret() = cardFaces(Layout.Transform,
        siege("Invasion of Khemret", "{1}{U}{B}",
            "When this Siege enters, each player mills three cards, then each opponent discards a card and you draw a card. (To mill three cards, a player puts the top three cards of their library into their graveyard.)",
            "4") to abilities(
            triggered(entersSelf, mill(3, eachPlayer), discard(1, eachOpponent), draw(1)),
        ),
        card(
            name = "Lazuli Convert",
            cost = null,
            type = "Creature — Zombie",
            pt = "4/4",
            colorIndicator = "UB",
            text = "You may have this creature enter as a copy of any creature card in a graveyard, except it's a 4/4 black Zombie in addition to its other colors and types.",
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersAsCopy(and(creature, inGraveyard), except = listOf(
                    CopyException.PowerToughness(4, 4),
                    CopyException.Colors(listOf(ColorRef.Literal(Color.Black)), add = true),
                    CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Zombie"))))), optional = true)))),
                affects = Affected.Objects(Objects.Self)),
        ),
    )

    // CR 701.17a; CR 701.27; CR 115.1: the target is any graveyard's card, put under your control (CR 110.2a)
    @Test
    fun ratSpokerunner() = cardFaces(Layout.Transform,
        card(
            name = "Rat Spokerunner",
            cost = "{3}{B}",
            type = "Creature — Rat Samurai",
            pt = "3/3",
            text = """
                Menace
                When this creature enters, each player mills three cards. (To mill three cards, a player puts the top three cards of their library into their graveyard.)
                {5}{W/P}: Transform this creature. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)
            """,
        ) to abilities(
            Keyword.Menace,
            triggered(entersSelf, mill(3, eachPlayer)),
            transformAsSorcery("{5}{W/P}"),
        ),
        card(
            name = "Ghastly Spokewheel",
            cost = null,
            type = "Creature — Phyrexian Rat",
            pt = "4/5",
            colorIndicator = "B",
            text = """
                Menace (This creature can't be blocked except by two or more creatures.)
                When this creature transforms into Ghastly Spokewheel, put target permanent card with mana value 2 or less from a graveyard onto the battlefield under your control.
            """,
        ) to abilities(
            Keyword.Menace,
            triggered(transformsInto("Ghastly Spokewheel"), move(target("card"), Zone.Battlefield, controller = Players.You),
                targets = listOf(slot("card", and(permanentCard, manaValue(Comparison.AtMost, 2), inGraveyard)))),
        ),
    )

    // CR 608.2c: the land card is chosen as it resolves from that opponent's graveyard; CR 400.7: "it" is the land it became; CR 614.1a,
    // 611.2a: the gained replacement lasts while it stays. CR 701.23: "any number of" Praetor cards
    @Test
    fun worldsplitterTheConquestRoot() = card(
        name = "Worldsplitter, the Conquest Root",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            {2}, {T}: Target opponent mills three cards. Put a land card from their graveyard onto the battlefield tapped under your control. It gains "If this land would leave the battlefield, exile it instead of putting it anywhere else."
            {10}, {T}, Sacrifice Worldsplitter: Search your library for any number of Praetor cards, put them onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap),
            mill(3, Players.Target("opponent")),
            Step.Choose(Choice.Objects(and(land, inGraveyard, ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
                bind = "chosen", chooser = Players.You),
            move(chosen, Zone.Battlefield, tapped = true, controller = Players.You, postBind = "put"),
            doing(Effect.ApplyMechanics(listOf(has(Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, notTo = Zone.Exile),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self)))),
                Affected.Objects(Objects.Bound("put")), Duration.Indefinitely)),
            targets = listOf(targetOpponent)),
        activated(cost(mana("{10}"), tap, sacrificeSelf),
            doing(Effect.Search(Players.You, sub("Praetor"), max = Value.Count(Objects.Each(and(sub("Praetor"), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Battlefield),
            shuffle),
    )

    // CR 602.5b: "Activate only as a sorcery and only if …"; CR 701.27; CR 714.2: chapters; CR 608.2c: each opponent discards, then mills
    @Test
    fun morvayne() {
        val exiled = Objects.Bound("exiled")
        cardFaces(Layout.Transform,
            card(
                name = "Morvayne",
                cost = "{3}{B}{B}",
                type = "Legendary Creature — Phyrexian Praetor",
                pt = "4/5",
                text = """
                    Menace
                    When Morvayne enters, each opponent sacrifices a nontoken creature or planeswalker of their choice.
                    {4}{B}: Exile Morvayne, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery and only if an opponent has eight or more cards in their graveyard.
                """,
            ) to abilities(
                Keyword.Menace,
                triggered(entersSelf, Step.ForEach(Each.Players(eachOpponent), "opponent", together = true, steps = listOf(
                    Step.Choose(Choice.Objects(and(not(token), or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                        bind = "sacrificed", chooser = Players.Bound("opponent")),
                    doing(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
                activated(manaCost("{4}{B}"),
                    move(Objects.Self, Zone.Exile, postBind = "exiled"),
                    doing(Effect.Move(exiled, Destination(Zone.Battlefield, controller = Players.OwnerOf(exiled), transformed = true))),
                    rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You, condition = opponentHasEightInGraveyard)),
            ),
            card(
                name = "The Bleak Scriptures",
                cost = null,
                type = "Enchantment — Saga",
                colorIndicator = "B",
                text = """
                    (As this Saga enters and after your draw step, add a lore counter.)
                    I — For each opponent, destroy up to one target creature or planeswalker that player controls.
                    II — Each opponent discards three cards, then mills three cards.
                    III — Put all creature cards from all graveyards onto the battlefield under your control. Exile this Saga, then return it to the battlefield (front face up).
                """,
            ) to abilities(
                chapter(1, destroy(target("permanent")), targets = listOf(TargetSlot("permanent",
                    TargetKind.Objects(and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer)))), min = n(0), max = n(1),
                    perPlayer = PlayerFilter.Opponent))),
                chapter(2, discard(3, eachOpponent), mill(3, eachOpponent)),
                chapter(3, move(Objects.Each(and(creature, inGraveyard)), Zone.Battlefield, controller = Players.You),
                    move(Objects.Self, Zone.Exile, postBind = "exiled"), doing(Effect.Move(exiled, Destination(Zone.Battlefield)))),
            ),
        )
    }

    // CR 611.3a: a static ability's condition is checked continuously; CR 506.4: attacks or blocks; CR 701.17a
    @Test
    fun veteranOilspeaker() = card(
        name = "Veteran Oilspeaker",
        cost = "{3}{B}",
        type = "Creature — Phyrexian Wizard",
        pt = "2/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            This creature gets +3/+0 as long as an opponent has eight or more cards in their graveyard.
            Whenever this creature attacks or blocks, each player mills a card.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        static(pt(3, 0), condition = opponentHasEightInGraveyard),
        triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)),
            EventPattern.Block(blocker = ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any))), mill(1, eachPlayer)),
    )

    // CR 701.17a; CR 608.2c: up to two creature cards chosen as it resolves, after the mill (no target)
    @Test
    fun unbarTheOssuary() = card(
        name = "Unbar the Ossuary",
        cost = "{2}{B}",
        type = "Instant",
        text = "Each player mills three cards. Then you return up to two creature cards from your graveyard to your hand. (To mill three cards, a player puts the top three cards of their library into their graveyard.)",
    ) parsesTo abilities(
        spell(mill(3, eachPlayer), chooseFromYourGraveyard(creature, 0, 2), move(chosen, Zone.Hand)),
    )

    // CR 205.1b: "It's still a land"; CR 611.2b: until your next turn; CR 114.2: the emblem; CR 305.1, 601.2: playing lands and casting
    // permanent spells is playing those cards
    @Test
    fun lirraAndRootwalker() {
        val anyColorMana = activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))
        card(
            name = "Lirra and Rootwalker",
            cost = "{1}{G}{G}",
            type = "Legendary Planeswalker — Wrenn",
            loyalty = "4",
            text = """
                Lands you control have "{T}: Add one mana of any color."
                [+1]: Up to one target land you control becomes a 3/3 Elemental creature with vigilance, hexproof, and haste until your next turn. It's still a land.
                [−2]: Mill three cards. You may put a permanent card from among the milled cards into your hand.
                [−7]: You get an emblem with "You may play lands and cast permanent spells from your graveyard."
            """,
        ) parsesTo abilities(
            static(Mechanic.GainAbilities(listOf(anyColorMana)), affects = Objects.Each(and(land, you))),
            loyalty(1, doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                Mechanic.SetPowerToughness(n(3), n(3)),
                Mechanic.GainAbilities(listOf(Keyword.Vigilance, Keyword.Hexproof, Keyword.Haste))),
                Affected.Objects(target("land")), Duration.UntilYourNextTurn)),
                targets = listOf(slot("land", and(land, you), min = 0))),
            loyalty(-2, mill(3, postBind = "milled"),
                Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                move(chosen, Zone.Hand)),
            loyalty(-7, doing(Effect.CreateEmblem(listOf(
                static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You),
                    affects = Objects.Each(and(or(land, permanentCard), inGraveyard, ObjectFilter.OwnedBy(PlayerFilter.You))))), players = Players.You))),
        )
    }
}
