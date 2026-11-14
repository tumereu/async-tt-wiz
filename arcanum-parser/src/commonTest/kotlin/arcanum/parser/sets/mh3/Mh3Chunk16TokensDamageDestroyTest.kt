package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 16: creating tokens (CR 111), dealing damage (CR 120) and destroying (CR 701.8), with five modal double-faced
 * cards (CR 712).
 */
class Mh3Chunk16TokensDamageDestroyTest {
    private val artifactCreature: List<CardType> = listOf(CardType.Artifact, CardType.Creature)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val basic = ObjectFilter.Super(Supertype.Basic)
    private val creatureOrPlaneswalkerYouDontControl = and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))

    /** CR 700.4: a creature died means it went from the battlefield to a graveyard. */
    private val morbid = Condition.HappenedThisTurn(
        EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)

    private fun wurm(power: Int, toughness: Int, keyword: Keyword) =
        creatureToken("Phyrexian Wurm", listOf(Color.Black), power, toughness, listOf(keyword), artifactCreature)

    /** "As this land enters, you may pay 3 life. If you don't, it enters tapped." (CR 614.1c) */
    private val payThreeLifeOrEntersTapped: Ability = Ability.Static(listOf(Mechanic.Replacement(
        event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(cost = cost(CostPart.PayLife(n(3))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), Step.Modify(Modification.EntersTapped)),
        ))),
    )), affects = Affected.Objects(Objects.Self))

    /** "Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle." The controller is the destroyed land's. */
    private val landControllerSearches: Array<Step> = run {
        val controller = Players.ControllerOf(target("land"))
        arrayOf(
            Step.May(
                steps = listOf(
                    Step.Do(Effect.Search(controller, and(basic, land), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = controller)),
                    move(Objects.Bound("found"), Zone.Battlefield, tapped = true, controller = controller),
                    Step.Do(Effect.Shuffle(controller)),
                ),
                player = controller,
            ),
        )
    }
    // CR 111.1, 111.5 (two tokens), 700.2 (modes chosen as the ability is activated), 119.1, 120.3, 701.21
    @Test
    fun breya() = card(
        name = "Brenya, Etherium Weaver",
        cost = "{W}{U}{B}{R}",
        type = "Legendary Artifact Creature — Human",
        pt = "4/4",
        text = """
            When Brenya enters, create two 1/1 blue Thopter artifact creature tokens with flying.
            {2}, Sacrifice two artifacts: Choose one —
            • Brenya deals 3 damage to target player or planeswalker.
            • Target creature gets -4/-4 until end of turn.
            • You gain 5 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Thopter", listOf(Color.Blue), 1, 1, listOf(Keyword.Flying), artifactCreature), n(2))),
        Ability.Activated(
            cost(mana("{2}"), CostPart.Sacrifice(CostObjects.Choose(artifact, count = n(2)))),
            Instructions(modes = modes(
            mode(damage(3, Recipients(target = "player or planeswalker")), targets = listOf(TargetSlot("player or planeswalker",
                TargetKind.Either(planeswalker, PlayerFilter.Any), min = n(1), max = n(1)))),
            mode(untilEndOfTurn(target("creature"), pt(-4, -4)), targets = listOf(slot("creature", creature))),
            mode(gainLife(5)),
            )),
            anyTime,
        ),
    )

    @Test
    fun glaringFleshraker() = card(
        name = "Glaring Drone",
        cost = "{2}{C}",
        type = "Creature — Eldrazi Drone",
        pt = "2/2",
        text = """
            Whenever you cast a colorless spell, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
            Whenever another colorless creature you control enters, this creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        triggered(youCast(and(ObjectFilter.Colorless, spellFilter)), create(eldraziSpawn)),
        triggered(enters(and(another, ObjectFilter.Colorless, creature, you)), damage(1, eachOpponent)),
    )

    @Test
    fun gravedig() = card(
        name = "Gravefill",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target player creates a 2/2 black Zombie creature token.
            • Return target creature card from your graveyard to your hand.
            Entwine {2} (Choose both if you pay the entwine cost.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(
                Step.Do(Effect.CreateToken(creatureToken("Zombie", listOf(Color.Black), 2, 2), n(1), Players.Target("player"))),
                targets = listOf(targetPlayer),
            ),
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        ))),
        Keyword.Entwine(manaCost("{2}")),
    )

    @Test
    fun musterTheDeparted() = card(
        name = "Gather the Departed",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 1/1 white Spirit creature token with flying.
            Morbid — At the beginning of your end step, if a creature died this turn, populate. (Create a token that's a copy of a creature token you control.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Spirit", listOf(Color.White), 1, 1, listOf(Keyword.Flying)))),
        triggered(yourEndStep, Step.Do(Effect.Action(KeywordAction.Populate)), interveningIf = morbid, abilityWord = "Morbid"),
    )

    @Test
    fun ophiomancer() = card(
        name = "Ophiomaster",
        cost = "{2}{B}",
        type = "Creature — Human Shaman",
        pt = "2/2",
        text = "At the beginning of each upkeep, if you control no Snakes, create a 1/1 black Snake creature token with deathtouch.",
    ) parsesTo abilities(
        triggered(
            stepBegins(TurnStep.Upkeep, PlayerFilter.Any),
            create(creatureToken("Snake", listOf(Color.Black), 1, 1, listOf(Keyword.Deathtouch))),
            interveningIf = Condition.Not(Condition.Exists(and(sub("Snake"), you), atLeast = 1)),
        ),
    )

    @Test
    fun reefWorm() = card(
        name = "Pond Worm",
        cost = "{3}{U}",
        type = "Creature — Worm",
        pt = "0/1",
        text = "When this creature dies, create a 3/3 blue Fish creature token with \"When this token dies, create a 6/6 blue Whale creature token with 'When this token dies, create a 9/9 blue Kraken creature token.'\"",
    ) parsesTo abilities(
        triggered(diesSelf, create(
            creatureToken("Fish", listOf(Color.Blue), 3, 3, listOf(
                triggered(diesSelf, create(
                    creatureToken("Whale", listOf(Color.Blue), 6, 6, listOf(
                        triggered(diesSelf, create(creatureToken("Kraken", listOf(Color.Blue), 9, 9))),
                    ))
                )),
            ))
        )),
    )

    @Test
    fun wurmcoilLarva() = card(
        name = "Larva of Wurmcoil",
        cost = "{3}{B}{B}",
        type = "Artifact Creature — Phyrexian Wurm",
        pt = "3/3",
        text = """
            Deathtouch, lifelink
            When this creature dies, create a 1/2 black Phyrexian Wurm artifact creature token with deathtouch and a 2/1 black Phyrexian Wurm artifact creature token with lifelink.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Lifelink,
        triggered(
            diesSelf,
            create(wurm(1, 2, Keyword.Deathtouch)),
            create(wurm(2, 1, Keyword.Lifelink)),
        ),
    )

    @Test
    fun horrificAssault() = card(
        name = "Fierce Blow",
        cost = "{G}",
        type = "Sorcery",
        text = "Target creature you control deals damage equal to its power to target creature or planeswalker you don't control. If you control an Eldrazi, you gain 3 life.",
    ) parsesTo abilities(
        spell(
            damage(power(target("creature")), toObjects(target("permanent")), target("creature")),
            ifThen(Condition.Exists(and(sub("Eldrazi"), you), atLeast = 1), gainLife(3)),
            targets = listOf(slot("creature", creatureYouControl), slot("permanent", creatureOrPlaneswalkerYouDontControl)),
        ),
    )

    @Test
    fun moggMob() = card(
        name = "Mogg Throng",
        cost = "{R}{R}{R}",
        type = "Creature — Goblin",
        pt = "3/3",
        text = "Sacrifice this creature: It deals 3 damage divided as you choose among one, two, or three targets.",
    ) parsesTo abilities(
        activated(
            cost(sacrificeSelf),
            damage(Value.DividedShare, toAny),
            targets = listOf(TargetSlot("any", anyTarget.what, min = n(1), max = n(3), divided = n(3))),
        ),
    )

    @Test
    fun moltenGatekeeper() = card(
        name = "Ember Gatekeeper",
        cost = "{2}{R}",
        type = "Artifact Creature — Golem",
        pt = "2/3",
        text = """
            Whenever another creature you control enters, this creature deals 1 damage to each opponent.
            Unearth {R} ({R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you)), damage(1, eachOpponent)),
        Keyword.Unearth(manaCost("{R}")),
    )

    @Test
    fun razorgrass() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Razorgrass Strike",
            cost = "{1}{W}",
            type = "Instant",
            text = "Razorgrass Strike deals 3 damage to target attacking or blocking creature.",
        ) to abilities(
            spell(
                damage(3, toObjects(target("creature"))),
                targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(Status.Blocking)), creature))),
            ),
        ),
        card(
            name = "Razorgrass Meadow",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {W}.
            """,
        ) to abilities(
            payThreeLifeOrEntersTapped,
            addOne("{W}"),
        ),
    )

    @Test
    fun stumpStomp() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Stump Crush",
            cost = "{1}{R/G}",
            type = "Sorcery",
            text = "Target creature you control deals damage equal to its power to target creature or planeswalker you don't control.",
        ) to abilities(
            spell(
                damage(power(target("creature")), toObjects(target("permanent")), target("creature")),
                targets = listOf(slot("creature", creatureYouControl), slot("permanent", creatureOrPlaneswalkerYouDontControl)),
            ),
        ),
        card(
            name = "Burnwillow Glade",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {R} or {G}.
            """,
        ) to abilities(
            entersTapped,
            addEither("R", "G"),
        ),
    )

    @Test
    fun thrabenCharm() = card(
        name = "Thraben Vow",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Thraben Vow deals damage equal to twice the number of creatures you control to target creature.
            • Destroy target enchantment.
            • Exile any number of target players' graveyards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(
                damage(Value.Times(count(and(creature, you)), 2), toObjects(target("creature"))),
                targets = listOf(slot("creature", creature)),
            ),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(
                move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("players"))))), Zone.Exile),
                targets = listOf(TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(0), max = null)),
            ),
        ))),
    )

    @Test
    fun collectiveResistance() = card(
        name = "Shared Resistance",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Escalate {G} (Pay this cost for each mode chosen beyond the first.)
            Choose one or more —
            • Destroy target artifact.
            • Destroy target enchantment.
            • Target creature gains hexproof and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Escalate(manaCost("{G}")),
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(
                gains(target("creature"), Keyword.Hexproof, Keyword.Indestructible),
                targets = listOf(slot("creature", creature)),
            ),
            min = 1, max = 3,
        ))),
    )

    @Test
    fun fellTheProfane() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Fell the Unclean",
            cost = "{2}{B}{B}",
            type = "Instant",
            text = "Destroy target creature or planeswalker.",
        ) to abilities(
            spell(
                destroy(target("permanent")),
                targets = listOf(slot("permanent", or(creature, planeswalker))),
            ),
        ),
        card(
            name = "Fell Marsh",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {B}.
            """,
        ) to abilities(
            payThreeLifeOrEntersTapped,
            addOne("{B}"),
        ),
    )

    @Test
    fun fowlStrike() = card(
        name = "Fowl Smite",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Destroy target creature with flying.
            Reinforce 2—{2}{G} ({2}{G}, Discard this card: Put two +1/+1 counters on target creature.)
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(creature, kw(Keyword.Flying.kind))))),
        Keyword.Reinforce(2, manaCost("{2}{G}")),
    )

    @Test
    fun meltdown() = card(
        name = "Meltdown Wave",
        cost = "{X}{R}",
        type = "Sorcery",
        text = "Destroy each artifact with mana value X or less.",
    ) parsesTo abilities(
        spell(
            destroy(Objects.Each(and(artifact, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced("X"))))),
            announced = listOf("X"),
        ),
    )

    @Test
    fun siegeSmash() = card(
        name = "Siege Crush",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Split second (As long as this spell is on the stack, players can't cast spells or activate abilities that aren't mana abilities.)
            Choose one —
            • Destroy target artifact.
            • Target creature gets +3/+2 and gains trample until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.SplitSecond,
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            mode(
                pump(target("creature"), 3, 2, has(Keyword.Trample)),
                targets = listOf(slot("creature", creature)),
            ),
        ))),
    )

    @Test
    fun sunderingEruption() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Sundering Blast",
            cost = "{2}{R}",
            type = "Sorcery",
            text = "Destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle. Creatures without flying can't block this turn.",
        ) to abilities(
            spell(
                destroy(target("land")),
                *landControllerSearches,
                untilEndOfTurn(Objects.Each(and(creature, not(kw(Keyword.Flying.kind)))), Mechanic.CantBlock(ObjectFilter.Any)),
                targets = listOf(slot("land", land)),
            ),
        ),
        card(
            name = "Volcanic Rift",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {R}.
            """,
        ) to abilities(
            payThreeLifeOrEntersTapped,
            addOne("{R}"),
        ),
    )

    @Test
    fun whiteOrchidPhantom() = card(
        name = "White Orchid Wraith",
        cost = "{W}{W}",
        type = "Creature — Spirit Knight",
        pt = "2/2",
        text = """
            Flying, first strike
            When this creature enters, destroy up to one target nonbasic land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        triggered(
            entersSelf,
            destroy(target("land")),
            *landControllerSearches,
            targets = listOf(slot("land", and(not(basic), land), min = 0)),
        ),
    )

    @Test
    fun witchEnchanter() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Witch Enchantress",
            cost = "{3}{W}",
            type = "Creature — Human Warlock",
            pt = "2/2",
            text = "When this creature enters, destroy target artifact or enchantment an opponent controls.",
        ) to abilities(
            triggered(
                entersSelf,
                destroy(target("permanent")),
                targets = listOf(slot("permanent", and(or(artifact, enchantment), opponents))),
            ),
        ),
        card(
            name = "Witch-Blessed Glade",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {W}.
            """,
        ) to abilities(
            payThreeLifeOrEntersTapped,
            addOne("{W}"),
        ),
    )
}
