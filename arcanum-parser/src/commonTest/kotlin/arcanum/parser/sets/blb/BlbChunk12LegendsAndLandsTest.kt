package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
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
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 12: legendary creatures, artifacts and lands. Comparisons with an opponent (CR 102.3), a colour or
 * creature type chosen as it enters (CR 614.12, 607.2d), mana of a chosen colour, tapping untapped tokens as a cost,
 * a Vehicle that becomes a creature without crew, playing from the top of the library, total power.
 */
class BlbChunk12LegendsAndLandsTest {
    private val yourAbilities = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val entering = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val chosenType = ObjectFilter.Sub(SubtypeRef.Remembered("type"))
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
    private fun tapTokens(n: Int) = CostPart.TapObjects(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Token), one(n)))
    private fun asEnters(step: Step) = Ability.Static(listOf(Mechanic.Replacement(entering, modify = listOf(Modification.AsEnters(listOf(step))))),
        affects = Affected.Objects(Objects.Self))
    private fun opponentHasMore(of: (PlayerFilter) -> Value) = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent,
        PlayerFilter.Where(Condition.Compare(of(PlayerFilter.Is(Players.Tested)), Comparison.Greater, of(PlayerFilter.You))))), atLeast = 1)
    private fun counted(filter: ObjectFilter) = { p: PlayerFilter -> Value.Count(Objects.Each(ObjectFilter.And(listOf(filter, ObjectFilter.ControlledBy(p))))) }

    // CR 102.3: "an opponent" is any one opponent
    @Test
    fun vessaTheLeapingSpring() = card(
        name = "Vessa, the Leaping Spring",
        cost = "{2}{W}{W}",
        type = "Legendary Creature — Elemental Elk",
        pt = "4/5",
        text = "When Vessa enters, create a Treasure token if an opponent controls more lands than you. You gain 4 life if an opponent has more life than you. Create two 1/1 blue Fish creature tokens if an opponent controls more creatures than you. Draw a card if an opponent has more cards in hand than you.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.If(opponentHasMore(counted(land)), then = listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Treasure"), one(), controller = Players.You)))),
            Step.If(Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.Greater, Value.LifeOf(Players.You)))), atLeast = 1),
                then = listOf(Step.Do(Effect.GainLife(Players.You, one(4))))),
            Step.If(opponentHasMore(counted(creature)), then = listOf(Step.Do(Effect.CreateToken(fishToken, one(2), controller = Players.You)))),
            Step.If(Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested),
                Comparison.Greater, Value.HandSize(Players.You))))), atLeast = 1), then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
        ),
    )

    // CR 614.1c, 614.12: tapped, and the colour chosen as it enters
    @Test
    fun unmappedRefuge() = card(
        name = "Unmapped Refuge",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(entering, modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self)),
        asEnters(Step.Choose(Choice.Color(), bind = "color", remember = true, chooser = Players.You)),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)),
            steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(one(), listOf(ColorRef.Remembered("color")))), Players.You))), yourAbilities),
    )

    // CR 605.1a: a mana ability even with its colour choice
    @Test
    fun twinOakHamlet() = card(
        name = "Twin Oak Hamlet",
        cost = null,
        type = "Legendary Land",
        text = """
            As Twin Oak Hamlet enters, choose a creature type.
            {T}: Add {C}.
            {2}, {T}: Choose a color. Add an amount of mana of that color equal to the number of creatures you control of the chosen type.
        """,
    ) parsesTo abilities(
        asEnters(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)),
            steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))), yourAbilities),
        Ability.Activated(Cost(listOf(mana("{2}"), CostPart.TapSelf)), steps(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, you, chosenType)))),
                listOf(ColorRef.Chosen("color")))), Players.You)),
        ), yourAbilities),
    )

    @Test
    fun quiltedPennant() = card(
        name = "Quilted Pennant",
        cost = "{3}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a creature type.
            Creatures you control of the chosen type get +1/+1.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        asEnters(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(), one())), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, you, chosenType))))),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), steps(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))), yourAbilities),
    )

    // CR 701.23: search; the land put onto the battlefield is untapped if you then control four or more lands
    @Test
    fun storiedPathway() = card(
        name = "Storied Pathway",
        cost = null,
        type = "Land",
        text = "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. Then if you control four or more lands, untap that land.",
    ) parsesTo abilities(
        Ability.Activated(
            Cost(listOf(CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            steps(
                Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), land)), one(), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true), postBind = "land")),
                Step.Do(Effect.Shuffle(Players.You)),
                Step.If(Condition.Exists(ObjectFilter.And(listOf(land, you)), atLeast = 4), then = listOf(Step.Do(Effect.Untap(Objects.Bound("land"))))),
            ),
            yourAbilities,
        ),
    )

    // CR 301.7b: a Vehicle becomes an artifact creature
    @Test
    fun snarlRoller() = card(
        name = "Snarl Roller",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "6/6",
        text = """
            Vigilance
            {3}, {T}: Put a +1/+1 counter on target creature.
            Tap two untapped tokens you control: This Vehicle becomes an artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Activated(Cost(listOf(mana("{3}"), CostPart.TapSelf)),
            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(counters(Objects.Target("creature")))), yourAbilities),
        Ability.Activated(Cost(listOf(tapTokens(2))), steps(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
            Affected.Objects(Objects.Self), Duration.EndOfTurn))), yourAbilities),
    )

    // CR 701.26a: only untapped permanents you control can be tapped for a cost
    @Test
    fun torvinTheHayfork() = card(
        name = "Torvin, the Hayfork",
        cost = "{R}{G}{W}",
        type = "Legendary Creature — Rabbit Warrior",
        pt = "4/3",
        text = """
            Tap two untapped tokens you control: Add one mana of any color.
            Tap three untapped tokens you control: Draw a card.
            Tap four untapped tokens you control: Put three +1/+1 counters on Torvin. It gains trample until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(tapTokens(2))), steps(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))), yourAbilities),
        Ability.Activated(Cost(listOf(tapTokens(3))), steps(Step.Do(Effect.Draw(Players.You, one()))), yourAbilities),
        Ability.Activated(Cost(listOf(tapTokens(4))), steps(counters(Objects.Self, 3), gains(Objects.Self, Keyword.Trample)), yourAbilities),
    )

    // CR 602.5b: a restriction on activating; CR 707.10c: new targets for the copy
    @Test
    fun pippaOtterballAce() = card(
        name = "Pippa, Otterball Ace",
        cost = "{1}{U}",
        type = "Legendary Creature — Otter Wizard",
        pt = "1/3",
        text = """
            Vigilance
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            {T}: Draw a card, then discard a card.
            {2}, {T}: Copy target instant or sorcery spell you control. You may choose new targets for the copy. Activate only if Pippa's power is 3 or greater.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Prowess,
        Ability.Activated(Cost(listOf(CostPart.TapSelf)),
            steps(Step.Do(Effect.Draw(Players.You, one())), Step.Do(Effect.Discard(Players.You, one(), filter = ObjectFilter.Any))), yourAbilities),
        Ability.Activated(
            Cost(listOf(mana("{2}"), CostPart.TapSelf)),
            Instructions(
                targets = listOf(slot("spell", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                    ObjectFilter.Category(ObjectCategory.Spell), you)))),
                steps = listOf(Step.Do(Effect.CopySpell(Objects.Target("spell"), controller = Players.You, newTargets = true))),
            ),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You,
                condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Power(Comparison.AtLeast, one(3)))),
        ),
    )

    // CR 601.2a, 305.1: playing from the top of the library
    @Test
    fun grubbCalamitysSeer() = card(
        name = "Grubb, Calamity's Seer",
        cost = "{B}{G}{U}",
        type = "Legendary Creature — Frog Wizard Noble",
        pt = "2/4",
        text = """
            Deathtouch
            You may look at the top card of your library any time.
            You may play lands and cast spells with mana value 4 or greater from the top of your library.
            {T}: Surveil 2.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, one(), fromTop = true))), affects = Affected.Players(Players.You)),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(land, ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.ManaValue(Comparison.AtLeast, one(4)),
                ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), steps(Step.Do(Effect.Surveil(Players.You, one(2)))), yourAbilities),
    )

    // CR 608.2c: the total power is checked after the counters are put
    @Test
    fun arloSharpArcher() = card(
        name = "Arlo, Sharp Archer",
        cost = "{G}{W}",
        type = "Legendary Creature — Rabbit Archer",
        pt = "2/2",
        text = """
            Reach, vigilance
            Whenever Arlo attacks, put a +1/+1 counter on each other creature you control that's a token or a Rabbit. Then if creatures you control have total power 10 or greater, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        Ability.Triggered(
            Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            steps(
                counters(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you,
                    ObjectFilter.Or(listOf(ObjectFilter.Category(ObjectCategory.Token), sub("Rabbit"))))))),
                Step.If(Condition.Compare(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Total), Comparison.AtLeast, one(10)),
                    then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
            ),
        ),
    )
}
