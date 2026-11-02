package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 12: Dimir (blue and black). Cards exiled face down and linked to this creature (CR 607.2a, 406.3),
 * revealing until a land card for each opponent, a characteristic-defining count of opponents' graveyards (CR 604.3),
 * modal spells (CR 700.2), returning a permanent and its owner discarding, delayed triggers for the turn (CR 603.7b),
 * each player revealing in turn (CR 101.4), becoming a copy with exceptions and this ability (CR 707.9), "X can't be 0"
 * (CR 107.3), an ability only opponents may activate (CR 602.2), counting milled creature cards, and playing cards
 * exiled with this creature.
 */
class GtcChunk12DimirTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))
    private val exiledWith = Objects.Remembered("exiled")
    private fun revealUntilLand(library: Players, count: Value) = listOf(
        Step.Do(Effect.RevealUntil(land, library, count, reveal = true, foundBind = "found", restBind = "rest")),
        Step.Do(Effect.Move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Destination(Zone.Graveyard))),
    )

    // CR 406.3: exiled face down; CR 607.2a: the cards are linked to this creature's other abilities
    @Test
    fun whisperAlleyBroker() = card(
        name = "Whisper Alley Broker",
        cost = "{1}{U}{B}",
        type = "Creature — Human Rogue",
        pt = "0/3",
        text = """
            {T}: Draw a card, then exile a card from your hand face down.
            You may look at cards exiled with this creature.
            {U}{B}, {T}: Return a card exiled with this creature to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Activated(costOf(CostPart.TapSelf), steps(
            Step.Do(Effect.Draw(Players.You, n(1))),
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You)))), n(1), n(1)),
                bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
        ), rules = anyTime),
        Ability.Static(listOf(Mechanic.MayLookAt(Objects.Affected)), affects = Affected.Objects(exiledWith)),
        Ability.Activated(costOf(manaPart("{U}{B}"), CostPart.TapSelf), steps(
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Where(exiledWith, ObjectFilter.InZone(Zone.Exile))), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
        ), rules = anyTime),
    )

    // CR 604.3: a characteristic-defining ability; each opponent reveals in turn (CR 101.4)
    @Test
    fun devouringAberration() {
        val count = Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard),
            ObjectFilter.OwnedBy(PlayerFilter.Opponent)))))
        card(
            name = "Devouring Aberration",
            cost = "{3}{U}{B}",
            type = "Creature — Horror",
            pt = "*/*",
            text = """
                Devouring Aberration's power and toughness are each equal to the number of cards in your opponents' graveyards.
                Whenever you cast a spell, each opponent reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
            """,
        ) parsesTo abilities(
            Ability.Static(listOf(Mechanic.SetPowerToughness(count, count)), affects = Affected.Objects(Objects.Self), characteristicDefining = true),
            Ability.Triggered(
                Trigger.On(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)),
                steps(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent",
                    revealUntilLand(Players.Bound("opponent"), n(1)), together = true)),
            ),
        )
    }

    // CR 700.2: one mode; "put one back" leaves it on top
    @Test
    fun shadowCharm() = card(
        name = "Shadow Charm",
        cost = "{U}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target sorcery spell.
            • Destroy target creature with power 2 or less.
            • Look at the top three cards of target player's library. Put one back and the rest into that player's graveyard.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("spell", ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Sorcery), ObjectFilter.Category(ObjectCategory.Spell))))),
                steps = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))))),
            Mode(Instructions(targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Power(arcanum.api.Comparison.AtMost, n(2)))))),
                steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature")))))),
            Mode(Instructions(targets = listOf(targetPlayer), steps = listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.Target("player"), n(3), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Graveyard))),
            ))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // The returned permanent's owner discards
    @Test
    fun dreadHorror() = card(
        name = "Dread Horror",
        cost = "{4}{U}{B}",
        type = "Creature — Horror",
        pt = "4/4",
        text = "When this creature enters, return target permanent to its owner's hand, then that player discards a card.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Hand))),
            Step.Do(Effect.Discard(Players.OwnerOf(Objects.Target("permanent")), n(1), filter = ObjectFilter.Any)),
            targets = listOf(slot("permanent", ObjectFilter.Category(ObjectCategory.Permanent))),
        ),
    )

    // CR 603.7b: a delayed trigger each time a card goes to an opponent's graveyard this turn; its owner loses life
    @Test
    fun shadowcloakGuildmage() = card(
        name = "Shadowcloak Guildmage",
        cost = "{U}{B}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            {1}{U}{B}: Whenever a card is put into an opponent's graveyard from anywhere this turn, that player loses 1 life.
            {2}{U}{B}: Target player mills two cards.
        """,
    ) parsesTo abilities(
        Ability.Activated(mana("{1}{U}{B}"), steps(Step.Do(Effect.CreateDelayedTrigger(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.Opponent))), to = Zone.Graveyard)),
            steps(Step.Do(Effect.LoseLife(Players.OwnerOf(Objects.EventObject(EventRole.After)), n(1)))),
            duration = Duration.EndOfTurn, repeats = true,
        ))), rules = anyTime),
        Ability.Activated(mana("{2}{U}{B}"), Instructions(targets = listOf(targetPlayer), steps = listOf(Step.Do(Effect.Mill(Players.Target("player"), n(2))))), rules = anyTime),
    )

    // CR 101.4: each player in turn does every step
    @Test
    fun shadowcloakSeer() {
        val player = Players.Bound("player")
        card(
            name = "Shadowcloak Seer",
            cost = "{2}{U}{B}",
            type = "Creature — Vampire Wizard",
            pt = "4/4",
            text = """
                Flying
                At the beginning of your upkeep, each player reveals the top card of their library, loses life equal to that card's mana value, then puts it into their hand.
            """,
        ) parsesTo abilities(
            Keyword.Flying,
            Ability.Triggered(yourUpkeep, steps(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Do(Effect.Reveal(Objects.LibraryCards(player, n(1), fromTop = true), bind = "revealed")),
                Step.Do(Effect.LoseLife(player, Value.Of(NumericProperty.ManaValue, Objects.Bound("revealed"), arcanum.api.Combine.Single))),
                Step.Do(Effect.Move(Objects.Bound("revealed"), Destination(Zone.Hand))),
            ), together = true))),
        )
    }

    // CR 707.9a, b: the exceptions are part of the copy; it keeps this ability
    @Test
    fun vazalShadowMastermind() = card(
        name = "Vazal, Shadow Mastermind",
        cost = "{U}{U}{B}{B}",
        type = "Legendary Creature — Shapeshifter",
        pt = "3/3",
        text = """
            Hexproof
            Whenever a creature card is put into an opponent's graveyard from anywhere, you may have Vazal become a copy of that card, except its name is Vazal, Shadow Mastermind, it's legendary in addition to its other types, and it has hexproof and this ability.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.Opponent))), to = Zone.Graveyard)),
            steps(Step.May(listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.EventObject(EventRole.After), listOf(
                CopyException.Name("Vazal, Shadow Mastermind"),
                CopyException.Types(addSupertypes = listOf(Supertype.Legendary)),
                CopyException.AddAbilities(listOf(Keyword.Hexproof)),
                CopyException.ThisAbility,
            ))), Affected.Objects(Objects.Self), Duration.Indefinitely))), Players.You)),
        ),
    )

    // CR 107.3: X can't be 0; each opponent reveals until X land cards
    @Test
    fun mindgrinder() = card(
        name = "Mindgrinder",
        cost = "{X}{U}{B}",
        type = "Sorcery",
        text = "Each opponent reveals cards from the top of their library until they reveal X land cards, then puts all cards revealed this way into their graveyard. X can't be 0.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", revealUntilLand(Players.Bound("opponent"), Value.Announced("X")), together = true),
            announced = listOf("X"),
        ),
        Ability.Static(listOf(Mechanic.XAtLeast(1)), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun boneStrider() = card(
        name = "Bone Strider",
        cost = "{1}{U}{B}",
        type = "Creature — Skeleton",
        pt = "1/1",
        text = "When this creature dies, return it to its owner's hand.",
    ) parsesTo abilities(Ability.Triggered(diesSelf, steps(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Hand))))))

    @Test
    fun psychicJolt() = card(
        name = "Psychic Jolt",
        cost = "{1}{U}{B}",
        type = "Instant",
        text = "Counter target spell. Its controller mills two cards.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Counter(Objects.Target("spell"))),
            Step.Do(Effect.Mill(Players.ControllerOf(Objects.Target("spell")), n(2))),
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
        ),
    )

    // CR 602.2: only opponents may activate it; "this Aura's controller" isn't the activating player
    @Test
    fun soulHostage() = card(
        name = "Soul Hostage",
        cost = "{2}{U}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            You control enchanted creature.
            Discard two cards: This Aura's controller sacrifices it, then draws two cards. Only your opponents may activate this ability.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.ControlledBy(Players.You)), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Ability.Activated(
            costOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = n(2)))),
            steps(Step.Do(Effect.Sacrifice(Objects.Self)), Step.Do(Effect.Draw(Players.ControllerOf(Objects.Self), n(2)))),
            rules = arcanum.api.ActivationRules(timing = arcanum.api.Timing.Any, activatedBy = PlayerFilter.Opponent),
        ),
    )

    // The milled creature cards counted where they went
    @Test
    fun forcedConfession() = card(
        name = "Forced Confession",
        cost = "{4}{U/B}",
        type = "Sorcery",
        text = "Target player mills four cards. You draw a card for each creature card put into their graveyard this way.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Mill(Players.Target("player"), n(4), postBind = "milled")),
            Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Where(Objects.Bound("milled"), ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard))))))),
            targets = listOf(targetPlayer),
        ),
    )

    @Test
    fun shadowcultRogue() = card(
        name = "Shadowcult Rogue",
        cost = "{1}{U/B}{U/B}",
        type = "Creature — Human Rogue",
        pt = "2/2",
        text = "This creature can't be blocked except by Rogues.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.Sub(Subtype("Rogue")))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 607.2a: the cards exiled with it may be played by its controller
    @Test
    fun veilstalkerSpecter() = card(
        name = "Veilstalker Specter",
        cost = "{U/B}{U/B}{U/B}",
        type = "Creature — Specter",
        pt = "2/3",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, that player exiles the top card of their library.
            You may play lands and cast spells from among cards exiled with this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(combatDamageToPlayer, steps(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
        )),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), affects = Affected.Objects(exiledWith)),
    )
}
