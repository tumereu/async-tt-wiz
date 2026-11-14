package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 07: cloak (CR 701.58) from a library, a hand, looked-at cards and exile; Auras (CR 303.4); mill (CR 701.17). */
class MkmChunk07CloakAurasMillTest {
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val faceDown = ObjectFilter.HasStatus(Status.FaceDown)
    private fun cloak(cards: Objects, count: Value = n(1), bind: String? = null): Step =
        Step.Do(Effect.Action(KeywordAction.Cloak(count, cards = cards), bind = bind))

    // CR 701.58a: the cloaked permanent is "it"; CR 701.3a: the Equipment is attached to it
    @Test
    fun veiledMantle() = card(
        name = "Veiled Mantle",
        cost = "{2}{U}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, cloak the top card of your library, then attach this Equipment to it. (To cloak a card, put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)
            Equipped creature gets +1/+0 and can't be blocked.
            {1}{U}: Return this Equipment to its owner's hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Action(KeywordAction.Cloak(n(1)), bind = "cloaked")), doing(Effect.Attach(Objects.Self, toObjects(Objects.Bound("cloaked"))))),
        static(pt(1, 0), Mechanic.CantBeBlocked, affects = enchanted),
        activated(manaCost("{1}{U}"), move(Objects.Self, Zone.Hand)),
    )

