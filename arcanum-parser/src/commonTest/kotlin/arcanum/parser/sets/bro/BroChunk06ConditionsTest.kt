package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 06: Ward and conditions: "unless … colors of mana were spent", "during turns other than yours", "if a creature enters this
 * way", "if it has unearth", "for as long as that card remains exiled", "if you control creatures named … and …". */
class BroChunk06ConditionsTest {
    private fun addMana(symbols: String): Step = doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(symbols)!!)), Players.You))
    private fun named(name: String): Condition = Condition.Exists(and(creature, ObjectFilter.Named(name), you), atLeast = 1)
    private fun controlBoth(first: String, second: String): Condition = Condition.And(listOf(named(first), named(second)))

    // CR 702.21a, 106.4: a dies trigger adds mana that stays until end of turn
    @Test
    fun suChiCaveGuard() = card(
        name = "Cavern Golem Sentry",
        cost = "{8}",
        type = "Artifact Creature — Construct",
        pt = "8/8",
        text = """
            Vigilance
            Ward {4} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {4}.)
            When this creature dies, add eight {C}. Until end of turn, you don't lose this mana as steps and phases end.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Ward(manaCost("{4}")),
        triggered(diesSelf, doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}{C}{C}{C}{C}{C}{C}")!!), keptUntil = Duration.EndOfTurn),
            Players.You))),
    )

    // CR 614.1c, 601.2h: it enters with the counters unless two or more colors of mana were spent to cast it
    @Test
    fun steelExemplar() = card(
        name = "Ironclad Paragon",
        cost = "{5}",
        type = "Artifact Creature — Wizard",
        pt = "4/4",
        text = """
            Trample
            This creature enters with two +1/+1 counters on it unless two or more colors of mana were spent to cast it.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(2))))), affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.Compare(Value.ManaSpent(distinctColors = true, of = Objects.Self), Comparison.AtLeast, n(2)))),
    )

    // CR 614.1a: damage from a red or artifact source you control, to an opponent or their permanent, plus 1
    @Test
    fun mechanizedWarfare() = card(
        name = "Gearwork Onslaught",
        cost = "{1}{R}{R}",
        type = "Enchantment",
        text = "If a red or artifact source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus 1 instead.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.Damage(and(or(ObjectFilter.OfColor(ColorRef.Literal(Color.Red)), artifact), you),
                RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent)),
            modify = listOf(Modification.AddAmount(n(1))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 508.1m, 603.5: "you may sacrifice another creature. If you do, …"; CR 111.10h
    @Test
    fun ashnodFleshMechanist() = card(
        name = "Vessa, Flesh Tinker",
        cost = "{B}",
        type = "Legendary Creature — Human Artificer",
        pt = "1/1",
        text = """
            Deathtouch
            Whenever Vessa attacks, you may sacrifice another creature. If you do, create a tapped Powerstone token.
            {5}, Exile a creature card from your graveyard: Create a tapped 3/3 colorless Zombie artifact creature token.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(attacksSelf, Step.Pay(cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), createPowerstone())),
        activated(cost(mana("{5}"), CostPart.Exile(CostObjects.Choose(creature, n(1)), from = Zone.Graveyard)),
            doing(Effect.CreateToken(creatureToken("Zombie", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature)), n(1), Players.You, tapped = true))),
    )

    // CR 601.2f: it costs {3} more while it isn't your turn; CR 723.1
    @Test
    fun hurkylsFinalMeditation() = card(
        name = "Sage's Last Reverie",
        cost = "{4}{U}{U}{U}",
        type = "Instant",
        text = """
            During turns other than yours, this spell costs {3} more to cast.
            Return all nonland permanents to their owners' hands. End the turn. (Exile all spells and abilities from the stack, including this card. The player whose turn it is discards down to their maximum hand size. Damage wears off, and "this turn" and "until end of turn" effects end.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(isSelf, CostChange.More(ManaCost.parse("{3}")!!, n(1)))), affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.YourTurn)),
        spell(move(Objects.Each(and(nonland, permanentFilter)), Zone.Hand), doing(Effect.EndTurn)),
    )

    // CR 101.2, 602.5: during your turn, opponents can't cast spells or activate abilities of those permanents (mana abilities too)
    @Test
    fun myrelShieldOfArgive() = card(
        name = "Corvin, Shield of Thran",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/4",
        text = """
            During your turn, your opponents can't cast spells or activate abilities of artifacts, creatures, or enchantments.
            Whenever Corvin attacks, create X 1/1 colorless Soldier artifact creature tokens, where X is the number of Soldiers you control.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantCast(ObjectFilter.Any), Mechanic.CantActivate(ObjectFilter.AbilityOf(source = or(artifact, creature, enchantment)))),
            affects = Affected.Players(Players.Each(PlayerFilter.Opponent)), condition = Condition.YourTurn),
        triggered(attacksSelf, doing(Effect.CreateToken(creatureToken("Soldier", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature)),
            count(and(sub("Soldier"), you)), Players.You))),
    )

    // CR 614.1c, 614.12: the counter only if the permanent is a creature as it enters
    @Test
    fun recommission() = card(
        name = "Reactivation Order",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Return target artifact or creature card with mana value 3 or less from your graveyard to the battlefield. If a creature enters this way, it enters with an additional +1/+1 counter on it.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(ObjectFilter.Is(target("card")), creature), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))), null, Duration.EndOfTurn, uses = 1)),
            move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(or(artifact, creature), manaValue(Comparison.AtMost, 3)))),
        ),
    )

    // CR 702.84a, 400.7: an unearthed permanent would be exiled instead of leaving, so it goes to exile, then that new card to hand
    @Test
    fun meticulousExcavation() = card(
        name = "Careful Unearthing",
        cost = "{W}",
        type = "Enchantment",
        text = "{2}{W}: Return target permanent you control to its owner's hand. If it has unearth, instead exile it, then return that card to its owner's hand. Activate only during your turn.",
    ) parsesTo abilities(
        activated(cost(mana("{2}{W}")),
            ifThen(Condition.ObjectMatches(target("permanent"), ObjectFilter.HasKeyword(Keyword.Unearth.Kind)),
                move(target("permanent"), Zone.Exile, postBind = "exiled"), move(Objects.Bound("exiled"), Zone.Hand),
                otherwise = listOf(move(target("permanent"), Zone.Hand))),
            targets = listOf(slot("permanent", and(permanentFilter, you))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.YourTurn)),
    )

    // CR 613.1d, 613.4b: a noncreature permanent becomes a 0/0 creature, keeping its other types (indefinitely)
    @Test
    fun tawnossTinkering() = card(
        name = "Tinker's Renewal",
        cost = "{3}{G}",
        type = "Instant",
        text = "Put two +1/+1 counters on target artifact, creature, or land you control. Untap that permanent. If it isn't a creature, it becomes a 0/0 creature in addition to its other types.",
    ) parsesTo abilities(
        spell(
            counters(target("permanent"), 2),
            untapIt(target("permanent")),
            ifThen(Condition.ObjectMatches(target("permanent"), not(creature)), doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = emptyList()), Mechanic.SetPowerToughness(n(0), n(0))),
                Affected.Objects(target("permanent")), Duration.Indefinitely))),
            targets = listOf(slot("permanent", and(or(artifact, creature, land), you))),
        ),
    )

    // CR 400.7, 601.2f: its owner may play it from exile; a spell an opponent casts this way costs {2} more
    @Test
    fun soulPartition() {
        val card = Objects.Bound("exiled")
        fun mayPlay(costChange: CostChange?) = doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play,
            holder = PlayerFilter.Is(Players.OwnerOf(card)), costChange = costChange)), Affected.Objects(card), Duration.Indefinitely))
        card(
            name = "Spirit Severance",
            cost = "{1}{W}",
            type = "Instant",
            text = "Exile target nonland permanent. For as long as that card remains exiled, its owner may play it. A spell cast by an opponent this way costs {2} more to cast.",
        ) parsesTo abilities(
            spell(
                move(target("permanent"), Zone.Exile, postBind = "exiled"),
                ifThen(Condition.PlayerMatches(Players.OwnerOf(card), PlayerFilter.Opponent), mayPlay(CostChange.More(ManaCost.parse("{2}")!!, n(1))),
                    otherwise = listOf(mayPlay(null))),
                targets = listOf(slot("permanent", and(nonland, permanentFilter))),
            ),
        )
    }

    // CR 201.2, 614.1a: one creature of each name; the effect is replaced by the bigger one
    @Test
    fun mineWorker() = card(
        name = "Quarry Worker",
        cost = "{2}",
        type = "Artifact Creature — Assembly-Worker",
        pt = "2/1",
        text = "{T}: You gain 1 life. If you control creatures named Furnace Worker and Spire Worker, you gain 3 life instead.",
    ) parsesTo abilities(
        activated(cost(tap), ifThen(controlBoth("Furnace Worker", "Spire Worker"), gainLife(3), otherwise = listOf(gainLife(1)))),
    )

    // CR 201.2, 602.5b: "Activate only once each turn"
    @Test
    fun powerPlantWorker() = card(
        name = "Furnace Worker",
        cost = "{5}",
        type = "Artifact Creature — Assembly-Worker",
        pt = "4/4",
        text = "{3}: This creature gets +2/+2 until end of turn. If you control creatures named Quarry Worker and Spire Worker, put two +1/+1 counters on this creature instead. Activate only once each turn.",
    ) parsesTo abilities(
        activated(cost(mana("{3}")), ifThen(controlBoth("Quarry Worker", "Spire Worker"), counters(Objects.Self, 2), otherwise = listOf(pump(Objects.Self, 2, 2))),
            rules = ActivationRules(perTurn = 1, timing = Timing.Any, activatedBy = PlayerFilter.You)),
    )

    // CR 605.1a, 201.2: a mana ability whose amount depends on the creatures you control
    @Test
    fun towerWorker() = card(
        name = "Spire Worker",
        cost = "{3}",
        type = "Artifact Creature — Assembly-Worker",
        pt = "1/3",
        text = """
            Reach
            {T}: Add {C}. If you control creatures named Quarry Worker and Furnace Worker, add {C}{C}{C} instead.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(tap), ifThen(controlBoth("Quarry Worker", "Furnace Worker"), addMana("{C}{C}{C}"), otherwise = listOf(addMana("{C}")))),
    )

    // CR 608.2d: the keyword is chosen as the ability resolves, after the -1/-1
    @Test
    fun argivianAvenger() = card(
        name = "Thran Mimic Avenger",
        cost = "{6}",
        type = "Artifact Creature — Shapeshifter",
        pt = "5/5",
        text = "{1}: Until end of turn, this creature gets -1/-1 and gains your choice of flying, vigilance, deathtouch, or haste.",
    ) parsesTo abilities(
        activated(cost(mana("{1}")),
            pump(Objects.Self, -1, -1),
            Step.Choose(Choice.Option(listOf("flying", "vigilance", "deathtouch", "haste")), bind = "keyword", chooser = Players.You),
            ifThen(Condition.Chose("keyword", "flying"), gains(Objects.Self, Keyword.Flying), otherwise = listOf(
                ifThen(Condition.Chose("keyword", "vigilance"), gains(Objects.Self, Keyword.Vigilance), otherwise = listOf(
                    ifThen(Condition.Chose("keyword", "deathtouch"), gains(Objects.Self, Keyword.Deathtouch), otherwise = listOf(
                        gains(Objects.Self, Keyword.Haste)))))))),
    )

    // CR 603.6c, 603.10a: a granted leaves-the-battlefield trigger returns the new object to its owner's hand
    @Test
    fun ashnodsIntervention() = card(
        name = "Vessa's Reprieve",
        cost = "{B}",
        type = "Instant",
        text = "Until end of turn, target creature gets +2/+0 and gains \"When this creature dies or is put into exile from the battlefield, return it to its owner's hand.\"",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(2, 0), has(Ability.Triggered(
                Trigger.On(listOf(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, to = Zone.Graveyard),
                    EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, to = Zone.Exile))),
                Instructions(steps = listOf(move(Objects.EventObject(EventRole.After), Zone.Hand)))))),
            targets = listOf(slot("creature", creature)),
        ),
    )
}
