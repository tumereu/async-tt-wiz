package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaPaymentWay
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 05: convoke (CR 702.51). */
class MomChunk05ConvokeTest {
    private val convokeReminder = "Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)"
    private val convoked: Objects = Objects.PaidWith(ManaPaymentWay.TapCreatures, Objects.Self)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private fun modal(vararg options: arcanum.api.Mode, max: Int = 1) = Ability.Spell(Instructions(modes = modes(*options, max = max)))

    // CR 702.51c: the creatures tapped to pay for it; CR 614.1c: it enters with the counters
    @Test
    fun ancientImperiosaur() = card(
        name = "Thronging Saurian",
        cost = "{5}{G}{G}",
        type = "Creature — Dinosaur",
        text = """
            $convokeReminder
            Trample, ward {2}
            This creature enters with two +1/+1 counters on it for each creature that convoked it.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Trample,
        Keyword.Ward(manaCost("{2}")),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.Times(Value.Count(convoked), 2))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 614.1a: triple the damage of any source you control, to a permanent or a player
    @Test
    fun cityOnFire() = card(
        name = "Metropolis Ablaze",
        cost = "{5}{R}{R}{R}",
        type = "Enchantment",
        text = """
            $convokeReminder
            If a source you control would deal damage to a permanent or player, it deals triple that damage instead.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.Damage(you, RecipientFilter(objects = permanentFilter, players = PlayerFilter.Any)),
            modify = listOf(Modification.MultiplyAmount(3)))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 702.8a: a permission of the player for the turn; CR 603.7b: the delayed trigger triggers once; CR 707.10c: new targets for the copies
    @Test
    fun completeTheCircuit() = card(
        name = "Closed Loop Surge",
        cost = "{5}{U}",
        type = "Instant",
        text = """
            $convokeReminder
            You may cast sorcery spells this turn as though they had flash.
            When you next cast an instant or sorcery spell this turn, copy that spell twice. You may choose new targets for the copies.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.CastAsThoughFlash(and(sorcery, spellFilter))), Affected.Players(Players.You), Duration.EndOfTurn)),
            doing(Effect.CreateDelayedTrigger(youCast(and(or(instant, sorcery), spellFilter)), Instructions(steps = listOf(
                Step.Repeat(n(2), listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))))), Duration.EndOfTurn)),
        ),
    )

    // CR 606.3: a planeswalker one of whose loyalty abilities was activated this turn; CR 115.1: one target of either kind
    @Test
    fun cutShort() = card(
        name = "Abrupt Curtail",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            $convokeReminder
            Destroy target planeswalker that was activated this turn or tapped creature.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(
            and(planeswalker, ObjectFilter.ThisTurn(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.Any, kind = arcanum.api.AbilityKind.Loyalty),
                role = EventRole.Source)),
            and(tapped, creature),
        )))),
    )

    // CR 701.23a, 406.3: exiled face down, you may look at it and play it while it stays exiled (CR 400.7); CR 601.2a: where it is cast from
    @Test
    fun hoardingBroodlord() = card(
        name = "Brood Hoardmother",
        cost = "{5}{B}{B}{B}",
        type = "Creature — Dragon",
        text = """
            Convoke
            Flying
            When this creature enters, search your library for a card, exile it face down, then shuffle. For as long as that card remains exiled, you may play it.
            Spells you cast from exile have convoke.
        """,
        pt = "7/6",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Flying,
        triggered(entersSelf,
            search(cardFilter),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
            shuffle,
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound("exiled"))), Affected.Players(Players.You), Duration.Indefinitely)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely)),
        ),
        static(has(Keyword.Convoke), affects = Objects.Each(and(spellFilter, you, ObjectFilter.WasCast(from = Zone.Exile)))),
    )

    // CR 702.51c: X is how many creatures convoked it, counted as the ability resolves (CR 608.2h); the rest are shuffled in (CR 701.24a)
    @Test
    fun knightErrantOfEos() = card(
        name = "Errant Sunlance",
        cost = "{4}{W}",
        type = "Creature — Human Knight",
        text = """
            Convoke
            When this creature enters, look at the top six cards of your library. You may reveal up to two creature cards with mana value X or less from among them, where X is the number of creatures that convoked this creature. Put the revealed cards into your hand, then shuffle.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Convoke,
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Count(convoked))), n(0), n(2),
                from = Objects.Bound("looked")), bind = "revealed", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            move(Objects.Bound("revealed"), Zone.Hand),
            shuffle,
        ),
    )

    // CR 122.1d: each stun counter goes on one of the targets, chosen as the spell resolves; the same creature may get both (CR 608.2c)
    @Test
    fun wickedSlumber() = card(
        name = "Wicked Doze",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            $convokeReminder
            Tap up to two target creatures. Put a stun counter on either of them. Then put a stun counter on either of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(
            tapIt(target("creatures")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = target("creatures")), bind = "chosen", chooser = Players.You),
            counters(Objects.Bound("chosen"), 1, CounterKind.Stun),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = target("creatures")), bind = "chosen", chooser = Players.You),
            counters(Objects.Bound("chosen"), 1, CounterKind.Stun),
            targets = listOf(slot("creatures", creature, min = 0, max = 2)),
        ),
    )

    // CR 702.51c, 122.1b: a flying counter on each creature tapped to pay for it
    @Test
    fun zephyrSinger() = card(
        name = "Gale Chanteuse",
        cost = "{2}{U}{U}",
        type = "Creature — Siren Pirate",
        text = """
            $convokeReminder
            Flying, vigilance
            When this creature enters, put a flying counter on each creature that convoked it.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(entersSelf, counters(convoked, 1, CounterKind.Keyword(Keyword.Flying.kind))),
    )

    // CR 611.2a
    @Test
    fun aerialBoost() = card(
        name = "Skyward Lift",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            $convokeReminder
            Target creature gets +2/+2 and gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(pump(target("creature"), 2, 2, Mechanic.GainAbilities(listOf(Keyword.Flying))), targets = listOf(slot("creature", creature))),
    )

    // CR 700.2: one or both modes
    @Test
    fun artisticRefusal() = card(
        name = "Curated Denial",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = """
            $convokeReminder
            Choose one or both —
            • Counter target spell.
            • Draw two cards, then discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        modal(
            mode(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
            mode(draw(2), discard(1)),
            max = 2,
        ),
    )

    // CR 303.4: enchant creature; CR 603.6a
    @Test
    fun astralWingspan() = card(
        name = "Starlit Pinions",
        cost = "{4}{U}",
        type = "Enchantment — Aura",
        text = """
            $convokeReminder
            Enchant creature
            When this Aura enters, draw a card.
            Enchanted creature gets +2/+2 and has flying.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, draw(1)),
        static(pt(2, 2), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 115.1: up to two targets
    @Test
    fun burningSunsFury() = card(
        name = "Searing Noon Rage",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            $convokeReminder
            Up to two target creatures each get +2/+0 and gain haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(pump(target("creatures"), 2, 0, Mechanic.GainAbilities(listOf(Keyword.Haste))), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 611.2a
    @Test
    fun collectiveNightmare() = card(
        name = "Shared Dread",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            $convokeReminder
            Target creature gets -3/-3 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(pump(target("creature"), -3, -3), targets = listOf(slot("creature", creature))),
    )

    // CR 702.51a: convoke on a colourless artifact creature
    @Test
    fun haloHopper() = card(
        name = "Halo Leaper",
        cost = "{3}",
        type = "Artifact Creature — Frog",
        text = convokeReminder,
        pt = "3/2",
    ) parsesTo abilities(Keyword.Convoke)

    // CR 702.21a; CR 401.4: the rest on the bottom in a random order
    @Test
    fun interdisciplinaryMascot() = card(
        name = "Crossfield Emblem",
        cost = "{6}{U}{U}",
        type = "Creature — Elemental Fractal",
        text = """
            $convokeReminder
            Ward {3}
            When this creature enters, look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in a random order.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Ward(manaCost("{3}")),
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 121.1
    @Test
    fun meetingOfMinds() = card(
        name = "Gathered Thoughts",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            $convokeReminder
            Draw two cards.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(draw(2)),
    )

    // CR 701.8a, 701.25a
    @Test
    fun pileOn() = card(
        name = "Dogpile Strike",
        cost = "{3}{B}",
        type = "Instant",
        text = """
            $convokeReminder
            Destroy target creature or planeswalker. Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(destroy(target("permanent")), doing(Effect.Surveil(Players.You, n(2))), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 301.5, 702.6a
    @Test
    fun ramosianGreatsword() = card(
        name = "Ramish Longblade",
        cost = "{4}{R}",
        type = "Artifact — Equipment",
        text = """
            $convokeReminder
            Equipped creature gets +3/+1 and has trample.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        static(pt(3, 1), has(Keyword.Trample), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 122.1d: a stun counter
    @Test
    fun refereeSquad() = card(
        name = "Umpire Detail",
        cost = "{2}{U}",
        type = "Creature — Homunculus",
        text = """
            $convokeReminder
            Vigilance
            When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Vigilance,
        triggered(entersSelf, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 700.2; CR 310.6: damage to a battle removes defense counters
    @Test
    fun shatterTheSource() = card(
        name = "Sunder the Wellspring",
        cost = "{5}{R}",
        type = "Instant",
        text = """
            $convokeReminder
            Choose one —
            • Sunder the Wellspring deals 6 damage to target creature, planeswalker, or battle.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        modal(
            mode(damage(6, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, planeswalker, battle)))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ),
    )

    // CR 702.9a, 702.10a
    @Test
    fun shivanBranchBurner() = card(
        name = "Shiv Bough-Scorcher",
        cost = "{5}{R}{R}",
        type = "Creature — Dragon",
        text = """
            $convokeReminder
            Flying, haste
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Convoke, Keyword.Flying, Keyword.Haste)

    // CR 115.4: any target
    @Test
    fun stokeTheFlames() = card(
        name = "Fan the Embers",
        cost = "{2}{R}{R}",
        type = "Instant",
        text = """
            $convokeReminder
            Fan the Embers deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(damage(4, toAny), targets = listOf(anyTarget)),
    )

    // CR 401.4: the owner chooses; "second from the top" is position 1 counted from 0 at the top (the audit's question: the model is right)
    @Test
    fun temporalCleansing() = card(
        name = "Chrono Scour",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            $convokeReminder
            The owner of target nonland permanent puts it into their library second from the top or on the bottom.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(
            Step.Choose(Choice.Option(listOf("second from the top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            Step.If(Condition.Chose("position", "second from the top"), then = listOf(move(target("permanent"), Zone.Library, position = 1)),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 702.9a
    @Test
    fun thunderheadSquadron() = card(
        name = "Stormcloud Wing",
        cost = "{5}{U}",
        type = "Creature — Human Knight",
        text = """
            $convokeReminder
            Flying
        """,
        pt = "3/4",
    ) parsesTo abilities(Keyword.Convoke, Keyword.Flying)

    // CR 107.3a: X announced as it is cast; CR 702.51a: convoke pays for X too
    @Test
    fun transcendentMessage() = card(
        name = "Ascendant Missive",
        cost = "{X}{U}{U}{U}{U}",
        type = "Instant",
        text = """
            $convokeReminder
            Draw X cards.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(doing(Effect.Draw(Players.You, Value.Announced("X"))), announced = listOf("X")),
    )
}
