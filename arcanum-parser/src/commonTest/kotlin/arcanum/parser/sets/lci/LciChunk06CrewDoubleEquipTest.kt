package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Doubled
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Color
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class LciChunk06CrewDoubleEquipTest {
    private val equipped = Objects.AttachedTo(Objects.Self)

    // CR 120.4a, 603.2c: excess noncombat damage to one or more creatures your opponents control, once for the batch; CR 702.122 crew
    @Test
    fun cinderwakeGalleon() = card(
        name = "Cinderwake Galleon",
        cost = "{3}{R}{R}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            When this Vehicle enters, it deals 5 damage to target creature an opponent controls.
            Whenever one or more creatures your opponents control are dealt excess noncombat damage, create a Treasure token.
            Crew 2
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(5), source = Objects.Self)),
            targets = listOf(slot("creature", creatureOpponentControls)),
        ),
        triggered(
            Trigger.On(
                listOf(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = creatureOpponentControls), combat = false, excess = true)),
                oneOrMore = true,
            ),
            createTreasure(),
        ),
        Keyword.Crew(2),
    )

    // CR 305.1, 601.2a: a look permission and play permissions from the top of your library until end of turn
    @Test
    fun theQuarrelsome() = card(
        name = "The Quarrelsome",
        cost = "{2}{U}{R}",
        type = "Legendary Artifact — Vehicle",
        pt = "5/5",
        text = """
            Whenever The Quarrelsome attacks, create a Treasure token. Until end of turn, you may look at the top card of your library any time, and you may play lands and cast spells from the top of your library.
            Crew 3
        """,
    ) parsesTo abilities(
        triggered(
            attacksSelf,
            createTreasure(),
            doing(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayLookAt(Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true))),
                affects = Affected.Players(Players.You),
                duration = Duration.EndOfTurn,
            )),
            untilEndOfTurn(
                Objects.Each(and(land, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))),
                Mechanic.MayPlay(from = Zone.Library, how = PlayKind.Play, topOnly = true, holder = PlayerFilter.You),
            ),
            untilEndOfTurn(
                Objects.Each(and(not(land), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))),
                Mechanic.MayPlay(from = Zone.Library, how = PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            ),
        ),
        Keyword.Crew(3),
    )

    // CR 111.10a Treasure, CR 702.122 crew
    @Test
    fun rattlingOreCart() = card(
        name = "Rattling Ore Cart",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "3/3",
        text = """
            Whenever this Vehicle attacks, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, createTreasure()),
        Keyword.Crew(1),
    )

    // CR 702.122b: a creature that crewed it this turn; CR 701.44 explore
    @Test
    fun undercaveSkiff() = card(
        name = "Undercave Skiff",
        cost = "{1}{U}",
        type = "Artifact — Vehicle",
        pt = "3/4",
        text = """
            Whenever this Vehicle attacks, target creature that crewed it this turn explores. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard.)
            Crew 1
        """,
    ) parsesTo abilities(
        triggered(
            attacksSelf,
            doing(Effect.Action(KeywordAction.Explore(target("creature")))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.CrewedThisTurn(Objects.Self)))),
        ),
        Keyword.Crew(1),
    )

    // CR 502.3, 603.2c: the untap step's untapping, once for the batch; CR 701.10e doubling counters; CR 603.8 a state trigger
    @Test
    fun theAeonAlmanac() = card(
        name = "The Aeon Almanac",
        cost = "{1}",
        type = "Legendary Artifact",
        text = """
            Whenever you untap one or more permanents during your untap step, put that many time counters on The Aeon Almanac.
            {2}, {T}: Double the number of time counters on The Aeon Almanac.
            When there are 1,000 or more time counters on The Aeon Almanac, sacrifice it and each opponent loses 1,000 life.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(listOf(EventPattern.Untap(and(permanentFilter, you))), oneOrMore = true),
            counters(Objects.Self, Value.Count(Objects.EventObject(EventRole.Object)), CounterKind.Time),
            triggersOnlyIf = Condition.And(listOf(Condition.YourTurn, Condition.StepIs(TurnStep.Untap))),
        ),
        activated(cost(mana("{2}"), tap), doing(Effect.Action(KeywordAction.Double(Doubled.Counters(Objects.Self, CounterKind.Time))))),
        Ability.Triggered(
            Trigger.State(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Time, atLeast = 1000))),
            Instructions(steps = listOf(sacrificeIt, loseLife(1000, Players.Each(PlayerFilter.Opponent)))),
        ),
    )

    // CR 601.2f cost reduction; CR 603.10a, 608.2h the total power of the creatures that died as they last existed, CR 111.4; CR 701.10d
    @Test
    fun theSporeboneCrux() = card(
        name = "The Sporebone Crux",
        cost = "{6}{G}{G}",
        type = "Legendary Artifact",
        text = """
            This spell costs {X} less to cast, where X is the greatest power among creatures you control.
            Whenever one or more nontoken creatures you control die, create a green Fungus Dinosaur creature token with base power and toughness each equal to the total power of those creatures.
            {2}, {T}: Double target creature's power until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
        triggered(
            Trigger.On(EventPattern.ZoneChange(and(not(token), creature, you), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            doing(Effect.CreateToken(
                TokenSpec.Defined(
                    FaceDefinition(
                        name = "Fungus Dinosaur Token",
                        colorIndicator = listOf(Color.Green),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Fungus"), Subtype("Dinosaur")),
                        power = PrintedNumber.X,
                        toughness = PrintedNumber.X,
                    ),
                    power = Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Before), Combine.Total),
                    toughness = Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Before), Combine.Total),
                ),
                n(1),
                Players.You,
            )),
        ),
        activated(
            cost(mana("{2}"), tap),
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), n(0))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 301.5c: an Equipment granting two abilities; CR 701.44 explore
    @Test
    fun glimmersporeLamp() = card(
        name = "Glimmerspore Lamp",
        cost = "{G}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has "You may look at the top card of your library any time" and "Whenever this creature attacks, it explores." (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or put it into your graveyard.)
            Equip {2}
        """,
    ) parsesTo abilities(
        static(
            has(playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true)))),
            has(triggered(attacksSelf, doing(Effect.Action(KeywordAction.Explore(Objects.Self))))),
            affects = equipped,
        ),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 611.3a: one static for your turns, another for the others
    @Test
    fun stalkersDartpipe() = card(
        name = "Stalker's Dartpipe",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1.
            Equipped creature has deathtouch during your turn. Otherwise, it has reach.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = equipped),
        static(has(Keyword.Deathtouch), affects = equipped, condition = Condition.YourTurn),
        static(has(Keyword.Reach), affects = equipped, condition = Condition.Not(Condition.YourTurn)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 700.4: put into a graveyard from the battlefield; CR 301.5 equipped creature
    @Test
    fun vexlansSpiritreaver() = card(
        name = "Vexlan's Spiritreaver",
        cost = "{1}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature has vigilance.
            Whenever another artifact or creature is put into a graveyard from the battlefield, put a +1/+1 counter on equipped creature.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(has(Keyword.Vigilance), affects = equipped),
        triggered(
            on(EventPattern.ZoneChange(and(another, or(artifact, creature)), from = Zone.Battlefield, to = Zone.Graveyard)),
            counters(equipped),
        ),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 702.6a: an equip cost of one of two costs (CR 118.1)
    @Test
    fun gorebriarFlail() = card(
        name = "Gorebriar Flail",
        cost = "{B}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+1.
            Equip—Pay {3} or discard a card.
        """,
    ) parsesTo abilities(
        static(pt(2, 1), affects = equipped),
        Keyword.Equip(Cost(listOf(CostPart.OneOf(listOf(manaCost("{3}"), Cost(listOf(discardACard))))))),
    )

    // CR 301.5c: a granted ability that sacrifices the Equipment granting it
    @Test
    fun dismantlingMallet() = card(
        name = "Dismantling Mallet",
        cost = "{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has "{3}, {T}, Sacrifice Dismantling Mallet: Destroy target artifact or enchantment."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(
            pt(1, 1),
            has(activated(
                cost(mana("{3}"), tap, CostPart.Sacrifice(CostObjects.These(Objects.Granter))),
                destroy(target("permanent")),
                targets = listOf(slot("permanent", or(artifact, enchantment))),
            )),
            affects = equipped,
        ),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 702.12b indestructible; CR 301.5c granted attack trigger
    @Test
    fun gemstoneMattock() = card(
        name = "Gemstone Mattock",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Indestructible (Effects that say "destroy" don't destroy this Equipment.)
            Equipped creature gets +1/+1 and has "Whenever this creature attacks, create a Treasure token." (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        static(pt(1, 1), has(triggered(attacksSelf, createTreasure())), affects = equipped),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 702.8a flash; CR 701.3a attach
    @Test
    fun junglemawScythe() = card(
        name = "Junglemaw Scythe",
        cost = "{2}{G}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +2/+2.
            Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
        static(pt(2, 2), affects = equipped),
        Keyword.Equip(manaCost("{4}")),
    )

    // CR 702.6a, 702.6e: "Equip Pirate" may target only a Pirate you control
    @Test
    fun corsairsTricorne() = card(
        name = "Corsair's Tricorne",
        cost = "{1}{U}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has "Whenever this creature attacks, draw a card, then discard a card."
            Equip Pirate {1}
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), has(triggered(attacksSelf, draw(1), discard(1))), affects = equipped),
        Keyword.Equip(manaCost("{1}"), onto = sub("Pirate")),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 603.12 reflexive trigger; the sacrifice is optional ("you may", a Pay that isn't mandatory, CR 608.2c)
    @Test
    fun dawnflareBrand() = card(
        name = "Dawnflare Brand",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has "Whenever this creature attacks, you may sacrifice Dawnflare Brand. When you do, this creature deals 2 damage to any target."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(
            pt(1, 0),
            has(triggered(
                attacksSelf,
                Step.Pay(cost(CostPart.Sacrifice(CostObjects.These(Objects.Granter))), payer = Players.You, mandatory = false, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(reflexive(damage(2, toAny), targets = listOf(anyTarget)))),
            )),
            affects = equipped,
        ),
        Keyword.Equip(manaCost("{1}")),
    )
}