    // CR 701.58g: a cloaked instant or sorcery card can't be turned face up ("If you can't"); CR 608.2g: cast as the ability resolves;
    // CR 701.58a: the damaged player's top card, cloaked under your control
    @Test
    fun ysolde() = card(
        name = "Ysolde, Shrouded Fugitive",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Vampire Assassin",
        pt = "1/4",
        text = """
            Deathtouch
            Face-down creatures you control have "{2}{U}{B}: Turn this creature face up. If you can't, exile it, then you may cast the exiled card without paying its mana cost."
            Whenever an Assassin you control deals combat damage to an opponent, cloak the top card of that player's library.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        static(has(activated(manaCost("{2}{U}{B}"),
            doing(Effect.TurnFaceUp(Objects.Self)),
            ifThen(Condition.ObjectMatches(Objects.Self, faceDown),
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You)),
        )), affects = Objects.Each(and(faceDown, creature, you))),
        triggered(on(EventPattern.Damage(and(sub("Assassin"), you), RecipientFilter(players = PlayerFilter.Opponent), combat = true)),
            cloak(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true))),
    )

    // CR 700.2: one or both modes; CR 708.8: turned face up; CR 701.58a, e: exiled face down, then each cloaked
    @Test
    fun unmaskTheSchemer() = card(
        name = "Unmask the Schemer",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one or both —
            • Turn target face-down creature face up.
            • Exile any number of face-up creatures you control with disguise in a face-down pile, shuffle that pile, then cloak them. (To cloak a card, put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(doing(Effect.TurnFaceUp(target("creature"))), targets = listOf(slot("creature", and(faceDown, creature)))),
            mode(
                Step.Choose(Choice.Objects(and(not(faceDown), creature, you, kw(Keyword.Disguise.Kind)), n(0), null), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                cloak(Objects.Bound("exiled"), count = Value.Count(Objects.Bound("exiled"))),
            ),
            min = 1, max = 2,
        ))),
    )

    // CR 701.58a, e: two of the five looked at are cloaked one at a time; CR 401.4: the rest on the bottom in a random order
    @Test
    fun blendIntoTheCrowd() = card(
        name = "Blend into the Crowd",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Look at the top five cards of your library, cloak two of them, and put the rest on the bottom of your library in a random order. (To cloak a card, put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            cloak(Objects.Bound("chosen"), count = n(2)),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 701.58a: a card from your hand, chosen as it resolves; CR 105.2c: colorless creatures
    @Test
    fun oriel() = card(
        name = "Oriel, Shifting Enigma",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Elf Ooze Wizard",
        pt = "3/4",
        text = """
            At the beginning of combat on your turn, choose one —
            • Cloak a card from your hand. (Put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)
            • Put a +1/+1 counter on each colorless creature you control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(beginningOfCombatOnYourTurn, Instructions(modes = modes(
            mode(
                Step.Choose(Choice.Objects(and(cardFilter, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(1), n(1)), bind = "chosen", chooser = Players.You),
                cloak(Objects.Bound("chosen")),
            ),
            mode(counters(Objects.Each(and(ObjectFilter.Colorless, creature, you)))),
        ))),
    )

    // CR 701.60a: no longer suspected; CR 701.60a, 614.1: it can't become suspected while enchanted
    @Test
    fun ironcladExcuse() = card(
        name = "Ironclad Excuse",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, untap enchanted creature. It gains hexproof until end of turn. If it's suspected, it's no longer suspected.
            Enchanted creature gets +2/+2 and can't become suspected.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        triggered(entersSelf, untapIt(enchanted), gains(enchanted, Keyword.Hexproof), ifThen(isSuspected(enchanted), noLongerSuspected(enchanted))),
        static(pt(2, 2), Mechanic.CantBecomeMarked(ObjectMark.Suspected), affects = enchanted),
    )

    // CR 611.3a: checked continuously, whoever controls the Aura; CR 613.4b: base power and toughness; CR 509.1b: can't block Detectives
    @Test
    fun weightOfEvidence() = card(
        name = "Weight of Evidence",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets +2/+2 as long as it's a Detective you control. Otherwise, it has base power and toughness 1/1 and can't block Detectives.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(pt(2, 2), affects = enchanted, condition = Condition.ObjectMatches(enchanted, and(sub("Detective"), you))),
        static(Mechanic.SetPowerToughness(n(1), n(1)), Mechanic.CantBlock(sub("Detective")), affects = enchanted,
            condition = Condition.Not(Condition.ObjectMatches(enchanted, and(sub("Detective"), you)))),
    )

    // CR 613.1b, 613.4b, 613.1f, 613.1d: control, base power and toughness, deathtouch and a creature type
    @Test
    fun compelledToMurder() = card(
        name = "Compelled to Murder",
        cost = "{3}{U}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            You control enchanted creature.
            Enchanted creature has base power and toughness 1/1, has deathtouch, and is an Assassin in addition to its other types.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(Mechanic.ControlledBy(Players.You), affects = enchanted),
        static(Mechanic.SetPowerToughness(n(1), n(1)), has(Keyword.Deathtouch), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Assassin")))),
            affects = enchanted),
    )

    // CR 701.60a: the enchanted creature becomes suspected; CR 113.6: the last ability works in the graveyard
    @Test
    fun handyScapegoat() = card(
        name = "Handy Scapegoat",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, suspect enchanted creature. (It has menace and can't block.)
            Enchanted creature gets +1/+1.
            {2}{R}: Return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(entersSelf, suspect(enchanted)),
        static(pt(1, 1), affects = enchanted),
        activated(manaCost("{2}{R}"), move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 115.1: a target other than the creature it enchants
    @Test
    fun carefulScrutiny() = card(
        name = "Careful Scrutiny",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, target creature you control other than enchanted creature gets +2/+2 and gains vigilance until end of turn.
            Enchanted creature gets +2/+2 and has vigilance.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(entersSelf, pump(target("creature"), 2, 2, has(Keyword.Vigilance)),
            targets = listOf(slot("creature", and(not(ObjectFilter.Is(enchanted)), creature, you)))),
        static(pt(2, 2), has(Keyword.Vigilance), affects = enchanted),
    )

    // CR 610.3: exiled until the Aura leaves; CR 605.1b, 106.12a: a triggered mana ability adding one more mana
    @Test
    fun interredBeneathRoses() = card(
        name = "Interred Beneath Roses",
        cost = "{2}{G}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, exile target nonland permanent you don't control until this Aura leaves the battlefield.
            Whenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(enchanted), forMana = true)),
            Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Choose(n(1))), Players.ControllerOf(Objects.EventObject(arcanum.api.EventRole.Object))))),
    )

    // CR 603.2: the granted trigger; CR 701.16a: investigate
    @Test
    fun nosyQuestioning() = card(
        name = "Nosy Questioning",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has "Whenever this creature deals combat damage to a player, investigate." (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(1, 1), has(triggered(combatDamageToAPlayer, investigate())), affects = enchanted),
    )

    // CR 502.3: doesn't untap; CR 701.24a: shuffled into its owner's library (that creature's owner)
    @Test
    fun theatricalCharge() = card(
        name = "Theatrical Charge",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
            {U}{U}: Shuffle enchanted creature into its owner's library.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(entersSelf, tapIt(enchanted)),
        static(Mechanic.DoesntUntap, affects = enchanted),
        activated(manaCost("{U}{U}"), move(enchanted, Zone.Library), Step.Do(Effect.Shuffle(Players.OwnerOf(enchanted)))),
    )

    // CR 702.8a: flash; CR 702.11a: hexproof for the turn
    @Test
    fun spriteWings() = card(
        name = "Sprite Wings",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, enchanted creature gains hexproof until end of turn.
            Enchanted creature gets +1/+0 and has flying.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        triggered(entersSelf, gains(enchanted, Keyword.Hexproof)),
        static(pt(1, 0), has(Keyword.Flying), affects = enchanted),
    )

    // CR 603.10a: one trigger for each batch of deaths; CR 701.17a: mill; CR 603.12: the reflexive trigger only when it is sacrificed
    @Test
    fun gorePatternStudy() = card(
        name = "Gore Pattern Study",
        cost = "{B}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, it deals 3 damage to target creature an opponent controls.
            Whenever one or more creatures die, mill a card and put a bloodstain counter on this enchantment. Then sacrifice it if it has five or more bloodstain counters on it. When you do, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", creatureOpponentControls))),
        Ability.Triggered(Trigger.On(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true), Instructions(steps = listOf(
            mill(1),
            counters(Objects.Self, 1, CounterKind.Named("bloodstain")),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("bloodstain"), atLeast = 5)),
                sacrificeIt,
                reflexive(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature))))),
        ))),
    )

    // CR 701.17a: mill two; CR 602.5b: once each turn
    @Test
    fun rotgrubLeech() = card(
        name = "Rotgrub Leech",
        cost = "{B}",
        type = "Creature — Zombie Leech",
        pt = "1/1",
        text = """
            Whenever this creature deals combat damage to a player, you mill two cards.
            {1}{B}: This creature gets +2/+2 until end of turn. Activate only once each turn.
        """,
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer, mill(2)),
        Ability.Activated(manaCost("{1}{B}"), Instructions(steps = listOf(pump(Objects.Self, 2, 2))), anyTime.copy(perTurn = 1)),
    )

    // CR 701.17a: mill three as it enters; CR 400.7: each land card returned is a new permanent, tapped
    @Test
    fun wreckageExaminer() = card(
        name = "Wreckage Examiner",
        cost = "{1}{G}",
        type = "Creature — Elf Detective",
        pt = "1/3",
        text = """
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            {3}{G}, Sacrifice this creature: Return all land cards from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(3)),
        activated(cost(mana("{3}{G}"), sacrificeSelf), move(Objects.Each(inYourGraveyard(land)), Zone.Battlefield, tapped = true)),
    )
}
