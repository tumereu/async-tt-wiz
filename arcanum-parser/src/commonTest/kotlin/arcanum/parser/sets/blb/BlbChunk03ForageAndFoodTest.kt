package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 03: forage and Food. To forage is to exile three cards from your graveyard or sacrifice a Food (CR
 * 701.61a): a keyword action used as a cost ("{T}, Forage:", "forage or pay {B}", "by foraging in addition to paying
 * their other costs"), paid on resolution ("you may forage. If you do / When you do", CR 118.12, 603.12), done
 * optionally ("you may forage"), and an event ("whenever you forage"). Food (CR 111.10b) makes the rest: Food tokens,
 * Food artifacts that sacrifice themselves, "whenever you sacrifice one or more Foods", creatures that become Food.
 */
class BlbChunk03ForageAndFoodTest {
    private val forage = CostPart.Action(KeywordAction.Forage)
    private val food = sub("Food")
    private val squirrel = sub("Squirrel")
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val foodToken = Step.Do(Effect.CreateToken(TokenSpec.Predefined("Food"), count = one(), controller = Players.You))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)

    /** "{2}, {T}, Sacrifice this artifact: You gain 3 life." (a Food's ability, CR 111.10b) */
    private fun foodAbility(sacrificed: CostObjects = CostObjects.Self) = Ability.Activated(
        cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{2}")!!), CostPart.TapSelf, CostPart.Sacrifice(sacrificed))),
        instructions = steps(Step.Do(Effect.GainLife(Players.You, one(3)))),
        rules = any,
    )

    /** "you may forage. If you do, [then]" (CR 118.12) */
    private fun mayForage(vararg then: Step) = listOf(
        Step.Pay(Cost(listOf(forage)), payer = Players.You, bind = "paid"),
        Step.If(Condition.Did("paid"), then = then.toList()),
    )

    // ---- Forage

    // CR 601.2f: one of two additional costs
    @Test
    fun nourishTheLoop() = card(
        name = "Nourish the Loop",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, forage or pay {B}. (To forage, exile three cards from your graveyard or sacrifice a Food.)
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(
                Cost(listOf(forage)),
                Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{B}")!!))),
            )))))),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker))))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
        )),
    )

    // CR 601.2a, 601.2f: a permission with an added cost; the creature cast this way enters with a finality counter
    // (CR 122.1h), carried by the spell to the permanent it becomes (CR 400.7d)
    @Test
    fun bonegrafterApprentice() = card(
        name = "Bonegrafter Apprentice",
        cost = "{1}{B}",
        type = "Creature — Squirrel Warlock",
        pt = "2/2",
        text = """
            Deathtouch
            {T}: Until end of turn, you may cast creature spells from your graveyard by foraging in addition to paying their other costs. If you cast a spell this way, that creature enters with a finality counter on it. (To forage, exile three cards from your graveyard or sacrifice a Food. If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = steps(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.MayPlay(
                    Zone.Graveyard,
                    PlayKind.Cast,
                    additionalCost = Cost(listOf(forage)),
                    spellGets = listOf(Mechanic.Replacement(
                        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                        modify = listOf(Modification.EntersWithCounters(CounterKind.Finality, one())),
                    )),
                    holder = PlayerFilter.You,
                )),
                Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature) + yourGraveyard))),
                Duration.EndOfTurn,
            ))),
            rules = any,
        ),
    )

    @Test
    fun fluffytailGuard() = card(
        name = "Fluffytail Guard",
        cost = "{1}{G}",
        type = "Creature — Squirrel Warrior",
        pt = "2/1",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            When this creature enters, you may forage. If you do, put two +1/+1 counters on it. (To forage, exile three cards from your graveyard or sacrifice a Food.)
        """,
    ) parsesTo abilities(
        Keyword.Offspring(manaCost("{2}")),
        whenThisEnters(*mayForage(counters(Objects.EventObject(EventRole.After), 2)).toTypedArray()),
    )

    // CR 603.12: "When you do" is a reflexive trigger, its target chosen then
    @Test
    fun nosyGatherer() = card(
        name = "Nosy Gatherer",
        cost = "{2}{G}",
        type = "Creature — Squirrel Druid",
        pt = "3/2",
        text = """
            When this creature enters, you may forage. When you do, return target permanent card from your graveyard to your hand. (To forage, exile three cards from your graveyard or sacrifice a Food.)
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Pay(Cost(listOf(forage)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature,
                    CardType.Enchantment, CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) })) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand)))),
            ))))),
        ),
    )

    @Test
    fun canopyLookouts() = card(
        name = "Canopy Lookouts",
        cost = "{3}{G}",
        type = "Creature — Squirrel Archer",
        pt = "2/4",
        text = """
            Reach
            When this creature enters, you may forage. If you do, draw a card. (To forage, exile three cards from your graveyard or sacrifice a Food.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        whenThisEnters(*mayForage(Step.Do(Effect.Draw(Players.You, one()))).toTypedArray()),
    )

    // CR 605.1a: still a mana ability, its cost exiling or sacrificing
    @Test
    fun brambleRootGatherer() = card(
        name = "Brambleroot Gatherer",
        cost = "{1}{G}",
        type = "Creature — Squirrel Ranger",
        pt = "2/2",
        text = """
            {T}: Add {G}.
            {T}, Forage: Add two mana in any combination of colors. (To forage, exile three cards from your graveyard or sacrifice a Food.)
            {3}{G}, {T}: Search your library for a Squirrel card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{G}")!!)), Players.You))),
            rules = any,
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf, forage)),
            instructions = steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(one(2), combination = true)), Players.You))),
            rules = any,
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{3}{G}")!!), CostPart.TapSelf)),
            instructions = steps(
                Step.Do(Effect.Search(Players.You, squirrel, max = one(), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.You)),
            ),
            rules = any,
        ),
    )

    // CR 113.10: granted menace; "whenever you sacrifice one or more Foods" triggers once per batch
    @Test
    fun petuniaTheAcornhoarder() = card(
        name = "Petunia, the Acornhoarder",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Squirrel Warlock",
        pt = "3/3",
        text = """
            Menace
            Other Squirrels you control have menace.
            Whenever you sacrifice one or more Foods, create a 1/1 green Squirrel creature token.
            {2}, Forage: Put a +1/+1 counter on each other Squirrel you control. (To forage, exile three cards from your graveyard or sacrifice a Food.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Keyword.Menace))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), squirrel, you)))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(ObjectFilter.And(listOf(food, you))), oneOrMore = true),
            instructions = steps(Step.Do(Effect.CreateToken(creatureToken("Squirrel", listOf(Color.Green), 1, 1), count = one(), controller = Players.You))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{2}")!!), forage)),
            instructions = steps(counters(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), squirrel, you))))),
            rules = any,
        ),
    )

    // CR 701.61a: "whenever you forage" triggers whether the forage was a cost or an effect
    @Test
    fun gravefruitTender() = card(
        name = "Gravefruit Tender",
        cost = "{1}{B/G}{B/G}",
        type = "Creature — Squirrel Warlock",
        pt = "2/3",
        text = """
            At the beginning of combat on your turn, you may forage. (Exile three cards from your graveyard or sacrifice a Food.)
            Whenever you forage, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = yourCombat,
            instructions = steps(Step.May(listOf(Step.Do(Effect.Action(KeywordAction.Forage))), player = Players.You)),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Forage, PlayerFilter.You)),
            instructions = steps(counters(Objects.Self)),
        ),
    )

    // ---- Food

    // CR 702.21a: ward with a cost that isn't mana; CR 205.1b: Food artifacts "in addition to their other types"
    @Test
    fun gorvaxDevourerOfEverything() = card(
        name = "Gorvax, Devourer of Everything",
        cost = "{3}{B}{G}",
        type = "Legendary Creature — Elemental Cat",
        pt = "6/6",
        text = """
            Ward—Sacrifice a Food.
            Other creatures are Food artifacts in addition to their other types and have "{2}, {T}, Sacrifice this permanent: You gain 3 life."
            Whenever a Food is put into a graveyard from the battlefield, put two +1/+1 counters on Gorvax.
        """,
    ) parsesTo abilities(
        Keyword.Ward(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(food, count = one()))))),
        Ability.Static(
            listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Food")))),
                Mechanic.GainAbilities(listOf(foodAbility())),
            ),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(food, from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = steps(counters(Objects.Self, 2)),
        ),
    )

    // CR 400.7: cards leaving a graveyard are counted, each one; a sacrificed Food is an event this turn
    @Test
    fun marrowhoardWarden() = card(
        name = "Marrowhoard Warden",
        cost = "{B}",
        type = "Creature — Squirrel Warlock",
        pt = "1/1",
        text = "{T}, Pay 1 life: Draw a card. Activate only if three or more cards left your graveyard this turn or if you've sacrificed a Food this turn.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf, CostPart.PayLife(one()))),
            instructions = steps(Step.Do(Effect.Draw(Players.You, one()))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.Or(listOf(
                Condition.HappenedThisTurn(
                    EventPattern.ZoneChange(ObjectFilter.And(listOf(anyCard, ObjectFilter.OwnedBy(PlayerFilter.You))), from = Zone.Graveyard),
                    atLeast = 3,
                    measure = Measure.Objects,
                ),
                Condition.HappenedThisTurn(EventPattern.Sacrifice(ObjectFilter.And(listOf(food, you))), atLeast = 1, measure = Measure.Events),
            ))),
        ),
    )

    // CR 613.1d, e, f: types, colour and abilities replaced; "other" abilities, so the granted one stays
    @Test
    fun candiedShell() = card(
        name = "Candied Shell",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature or Food
            Enchanted permanent is a colorless Food artifact with "{2}, {T}, Sacrifice this artifact: You gain 3 life" and loses all other card types and abilities.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(ObjectFilter.Or(listOf(creature, food)))),
        Ability.Static(
            listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Food"))), replaceTypes = true, replaceSubtypes = true),
                Mechanic.SetColors(emptyList()),
                Mechanic.LoseAbilities(),
                Mechanic.GainAbilities(listOf(foodAbility())),
            ),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self)),
        ),
    )

    // CR 122.6: counters put on as the enters trigger resolves, counting the others then
    @Test
    fun reveredNestwarden() = card(
        name = "Revered Nestwarden",
        cost = "{2}{G}",
        type = "Creature — Squirrel Warrior",
        pt = "1/1",
        text = """
            Trample
            When this creature enters, put a +1/+1 counter on it for each other Squirrel and/or Food you control.
            Whenever another Squirrel or Food you control enters, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        run {
            val others = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Or(listOf(squirrel, food)), you))
            whenThisEnters(Step.Do(Effect.PutCounters(Objects.EventObject(EventRole.After), p1p1, Value.Count(Objects.Each(others)))))
        },
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Or(listOf(squirrel, food)), you)),
                to = Zone.Battlefield,
            )),
            instructions = steps(counters(Objects.Self)),
        ),
    )

    // CR 603.2: one ability with two trigger events
    @Test
    fun turnipTart() = card(
        name = "Turnip Tart",
        cost = "{1}{W}",
        type = "Artifact — Food",
        text = """
            When this artifact enters and when you sacrifice it, create a 1/1 white Rabbit creature token and scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.Sacrifice(ObjectFilter.Is(Objects.Self)),
            )),
            instructions = steps(
                Step.Do(Effect.CreateToken(rabbitToken, count = one(), controller = Players.You)),
                Step.Do(Effect.Scry(Players.You, one())),
            ),
        ),
        foodAbility(),
    )

    @Test
    fun bountifulBasket() = card(
        name = "Bountiful Basket",
        cost = "{2}{G}",
        type = "Artifact — Food",
        text = """
            When this artifact enters and when you sacrifice it, you may search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.Sacrifice(ObjectFilter.Is(Objects.Self)),
            )),
            instructions = steps(Step.May(listOf(
                Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(arcanum.api.Supertype.Basic), land)), max = one(),
                    zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
                Step.Do(Effect.Shuffle(Players.You)),
            ), player = Players.You)),
        ),
        foodAbility(),
    )

    // CR 701.17a: milling; "this way" is the milled cards; a Squirrel card returned this way is the chosen one
    @Test
    fun stashSnatch() = card(
        name = "Stash Snatch",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Mill four cards. You may put a permanent card from among the cards milled this way into your hand. If you control a Squirrel or returned a Squirrel card to your hand this way, create a Food token. (To mill four cards, put the top four cards of your library into your graveyard. A Food token is an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Ability.Spell(steps(
            Step.Do(Effect.Mill(Players.You, one(4), postBind = "milled")),
            Step.Choose(
                Choice.Objects(
                    ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
                        CardType.Planeswalker).map { ObjectFilter.Type(it) }),
                    min = one(0),
                    max = one(),
                    from = Objects.Bound("milled"),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
            Step.If(
                Condition.Or(listOf(
                    Condition.Exists(ObjectFilter.And(listOf(squirrel, you)), atLeast = 1),
                    Condition.ObjectMatches(Objects.Bound("chosen"), squirrel),
                )),
                then = listOf(foodToken),
            ),
        )),
    )

    // CR 111.10b: Food tokens
    @Test
    fun relish() = card(
        name = "Relish",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Target creature gets -2/-2 until end of turn. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creature)),
            steps = listOf(pump(Objects.Target("creature"), -2, -2), foodToken),
        )),
    )

    @Test
    fun vinepickerElder() = card(
        name = "Vinepicker Elder",
        cost = "{B}{G}",
        type = "Creature — Squirrel Druid",
        pt = "3/2",
        text = """
            When this creature enters or dies, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            )),
            instructions = steps(foodToken),
        ),
    )

    @Test
    fun honeyblossomsTeapot() = card(
        name = "Honeyblossom's Teapot",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {5}, {T}, Sacrifice this artifact: Destroy target nonland permanent. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        whenThisEnters(foodToken),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{5}")!!), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(slot("permanent", nonlandPermanent)),
                steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
            ),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun cloverleafPhalanx() = card(
        name = "Cloverleaf Phalanx",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with flying.
            • Destroy target enchantment.
            • Draw a card. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(
                    targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.HasKeyword(Keyword.Flying))))),
                    steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature")))),
                )),
                Mode(Instructions(
                    targets = listOf(slot("enchantment", ObjectFilter.Type(CardType.Enchantment))),
                    steps = listOf(Step.Do(Effect.Destroy(Objects.Target("enchantment")))),
                )),
                Mode(steps(Step.Do(Effect.Draw(Players.You, one())), foodToken)),
            ),
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )
}
