package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.DamageOccurrence
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 05: ward (CR 702.21) and the conditions of this set's cards: an ability's activations this turn, a creature's
 * subtype, a card discarded or revealed, an opponent's spells this turn, excess damage, X as announced, and what follows a conditional
 * move.
 */
class DmuChunk05WardAndConditionsTest {
    private val yourEndStep = stepBegins(TurnStep.End, PlayerFilter.You)
    private val topCard = Objects.LibraryCards(Players.You, n(1), fromTop = true)
    private val chosenName = ObjectFilter.RememberedName("name")
    private fun dies(filter: ObjectFilter) = on(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))
    private fun becomes(vararg subtypes: String, p: Int, t: Int) = doing(Effect.ApplyMechanics(listOf(
        Mechanic.SetTypes(addSubtypes = subtypes.map { SubtypeRef.Literal(Subtype(it)) }, replaceSubtypes = true),
        Mechanic.SetPowerToughness(n(p), n(t))), Affected.Objects(Objects.Self), Duration.Indefinitely))

    // CR 707.9b: the copy's exceptions (not legendary; 2/2, black and a Zombie in addition); CR 603.10a: "that creature", copied as it last existed
    @Test
    fun ratadrabikOfUrborg() = card(
        name = "Vexadrabik of Morrow",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Zombie Wizard",
        pt = "3/3",
        text = """
            Vigilance, ward {2}
            Other Zombies you control have vigilance.
            Whenever another legendary creature you control dies, create a token that's a copy of that creature, except it's not legendary and it's a 2/2 black Zombie in addition to its other colors and types.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Ward(manaCost("{2}")),
        static(has(Keyword.Vigilance), affects = Objects.Each(and(another, sub("Zombie"), you))),
        triggered(dies(and(another, legendary, creature, you)), doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.EventObject(arcanum.api.EventRole.After), except = listOf(
            CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)),
            CopyException.PowerToughness(2, 2),
            CopyException.Colors(listOf(ColorRef.Literal(Color.Black)), add = true),
            CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Zombie")))),
        )), n(1), Players.You))),
    )

    // CR 120.4a: excess damage, dealt to a creature or planeswalker an opponent controlled then; CR 603.4: intervening "if"
    @Test
    fun rithLiberatedPrimeval() = card(
        name = "Ryth, Unbound Primordial",
        cost = "{2}{R}{G}{W}",
        type = "Legendary Creature — Dragon",
        pt = "5/5",
        text = """
            Flying, ward {2}
            Other Dragons you control have ward {2}.
            At the beginning of your end step, if a creature or planeswalker an opponent controlled was dealt excess damage this turn, create a 4/4 red Dragon creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(manaCost("{2}")),
        static(has(Keyword.Ward(manaCost("{2}"))), affects = Objects.Each(and(another, sub("Dragon"), you))),
        triggered(yourEndStep, create(creatureToken("Dragon", listOf(Color.Red), 4, 4, listOf(Keyword.Flying))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = and(or(creature, planeswalker), opponents)),
                excess = true, per = DamageOccurrence.Recipient), atLeast = 1, measure = Measure.Events)),
    )

    // CR 601.2f: a reduction counted as it is cast; CR 702.21a: ward
    @Test
    fun tolarianTerror() = card(
        name = "Lighthouse Leviathan",
        cost = "{6}{U}",
        type = "Creature — Serpent",
        pt = "5/5",
        text = """
            This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(or(instant, sorcery)))))),
        Keyword.Ward(manaCost("{2}")),
    )

    // CR 602.1: the activations of this ability this turn, this one included, counted as it resolves; CR 603.7: a delayed trigger
    @Test
    fun dragonWhelp() = card(
        name = "Cinder Whelpling",
        cost = "{2}{R}{R}",
        type = "Creature — Dragon",
        pt = "2/3",
        text = """
            Flying
            {R}: This creature gets +1/+0 until end of turn. If this ability has been activated four or more times this turn, sacrifice this creature at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{R}"), pump(Objects.Self, 1, 0), Step.If(
            Condition.Compare(Value.AbilityUsesThisTurn(resolved = false), Comparison.AtLeast, n(4)),
            then = listOf(doing(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                arcanum.api.Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Self))))))),
        )),
    )

    // CR 205.1a: the new creature types replace the old ones; layer 7b; CR 122.1b: a deathtouch counter; checked as each ability resolves
    @Test
    fun evolvedSleeper() = card(
        name = "Awakened Dreamer",
        cost = "{B}",
        type = "Creature — Human",
        pt = "1/1",
        text = """
            {B}: This creature becomes a Human Cleric with base power and toughness 2/2.
            {1}{B}: If this creature is a Cleric, put a deathtouch counter on it and it becomes a Phyrexian Human Cleric with base power and toughness 3/3.
            {1}{B}{B}: If this creature is a Phyrexian, put a +1/+1 counter on it, then you draw a card and you lose 1 life.
        """,
    ) parsesTo abilities(
        activated(manaCost("{B}"), becomes("Human", "Cleric", p = 2, t = 2)),
        activated(manaCost("{1}{B}"), Step.If(Condition.ObjectMatches(Objects.Self, sub("Cleric")), then = listOf(
            counters(Objects.Self, kind = CounterKind.Keyword(Keyword.Deathtouch.kind)),
            becomes("Phyrexian", "Human", "Cleric", p = 3, t = 3),
        ))),
        activated(manaCost("{1}{B}{B}"), Step.If(Condition.ObjectMatches(Objects.Self, sub("Phyrexian")), then = listOf(
            counters(Objects.Self),
            draw(1),
            loseLife(1),
        ))),
    )

    // CR 701.9a: the card discarded, if any; CR 601.2a: cast from the graveyard; CR 614.1a: exiled instead
    @Test
    fun voharVodalianDesecrator() = card(
        name = "Vahor, Tidepool Defiler",
        cost = "{U}{B}",
        type = "Legendary Creature — Phyrexian Merfolk Wizard",
        pt = "1/2",
        text = """
            {T}: Draw a card, then discard a card. If you discarded an instant or sorcery card this way, each opponent loses 1 life and you gain 1 life.
            {2}, Sacrifice Vahor: You may cast target instant or sorcery card from your graveyard this turn. If that spell would be put into your graveyard, exile it instead. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(tap), draw(1), doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, postBind = "discarded")), Step.If(
            Condition.Compare(Value.Count(Objects.Where(Objects.Bound("discarded"), or(instant, sorcery))), Comparison.AtLeast, n(1)),
            then = listOf(loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
        )),
        activated(cost(mana("{2}"), sacrificeSelf),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You, then = Destination(Zone.Exile), thenInsteadOf = Zone.Graveyard)), Affected.Objects(target("card")), Duration.EndOfTurn)),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery)))), rules = asSorcery),
    )

    // CR 701.21a: sacrifice; CR 101.4: each opponent in turn order chooses, then all sacrifice at once; CR 608.2h: "it" as it last existed
    @Test
    fun braidsArisenNightmare() = card(
        name = "Plaits, Waking Nightmare",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Nightmare",
        pt = "3/3",
        text = "At the beginning of your end step, you may sacrifice an artifact, creature, enchantment, land, or planeswalker. If you do, each opponent may sacrifice a permanent of their choice that shares a card type with it. For each opponent who doesn't, that player loses 2 life and you draw a card.",
    ) parsesTo abilities(
        triggered(yourEndStep,
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(or(artifact, creature, enchantment, land, planeswalker), count = n(1)), preBind = "sacrificed"))),
                payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                    Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(permanentFilter, ObjectFilter.Shares(SharedQuality.CardType, Objects.Bound("sacrificed"))),
                        count = n(1))))), payer = Players.Bound("opponent"), bind = "paid"),
                    Step.If(Condition.Not(Condition.Did("paid")), then = listOf(loseLife(2, Players.Bound("opponent")), draw(1))),
                ), together = true),
            )),
        ),
    )

    // CR 702.150a: compleated; CR 401.4: the revealed card; CR 601.2d: three counters distributed among up to three targets, each at least one;
    // CR 114.1: an emblem
    @Test
    fun ajaniSleeperAgent() = card(
        name = "Ajora, Dormant Agent",
        cost = "{1}{G}{G/W/P}{W}",
        type = "Legendary Planeswalker — Ajani",
        loyalty = "4",
        text = """
            Compleated ({G/W/P} can be paid with {G}, {W}, or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
            [+1]: Reveal the top card of your library. If it's a creature or planeswalker card, put it into your hand. Otherwise, you may put it on the bottom of your library.
            [−3]: Distribute three +1/+1 counters among up to three target creatures. They gain vigilance until end of turn.
            [−6]: You get an emblem with "Whenever you cast a creature or planeswalker spell, target opponent gets two poison counters."
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(1,
            doing(Effect.Reveal(topCard, bind = "revealed")),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), or(creature, planeswalker)),
                then = listOf(move(Objects.Bound("revealed"), Zone.Hand)),
                otherwise = listOf(Step.May(listOf(move(Objects.Bound("revealed"), Zone.Library, position = -1)), Players.You))),
        ),
        loyalty(-3,
            doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
            gains(target("creatures"), Keyword.Vigilance),
            targets = listOf(slot("creatures", creature, min = 0, max = 3).copy(divided = n(3))),
        ),
        loyalty(-6, doing(Effect.CreateEmblem(listOf(triggered(youCast(or(creature, planeswalker)),
            doing(Effect.GivePlayerCounters(Players.Target("opponent"), CounterKind.Poison, n(2))), targets = listOf(targetOpponent))), Players.You))),
    )

    // CR 401.4: the card looked at; CR 110.5: put onto the battlefield tapped; the token unless a card was put onto the battlefield (declined, or
    // not a land)
    @Test
    fun rulikMonsWarrenChief() = card(
        name = "Ruvik Mons, Burrow Boss",
        cost = "{1}{R}{G}{G}",
        type = "Legendary Creature — Goblin",
        pt = "3/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever Ruvik Mons attacks, look at the top card of your library. If it's a land card, you may put it onto the battlefield tapped. If you didn't put a card onto the battlefield this way, create a 1/1 red Goblin creature token.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf,
            doing(Effect.LookAt(topCard, Players.You, bind = "seen")),
            Step.If(Condition.ObjectMatches(Objects.Bound("seen"), land), then = listOf(
                Step.May(listOf(move(Objects.Bound("seen"), Zone.Battlefield, tapped = true, postBind = "put")), Players.You))),
            Step.If(Condition.Not(Condition.NonEmpty("put")), then = listOf(create(creatureToken("Goblin", listOf(Color.Red), 1, 1)))),
        ),
    )

    // CR 613.4c: layer 7c; CR 406.3: exiled; CR 601.2a: cast from exile until the end of your next turn, only a Goblin creature card
    @Test
    fun rundveltHordemaster() = card(
        name = "Rundmark Mobmaster",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        pt = "1/1",
        text = """
            Other Goblins you control get +1/+1.
            Whenever this creature or another Goblin you control dies, exile the top card of your library. If it's a Goblin creature card, you may cast that card until the end of your next turn.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Goblin"), you))),
        triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, sub("Goblin"), you))),
            doing(Effect.Move(topCard, Destination(Zone.Exile), postBind = "exiled")),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), and(sub("Goblin"), creature)), then = listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.UntilEndOfYourNextTurn)))),
        ),
    )

    // CR 604.3: a characteristic-defining ability; CR 614.1a: one more token, only when creature tokens would be created
    @Test
    fun queenAllenalOfRuadach() = card(
        name = "Queen Ellanor of Ruamach",
        cost = "{G}{W}{W}",
        type = "Legendary Creature — Elf Noble",
        pt = "*/*",
        text = """
            Queen Ellanor of Ruamach's power and toughness are each equal to the number of creatures you control.
            If one or more creature tokens would be created under your control, those tokens plus a 1/1 white Soldier creature token are created instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(creatureYouControl), count(creatureYouControl)), characteristicDefining = true),
        static(Mechanic.Replacement(EventPattern.CreateToken(and(creature, you)), modify = listOf(Modification.AlsoCreate(soldier, n(1))))),
    )

    // CR 702.3b: attacks as though it didn't have defender; CR 510.1c: damage by toughness; CR 702.10: haste; CR 611.2a: until end of turn
    @Test
    fun walkingBulwark() = card(
        name = "Striding Rampart",
        cost = "{1}",
        type = "Artifact Creature — Golem",
        pt = "0/3",
        text = """
            Defender
            {2}: Until end of turn, target creature with defender gains haste, can attack as though it didn't have defender, and assigns combat damage equal to its toughness rather than its power. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(manaCost("{2}"), untilEndOfTurn(target("creature"), has(Keyword.Haste), Mechanic.CanAttackDespiteDefender, Mechanic.AssignsDamageByToughness),
            targets = listOf(slot("creature", and(creature, kw(Keyword.Defender.kind)))), rules = asSorcery),
    )

    // CR 607.2d: the name chosen as it entered; CR 601.2f: opponents' spells with that name cost more; CR 605.1a: mana abilities don't
    @Test
    fun anointedPeacekeeper() = card(
        name = "Consecrated Warden",
        cost = "{2}{W}",
        type = "Creature — Human Cleric",
        pt = "3/3",
        text = """
            Vigilance
            As this creature enters, look at an opponent's hand, then choose any card name.
            Spells your opponents cast with the chosen name cost {2} more to cast.
            Activated abilities of sources with the chosen name cost {2} more to activate unless they're mana abilities.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.LookAt(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent"))))), Players.You)),
            Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You, remember = true),
        ))))),
        static(Mechanic.CostChange(and(spellFilter, chosenName, opponents), CostChange.More(ManaCost.parse("{2}")!!, n(1)))),
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = chosenName), not(ObjectFilter.AbilityOf(kind = AbilityKind.Mana))),
            CostChange.More(ManaCost.parse("{2}")!!, n(1)))),
    )

    // CR 601.2f, 118.7c: {U} less; one opponent cast two or more spells this turn (not all opponents together)
    @Test
    fun ertaisScorn() = card(
        name = "Ertoro's Disdain",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            This spell costs {U} less to cast if an opponent cast two or more spells this turn.
            Counter target spell.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{U}")!!, n(1))),
            condition = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.HappenedThisTurn(
                EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Is(Players.Tested)), atLeast = 2, measure = Measure.Events)))), atLeast = 1)),
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 702.8a: as though it had flash; CR 601.2b: X announced, CR 601.3a: begun only if some X makes it legal
    @Test
    fun silverScrutiny() = card(
        name = "Argent Inquiry",
        cost = "{X}{U}{U}",
        type = "Sorcery",
        text = """
            You may cast this spell as though it had flash if X is 3 or less.
            Draw X cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.InstantSpeed, condition = Condition.Compare(Value.CastX(Objects.Self), Comparison.AtMost, n(3))),
        spell(doing(Effect.Draw(Players.You, Value.Announced("X"))), announced = listOf("X")),
    )

    // CR 106.6: a spending restriction: an instant or sorcery spell, or a spell whose kicker cost was paid (CR 702.33d)
    @Test
    fun volsheTideturner() = card(
        name = "Volmar Tidebender",
        cost = "{1}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "1/3",
        text = "{T}: Add {U}. Spend this mana only to cast an instant or sorcery spell or a kicked spell.",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!), restriction = or(
            and(or(instant, sorcery), spellFilter),
            and(spellFilter, ObjectFilter.WasPaid(Keyword.Kicker.Kind, times = 1)),
        )), Players.You))),
    )

    // ---- The Defilers: CR 110.4b (a permanent spell is a spell of a permanent type), CR 601.2b (an optional additional cost of each such spell you cast), 601.2f (paid, {C} less, only of that colour,
    // not generic mana as CR 118.7b, c would), CR 112.2 (a spell's controller is who cast it)

    // a permanent spell is an artifact, battle, creature, enchantment, or planeswalker spell (CR 110.4b; a land is never cast, CR 305.9)
    private fun coloredPermanentSpells(color: Color) = and(ObjectFilter.OfColor(color), ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle,
        CardType.Creature, CardType.Enchantment, CardType.Planeswalker).map { ObjectFilter.Type(it) }))
    private fun defilerCost(color: Color, symbol: String) = static(has(static(Mechanic.AdditionalCost(cost = cost(CostPart.PayLife(n(2))), optional = true,
        reducesBy = ManaCost.parse(symbol)!!))), affects = Objects.Each(and(coloredPermanentSpells(color), spellFilter, you)))
    private fun defilerText(color: String, symbol: String) =
        "As an additional cost to cast $color permanent spells, you may pay 2 life. Those spells cost $symbol less to cast if you paid life this way. This effect reduces only the amount of $color mana you pay."

    @Test
    fun defilerOfDreams() = card(
        name = "Despoiler of Dreams",
        cost = "{3}{U}{U}",
        type = "Creature — Phyrexian Sphinx",
        pt = "4/3",
        text = """
            Flying
            ${defilerText("blue", "{U}")}
            Whenever you cast a blue permanent spell, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        defilerCost(Color.Blue, "{U}"),
        triggered(youCast(coloredPermanentSpells(Color.Blue)), draw(1)),
    )

    @Test
    fun defilerOfFaith() = card(
        name = "Despoiler of Faith",
        cost = "{3}{W}{W}",
        type = "Creature — Phyrexian Human",
        pt = "5/5",
        text = """
            Vigilance
            ${defilerText("white", "{W}")}
            Whenever you cast a white permanent spell, create a 1/1 white Soldier creature token.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        defilerCost(Color.White, "{W}"),
        triggered(youCast(coloredPermanentSpells(Color.White)), create(soldier)),
    )

    @Test
    fun defilerOfFlesh() = card(
        name = "Despoiler of Flesh",
        cost = "{2}{B}{B}",
        type = "Creature — Phyrexian Horror",
        pt = "4/4",
        text = """
            Menace
            ${defilerText("black", "{B}")}
            Whenever you cast a black permanent spell, target creature you control gets +1/+1 and gains menace until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        defilerCost(Color.Black, "{B}"),
        triggered(youCast(coloredPermanentSpells(Color.Black)), pump(target("creature"), 1, 1, has(Keyword.Menace)), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun defilerOfInstinct() = card(
        name = "Despoiler of Instinct",
        cost = "{2}{R}{R}",
        type = "Creature — Phyrexian Kavu",
        pt = "4/4",
        text = """
            First strike
            ${defilerText("red", "{R}")}
            Whenever you cast a red permanent spell, this creature deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        defilerCost(Color.Red, "{R}"),
        triggered(youCast(coloredPermanentSpells(Color.Red)), damage(1, toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun defilerOfVigor() = card(
        name = "Despoiler of Vigor",
        cost = "{3}{G}{G}",
        type = "Creature — Phyrexian Wurm",
        pt = "6/6",
        text = """
            Trample
            ${defilerText("green", "{G}")}
            Whenever you cast a green permanent spell, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        defilerCost(Color.Green, "{G}"),
        triggered(youCast(coloredPermanentSpells(Color.Green)), counters(Objects.Each(creatureYouControl))),
    )
}
