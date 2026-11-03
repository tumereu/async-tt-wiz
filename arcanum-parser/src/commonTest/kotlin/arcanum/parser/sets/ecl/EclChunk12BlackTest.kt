package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 12: black cards. -1/-1 counters, a hand revealed and a card chosen to discard (CR 701.20), an Equipment that
 * attaches as it enters (CR 701.3), wither until end of turn (CR 702.80), "Whenever you attack" (CR 508.3d), two targets for one X,
 * players who can't draw or gain life (CR 121.1, 119.7), each player's draw step (the active player, CR 102.1), spells cast during an
 * opponent's turn, a dead creature returning without its abilities (CR 400.7, 611.2e), a delayed "when that creature dies this turn"
 * (CR 603.7c), cards revealed from an opponent's hand and one exiled to cast with mana of any type (CR 609.4b), and creatures that entered
 * from a graveyard or were cast from one (CR 603.4), once each turn.
 */
class EclChunk12BlackTest {
    private val opponentsHand = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))

    @Test
    fun grannysVerdict() = card(
        name = "Granny's Verdict",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target opponent reveals their hand. You choose a nonland permanent card from it. That player discards that card.
            • Target creature gets -2/-2 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(doing(Effect.Reveal(Objects.Each(opponentsHand), bind = "revealed")),
                Step.Choose(Choice.Objects(and(nonland, permanentCard), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetOpponent)),
            mode(pump(target("creature"), -2, -2), targets = listOf(slot("creature", creature)))), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 701.3a, 702.80a
    @Test
    fun hookedLancet() = card(
        name = "Hooked Lancet",
        cost = "{1}{B}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains wither until end of turn. (It deals damage to creatures in the form of -1/-1 counters.)
            Equipped creature gets +1/+2.
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, toObjects(target("creature")))), gains(target("creature"), Keyword.Wither),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 2), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun gallFlaskHobgoblin() = card(
        name = "Gall-Flask Hobgoblin",
        cost = "{B}",
        type = "Creature — Goblin Assassin",
        text = "When this creature dies, put a -1/-1 counter on up to one target creature.",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(diesSelf, counters(target("creature"), 1, m1m1), targets = listOf(slot("creature", creature, min = 0))),
    )

    @Test
    fun sourpetalCarrier() = card(
        name = "Sourpetal Carrier",
        cost = "{B}{B}",
        type = "Creature — Faerie Rogue",
        text = """
            Flash
            Flying
            At the beginning of your upkeep, you lose 1 life and create a 1/1 blue and black Faerie creature token with flying.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), loseLife(1), create(faerie)),
    )

    @Test
    fun cankerDecay() = card(
        name = "Canker Decay",
        cost = "{2}{B}",
        type = "Instant",
        text = "Put four -1/-1 counters on target creature.",
    ) parsesTo abilities(
        spell(counters(target("creature"), 4, m1m1), targets = listOf(slot("creature", creature))),
    )

    // CR 508.3d: once for the declaration
    @Test
    fun hobgoblinTrickster() = card(
        name = "Hobgoblin Trickster",
        cost = "{1}{B}",
        type = "Creature — Goblin Warrior",
        text = "Whenever you attack, target attacking Goblin you control gets +1/+0 until end of turn.",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(youAttack, pump(target("Goblin"), 1, 0), targets = listOf(slot("Goblin", and(attacking, sub("Goblin"), you)))),
    )

    @Test
    fun gloomFalls() = card(
        name = "Gloom Falls",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = "Put two -1/-1 counters on each creature.",
    ) parsesTo abilities(
        spell(counters(Objects.Each(creature), 2, m1m1)),
    )

    @Test
    fun duskpalmMourner() = card(
        name = "Duskpalm Mourner",
        cost = "{3}{B}",
        type = "Creature — Elf Warlock",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, mill three cards. Then if there is an Elf card in your graveyard, each opponent loses 2 life and you gain 2 life. (To mill three cards, put the top three cards of your library into your graveyard.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, mill(3), Step.If(Condition.Exists(inYourGraveyard(sub("Elf")), atLeast = 1), then = listOf(
            loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)))),
    )

    // one X for both targets
    @Test
    fun murkRender() = card(
        name = "Murk Render",
        cost = "{3}{B}{B}",
        type = "Creature — Elf Assassin",
        text = "When this creature enters, target creature you control gets +X/+0 until end of turn and up to one target creature an opponent controls gets -0/-X until end of turn, where X is the number of Elves you control plus the number of Elf cards in your graveyard.",
        pt = "4/4",
    ) parsesTo abilities(
        run {
            val x = Value.Sum(listOf(count(and(sub("Elf"), you)), count(inYourGraveyard(sub("Elf")))))
            triggered(entersSelf, pumpBy(target("creature"), x, n(0)), pumpBy(target("second creature"), n(0), Value.Times(x, -1)),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls, min = 0)))
        },
    )

    @Test
    fun steelGuardElf() = card(
        name = "Steel-Guard Elf",
        cost = "{1}{B}",
        type = "Creature — Elf Warrior",
        text = """
            Discard a card: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it. If its toughness is 0 or less, it still dies.)
        """,
        pt = "3/1",
    ) parsesTo abilities(
        activated(cost(discardACard), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
    )

    @Test
    fun nightbloomDrainer() = card(
        name = "Nightbloom Drainer",
        cost = "{2}{B}",
        type = "Creature — Elf Warlock",
        text = "Whenever this creature attacks, you draw a card and lose 1 life.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(attacksSelf, draw(1), loseLife(1)),
    )

    // CR 121.1: no draws at all; CR 119.7: no life gained; CR 102.1: the draw step's active player
    @Test
    fun duskhymnRefrain() = card(
        name = "Duskhymn Refrain",
        cost = "{1}{B}{B}",
        type = "Legendary Enchantment",
        text = """
            Players can't draw cards or gain life.
            At the beginning of each player's draw step, that player loses 3 life, searches their library for a card, puts it into their hand, then shuffles.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MaxDrawsPerTurn(0), Mechanic.CantGainLife, players = Players.Each(PlayerFilter.Any)),
        triggered(stepBegins(TurnStep.Draw, PlayerFilter.Any),
            loseLife(3, Players.Active),
            doing(Effect.Search(Players.Active, cardFilter, max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.Active)),
            move(Objects.Bound("found"), Zone.Hand),
            doing(Effect.Shuffle(Players.Active))),
    )

    @Test
    fun nightTerrorSeeder() = card(
        name = "Night Terror Seeder",
        cost = "{3}{B}",
        type = "Creature — Faerie Assassin",
        text = """
            Flying, lifelink
            Whenever you cast a spell during an opponent's turn, put a -1/-1 counter on up to one target creature.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(youCast(ObjectFilter.Any), counters(target("creature"), 1, m1m1), targets = listOf(slot("creature", creature, min = 0)),
            triggersOnlyIf = Condition.Not(Condition.YourTurn)),
    )

    // CR 603.10a: the counters as it last existed; CR 611.2e: it has no abilities as it enters
    @Test
    fun gaggingWretch() = card(
        name = "Gagging Wretch",
        cost = "{2}{B}",
        type = "Creature — Goblin",
        text = "When this creature dies, if it had a -1/-1 counter on it, return it to the battlefield under its owner's control and it loses all abilities.",
        pt = "4/2",
    ) parsesTo abilities(
        triggered(diesSelf,
            doing(Effect.Move(Objects.EventObject(EventRole.After), arcanum.api.Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(EventRole.After)),
                entersAs = listOf(Mechanic.LoseAbilities())))),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(m1m1, atLeast = 1))),
    )

    @Test
    fun scarknifeRanger() = card(
        name = "Scarknife Ranger",
        cost = "{1}{B}",
        type = "Creature — Elf Scout",
        text = """
            Lifelink
            When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, mill(2)),
    )

    // CR 603.7c: a delayed trigger for this turn on the targeted creature
    @Test
    fun scarknifesSpite() = card(
        name = "Scarknife's Spite",
        cost = "{B}",
        type = "Instant",
        text = "Target creature you control gains deathtouch and lifelink until end of turn. When that creature dies this turn, create a 2/2 black and green Elf creature token.",
    ) parsesTo abilities(
        spell(gains(target("creature"), Keyword.Deathtouch, Keyword.Lifelink),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(create(elf))), duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 609.4b: mana of any type; for as long as you control it
    @Test
    fun samplerOfGoods() = card(
        name = "Sampler of Goods",
        cost = "{2}{B}",
        type = "Creature — Goblin Warlock",
        text = "When this creature enters, target opponent reveals X cards from their hand, where X is the number of Goblins you control. You choose one of those cards. That player exiles it. If an instant or sorcery card is exiled this way, you may cast it for as long as you control this creature, and mana of any type can be spent to cast that spell.",
        pt = "3/2",
    ) parsesTo abilities(
        run {
            val x = count(and(sub("Goblin"), you))
            triggered(entersSelf,
                Step.Choose(Choice.Objects(opponentsHand, x, x), bind = "revealed", chooser = Players.Target("opponent")),
                doing(Effect.Reveal(Objects.Bound("revealed"))),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
                Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), or(instant, sorcery)), then = listOf(doing(Effect.ApplyMechanics(
                    listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You, manaAsAnyColor = true)),
                    arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))))),
                targets = listOf(targetOpponent))
        },
    )

    // CR 603.2c: once for the batch, only creatures that came from a graveyard
    @Test
    fun duskAugur() = card(
        name = "Dusk Augur",
        cost = "{2}{B}",
        type = "Creature — Elf Cleric",
        text = """
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            Whenever one or more other creatures you control enter, if they entered or were cast from a graveyard, create a token that's a copy of one of them. This ability triggers only once each turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Surveil(Players.You, n(2)))),
        run {
            val others = and(another, creature, you)
            triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(others, from = Zone.Graveyard, to = Zone.Battlefield),
                EventPattern.ZoneChange(and(others, ObjectFilter.WasCast(Zone.Graveyard)), from = Zone.Stack, to = Zone.Battlefield)), oneOrMore = true),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.EventObject(EventRole.After)), bind = "chosen", chooser = Players.You),
                create(TokenSpec.CopyOf(Objects.Bound("chosen"))),
                perTurn = 1)
        },
    )
}
