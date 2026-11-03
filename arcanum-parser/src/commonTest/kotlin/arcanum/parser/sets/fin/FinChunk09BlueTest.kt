package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 09: blue cards: coins that come up heads (CR 705.2), copying an ability X times (CR 707.10) with an ability that can't
 * be copied, an additional cost of one of two kinds (CR 601.2b), countering abilities (CR 701.6b), playing the top card after attacking,
 * cards handed out between you and an opponent, casting a card from a graveyard with mana of any type, borrowing an Equipment until a
 * delayed trigger unattaches it, an Aura locking a permanent, milling replaced by a bigger mill (CR 614.1a), an additional end step
 * (CR 500.9).
 */
class FinChunk09BlueTest {
    // CR 705.2, 603.4 (only the first time each turn)
    @Test
    fun edrikKingOfSands() = card(
        name = "Edrik, King of the Sands",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Human Artificer Noble",
        pt = "4/5",
        text = """
            When Edrik enters, draw a card for each artifact you control.
            Two-Headed Coin — The first time you flip one or more coins each turn, those coins come up heads and you win those flips.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Draw(Players.You, count(and(artifact, you))))),
        static(Mechanic.Replacement(EventPattern.FlipCoin(PlayerFilter.You), modify = listOf(Modification.WinFlips)),
            condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.FlipCoin(PlayerFilter.You), atLeast = 1, measure = Measure.Events)))
            .copy(abilityWord = "Two-Headed Coin"),
    )

    // CR 707.10, 107.3 (X can't be 0)
    @Test
    fun mimeOfManyForms() = card(
        name = "Mimo, Master of Mimicry",
        cost = "{2}{U}",
        type = "Legendary Creature — Wizard",
        pt = "2/4",
        text = "{X}{X}, {T}: Copy target activated or triggered ability you control X times. You may choose new targets for the copies. This ability can't be copied and X can't be 0. (Mana abilities can't be targeted.)",
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{X}{X}"), tap), Instructions(
            targets = listOf(slot("ability", and(ObjectFilter.Category(ObjectCategory.StackAbility),
                or(ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Triggered)), you))),
            steps = listOf(Step.Repeat(Value.Announced("X"), listOf(doing(Effect.CopySpell(target("ability"), Players.You, newTargets = true))))),
            announced = listOf("X")),
            anyTime.copy(minimumX = 1, cantBeCopied = true)),
    )

    // CR 601.2b, 701.6b
    @Test
    fun mentorsSacrifice() = card(
        name = "Mentor's Sacrifice",
        cost = "{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a legendary creature or pay {2}.
            Counter target activated ability, triggered ability, or noncreature spell.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(cost(sacrificeA(and(legendary, creature))), manaCost("{2}"))))))),
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", or(
            and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated)),
            and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Triggered)),
            and(noncreature, spellFilter))))),
    )

    // CR 401.5, 305.1 / 601.2a (playing the top card)
    @Test
    fun theMoonWhale() = card(
        name = "The Moonward Whale",
        cost = "{3}{U}",
        type = "Legendary Artifact — Vehicle",
        pt = "3/5",
        text = """
            Flying
            You may look at the top card of your library any time.
            As long as The Moonward Whale attacked this turn, you may play the top card of your library.
            Crew 1
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))),
            condition = Condition.HappenedThisTurn(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), atLeast = 1, measure = Measure.Events)),
        Keyword.Crew(1),
    )

    /** Five cards revealed, handed out in turns: you take one, an opponent bottoms one, you take one, they bottom one, you take the last. */
    @Test
    fun recollectionsReturning() = card(
        name = "Recollections Returning",
        cost = "{2}{U}{U}",
        type = "Sorcery",
        text = """
            Reveal the top five cards of your library. Put one of them into your hand. Then choose an opponent. They put one on the bottom of your library. Then you put one into your hand. Then they put one on the bottom of your library. Put the other into your hand.
            Flashback {7}{U}{U}
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "revealed")),
            pick("first", Players.You), move(Objects.Bound("first"), Zone.Hand),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            pick("second", Players.Bound("opponent"), "first"), move(Objects.Bound("second"), Zone.Library, position = -1),
            pick("third", Players.You, "first", "second"), move(Objects.Bound("third"), Zone.Hand),
            pick("fourth", Players.Bound("opponent"), "first", "second", "third"), move(Objects.Bound("fourth"), Zone.Library, position = -1),
            move(rest("first", "second", "third", "fourth"), Zone.Hand),
        ),
        Keyword.Flashback(manaCost("{7}{U}{U}")),
    )

    // CR 608.2g (cast while resolving), 601.2h
    @Test
    fun questaTrepid() = card(
        name = "Quessa Trepid",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/2",
        text = "Blue Magic — When Quessa Trepid enters, you may cast target instant or sorcery card from a graveyard, and mana of any type can be spent to cast that spell. If that spell would be put into a graveyard, exile it instead.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.May(listOf(doing(Effect.Cast(target("card"), PlayKind.Cast, bind = "spell", caster = Players.You, manaAsAnyType = true))), Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely)),
            targets = listOf(slot("card", and(or(instant, sorcery), inZone(Zone.Graveyard)))), abilityWord = "Blue Magic"),
    )

    @Test
    fun recoverTheEsper() = card(
        name = "Recover the Esper",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Create a 3/3 blue Robot Warrior artifact creature token. Then if this spell was cast from a graveyard, put two +1/+1 counters on that token.
            Flashback {5}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Robot Warrior", listOf(arcanum.api.Color.Blue), 3, 3, types = listOf(CardType.Artifact, CardType.Creature)), bind = "tokens"),
            ifThen(Condition.CastFrom(Zone.Graveyard, Objects.Self), counters(Objects.Bound("tokens"), 2))),
        Keyword.Flashback(manaCost("{5}{U}")),
    )

    /** "When you lose control of that Equipment this turn, if it's attached to a creature you control, unattach it." (CR 603.7c) */
    @Test
    fun borrowedUniform() = card(
        name = "Borrowed Uniform",
        cost = "{U}",
        type = "Instant",
        text = "Choose target creature you control and target Equipment. Gain control of that Equipment until end of turn. Attach it to the chosen creature. When you lose control of that Equipment this turn, if it's attached to a creature you control, unattach it.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("Equipment")), Duration.EndOfTurn)),
            doing(Effect.Attach(target("Equipment"), Recipients(objects = target("creature")))),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.ControlChange(ObjectFilter.Is(target("Equipment")), from = PlayerFilter.You, to = null)),
                Instructions(steps = listOf(ifThen(Condition.ObjectMatches(target("Equipment"), ObjectFilter.Attached(to = Objects.Each(creatureYouControl))),
                    doing(Effect.Unattach(target("Equipment")))))), Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureYouControl), slot("Equipment", equipment))),
    )

    // CR 302.6 / 502.3 (doesn't untap), 602.5a
    @Test
    fun trappedInTheSanctum() = card(
        name = "Trapped in the Sanctum",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant artifact or creature
            When this Aura enters, tap enchanted permanent.
            Enchanted permanent doesn't untap during its controller's untap step and its activated abilities can't be activated.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(or(artifact, creature))),
        triggered(entersSelf, tapIt(equipped)),
        static(Mechanic.DoesntUntap, Mechanic.CantBeActivated(manaAbilities = true), affects = equipped),
    )

    /** "Choose target spell. Surveil 2, then counter the chosen spell unless its controller pays {1} for each card in your graveyard." */
    // CR 118.12a
    @Test
    fun devouredByTheDeep() = card(
        name = "Devoured by the Deep",
        cost = "{2}{U}",
        type = "Instant",
        text = "Choose target spell. Surveil 2, then counter the chosen spell unless its controller pays {1} for each card in your graveyard. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        spell(
            surveil(2),
            Step.Pay(Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{1}")!!, times = count(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
                payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 614.1a
    @Test
    fun theTideCrystal() = card(
        name = "The Tide Crystal",
        cost = "{2}{U}{U}",
        type = "Legendary Artifact",
        text = """
            Blue spells you cast cost {1} less to cast.
            If an opponent would mill one or more cards, they mill that many cards plus four instead.
            {4}{U}{U}, {T}: Each opponent mills cards equal to the number of cards in your hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(arcanum.api.Color.Blue)), spellFilter, you),
            arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1)))),
        static(Mechanic.Replacement(EventPattern.Mill(PlayerFilter.Opponent), modify = listOf(Modification.AddAmount(n(4))))),
        activated(cost(mana("{4}{U}{U}"), tap), doing(Effect.Mill(Players.Each(PlayerFilter.Opponent), Value.HandSize(Players.You)))),
    )

    /** "Then if it's the first end step of the turn, there is an additional end step after this step." (CR 500.9) */
    @Test
    fun yshraWoodSeer() = card(
        name = "Ysra, Wood Seer",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Cat Druid",
        pt = "3/5",
        text = "At the beginning of your end step, exile target creature you control, then return it to the battlefield under its owner's control. Then if it's the first end step of the turn, there is an additional end step after this step.",
    ) parsesTo abilities(
        triggered(yourEndStep,
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
            ifThen(Condition.Not(Condition.HappenedThisTurn(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any), atLeast = 2, measure = Measure.Events)),
                doing(Effect.AdditionalStep(TurnStep.End))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    /** The revealed cards other than those already handed out. */
    private fun rest(vararg taken: String): Objects =
        if (taken.isEmpty()) Objects.Bound("revealed") else Objects.Except(Objects.Bound("revealed"), Objects.Union(taken.map { Objects.Bound(it) }))
    /** [chooser] chooses one of the revealed cards not [taken] yet, named [into]. */
    private fun pick(into: String, chooser: Players, vararg taken: String): Step =
        Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = rest(*taken)), bind = into, chooser = chooser)
}
