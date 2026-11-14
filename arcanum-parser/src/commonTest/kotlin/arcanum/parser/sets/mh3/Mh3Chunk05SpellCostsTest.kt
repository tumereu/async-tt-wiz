package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AbilityKind
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MH3 chunk 05: spells' costs and what spells have: cost changes for this spell (life lost, cards drawn), for named or chosen-type
 * spells, keywords given to spells (improvise, convoke from exile, affinity for Auras), and mana restricted to colorless spells.
 */
class Mh3Chunk05SpellCostsTest {
    private fun less(amount: String, times: Value = n(1)) = CostChange.Less(ManaCost.parse(amount)!!, times)
    private fun asEnters(choose: Step) = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(choose)))))
    private val chosenName = ObjectFilter.RememberedName("name")
    private val selfPower = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)
    private val modified = ObjectFilter.Modified

    // CR 702.126a: improvise given to the next spell you cast; CR 614.1d: enters tapped unless you control an Island
    @Test
    fun archOfIngenuity() = card(
        name = "Arch of Ingenuity",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control an Island.
            {T}: Add {U}.
            {U}, {T}: The next spell you cast this turn has improvise. (Your artifacts can help cast that spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.Exists(and(sub("Island"), you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)), Players.You))),
        activated(cost(mana("{U}"), tap), doing(Effect.ApplyMechanics(listOf(has(Keyword.Improvise)), Affected.NextSpells(and(spellFilter, you), 1),
            Duration.EndOfTurn))),
    )

    // CR 601.2f: this spell's cost less the life opponents lost this turn; CR 609.4b: any type of mana for the spells cast from among them
    @Test
    fun crimsonRevelation() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Crimson Revelation",
            cost = "{5}{B/R}{B/R}",
            type = "Sorcery",
            text = """
                This spell costs {1} less to cast for each 1 life your opponents have lost this turn.
                Target opponent exiles the top three cards of their library. Until the end of your next turn, you may play those cards. If you cast a spell this way, mana of any type can be spent to cast it.
            """,
        ) to abilities(
            static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), less("{1}", Value.EventsThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), Measure.Amount)))),
            spell(
                move(Objects.LibraryCards(Players.Target("opponent"), n(3), fromTop = true), Zone.Exile, postBind = "exiled"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, manaAsAnyColor = true, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
                targets = listOf(targetOpponent),
            ),
        ),
        card(
            name = "Gorefen Mire",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {B} or {R}.
            """,
        ) to abilities(
            entersTapped,
            addEither("B", "R"),
        ),
    )

    // CR 601.2f: one less for each card drawn this turn (one draw is one card, CR 121.2); CR 401.4: its owner chooses where
    @Test
    fun ruleLesser() = card(
        name = "Rule Lesser",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            This spell costs {1} less to cast for each card you've drawn this turn.
            The owner of target nonland permanent puts it into their library second from the top or on the bottom.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), less("{1}", Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.You), Measure.Events)))),
        spell(
            Step.Choose(Choice.Option(listOf("second from the top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            Step.If(Condition.Chose("position", "second from the top"), then = listOf(move(target("permanent"), Zone.Library, position = 1)),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 201.3: any card name; CR 601.2f: every player's spells with that name cost more; CR 602.5: no activation, mana abilities excepted
    @Test
    fun silencerPipe() = card(
        name = "Silencer Pipe",
        cost = "{2}",
        type = "Artifact",
        text = """
            Flash
            As this artifact enters, choose a card name.
            Spells with the chosen name cost {3} more to cast.
            Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        asEnters(Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You, remember = true)),
        static(Mechanic.CostChange(and(spellFilter, chosenName), CostChange.More(ManaCost.parse("{3}")!!, n(1)))),
        static(Mechanic.CantBeActivated(manaAbilities = false), affects = Objects.Each(and(chosenName, ObjectFilter.Or(listOf(Zone.Battlefield, Zone.Hand,
            Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command).map { ObjectFilter.InZone(it) })))),
    )

    // CR 601.2f: {1} less for each point of this creature's power; CR 700.9: modified creatures
    @Test
    fun silvermaneTutor() = card(
        name = "Silvermane Tutor",
        cost = "{1}{G}{W}",
        type = "Creature — Fox Samurai",
        pt = "1/3",
        text = """
            Aura and Equipment spells you cast cost {X} less to cast, where X is this creature's power.
            Whenever this creature attacks, other modified creatures you control get +X/+X until end of turn, where X is this creature's power. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(or(sub("Aura"), equipment), spellFilter, you), less("{1}", selfPower))),
        triggered(attacksSelf, untilEndOfTurn(Objects.Each(and(another, creature, you, modified)), Mechanic.ModifyPowerToughness(selfPower, selfPower))),
    )

    // CR 601.2f: colorless spells with mana value 7 or greater; CR 613.4c: other colorless creatures get +1/+1
    @Test
    fun thatWhichForetells() = card(
        name = "That Which Foretells",
        cost = "{1}{C}",
        type = "Creature — Eldrazi Drone",
        pt = "2/2",
        text = """
            Colorless spells you cast with mana value 7 or greater cost {1} less to cast.
            Other colorless creatures you control get +1/+1.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.Colorless, spellFilter, you, manaValue(arcanum.api.Comparison.AtLeast, 7)), less("{1}"))),
        static(pt(1, 1), affects = Objects.Each(and(another, ObjectFilter.Colorless, creature, you))),
    )

    // CR 702.51a: convoke for noncreature spells cast from exile (CR 601.2a); CR 505.1a: the first main phase is the precombat one (there is
    // one a turn); "You may play that card" is its own sentence about the chosen card (none if nothing was discarded)
    @Test
    fun revelBasher() = card(
        name = "Revel Basher",
        cost = "{1}{R}",
        type = "Creature — Lizard Wizard",
        pt = "1/4",
        text = """
            Noncreature spells you cast from exile have convoke. (Each creature you tap while casting a noncreature spell from exile pays for {1} or one mana of that creature's color.)
            At the beginning of your first main phase, you may discard a card. If you do, exile the top two cards of your library, then choose one of them. You may play that card this turn.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Convoke), affects = Objects.Each(and(not(creature), spellFilter, you, ObjectFilter.WasCast(from = Zone.Exile)))),
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
                Step.Choose(Choice.Objects(ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            )),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("chosen")), Duration.EndOfTurn)),
        ),
    )

    // CR 702.41a: affinity for Auras given to enchantment spells; CR 700.9: an Aura spell targeting a modified permanent you control
    @Test
    fun ivoryPawCourtCounsel() = card(
        name = "Ivory-Paw, Court Counsel",
        cost = "{1}{W}{W}",
        type = "Legendary Creature — Fox Advisor",
        pt = "3/4",
        text = """
            Lifelink
            Enchantment spells you cast have affinity for Auras. (They cost {1} less to cast for each Aura you control.)
            Whenever you cast an Aura spell that targets a modified permanent you control, draw a card. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        static(has(Keyword.Affinity(sub("Aura"), "Auras")), affects = Objects.Each(and(enchantment, spellFilter, you))),
        triggered(youCast(and(sub("Aura"), spellFilter, ObjectFilter.Targets(Objects.Each(and(permanentFilter, you, modified))))), draw(1)),
    )

    // CR 106.6: mana spendable only on colorless spells and activated abilities
    @Test
    fun seerOfTheUnfathomable() = card(
        name = "Seer of the Unfathomable",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "0/4",
        text = "{T}: Add {C}. Spend this mana only to cast a colorless spell or to activate an ability.",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!), restriction = or(and(spellFilter, ObjectFilter.Colorless),
            ObjectFilter.AbilityOf(kind = AbilityKind.Activated))), Players.You))),
    )

    // CR 601.2f: every player's creature spells of the chosen type cost less (the type chosen as it entered, CR 614.12)
    @Test
    fun tinkersBroodVat() = card(
        name = "Tinker's Brood Vat",
        cost = "{3}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a creature type.
            Creature spells of the chosen type cost {2} less to cast.
        """,
    ) parsesTo abilities(
        asEnters(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)),
        static(Mechanic.CostChange(and(creature, spellFilter, ObjectFilter.Sub(SubtypeRef.Remembered("type"))), less("{2}"))),
    )
}
