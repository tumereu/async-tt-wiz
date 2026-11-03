package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 07: white cards: life for creatures entering, Food, Auras that pump by their attachments and take a player's damage (CR 614.1a), a creature exiled until this leaves (CR 610.3), sacrifice unless tapping two creatures and/or Treasures.
 */
class SpmChunk07WhiteTest {

    @Test
    fun kindlyAuntOdette() = card(
        name = "Kindly Aunt Odette",
        cost = "{W}",
        type = "Legendary Creature — Human Citizen",
        pt = "0/2",
        text = "Whenever another creature you control enters, you gain 1 life. If it's a Spider, put a +1/+1 counter on it.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(another, creature, you), to = Zone.Battlefield)),
            doing(Effect.GainLife(players = Players.You, amount = n(1))),
            Step.If(
                condition = Condition.ObjectMatches(objects = Objects.EventObject(EventRole.After), filter = sub("Spider")),
                then = listOf(
                    doing(Effect.PutCounters(objects = Objects.EventObject(EventRole.After), kind = p1p1, amount = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun plazaPigeon() = card(
        name = "Plaza Pigeon",
        cost = "{W}",
        type = "Creature — Bird",
        pt = "1/1",
        text = """
            Flying
            When this creature leaves the battlefield, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Food"), count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun welcomingBlock() = card(
        name = "Welcoming Block",
        cost = "{3}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, create three 1/1 green and white Human Citizen creature tokens.
            Enchanted land has "{1}, {T}: Target creature gets +1/+1 until end of turn for each creature you control. Activate only as a sorcery."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Human Citizen Token",
                            colorIndicator = listOf(Color.Green, Color.White),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Human"), Subtype("Citizen")),
                            power = PrintedNumber.Fixed(1),
                            toughness = PrintedNumber.Fixed(1),
                        ),
                    ),
                    count = n(3),
                    controller = Players.You,
                )),
        ),
        static(
            has(
                activated(
                    cost(mana("{1}"), tap),
                    untilEndOfTurn(
                        target("creature"),
                        Mechanic.ModifyPowerToughness(
                            power = Value.Count(Objects.Each(and(creature, you))),
                            toughness = Value.Count(Objects.Each(and(creature, you))),
                        ),
                    ),
                    targets = listOf(slot("creature", creature)),
                    rules = asSorcery,
                ),
            ),
            affects = Objects.AttachedTo(Objects.Self),
        ),
    )

    @Test
    fun landlordSKnock() = card(
        name = "Landlord's Knock",
        cost = "{W}",
        type = "Enchantment",
        text = "At the beginning of your end step, you may tap two untapped creatures and/or Treasures you control. If you do, draw a card. Otherwise, sacrifice this enchantment.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            Step.Pay(
                cost = cost(CostPart.TapObjects(CostObjects.Choose(filter = or(creature, sub("Treasure")), count = n(2)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
                otherwise = listOf(doing(Effect.Sacrifice(Objects.Self))),
            ),
        ),
    )

    @Test
    fun dazzlingWebcrawler() = card(
        name = "Dazzling Webcrawler",
        cost = "{1}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/2",
        text = """
            Flash
            {1}: Dazzling Webcrawler gains flying until end of turn.
            {1}, Sacrifice Dazzling Webcrawler: Creatures you control gain hexproof and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        activated(cost(mana("{1}")), untilEndOfTurn(Objects.Self, has(Keyword.Flying))),
        activated(
            cost(mana("{1}"), CostPart.Sacrifice(CostObjects.Self)),
            untilEndOfTurn(Objects.Each(and(creature, you)), has(Keyword.Hexproof, Keyword.Indestructible)),
        ),
    )

    @Test
    fun swiftwingSkyborneFriend() = card(
        name = "Swiftwing, Skyborne Friend",
        cost = "{4}{W}",
        type = "Legendary Creature — Human Hero",
        pt = "3/4",
        text = """
            Flying
            When Swiftwing enters, another target creature you control gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            untilEndOfTurn(target("creature"), has(Keyword.Flying)),
            targets = listOf(slot("creature", and(another, creature, you))),
        ),
    )

    @Test
    fun abruptTakedown() = card(
        name = "Abrupt Takedown",
        cost = "{1}{W}",
        type = "Instant",
        text = "Destroy target attacking or blocking creature.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(target("creature"))),
            targets = listOf(
                slot(
                    "creature",
                    and(or(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Blocking)), creature),
                ),
            ),
        ),
    )

    @Test
    fun zzip() = card(
        name = "Zzip!",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains flying until end of turn. If it's a Spider, you gain 2 life.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(2, 2), has(Keyword.Flying)),
            Step.If(
                condition = Condition.ObjectMatches(objects = target("creature"), filter = sub("Spider")),
                then = listOf(doing(Effect.GainLife(players = Players.You, amount = n(2)))),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun cocoonedAway() = card(
        name = "Cocooned Away",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(objects = target("permanent"), to = Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(not(land), ObjectFilter.Category(ObjectCategory.Permanent), opponents))),
        ),
    )

    @Test
    fun wristSpinnerets() = card(
        name = "Wrist Spinnerets",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has reach and "Whenever this creature attacks, tap target creature an opponent controls."
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(
            pt(1, 1),
            has(
                Keyword.Reach,
                triggered(
                    on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
                    doing(Effect.Tap(target("creature"))),
                    targets = listOf(slot("creature", and(creature, opponents))),
                ),
            ),
            affects = Objects.AttachedTo(Objects.Self),
        ),
        Keyword.Equip(cost(mana("{2}"))),
    )

    @Test
    fun feralPackMercenaries() = card(
        name = "Feral Pack Mercenaries",
        cost = "{2}{W}",
        type = "Creature — Human Mercenary",
        pt = "2/3",
        text = "At the beginning of combat on your turn, up to one target creature gains first strike and vigilance until end of turn.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            untilEndOfTurn(target("creature"), has(Keyword.FirstStrike, Keyword.Vigilance)),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
    )

    @Test
    fun burdenOfMight() = card(
        name = "Burden of Might . . .",
        cost = "{3}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            Enchanted creature gets +2/+2 for each Aura and Equipment attached to it.
            All damage that would be dealt to you is dealt to enchanted creature instead.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(and(creature, you))),
        static(
            Mechanic.ModifyPowerToughness(
                power = Value.Times(
                    value = Value.Count(Objects.AttachmentsOf(of = Objects.AttachedTo(Objects.Self), filter = or(sub("Aura"), sub("Equipment")))),
                    factor = 2,
                ),
                toughness = Value.Times(
                    value = Value.Count(Objects.AttachmentsOf(of = Objects.AttachedTo(Objects.Self), filter = or(sub("Aura"), sub("Equipment")))),
                    factor = 2,
                ),
            ),
            affects = Objects.AttachedTo(Objects.Self),
        ),
        static(
            Mechanic.Replacement(
                event = EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter(players = PlayerFilter.You)),
                modify = listOf(Modification.ChangeRecipient(Recipients(Objects.AttachedTo(Objects.Self)))),
            ),
        ),
    )
}
