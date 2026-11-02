package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Timing
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
 * Bloomburrow chunk 09: graveyards and threshold. Threshold is an ability word (CR 207.2c) on abilities that work with
 * seven or more cards in your graveyard; with them: cost reductions counting graveyard and exile cards, casting from
 * the graveyard for free or for life, "cast from anywhere other than your hand", reflexive casts, card types among
 * cards exiled with a creature (CR 607.2a), cards that return from the graveyard tapped and attacking (CR 508.4).
 */
class BlbChunk09GraveyardAndThresholdTest {
    private val yourGraveyardCards = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val threshold = Condition.Exists(yourGraveyardCards, atLeast = 7)
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val instantOrSorcery = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
    private val x = Value.Announced("X")

    /** "If that spell would be put into your graveyard, exile it instead." after casting it (CR 614.1a) */
    private val exileTheSpellInstead = Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        null,
        Duration.Indefinitely,
    ))

    // CR 601.2f: a reduction counting your instants and sorceries; CR 614.1c: tapped unless it's your turn
    @Test
    fun bogswirlCrab() = card(
        name = "Bogswirl Crab",
        cost = "{5}{U}{U}",
        type = "Creature — Elemental Crab",
        pt = "5/5",
        text = """
            Flash
            This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
            This creature enters tapped if it's not your turn.
            When this creature enters, tap up to two target creatures.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!,
            Value.Count(Objects.Each(ObjectFilter.And(listOf(instantOrSorcery) + yourGraveyard)))))), affects = Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self), condition = Condition.Not(Condition.YourTurn)),
        whenThisEnters(Step.Do(Effect.Tap(Objects.Target("creatures"))), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 613.4c: opponents' creatures, while the condition holds
    @Test
    fun thoughtwhisker() = card(
        name = "Thoughtwhisker",
        cost = "{2}{U}",
        type = "Creature — Rat Wizard",
        pt = "3/2",
        text = """
            At the beginning of your upkeep, surveil 1.
            Threshold — As long as there are seven or more cards in your graveyard, creatures your opponents control get -1/-0.
        """,
    ) parsesTo abilities(
        Ability.Triggered(trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.Surveil(Players.You, one())))),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(-1), one(0))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, opponents)))),
            condition = threshold, abilityWord = "Threshold"),
    )

    @Test
    fun duskwhorlRecluse() = card(
        name = "Duskwhorl Recluse",
        cost = "{2}{U}",
        type = "Creature — Rat Rogue",
        pt = "1/4",
        text = """
            Vigilance
            Threshold — As long as there are seven or more cards in your graveyard, this creature gets +1/+0 and can't be blocked.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(1), one(0)), Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self),
            condition = threshold, abilityWord = "Threshold"),
    )

    // CR 118.12a: "unless" with a condition, checked as it resolves
    @Test
    fun strandlinePilferer() = card(
        name = "Strandline Pilferer",
        cost = "{1}{U}",
        type = "Creature — Rat Rogue",
        pt = "1/1",
        text = """
            This creature can't be blocked.
            Threshold — Whenever this creature deals combat damage to a player, draw a card. Then discard a card unless there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            instructions = steps(
                Step.Do(Effect.Draw(Players.You, one())),
                Step.If(Condition.Not(threshold), then = listOf(Step.Do(Effect.Discard(Players.You, one(), filter = ObjectFilter.Any)))),
            ),
            abilityWord = "Threshold",
        ),
    )

    @Test
    fun thoughtdrillRaider() = card(
        name = "Thoughtdrill Raider",
        cost = "{2}{U/B}{U/B}",
        type = "Creature — Rat Warlock",
        pt = "2/5",
        text = """
            Threshold — As long as there are seven or more cards in your graveyard, this creature gets +3/+0.
            {2}{U/B}: Surveil 1.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(3), one(0))), affects = Affected.Objects(Objects.Self), condition = threshold,
            abilityWord = "Threshold"),
        Ability.Activated(Cost(listOf(mana("{2}{U/B}"))), steps(Step.Do(Effect.Surveil(Players.You, one()))), any),
    )

    // CR 602.5b: "only once" for the object's lifetime (D44)
    @Test
    fun ideaHusker() = card(
        name = "Idea Husker",
        cost = "{1}{U}",
        type = "Creature — Rat Rogue",
        pt = "1/3",
        text = "Threshold — {1}{U}: Put a +1/+1 counter on this creature and draw a card. Activate only if there are seven or more cards in your graveyard and only once.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{1}{U}"))),
            instructions = steps(counters(Objects.Self), Step.Do(Effect.Draw(Players.You, one()))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, once = true, condition = threshold),
            abilityWord = "Threshold",
        ),
    )

    // CR 400.7d: "this spell was cast from anywhere other than your hand"; the token created by it
    @Test
    fun otterpupShenanigans() = card(
        name = "Otterpup Shenanigans",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Create a 1/1 blue and red Otter creature token with prowess. If this spell was cast from anywhere other than your hand, put a +1/+1 counter on that creature.
            Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(steps(
            Step.Do(Effect.CreateToken(creatureToken("Otter", listOf(Color.Blue, Color.Red), 1, 1, listOf(Keyword.Prowess)), count = one(),
                controller = Players.You, bind = "token")),
            Step.If(Condition.And(listOf(Condition.CastFrom(null, Objects.Self), Condition.Not(Condition.CastFrom(Zone.Hand, Objects.Self)))),
                then = listOf(counters(Objects.Bound("token")))),
        )),
        Keyword.Flashback(manaCost("{3}{U}")),
    )

    // CR 603.12: the reflexive trigger's target depends on the counters as it is put on the Stack
    @Test
    fun wishwaterFountain() = card(
        name = "Wishwater Fountain",
        cost = "{3}{U}",
        type = "Artifact",
        text = "{T}: Put a coin counter on this artifact. When you do, you may cast target instant or sorcery card with mana value equal to the number of coin counters on this artifact from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = steps(
                counters(Objects.Self, kind = CounterKind.Named("coin")),
                Step.Do(Effect.ReflexiveTrigger(Instructions(
                    targets = listOf(slot("card", ObjectFilter.And(listOf(instantOrSorcery, ObjectFilter.ManaValue(Comparison.Equal,
                        Value.Of(NumericProperty.Counters(CounterKind.Named("coin")), Objects.Self, Combine.Single))) + yourGraveyard))),
                    steps = listOf(
                        Step.May(listOf(Step.Do(Effect.Cast(Objects.Target("card"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You))),
                            player = Players.You),
                        exileTheSpellInstead,
                    ),
                ))),
            ),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun boldWavecrester() = card(
        name = "Bold Wavecrester",
        cost = "{4}{U}{U}",
        type = "Creature — Otter Wizard",
        pt = "4/4",
        text = "When this creature enters, you may cast target instant or sorcery card with mana value 4 or less from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.May(listOf(Step.Do(Effect.Cast(Objects.Target("card"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You))),
                player = Players.You),
            exileTheSpellInstead,
            targets = listOf(slot("card", ObjectFilter.And(listOf(instantOrSorcery, ObjectFilter.ManaValue(Comparison.AtMost, one(4))) + yourGraveyard))),
        ),
    )

    // CR 601.2f: creature cards you own in exile and in your graveyard
    @Test
    fun husksplitterHorde() = card(
        name = "Husksplitter Horde",
        cost = "{7}{B}",
        type = "Creature — Elemental Insect",
        pt = "6/6",
        text = """
            This spell costs {1} less to cast for each creature card you own in exile and in your graveyard.
            Menace, deathtouch
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!,
            Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Exile), ObjectFilter.InZone(Zone.Graveyard)))))))))), affects = Affected.Objects(Objects.Self)),
        Keyword.Menace,
        Keyword.Deathtouch,
    )

    // CR 113.6m: works from the graveyard; CR 508.4: returned tapped and attacking
    @Test
    fun relentlessBogstalker() = card(
        name = "Relentless Bogstalker",
        cost = "{1}{B}",
        type = "Creature — Rat Berserker",
        pt = "3/1",
        text = """
            This creature gets +1/+0 for each other Rat you control.
            Threshold — Whenever you attack with one or more Rats, if there are seven or more cards in your graveyard, you may pay {2}{B}. If you do, return this card from your graveyard to the battlefield tapped and attacking.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
            sub("Rat"), you)))), one(0))), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.And(listOf(sub("Rat"), you))), oneOrMore = true),
            instructions = steps(
                Step.Pay(Cost(listOf(mana("{2}{B}"))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Move(Objects.Self,
                    Destination(Zone.Battlefield, tapped = true, attacking = AttackTarget.Chosen))))),
            ),
            interveningIf = threshold,
            functionsIn = listOf(Zone.Graveyard),
            abilityWord = "Threshold",
        ),
    )

    @Test
    fun mindswirl() = card(
        name = "Mindswirl",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Target opponent discards two cards. Then if you control a Rat, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                Step.Do(Effect.Discard(Players.Target("opponent"), one(2), filter = ObjectFilter.Any)),
                Step.If(Condition.Exists(ObjectFilter.And(listOf(sub("Rat"), you)), atLeast = 1), then = listOf(Step.Do(Effect.Surveil(Players.You, one(2))))),
            ),
        )),
    )

    // CR 702.34a: flashback casts it from a graveyard
    @Test
    fun cutthroatBargaining() = card(
        name = "Cutthroat Bargaining",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Target opponent exiles a card from their hand. If this spell was cast from a graveyard, draw a card.
            Flashback {4}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))),
                    min = one(), max = one()), bind = "chosen", chooser = Players.Target("opponent")),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile))),
                Step.If(Condition.CastFrom(Zone.Graveyard, Objects.Self), then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
            ),
        )),
        Keyword.Flashback(manaCost("{4}{B}")),
    )

    // CR 107.3: X announced; twice X looked at
    @Test
    fun skygaze() = card(
        name = "Skygaze",
        cost = "{X}{B}{B}",
        type = "Sorcery",
        text = "Look at twice X cards from the top of your library. Put X cards from among them into your hand and the rest into your graveyard. You lose X life.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Times(x, 2), fromTop = true), players = Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, min = x, max = x, from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Graveyard))),
                Step.Do(Effect.LoseLife(Players.You, x)),
            ),
            announced = listOf("X"),
        )),
    )

    // CR 603.12: "When you do" after milling; X read as the reflexive trigger resolves
    @Test
    fun fennicksSentries() = card(
        name = "Fennick's Sentries",
        cost = "{4}{B}{B}",
        type = "Creature — Rat Warlock",
        pt = "5/3",
        text = "When this creature enters, mill three cards. When you do, target creature an opponent controls gets -X/-X until end of turn, where X is the greatest mana value among cards in your graveyard.",
    ) parsesTo abilities(
        run {
            val greatest = Value.Of(NumericProperty.ManaValue, Objects.Each(yourGraveyardCards), Combine.Greatest)
            whenThisEnters(
                Step.Do(Effect.Mill(Players.You, one(3))),
                Step.Do(Effect.ReflexiveTrigger(Instructions(
                    targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, opponents)))),
                    steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Times(greatest, -1), Value.Times(greatest, -1))),
                        Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn))),
                ))),
            )
        },
    )

    // CR 601.2a, 601.2f: an added cost of life, during your turn; CR 614.1a: your cards and tokens are exiled instead
    @Test
    fun feastOfCinders() = card(
        name = "Feast of Cinders",
        cost = "{4}{R}",
        type = "Enchantment",
        text = """
            During your turn, you may cast instant and sorcery spells from your graveyard by paying 1 life in addition to their other costs.
            If a card or token would be put into your graveyard from anywhere, exile it instead.
            {1}{R}: Sacrifice this enchantment.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, additionalCost = Cost(listOf(CostPart.PayLife(one()))), holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(instantOrSorcery) + yourGraveyard))),
            condition = Condition.YourTurn,
        ),
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Category(ObjectCategory.Card),
                    ObjectFilter.Category(ObjectCategory.Token))), ObjectFilter.OwnedBy(PlayerFilter.You))), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Activated(Cost(listOf(mana("{1}{R}"))), steps(Step.Do(Effect.Sacrifice(Objects.Self))), any),
    )

    // CR 607.2a: cards exiled with it, counted by card type
    @Test
    fun sharpEyedCollector() = card(
        name = "Sharp-Eyed Collector",
        cost = "{G}{G}",
        type = "Creature — Raccoon Scout",
        pt = "3/3",
        text = """
            As long as there are four or more card types among cards exiled with this creature, it gets +4/+4 and has trample.
            {1}: Exile target card from a graveyard.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.ModifyPowerToughness(one(4), one(4)), Mechanic.GainAbilities(listOf(Keyword.Trample))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Compare(Value.CountDistinct(Objects.Remembered("exiled"), SharedQuality.CardType), Comparison.AtLeast, one(4)),
        ),
        Ability.Activated(
            cost = Cost(listOf(mana("{1}"))),
            instructions = Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(anyCard, ObjectFilter.InZone(Zone.Graveyard))))),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
                ),
            ),
            rules = any,
        ),
    )

    @Test
    fun ebbcallerElder() = card(
        name = "Ebbcaller Elder",
        cost = "{1}{U}{B}",
        type = "Creature — Rat Wizard",
        pt = "3/3",
        text = """
            Menace
            Threshold — When this creature enters, if there are seven or more cards in your graveyard, return up to one target nonland permanent to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = arcanum.parser.cube.thisEnters,
            instructions = Instructions(targets = listOf(slot("permanent", nonlandPermanent, min = 0)),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Hand))))),
            interveningIf = threshold,
            abilityWord = "Threshold",
        ),
    )

    @Test
    fun barkshapeGatherer() = card(
        name = "Barkshape Gatherer",
        cost = "{3}",
        type = "Artifact Creature — Shapeshifter",
        pt = "2/3",
        text = """
            Changeling (This card is every creature type.)
            Reach
            {2}: Put target card from your graveyard on the bottom of your library.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        Keyword.Reach,
        Ability.Activated(
            cost = Cost(listOf(mana("{2}"))),
            instructions = Instructions(targets = listOf(slot("card", ObjectFilter.And(listOf(anyCard) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Library, libraryPosition = -1))))),
            rules = any,
        ),
    )

    @Test
    fun bonetieSpeaker() = card(
        name = "Bonetie Speaker",
        cost = "{1}{B}",
        type = "Creature — Squirrel Warlock Bard",
        pt = "2/2",
        text = "{3}{B}, Exile this card from your graveyard: Return another target creature card from your graveyard to your hand.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{3}{B}"), CostPart.Exile(CostObjects.Self, Zone.Graveyard))),
            instructions = Instructions(targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))))),
            rules = any,
        ),
    )

    @Test
    fun knifetoothPair() = card(
        name = "Knifetooth Pair",
        cost = "{2}{B}",
        type = "Creature — Rat Squirrel",
        pt = "3/2",
        text = """
            Deathtouch
            When this creature enters, you may mill two cards. (You may put the top two cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        whenThisEnters(Step.May(listOf(Step.Do(Effect.Mill(Players.You, one(2)))), player = Players.You)),
    )

    @Test
    fun grimsight() = card(
        name = "Grimsight",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Surveil 2, then draw two cards. You lose 2 life. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        Ability.Spell(steps(Step.Do(Effect.Surveil(Players.You, one(2))), Step.Do(Effect.Draw(Players.You, one(2))), Step.Do(Effect.LoseLife(Players.You, one(2))))),
    )
}
