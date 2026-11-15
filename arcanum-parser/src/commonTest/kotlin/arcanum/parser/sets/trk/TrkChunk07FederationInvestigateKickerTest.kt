package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 07: Federation ("the number of creature types among non-Borg creatures you control", CR 205.3m), Food and Clue tokens
 * (investigate, CR 701.16a) and kicker (CR 702.33).
 */
class TrkChunk07FederationInvestigateKickerTest {
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private val looked = Objects.Bound("looked")
    private val stun = CounterKind.Stun
    private fun Ability.word(word: String = "Federation"): Ability = when (this) {
        is Ability.Static -> copy(abilityWord = word)
        is Ability.Triggered -> copy(abilityWord = word)
        is Ability.Activated -> copy(abilityWord = word)
        is Ability.Spell -> copy(abilityWord = word)
        else -> error("no ability word on $this")
    }
    private fun withMenace(of: Objects) = TokenSpec.CopyOf(of, except = listOf(CopyException.AddAbilities(listOf(Keyword.Menace))))

    // ---- Federation

    // CR 201.5c (a shortened name: first and last word), 111.4 (a named token), 205.3m (creature types), 602.5d (activate only as a sorcery)
    @Test
    fun captainDelmarVoss() = card(
        name = "Captain Delmar Voss",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Officer",
        pt = "3/3",
        text = """
            When Captain Voss enters, create Rufus, a legendary 1/1 green Dog creature token.
            Federation — {G}, {T}: Target creature you control gets +X/+X until end of turn, where X is the number of creature types among non-Borg creatures you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Dog", listOf(Color.Green), 1, 1, name = "Rufus").let {
            it.copy(face = it.face.copy(supertypes = listOf(Supertype.Legendary)))
        })),
        activated(cost(mana("{G}"), tap), untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(federation, federation)),
            targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery).word(),
    )

    // CR 205.3m, 401.4 (the rest on the bottom in a random order)
    @Test
    fun ilsaMarrowLinguist() = card(
        name = "Ilsa Marrow, Linguist",
        cost = "{4}{U}",
        type = "Legendary Creature — Human Scientist",
        pt = "3/3",
        text = "Federation — When Ilsa Marrow enters, look at the top X cards of your library, where X is the number of creature types among non-Borg creatures you control. Put one of them into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, federation, fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = looked), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(looked, Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ).word(),
    )

    // CR 707.12 (copies of a card, cast), 118.9 (without paying their mana costs), 608.2c (the spell exiles itself as it resolves)
    @Test
    fun boundlessVariety() = card(
        name = "Boundless Variety",
        cost = "{7}{R}{R}",
        type = "Sorcery",
        text = "Federation — Exile target instant or sorcery card from your graveyard. Copy that card X times, where X is the number of creature types among non-Borg creatures you control. You may cast the copies without paying their mana costs. Exile Boundless Variety.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.CopyCard(Objects.Bound("exiled"), Players.You, bind = "copies", times = federation)),
            Step.ForEach(Each.Objects(Objects.Bound("copies")), bind = "copy", steps = listOf(
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You))),
            move(Objects.Self, Zone.Exile),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery)))),
        ).word(),
    )

    // CR 702.8a (flash), 205.3m, 506.4 (attacking or blocking)
    @Test
    fun tavrinStoicCadet() = card(
        name = "Tavrin, Stoic Cadet",
        cost = "{4}{W}",
        type = "Legendary Creature — Vulcan Advisor",
        pt = "2/2",
        text = """
            Flash
            Federation — When Tavrin enters, she deals X damage to target attacking or blocking creature an opponent controls, where X is the number of creature types among non-Borg creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, damage(federation, toObjects(target("creature"))),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature, opponents)))).word(),
    )

    // CR 205.3m, 107.1a (rounded down: one card for every five), 121.2 (cards drawn this way)
    @Test
    fun solvarFederationEnvoy() = card(
        name = "Solvar, Federation Envoy",
        cost = "{1}{G}{W}{U}",
        type = "Legendary Creature — Vulcan Advisor",
        pt = "0/5",
        text = "Federation — At the beginning of combat on your turn, draw a card for every five creature types among non-Borg creatures you control. If you draw three or more cards this way, put two +1/+1 counters on each creature you control.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            doing(Effect.Draw(Players.You, Value.Quotient(federation, 5), postBind = "drawn")),
            ifThen(Condition.Compare(Value.Count(Objects.Bound("drawn")), Comparison.AtLeast, n(3)), counters(Objects.Each(creatureYouControl), 2)),
        ).word(),
    )

    // CR 605.1a (a mana ability), 106.1b (an amount of mana of the chosen color)
    @Test
    fun fleetCadetCollege() = card(
        name = "Fleet Cadet College",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {C}.
            Federation — {5}, {T}: Choose a color. Add an amount of mana of that color equal to the number of creature types among non-Borg creatures you control.
        """,
    ) parsesTo abilities(
        tapFor("C"),
        activated(cost(mana("{5}"), tap),
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(federation, listOf(ColorRef.Chosen("color")))), Players.You)),
        ).word(),
    )

    // ---- Food and Clue tokens

    // CR 111.10b (Food), 707.2 (a token that's a copy), 111.1 (tapped)
    @Test
    fun duplicatorUnit() = card(
        name = "Duplicator Unit",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {2}, {T}: Create a tapped token that's a copy of target token you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(food)),
        activated(cost(mana("{2}"), tap), doing(Effect.CreateToken(TokenSpec.CopyOf(target("token")), n(1), Players.You, tapped = true)),
            targets = listOf(slot("token", and(token, you)))),
    )

    // CR 201.5c (first and last word), 700.2 (modes), 115.6 (up to one target), 701.16a (investigate)
    @Test
    fun commanderLenaOkafor() = card(
        name = "Commander Lena Okafor",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Officer",
        pt = "3/2",
        text = """
            When Commander Okafor enters, choose one —
            • Exile up to one target card from a graveyard. You gain 2 life.
            • Investigate. You lose 2 life. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), gainLife(2), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
            mode(investigate(), loseLife(2)),
        ))),
    )

    // CR 700.2, 122.1d (stun counters), 701.16a (investigate twice)
    @Test
    fun wardenUlmo() = card(
        name = "Warden Ulmo",
        cost = "{3}{W/U}{W/U}",
        type = "Legendary Creature — Shapeshifter Detective",
        pt = "4/3",
        text = """
            When Warden Ulmo enters, choose one —
            • Tap target creature an opponent controls. Put two stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            • Investigate twice. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(tapIt(target("creature")), counters(target("creature"), 2, stun), targets = listOf(slot("creature", creatureOpponentControls))),
            mode(investigate(n(2))),
        ))),
    )

    // CR 714.2b (chapters), 701.21a (each opponent sacrifices of their choice), 701.16a, 118.12 ("you may sacrifice … If you do")
    @Test
    fun underTheDimLantern() = card(
        name = "Under the Dim Lantern",
        cost = "{3}{B}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Each opponent sacrifices a nontoken creature of their choice.
            II — Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            III — You may sacrifice an artifact or creature. If you do, create two 2/2 black Romulan creature tokens.
        """,
    ) parsesTo abilities(
        chapter(1, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", steps = listOf(
            Step.Choose(Choice.Objects(and(not(token), creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Bound("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)),
        chapter(2, investigate()),
        chapter(3, Step.Pay(cost(sacrificeA(or(artifact, creature))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), create(creatureToken("Romulan", listOf(Color.Black), 2, 2), n(2)))),
    )

    // CR 207.2c (landfall, an ability word), 603.2 ("the second time this ability has resolved this turn"), 701.16a
    @Test
    fun jemHalloWarReporter() = card(
        name = "Jem Hallo, War Reporter",
        cost = "{1}{G}",
        type = "Legendary Creature — Human Citizen",
        pt = "2/2",
        text = """Landfall — Whenever a land you control enters, you gain 1 life. If this is the second time this ability has resolved this turn, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        triggered(landfall, gainLife(1),
            ifThen(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2)), investigate())).word("Landfall"),
    )

    // CR 702.10a (haste), 603.6c (dies), 603.2 ("during your turn": triggers only then)
    @Test
    fun fleetGuardRecruit() = card(
        name = "Fleet Guard Recruit",
        cost = "{R}",
        type = "Creature — Human Officer",
        pt = "1/1",
        text = """
            Haste
            When this creature dies during your turn, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(diesSelf, investigate(), triggersOnlyIf = Condition.YourTurn),
    )

    // CR 714.2b, 610.3 (return the exiled card), 400.7 (a new object), 111.1 (nontoken)
    @Test
    fun theWorthOfAMind() = card(
        name = "The Worth of a Mind",
        cost = "{2}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Exile up to one target nontoken artifact or creature.
            II — Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            III — Return the exiled card to the battlefield under its owner's control. You gain 2 life.
        """,
    ) parsesTo abilities(
        chapter(1, move(target("permanent"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("permanent", and(not(token), or(artifact, creature)), min = 0))),
        chapter(2, investigate()),
        chapter(3, move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled"))), gainLife(2)),
    )

    // ---- Kicker

    // CR 702.33d (if kicked, instead), 601.2c (each version's targets only when it happens), 707.9b (except it has menace), 608.2c
    @Test
    fun shadowRealmIncursion() = card(
        name = "Shadow Realm Incursion",
        cost = "{1}{U}{B}",
        type = "Sorcery",
        text = """
            Kicker {4}{U}{B} (You may pay an additional {4}{U}{B} as you cast this spell.)
            Create a token that's a copy of target creature an opponent controls, except it has menace. If this spell was kicked, instead for each creature target opponent controls, create a token that's a copy of that creature, except it has menace.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}{U}{B}"))),
        spell(
            ifThen(kicked,
                Step.ForEach(Each.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))))), "creature",
                    listOf(doing(Effect.CreateToken(withMenace(Objects.Bound("creature")), n(1), Players.You)))),
                otherwise = listOf(doing(Effect.CreateToken(withMenace(target("creature")), n(1), Players.You)))),
            targets = listOf(slot("creature", creatureOpponentControls).copy(onlyIf = Condition.Not(kicked)), targetOpponent.copy(onlyIf = kicked)),
        ),
    )

    // CR 702.33a (kicker: sacrifice as an additional cost), 702.33d
    @Test
    fun ventTheReactorCore() = card(
        name = "Vent the Reactor Core",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
            Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -6/-6 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(sacrificeA(or(artifact, creature))))),
        spell(ifThen(kicked, pump(target("creature"), -6, -6), otherwise = listOf(pump(target("creature"), -2, -2))), targets = listOf(slot("creature", creature))),
    )

    // CR 702.33d, 601.2c (the unkicked spell targets only a spell with mana value 2 or less, the kicked one any spell)
    @Test
    fun mostIrrational() = card(
        name = "Most Irrational",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            Counter target spell with mana value 2 or less. If this spell was kicked, instead counter target spell.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{1}{U}"))),
        spell(
            ifThen(kicked, doing(Effect.Counter(objects = target("promised spell"))), otherwise = listOf(doing(Effect.Counter(objects = target("spell"))))),
            targets = listOf(slot("spell", and(spellFilter, manaValue(Comparison.AtMost, 2))).copy(onlyIf = Condition.Not(kicked)),
                slot("promised spell", spellFilter).copy(onlyIf = kicked)),
        ),
    )
}
