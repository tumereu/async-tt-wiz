package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 08: animal types. Lists of creature types ("Rabbits, Bats, Birds, and Mice", "a Bird, Frog, Otter,
 * or Rat") are any of those types (CR 205.3); "this creature or another Rabbit you control enters"; "you control …
 * without flying"; the villages' restricted mana and type-matters abilities; flicker with counters; damage plus one for
 * some types; "the second time this ability has resolved this turn".
 */
class BlbChunk08TypalTest {
    private fun types(vararg names: String) = ObjectFilter.Or(names.map { sub(it) })
    private val other = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val withoutFlying = ObjectFilter.Not(ObjectFilter.HasKeyword(Keyword.Flying))
    private fun selfOrAnother(filter: ObjectFilter) =
        ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self), ObjectFilter.And(listOf(other) + (filter as ObjectFilter.And).all)))
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
    private val creatureSpell = ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Spell), creature))

    /** "{T}: Add {C}." and "{T}: Add [color]. Spend this mana only to cast a creature spell." of a village (CR 106.6) */
    private fun villageMana(color: String) = listOf(
        Ability.Activated(Cost(listOf(CostPart.TapSelf)),
            steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))), any),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)),
            steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse(color)!!), restriction = creatureSpell), Players.You))), any),
    )

    // CR 603.2; "the second time this ability has resolved this turn" counts this resolution
    @Test
    fun reapriteHostess() = card(
        name = "Reaprite Hostess",
        cost = "{2}{W}",
        type = "Creature — Rabbit Citizen",
        pt = "3/3",
        text = "Whenever this creature or another Rabbit you control enters, target creature you control gets +1/+0 until end of turn. Then draw a card if this is the second time this ability has resolved this turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(selfOrAnother(ObjectFilter.And(listOf(sub("Rabbit"), you))), to = Zone.Battlefield)),
            instructions = Instructions(
                targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(
                    pump(Objects.Target("creature"), 1, 0),
                    Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, one(2)),
                        then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
                ),
            ),
        ),
    )

    // CR 603.10a: the dead creature's mana value as it last existed; "another" card than the one that died
    @Test
    fun magpieRescuer() = card(
        name = "Magpie Rescuer",
        cost = "{2}{W}",
        type = "Creature — Bird Cleric",
        pt = "3/1",
        text = """
            Flying
            Whenever this creature or another creature you control with flying dies, return another target creature card with lesser mana value from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                selfOrAnother(ObjectFilter.And(listOf(creature, you, ObjectFilter.HasKeyword(Keyword.Flying)))),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.EventObject(EventRole.After))),
                    creature,
                    ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Before), Combine.Single)),
                ) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield)))),
            ),
        ),
    )

    @Test
    fun crestedQuartermaster() = card(
        name = "Crested Quartermaster",
        cost = "{4}{W}",
        type = "Creature — Bird Scout",
        pt = "3/4",
        text = """
            Flying
            When this creature enters, put a +1/+1 counter on target creature you control without flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(counters(Objects.Target("creature")), targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, you, withoutFlying))))),
    )

    // CR 603.7: the creature comes back at the next end step with a flying counter (CR 122.1b)
    @Test
    fun deliveranceHeron() = card(
        name = "Deliverance Heron",
        cost = "{3}{W}",
        type = "Creature — Bird Cleric",
        pt = "3/3",
        text = """
            Flash
            Flying
            Whenever this creature or another Bird you control enters, exile up to one target creature you control without flying. Return it to the battlefield under its owner's control with a flying counter on it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(selfOrAnother(ObjectFilter.And(listOf(sub("Bird"), you))), to = Zone.Battlefield)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, you, withoutFlying)), min = 0)),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.CreateDelayedTrigger(
                        Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                        steps(Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield,
                            controller = Players.OwnerOf(Objects.Bound("exiled")),
                            counters = listOf(CountersToPut(CounterKind.Keyword(Keyword.Flying), one()))), postBind = "returned"))),
                    )),
                ),
            ),
        ),
    )

    // CR 603.2: "while you control a token" is checked as it would trigger
    @Test
    fun veteranBurrowguard() = card(
        name = "Veteran Burrowguard",
        cost = "{W}",
        type = "Creature — Rabbit Warrior",
        pt = "1/2",
        text = "Whenever this creature attacks while you control a token, this creature gets +2/+0 until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            instructions = steps(pump(Objects.Self, 2, 0)),
            triggersOnlyIf = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Token), you)), atLeast = 1),
        ),
    )

    // CR 205.3: a list of creature types is any of them
    @Test
    fun glenRallycaller() = card(
        name = "Glen Rallycaller",
        cost = "{1}{W}",
        type = "Creature — Rabbit Warrior",
        pt = "2/3",
        text = """
            Whenever one or more other Rabbits, Bats, Birds, and/or Mice you control enter, scry 1.
            Other Rabbits, Bats, Birds, and Mice you control get +1/+1.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(other, types("Rabbit", "Bat", "Bird", "Mouse"), you)), to = Zone.Battlefield),
                oneOrMore = true),
            instructions = steps(Step.Do(Effect.Scry(Players.You, one()))),
        ),
        Ability.Static(
            listOf(Mechanic.ModifyPowerToughness(one(), one())),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(other, types("Rabbit", "Bat", "Bird", "Mouse"), you)))),
        ),
    )

    @Test
    fun lancekingfisher() = card(
        name = "Lancekingfisher",
        cost = "{3}{U}{U}",
        type = "Creature — Bird Knight",
        pt = "4/5",
        text = """
            Flying
            Whenever another nontoken Bird you control enters, create a 1/1 blue Fish creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(other, ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                sub("Bird"), you)), to = Zone.Battlefield)),
            instructions = steps(Step.Do(Effect.CreateToken(fishToken, count = one(), controller = Players.You))),
        ),
    )

    @Test
    fun cloudhopPair() = card(
        name = "Cloudhop Pair",
        cost = "{4}{U}",
        type = "Creature — Bird Frog",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, exile up to one other target creature you control. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                steps(Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
            )),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(other, creature, you)), min = 0)),
        ),
    )

    // CR 608.2h: the exiled card's types, as it last existed
    @Test
    fun rippleGateway() = card(
        name = "Ripple Gateway",
        cost = "{U}",
        type = "Sorcery",
        text = "Exile target creature you control, then return it to the battlefield under its owner's control. If that creature is a Bird, Frog, Otter, or Rat, draw a card.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
                Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), types("Bird", "Frog", "Otter", "Rat")),
                    then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
            ),
        )),
    )

    // CR 702.8a-like permission for the player's noncreature spells; "Untap them": the creatures pumped
    @Test
    fun glenFloodsinger() = card(
        name = "Glen Floodsinger",
        cost = "{2}{U}",
        type = "Creature — Otter Wizard",
        pt = "2/2",
        text = """
            Flash
            You may cast noncreature spells as though they had flash.
            Whenever you cast a noncreature spell, Birds, Frogs, Otters, and Rats you control get +1/+1 until end of turn. Untap them.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(listOf(Mechanic.CastAsThoughFlash(ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Spell))))),
            affects = Affected.Players(Players.You)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), PlayerFilter.You)),
            instructions = steps(
                pump(Objects.Each(ObjectFilter.And(listOf(types("Bird", "Frog", "Otter", "Rat"), you))), 1, 1),
                Step.Do(Effect.Untap(Objects.Each(ObjectFilter.And(listOf(types("Bird", "Frog", "Otter", "Rat"), you))))),
            ),
        ),
    )

    @Test
    fun glenRotsinger() = card(
        name = "Glen Rotsinger",
        cost = "{1}{B}",
        type = "Creature — Squirrel Warlock",
        pt = "1/3",
        text = """
            Menace
            Whenever this creature attacks, each opponent loses X life and you gain X life, where X is the number of other Squirrels, Bats, Lizards, and Rats you control.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        run {
            val x = Value.Count(Objects.Each(ObjectFilter.And(listOf(other, types("Squirrel", "Bat", "Lizard", "Rat"), you))))
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
                instructions = steps(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), x)), Step.Do(Effect.GainLife(Players.You, x))),
            )
        },
    )

    // CR 608.2h: the sacrificed creature's power as it last existed
    @Test
    fun fennickTheCoiledThought() = card(
        name = "Fennick, the Coiled Thought",
        cost = "{3}{B}",
        type = "Legendary Creature — Rat Warlock",
        pt = "2/4",
        text = """
            Whenever Fennick or another Rat you control enters, create a 1/1 black Snail creature token if you don't control a Snail. Otherwise, put a +1/+1 counter on a Snail you control.
            {U}{B}{R}, Sacrifice a Snail: Fennick deals damage equal to the sacrificed creature's power to each opponent. Then draw cards equal to the sacrificed creature's power.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(selfOrAnother(ObjectFilter.And(listOf(sub("Rat"), you))), to = Zone.Battlefield)),
            instructions = steps(Step.If(
                Condition.Not(Condition.Exists(ObjectFilter.And(listOf(sub("Snail"), you)), atLeast = 1)),
                then = listOf(Step.Do(Effect.CreateToken(creatureToken("Snail", listOf(Color.Black), 1, 1), count = one(), controller = Players.You))),
                otherwise = listOf(
                    Step.Choose(Choice.Objects(ObjectFilter.And(listOf(sub("Snail"), you)), min = one(), max = one()), bind = "chosen", chooser = Players.You),
                    counters(Objects.Bound("chosen")),
                ),
            )),
        ),
        run {
            val power = Value.Of(NumericProperty.Power, Objects.Bound("sacrificed"), Combine.Single)
            Ability.Activated(
                cost = Cost(listOf(mana("{U}{B}{R}"), CostPart.Sacrifice(CostObjects.Choose(sub("Snail"), count = one()), preBind = "sacrificed"))),
                instructions = steps(
                    Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), power, source = Objects.Self)),
                    Step.Do(Effect.Draw(Players.You, power)),
                ),
                rules = any,
            )
        },
    )

    @Test
    fun thornwardCaptain() = card(
        name = "Thornward Captain",
        cost = "{3}{R}",
        type = "Creature — Mouse Soldier",
        pt = "2/3",
        text = "At the beginning of combat on your turn, target creature you control gets +X/+0 until end of turn, where X is this creature's power.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = yourCombat,
            instructions = Instructions(
                targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), one(0))),
                    Affected.Objects(Objects.Target("creature")),
                    Duration.EndOfTurn,
                ))),
            ),
        ),
    )

    // CR 614.1a: damage from creatures of those types is one more
    @Test
    fun glenFlamesinger() = card(
        name = "Glen Flamesinger",
        cost = "{2}{R}",
        type = "Creature — Lizard Warlock",
        pt = "3/3",
        text = "If a Lizard, Mouse, Otter, or Raccoon you control would deal damage to a permanent or player, it deals that much damage plus 1 instead.",
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.Damage(ObjectFilter.And(listOf(types("Lizard", "Mouse", "Otter", "Raccoon"), you)), RecipientFilter()),
                modify = listOf(Modification.AddAmount(one())),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    @Test
    fun glenMightsinger() = card(
        name = "Glen Mightsinger",
        cost = "{G}",
        type = "Creature — Frog Warrior",
        pt = "1/1",
        text = """
            Trample
            Whenever another Frog, Rabbit, Raccoon, or Squirrel you control enters, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(other, types("Frog", "Rabbit", "Raccoon", "Squirrel"), you)), to = Zone.Battlefield)),
            instructions = steps(counters(Objects.Self)),
        ),
    )

    // CR 111.4: a named legendary token
    @Test
    fun maribelHeirToCindercrag() = card(
        name = "Maribel, Heir to Cindercrag",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Mouse Soldier",
        pt = "3/3",
        text = """
            Other Mice you control get +1/+1.
            When Maribel enters, create Cindercrag, a legendary colorless Equipment artifact token with "Equipped creature gets +1/+1 and has vigilance, trample, and haste" and equip {2}.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(), one())),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(other, sub("Mouse"), you))))),
        whenThisEnters(Step.Do(Effect.CreateToken(
            TokenSpec.Defined(FaceDefinition(
                name = "Cindercrag",
                colorIndicator = emptyList(),
                supertypes = listOf(Supertype.Legendary),
                types = listOf(CardType.Artifact),
                subtypes = listOf(Subtype("Equipment")),
                abilities = listOf(
                    Ability.Static(
                        listOf(Mechanic.ModifyPowerToughness(one(), one()), Mechanic.GainAbilities(listOf(Keyword.Vigilance, Keyword.Trample, Keyword.Haste))),
                        affects = Affected.Objects(Objects.AttachedTo(Objects.Self)),
                    ),
                    Keyword.Equip(manaCost("{2}")),
                ),
            )),
            count = one(),
            controller = Players.You,
        ))),
    )

    @Test
    fun plumelineTutor() = card(
        name = "Plumeline Tutor",
        cost = "{1}{W}{U}",
        type = "Creature — Bird Scout",
        pt = "2/3",
        text = """
            Flying
            Whenever this creature or another creature you control with flying enters, put a +1/+1 counter on target creature you control without flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(selfOrAnother(ObjectFilter.And(listOf(creature, you, ObjectFilter.HasKeyword(Keyword.Flying)))),
                to = Zone.Battlefield)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, you, withoutFlying)))),
                steps = listOf(counters(Objects.Target("creature"))),
            ),
        ),
    )

    @Test
    fun seedhuskPage() = card(
        name = "Seedhusk Page",
        cost = "{3}{W/U}",
        type = "Creature — Bird Scout",
        pt = "3/3",
        text = """
            Flying
            Whenever this creature attacks, target creature you control without flying gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, you, withoutFlying)))),
                steps = listOf(pump(Objects.Target("creature"), 1, 1)),
            ),
        ),
    )

    // ---- Villages (CR 106.6: mana spent only on creature spells)

    @Test
    fun lotuspadHamlet() = card(
        name = "Lotuspad Hamlet",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {U}. Spend this mana only to cast a creature spell.
            {U}, {T}: Surveil 2. Activate only if a Bird, Frog, Otter, or Rat entered the battlefield under your control this turn.
        """,
    ) parsesTo villageMana("{U}") + abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{U}"), CostPart.TapSelf)),
            instructions = steps(Step.Do(Effect.Surveil(Players.You, one(2)))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(types("Bird", "Frog", "Otter", "Rat"), you)), to = Zone.Battlefield),
                atLeast = 1, measure = Measure.Events)),
        ),
    )

    @Test
    fun lupinbloomHamlet() = card(
        name = "Lupinbloom Hamlet",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {W}. Spend this mana only to cast a creature spell.
            {1}{W}, {T}, Sacrifice this land: Look at the top six cards of your library. You may reveal a Bat, Bird, Mouse, or Rabbit card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo villageMana("{W}") + abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{1}{W}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = steps(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, one(6), fromTop = true), players = Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(types("Bat", "Bird", "Mouse", "Rabbit"), min = one(0), max = one(), from = Objects.Bound("seen")), bind = "chosen",
                    chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            ),
            rules = any,
        ),
    )

    @Test
    fun mudbankHamlet() = card(
        name = "Mudbank Hamlet",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {B}. Spend this mana only to cast a creature spell.
            {1}{B}, {T}, Sacrifice this land: Return target Bat, Lizard, Rat, or Squirrel card from your graveyard to your hand.
        """,
    ) parsesTo villageMana("{B}") + abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{1}{B}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(types("Bat", "Lizard", "Rat", "Squirrel")) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand)))),
            ),
            rules = any,
        ),
    )

    @Test
    fun acornhollowHamlet() = card(
        name = "Acornhollow Hamlet",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {G}. Spend this mana only to cast a creature spell.
            {G}, {T}: Put a +1/+1 counter on each Frog, Rabbit, Raccoon, or Squirrel you control that entered the battlefield this turn.
        """,
    ) parsesTo villageMana("{G}") + abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{G}"), CostPart.TapSelf)),
            instructions = steps(counters(Objects.Each(ObjectFilter.And(listOf(types("Frog", "Rabbit", "Raccoon", "Squirrel"), you,
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))))))),
            rules = any,
        ),
    )

    @Test
    fun cliffsideHamlet() = card(
        name = "Cliffside Hamlet",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {R}. Spend this mana only to cast a creature spell.
            {R}, {T}: Target Lizard, Mouse, Otter, or Raccoon you control gets +1/+0 and gains haste until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo villageMana("{R}") + abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{R}"), CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(types("Lizard", "Mouse", "Otter", "Raccoon"), you)))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.ModifyPowerToughness(one(1), one(0)), Mechanic.GainAbilities(listOf(Keyword.Haste))),
                    Affected.Objects(Objects.Target("permanent")),
                    Duration.EndOfTurn,
                ))),
            ),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )
}
