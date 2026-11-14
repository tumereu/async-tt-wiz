package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class LciChunk10EffectsTest {
    private val descended = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentCard, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
        to = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)
    private val descendedReminder = "(You descended if a permanent card was put into your graveyard from anywhere.)"
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val gnome = creatureToken("Gnome", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
    private val map = TokenSpec.Predefined("Map")
    private val nextEndStep = stepBegins(TurnStep.End, PlayerFilter.Any)
    private fun counterOnSelfAtEndStepIfDescended() = triggered(yourEndStep, counters(Objects.Self), interveningIf = descended)

    // CR 707.9b, 614.1c: enters as a copy of a land card in any graveyard, tapped only if it does, a Cave as well
    @Test
    fun resoundingHollows() = card(
        name = "Resounding Hollows",
        cost = null,
        type = "Land — Cave",
        text = """
            You may have this land enter tapped as a copy of any land card in a graveyard, except it's a Cave in addition to its other types.
            {T}: Add {C}.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersAsCopy(and(land, inZone(Zone.Graveyard)),
                listOf(CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Cave"))))), optional = true, tapped = true)))),
            affects = Affected.Objects(Objects.Self)),
        addOne("{C}"),
    )

    // CR 701.6b: countering an activated or triggered ability; CR 113.7: its source, which loses all abilities while this creature stays
    // (CR 611.2b)
    @Test
    fun hushwaterBinder() = card(
        name = "Hushwater Binder",
        cost = "{2}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "3/2",
        text = """
            Flash
            When this creature enters, counter up to one target activated or triggered ability. If an ability of an artifact, creature, or planeswalker is countered this way, that permanent loses all abilities for as long as this creature remains on the battlefield. (Mana abilities can't be targeted.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf,
            Step.Bind(Objects.SourceOf(target("ability")), "countered source"),
            doing(Effect.Counter(target("ability"))),
            ifThen(Condition.Not(Condition.ObjectMatches(target("ability"), inZone(Zone.Stack))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.LoseAbilities()), Affected.Objects(Objects.Where(Objects.Bound("countered source"),
                    and(or(artifact, creature, planeswalker), inZone(Zone.Battlefield)))), Duration.UntilLeaves(Objects.Self)))),
            targets = listOf(slot("ability", or(and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
                and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered))), min = 0))),
    )

    // CR 508.1m: an attack trigger; CR 508.4: tokens put onto the battlefield attacking; X counts the counters after the first step
    @Test
    fun ostraKell() = card(
        name = "Ostra Kell, Waning Moon",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "1/2",
        text = "Whenever you attack with one or more non-Gnome creatures, put a +1/+1 counter on Ostra Kell, then create X 1/1 colorless Gnome artifact creature tokens that are tapped and attacking, where X is the number of +1/+1 counters on Ostra Kell.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(and(not(sub("Gnome")), creature, you)), oneOrMore = true),
            counters(Objects.Self),
            doing(Effect.CreateToken(gnome, Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single), Players.You, tapped = true,
                attacking = AttackTarget.Chosen))),
    )

    // CR 106.3: mana produced by a Cave; CR 400.7d: the permanent keeps the mana spent on its spell
    @Test
    fun roostOfGloomwings() = card(
        name = "Roost of Gloomwings",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 1/1 black Bat creature token with flying for each mana from a Cave spent to cast it.
            Whenever a Cave you control enters, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(creatureToken("Bat", listOf(Color.Black), 1, 1, listOf(Keyword.Flying)),
            Value.ManaSpent(of = Objects.Self, source = sub("Cave")), Players.You))),
        triggered(enters(and(sub("Cave"), you)), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 715.3: an adventure; CR 102.2: each opponent who controls an artifact counts once
    @Test
    fun tamsinBoldWanderer() = cardFaces(Layout.Adventure,
        card(name = "Tamsin, Bold Wanderer", cost = "{1}{W}", type = "Legendary Creature — Human Faerie Scout", pt = "2/3",
            text = "Whenever Tamsin attacks, reveal the top card of your library. If it's a creature card with mana value 3 or less, put it into your hand. Otherwise, you may put it into your graveyard.",
        ) to abilities(
            triggered(attacksSelf,
                doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
                ifThen(Condition.ObjectMatches(Objects.Bound("revealed"), and(creature, manaValue(Comparison.AtMost, 3))),
                    move(Objects.Bound("revealed"), Zone.Hand),
                    otherwise = listOf(Step.May(listOf(move(Objects.Bound("revealed"), Zone.Graveyard)), player = Players.You)))),
        ),
        card(name = "Onward Trek", cost = "{G}", type = "Sorcery — Adventure",
            text = "Create X Map tokens, where X is one plus the number of opponents who control an artifact. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(doing(Effect.CreateToken(map, Value.Sum(listOf(n(1), Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Where(Condition.Exists(and(artifact, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))), atLeast = 1)))))))),
                Players.You))),
        ),
    )

    // CR 205.1b: "It's still a land"; CR 111.10s: a Map token
    @Test
    fun driftingMoorage() = card(
        name = "Drifting Moorage",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
            {1}{W}{U}: Until end of turn, this land becomes a 2/3 white and blue Bird creature with flying. It's still a land.
            Whenever this land attacks, create a Map token.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("W", "U"),
        activated(cost(mana("{1}{W}{U}")), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bird")))),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue))),
            Mechanic.SetPowerToughness(n(2), n(3)),
            Mechanic.GainAbilities(listOf(Keyword.Flying)))),
        triggered(attacksSelf, create(map)),
    )

    // CR 707.9b: the copy is an artifact too; CR 603.7: a delayed trigger sacrifices it
    @Test
    fun velka() = card(
        name = "Velka, the Dawn's Brilliance",
        cost = "{U}{R}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/2",
        text = "{U}{R}, {T}: Create a token that's a copy of another target creature or artifact you control, except it's an artifact in addition to its other types. It gains haste. Sacrifice it at the beginning of the next end step.",
    ) parsesTo abilities(
        activated(cost(mana("{U}{R}"), tap),
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("permanent"), listOf(CopyException.Types(add = listOf(CardType.Artifact)))), n(1),
                Players.You, bind = "token")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("token")), Duration.Indefinitely)),
            delayed(nextEndStep, doing(Effect.Sacrifice(Objects.Bound("token")))),
            targets = listOf(slot("permanent", and(another, or(creature, artifact), you)))),
    )

    // CR 614.1c: enters with counters; CR 208.3: "its power" as the trigger resolves
    @Test
    fun tripleClankingHulk() = card(
        name = "Triple Clanking Hulk",
        cost = "{7}",
        type = "Artifact Creature — Gnome",
        pt = "0/0",
        text = """
            This creature enters with three +1/+1 counters on it.
            Whenever this creature enters or attacks, create a number of 1/1 colorless Gnome artifact creature tokens equal to its power.
            {2}, Sacrifice another artifact: Put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(3))))), affects = Affected.Objects(Objects.Self)),
        triggered(entersOrAttacksSelf, doing(Effect.CreateToken(gnome, power(Objects.Self), Players.You))),
        activated(cost(mana("{2}"), sacrificeA(and(another, artifact))), counters(Objects.Self)),
    )

    // CR 120.3: damage to each creature and planeswalker; CR 107.3c: X defined by the text
    @Test
    fun cavernousCollapse() = card(
        name = "Cavernous Collapse",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Cavernous Collapse deals X damage to each creature and each planeswalker, where X is the number of Caves you control plus the number of Cave cards in your graveyard.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(count(and(sub("Cave"), you)), count(inYourGraveyard(sub("Cave"))))),
            toObjects(Objects.Each(or(creature, planeswalker))))),
    )

    // CR 120.3: each opponent and each creature an opponent controls (any number of opponents)
    @Test
    fun quakingPeril() = card(
        name = "Quaking Peril",
        cost = "{R}",
        type = "Sorcery",
        text = "Quaking Peril deals 1 damage to each opponent and each creature they control.",
    ) parsesTo abilities(
        spell(damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent), objects = Objects.Each(and(creature, opponents))))),
    )

    // CR 208.1: the greatest power among Dinosaurs you control, at least 2
    @Test
    fun victoriousCrunch() = card(
        name = "Victorious Crunch",
        cost = "{R}",
        type = "Sorcery",
        text = "Victorious Crunch deals damage to target creature equal to 2 or the greatest power among Dinosaurs you control, whichever is greater.",
    ) parsesTo abilities(
        spell(damage(Value.Max(listOf(n(2), Value.Of(NumericProperty.Power, Objects.Each(and(sub("Dinosaur"), you)), Combine.Greatest))),
            toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 700.11: descended this turn (CR 603.4: checked as the step begins and as it resolves); CR 111.4: the token's ability
    @Test
    fun sporewrathFungoid() = card(
        name = "Sporewrath Fungoid",
        cost = "{3}{B}",
        type = "Creature — Fungus",
        pt = "4/3",
        text = "At the beginning of your end step, if you descended this turn, create a 1/1 black Fungus creature token with \"This token can't block.\" $descendedReminder",
    ) parsesTo abilities(
        triggered(yourEndStep, create(creatureToken("Fungus", listOf(Color.Black), 1, 1, listOf(static(Mechanic.CantBlock(ObjectFilter.Any))))),
            interveningIf = descended),
    )

    // CR 700.11
    @Test
    fun hallowedInGore() = card(
        name = "Hallowed in Gore",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            At the beginning of your end step, if you descended this turn, put a +1/+1 counter on target creature you control. $descendedReminder
            {5}{B}{B}, Sacrifice this enchantment: Create a 4/3 white and black Vampire Demon creature token with flying.
        """,
    ) parsesTo abilities(
        triggered(yourEndStep, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)), interveningIf = descended),
        activated(cost(mana("{5}{B}{B}"), sacrificeSelf), create(creatureToken("Vampire Demon", listOf(Color.White, Color.Black), 4, 3,
            listOf(Keyword.Flying)))),
    )

    // CR 700.11
    @Test
    fun scionOfTheCaldera() = card(
        name = "Scion of the Caldera",
        cost = "{3}{R}",
        type = "Creature — Elemental",
        pt = "3/3",
        text = """
            Trample
            At the beginning of your end step, if you descended this turn, put a +1/+1 counter on this creature. $descendedReminder
        """,
    ) parsesTo abilities(Keyword.Trample, counterOnSelfAtEndStepIfDescended())

    // CR 700.11; CR 119.4: paying life; CR 613.4c: Skeletons get +1/+0
    @Test
    fun remainsOfTheDrowned() = card(
        name = "Remains of the Drowned",
        cost = "{2}{B}",
        type = "Enchantment",
        text = """
            Skeletons you control get +1/+0 and have haste.
            When this enchantment enters, create a 2/2 black Skeleton Pirate creature token.
            At the beginning of your end step, if you descended this turn, you may pay 1 life. If you do, return this enchantment to its owner's hand. $descendedReminder
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Haste), affects = Objects.Each(and(sub("Skeleton"), you))),
        triggered(entersSelf, create(creatureToken("Skeleton Pirate", listOf(Color.Black), 2, 2))),
        triggered(yourEndStep,
            Step.Pay(cost = cost(CostPart.PayLife(n(1))), mandatory = false, payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), move(Objects.Self, Zone.Hand)),
            interveningIf = descended),
    )

    // CR 700.11
    @Test
    fun undergloomGoblinHeadhunter() = card(
        name = "Undergloom Goblin Headhunter",
        cost = "{2}{B}",
        type = "Creature — Goblin Warrior",
        pt = "2/2",
        text = """
            Menace
            At the beginning of your end step, if you descended this turn, put a +1/+1 counter on this creature. $descendedReminder
        """,
    ) parsesTo abilities(Keyword.Menace, counterOnSelfAtEndStepIfDescended())

    // CR 700.11; CR 608.2h: X is the sacrificed creature's power as it last existed
    @Test
    fun dripstoneProwler() = card(
        name = "Dripstone Prowler",
        cost = "{B}",
        type = "Creature — Goblin Rogue",
        pt = "1/1",
        text = """
            Menace
            At the beginning of your end step, if you descended this turn, put a +1/+1 counter on this creature. $descendedReminder
            {2}{B}, Sacrifice this creature: Target creature gets -X/-X until end of turn, where X is this creature's power.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        counterOnSelfAtEndStepIfDescended(),
        activated(cost(mana("{2}{B}"), sacrificeSelf), untilEndOfTurn(target("creature"),
            Mechanic.ModifyPowerToughness(Value.Times(power(Objects.Self), -1), Value.Times(power(Objects.Self), -1))),
            targets = listOf(slot("creature", creature))),
    )
}
