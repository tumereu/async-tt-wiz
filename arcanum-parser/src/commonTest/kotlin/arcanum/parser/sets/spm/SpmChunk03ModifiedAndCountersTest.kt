package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 03: modified creatures (CR 700.9: counters, Equipment, Auras their controller controls), counters moved, put and doubled (CR 701.10e), damage prevented into counters (CR 615.5), a leaving creature's counters put on another, and a counter for mana.
 */
class SpmChunk03ModifiedAndCountersTest {

    @Test
    fun disguiseWardrobe() = card(
        name = "Disguise Wardrobe",
        cost = "{1}{W}",
        type = "Artifact",
        text = """
            This artifact enters with two +1/+1 counters on it.
            {T}: Move a +1/+1 counter from this artifact onto target creature you control. Activate only as a sorcery.
            Whenever a modified creature you control leaves the battlefield, put a +1/+1 counter on this artifact. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = n(2))),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.MoveCounters(from = Objects.Self, to = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(creature, you))),
            rules = asSorcery,
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = and(creature, you, ObjectFilter.Modified), from = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun argentVixenHiredCaptain() = card(
        name = "Argent Vixen, Hired Captain",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Mercenary Hero",
        pt = "2/3",
        text = """
            When Argent Vixen enters, put a +1/+1 counter on another target creature.
            Whenever Argent Vixen attacks, target modified creature you control gains lifelink until end of turn. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(another, creature))),
        ),
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            untilEndOfTurn(target("creature"), has(Keyword.Lifelink)),
            targets = listOf(slot("creature", and(creature, you, ObjectFilter.Modified))),
        ),
    )

    @Test
    fun updraftWebrunner() = card(
        name = "Updraft Webrunner",
        cost = "{W/U}{W/U}",
        type = "Creature — Spider Human Hero",
        pt = "2/2",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            This creature has flying as long as it's modified. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost(mana("{2}"))),
        static(
            has(Keyword.Flying),
            condition = Condition.ObjectMatches(objects = Objects.Self, filter = ObjectFilter.Modified),
        ),
    )

    @Test
    fun gearwebWalkerChildPiloted() = card(
        name = "Gearweb Walker, Child-Piloted",
        cost = "{3}{W}{U}",
        type = "Legendary Artifact Creature — Spider Hero",
        pt = "4/4",
        text = """
            Vigilance
            When Gearweb Walker enters, put a +1/+1 counter on target creature.
            Whenever a modified creature you control deals combat damage to a player, draw a card. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", creature)),
        ),
        triggered(
            on(EventPattern.Damage(
                    source = and(creature, you, ObjectFilter.Modified),
                    to = RecipientFilter(players = PlayerFilter.Any),
                    combat = true,
                )),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun teyanaHeartOfTheWeave() = card(
        name = "Teyana, Heart of the Weave",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/3",
        text = """
            Whenever you attack, put a +1/+1 counter on target attacking creature.
            Whenever a modified creature you control deals combat damage to a player, exile the top card of your library. You may play that card this turn. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(events = listOf(EventPattern.Attack(you)), oneOrMore = true),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(ObjectFilter.HasStatus(Status.Attacking), creature))),
        ),
        triggered(
            on(EventPattern.Damage(
                    source = and(creature, you, ObjectFilter.Modified),
                    to = RecipientFilter(players = PlayerFilter.Any),
                    combat = true,
                )),
            doing(Effect.Move(
                    objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
            untilEndOfTurn(
                Objects.Bound("exiled"),
                Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You),
            ),
        ),
    )

    @Test
    fun livingsteelShell() = card(
        name = "Livingsteel Shell",
        cost = "{2}{W}{U}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +2/+2 and has "Whenever this creature deals combat damage to a player, draw a card for each modified creature you control." (Equipment, Auras you control, and counters are modifications.)
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(target("creature")))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        static(
            pt(2, 2),
            has(
                triggered(
                    on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
                    doing(Effect.Draw(players = Players.You, count = Value.Count(Objects.Each(and(creature, you, ObjectFilter.Modified))))),
                ),
            ),
            affects = Objects.AttachedTo(Objects.Self),
        ),
        Keyword.Equip(cost(mana("{2}"))),
    )

    @Test
    fun dutifulPrecinctCaptain() = card(
        name = "Dutiful Precinct Captain",
        cost = "{1}{W}",
        type = "Creature — Human Detective",
        pt = "1/1",
        text = """
            This creature enters with a +1/+1 counter on it.
            When this creature leaves the battlefield, put its +1/+1 counters on target creature you control.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = n(1))),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            doing(Effect.PutCounters(
                    objects = target("creature"),
                    kind = p1p1,
                    amount = Value.Of(
                        property = NumericProperty.Counters(p1p1),
                        objects = Objects.EventObject(EventRole.Before),
                        how = Combine.Single,
                    ),
                )),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun pallidBloomDreadMender() = card(
        name = "Pallid Bloom, Dread Mender",
        cost = "{W}{W}{W}{W}{W}",
        type = "Legendary Creature — Symbiote Hero",
        pt = "5/5",
        text = """
            When Pallid Bloom enters, if he was cast, return target creature card from your graveyard to the battlefield.
            If damage would be dealt to Pallid Bloom, prevent that damage and put that many +1/+1 counters on him.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(objects = target("card"), to = Destination(Zone.Battlefield))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            interveningIf = Condition.CastFrom(zone = null, of = Objects.Self),
        ),
        static(
            Mechanic.Prevention(
                event = EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter(ObjectFilter.Is(Objects.Self))),
                additional = Instructions(steps = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = Value.EventAmount)))),
            ),
        ),
    )

    @Test
    fun unbendingResolve() = card(
        name = "Unbending Resolve",
        cost = "{1}{G}",
        type = "Instant",
        text = "Until end of turn, target creature you control gains indestructible and \"Whenever this creature is dealt damage, put that many +1/+1 counters on it.\"",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(
                target("creature"),
                has(
                    Keyword.Indestructible,
                    triggered(
                        on(EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter(ObjectFilter.Is(Objects.Self)))),
                        doing(Effect.PutCounters(objects = Objects.EventObject(EventRole.Object), kind = p1p1, amount = Value.EventAmount)),
                    ),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun steelweaveArachnidPrototypeMkIi() = card(
        name = "Steelweave Arachnid, Prototype Mk II",
        cost = "{3}",
        type = "Legendary Artifact Creature — Spider Hero",
        pt = "2/3",
        text = """
            Vigilance
            {T}: Put a +1/+1 counter on each artifact creature and/or Vehicle you control.
            {2}, Remove two +1/+1 counters from among artifacts you control: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(
            cost(tap),
            doing(Effect.PutCounters(objects = Objects.Each(and(or(and(artifact, creature), sub("Vehicle")), you)), kind = p1p1, amount = n(1))),
        ),
        activated(
            cost(
                mana("{2}"),
                CostPart.RemoveCounters(
                    kind = p1p1,
                    amount = n(2),
                    from = CostObjects.Choose(filter = and(artifact, you), count = null),
                    distributed = true,
                ),
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun roaringOnlookers() = card(
        name = "Roaring Onlookers",
        cost = "{1}{R/G}",
        type = "Creature — Human Citizen",
        pt = "2/2",
        text = "At the beginning of each player's first main phase, that player may put a +1/+1 counter on this creature. If they do, they add {C} for each counter on it.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.PrecombatMain, whose = PlayerFilter.Any)),
            Step.Pay(
                steps = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1)))),
                payer = Players.EventPlayer,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(
                    doing(Effect.AddMana(
                            mana = ManaProduction(
                                ManaKind.Fixed(
                                    mana = ManaCost.parse("{C}")!!,
                                    times = Value.Of(property = NumericProperty.Counters(null), objects = Objects.Self, how = Combine.Single),
                                ),
                            ),
                            players = Players.EventPlayer,
                        )),
                ),
            ),
        ),
    )

    @Test
    fun darioVance() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Dario Vance",
            cost = "{1}{G}",
            type = "Legendary Creature — Human Citizen Hero",
            pt = "1/2",
            text = """
                When Dario Vance enters, put a +1/+1 counter on each of up to two target creatures.
                {3}{R}{G}{W}: Transform Dario Vance. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(
                on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
                doing(Effect.PutCounters(objects = target("creatures"), kind = p1p1, amount = n(1))),
                targets = listOf(slot("creatures", creature, min = 0, max = 2)),
            ),
            activated(cost(mana("{3}{R}{G}{W}")), doing(Effect.Transform(Objects.Self)), rules = asSorcery),
        ),
        card(
            name = "Unrivaled Webstalker",
            cost = "{3}{R}{G}{W}",
            type = "Legendary Creature — Spider Human Hero",
            pt = "4/3",
            text = """
                First strike, haste
                Camouflage — {2}: Put a +1/+1 counter on Unrivaled Webstalker. He gains hexproof and becomes colorless until end of turn.
                Whenever you attack, double the number of each kind of counter on each Spider and legendary creature you control.
            """,
        ) to abilities(
            Keyword.FirstStrike,
            Keyword.Haste,
            Ability.Activated(
                cost = cost(mana("{2}")),
                instructions = Instructions(
                    steps = listOf(
                        doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
                        untilEndOfTurn(Objects.Self, has(Keyword.Hexproof), Mechanic.SetColors(emptyList())),
                    ),
                ),
                rules = anyTime,
                abilityWord = "Camouflage",
            ),
            triggered(
                Trigger.On(events = listOf(EventPattern.Attack(you)), oneOrMore = true),
                doing(Effect.Action(
                        KeywordAction.Double(Doubled.Counters(objects = Objects.Each(and(or(sub("Spider"), and(legendary, creature)), you)), kind = null)),
                    )),
            ),
        ),
    )
}
