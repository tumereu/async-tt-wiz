package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 35: creatures with triggers on dying ("dies" is put into a graveyard from the battlefield, CR 700.4). "This
 * creature or another creature you control dies"; drain and card draw on each death; tokens made on death, counted from
 * the creature's +1/+1 counters or power as it last existed (CR 113.7a, 608.2h); returning "another target" artifact card,
 * which can't be the card the creature just became (CR 400.7); "you may pay {X}" for a card of mana value X; each player
 * searching in turn (CR 101.4); "whenever you sacrifice a permanent" (CR 701.21a); devotion as a characteristic-defining
 * ability (CR 604.3, 700.5); creatures that enter with X +1/+1 counters (CR 107.3m).
 */
class Chunk35DeathTriggersTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val artifact = ObjectFilter.Type(CardType.Artifact)
    private val self = ObjectFilter.Is(Objects.Self)
    private val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val nontoken = ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token))
    private val zombieType = ObjectFilter.Sub(Subtype("Zombie"))
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** "When [objects] die" (CR 700.4). */
    private fun dies(filter: ObjectFilter) = Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))
    private val thisDies = dies(self)

    /** "this creature or another [kind]". */
    private fun selfOrAnother(vararg kind: ObjectFilter) = ObjectFilter.Or(listOf(
        self,
        ObjectFilter.And(listOf(ObjectFilter.Not(self)) + kind),
    ))

    private fun steps(vararg steps: Step) = Instructions(steps = steps.toList())
    private fun draw(n: Int = 1) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))

    /** "each opponent loses 1 life and you gain 1 life". */
    private val drain = steps(
        Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1))),
        Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))),
    )

    private val putCounterOnSelf = Step.Do(Effect.PutCounters(Objects.Self, plusOne, amount = Value.Fixed(1)))

    /** A number read from this creature; in a dies trigger, as it last existed on the battlefield (CR 113.7a, 608.2h). */
    private fun ofSelf(property: NumericProperty) = Value.Of(property, Objects.Self, Combine.Single)

    /** "This creature enters with X +1/+1 counters on it.": X is the X its spell was cast with (CR 107.3m). */
    private val entersWithXCounters = Ability.Static(listOf(Mechanic.Replacement(
        event = EventPattern.ZoneChange(self, to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(plusOne, Value.CastX(of = Objects.Self))),
    )), affects = Affected.Objects(Objects.Self))

    /** A card in your graveyard. "Another": not the card this creature became as it died (a new object, CR 400.7). */
    private fun cardInYourGraveyard(vararg kind: ObjectFilter, another: Boolean = false) = ObjectFilter.And(
        listOfNotNull(if (another) ObjectFilter.Not(ObjectFilter.Is(Objects.SelfAfter)) else null) + kind +
            listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)),
    )

    /** "When this creature dies, return another target artifact card from your graveyard to your hand." */
    private val returnAnotherArtifactCard = Ability.Triggered(
        trigger = thisDies,
        instructions = Instructions(
            targets = listOf(TargetSlot(
                "card",
                TargetKind.Objects(cardInYourGraveyard(artifact, another = true)),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
        ),
    )

    /** The "any target" slot (CR 115.4). */
    private val anyTarget = TargetSlot(
        name = "any",
        what = TargetKind.Either(
            objects = ObjectFilter.Or(listOf(
                ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Type(CardType.Planeswalker),
                ObjectFilter.Type(CardType.Battle),
            )),
            players = PlayerFilter.Any,
        ),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )

    /** A token described by its characteristics (CR 111.4). */
    private fun token(subtype: String, colors: List<Color>, power: Int, toughness: Int, vararg types: CardType, abilities: List<Ability> = emptyList()) =
        TokenSpec.Defined(FaceDefinition(
            name = "$subtype Token",
            colorIndicator = colors,
            types = types.toList(),
            subtypes = listOf(Subtype(subtype)),
            power = PrintedNumber.Fixed(power),
            toughness = PrintedNumber.Fixed(toughness),
            abilities = abilities,
        ))

    // ---- Drain and draw on each death

    @Test
    fun crimsonMourner() = card(
        name = "Crimson Mourner",
        cost = "{1}{B}",
        type = "Creature — Vampire",
        text = "Whenever this creature or another creature dies, target player loses 1 life and you gain 1 life.",
        pt = "0/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(selfOrAnother(creature)),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    "player",
                    TargetKind.Players(PlayerFilter.Any),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(
                    Step.Do(Effect.LoseLife(Players.Target("player"), Value.Fixed(1))),
                    Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))),
                ),
            ),
        ),
    )

    @Test
    fun harborsideKnifer() = card(
        name = "Harborside Knifer",
        cost = "{1}{B}",
        type = "Creature — Human Rogue Ally",
        text = "Whenever this creature or another creature you control dies, each opponent loses 1 life and you gain 1 life.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(selfOrAnother(creature, yours)),
            instructions = drain,
        ),
    )

    @Test
    fun ghoulishReveler() = card(
        name = "Ghoulish Reveler",
        cost = "{W}{B}",
        type = "Creature — Vampire",
        text = "Whenever this creature or another creature or planeswalker you control dies, each opponent loses 1 life and you gain 1 life.",
        pt = "1/2",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(selfOrAnother(
                ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker))),
                yours,
            )),
            instructions = drain,
        ),
    )

    @Test
    fun undeadSoothsayer() = card(
        name = "Undead Soothsayer",
        cost = "{B}{B}",
        type = "Creature — Zombie Wizard",
        text = "Whenever this creature or another Zombie you control dies, you draw a card and lose 1 life.",
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(selfOrAnother(zombieType, yours)),
            instructions = steps(
                draw(),
                Step.Do(Effect.LoseLife(Players.You, Value.Fixed(1))),
            ),
        ),
    )

    @Test
    fun duskHarvester() = card(
        name = "Dusk Harvester",
        cost = "{2}{B}",
        type = "Creature — Zombie Knight",
        text = "Whenever a nontoken creature you control dies, this creature deals 1 damage to you and you draw a card.",
        pt = "3/2",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(ObjectFilter.And(listOf(nontoken, creature, yours))),
            instructions = steps(
                Step.Do(Effect.DealDamage(
                    Recipients(players = Players.You),
                    amount = Value.Fixed(1),
                    source = Objects.Self,
                )),
                draw(),
            ),
        ),
    )

    // ---- Tokens on death

    @Test
    fun necklessHorseman() = card(
        name = "Neckless Horseman",
        cost = "{2}{B}",
        type = "Creature — Zombie",
        text = "Whenever this creature or another nontoken Zombie you control dies, create a 2/2 black Zombie creature token.",
        pt = "3/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(selfOrAnother(nontoken, zombieType, yours)),
            instructions = steps(Step.Do(Effect.CreateToken(
                token("Zombie", listOf(Color.Black), 2, 2, CardType.Creature),
                count = Value.Fixed(1),
                controller = Players.You,
            ))),
        ),
    )

    /** CR 604.3: the power is a characteristic-defining ability; devotion counts {R} among your permanents' mana costs
     * (CR 700.5). "The creature had power 4 or greater" is the dying creature as it last existed (CR 603.10a). */
    @Test
    fun pyrrhusTemperedInAsh() = card(
        name = "Pyrrhus, Tempered in Ash",
        cost = "{1}{R}{R}",
        type = "Legendary Enchantment Creature — Demigod",
        text = """
            Pyrrhus's power is equal to your devotion to red. (Each {R} in the mana costs of permanents you control counts toward your devotion to red.)
            Whenever Pyrrhus or another nontoken creature you control dies, create a 1/1 red Satyr creature token with "This token can't block." If the creature had power 4 or greater, create two of those tokens instead.
        """,
        pt = "*/3",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.SetPowerToughness(
                power = Value.Devotion(listOf(Color.Red), players = Players.You),
                toughness = null,
            )),
            characteristicDefining = true,
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Triggered(
            trigger = dies(selfOrAnother(nontoken, creature, yours)),
            instructions = steps(Step.If(
                condition = Condition.ObjectMatches(
                    Objects.EventObject(EventRole.Before),
                    ObjectFilter.Power(Comparison.AtLeast, Value.Fixed(4)),
                ),
                then = listOf(Step.Do(Effect.CreateToken(satyr, count = Value.Fixed(2), controller = Players.You))),
                otherwise = listOf(Step.Do(Effect.CreateToken(
                    satyr,
                    count = Value.Fixed(1),
                    controller = Players.You,
                ))),
            )),
        ),
    )

    private val satyr = token("Satyr", listOf(Color.Red), 1, 1, CardType.Creature,
        abilities = listOf(Ability.Static(
            listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)),
            affects = Affected.Objects(Objects.Self),
        )))

    /** The tokens are counted from the counters it had as it last existed (CR 113.7a). */
    @Test
    fun hangarStrider() = card(
        name = "Hangar Strider",
        cost = "{X}{X}",
        type = "Artifact Creature — Construct",
        text = """
            This creature enters with X +1/+1 counters on it.
            When this creature dies, create a 1/1 colorless Thopter artifact creature token with flying for each +1/+1 counter on this creature.
            {1}, {T}: Put a +1/+1 counter on this creature.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        entersWithXCounters,
        Ability.Triggered(
            trigger = thisDies,
            instructions = steps(Step.Do(Effect.CreateToken(
                token("Thopter", emptyList(), 1, 1, CardType.Artifact, CardType.Creature, abilities = listOf(Keyword.Flying)),
                count = ofSelf(NumericProperty.Counters(plusOne)),
                controller = Players.You,
            ))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf)),
            instructions = steps(putCounterOnSelf),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun burrowedShambler() = card(
        name = "Burrowed Shambler",
        cost = "{B}",
        type = "Creature — Zombie",
        text = "When this creature dies, create X tapped 1/1 green Squirrel creature tokens, where X is this creature's power.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = thisDies,
            instructions = steps(Step.Do(Effect.CreateToken(
                token("Squirrel", listOf(Color.Green), 1, 1, CardType.Creature),
                count = ofSelf(NumericProperty.Power),
                tapped = true,
                controller = Players.You,
            ))),
        ),
    )

    // ---- Counters and damage from the dying creature

    @Test
    fun scrapfedDevourer() = card(
        name = "Scrapfed Devourer",
        cost = "{2}",
        type = "Artifact Creature — Beast",
        text = """
            Sacrifice an artifact: Put a +1/+1 counter on this creature.
            Modular 1 (This creature enters with a +1/+1 counter on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(artifact, count = Value.Fixed(1))))),
            instructions = steps(putCounterOnSelf),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Keyword.Modular(1),
    )

    @Test
    fun bazaarbackStrider() = card(
        name = "Bazaarback Strider",
        cost = "{X}{X}",
        type = "Artifact Creature — Construct",
        text = """
            This creature enters with X +1/+1 counters on it.
            {4}: Put a +1/+1 counter on this creature.
            When this creature dies, draw a card for each +1/+1 counter on it.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        entersWithXCounters,
        Ability.Activated(
            cost = manaCost("{4}"),
            instructions = steps(putCounterOnSelf),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Triggered(
            trigger = thisDies,
            instructions = steps(Step.Do(Effect.Draw(Players.You, count = ofSelf(NumericProperty.Counters(plusOne))))),
        ),
    )

    /** CR 701.21a: only a permanent's controller can sacrifice it, so "you sacrifice" is about permanents you control.
     * "It deals damage equal to its power": the creature as it last existed deals it (CR 113.7a). */
    @Test
    fun veloMasterOfCeremonies() = card(
        name = "Velo, Master of Ceremonies",
        cost = "{B}{R}",
        type = "Legendary Creature — Human Shaman",
        text = """
            Whenever you sacrifice a permanent, put a +1/+1 counter on Velo.
            When Velo dies, it deals damage equal to its power to any target.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(yours)),
            instructions = steps(putCounterOnSelf),
        ),
        Ability.Triggered(
            trigger = thisDies,
            instructions = Instructions(
                targets = listOf(anyTarget),
                steps = listOf(Step.Do(Effect.DealDamage(
                    Recipients(target = "any"),
                    amount = ofSelf(NumericProperty.Power),
                    source = Objects.Self,
                ))),
            ),
        ),
    )

    @Test
    fun volatileMyr() = card(
        name = "Volatile Myr",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Myr",
        text = "When this creature dies, it deals 2 damage to any target.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = thisDies,
            instructions = Instructions(
                targets = listOf(anyTarget),
                steps = listOf(Step.Do(Effect.DealDamage(
                    Recipients(target = "any"),
                    amount = Value.Fixed(2),
                    source = Objects.Self,
                ))),
            ),
        ),
    )

    // ---- Returning cards from the graveyard

    @Test
    fun salvageGull() = card(
        name = "Salvage Gull",
        cost = "{3}",
        type = "Artifact Creature — Bird",
        text = """
            Flying
            When this creature dies, return another target artifact card from your graveyard to your hand.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        returnAnotherArtifactCard,
    )

    @Test
    fun myrGleaner() = card(
        name = "Myr Gleaner",
        cost = "{2}",
        type = "Artifact Creature — Myr",
        text = "When this creature dies, return another target artifact card from your graveyard to your hand.",
        pt = "1/1",
    ) parsesTo abilities(
        returnAnotherArtifactCard,
    )

    @Test
    fun foundryApprentice() = card(
        name = "Foundry Apprentice",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        text = "When this creature dies, return another target artifact card from your graveyard to your hand.",
        pt = "1/2",
    ) parsesTo abilities(
        returnAnotherArtifactCard,
    )

    /** One ability with two trigger events; "lesser mana value" is less than the mana value the object that went to the
     * graveyard had on the battlefield (CR 603.10a). */
    @Test
    fun scrapDredger() = card(
        name = "Scrap Dredger",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        text = "Whenever this creature dies or another artifact you control is put into a graveyard from the battlefield, return to your hand target artifact card in your graveyard with lesser mana value.",
        pt = "3/2",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(self, from = Zone.Battlefield, to = Zone.Graveyard),
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(self), artifact, yours)), from = Zone.Battlefield, to = Zone.Graveyard),
            )),
            instructions = Instructions(
                targets = listOf(TargetSlot("card", TargetKind.Objects(ObjectFilter.And(listOf(
                    artifact,
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Before), Combine.Single)),
                ))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
            ),
        ),
    )

    /** "Return it": the card it became in the graveyard (CR 400.7). The target is chosen as the trigger goes on the Stack
     * (CR 603.3d), before {X} is paid, so X is that card's mana value. */
    @Test
    fun unyieldingDead() = card(
        name = "Unyielding Dead",
        cost = "{B}{B}",
        type = "Creature — Zombie",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature dies, you may pay {B}. If you do, return it to its owner's hand.
            When this creature dies, you may pay {X}. If you do, return another target Zombie creature card with mana value X from your graveyard to the battlefield.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = thisDies,
            instructions = steps(
                Step.Pay(manaCost("{B}"), bind = "paid", payer = Players.You),
                Step.If(
                    condition = Condition.Did("paid"),
                    then = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), to = Destination(Zone.Hand)))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = thisDies,
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    "card",
                    TargetKind.Objects(cardInYourGraveyard(zombieType, creature, another = true)),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(
                    Step.Pay(
                        Cost(listOf(CostPart.Mana(
                            cost = ManaCost.parse("{X}")!!,
                            x = Value.Of(NumericProperty.ManaValue, Objects.Target("card"), Combine.Single),
                        ))),
                        bind = "paid",
                        payer = Players.You,
                    ),
                    Step.If(
                        condition = Condition.Did("paid"),
                        then = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Battlefield)))),
                    ),
                ),
            ),
        ),
    )

    // ---- Searching

    @Test
    fun junkyardReassembler() = card(
        name = "Junkyard Reassembler",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        text = """
            Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
            {T}, Sacrifice an artifact: Search your library for a Construct card, reveal it, put it into your hand, then shuffle.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Modular(2),
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.Sacrifice(CostObjects.Choose(artifact, count = Value.Fixed(1))),
            )),
            instructions = steps(
                Step.Do(Effect.Search(
                    Players.You,
                    ObjectFilter.Sub(Subtype("Construct")),
                    reveal = true,
                    bind = "found",
                    max = Value.Fixed(1),
                    zones = listOf(Zone.Library),
                    searcher = Players.You,
                )),
                Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.You)),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    /** CR 101.4: each player in turn order decides and searches, then all the lands enter at once. */
    @Test
    fun seasonedPathfinder() = card(
        name = "Seasoned Pathfinder",
        cost = "{G}",
        type = "Creature — Human Soldier Scout",
        text = "When this creature dies, each player may search their library for up to two basic land cards, put them onto the battlefield, then shuffle.",
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = thisDies,
            instructions = steps(Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Any)),
                bind = "player",
                together = true,
                steps = listOf(Step.May(
                    player = Players.Bound("player"),
                    steps = listOf(
                        Step.Do(Effect.Search(
                            library = Players.Bound("player"),
                            filter = ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))),
                            max = Value.Fixed(2),
                            bind = "found",
                            searcher = Players.Bound("player"),
                            zones = listOf(Zone.Library),
                        )),
                        Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
                        Step.Do(Effect.Shuffle(Players.Bound("player"))),
                    ),
                )),
            )),
        ),
    )
}
