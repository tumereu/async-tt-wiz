package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 06: copies of creatures, tokens and abilities (CR 707), cards played from the library, exile and elsewhere (CR 601.2a, 305.1), "first or second card each turn", the legend rule for Spiders (CR 704.5j), turn counts ("your first, second, or third turns") and mana for spells cast from exile (CR 106.6).
 */
class SpmChunk06CopiesAndElsewhereTest {

    @Test
    fun doubtingDoppelgangers() = card(
        name = "Doubting Doppelgangers",
        cost = "{4}{U}{U}",
        type = "Enchantment",
        text = "Whenever a nontoken creature you control deals combat damage to a player, create a token that's a copy of it, except it isn't legendary.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Damage(
                    source = and(not(ObjectFilter.Category(ObjectCategory.Token)), creature, you),
                    to = RecipientFilter(players = PlayerFilter.Any),
                    combat = true,
                )),
            doing(Effect.CreateToken(
                    token = TokenSpec.CopyOf(
                        objects = Objects.EventObject(EventRole.Source),
                        except = listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary))),
                    ),
                    count = n(1),
                    controller = Players.You,
                )),
        ),
    )

    @Test
    fun hyenaGeneSplicer() = card(
        name = "Hyena, Gene Splicer",
        cost = "{G}{U}",
        type = "Legendary Creature — Human Scientist Villain",
        pt = "1/1",
        text = """
            Trample
            Whenever you cast a creature spell with mana value equal to Hyena's power, copy that spell, except the copy isn't legendary. Then put a +1/+1 counter on Hyena. (The copy becomes a token.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.Cast(
                    spell = and(
                        creature,
                        ObjectFilter.Category(ObjectCategory.Spell),
                        ObjectFilter.ManaValue(
                            comparison = Comparison.Equal,
                            value = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                        ),
                    ),
                    by = PlayerFilter.You,
                )),
            doing(Effect.CopySpell(
                    objects = Objects.EventObject(EventRole.Object),
                    controller = Players.You,
                    newTargets = false,
                    except = listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary))),
                )),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun usurperOfTheMask() = card(
        name = "Usurper of the Mask",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "4/4",
        text = "Mind Swap — You may have Usurper of the Mask enter as a copy of any creature card in a graveyard, except his name is Usurper of the Mask and he's a 4/4 Spider Human Hero in addition to his other types. When you do, exile that card.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                    modify = listOf(
                        Modification.EntersAsCopy(
                            of = and(creature, inZone(Zone.Graveyard)),
                            except = listOf(
                                CopyException.Name("Usurper of the Mask"),
                                CopyException.PowerToughness(power = 4, toughness = 4),
                                CopyException.Types(
                                    addSubtypes = listOf(
                                        SubtypeRef.Literal(Subtype("Spider")),
                                        SubtypeRef.Literal(Subtype("Human")),
                                        SubtypeRef.Literal(Subtype("Hero")),
                                    ),
                                ),
                            ),
                            optional = true,
                            bind = "copied",
                        ),
                    ),
                    whenApplied = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Bound("copied"), to = Destination(Zone.Exile))))),
                ),
            ),
            affects = Affected.Objects(Objects.Self),
            abilityWord = "Mind Swap",
        ),
    )

    @Test
    fun inkboundWebslinger() = card(
        name = "Inkbound Webslinger",
        cost = "{2}{U/B}",
        type = "Legendary Creature — Symbiote Spider Hero",
        pt = "2/4",
        text = """
            Whenever this creature deals combat damage to a player, look at that many cards from the top of your library. Put one of them into your hand and the rest into your graveyard.
            Find New Host — {2}{U/B}, Exile this card from your graveyard: Put a +1/+1 counter on target creature you control. It gains this card's other abilities. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            doing(Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.You, count = Value.EventAmount, fromTop = true),
                    players = Players.You,
                    bind = "seen",
                )),
            Step.Choose(
                choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("seen")),
                bind = "kept",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = Objects.Bound("kept"), to = Destination(Zone.Hand))),
            doing(Effect.Move(
                    objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("kept")),
                    to = Destination(Zone.Graveyard),
                )),
        ),
        Ability.Activated(
            cost = cost(mana("{2}{U/B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard)),
            instructions = Instructions(
                targets = listOf(slot("creature", and(creature, you))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(Effect.ApplyMechanics(
                            mechanics = listOf(
                                has(
                                    // the other ability, with its printed line (CR 113.10)
                                    lookAtThatManyText(triggered(
                                        combatDamageToAPlayer,
                                        doing(Effect.LookAt(
                                                objects = Objects.LibraryCards(of = Players.You, count = Value.EventAmount, fromTop = true),
                                                players = Players.You,
                                                bind = "seen",
                                            )),
                                        Step.Choose(
                                            choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("seen")),
                                            bind = "kept",
                                            chooser = Players.You,
                                        ),
                                        doing(Effect.Move(objects = Objects.Bound("kept"), to = Destination(Zone.Hand))),
                                        doing(Effect.Move(
                                                objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("kept")),
                                                to = Destination(Zone.Graveyard),
                                            )),
                                    )),
                                ),
                            ),
                            affects = Affected.Objects(target("creature")),
                            duration = Duration.Indefinitely,
                        )),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Find New Host",
        ),
    )

    @Test
    fun freelancerSShutterbox() = card(
        name = "Freelancer's Shutterbox",
        cost = "{1}",
        type = "Artifact",
        text = """
            This artifact enters with three film counters on it.
            {2}, {T}, Remove a film counter from this artifact: Copy target activated or triggered ability you control. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = CounterKind.Named("film"), amount = n(3))),
            ),
        ),
        activated(
            cost(
                mana("{2}"),
                tap,
                CostPart.RemoveCounters(kind = CounterKind.Named("film"), amount = n(1), from = CostObjects.Self),
            ),
            doing(Effect.CopySpell(objects = target("ability"), controller = Players.You, newTargets = true)),
            targets = listOf(
                slot(
                    "ability",
                    and(
                        or(ObjectFilter.AbilityOf(AbilityKind.Activated), ObjectFilter.AbilityOf(AbilityKind.Triggered)),
                        you,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun weaveOfWorlds() = card(
        name = "Weave of Worlds",
        cost = "{3}{R}{R}",
        type = "Enchantment",
        text = """
            The "legend rule" doesn't apply to Spiders you control.
            Whenever you cast a spell from anywhere other than your hand, you may copy it. If you do, you may choose new targets for the copy. If the copy is a permanent spell, it gains haste. Do this only once each turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.LegendRuleDoesntApply, affects = Objects.Each(and(sub("Spider"), you))),
        Ability.Triggered(
            trigger = on(EventPattern.Cast(spell = not(ObjectFilter.WasCast(Zone.Hand)), by = PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        steps = listOf(
                            doing(Effect.CopySpell(objects = Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true, bind = "copy")),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.And(
                            listOf(
                                Condition.Did("paid"),
                                Condition.ObjectMatches(
                                    objects = Objects.Bound("copy"),
                                    filter = or(
                                        artifact,
                                        ObjectFilter.Type(CardType.Battle),
                                        creature,
                                        enchantment,
                                        land,
                                        ObjectFilter.Type(CardType.Planeswalker),
                                    ),
                                ),
                            ),
                        ),
                        then = listOf(doing(Effect.SpellGets(objects = Objects.Bound("copy"), mechanics = listOf(has(Keyword.Haste))))),
                    ),
                ),
            ),
            actionOncePerTurn = true,
        ),
    )

    @Test
    fun madameGrappleBrilliantTinker() = card(
        name = "Madame Grapple, Brilliant Tinker",
        cost = "{U}",
        type = "Legendary Creature — Human Scientist Villain",
        pt = "0/2",
        text = """
            Whenever you draw your first or second card each turn, put an ingenuity counter on Madame Grapple.
            {T}: You may cast an artifact spell from your hand with mana value less than or equal to the number of ingenuity counters on Madame Grapple without paying its mana cost.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(events = listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 2, throughNth = true),
            doing(Effect.PutCounters(objects = Objects.Self, kind = CounterKind.Named("ingenuity"), amount = n(1))),
        ),
        activated(
            cost(tap),
            Step.Choose(
                choice = Choice.Objects(
                    filter = and(
                        artifact,
                        inZone(Zone.Hand),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                        ObjectFilter.ManaValue(
                            comparison = Comparison.AtMost,
                            value = Value.Of(
                                property = NumericProperty.Counters(CounterKind.Named("ingenuity")),
                                objects = Objects.Self,
                                how = Combine.Single,
                            ),
                        ),
                    ),
                    min = n(0),
                    max = n(1),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Cast(objects = Objects.Bound("chosen"), how = PlayKind.Cast, withoutPaying = true, caster = Players.You)),
        ),
    )

    @Test
    fun oracleOfThreadsSeer() = card(
        name = "Oracle of Threads, Seer",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Mutant Advisor",
        pt = "4/4",
        text = """
            You may look at the top card of your library any time.
            You may cast Spider spells and noncreature spells from the top of your library.
            Whenever you attack, you may mill a card. (You may put the top card of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true))),
        static(
            Mechanic.MayPlay(from = Zone.Library, how = PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(or(sub("Spider"), not(creature)), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))),
        ),
        triggered(
            Trigger.On(events = listOf(EventPattern.Attack(you)), oneOrMore = true),
            Step.May(steps = listOf(doing(Effect.Mill(players = Players.You, count = n(1)))), player = Players.You),
        ),
    )

    @Test
    fun velvetProwlSlyBurglar() = card(
        name = "Velvet Prowl, Sly Burglar",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Human Rogue Villain",
        pt = "2/3",
        text = "When Velvet Prowl enters, look at the top nine cards of target opponent's library, exile two of them face down, then put the rest on the bottom of their library in a random order. You may play the exiled cards for as long as they remain exiled. Mana of any type can be spent to cast spells this way.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.Target("opponent"), count = n(9), fromTop = true),
                    players = Players.You,
                    bind = "seen",
                )),
            Step.Choose(
                choice = Choice.Objects(filter = ObjectFilter.Any, min = n(2), max = n(2), from = Objects.Bound("seen")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(zone = Zone.Exile, faceDown = true), postBind = "exiled")),
            doing(Effect.Move(
                    objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("chosen")),
                    to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                )),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, manaAsAnyColor = true, holder = PlayerFilter.You),
                    ),
                    affects = Affected.Objects(Objects.Bound("exiled")),
                    duration = Duration.Indefinitely,
                )),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )

    @Test
    fun inkspiderWithoutMercy() = card(
        name = "Inkspider, Without Mercy",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Symbiote Spider Hero",
        pt = "4/4",
        text = """
            Deathtouch, lifelink
            Whenever Inkspider attacks, until end of turn, you may look at the top card of your library any time and you may play cards from the top of your library. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Lifelink,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
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
                Mechanic.MayPlay(
                    from = Zone.Library,
                    how = PlayKind.Cast,
                    topOnly = true,
                    cost = cost(
                        CostPart.PayLife(Value.Of(property = NumericProperty.ManaValue, objects = Objects.Affected, how = Combine.Single)),
                    ),
                    holder = PlayerFilter.You,
                ),
            ),
        ),
    )

    @Test
    fun wrenAshby() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Wren Ashby",
            cost = "{1}{R}",
            type = "Legendary Creature — Human Performer Hero",
            pt = "2/1",
            text = """
                When Wren Ashby enters, exile the top card of your library. You may play that card for as long as you control this creature.
                {2}{U}{R}{W}: Transform Wren Ashby. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(
                on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
                doing(Effect.Move(
                        objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                        to = Destination(Zone.Exile),
                        postBind = "exiled",
                    )),
                doing(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                        affects = Affected.Objects(Objects.Bound("exiled")),
                        duration = Duration.AsLongAs(Condition.ObjectMatches(objects = Objects.Self, filter = you)),
                    )),
            ),
            activated(cost(mana("{2}{U}{R}{W}")), doing(Effect.Transform(Objects.Self)), rules = asSorcery),
        ),
        card(
            name = "Phantom Webdancer",
            cost = "{2}{U}{R}{W}",
            type = "Legendary Creature — Spider Human Hero",
            pt = "4/4",
            text = """
                Flying, vigilance, haste
                Whenever you play a land from exile or cast a spell from exile, put a +1/+1 counter on Phantom Webdancer.
                Remove two counters from Phantom Webdancer: Exile the top card of your library. You may play that card this turn.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Vigilance,
            Keyword.Haste,
            triggered(
                Trigger.On(
                    listOf(
                        EventPattern.PlayLand(lands = land, players = PlayerFilter.You, from = Zone.Exile),
                        EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You, from = Zone.Exile),
                    ),
                ),
                doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            ),
            activated(
                cost(CostPart.RemoveCounters(kind = null, amount = n(2), from = CostObjects.Self)),
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
        ),
    )

    @Test
    fun lesserRivalsOfTheWeave() = card(
        name = "Lesser Rivals of the Weave",
        cost = "{2}{R}",
        type = "Creature — Human Rogue Villain",
        pt = "3/3",
        text = """
            Trample
            Whenever you cast a spell with mana value 4 or greater, you may exile the top card of your library. If you do, you may play that card until you exile another card with this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.Cast(
                    spell = and(
                        ObjectFilter.Category(ObjectCategory.Spell),
                        ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                    ),
                    by = PlayerFilter.You,
                )),
            Step.Pay(
                steps = listOf(
                    doing(Effect.Move(
                            objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                            to = Destination(Zone.Exile),
                            postBind = "exiled",
                        )),
                ),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(
                    doing(Effect.Remember(key = "exiled", objects = Objects.Bound("exiled"), replace = true)),
                    doing(Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                            affects = Affected.Objects(Objects.Bound("exiled")),
                            duration = Duration.AsLongAs(
                                Condition.ObjectMatches(objects = Objects.Bound("exiled"), filter = ObjectFilter.Is(Objects.Remembered("exiled"))),
                            ),
                        )),
                ),
            ),
        ),
    )

    @Test
    fun loomwrightAndHatchling() = card(
        name = "Loomwright and Hatchling",
        cost = "{R}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "1/2",
        text = """
            Whenever you attack with two or more Spiders, put a +1/+1 counter on Loomwright and Hatchling.
            Whenever Loomwright and Hatchling deals 4 or more damage, exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(events = listOf(EventPattern.Attack(and(sub("Spider"), you))), oneOrMore = true, atLeast = 2),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
        triggered(
            Trigger.On(
                events = listOf(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter())),
                oneOrMore = true,
            ),
            doing(Effect.Move(
                    objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                    affects = Affected.Objects(Objects.Bound("exiled")),
                    duration = Duration.UntilEndOfYourNextTurn,
                )),
            triggersOnlyIf = Condition.Compare(left = Value.EventAmount, comparison = Comparison.AtLeast, right = n(4)),
        ),
    )

    @Test
    fun webrunnerOfTomorrow() = card(
        name = "Webrunner of Tomorrow",
        cost = "{U}{R}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "2/3",
        text = """
            From the Future — You can't cast Webrunner of Tomorrow during your first, second, or third turns of the game.
            Double strike, vigilance
            At the beginning of your end step, if you've played a land or cast a spell this turn from anywhere other than your hand, Webrunner of Tomorrow deals damage equal to his power to any target.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.CastRestriction(
                    condition = Condition.Not(
                        Condition.And(
                            listOf(
                                Condition.YourTurn,
                                Condition.Compare(left = Value.TurnsTaken(Players.You), comparison = Comparison.AtMost, right = n(3)),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(Objects.Self),
            abilityWord = "From the Future",
        ),
        Keyword.DoubleStrike,
        Keyword.Vigilance,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.DealDamage(
                    to = Recipients(target = "any"),
                    amount = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                    source = Objects.Self,
                )),
            targets = listOf(
                TargetSlot(
                    name = "any",
                    what = TargetKind.Either(
                        objects = or(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)),
                        players = PlayerFilter.Any,
                    ),
                    min = n(1),
                    max = n(1),
                ),
            ),
            interveningIf = Condition.Or(
                listOf(
                    Condition.HappenedThisTurn(
                        event = EventPattern.PlayLand(lands = land, players = PlayerFilter.You, notFrom = Zone.Hand),
                        atLeast = 1,
                        measure = Measure.Events,
                    ),
                    Condition.HappenedThisTurn(
                        event = EventPattern.Cast(spell = not(ObjectFilter.WasCast(Zone.Hand)), by = PlayerFilter.You),
                        atLeast = 1,
                        measure = Measure.Events,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun gremlinSLingeringDread() = card(
        name = "Gremlin's Lingering Dread",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            Unreliable Visions — At the beginning of your first main phase, discard a card. If you do, draw a card.
            Undying Vengeance — Whenever you play a land or cast a spell from anywhere other than your hand, this enchantment deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.PrecombatMain, whose = PlayerFilter.You)),
            Step.Pay(
                cost = cost(
                    CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                ),
                payer = Players.You,
                mandatory = true,
                bind = "paid",
            ),
            Step.If(condition = Condition.Did("paid"), then = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
            abilityWord = "Unreliable Visions",
        ),
        triggered(
            Trigger.On(
                listOf(
                    EventPattern.PlayLand(lands = land, players = PlayerFilter.You, notFrom = Zone.Hand),
                    EventPattern.Cast(spell = not(ObjectFilter.WasCast(Zone.Hand)), by = PlayerFilter.You),
                ),
            ),
            doing(Effect.DealDamage(to = Recipients(players = Players.Each(PlayerFilter.Opponent)), amount = n(1), source = Objects.Self)),
            abilityWord = "Undying Vengeance",
        ),
    )

    @Test
    fun crossworldChronometer() = card(
        name = "Crossworld Chronometer",
        cost = "{4}",
        type = "Artifact",
        text = """
            When this artifact enters, exile the top two cards of your library. Until the end of your next turn, you may play those cards.
            {T}: Add two mana in any combination of colors. Spend this mana only to cast spells from exile.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(
                    objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                    affects = Affected.Objects(Objects.Bound("exiled")),
                    duration = Duration.UntilEndOfYourNextTurn,
                )),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(
                    mana = ManaProduction(
                        kind = ManaKind.Choose(amount = n(2), combination = true),
                        restriction = and(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.WasCast(Zone.Exile)),
                    ),
                    players = Players.You,
                )),
        ),
    )
}
