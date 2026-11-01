package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 32: creatures whose enters trigger searches the library for a card to put into the hand (some only if mana of a
 * colour was spent or a card was discarded), makes tokens or token copies, pumps the team, copies a spell, or returns a
 * permanent of your own to its owner's hand; with their other lines: granted keywords, a conditional alternative cost,
 * fabricate, ninjutsu, evoke, and activated abilities that untap, sacrifice Foods or stop opponents' spells.
 */
class Chunk32EntersTutorsTokensAndBounceTest {
    // ---- Shared shapes

    /** "Search your library for [filter], reveal it, put it into your hand, then shuffle." */
    private fun searchToHand(filter: ObjectFilter) = listOf(
        Step.Do(Effect.Search(Players.You, filter, reveal = true, bind = "found")),
        Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Hand))),
        Step.Do(Effect.Shuffle(Players.You)),
    )

    /** "You may search your library for [filter], reveal it, put it into your hand, then shuffle." */
    private fun maySearchToHand(filter: ObjectFilter) = whenThisEnters(Step.May(searchToHand(filter)))

    /** "Return a [filter] you control to its owner's hand.": chosen on resolution, no target. */
    private fun returnYourOwn(filter: ObjectFilter) = whenThisEnters(
        Step.Choose(
            choice = Choice.Objects(allOf(filter, ObjectFilter.ControlledBy(PlayerFilter.You)), min = Value.Fixed(1), max = Value.Fixed(1)),
            bind = "chosen",
        ),
        Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Hand))),
    )

    private val creature = ObjectFilter.Type(CardType.Creature)

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
    )

    // ---- Tutors

    @Test
    fun royalEnlister() = card(
        name = "Royal Enlister",
        cost = "{2}{R}",
        type = "Creature — Human Advisor",
        pt = "1/1",
        text = "When this creature enters, search your library for a creature card with power 2 or less, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        whenThisEnters(*searchToHand(allOf(creature, ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)))).toTypedArray()),
    )

    @Test
    fun enlisterOfTheWatch() = card(
        name = "Enlister of the Watch",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "1/1",
        text = "When this creature enters, you may search your library for a creature card with toughness 2 or less, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        maySearchToHand(allOf(creature, ObjectFilter.Toughness(Comparison.AtMost, Value.Fixed(2)))),
    )

    @Test
    fun rangerMarshalOfDawn() = card(
        name = "Ranger-Marshal of Dawn",
        cost = "{1}{W}{W}",
        type = "Creature — Human Soldier Ranger",
        pt = "3/3",
        text = """
            When this creature enters, you may search your library for a creature card with mana value 1 or less, reveal it, put it into your hand, then shuffle.
            Sacrifice this creature: Your opponents can't cast noncreature spells this turn.
        """,
    ) parsesTo abilities(
        maySearchToHand(allOf(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(1)))),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.CantCast(allOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Spell)))),
                affects = Affected.Players(Players.Each(PlayerFilter.Opponent)),
                duration = Duration.EndOfTurn,
            )))),
        ),
    )

    @Test
    fun arcaneSeeker() = card(
        name = "Arcane Seeker",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "1/1",
        text = "When this creature enters, you may search your library for an instant or sorcery card with mana value 2 or less, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        maySearchToHand(allOf(
            ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
            ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(2)),
        )),
    )

    @Test
    fun anvilbornMystic() = card(
        name = "Anvilborn Mystic",
        cost = "{1}{W}",
        type = "Creature — Kor Artificer",
        pt = "1/2",
        text = """
            When this creature enters, you may search your library for an Equipment card, reveal it, put it into your hand, then shuffle.
            {1}{W}, {T}: You may put an Equipment card from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        maySearchToHand(ObjectFilter.Sub(Subtype("Equipment"))),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{W}")!!), CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(
                Step.Choose(
                    choice = Choice.Objects(
                        filter = allOf(ObjectFilter.Sub(Subtype("Equipment")), ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                        min = Value.Fixed(0),
                        max = Value.Fixed(1),
                    ),
                    bind = "chosen",
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Battlefield))),
            )),
        ),
    )

    // CR 118.12: "you may discard a card. If you do" is a cost paid on resolution.
    @Test
    fun commandingOrator() = card(
        name = "Commanding Orator",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        pt = "2/4",
        text = """
            When this creature enters, you may discard a card. If you do, search your library for a creature card, reveal it, put it into your hand, then shuffle.
            {1}, {T}: Untap another target permanent.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Pay(cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))))), bind = "paid"),
            Step.If(condition = Condition.Did("paid"), then = searchToHand(creature)),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(objectSlot("permanent", allOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Category(ObjectCategory.Permanent)))),
                steps = listOf(Step.Do(Effect.Untap(Objects.Target("permanent")))),
            ),
        ),
    )

    // CR 603.4: "if {R}{R} was spent to cast it" is an intervening "if". CR 702.74a: evoke.
    @Test
    fun vivacity() = card(
        name = "Vivacity",
        cost = "{3}{R/G}{R/G}",
        type = "Creature — Elemental Incarnation",
        pt = "4/4",
        text = """
            When this creature enters, if {R}{R} was spent to cast it, this creature deals 3 damage to any target.
            When this creature enters, if {G}{G} was spent to cast it, search your library for a land card, reveal it, put it into your hand, then shuffle. You gain 2 life.
            Evoke {R/G}{R/G}
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(3))),
            targets = listOf(anyTarget),
            interveningIf = Condition.Compare(Value.ManaSpent(color = Color.Red), Comparison.AtLeast, Value.Fixed(2)),
        ),
        whenThisEnters(
            *(searchToHand(ObjectFilter.Type(CardType.Land)) + Step.Do(Effect.GainLife(Players.You, Value.Fixed(2)))).toTypedArray(),
            interveningIf = Condition.Compare(Value.ManaSpent(color = Color.Green), Comparison.AtLeast, Value.Fixed(2)),
        ),
        Keyword.Evoke(manaCost("{R/G}{R/G}")),
    )

    // CR 118.9: an alternative cost; this card has no mana cost, so it is the only way to cast it.
    @Test
    fun bartholomewtheophilustrindleberry() = card(
        name = "Bartholomewtheophilustrindleberry",
        cost = null,
        type = "Legendary Creature — Human Wizard",
        pt = "3/3",
        text = """
            As long as you've discarded a card this turn, you may pay {B/R} to cast this spell.
            When Bartholomewtheophilustrindleberry enters, you may search your library for a card named The Netherworld Recipe Book, reveal it, put it into your hand, then shuffle.
            Sacrifice two Foods: Target creature deals 6 damage to itself.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AlternativeCost(cost = manaCost("{B/R}"))),
            condition = Condition.HappenedThisTurn(EventPattern.Discard(players = PlayerFilter.You)),
        ),
        maySearchToHand(ObjectFilter.Named("The Netherworld Recipe Book")),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Sub(Subtype("Food")), count = Value.Fixed(2))))),
            instructions = Instructions(
                targets = listOf(objectSlot("creature", creature)),
                steps = listOf(Step.Do(Effect.DealDamage(
                    to = Recipients(objects = Objects.Target("creature")),
                    amount = Value.Fixed(6),
                    source = Objects.Target("creature"),
                ))),
            ),
        ),
    )

    // ---- Tokens and the team

    @Test
    fun golemGrafter() = card(
        name = "Golem Grafter",
        cost = "{2}{W}",
        type = "Creature — Phyrexian Human Artificer",
        pt = "1/1",
        text = """
            When this creature enters, create a 3/3 colorless Phyrexian Golem artifact creature token.
            Golems you control have first strike.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
            name = "Phyrexian Golem Token",
            colorIndicator = emptyList(),
            types = listOf(CardType.Artifact, CardType.Creature),
            subtypes = listOf(Subtype("Phyrexian"), Subtype("Golem")),
            power = PrintedNumber.Fixed(3),
            toughness = PrintedNumber.Fixed(3),
        ))))),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.FirstStrike))),
            affects = Affected.Objects(Objects.Each(allOf(ObjectFilter.Sub(Subtype("Golem")), ObjectFilter.ControlledBy(PlayerFilter.You)))),
        ),
    )

    // CR 107.3c: X is defined by the text; CR 611.2c: the creatures affected and X are fixed as the effect begins.
    @Test
    fun thunderhoofColossus() = card(
        name = "Thunderhoof Colossus",
        cost = "{5}{G}{G}{G}",
        type = "Creature — Beast",
        pt = "5/5",
        text = """
            Haste
            When this creature enters, creatures you control gain trample and get +X/+X until end of turn, where X is the number of creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        whenThisEnters(Step.Do(Effect.ApplyMechanics(
            mechanics = listOf(
                Mechanic.GainAbilities(listOf(Keyword.Trample)),
                Mechanic.ModifyPowerToughness(
                    power = Value.Count(Objects.Each(allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You)))),
                    toughness = Value.Count(Objects.Each(allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You)))),
                ),
            ),
            affects = Affected.Objects(Objects.Each(allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You)))),
            duration = Duration.EndOfTurn,
        ))),
    )

    // CR 702.123a: fabricate.
    @Test
    fun puppeteersApprentice() = card(
        name = "Puppeteer's Apprentice",
        cost = "{1}{B}",
        type = "Creature — Human Artificer",
        pt = "1/2",
        text = """
            Fabricate 1 (When this creature enters, put a +1/+1 counter on it or create a 1/1 colorless Servo artifact creature token.)
            Whenever another creature or artifact you control is put into a graveyard from the battlefield, each opponent loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Fabricate(1),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = allOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Artifact))),
                    ObjectFilter.ControlledBy(PlayerFilter.You),
                ),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(steps = listOf(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1))))),
        ),
    )

    // CR 702.49a: ninjutsu. CR 508.4: a token put onto the battlefield attacking attacks what its controller chooses.
    @Test
    fun myriadFacedShade() = card(
        name = "Myriad-Faced Shade",
        cost = "{U}",
        type = "Creature — Human Ninja",
        pt = "1/1",
        text = """
            Ninjutsu {2}{U}{U} ({2}{U}{U}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)
            Flying
            When this creature enters from your hand, if it's attacking, create a token that's a copy of another target attacking creature. The token enters tapped and attacking.
        """,
    ) parsesTo abilities(
        Keyword.Ninjutsu(manaCost("{2}{U}{U}")),
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Hand, to = Zone.Battlefield)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Attacking)),
            instructions = Instructions(
                targets = listOf(objectSlot("creature", allOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.HasStatus(Status.Attacking),
                    creature,
                ))),
                steps = listOf(Step.Do(Effect.CreateToken(
                    token = TokenSpec.CopyOf(Objects.Target("creature")),
                    tapped = true,
                    attacking = AttackTarget.Chosen,
                ))),
            ),
        ),
    )

    // CR 707.10c: the copy may get new targets.
    @Test
    fun twincastAdept() = card(
        name = "Twincast Adept",
        cost = "{1}{R}{R}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            Flash
            When this creature enters, copy target instant or sorcery spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        whenThisEnters(
            Step.Do(Effect.CopySpell(Objects.Target("spell"), newTargets = true)),
            targets = listOf(objectSlot("spell", allOf(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                ObjectFilter.Category(ObjectCategory.Spell),
            ))),
        ),
    )

    // ---- Returning your own

    @Test
    fun korCloudangler() = card(
        name = "Kor Cloudangler",
        cost = "{1}{W}",
        type = "Creature — Kor Soldier",
        pt = "2/3",
        text = """
            Flying
            When this creature enters, return a permanent you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        returnYourOwn(ObjectFilter.Category(ObjectCategory.Permanent)),
    )

    @Test
    fun screechingDrakelet() = card(
        name = "Screeching Drakelet",
        cost = "{U}",
        type = "Creature — Drake",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, return a creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        returnYourOwn(creature),
    )

    @Test
    fun silvermaneLion() = card(
        name = "Silvermane Lion",
        cost = "{1}{W}",
        type = "Creature — Cat",
        pt = "2/2",
        text = """
            Flash
            When this creature enters, return a creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        returnYourOwn(creature),
    )

    @Test
    fun stoneshroudGargoyle() = card(
        name = "Stoneshroud Gargoyle",
        cost = "{2}{W}",
        type = "Creature — Gargoyle",
        pt = "3/2",
        text = """
            Flash
            Flying
            When this creature enters, return a creature you control to its owner's hand.
            When this creature enters, exile target card from a graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        returnYourOwn(creature),
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Exile), postBind = "exiled")),
            targets = listOf(objectSlot("card", allOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard)))),
        ),
    )
}
